package com.example.elevatorsandmore.block;

import com.example.elevatorsandmore.ElevatorsAndMore;
import com.example.elevatorsandmore.util.ElevatorShaftHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class IndustrialControlPedestalBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape PEDESTAL_BASE = Block.box(4.0, 0.0, 4.0, 12.0, 2.0, 12.0);
    private static final VoxelShape PEDESTAL_POST = Block.box(6.0, 2.0, 6.0, 10.0, 11.0, 10.0);
    private static final VoxelShape PEDESTAL_HEAD = Block.box(3.0, 11.0, 3.0, 13.0, 16.0, 13.0);
    private static final VoxelShape SHAPE = Shapes.or(PEDESTAL_BASE, PEDESTAL_POST, PEDESTAL_HEAD);

    public IndustrialControlPedestalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Mount onto an existing Safety Railing block (railing + pedestal share the same block space)
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        if (existing.getBlock() instanceof IndustrialSafetyRailingBlock) {
            return existing.getValue(IndustrialSafetyRailingBlock.PEDESTAL)
                    ? null
                    : existing.setValue(IndustrialSafetyRailingBlock.PEDESTAL, true);
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        // A Safety Railing can be added around a standalone pedestal
        return context.getItemInHand().is(ElevatorsAndMore.INDUSTRIAL_SAFETY_RAILING_ITEM.get());
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        // Let railing items fall through to normal placement (merging handled by canBeReplaced)
        if (stack.is(ElevatorsAndMore.INDUSTRIAL_SAFETY_RAILING_ITEM.get())) {
            return InteractionResult.PASS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ElevatorShaftHelper.openElevatorGuiFromPlatform(level, pos, serverPlayer);
        }
        return InteractionResult.SUCCESS;
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
