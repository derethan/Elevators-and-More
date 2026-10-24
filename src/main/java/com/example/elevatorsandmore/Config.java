package com.example.elevatorsandmore;

import net.neoforged.neoforge.common.ModConfigSpec;

// Configuration class using NeoForge's config APIs.
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        // Configuration options can be added here
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
