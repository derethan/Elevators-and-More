package com.example.elevatorsandmore;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import com.example.elevatorsandmore.block.IndustrialControlPedestalBlock;
import com.example.elevatorsandmore.block.IndustrialElevatorFloorBlock;
import com.example.elevatorsandmore.block.IndustrialElevatorStationBlock;
import com.example.elevatorsandmore.block.IndustrialGuideRailBlock;
import com.example.elevatorsandmore.block.IndustrialSafetyRailingBlock;
import com.example.elevatorsandmore.block.entity.IndustrialElevatorStationBlockEntity;
import com.example.elevatorsandmore.entity.IndustrialElevatorCarriageEntity;
import com.example.elevatorsandmore.network.ElevatorActionPayload;
import com.example.elevatorsandmore.network.ElevatorSyncPayload;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ElevatorsAndMore.MODID)
public class ElevatorsAndMore {
    public static final String MODID = "elevatorsandmore";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Deferred Registers
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // 1. Industrial Guide Rail
    public static final DeferredBlock<IndustrialGuideRailBlock> INDUSTRIAL_GUIDE_RAIL = BLOCKS.registerBlock(
            "industrial_guide_rail",
            IndustrialGuideRailBlock::new,
            p -> p.strength(3.0F, 6.0F).sound(SoundType.NETHERITE_BLOCK).noOcclusion()
    );
    public static final DeferredItem<BlockItem> INDUSTRIAL_GUIDE_RAIL_ITEM = ITEMS.registerSimpleBlockItem(
            "industrial_guide_rail",
            INDUSTRIAL_GUIDE_RAIL
    );

    // 2. Industrial Elevator Floor
    public static final DeferredBlock<IndustrialElevatorFloorBlock> INDUSTRIAL_ELEVATOR_FLOOR = BLOCKS.registerBlock(
            "industrial_elevator_floor",
            IndustrialElevatorFloorBlock::new,
            p -> p.strength(3.0F, 6.0F).sound(SoundType.METAL)
    );
    public static final DeferredItem<BlockItem> INDUSTRIAL_ELEVATOR_FLOOR_ITEM = ITEMS.registerSimpleBlockItem(
            "industrial_elevator_floor",
            INDUSTRIAL_ELEVATOR_FLOOR
    );

    // 3. Industrial Safety Railing
    public static final DeferredBlock<IndustrialSafetyRailingBlock> INDUSTRIAL_SAFETY_RAILING = BLOCKS.registerBlock(
            "industrial_safety_railing",
            IndustrialSafetyRailingBlock::new,
            p -> p.strength(2.0F, 4.0F).sound(SoundType.METAL).noOcclusion()
    );
    public static final DeferredItem<BlockItem> INDUSTRIAL_SAFETY_RAILING_ITEM = ITEMS.registerSimpleBlockItem(
            "industrial_safety_railing",
            INDUSTRIAL_SAFETY_RAILING
    );

    // 4. Industrial Control Pedestal
    public static final DeferredBlock<IndustrialControlPedestalBlock> INDUSTRIAL_CONTROL_PEDESTAL = BLOCKS.registerBlock(
            "industrial_control_pedestal",
            IndustrialControlPedestalBlock::new,
            p -> p.strength(2.5F, 5.0F).sound(SoundType.METAL).noOcclusion()
    );
    public static final DeferredItem<BlockItem> INDUSTRIAL_CONTROL_PEDESTAL_ITEM = ITEMS.registerSimpleBlockItem(
            "industrial_control_pedestal",
            INDUSTRIAL_CONTROL_PEDESTAL
    );

    // 5. Industrial Elevator Station
    public static final DeferredBlock<IndustrialElevatorStationBlock> INDUSTRIAL_ELEVATOR_STATION = BLOCKS.registerBlock(
            "industrial_elevator_station",
            IndustrialElevatorStationBlock::new,
            p -> p.strength(2.5F, 5.0F).sound(SoundType.METAL).noOcclusion()
    );
    public static final DeferredItem<BlockItem> INDUSTRIAL_ELEVATOR_STATION_ITEM = ITEMS.registerSimpleBlockItem(
            "industrial_elevator_station",
            INDUSTRIAL_ELEVATOR_STATION
    );

