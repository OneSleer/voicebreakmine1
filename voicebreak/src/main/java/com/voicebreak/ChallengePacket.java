package com.voicebreak;

import com.voicebreak.client.ClientVoice;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер -> клиент: «повтори этот звук голосом». */
public class ChallengePacket {
    private final Component prompt;
    private final String soundId;
    private final int timeoutTicks;

    public ChallengePacket(Component prompt, String soundId, int timeoutTicks) {
        this.prompt = prompt;
        this.soundId = soundId;
        this.timeoutTicks = timeoutTicks;
    }

    public Component prompt() {
        return prompt;
    }

    /** Идентификатор звука-подсказки или пустая строка. */
    public String soundId() {
        return soundId;
    }

    public int timeoutTicks() {
        return timeoutTicks;
    }

    public static void encode(ChallengePacket p, FriendlyByteBuf buf) {
        buf.writeComponent(p.prompt);
        buf.writeUtf(p.soundId, 256);
        buf.writeVarInt(p.timeoutTicks);
    }

    public static ChallengePacket decode(FriendlyByteBuf buf) {
        Component prompt = buf.readComponent();
        String soundId = buf.readUtf(256);
        int timeoutTicks = buf.readVarInt();
        return new ChallengePacket(prompt, soundId, timeoutTicks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientVoice.start(this)));
        ctx.get().setPacketHandled(true);
    }
}
