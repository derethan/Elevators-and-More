package com.example.elevatorsandmore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class IndustrialSafetyRailingBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Outer edge boundary shapes with 24-pixel fence height collision
    private static final VoxelShape SHAPE_NORTH = Block.box(0.0, 0.0, 0.0, 16.0, 24.0, 3.0);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0.0, 0.0, 13.0, 16.0, 24.0, 16.0);
    private static final VoxelShape SHAPE_WEST = Block.box(0.0, 0.0, 0.0, 3.0, 24.0, 16.0);
    private static final VoxelShape SHAPE_EAST = Block.box(13.0, 0.0, 0.0, 16.0, 24.0, 16.0);

    // Visual selection shape (16px high)
    private static final VoxelShape VISUAL_NORTH = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 3.0);
    private static final VoxelShape VISUAL_SOUTH = Block.box(0.0, 0.0, 13.0, 16.0, 16.0, 16.0);
    private static final VoxelShape VISUAL_WEST = Block.box(0.0, 0.0, 0.0, 3.0, 16.0, 16.0);
    private static final VoxelShape VISUAL_EAST = Block.box(13.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public IndustrialSafetyRailingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> VISUAL_SOUTH;
            case WEST -> VISUAL_WEST;
            case EAST -> VISUAL_EAST;
            default -> VISUAL_NORTH;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
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
