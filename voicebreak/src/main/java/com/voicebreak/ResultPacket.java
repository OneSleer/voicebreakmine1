package com.voicebreak;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент -> сервер: итог попытки. */
public class ResultPacket {
    public static final int FAIL = 0;
    public static final int SUCCESS = 1;
    public static final int NO_MIC = 2;

    private final int status;

    public ResultPacket(int status) {
        this.status = status;
    }

    public static void encode(ResultPacket p, FriendlyByteBuf buf) {
        buf.writeByte(p.status);
    }

    public static ResultPacket decode(FriendlyByteBuf buf) {
        return new ResultPacket(buf.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ChallengeManager.onResult(player, status);
            }
        });
        context.setPacketHandled(true);
    }
}
