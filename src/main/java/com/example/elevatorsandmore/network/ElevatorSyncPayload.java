package com.example.elevatorsandmore.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.example.elevatorsandmore.ElevatorsAndMore;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ElevatorSyncPayload(
        BlockPos originPos,
        BlockPos railPos,
        List<FloorInfo> floors,
        int currentElevatorY,
        boolean isMoving
) implements CustomPacketPayload {

    public static final Type<ElevatorSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ElevatorsAndMore.MODID, "elevator_sync"));

    public static final StreamCodec<ByteBuf, ElevatorSyncPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ElevatorSyncPayload::originPos,
            BlockPos.STREAM_CODEC,
            ElevatorSyncPayload::railPos,
            ByteBufCodecs.collection(ArrayList::new, FloorInfo.STREAM_CODEC),
            ElevatorSyncPayload::floors,
            ByteBufCodecs.VAR_INT,
            ElevatorSyncPayload::currentElevatorY,
            ByteBufCodecs.BOOL,
            ElevatorSyncPayload::isMoving,
            ElevatorSyncPayload::new
    );

    public static @Nullable Consumer<ElevatorSyncPayload> CLIENT_HANDLER = null;

    @Override
    public Type<ElevatorSyncPayload> type() {
        return TYPE;
    }

    public static void handle(ElevatorSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (CLIENT_HANDLER != null) {
                CLIENT_HANDLER.accept(payload);
            }
        });
    }
}
