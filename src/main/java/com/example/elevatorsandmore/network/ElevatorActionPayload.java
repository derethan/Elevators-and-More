package com.example.elevatorsandmore.network;

import com.example.elevatorsandmore.ElevatorsAndMore;
import com.example.elevatorsandmore.util.ElevatorShaftHelper;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ElevatorActionPayload(
        BlockPos originPos,
        int actionType,
        int targetY,
        String newName
) implements CustomPacketPayload {

    public static final int ACTION_TRAVEL = 0;
    public static final int ACTION_CALL = 1;
    public static final int ACTION_RENAME = 2;
    public static final int ACTION_EMERGENCY_STOP = 3;

    public static final Type<ElevatorActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ElevatorsAndMore.MODID, "elevator_action"));

    public static final StreamCodec<ByteBuf, ElevatorActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ElevatorActionPayload::originPos,
            ByteBufCodecs.VAR_INT,
            ElevatorActionPayload::actionType,
            ByteBufCodecs.VAR_INT,
            ElevatorActionPayload::targetY,
            ByteBufCodecs.stringUtf8(32),
            ElevatorActionPayload::newName,
            ElevatorActionPayload::new
    );

    @Override
    public Type<ElevatorActionPayload> type() {
        return TYPE;
    }

    public static void handle(ElevatorActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ElevatorShaftHelper.handlePlayerAction(serverPlayer, payload);
            }
        });
    }
}
