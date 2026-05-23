package _959.server_waypoint.common.network.payload;

//? if >= 1.20.5 {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.FabricPacket;
*///?} else if neoforge {
/*import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
*///?} else if forge {
/*import net.minecraft.network.FriendlyByteBuf;
*///?}

public interface ModPayload
//? if >= 1.20.5 {
        extends CustomPacketPayload
//?} else if fabric {
        /*extends FabricPacket
*///?} else if neoforge {
        /*extends CustomPacketPayload
*///?}
{
//? if forge
    /*void write(FriendlyByteBuf buf);*/
}
