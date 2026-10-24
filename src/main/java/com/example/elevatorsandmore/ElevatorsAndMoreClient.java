package com.example.elevatorsandmore;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// This class will not load on dedicated servers. Accessing client-side code from here is safe.
@Mod(value = ElevatorsAndMore.MODID, dist = Dist.CLIENT)
public class ElevatorsAndMoreClient {
    public ElevatorsAndMoreClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::clientSetup);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        ElevatorsAndMore.LOGGER.info("Elevators and More client setup starting...");
    }
}
