package com.example.elevatorsandmore.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record FloorInfo(
        BlockPos stationPos,
        String floorName,
        int yLevel,
        boolean isElevatorDocked
) {
    public static final StreamCodec<ByteBuf, FloorInfo> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            FloorInfo::stationPos,
            ByteBufCodecs.stringUtf8(32),
            FloorInfo::floorName,
            ByteBufCodecs.VAR_INT,
            FloorInfo::yLevel,
            ByteBufCodecs.BOOL,
            FloorInfo::isElevatorDocked,
            FloorInfo::new
    );
}
