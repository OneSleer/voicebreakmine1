package com.voicebreak;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Серверная логика: кто сейчас должен повторить звук и кому уже разрешено действовать. */
public final class ChallengeManager {
    private static final int GRACE_TICKS = 60;

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Set<UUID> DISABLED = new HashSet<>();

    private static final class State {
        boolean pending;
        long pendingUntil;
        int tokens;
        long tokensUntil;
    }

    private ChallengeManager() {}

    /**
     * @return true, если действие разрешено прямо сейчас. Если false — действие нужно отменить;
     * при этом игроку уже отправлено задание повторить звук.
     */
    public static boolean tryConsume(ServerPlayer player, Component prompt, @Nullable SoundEvent hint) {
        UUID id = player.getUUID();
        if (DISABLED.contains(id)) {
            return true;
        }

        long now = player.serverLevel().getGameTime();
        State s = STATES.computeIfAbsent(id, k -> new State());

        if (s.tokens > 0 && now <= s.tokensUntil) {
            s.tokens--;
            return true;
        }
        s.tokens = 0;

        if (s.pending && now <= s.pendingUntil) {
            player.displayClientMessage(
                    Component.literal("Сначала повтори звук голосом!").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }

        int timeoutTicks = Config.COMMON.timeoutSeconds.get() * 20;
        s.pending = true;
        s.pendingUntil = now + timeoutTicks + GRACE_TICKS;

        String soundId = hint == null ? "" : hint.getLocation().toString();
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ChallengePacket(prompt, soundId, timeoutTicks));
        return false;
    }

    public static void onResult(ServerPlayer player, int status) {
        State s = STATES.get(player.getUUID());
        if (s == null || !s.pending) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (now > s.pendingUntil) {
            s.pending = false;
            return;
        }
        s.pending = false;

        boolean ok = status == ResultPacket.SUCCESS
                || (status == ResultPacket.NO_MIC && Config.COMMON.allowWithoutMic.get());

        if (ok) {
            s.tokens = Config.COMMON.actionsPerSuccess.get();
            s.tokensUntil = now + Config.COMMON.tokenLifetimeSeconds.get() * 20L;
            String text = status == ResultPacket.NO_MIC
                    ? "Микрофон недоступен — засчитано без проверки. Действуй!"
                    : "Звук засчитан! Действуй!";
            player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GREEN), true);
        } else {
            String text = status == ResultPacket.NO_MIC
                    ? "Микрофон недоступен. Проверьте настройки системы."
                    : "Не расслышал. Попробуй ещё раз!";
            player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.RED), true);
        }
    }

    public static void setEnabled(ServerPlayer player, boolean enabled) {
        if (enabled) {
            DISABLED.remove(player.getUUID());
        } else {
            DISABLED.add(player.getUUID());
            STATES.remove(player.getUUID());
        }
    }

    public static void forget(UUID id) {
        STATES.remove(id);
    }
}
