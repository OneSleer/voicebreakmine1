package com.voicebreak.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class VoiceHud {
    private VoiceHud() {}

    public static void render(GuiGraphics g, int width, int height) {
        if (System.currentTimeMillis() >= ClientVoice.showUntilMs) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int cx = width / 2;
        int y = height / 2 - 70;

        g.drawCenteredString(font, ClientVoice.prompt, cx, y, 0xFFFFFF);
        g.drawCenteredString(font, ClientVoice.status, cx, y + 12, ClientVoice.statusColor);

        int barWidth = 120;
        int x0 = cx - barWidth / 2;
        int by = y + 28;
        g.fill(x0 - 1, by - 1, x0 + barWidth + 1, by + 8, 0xAA000000);
        g.fill(x0, by, x0 + (int) (barWidth * ClientVoice.progress), by + 3, 0xFF55FF55);  // прогресс засчёта
        g.fill(x0, by + 4, x0 + (int) (barWidth * ClientVoice.level), by + 7, 0xFFFFFF55);  // уровень микрофона
    }
}
