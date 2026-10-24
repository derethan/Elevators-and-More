package com.example.elevatorsandmore.block;

import org.jspecify.annotations.Nullable;

import com.example.elevatorsandmore.block.entity.IndustrialElevatorStationBlockEntity;
import com.example.elevatorsandmore.util.ElevatorShaftHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class IndustrialElevatorStationBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty DOCKED = BooleanProperty.create("docked");

    // Wall-mounted call station box (6 pixels deep from mounting wall)
    private static final VoxelShape SHAPE_NORTH = Block.box(2.0, 2.0, 10.0, 14.0, 14.0, 16.0);
    private static final VoxelShape SHAPE_SOUTH = Block.box(2.0, 2.0, 0.0, 14.0, 14.0, 6.0);
    private static final VoxelShape SHAPE_WEST = Block.box(10.0, 2.0, 2.0, 16.0, 14.0, 14.0);
    private static final VoxelShape SHAPE_EAST = Block.box(0.0, 2.0, 2.0, 6.0, 14.0, 14.0);

    public IndustrialElevatorStationBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(DOCKED, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockPos railPos = ElevatorShaftHelper.findAdjacentRail(level, pos);
        if (railPos == null) {
            if (!level.isClientSide() && context.getPlayer() != null) {
                context.getPlayer().sendSystemMessage(Component.literal("§c[Elevators & More] Cannot place Elevator Control Board here! It must be within 2 blocks of the outside perimeter of an elevator floor."));
            }
            return null;
        }

        Direction clickedFace = context.getClickedFace();
        Direction facing;
        if (clickedFace.getAxis().isHorizontal()) {
            facing = clickedFace;
        } else {
            facing = context.getHorizontalDirection().getOpposite();
        }
        return this.defaultBlockState().setValue(FACING, facing).setValue(DOCKED, false);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockPos rail = ElevatorShaftHelper.findAdjacentRail(level, pos);
            if (rail == null) {
                serverPlayer.sendSystemMessage(Component.literal("§c[Elevators & More] This Control Board is too far from an elevator floor! It must be within 2 blocks of the elevator floor perimeter."));
                return InteractionResult.CONSUME;
            }
            ElevatorShaftHelper.openElevatorGuiFromStation(level, pos, serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean isStationReceivingRedstone(Level level, BlockPos pos, BlockState state) {
        // 1. Direct signal to the Control Board
        if (level.hasNeighborSignal(pos)) {
            return true;
        }
        // 2. Signal through the wall block it is mounted on
        if (state.hasProperty(FACING)) {
            Direction wallDir = state.getValue(FACING).getOpposite();
            BlockPos wallPos = pos.relative(wallDir);
            if (level.hasNeighborSignal(wallPos)) {
                return true;
            }
        }
        // 3. Signal to the floor block directly beneath or 2 below
        if (level.hasNeighborSignal(pos.below()) || level.hasNeighborSignal(pos.below(2))) {
            return true;
        }
        return false;
    }

    private void checkRedstoneCall(Level level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof IndustrialElevatorStationBlockEntity stationBE)) {
            return;
        }
        boolean isReceiving = isStationReceivingRedstone(level, pos, state);
        boolean wasPowered = stationBE.isRedstonePowered();

        if (isReceiving != wasPowered) {
            stationBE.setRedstonePowered(isReceiving);
            if (isReceiving && level instanceof ServerLevel serverLevel) {
                ElevatorShaftHelper.callElevatorToStation(serverLevel, pos);
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (!level.isClientSide()) {
            checkRedstoneCall(level, pos, state);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            checkRedstoneCall(level, pos, state);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IndustrialElevatorStationBlockEntity(pos, state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DOCKED);
    }
}
