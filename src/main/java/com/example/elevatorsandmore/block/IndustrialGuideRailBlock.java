package com.example.elevatorsandmore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class IndustrialGuideRailBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    // VoxelShapes positioned at the face touching the elevator platform
    private static final VoxelShape SHAPE_SOUTH = Block.box(1.0, 0.0, 12.0, 15.0, 16.0, 16.0);
    private static final VoxelShape SHAPE_NORTH = Block.box(1.0, 0.0, 0.0, 15.0, 16.0, 4.0);
    private static final VoxelShape SHAPE_EAST  = Block.box(12.0, 0.0, 1.0, 16.0, 16.0, 15.0);
    private static final VoxelShape SHAPE_WEST  = Block.box(0.0, 0.0, 1.0, 4.0, 16.0, 15.0);

    public IndustrialGuideRailBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        net.minecraft.world.level.Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        // 1. If stacked vertically on or under an existing guide rail, preserve its facing
        BlockState belowState = level.getBlockState(pos.below());
        if (belowState.getBlock() instanceof IndustrialGuideRailBlock && belowState.hasProperty(FACING)) {
            return this.defaultBlockState().setValue(FACING, belowState.getValue(FACING));
        }
        BlockState aboveState = level.getBlockState(pos.above());
        if (aboveState.getBlock() instanceof IndustrialGuideRailBlock && aboveState.hasProperty(FACING)) {
            return this.defaultBlockState().setValue(FACING, aboveState.getValue(FACING));
        }

        // 2. If placed adjacent to an elevator floor block, automatically face towards the floor
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(dir)).getBlock() instanceof IndustrialElevatorFloorBlock) {
                return this.defaultBlockState().setValue(FACING, dir);
            }
        }

        // 3. If clicked against a wall, face into the shaft away from the wall
        Direction clickedFace = context.getClickedFace();
        if (clickedFace.getAxis().isHorizontal()) {
            return this.defaultBlockState().setValue(FACING, clickedFace);
        }

        // 4. Default: face towards player
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        builder.add(FACING);
    }
}
