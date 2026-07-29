package _959.server_waypoint.command;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.buffer.MessageBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.navigation.NavigationMath;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationPlatform;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.NavigationSession;
import _959.server_waypoint.navigation.NavigationSnapshot;
import _959.server_waypoint.navigation.NavigationTarget;
import _959.server_waypoint.navigation.TextDisplayTransformation;
import _959.server_waypoint.navigation.TextDisplayTransformationHandler;
import _959.server_waypoint.util.StringCommandBuilder;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreWaypointCommandNavigationTest {
    @TempDir
    private Path tempDir;
    private Config originalConfig;
    private WaypointServerCore server;
    private TestMessageSender sender;
    private TestNavigationPlatform platform;
    private List<TestNavigationHandler> handlers;
    private NavigationService<TestPlayer> navigationService;
    private TestPermissionManager permissionManager;
    private TestWaypointCommand command;
    private CommandDispatcher<TestSource> dispatcher;
    private TestPlayer player;
    private TestSource source;

    @BeforeEach
    void setUp() {
        this.originalConfig = WaypointServerCore.CONFIG;
        WaypointServerCore.CONFIG = new Config();
        this.server = new WaypointServerCore(this.tempDir) {
        };
        WaypointFileManager fileManager = this.server.getOrCreateWaypointFileManager("overworld");
        fileManager.addWaypointList(new WaypointList(
                "bases",
                1,
                List.of(
                        waypoint("Home", 10),
                        waypoint("Mine", 20)
                )
        ));

        this.sender = new TestMessageSender();
        this.platform = new TestNavigationPlatform();
        this.handlers = new ArrayList<>();
        for (NavigationMethod method : NavigationMethod.values()) {
            this.handlers.add(new TestNavigationHandler(method));
        }
        this.navigationService = new NavigationService<>(this.platform, this.handlers);
        this.permissionManager = new TestPermissionManager(true);
        this.player = this.platform.addPlayer();
        this.source = new TestSource(
                "overworld",
                new WaypointPos(0, 64, 0),
                this.player
        );
        this.registerCommand(this.permissionManager);
    }

    @AfterEach
    void tearDown() {
        WaypointServerCore.CONFIG = this.originalConfig;
    }

    @Test
    void navigationIsPlayerOnlyAndReportsTranslatedFeedback() throws CommandSyntaxException {
        TestSource console = new TestSource("overworld", new WaypointPos(0, 64, 0), null);

        this.dispatcher.execute("wp navigate overworld bases Home", console);

        assertEquals(List.of("waypoint.navigation.player_only"), this.sender.errorKeys());
        assertEquals(0, this.navigationService.sessionCount());
    }

    @Test
    void navigationRequiresItsOwnOrdinaryPlayerPermission() {
        TestPermissionManager deniedPermission = new TestPermissionManager(false);
        this.registerCommand(deniedPermission);

        assertThrows(
                CommandSyntaxException.class,
                () -> this.dispatcher.execute("wp navigate overworld bases Home", this.source)
        );

        assertTrue(deniedPermission.sawCheck("navigate", 0));
        assertTrue(this.navigationService.findSession(this.player.uuid()).isEmpty());
        assertTrue(this.sender.messages.isEmpty());
        assertTrue(this.sender.errors.isEmpty());
    }

    @Test
    void useDisableAndStatusLiteralsAreNotParsedAsDimensions() throws CommandSyntaxException {
        this.dispatcher.execute("wp navigate use compass", this.source);
        this.dispatcher.execute("wp navigate disable map", this.source);
        this.dispatcher.execute("wp navigate disable", this.source);
        this.dispatcher.execute("wp navigate status", this.source);

        assertEquals(List.of(
                "waypoint.navigation.no_active",
                "waypoint.navigation.no_active",
                "waypoint.navigation.no_active",
                "waypoint.navigation.no_active"
        ), this.sender.errorKeys());
        assertTrue(this.command.validatedDimensions.isEmpty());
    }

    @Test
    void navigationWithoutMethodSuffixStartsWithConfiguredDefault() throws CommandSyntaxException {
        this.dispatcher.execute("wp navigate overworld bases Home", this.source);

        NavigationSession session = this.session();
        assertEquals("Home", session.target().waypointName());
        assertEquals(Set.of(NavigationMethod.ACTIONBAR), session.enabledMethods());
        assertEquals(List.of("waypoint.navigation.started"), this.sender.messageKeys());
        assertTrue(this.permissionManager.sawCheck("navigate", 0));
    }

    @Test
    void editingTargetRefreshesNavigationInformationAndItems()
            throws CommandSyntaxException, IOException {
        this.dispatcher.execute("wp navigate overworld bases Home all", this.source);
        for (TestNavigationHandler handler : this.handlers) {
            handler.updateCount = 0;
        }
        Files.createDirectories(this.tempDir.resolve("waypoints"));

        this.dispatcher.execute(
                "wp edit overworld bases Home Renamed R position ABCDEF 45 true",
                this.source
        );

        NavigationTarget target = this.session().target();
        assertEquals("Renamed", target.waypointName());
        assertEquals(this.source.position(), target.position());
        assertEquals(0xABCDEF, target.rgb());
        for (TestNavigationHandler handler : this.handlers) {
            assertEquals(1, handler.updateCount);
            assertEquals(target, handler.lastUpdatedTarget);
        }
    }

    @Test
    void configuredAllDefaultStartsWithEveryMethod() throws CommandSyntaxException {
        this.server.loadConfig(new StringReader("""
                {
                  "defaultNavigationMethods": [
                    "compass",
                    "map",
                    "bossbar",
                    "actionbar",
                    "text_display"
                  ]
                }
                """));

        this.dispatcher.execute("wp navigate overworld bases Home", this.source);

        assertEquals(NavigationMethod.definedMethods(), this.session().enabledMethods());
    }

    @Test
    void configuredDefaultMethodsAreApplied() throws CommandSyntaxException {
        this.server.loadConfig(new StringReader("""
                {
                  "defaultNavigationMethods": ["map"]
                }
                """));
        this.dispatcher.execute("wp navigate overworld bases Home", this.source);
        assertEquals(Set.of(NavigationMethod.MAP), this.session().enabledMethods());

        this.navigationService.disableAll(this.player);
        this.dispatcher.execute("wp navigate overworld bases Mine default", this.source);
        assertEquals(Set.of(NavigationMethod.MAP), this.session().enabledMethods());
    }

    @Test
    void invalidConfiguredDefaultMethodThrows() {
        assertThrows(
                JsonParseException.class,
                () -> this.server.loadConfig(new StringReader("""
                        {
                          "defaultNavigationMethods": ["not-a-method"]
                        }
                        """))
        );
    }

    @Test
    void navigateHelpDocumentsEverySyntaxMethodAndClickableExample() throws CommandSyntaxException {
        this.dispatcher.execute("wp help navigate", this.source);

        Component help = this.sender.messages.get(this.sender.messages.size() - 1);
        String helpText = plainText(help);
        assertTrue(helpText.contains("/wp navigate <dimension> <list> <waypoint>"));
        assertTrue(helpText.contains(
                "/wp navigate <dimension> <list> <waypoint> [default|all|<method>]"
        ));
        assertTrue(helpText.contains("/wp navigate use <method>"));
        assertTrue(helpText.contains("/wp navigate disable [<method>]"));
        assertTrue(helpText.contains("/wp navigate status"));
        assertTrue(helpText.contains(
                "/wp navigate config text_display transformation translation <x> <y> <z>"
        ));
        assertTrue(helpText.contains(
                "/wp navigate config text_display transformation rotation <x> <y> <z>"
        ));
        assertTrue(helpText.contains(
                "/wp navigate config text_display transformation scale <x> <y> <z>"
        ));
        assertTrue(helpText.contains(
                "/wp navigate config text_display transformation reset"
        ));
        assertFalse(helpText.contains(" using "));
        assertFalse(helpText.contains("/wp navigate methods"));

        assertTrue(translationKeys(help).containsAll(List.of(
                "waypoint.help.navigate.title",
                "waypoint.help.navigate.summary",
                "waypoint.help.section.usage",
                "waypoint.help.navigate.usage.start",
                "waypoint.help.navigate.usage.methods",
                "waypoint.help.navigate.usage.use",
                "waypoint.help.navigate.usage.disable",
                "waypoint.help.navigate.usage.status",
                "waypoint.help.navigate.usage.transformation.translation",
                "waypoint.help.navigate.usage.transformation.rotation",
                "waypoint.help.navigate.usage.transformation.scale",
                "waypoint.help.navigate.usage.transformation.reset",
                "waypoint.help.section.arguments",
                "waypoint.help.navigate.argument.target_methods",
                "waypoint.help.navigate.argument.method",
                "waypoint.help.navigate.argument.transformation.vector",
                "waypoint.help.navigate.section.methods",
                "waypoint.help.navigate.method.compass",
                "waypoint.help.navigate.method.map",
                "waypoint.help.navigate.method.bossbar",
                "waypoint.help.navigate.method.actionbar",
                "waypoint.help.navigate.method.text_display",
                "waypoint.help.navigate.section.inventory",
                "waypoint.help.navigate.inventory",
                "waypoint.help.section.examples",
                "waypoint.help.navigate.example.default",
                "waypoint.help.navigate.example.all",
                "waypoint.help.navigate.example.method",
                "waypoint.help.navigate.example.use",
                "waypoint.help.navigate.example.transformation.translation",
                "waypoint.help.navigate.example.transformation.rotation",
                "waypoint.help.navigate.example.transformation.scale"
        )));

        List<String> suggestions = suggestedCommands(help);
        assertTrue(suggestions.contains(
                "/wp navigate minecraft:overworld \"Villages\" \"Oak Village\""
        ));
        assertTrue(suggestions.contains(
                "/wp navigate minecraft:overworld \"Villages\" \"Oak Village\" all"
        ));
        assertTrue(suggestions.contains(
                "/wp navigate minecraft:overworld \"Villages\" \"Oak Village\" bossbar"
        ));
        assertTrue(suggestions.contains("/wp navigate use bossbar"));
        assertTrue(suggestions.contains(
                "/wp navigate config text_display transformation translation 0 0.1 0"
        ));
        assertTrue(suggestions.contains(
                "/wp navigate config text_display transformation rotation 5 0 0"
        ));
        assertTrue(suggestions.contains(
                "/wp navigate config text_display transformation scale 1.35 1.35 1.35"
        ));
        assertFalse(suggestions.contains("/wp navigate methods"));
        assertEquals(List.of("/wp help"), runCommands(help));
    }

    @Test
    void helpLinksNavigateTopicWithoutRegisteringMethodsSubcommand() throws CommandSyntaxException {
        this.dispatcher.execute("wp help", this.source);

        Component mainHelp = this.sender.messages.get(this.sender.messages.size() - 1);
        assertTrue(translationKeys(mainHelp).contains("waypoint.help.navigate"));
        assertTrue(suggestedCommands(mainHelp).contains("/wp navigate "));
        assertTrue(runCommands(mainHelp).contains("/wp help navigate"));
        assertFalse(plainText(mainHelp).contains("/wp navigate methods"));

        var navigateNode = this.dispatcher.getRoot()
                .getChild("wp")
                .getChild("navigate");
        assertNull(navigateNode.getChild("methods"));
    }

    @Test
    void retargetWithoutMethodSuffixPreservesCurrentMethods() throws CommandSyntaxException {
        this.dispatcher.execute(
                "wp navigate overworld bases Home compass",
                this.source
        );
        this.sender.clear();

        this.dispatcher.execute("wp navigate overworld bases Mine", this.source);

        NavigationSession session = this.session();
        assertEquals("Mine", session.target().waypointName());
        assertEquals(Set.of(NavigationMethod.COMPASS), session.enabledMethods());
        assertEquals(List.of("waypoint.navigation.target_changed"), this.sender.messageKeys());
    }

    @Test
    void explicitMethodReplacesSelectionAndAllEnablesEverySupportedMethod()
            throws CommandSyntaxException {
        this.dispatcher.execute("wp navigate overworld bases Home", this.source);

        this.dispatcher.execute(
                "wp navigate overworld bases Mine bossbar",
                this.source
        );
        assertEquals(Set.of(NavigationMethod.BOSSBAR), this.session().enabledMethods());
        assertEquals("Mine", this.session().target().waypointName());

        this.dispatcher.execute(
                "wp navigate overworld bases Home all",
                this.source
        );
        assertEquals(
                this.navigationService.supportedNavigationMethods(),
                this.session().enabledMethods()
        );
        assertEquals("Home", this.session().target().waypointName());
    }

    @Test
    void allIncludesEverySupportedExperimentalMethod() throws CommandSyntaxException {
        this.dispatcher.execute(
                "wp navigate overworld bases Home text_display",
                this.source
        );

        assertEquals(Set.of(NavigationMethod.TEXT_DISPLAY), this.session().enabledMethods());

        this.dispatcher.execute(
                "wp navigate overworld bases Mine all",
                this.source
        );

        assertEquals(
                this.navigationService.supportedNavigationMethods(),
                this.session().enabledMethods()
        );
        assertTrue(this.session().isEnabled(NavigationMethod.TEXT_DISPLAY));
    }

    @Test
    void textDisplayTransformationCommandsUpdateStoredComponentsIndependentlyAndReset()
            throws CommandSyntaxException {
        this.dispatcher.execute(
                "wp navigate overworld bases Home text_display",
                this.source
        );
        this.sender.clear();

        this.dispatcher.execute(
                "wp navigate config text_display transformation translation 1 -2 3",
                this.source
        );

        TestNavigationHandler handler = this.handlers.stream()
                .filter(candidate -> candidate.method() == NavigationMethod.TEXT_DISPLAY)
                .findFirst()
                .orElseThrow();
        assertEquals(new Vector3f(1.0F, -2.45F, 1.8F), handler.lastTranslation);
        assertEquals(TextDisplayTransformation.baseScale(), handler.lastScale);

        this.dispatcher.execute(
                "wp navigate config text_display transformation rotation 10 20 30",
                this.source
        );
        this.dispatcher.execute(
                "wp navigate config text_display transformation scale 1 2 1",
                this.source
        );
        TextDisplayTransformation transformation = this.session().textDisplayTransformation();
        assertEquals(new Vector3f(1.0F, -2.0F, 3.0F), transformation.translation());
        assertEquals(new Vector3f(10.0F, 20.0F, 30.0F), transformation.rotation());
        assertEquals(new Vector3f(1.0F, 2.0F, 1.0F), transformation.scale());
        assertEquals(transformation.rotationQuaternion(), handler.lastRotation);
        assertEquals(new Vector3f(0.22F, 0.44F, 0.22F), handler.lastScale);
        assertEquals(
                "waypoint.navigation.text_display.transformation.updated",
                this.sender.lastMessageKey()
        );

        this.dispatcher.execute(
                "wp navigate config text_display transformation scale 1 1 1",
                this.source
        );
        assertEquals(TextDisplayTransformation.baseScale(), handler.lastScale);
        assertEquals(
                new Vector3f(1.0F, -2.0F, 3.0F),
                this.session().textDisplayTransformation().translation()
        );

        var transformationNode = this.dispatcher.getRoot()
                .getChild("wp")
                .getChild("navigate")
                .getChild("config")
                .getChild("text_display")
                .getChild("transformation");
        assertNotNull(transformationNode.getChild("translation"));
        assertNotNull(transformationNode.getChild("rotation"));
        assertNotNull(transformationNode.getChild("scale"));
        assertNull(transformationNode.getChild("left_rotation"));
        assertNull(transformationNode.getChild("right_rotation"));
        assertThrows(
                CommandSyntaxException.class,
                () -> this.dispatcher.execute(
                        "wp navigate config text_display transformation",
                        this.source
                )
        );
        assertThrows(
                CommandSyntaxException.class,
                () -> this.dispatcher.execute(
                        "wp navigate config text_display transformation translation 0 0 0 rotation 0 0 0 scale 1 1 1",
                        this.source
                )
        );

        this.dispatcher.execute(
                "wp navigate config text_display transformation reset",
                this.source
        );
        assertEquals(TextDisplayTransformation.defaultValue(), this.session().textDisplayTransformation());
        assertEquals(TextDisplayTransformation.baseTranslation(), handler.lastTranslation);
        assertEquals(TextDisplayTransformation.baseScale(), handler.lastScale);
        assertEquals(TextDisplayTransformation.defaultValue().rotationQuaternion(), handler.lastRotation);
        assertEquals(5, handler.transformationCount);
        assertEquals(
                "waypoint.navigation.text_display.transformation.reset",
                this.sender.lastMessageKey()
        );
    }

    @Test
    void textDisplayTransformationCommandsEnforceSafeRanges() throws CommandSyntaxException {
        this.dispatcher.execute(
                "wp navigate overworld bases Home text_display",
                this.source
        );

        assertThrows(
                CommandSyntaxException.class,
                () -> this.dispatcher.execute(
                        "wp navigate config text_display transformation translation 17 0 0",
                        this.source
                )
        );
        assertThrows(
                CommandSyntaxException.class,
                () -> this.dispatcher.execute(
                        "wp navigate config text_display transformation scale 5 1 1",
                        this.source
                )
        );
    }

    @Test
    void unsupportedExperimentalMethodIsNotRegisteredOrDocumented()
            throws CommandSyntaxException {
        List<TestNavigationHandler> handlers = NavigationMethod.definedMethods().stream()
                .filter(method -> method != NavigationMethod.TEXT_DISPLAY)
                .map(TestNavigationHandler::new)
                .toList();
        this.navigationService = new NavigationService<>(this.platform, handlers);
        this.registerCommand(this.permissionManager);

        var navigateNode = this.dispatcher.getRoot()
                .getChild("wp")
                .getChild("navigate");
        assertNull(navigateNode.getChild("use").getChild("text_display"));
        assertNull(navigateNode.getChild("disable").getChild("text_display"));
        assertNull(navigateNode.getChild("config").getChild("text_display"));

        this.dispatcher.execute("wp help navigate", this.source);
        Component help = this.sender.messages.get(this.sender.messages.size() - 1);
        assertFalse(plainText(help).contains("text_display"));
        assertFalse(translationKeys(help).contains(
                "waypoint.help.navigate.usage.transformation.translation"
        ));
    }

    @Test
    void useDisableAndStatusOperateOnTheActiveSession() throws CommandSyntaxException {
        this.dispatcher.execute("wp navigate overworld bases Home", this.source);
        this.sender.clear();

        this.dispatcher.execute("wp navigate use bossbar", this.source);
        assertEquals(
                Set.of(NavigationMethod.ACTIONBAR, NavigationMethod.BOSSBAR),
                this.session().enabledMethods()
        );
        assertEquals("waypoint.navigation.method_enabled", this.sender.lastMessageKey());

        this.dispatcher.execute("wp navigate status", this.source);
        assertEquals("waypoint.navigation.status", this.sender.lastMessageKey());

        this.dispatcher.execute("wp navigate disable actionbar", this.source);
        assertEquals(Set.of(NavigationMethod.BOSSBAR), this.session().enabledMethods());
        assertEquals("waypoint.navigation.method_disabled", this.sender.lastMessageKey());

        this.dispatcher.execute("wp navigate disable", this.source);
        assertTrue(this.navigationService.findSession(this.player.uuid()).isEmpty());
        assertEquals("waypoint.navigation.disabled", this.sender.lastMessageKey());

        this.dispatcher.execute("wp navigate status", this.source);
        assertEquals("waypoint.navigation.no_active", this.sender.lastErrorKey());
    }

    @Test
    void stringCommandBuilderSerializesEveryNavigationForm() {
        assertEquals(
                "/wp navigate minecraft:overworld \"Home Bases\" \"Main Home\"",
                StringCommandBuilder.navigateCmd(
                        "minecraft:overworld",
                        "Home Bases",
                        "Main Home"
                )
        );
        assertEquals(
                "/wp navigate minecraft:overworld \"Home Bases\" \"Main Home\" all",
                StringCommandBuilder.navigateWithMethodsCmd(
                        "minecraft:overworld",
                        "Home Bases",
                        "Main Home",
                        "all"
                )
        );
        assertEquals(
                "/wp navigate use bossbar",
                StringCommandBuilder.navigateUseCmd("bossbar")
        );
        assertEquals(
                "/wp navigate disable",
                StringCommandBuilder.navigateDisableCmd()
        );
        assertEquals(
                "/wp navigate disable map",
                StringCommandBuilder.navigateDisableCmd("map")
        );
        assertEquals(
                "/wp navigate status",
                StringCommandBuilder.navigateStatusCmd()
        );
    }

    private void registerCommand(TestPermissionManager permissions) {
        this.dispatcher = new CommandDispatcher<>();
        this.command = new TestWaypointCommand(
                this.server,
                this.sender,
                permissions,
                this.navigationService
        );
        this.command.register(this.dispatcher);
    }

    private NavigationSession session() {
        return this.navigationService.findSession(this.player.uuid()).orElseThrow();
    }

    private static String plainText(Component component) {
        StringBuilder text = new StringBuilder();
        appendPlainText(component, text);
        return text.toString();
    }

    private static void appendPlainText(Component component, StringBuilder text) {
        if (component instanceof TextComponent textComponent) {
            text.append(textComponent.content());
        }
        for (Component child : component.children()) {
            appendPlainText(child, text);
        }
    }

    private static List<String> suggestedCommands(Component component) {
        List<String> commands = new ArrayList<>();
        collectCommands(component, ClickEvent.Action.SUGGEST_COMMAND, commands);
        return commands;
    }

    private static List<String> runCommands(Component component) {
        List<String> commands = new ArrayList<>();
        collectCommands(component, ClickEvent.Action.RUN_COMMAND, commands);
        return commands;
    }

    private static void collectCommands(
            Component component,
            ClickEvent.Action action,
            List<String> commands
    ) {
        ClickEvent clickEvent = component.clickEvent();
        if (clickEvent != null && clickEvent.action() == action) {
            commands.add(clickEvent.value());
        }
        for (Component child : component.children()) {
            collectCommands(child, action, commands);
        }
    }

    private static List<String> translationKeys(Component component) {
        List<String> keys = new ArrayList<>();
        collectTranslationKeys(component, keys);
        return keys;
    }

    private static void collectTranslationKeys(Component component, List<String> keys) {
        if (component instanceof TranslatableComponent translatableComponent) {
            keys.add(translatableComponent.key());
        }
        for (Component child : component.children()) {
            collectTranslationKeys(child, keys);
        }
    }

    private static SimpleWaypoint waypoint(String name, int x) {
        return new SimpleWaypoint(
                name,
                name.substring(0, 1),
                new WaypointPos(x, 64, 0),
                0x39C5BB,
                0,
                false
        );
    }

    private record TestPlayer(UUID uuid) {
    }

    private record TestSource(
            String dimensionName,
            WaypointPos position,
            @Nullable TestPlayer player
    ) {
    }

    private static final class TestWaypointCommand
            extends CoreWaypointCommand<TestSource, String, TestPlayer, String, String> {
        private final List<String> validatedDimensions = new ArrayList<>();

        private TestWaypointCommand(
                WaypointServerCore server,
                TestMessageSender sender,
                PermissionManager<TestSource, String, TestPlayer> permissionManager,
                NavigationService<TestPlayer> navigationService
        ) {
            super(
                    server,
                    sender,
                    permissionManager,
                    navigationService,
                    StringArgumentType::string,
                    StringArgumentType::string
            );
        }

        @Override
        protected String toDimensionName(String dimensionArgument) {
            return dimensionArgument;
        }

        @Override
        protected WaypointPos toWaypointPos(TestSource source, String blockPositionArgument) {
            return source.position();
        }

        @Override
        protected boolean isDimensionValid(TestSource source, String dimensionArgument) {
            this.validatedDimensions.add(dimensionArgument);
            return "overworld".equals(dimensionArgument);
        }

        @Override
        protected void executeByServer(TestSource source, Runnable task) {
            task.run();
        }

        @Override
        protected String getSourceDimension(TestSource source) {
            return source.dimensionName();
        }

        @Override
        protected WaypointPos getSourcePosition(TestSource source) {
            return source.position();
        }

        @Override
        protected float getSourceYaw(TestSource source) {
            return 0.0F;
        }

        @Override
        protected @Nullable TestPlayer getPlayer(TestSource source) {
            return source.player();
        }

        @Override
        protected String getPlayerName(TestPlayer player) {
            return "player";
        }

        @Override
        protected void teleportPlayer(
                TestSource source,
                TestPlayer player,
                String dimensionArgument,
                WaypointPos pos,
                int yaw
        ) {
        }

        @Override
        protected Message getMessageFromComponent(Component component) {
            return component::toString;
        }
    }

    private static final class TestPermissionManager
            extends PermissionManager<TestSource, String, TestPlayer> {
        private final boolean allowNavigate;
        private final List<PermissionCheck> checks = new ArrayList<>();

        private TestPermissionManager(boolean allowNavigate) {
            super(new TestPermissionKeys());
            this.allowNavigate = allowNavigate;
        }

        @Override
        public boolean hasPermission(
                TestSource source,
                PermissionKeys<String>.PermissionKey key,
                int defaultLevel
        ) {
            String keyName = key.getKey();
            this.checks.add(new PermissionCheck(keyName, defaultLevel));
            return !"navigate".equals(keyName) || this.allowNavigate;
        }

        @Override
        public boolean checkPlayerPermission(
                TestPlayer player,
                PermissionKeys<String>.PermissionKey key,
                int defaultLevel
        ) {
            return this.hasPermission(null, key, defaultLevel);
        }

        private boolean sawCheck(String key, int defaultLevel) {
            return this.checks.contains(new PermissionCheck(key, defaultLevel));
        }
    }

    private record PermissionCheck(String key, int defaultLevel) {
    }

    private static final class TestPermissionKeys extends PermissionKeys<String> {
        @Override
        protected PermissionKey createAddPermissionKey() {
            return new PermissionKey("add");
        }

        @Override
        protected PermissionKey createEditPermissionKey() {
            return new PermissionKey("edit");
        }

        @Override
        protected PermissionKey createRemovePermissionKey() {
            return new PermissionKey("remove");
        }

        @Override
        protected PermissionKey createNavigatePermissionKey() {
            return new PermissionKey("navigate");
        }

        @Override
        protected PermissionKey createTpPermissionKey() {
            return new PermissionKey("tp");
        }

        @Override
        protected PermissionKey createReloadPermissionKey() {
            return new PermissionKey("reload");
        }
    }

    private static final class TestNavigationPlatform implements NavigationPlatform<TestPlayer> {
        private final Map<UUID, TestPlayer> players = new HashMap<>();

        private TestPlayer addPlayer() {
            TestPlayer player = new TestPlayer(UUID.randomUUID());
            this.players.put(player.uuid(), player);
            return player;
        }

        @Override
        public UUID playerUuid(TestPlayer player) {
            return player.uuid();
        }

        @Override
        public Optional<TestPlayer> findPlayer(UUID playerUuid) {
            return Optional.ofNullable(this.players.get(playerUuid));
        }

        @Override
        public NavigationSnapshot snapshot(TestPlayer player, NavigationTarget target) {
            return NavigationMath.snapshot(
                    "overworld",
                    0.0D,
                    64.0D,
                    0.0D,
                    0.0D,
                    target
            );
        }
    }

    private static final class TestNavigationHandler
            implements TextDisplayTransformationHandler<TestPlayer> {
        private final NavigationMethod method;
        private @Nullable Vector3f lastTranslation;
        private @Nullable Quaternionf lastRotation;
        private @Nullable Vector3f lastScale;
        private int transformationCount;
        private int updateCount;
        private @Nullable NavigationTarget lastUpdatedTarget;

        private TestNavigationHandler(NavigationMethod method) {
            this.method = method;
        }

        @Override
        public NavigationMethod method() {
            return this.method;
        }

        @Override
        public NavigationResult enable(
                TestPlayer player,
                NavigationSession session,
                NavigationSnapshot snapshot
        ) {
            return NavigationResult.success();
        }

        @Override
        public void update(
                TestPlayer player,
                NavigationSession session,
                NavigationSnapshot snapshot
        ) {
            this.updateCount++;
            this.lastUpdatedTarget = session.target();
        }

        @Override
        public void disable(TestPlayer player, NavigationSession session) {
        }

        @Override
        public void applyTransformation(
                TestPlayer player,
                Vector3f translation,
                Quaternionf rotation,
                Vector3f scale
        ) {
            this.lastTranslation = new Vector3f(translation);
            this.lastRotation = new Quaternionf(rotation);
            this.lastScale = new Vector3f(scale);
            this.transformationCount++;
        }
    }

    private static final class TestMessageSender
            implements PlatformMessageSender<TestSource, TestPlayer> {
        private final List<Component> messages = new ArrayList<>();
        private final List<Component> errors = new ArrayList<>();

        @Override
        public void sendMessage(TestSource source, Component component) {
            this.messages.add(component);
        }

        @Override
        public void sendPlayerMessage(TestPlayer player, Component component) {
        }

        @Override
        public void sendError(TestSource source, Component component) {
            this.errors.add(component);
        }

        @Override
        public void sendPacket(TestSource source, MessageBuffer packet) {
        }

        @Override
        public void sendPlayerPacket(TestPlayer player, MessageBuffer packet) {
        }

        @Override
        public Iterable<? extends TestPlayer> getBroadcastPlayers(TestSource source) {
            return List.of();
        }

        @Override
        public Component getSenderName(TestSource source) {
            return Component.text("tester");
        }

        private List<String> messageKeys() {
            return this.messages.stream().map(TestMessageSender::translationKey).toList();
        }

        private List<String> errorKeys() {
            return this.errors.stream().map(TestMessageSender::translationKey).toList();
        }

        private String lastMessageKey() {
            return translationKey(this.messages.get(this.messages.size() - 1));
        }

        private String lastErrorKey() {
            return translationKey(this.errors.get(this.errors.size() - 1));
        }

        private void clear() {
            this.messages.clear();
            this.errors.clear();
        }

        private static String translationKey(Component component) {
            if (component instanceof TranslatableComponent translatableComponent) {
                return translatableComponent.key();
            }
            throw new AssertionError("Expected translatable component but got " + component);
        }
    }
}
