package com.voicebreak;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Звук получения урона у моба защищён (protected), поэтому берём его через рефлексию.
 * Если метод не нашёлся, игроку просто покажут название моба без звуковой подсказки.
 */
public final class MobSounds {
    private static final Method HURT_SOUND = find();

    private MobSounds() {}

    private static Method find() {
        try {
            // m_7975_ = LivingEntity#getHurtSound(DamageSource)
            return ObfuscationReflectionHelper.findMethod(LivingEntity.class, "m_7975_", DamageSource.class);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    public static SoundEvent hurtSound(LivingEntity entity, DamageSource source) {
        if (HURT_SOUND == null) {
            return null;
        }
        try {
            return (SoundEvent) HURT_SOUND.invoke(entity, source);
        } catch (Throwable t) {
            return null;
        }
    }
}
