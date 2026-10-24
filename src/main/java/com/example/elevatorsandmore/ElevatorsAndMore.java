package com.example.elevatorsandmore;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ElevatorsAndMore.MODID)
public class ElevatorsAndMore {
    public static final String MODID = "elevatorsandmore";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Deferred Registers
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creative Mode Tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ELEVATORS_TAB = CREATIVE_MODE_TABS.register(
            "elevatorsandmore_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elevatorsandmore"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> new ItemStack(Blocks.IRON_BLOCK))
                    .displayItems((parameters, output) -> {
                        // Mod items will be added here
                    })
                    .build()
    );

    public ElevatorsAndMore(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.LOCAL, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Elevators and More common setup starting...");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Vanilla creative tab additions can be registered here
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Elevators and More initialized on server.");
    }
}
