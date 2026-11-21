package com.voicebreak;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(VoiceBreakMod.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private Net() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, ChallengePacket.class,
                ChallengePacket::encode, ChallengePacket::decode, ChallengePacket::handle);
        CHANNEL.registerMessage(id++, ResultPacket.class,
                ResultPacket::encode, ResultPacket::decode, ResultPacket::handle);
    }
}
