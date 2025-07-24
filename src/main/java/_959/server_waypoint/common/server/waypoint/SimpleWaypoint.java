package _959.server_waypoint.common.server.waypoint;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;

public class SimpleWaypoint {
    private String name;
    private String initials;
    private BlockPos pos;
    private int colorIdx;
    private int yaw;
    private boolean global;

    public static final PacketCodec<PacketByteBuf, SimpleWaypoint> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, SimpleWaypoint::name,
            PacketCodecs.STRING, SimpleWaypoint::initials,
            BlockPos.PACKET_CODEC, SimpleWaypoint::pos,
            PacketCodecs.INTEGER, SimpleWaypoint::colorIdx,
            PacketCodecs.INTEGER, SimpleWaypoint::yaw,
            //? if >= 1.21.5 {
            PacketCodecs.BOOLEAN,
            //?} else {
            /*PacketCodecs.BOOL,
            *///?}
            SimpleWaypoint::global,
            SimpleWaypoint::new
    );

    public SimpleWaypoint(String name, String initials, BlockPos pos, int colorIdx, int yaw, boolean global) {
        this.name = name;
        this.initials = initials;
        this.pos = pos;
        this.colorIdx = colorIdx;
        this.yaw = yaw;
        this.global = global;
    }

    public String name() {
        return this.name;
    }

    public String initials() {
        return this.initials;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public int colorIdx() {
        return this.colorIdx;
    }

    public int yaw() {
        return this.yaw;
    }

    public boolean global() {
        return this.global;
    }

    public SimpleWaypoint setName(String name) {
        this.name = name;
        return this;
    }

    public void setInitials(String initials) {
        this.initials = initials;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
    }

    public void setColorIdx(int colorIdx) {
        this.colorIdx = colorIdx;
    }

    public void setYaw(int yaw) {
        this.yaw = yaw;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }
    
    @Override
    public String toString() {
        return "SimpleWaypoint{" +
                "name='" + name + '\'' +
                ", initials='" + initials + '\'' +
                ", pos=" + pos +
                ", colorIdx=" + colorIdx +
                ", yaw=" + yaw +
                ", global=" + global +
                '}';
    }

    public boolean compareValues(String initials, BlockPos pos, int colorIdx, int yaw, boolean global) {
        return this.initials.equals(initials) &&
                this.pos.equals(pos) &&
                this.colorIdx == colorIdx &&
                this.yaw == yaw &&
                this.global == global;
    }
}