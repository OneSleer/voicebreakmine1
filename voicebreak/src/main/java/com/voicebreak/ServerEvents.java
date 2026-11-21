package com.voicebreak;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VoiceBreakMod.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    private static boolean exempt(ServerPlayer player) {
        if (player instanceof FakePlayer || player.isSpectator()) {
            return true;
        }
        return player.isCreative() && Config.COMMON.exemptCreative.get();
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!Config.COMMON.affectBlocks.get()) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player) || exempt(player)) {
            return;
        }

        BlockState state = event.getState();
        SoundEvent breakSound = state.getSoundType().getBreakSound();
        Component prompt = Component.literal("Повтори звук блока голосом: ").append(state.getBlock().getName());

        if (!ChallengeManager.tryConsume(player, prompt, breakSound)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!Config.COMMON.affectMobs.get()) {
            return;
        }
        // Событие приходит и на клиенте; решение принимает только сервер.
        if (!(event.getEntity() instanceof ServerPlayer player) || exempt(player)) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }

        DamageSource source = target.level().damageSources().playerAttack(player);
        SoundEvent hurt = MobSounds.hurtSound(target, source);
        Component prompt = Component.literal("Повтори звук моба голосом: ").append(target.getName());

        if (!ChallengeManager.tryConsume(player, prompt, hurt)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ChallengeManager.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("voicebreak")
                .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), false))));
    }

    private static int toggle(CommandSourceStack source, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ChallengeManager.setEnabled(player, enabled);
        source.sendSuccess(() -> Component.literal(
                enabled ? "Voice Break включён: повторяй звуки голосом." : "Voice Break выключен для тебя."), false);
        return 1;
    }
}