    // Block Entity: Station
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IndustrialElevatorStationBlockEntity>> INDUSTRIAL_ELEVATOR_STATION_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "industrial_elevator_station",
            () -> new BlockEntityType<>(IndustrialElevatorStationBlockEntity::new, INDUSTRIAL_ELEVATOR_STATION.get())
    );

    // Entity: Moving Carriage
    public static final DeferredHolder<EntityType<?>, EntityType<IndustrialElevatorCarriageEntity>> INDUSTRIAL_ELEVATOR_CARRIAGE_ENTITY = ENTITY_TYPES.register(
            "industrial_elevator_carriage",
            key -> EntityType.Builder.<IndustrialElevatorCarriageEntity>of(IndustrialElevatorCarriageEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key))
    );

    // Creative Mode Tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ELEVATORS_TAB = CREATIVE_MODE_TABS.register(
            "elevatorsandmore_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elevatorsandmore"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> INDUSTRIAL_ELEVATOR_STATION_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(INDUSTRIAL_GUIDE_RAIL_ITEM.get());
                        output.accept(INDUSTRIAL_ELEVATOR_FLOOR_ITEM.get());
                        output.accept(INDUSTRIAL_SAFETY_RAILING_ITEM.get());
                        output.accept(INDUSTRIAL_CONTROL_PEDESTAL_ITEM.get());
                        output.accept(INDUSTRIAL_ELEVATOR_STATION_ITEM.get());
                    })
                    .build()
    );

    public ElevatorsAndMore(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerPayloadHandlers);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.LOCAL, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Elevators and More common setup initialized.");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(INDUSTRIAL_ELEVATOR_STATION_ITEM.get());
            event.accept(INDUSTRIAL_GUIDE_RAIL_ITEM.get());
            event.accept(INDUSTRIAL_ELEVATOR_FLOOR_ITEM.get());
            event.accept(INDUSTRIAL_SAFETY_RAILING_ITEM.get());
            event.accept(INDUSTRIAL_CONTROL_PEDESTAL_ITEM.get());
        }
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                ElevatorSyncPayload.TYPE,
                ElevatorSyncPayload.STREAM_CODEC,
                ElevatorSyncPayload::handle
        );

        registrar.playToServer(
                ElevatorActionPayload.TYPE,
                ElevatorActionPayload.STREAM_CODEC,
                ElevatorActionPayload::handle
        );
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Elevators and More initialized on server.");
    }

    @SubscribeEvent
    public void onPlayerPreTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        IndustrialElevatorCarriageEntity carriage = IndustrialElevatorCarriageEntity.getCarriageBeneath(player);
        if (carriage != null) {
            player.fallDistance = 0.0F;
            player.setOnGround(true);
        }
    }

    @SubscribeEvent
    public void onPlayerPostTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        IndustrialElevatorCarriageEntity carriage = IndustrialElevatorCarriageEntity.getCarriageBeneath(player);
        if (carriage != null) {
            player.fallDistance = 0.0F;
            player.setOnGround(true);

            // If player is riding inside a vehicle (e.g. minecart or locomotive),
            // do NOT override their position! The vehicle manages their position.
            if (player.isPassenger()) {
                if (carriage.getLastMovedGameTime() == player.level().getGameTime()) {
                    double step = carriage.getLastStep();
                    player.yo = player.getY() - step;
                    player.yOld = player.getY() - step;
                }
                return;
            }

            // If the carriage ticked BEFORE the player in this game tick, Player.commonTick()/baseTick()
            // overwrote player.yo and player.yOld to player.getY(). Restore exact sub-tick interpolation
            // fields (yo for Camera, yOld for EntityRenderer) unless the player is jumping upward.
            if (carriage.getLastMovedGameTime() == player.level().getGameTime() && player.getDeltaMovement().y <= 0.15) {
                double targetY = carriage.getY() + 1.0;
                double oldY = carriage.yo + 1.0;
                player.setPos(player.getX(), targetY, player.getZ());
                player.yo = oldY;
                player.yOld = oldY;
            }
        }
    }

    @SubscribeEvent
    public void onEntityPreTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof AbstractMinecart cart) {
            IndustrialElevatorCarriageEntity carriage = IndustrialElevatorCarriageEntity.getCarriageBeneath(cart);
            if (carriage != null) {
                cart.fallDistance = 0.0F;
                cart.setOnGround(true);
            }
        }
    }

    @SubscribeEvent
    public void onEntityPostTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof AbstractMinecart cart) {
            IndustrialElevatorCarriageEntity carriage = IndustrialElevatorCarriageEntity.getCarriageBeneath(cart);
            if (carriage != null) {
                cart.fallDistance = 0.0F;
                cart.setOnGround(true);
            }
        }
    }
}
