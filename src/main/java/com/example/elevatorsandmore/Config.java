package com.example.elevatorsandmore;

import net.neoforged.neoforge.common.ModConfigSpec;

// Configuration class using NeoForge's config APIs.
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_PLATFORM_BLOCKS;
    public static final ModConfigSpec.IntValue MAX_HORIZONTAL_SPAN;
    public static final ModConfigSpec.IntValue MAX_SHAFT_HEIGHT;
    public static final ModConfigSpec.DoubleValue ELEVATOR_SPEED;

    static {
        BUILDER.push("industrial_elevator");

        MAX_PLATFORM_BLOCKS = BUILDER
                .comment("Maximum number of connected floor blocks that can make up an elevator platform.")
                .defineInRange("maxPlatformBlocks", 49, 1, 121);

        MAX_HORIZONTAL_SPAN = BUILDER
                .comment("Maximum horizontal span (width or length) in blocks from the guide rail anchor.")
                .defineInRange("maxHorizontalSpan", 7, 1, 11);

        MAX_SHAFT_HEIGHT = BUILDER
                .comment("Maximum vertical distance in blocks that an elevator shaft can span.")
                .defineInRange("maxShaftHeight", 384, 16, 512);

        ELEVATOR_SPEED = BUILDER
                .comment("Travel speed of the elevator in blocks per tick (0.20 = 4 blocks per second).")
                .defineInRange("elevatorSpeed", 0.20D, 0.05D, 1.0D);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
