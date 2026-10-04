package _959.server_waypoint.command;

import _959.server_waypoint.core.logging.PlayerActionLog;
import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.config.Config;
import _959.server_waypoint.crossserver.catalog.CatalogIndex;
import _959.server_waypoint.crossserver.catalog.CatalogCacheLimits;
import _959.server_waypoint.crossserver.catalog.RemoteCatalogStore;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.RemoteRevision;
import _959.server_waypoint.crossserver.RemoteCatalogSnapshot;
import _959.server_waypoint.crossserver.transport.TransportMode;
import _959.server_waypoint.crossserver.CatalogExportPolicy;
import _959.server_waypoint.crossserver.protocol.ProtocolLimits;
import _959.server_waypoint.crossserver.protocol.ApplicationCodec;
import _959.server_waypoint.crossserver.protocol.ApplicationEnvelope;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage;
import _959.server_waypoint.crossserver.transport.TcpChannel;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageDelivery;
import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.upload.UploadCoordinator;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.util.NamespacedId;
import _959.server_waypoint.util.StringCommandBuilder;
import _959.server_waypoint.navigation.NavigationPlatform;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.NavigationSnapshot;
import _959.server_waypoint.navigation.NavigationTarget;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.core.network.upload.UploadStatus;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreWaypointCommandListTest {
    @TempDir
    private Path tempDir;
    private CommandDispatcher<TestSource> dispatcher;
    private TestMessageSender sender;
    private TestSource source;
    private WaypointServerCore server;
    private Config originalConfig;

    @BeforeEach
    void setUp() {
        this.originalConfig = WaypointServerCore.CONFIG;
        this.server = new WaypointServerCore(this.tempDir) {
            @Override
            protected boolean isRegisteredIconItem(NamespacedId icon) {
                return List.of("minecraft:diamond", "mod:blue_gem").contains(icon.toString());
            }
        };
        this.server.putWaypointList(
                "overworld",
                new WaypointList("bases", 1, waypoints("base", 12))
        );
        this.server.putWaypointList("overworld", new WaypointList(
                "search",
                1,
                waypoints("reserved list waypoint", 12)
        ));
        this.server.putWaypointList("overworld", new WaypointList(
                "",
                1,
                waypoints("empty-name list waypoint", 12)
        ));

        this.sender = new TestMessageSender();
        this.source = new TestSource("overworld", new WaypointPos(0, 64, 0));
        this.dispatcher = new CommandDispatcher<>();
        new TestWaypointCommand(this.server, this.sender).register(this.dispatcher);
    }

    @AfterEach
    void tearDown() {
        WaypointServerCore.CONFIG = this.originalConfig;
    }

    @Test
    void keyGenerationIsStandaloneAndOldCrossServerNodeIsRemoved() {
        assertNotNull(this.dispatcher.getRoot().getChild("sw-cross-server-keygen"));
        assertNull(this.dispatcher.getRoot().getChild("wp").getChild("cross-server"));
        assertThrows(CommandSyntaxException.class,
                () -> this.dispatcher.execute("wp cross-server generate-key", this.source));
    }

    @Test
    void keyGenerationRequiresHighestPermissionServerConsole() {
        assertThrows(CommandSyntaxException.class,
                () -> this.dispatcher.execute("sw-cross-server-keygen", this.source));
        assertFalse(java.nio.file.Files.exists(this.tempDir.resolve("credentials/static.key")));
    }

    @Test
    void standaloneKeyGenerationExecutesForAuthorizedConsole() throws Exception {
        var command = new TestWaypointCommand(
                new WaypointServerCore(this.tempDir.toRealPath()) {
                    @Override
                    protected boolean isRegisteredIconItem(NamespacedId icon) {
                        return false;
                    }
                }, this.sender);
        command.authorizedConsole = true;
        var consoleDispatcher = new CommandDispatcher<TestSource>();
        command.register(consoleDispatcher);
        assertEquals(1, consoleDispatcher.execute("sw-cross-server-keygen", this.source));
        assertTrue(java.nio.file.Files.exists(this.tempDir.resolve("credentials/static.key")));
        assertTrue(java.nio.file.Files.exists(this.tempDir.resolve("cross-server-public-key.txt")));
    }

    @Test
    void sharedRootReadsAttachedRemoteStoreWithoutChangingLocalLists() throws Exception {
        var index = new CatalogIndex(
                CatalogCacheLimits.DEFAULT);
        var id = new RemoteServerId("remote-only");
        var revision = new RemoteRevision(1);
        Object owner = new Object();
        index.connected(id, owner, TransportMode.NOISE_KK,
                ProtocolLimits.DEFAULT);
        var snapshot = new RemoteCatalogSnapshot(id, revision, java.util.Map.of(), java.time.Instant.EPOCH);
        var codec = new ApplicationCodec(ProtocolLimits.DEFAULT);
        byte[] bytes = codec.encodeCatalog(snapshot); UUID request = UUID.randomUUID();
        index.receive(id, owner, new TcpChannel.Received(
                new ApplicationEnvelope(0, request,
                        new ApplicationMessage.CatalogMetadata(id, "Remote only", revision,
                                CatalogExportPolicy.PUBLIC, "minecraft:compass")), null));
        index.receive(id, owner, new TcpChannel.Received(
                new ApplicationEnvelope(1, request,
                        new ApplicationMessage.CatalogSnapshot(id, revision, UUID.randomUUID(), 0,
                                bytes.length, new ApplicationMessage.Bytes(bytes))), snapshot));
        this.server.setRemoteCatalogStore(new RemoteCatalogStore(index));
        this.dispatcher.execute("wp remote", this.source);
        assertTrue(plainText(lastMessage()).contains("Remote only"));
        this.dispatcher.execute("wp help remote", this.source);
        assertTrue(plainText(lastMessage()).contains("/wp remote list"));
        this.dispatcher.execute("wp list overworld bases", this.source);
        assertTrue(plainText(lastMessage()).contains("base"));
        assertFalse(plainText(lastMessage()).contains("remote-only"));
        assertNull(this.server.getWaypointFileManager("remote-only"));
    }

    @Test
    void downloadReportsSuccessOnlyAfterFinalDelivery() throws CommandSyntaxException {
        CompletableFuture<ChunkedMessageSendResult> completion = new CompletableFuture<>();
        this.sender.chunkedDelivery = ChunkedMessageDelivery.queued(completion);

        this.dispatcher.execute("wp download", this.source);

        assertTrue(this.sender.messages.isEmpty());
        completion.complete(ChunkedMessageSendResult.DELIVERED);
        assertTrue(translationKeys(lastMessage()).contains("wp.download.sent"));
    }

    @Test
    void downloadReportsAsynchronousDeliveryFailure() throws CommandSyntaxException {
        CompletableFuture<ChunkedMessageSendResult> completion = new CompletableFuture<>();
        this.sender.chunkedDelivery = ChunkedMessageDelivery.queued(completion);

        this.dispatcher.execute("wp download", this.source);
        completion.complete(ChunkedMessageSendResult.DELIVERY_FAILED);

        assertTrue(translationKeys(
                this.sender.errors.get(this.sender.errors.size() - 1)
        ).contains("wp.error.delivery"));
    }

    @Test
    void keepsOldListScopesAndAcceptsCombinedQueryOptions() {
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list all", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list overworld", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list overworld bases", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list overworld search", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list overworld \"search\"", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list all search base sort name order descending page 1 limit 5",
                this.source
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list search base sort distance page 1 limit 5",
                this.source
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list overworld sort color order descending limit 5",
                this.source
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list overworld bases search base sort name page 2 limit 5",
                this.source
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list all sort name order descending search base",
                this.source
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list all sort distance order ascending page 1 limit 5 search base",
                this.source
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list all view flat", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp list all sort name order descending view flat search base",
                this.source
        ));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list all sort default", this.source));
        assertDoesNotThrow(() -> this.dispatcher.execute("wp list all limit 20", this.source));
        assertThrows(
                CommandSyntaxException.class,
                () -> this.dispatcher.execute("wp list all limit 101", this.source)
        );
    }

    @Test
    void addUsesExactIdentifierAndPatchEditSetsDisplayName() throws CommandSyntaxException {
        String name = "{\"text\":\"Golden Beacon\",\"color\":\"gold\"}";
        String description = "{\"text\":\"Near spawn\",\"italic\":true}";
        String commandPrefix = "wp add overworld bases position "
                + StringArgumentType.escapeIfRequired(name)
                + " GB FFAA00 45 true";

        List<String> initialsSuggestions = this.dispatcher.getCompletionSuggestions(
                        this.dispatcher.parse(
                                "wp add overworld bases position "
                                        + StringArgumentType.escapeIfRequired(name)
                                        + " ",
                                this.source
                        )
                ).join().getList().stream()
                .map(suggestion -> suggestion.getText())
                .toList();
        assertTrue(initialsSuggestions.contains("GB"));

        this.dispatcher.execute(
                commandPrefix
                        + " " + StringArgumentType.escapeIfRequired("home, mining")
                        + " " + StringArgumentType.escapeIfRequired(description),
                this.source
        );

        WaypointList bases = this.server.getWaypointFileManager("overworld").getWaypointListByName("bases");
        assertNotNull(bases);
        SimpleWaypoint waypoint = bases.getWaypointByName(name);
        assertNotNull(waypoint);
        assertEquals(name, waypoint.name());
        assertEquals(name, waypoint.displayName());
        assertFalse(waypoint.hasDisplayNameOverride());
        assertEquals(List.of("home", "mining"), waypoint.keywords());
        assertEquals(description, waypoint.description());

        this.dispatcher.execute(
                "wp edit waypoint overworld bases " + StringArgumentType.escapeIfRequired(name)
                        + " set display-name \"Golden Beacon\"",
                this.source
        );
        assertEquals("Golden Beacon", bases.getWaypointByName(name).displayName());
        assertTrue(bases.getWaypointByName(name).hasDisplayNameOverride());

        this.dispatcher.execute(
                "wp add overworld bases position \"Legacy Marker\" LM FFAA00 45 true",
                this.source
        );
        SimpleWaypoint plainWaypoint = bases.getWaypointByName("Legacy Marker");
        assertNotNull(plainWaypoint);
        assertEquals("Legacy Marker", plainWaypoint.displayName());
        assertEquals(List.of(), plainWaypoint.keywords());
        assertEquals("", plainWaypoint.description());
    }

    @Test
    void addRejectsCaseInsensitiveDuplicateKeywords() throws CommandSyntaxException {
        this.dispatcher.execute(
                "wp add overworld bases position duplicate D FFAA00 0 true \"home, HOME\"",
                this.source
        );

        WaypointList bases = this.server.getWaypointFileManager("overworld").getWaypointListByName("bases");
        assertNull(bases.getWaypointByName("duplicate"));
        assertEquals(1, this.sender.errors.size());
        assertTrue(this.sender.errors.get(0).toString().contains("wp.error.keywords.duplicate"));
    }

    @Test
    void addSetAndClearIconThroughCommandTree() throws CommandSyntaxException {
        this.dispatcher.execute(
                "wp add overworld bases position icon-test I FFAA00 0 true \"\" \"\" icon minecraft:diamond",
                this.source);
        WaypointList bases = this.server.getWaypointFileManager("overworld").getWaypointListByName("bases");
        assertEquals("minecraft:diamond", bases.getWaypointByName("icon-test").icon().toString());

        this.dispatcher.execute("wp edit waypoint overworld bases icon-test set icon voxelmap:star", this.source);
        assertEquals("voxelmap:star", bases.getWaypointByName("icon-test").icon().toString());

        assertThrows(CommandSyntaxException.class, () -> this.dispatcher.execute(
                "wp edit waypoint overworld bases icon-test set icon Minecraft:Diamond", this.source));
        assertEquals("voxelmap:star", bases.getWaypointByName("icon-test").icon().toString());

        this.dispatcher.execute("wp edit waypoint overworld bases icon-test clear icon", this.source);
        assertNull(bases.getWaypointByName("icon-test").icon());
    }

    @Test
    void addIconWorksWithBothFullFormsAndRejectsInvalidId() throws CommandSyntaxException {
        this.dispatcher.execute("wp add position bases local-icon L FFAA00 0 true icon minecraft:diamond", this.source);
        this.dispatcher.execute("wp add overworld bases position remote-icon R FFAA00 0 true \"home\" icon voxelmap:star", this.source);
        assertThrows(CommandSyntaxException.class, () -> this.dispatcher.execute(
                "wp add position bases invalid-icon I FFAA00 0 true icon Minecraft:Diamond", this.source));

        WaypointList bases = this.server.getWaypointFileManager("overworld").getWaypointListByName("bases");
        assertEquals("minecraft:diamond", bases.getWaypointByName("local-icon").icon().toString());
        assertEquals("voxelmap:star", bases.getWaypointByName("remote-icon").icon().toString());
        assertNull(bases.getWaypointByName("invalid-icon"));
    }

    @Test
    void unknownIconsAreRejectedWithoutCreatingOrEditingWaypoints() throws CommandSyntaxException {
        WaypointList bases = this.server.getWaypointFileManager("overworld").getWaypointListByName("bases");
        int revision = bases.getSyncNum();
        for (String icon : List.of("minecraft:missing_item", "minecraft:air", "mod:missing_item", "voxelmap:missing_icon")) {
            this.dispatcher.execute("wp add position bases invalid I FFAA00 0 true icon " + icon, this.source);
            assertNull(bases.getWaypointByName("invalid"));
            this.dispatcher.execute("wp edit waypoint overworld bases \"base 1\" set icon " + icon, this.source);
            assertNull(bases.getWaypointByName("base 1").icon());
            assertEquals(revision, bases.getSyncNum());
            assertTrue(translationKeys(this.sender.errors.get(this.sender.errors.size() - 1))
                    .contains("wp.error.icon"));
        }
    }

    @Test
    void iconArgumentUsesPlatformParserAndCanonicalId() throws CommandSyntaxException {
        this.dispatcher.execute("wp add position bases native-icon N FFAA00 0 true icon diamond", this.source);

        WaypointList bases = this.server.getWaypointFileManager("overworld").getWaypointListByName("bases");
        assertEquals("minecraft:diamond", bases.getWaypointByName("native-icon").icon().toString());
    }

    @Test
    void generatedAddCommandCreatesWaypointWithIcon() throws CommandSyntaxException {
        SimpleWaypoint waypoint = new SimpleWaypoint("generated-icon", "generated-icon", "G",
                new WaypointPos(1, 64, 2), 0xFFFFFF, 0, true, List.of(), "",
                NamespacedId.parse("minecraft:diamond"));
        String generated = StringCommandBuilder.addCmd("overworld", "bases", waypoint, false);
        assertTrue(generated.endsWith("icon minecraft:diamond"));
        // This fixture accepts a single position token; production loaders parse three coordinates.
        this.dispatcher.execute(generated.replace(" 1 64 2 ", " position "), this.source);

        WaypointList bases = this.server.getWaypointFileManager("overworld").getWaypointListByName("bases");
        assertEquals(waypoint.icon(), bases.getWaypointByName("generated-icon").icon());
    }

    @Test
    void iconArgumentsSuggestAvailableItemAndVoxelMapIds() {
        List<String> editSuggestions = this.dispatcher.getCompletionSuggestions(this.dispatcher.parse(
                "wp edit waypoint overworld bases base0 set icon diam", this.source)).join().getList()
                .stream().map(suggestion -> suggestion.getText()).toList();
        assertTrue(editSuggestions.contains("minecraft:diamond"));
        assertFalse(editSuggestions.contains("voxelmap:star"));

        List<String> addSuggestions = this.dispatcher.getCompletionSuggestions(this.dispatcher.parse(
                "wp add overworld bases position new N FFAA00 0 true icon voxelmap:st", this.source))
                .join().getList().stream().map(suggestion -> suggestion.getText()).toList();
        assertTrue(addSuggestions.contains("voxelmap:star"));

        List<String> customSuggestions = this.dispatcher.getCompletionSuggestions(this.dispatcher.parse(
                "wp add position bases new N FFAA00 0 true \"\" icon mod:blue", this.source))
                .join().getList().stream().map(suggestion -> suggestion.getText()).toList();
        assertTrue(customSuggestions.contains("mod:blue_gem"));

        List<String> descriptionSuggestions = this.dispatcher.getCompletionSuggestions(this.dispatcher.parse(
                "wp add position bases new N FFAA00 0 true \"\" \"\" icon voxelmap:st", this.source))
                .join().getList().stream().map(suggestion -> suggestion.getText()).toList();
        assertTrue(descriptionSuggestions.contains("voxelmap:star"));
    }

    @Test
    void renameFeedbackBuildsDetailsControlsFromTheAfterSnapshot() throws CommandSyntaxException {
        this.dispatcher.execute("wp add overworld bases position old O FFAA00 0 true", this.source);
        this.sender.messages.clear();

        this.dispatcher.execute(
                "wp edit waypoint overworld bases old set identifier \"new identifier\"",
                this.source
        );

        Component details = lastMessage();
        assertTrue(plainText(details).contains("new identifier"));
        List<String> commands = new ArrayList<>();
        commands.addAll(runCommands(details));
        commands.addAll(suggestedCommands(details));
        assertTrue(commands.stream().anyMatch(command -> command.contains("\"new identifier\"")));
        assertFalse(commands.stream().anyMatch(command -> command.contains(" old ")));
    }

    @Test
    void addListStoresTheExactIdentifierWithoutDisplayNameOverride() throws CommandSyntaxException {
        String identifier = "{\"text\":\"Travel Hubs\",\"color\":\"aqua\"}";

        this.dispatcher.execute(
                "wp add overworld " + StringArgumentType.escapeIfRequired(identifier),
                this.source
        );

        WaypointList waypointList = this.server.getWaypointFileManager("overworld")
                .getWaypointListByName(identifier);
        assertNotNull(waypointList);
        assertEquals(identifier, waypointList.name());
        assertEquals(identifier, waypointList.displayName());
        assertFalse(waypointList.hasDisplayNameOverride());
    }

    @Test
    void emptyIdentifiersRoundTripThroughAddAndDetailsCommands() throws CommandSyntaxException {
        this.dispatcher.execute("wp add overworld \"\"", this.source);
        this.dispatcher.execute("wp add overworld \"\" position \"\" E FFAA00 0 true", this.source);

        WaypointList list = this.server.getWaypointFileManager("overworld")
                .getWaypointListByName("");
        assertNotNull(list);
        assertNotNull(list.getWaypointByName(""));
        assertFalse(list.hasDisplayNameOverride());
        assertFalse(list.getWaypointByName("").hasDisplayNameOverride());
        assertDoesNotThrow(() -> this.dispatcher.execute(
                "wp details waypoint overworld \"\" \"\"",
                this.source
        ));
    }

    @ParameterizedTest
    @EnumSource(UploadTarget.class)
    void localUploadAppliesWithoutNetworkCapability(UploadTarget target) throws CommandSyntaxException {
        TestWaypointCommand command = new TestWaypointCommand(this.server, this.sender);
        command.player = new Object();
        command.localUpload = true;
        this.sender.capable = false;
        CommandDispatcher<TestSource> localDispatcher = new CommandDispatcher<>();
        command.register(localDispatcher);

        localDispatcher.execute("wp upload " + target.name().toLowerCase(java.util.Locale.ROOT)
                + " overworld", this.source);

        assertEquals(target, command.collectedTarget);
        assertNotNull(this.server.getWaypointFileManager("overworld").getWaypointListByName("imported"));
        assertEquals(0, this.sender.sentPackets);
        assertTrue(this.sender.errors.isEmpty());
    }

    @Test
    void remoteUploadStillRequiresNetworkCapability() throws CommandSyntaxException {
        TestWaypointCommand command = new TestWaypointCommand(this.server, this.sender);
        command.player = new Object();
        this.sender.capable = false;
        CommandDispatcher<TestSource> remoteDispatcher = new CommandDispatcher<>();
        command.register(remoteDispatcher);

        remoteDispatcher.execute("wp upload xaero overworld", this.source);

        assertTrue(translationKeys(this.sender.errors.get(0)).contains("wp.error.upload.no_mod"));
        assertNull(command.collectedTarget);
        assertEquals(0, this.sender.sentPackets);
    }

    @Test
    void uploadSuggestsSupportedTargetsAndRejectsOthers() throws CommandSyntaxException {
        List<String> suggestions = this.dispatcher.getCompletionSuggestions(
                        this.dispatcher.parse("wp upload ", this.source)
                ).join().getList().stream()
                .map(suggestion -> suggestion.getText())
                .toList();

        assertEquals(2, suggestions.size());
        assertTrue(suggestions.containsAll(List.of("xaero", "voxelmap")));

        this.dispatcher.execute("wp upload unsupported", this.source);
        assertTrue(translationKeys(this.sender.errors.get(0))
                .contains("wp.error.upload.source"));
    }

    @Test
    void addAcceptsLongIdentifiersButRejectsDescriptionsOverTheirLimits() throws CommandSyntaxException {
        this.dispatcher.execute("wp add overworld " + "l".repeat(257), this.source);

        assertEquals(0, this.sender.errors.size());
        assertNotNull(this.server.getWaypointFileManager("overworld")
                .getWaypointListByName("l".repeat(257)));

        this.sender.errors.clear();
        this.dispatcher.execute(
                "wp add overworld bases position marker M FFAA00 0 true \"\" " + "d".repeat(2049),
                this.source
        );

        assertEquals(1, this.sender.errors.size());
        assertTrue(this.sender.errors.get(0).toString().contains("wp.error.too_long"));
        assertNull(this.server.getWaypointFileManager("overworld")
                .getWaypointListByName("bases")
                .getWaypointByName("marker"));
    }

    private Component lastMessage() {
        return this.sender.messages.get(this.sender.messages.size() - 1);
    }

    private static List<SimpleWaypoint> waypoints(String prefix, int count) {
        List<SimpleWaypoint> waypoints = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            waypoints.add(waypoint(prefix + " " + index, index));
        }
        return waypoints;
    }

    private static SimpleWaypoint waypoint(String name, int x) {
        return new SimpleWaypoint(name, name.substring(0, 1), new WaypointPos(x, 64, 0), 0xFFFFFF, 0, false);
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

    private static List<String> runCommands(Component component) {
        List<String> commands = new ArrayList<>();
        collectRunCommands(component, commands);
        return commands;
    }

    private static void assertClicksOnlyOnLeaves(Component component) {
        if (component.clickEvent() != null) {
            assertTrue(component.children().isEmpty());
        }
        component.children().forEach(CoreWaypointCommandListTest::assertClicksOnlyOnLeaves);
    }

    private static List<String> suggestedCommands(Component component) {
        List<String> commands = new ArrayList<>();
        collectSuggestedCommands(component, commands);
        return commands;
    }

    private static void collectSuggestedCommands(Component component, List<String> commands) {
        ClickEvent clickEvent = component.clickEvent();
        if (clickEvent != null && clickEvent.action() == ClickEvent.Action.SUGGEST_COMMAND) {
            commands.add(clickEvent.value());
        }
        for (Component child : component.children()) {
            collectSuggestedCommands(child, commands);
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
        if (component.hoverEvent() != null
                && component.hoverEvent().action() == net.kyori.adventure.text.event.HoverEvent.Action.SHOW_TEXT) {
            Object hoverValue = component.hoverEvent().value();
            if (hoverValue instanceof Component hoverComponent) {
                collectTranslationKeys(hoverComponent, keys);
            }
        }
        for (Component child : component.children()) {
            collectTranslationKeys(child, keys);
        }
    }

    private static void collectRunCommands(Component component, List<String> commands) {
        ClickEvent clickEvent = component.clickEvent();
        if (clickEvent != null && clickEvent.action() == ClickEvent.Action.RUN_COMMAND) {
            commands.add(clickEvent.value());
        }
        for (Component child : component.children()) {
            collectRunCommands(child, commands);
        }
    }

    private record TestSource(String dimensionName, WaypointPos position) {
    }

    private static final class TestWaypointCommand
            extends CoreWaypointCommand<TestSource, String, Object, String, String, NamespacedId> {
        @Override
        protected NamespacedId toIconId(NamespacedId iconArgument) {
            return iconArgument;
        }

        @Override
        protected CompletableFuture<Suggestions> suggestIconIds(CommandContext<TestSource> context,
                                                                  SuggestionsBuilder builder) {
            String remaining = builder.getRemaining();
            for (String id : List.of("minecraft:diamond", "mod:blue_gem", "voxelmap:star")) {
                if (id.startsWith(remaining) || !remaining.contains(":")
                        && id.substring(id.indexOf(':') + 1).startsWith(remaining)) {
                    builder.suggest(id);
                }
            }
            return builder.buildFuture();
        }

        private static ArgumentType<NamespacedId> iconArgument() {
            return reader -> {
                int start = reader.getCursor();
                while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
                    reader.skip();
                }
                String value = reader.getString().substring(start, reader.getCursor());
                try {
                    return NamespacedId.parse(value.contains(":") ? value : "minecraft:" + value);
                } catch (IllegalArgumentException invalid) {
                    throw new SimpleCommandExceptionType(() -> "Invalid icon ID").createWithContext(reader);
                }
            };
        }

        @Override
        protected boolean isServerConsoleWithHighestPermission(TestSource source) {
            return this.authorizedConsole;
        }

        private boolean authorizedConsole;
        private Object player;
        private boolean localUpload;
        private UploadTarget collectedTarget;

        @Override
        protected boolean usesLocalUpload(TestSource source, Object player) {
            return this.localUpload;
        }

        @Override
        protected java.util.concurrent.CompletionStage<ChunkedMessageSendResult> dispatchUpload(
                TestSource source, Object player, UploadRequestBuffer request,
                java.util.function.Consumer<WaypointData> receiver
        ) {
            if (!this.localUpload) {
                return super.dispatchUpload(source, player, request, receiver);
            }
            this.collectedTarget = request.target();
            receiver.accept(WaypointData.upload(request.requestId(), UploadStatus.SUCCESS, List.of(
                    new DimensionWaypointData("overworld", List.of(
                            new WaypointList("imported", WaypointList.SERVER_N, waypoints("local", 1))
                    ))
            )));
            return CompletableFuture.completedFuture(ChunkedMessageSendResult.DELIVERED);
        }

        private TestWaypointCommand(WaypointServerCore server, TestMessageSender sender) {
            this(server, sender, permissionManager(true));
        }

        private TestWaypointCommand(
                WaypointServerCore server,
                TestMessageSender sender,
                PermissionManager<TestSource, String, Object> permissionManager
        ) {
            super(
                    server,
                    sender,
                    permissionManager,
                    navigationService(),
                    new UploadCoordinator<>(
                            server,
                            (player, message) -> {
                            },
                            packet -> {
                            },
                            player -> true,
                            player -> true,
                            navigationService(),
                            player -> new PlayerActionLog.Actor(new UUID(0L, 0L), "player")
                    ),
                    StringArgumentType::string,
                    StringArgumentType::string,
                    TestWaypointCommand::iconArgument
            );
        }

        private static NavigationService<Object> navigationService() {
            NavigationPlatform<Object> platform = new NavigationPlatform<>() {
                @Override
                public UUID playerUuid(Object player) {
                    return new UUID(0, 0);
                }

                @Override
                public void executePlayer(UUID playerUuid, java.util.function.Consumer<Object> action) {
                }

                @Override
                public NavigationSnapshot snapshot(Object player, NavigationTarget target) {
                    return NavigationSnapshot.wrongDimension();
                }
            };
            return new NavigationService<>(platform, List.of());
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
            return true;
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
            return 0;
        }

        @Override
        protected Object getPlayer(TestSource source) {
            return this.player;
        }

        @Override
        protected String getPlayerName(Object player) {
            return "player";
        }

        @Override
        protected Component getPlayerDisplayName(Object player) {
            return Component.text(getPlayerName(player));
        }

        @Override
        protected java.util.concurrent.CompletionStage<Boolean> teleportPlayer(
                TestSource source,
                Object player,
                String dimensionArgument,
                WaypointPos pos,
                int yaw
        ) {
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        }

        @Override
        protected Message getMessageFromComponent(Component component) {
            return component::toString;
        }

        @Override
        protected List<String> getAvailableDimensionNames(TestSource source) {
            return List.of("overworld");
        }

        @Override
        protected java.util.Map<String, String> getDimensionTypes(TestSource source) {
            return java.util.Map.of("overworld", "minecraft:overworld");
        }

        private static PermissionManager<TestSource, String, Object> permissionManager(
                boolean allowPrivilegedCommands
        ) {
            PermissionKeys<String> keys = new PermissionKeys<>() {
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

                @Override
                protected PermissionKey createUploadPermissionKey() {
                    return new PermissionKey("upload");
                }

                @Override
                protected PermissionKey createUploadDeletePermissionKey() {
                    return new PermissionKey("upload.delete");
                }

                @Override
                protected PermissionKey createRemoteListPermissionKey() {
                    return new PermissionKey("remote.list");
                }

                @Override
                protected PermissionKey createRemoteTpPermissionKey() {
                    return new PermissionKey("remote.tp");
                }
            };
            return new PermissionManager<>(keys) {
                @Override
                public boolean hasPermission(
                        TestSource source,
                        PermissionKeys<String>.PermissionKey key,
                        int defaultLevel
                ) {
                    return allowPrivilegedCommands;
                }

                @Override
                public boolean checkPlayerPermission(
                        Object player,
                        PermissionKeys<String>.PermissionKey key,
                        int defaultLevel
                ) {
                    return allowPrivilegedCommands;
                }
            };
        }
    }

    private static final class TestMessageSender implements PlatformMessageSender<TestSource, Object> {
        private final List<Component> messages = new ArrayList<>();
        private final List<Component> errors = new ArrayList<>();
        private boolean capable = true;
        private int sentPackets;

        @Override
        public boolean canSendChunkedMessage(Object player) {
            return this.capable;
        }
        private ChunkedMessageDelivery chunkedDelivery = ChunkedMessageDelivery.rejected(
                ChunkedMessageSendResult.UNSUPPORTED
        );

        @Override
        public void sendMessage(TestSource source, Component component) {
            this.messages.add(component);
        }

        @Override
        public void sendPlayerMessage(Object player, Component component) {
        }

        @Override
        public void sendError(TestSource source, Component component) {
            this.errors.add(component);
        }

        @Override
        public void sendPacket(TestSource source, SinglePacketMessage message) {
        }

        @Override
        public void sendPlayerPacket(Object player, SinglePacketMessage message) {
            this.sentPackets++;
        }

        @Override
        public void broadcastPacket(SinglePacketMessage message) {
        }

        @Override
        public ChunkedMessageDelivery sendChunkedMessage(
                TestSource source,
                ChunkedMessage message
        ) {
            return this.chunkedDelivery;
        }

        @Override
        public boolean isPlainTextReceiver(TestSource source) {
            return false;
        }

        @Override
        public Iterable<?> getBroadcastPlayers(TestSource source) {
            return List.of();
        }

        @Override
        public PlayerActionLog.Actor playerActor(Object player) {
            return new PlayerActionLog.Actor(new UUID(0L, 0L), "player");
        }

        @Override
        public PlayerActionLog.Actor commandSenderActor(TestSource source) {
            return new PlayerActionLog.Actor(null, "Server");
        }

        @Override
        public Component getSenderName(TestSource source) {
            return Component.text("tester");
        }
    }
}
