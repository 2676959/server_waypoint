package _959.server_waypoint.common.client.integrations;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Applies the map-mod integration mixins only when their map mod is installed. Mixin asks a config plugin about
 * each mixin before it looks the target class up, so a skipped mixin leaves no trace, whereas a missing target
 * logs a warning even for a {@code @Pseudo} mixin. Mixin loads this before Minecraft starts and, on Forge and
 * NeoForge, before {@code ModList} exists, so it must not touch game classes and asks the loader's list of
 * loading mods instead.
 */
public final class MapModMixinPlugin implements IMixinConfigPlugin {
    private static final Map<String, String> MOD_BY_MIXIN_PACKAGE = Map.of(
            "_959.server_waypoint.mixin.voxelmap.", "voxelmap",
            "_959.server_waypoint.mixin.xaeros_minimap.", "xaerominimap",
            "_959.server_waypoint.mixin.xaeros_worldmap.", "xaeroworldmap");

    private final Predicate<String> isModLoaded;

    public MapModMixinPlugin() {
        this(MapModMixinPlugin::isLoadingMod);
    }

    MapModMixinPlugin(Predicate<String> isModLoaded) {
        this.isModLoaded = isModLoaded;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        for (Map.Entry<String, String> integration : MOD_BY_MIXIN_PACKAGE.entrySet()) {
            if (mixinClassName.startsWith(integration.getKey())) {
                return this.isModLoaded.test(integration.getValue());
            }
        }
        return true;
    }

    private static boolean isLoadingMod(String modId) {
        //? if fabric {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
        //?} elif neoforge && >=1.21.9 {
        /*return net.neoforged.fml.loading.FMLLoader.getCurrent().getLoadingModList().getModFileById(modId) != null;
        *///?} elif neoforge {
        /*return net.neoforged.fml.loading.LoadingModList.get().getModFileById(modId) != null;
        *///?} elif >=26 {
        /*return net.minecraftforge.fml.loading.LoadingModList.getModFileById(modId) != null;
        *///?} else {
        /*return net.minecraftforge.fml.loading.LoadingModList.get().getModFileById(modId) != null;
        *///?}
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
