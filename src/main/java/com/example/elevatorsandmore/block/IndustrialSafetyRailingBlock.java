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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class IndustrialSafetyRailingBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty PEDESTAL = BooleanProperty.create("pedestal");

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

    // Pedestal mounted shape
    private static final VoxelShape PEDESTAL_BASE = Block.box(4.0, 0.0, 4.0, 12.0, 2.0, 12.0);
    private static final VoxelShape PEDESTAL_POST = Block.box(6.0, 2.0, 6.0, 10.0, 11.0, 10.0);
    private static final VoxelShape PEDESTAL_HEAD = Block.box(3.0, 11.0, 3.0, 13.0, 16.0, 13.0);
    private static final VoxelShape PEDESTAL_SHAPE = Shapes.or(PEDESTAL_BASE, PEDESTAL_POST, PEDESTAL_HEAD);

    // 32 precomputed shape lookup tables
    private static final VoxelShape[] VISUAL_SHAPES = new VoxelShape[32];
    private static final VoxelShape[] COLLISION_SHAPES = new VoxelShape[32];

    static {
        for (int i = 0; i < 32; i++) {
            boolean north = (i & 1) != 0;
            boolean east = (i & 2) != 0;
            boolean south = (i & 4) != 0;
            boolean west = (i & 8) != 0;
            boolean pedestal = (i & 16) != 0;

            VoxelShape visual = Shapes.empty();
            VoxelShape collision = Shapes.empty();

            if (north) {
                visual = Shapes.or(visual, VISUAL_NORTH);
                collision = Shapes.or(collision, SHAPE_NORTH);
            }
            if (east) {
                visual = Shapes.or(visual, VISUAL_EAST);
                collision = Shapes.or(collision, SHAPE_EAST);
            }
            if (south) {
                visual = Shapes.or(visual, VISUAL_SOUTH);
                collision = Shapes.or(collision, SHAPE_SOUTH);
            }
            if (west) {
                visual = Shapes.or(visual, VISUAL_WEST);
                collision = Shapes.or(collision, SHAPE_WEST);
            }
            if (pedestal) {
                visual = Shapes.or(visual, PEDESTAL_SHAPE);
                collision = Shapes.or(collision, PEDESTAL_SHAPE);
            }

            if (visual.isEmpty()) {
                visual = Shapes.block();
                collision = Shapes.block();
            }

            VISUAL_SHAPES[i] = visual;
            COLLISION_SHAPES[i] = collision;
        }
    }

    public IndustrialSafetyRailingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(PEDESTAL, false));
    }

    private static int getIndex(BlockState state) {
        int idx = 0;
        if (state.getValue(NORTH)) idx |= 1;
        if (state.getValue(EAST)) idx |= 2;
        if (state.getValue(SOUTH)) idx |= 4;
        if (state.getValue(WEST)) idx |= 8;
        if (state.getValue(PEDESTAL)) idx |= 16;
        return idx;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return VISUAL_SHAPES[getIndex(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPES[getIndex(state)];
    }

    public static boolean hasEdge(BlockState state, Direction direction) {
        return switch (direction) {
            case NORTH -> state.getValue(NORTH);
            case EAST -> state.getValue(EAST);
            case SOUTH -> state.getValue(SOUTH);
            case WEST -> state.getValue(WEST);
            default -> false;
        };
    }

    public static BooleanProperty getPropertyForDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("Invalid horizontal direction: " + direction);
        };
    }

    /**
     * Resolves the target edge for initial placement.
     * Checks clicked quadrant of the block first; falls back to player horizontal facing.
     */
    public static Direction resolveEdgeDirection(Vec3 clickLocation, BlockPos pos, Direction playerFacing) {
        Vec3 hit = clickLocation.subtract(Vec3.atLowerCornerOf(pos));
        double dx = hit.x - 0.5;
        double dz = hit.z - 0.5;

        if (Math.abs(dx) > 0.1 || Math.abs(dz) > 0.1) {
            if (Math.abs(dx) > Math.abs(dz)) {
                return dx > 0 ? Direction.EAST : Direction.WEST;
            } else {
                return dz > 0 ? Direction.SOUTH : Direction.NORTH;
            }
        }
        return playerFacing;
    }

    /**
     * Returns true when the click lands on the outside boundary face of the block space
     * (e.g. the outer face of a rail). In that case the player intends to place into the
     * neighbouring block, so we must not merge into this one.
     */
    private static boolean isOuterFaceClick(BlockPlaceContext context) {
        if (!context.replacingClickedOnBlock()) {
            return false;
        }
        Direction face = context.getClickedFace();
        if (face.getAxis().isVertical()) {
            return false;
        }
        Vec3 hit = context.getClickLocation().subtract(Vec3.atLowerCornerOf(context.getClickedPos()));
        double eps = 1.0E-4;
        return switch (face) {
            case NORTH -> hit.z <= eps;
            case SOUTH -> hit.z >= 1.0 - eps;
            case WEST -> hit.x <= eps;
            case EAST -> hit.x >= 1.0 - eps;
            default -> false;
        };
    }

    /**
     * Chooses which unbuilt edge a railing placement should add to an existing block space,
     * based on the aimed quadrant (primary axis, then secondary axis), then player facing.
     * Returns null when no sensible edge is available.
     *
     * @param existingEdges state whose NORTH/EAST/SOUTH/WEST values are already occupied
     *                      (null = no railing edges yet)
     */
    public static @Nullable Direction chooseEdgeForPlacement(@Nullable BlockState existingEdges, BlockPlaceContext context) {
        Vec3 hit = context.getClickLocation().subtract(Vec3.atLowerCornerOf(context.getClickedPos()));
        double dx = hit.x - 0.5;
        double dz = hit.z - 0.5;
        Direction facing = context.getHorizontalDirection();

        Direction primary;
        Direction secondary;
        if (Math.abs(dx) <= 0.1 && Math.abs(dz) <= 0.1) {
            primary = facing;
            secondary = facing;
        } else if (Math.abs(dx) > Math.abs(dz)) {
            primary = dx > 0 ? Direction.EAST : Direction.WEST;
            secondary = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        } else {
            primary = dz > 0 ? Direction.SOUTH : Direction.NORTH;
            secondary = dx > 0 ? Direction.EAST : Direction.WEST;
        }

        for (Direction candidate : new Direction[]{primary, secondary, facing}) {
            if (existingEdges == null || !hasEdge(existingEdges, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static BlockState withEdge(BlockState state, Direction edge) {
        return state.setValue(getPropertyForDirection(edge), true);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        if (isOuterFaceClick(context)) {
            return false;
        }
        ItemStack stack = context.getItemInHand();
        // Another railing can merge into this block space if an edge is still free
        if (stack.is(this.asItem())) {
            return chooseEdgeForPlacement(state, context) != null;
        }
        // A Control Pedestal can be mounted into this block space if not already present
        if (stack.is(ElevatorsAndMore.INDUSTRIAL_CONTROL_PEDESTAL_ITEM.get())) {
            return !state.getValue(PEDESTAL);
        }
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());

        // Merge into an existing railing block (adds a new edge, e.g. corners)
        if (existing.is(this)) {
            Direction edge = chooseEdgeForPlacement(existing, context);
            return edge == null ? null : withEdge(existing, edge);
        }

        Direction edge = chooseEdgeForPlacement(null, context);
        if (edge == null) {
            edge = context.getHorizontalDirection();
        }
        BlockState placed = withEdge(this.defaultBlockState(), edge);

        // Merge into an existing standalone Control Pedestal (railing + pedestal share the block)
        if (existing.getBlock() instanceof IndustrialControlPedestalBlock) {
            placed = placed.setValue(PEDESTAL, true);
        }
        return placed;
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
        // Let railing / pedestal items fall through to normal block placement (merging handled by canBeReplaced)
        if (stack.is(this.asItem())
                || (stack.is(ElevatorsAndMore.INDUSTRIAL_CONTROL_PEDESTAL_ITEM.get()) && !state.getValue(PEDESTAL))) {
            return InteractionResult.PASS;
        }

        // If pedestal is present, allow interaction with any other held item to trigger GUI
        if (state.getValue(PEDESTAL)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(PEDESTAL)) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                ElevatorShaftHelper.openElevatorGuiFromPlatform(level, pos, serverPlayer);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, builder));
        int railingCount = 0;
        if (state.getValue(NORTH)) railingCount++;
        if (state.getValue(EAST)) railingCount++;
        if (state.getValue(SOUTH)) railingCount++;
        if (state.getValue(WEST)) railingCount++;

        if (railingCount > 1) {
            drops.add(new ItemStack(this.asItem(), railingCount - 1));
        }
        if (state.getValue(PEDESTAL)) {
            drops.add(new ItemStack(ElevatorsAndMore.INDUSTRIAL_CONTROL_PEDESTAL_ITEM.get(), 1));
        }
        return drops;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> state
                    .setValue(NORTH, state.getValue(WEST))
                    .setValue(EAST, state.getValue(NORTH))
                    .setValue(SOUTH, state.getValue(EAST))
                    .setValue(WEST, state.getValue(SOUTH));
            case CLOCKWISE_180 -> state
                    .setValue(NORTH, state.getValue(SOUTH))
                    .setValue(EAST, state.getValue(WEST))
                    .setValue(SOUTH, state.getValue(NORTH))
                    .setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90 -> state
                    .setValue(NORTH, state.getValue(EAST))
                    .setValue(EAST, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(WEST))
                    .setValue(WEST, state.getValue(NORTH));
            default -> state;
        };
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> state
                    .setValue(NORTH, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK -> state
                    .setValue(EAST, state.getValue(WEST))
                    .setValue(WEST, state.getValue(EAST));
            default -> state;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, PEDESTAL);
    }
}
