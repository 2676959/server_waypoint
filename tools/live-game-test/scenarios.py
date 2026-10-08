"""Keep gameplay assertions shared; generate only the Minecraft API differences."""
from pathlib import Path


def version(value):
    return tuple(int(part) for part in value.split("."))


def source_for(target, voxelmap, core=False):
    name = "CoreChecks.java.template" if core else "LiveChecks.java.template"
    source = Path(__file__).with_name("java").joinpath(name).read_text()
    output, skip = [], False
    for line in source.splitlines(keepends=True):
        if "// @voxel-start" in line:
            skip = not voxelmap
        elif "// @voxel-end" in line:
            skip = False
        elif not skip:
            output.append(line)
    return "".join(output)


def api_for(target, voxelmap):
    minecraft, loader = target.rsplit("-", 1)
    current = version(minecraft)
    screen = "mc.gui.screen()" if current >= (26, 2) else "mc.screen"
    version_name = "name" if current >= (1, 21, 6) else "getName"
    click = "screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), false)"
    if current < (1, 21, 9):
        click = "screen.mouseClicked(x, y, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT)"
    settings = "new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT"
    if current < (26,):
        rules = "new GameRules()" if current <= (1, 21) else "new " + (
            "net.minecraft.world.level.gamerules." if current >= (1, 21, 11) else "") + "GameRules(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS)"
        settings = "false, Difficulty.PEACEFUL, true, " + rules + ", WorldDataConfiguration.DEFAULT"
    parent = ", parent" if current >= (1, 20, 4) else ""
    load = 'mc.createWorldOpenFlows().openWorld("live-editor-verification", () -> {});'
    if current >= (1, 21, 11):
        disconnect = "mc.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);"
    elif current >= (1, 21, 6):
        disconnect = "mc.level.disconnect(net.minecraft.network.chat.Component.translatable(\"menu.disconnect\"));\n        mc.disconnect(new TitleScreen(), false);"
    elif current >= (1, 21):
        disconnect = "mc.level.disconnect();\n        mc.disconnect(new TitleScreen(), false);"
    elif current >= (1, 20, 2):
        disconnect = "mc.level.disconnect();\n        mc.disconnect(new TitleScreen());"
        load = 'mc.createWorldOpenFlows().loadLevel(new TitleScreen(), "live-editor-verification");'
    else:
        disconnect = "mc.level.disconnect();\n        mc.clearLevel(new TitleScreen());"
        load = 'mc.createWorldOpenFlows().loadLevel(new TitleScreen(), "live-editor-verification");'
    if (1, 20, 4) <= current < (1, 20, 6):
        load = 'mc.createWorldOpenFlows().checkForBackupAndLoad("live-editor-verification", () -> {});'
    elif current >= (1, 20, 6):
        load = 'mc.createWorldOpenFlows().openWorld("live-editor-verification", () -> {});'
    label = "((RightClickOption) option).getDisplayName()" + (".getString()" if current >= (26, 3) or loader == "forge" and current >= (26,) else "")
    voxel = "_959.server_waypoint.common.client.integrations.MapModIntegrations.syncNow(_959.server_waypoint.core.network.upload.UploadTarget.VOXELMAP, _959.server_waypoint.common.client.WaypointClientMod.getInstance());" if voxelmap else ""
    return f'''import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

/** Generated API adapter for {target}; assertions live in LiveChecks. */
public final class GameApi {{
    public static final boolean VOXELMAP = {str(voxelmap).lower()};

    public static String version() {{
        return net.minecraft.SharedConstants.getCurrentVersion().{version_name}();
    }}

    public static Screen screen(Minecraft mc) {{
        return {screen};
    }}

    public static boolean click(Screen screen, AbstractWidget button) {{
        double x = button.getX() + button.getWidth() / 2.0;
        double y = button.getY() + button.getHeight() / 2.0;
        return {click};
    }}

    public static void create(Minecraft mc, Screen parent) {{
        mc.createWorldOpenFlows().createFreshLevel("live-editor-verification",
                new LevelSettings("Live editor verification", GameType.CREATIVE, {settings}),
                new WorldOptions(20261007L, false, false), WorldPresets::createNormalWorldDimensions{parent});
    }}

    public static void load(Minecraft mc) {{
        {load}
    }}

    public static void disconnect(Minecraft mc) {{
        {disconnect}
    }}

    public static String label(Object option) {{
        return {label};
    }}

    public static void syncVoxel() {{
        {voxel}
    }}
}}
'''
