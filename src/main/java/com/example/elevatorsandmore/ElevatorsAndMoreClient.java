package com.example.elevatorsandmore;

import com.example.elevatorsandmore.client.gui.IndustrialElevatorScreen;
import com.example.elevatorsandmore.client.renderer.IndustrialElevatorCarriageRenderer;
import com.example.elevatorsandmore.network.ElevatorSyncPayload;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client-side code from here is safe.
@Mod(value = ElevatorsAndMore.MODID, dist = Dist.CLIENT)
public class ElevatorsAndMoreClient {

    public ElevatorsAndMoreClient(IEventBus modEventBus, ModContainer modContainer) {
        // Allows NeoForge to create a config screen for this mod's configs
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerEntityRenderers);

        // Bind client packet handler for elevator sync
        ElevatorSyncPayload.CLIENT_HANDLER = IndustrialElevatorScreen::open;
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        ElevatorsAndMore.LOGGER.info("Elevators and More client setup initialized.");
    }

    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ElevatorsAndMore.INDUSTRIAL_ELEVATOR_CARRIAGE_ENTITY.get(),
                IndustrialElevatorCarriageRenderer::new
        );
    }
}
