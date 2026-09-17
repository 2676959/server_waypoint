package _959.server_waypoint.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

//? if >= 1.20.5 {
import net.minecraft.core.Holder;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
//?} else {
/*import net.minecraft.world.level.saveddata.maps.MapDecoration;
*///?}

@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataAccessor {
    @Invoker(value = "addDecoration"/*? if >= 26 {*/, remap = false/*?}*/)
    //? if >= 1.20.5 {
    void serverWaypoint$addDecoration(
            Holder<MapDecorationType> type,
            LevelAccessor level,
            String key,
            double x,
            double z,
            double rotation,
            Component name
    );
    //?} else {
    /*void serverWaypoint$addDecoration(
            MapDecoration.Type type,
            LevelAccessor level,
            String key,
            double x,
            double z,
            double rotation,
            Component name
    );
    *///?}
}
