package com.voicebreak;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class Config {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Common, ForgeConfigSpec> common = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();

        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private Config() {}

    /** Настройки, которые читает сервер (в одиночной игре это тот же компьютер). */
    public static final class Common {
        public final ForgeConfigSpec.BooleanValue affectBlocks;
        public final ForgeConfigSpec.BooleanValue affectMobs;
        public final ForgeConfigSpec.BooleanValue exemptCreative;
        public final ForgeConfigSpec.BooleanValue allowWithoutMic;
        public final ForgeConfigSpec.IntValue actionsPerSuccess;
        public final ForgeConfigSpec.IntValue tokenLifetimeSeconds;
        public final ForgeConfigSpec.IntValue timeoutSeconds;

        Common(ForgeConfigSpec.Builder b) {
            b.push("rules");
            affectBlocks = b.comment("Нужно ли повторять звук при ломании блоков")
                    .define("affectBlocks", true);
            affectMobs = b.comment("Нужно ли повторять звук при ударе по мобам")
                    .define("affectMobs", true);
            exemptCreative = b.comment("Освободить игроков в творческом режиме от проверки")
                    .define("exemptCreative", false);
            allowWithoutMic = b.comment("Засчитывать действие, если микрофон недоступен (иначе игра встанет)")
                    .define("allowWithoutMic", true);
            actionsPerSuccess = b.comment("Сколько действий (ударов/блоков) разрешается после одного удачного повтора")
                    .defineInRange("actionsPerSuccess", 1, 1, 64);
            tokenLifetimeSeconds = b.comment("Сколько секунд действует разрешение после удачного повтора")
                    .defineInRange("tokenLifetimeSeconds", 20, 3, 300);
            timeoutSeconds = b.comment("Сколько секунд даётся на повтор звука")
                    .defineInRange("timeoutSeconds", 6, 2, 30);
            b.pop();
        }
    }

    /** Настройки микрофона: читаются только на клиенте. */
    public static final class Client {
        public final ForgeConfigSpec.DoubleValue micThreshold;
        public final ForgeConfigSpec.IntValue minVoicedMillis;

        Client(ForgeConfigSpec.Builder b) {
            b.push("microphone");
            micThreshold = b.comment("Порог громкости (RMS от 0 до 1), выше которого звук считается голосом. "
                            + "Если мод не слышит вас, уменьшите; если реагирует на шум, увеличьте.")
                    .defineInRange("micThreshold", 0.03D, 0.003D, 0.5D);
            minVoicedMillis = b.comment("Сколько миллисекунд суммарно нужно издавать звук, чтобы повтор засчитался")
                    .defineInRange("minVoicedMillis", 300, 100, 3000);
            b.pop();
        }
    }
}
