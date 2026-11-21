package com.voicebreak.client;

import com.voicebreak.ChallengePacket;
import com.voicebreak.Config;
import com.voicebreak.Net;
import com.voicebreak.ResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Клиент: проигрывает звук-подсказку, слушает микрофон и сообщает серверу результат.
 * Распознаётся не содержание звука, а то, что вы издавали звук достаточно долго и громко.
 */
public final class ClientVoice {
    private static final int SAMPLE_RATE = 16000;
    private static final int CHUNK_BYTES = SAMPLE_RATE / 50 * 2; // 20 мс, 16 бит, моно
    private static final int CHUNK_MS = 20;
    private static final int HINT_DELAY_MS = 450;   // пока звучит подсказка, микрофон игнорируем
    private static final int CALIBRATION_MS = 250;  // замер фонового шума

    private static final AtomicInteger GENERATION = new AtomicInteger();

    // Состояние для отрисовки (читается главным потоком, пишется потоком микрофона)
    public static volatile Component prompt = Component.empty();
    public static volatile Component status = Component.empty();
    public static volatile int statusColor = 0xFFFFFF;
    public static volatile float level = 0f;
    public static volatile float progress = 0f;
    public static volatile long showUntilMs = 0L;

    private ClientVoice() {}

    public static void start(ChallengePacket packet) {
        stop();
        final int gen = GENERATION.incrementAndGet();
        Minecraft mc = Minecraft.getInstance();

        boolean hint = false;
        String soundId = packet.soundId();
        if (!soundId.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(soundId);
            if (rl != null) {
                mc.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(rl), 1.0F, 1.0F));
                hint = true;
            }
        }

        prompt = packet.prompt();
        status = Component.literal("Слушаю… говори в микрофон");
        statusColor = 0xFFFF55;
        level = 0f;
        progress = 0f;
        showUntilMs = Long.MAX_VALUE;

        final long timeoutMs = packet.timeoutTicks() * 50L;
        final int minVoicedMs = Config.CLIENT.minVoicedMillis.get();
        final double threshold = Config.CLIENT.micThreshold.get();
        final int startDelayMs = hint ? HINT_DELAY_MS : 0;

        Thread worker = new Thread(
                () -> listen(gen, timeoutMs, minVoicedMs, threshold, startDelayMs), "VoiceBreak-Mic");
        worker.setDaemon(true);
        worker.start();
    }

    public static void stop() {
        GENERATION.incrementAndGet();
        showUntilMs = 0L;
    }

    private static void listen(int gen, long timeoutMs, int minVoicedMs, double threshold, int startDelayMs) {
        int result = ResultPacket.FAIL;
        TargetDataLine line = null;
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                result = ResultPacket.NO_MIC;
            } else {
                line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format, CHUNK_BYTES * 10);
                line.start();

                byte[] buf = new byte[CHUNK_BYTES];
                long t0 = System.nanoTime();
                int voicedMs = 0;
                double noiseFloor = Double.MAX_VALUE;

                while (GENERATION.get() == gen) {
                    long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
                    if (elapsedMs > timeoutMs) {
                        break;
                    }
                    int n = line.read(buf, 0, buf.length);
                    if (n <= 0) {
                        continue;
                    }
                    double rms = rms(buf, n);
                    level = (float) Math.min(1.0, rms * 6.0);

                    if (elapsedMs < startDelayMs) {
                        continue;
                    }
                    if (elapsedMs < startDelayMs + CALIBRATION_MS) {
                        noiseFloor = Math.min(noiseFloor, rms);
                        continue;
                    }

                    double limit = Math.max(threshold, noiseFloor == Double.MAX_VALUE ? 0 : noiseFloor * 3.0);
                    if (rms > limit) {
                        voicedMs += CHUNK_MS;
                    }
                    progress = Math.min(1f, voicedMs / (float) minVoicedMs);
                    if (voicedMs >= minVoicedMs) {
                        result = ResultPacket.SUCCESS;
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            // Нет микрофона, нет доступа, или в Java нет звукового модуля
            result = ResultPacket.NO_MIC;
        } finally {
            if (line != null) {
                try {
                    line.stop();
                    line.close();
                } catch (Throwable ignored) {
                }
            }
        }

        finish(gen, result);
    }

    private static void finish(int gen, int result) {
        Minecraft.getInstance().execute(() -> {
            if (GENERATION.get() != gen) {
                return; // уже началась новая попытка или мы вышли из мира
            }
            level = 0f;
            switch (result) {
                case ResultPacket.SUCCESS -> {
                    status = Component.literal("Засчитано!");
                    statusColor = 0x55FF55;
                }
                case ResultPacket.NO_MIC -> {
                    status = Component.literal("Микрофон недоступен");
                    statusColor = 0xFF5555;
                }
                default -> {
                    status = Component.literal("Не расслышал — попробуй снова");
                    statusColor = 0xFF5555;
                }
            }
            showUntilMs = System.currentTimeMillis() + 1500L;
            Net.CHANNEL.sendToServer(new ResultPacket(result));
        });
    }

    private static double rms(byte[] buf, int length) {
        int samples = length / 2;
        if (samples == 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            short s = (short) ((buf[i + 1] << 8) | (buf[i] & 0xFF));
            double v = s / 32768.0;
            sum += v * v;
        }
        return Math.sqrt(sum / samples);
    }
}
