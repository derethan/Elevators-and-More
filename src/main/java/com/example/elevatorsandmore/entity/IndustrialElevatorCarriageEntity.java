package com.example.elevatorsandmore.entity;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.example.elevatorsandmore.Config;
import com.example.elevatorsandmore.block.IndustrialElevatorStationBlock;
import com.example.elevatorsandmore.util.ElevatorShaftHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class IndustrialElevatorCarriageEntity extends Entity {

    private static final EntityDataAccessor<BlockPos> DATA_ANCHOR_RAIL =
            SynchedEntityData.defineId(IndustrialElevatorCarriageEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos> DATA_FLOOR_ANCHOR =
            SynchedEntityData.defineId(IndustrialElevatorCarriageEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_TARGET_Y =
            SynchedEntityData.defineId(IndustrialElevatorCarriageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_START_Y =
            SynchedEntityData.defineId(IndustrialElevatorCarriageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_BLOCKS =
            SynchedEntityData.defineId(IndustrialElevatorCarriageEntity.class, EntityDataSerializers.STRING);

    private final List<ElevatorShaftHelper.CapturedBlockEntry> parsedBlocks = new ArrayList<>();
    private String lastParsedBlocksStr = "";
    private int minRelX = 0, maxRelX = 0, minRelZ = 0, maxRelZ = 0;

    public IndustrialElevatorCarriageEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ANCHOR_RAIL, BlockPos.ZERO);
        builder.define(DATA_FLOOR_ANCHOR, BlockPos.ZERO);
        builder.define(DATA_TARGET_Y, 0);
        builder.define(DATA_START_Y, 0);
        builder.define(DATA_BLOCKS, "");
    }

    public void setupPlatform(
            BlockPos anchorRail,
            BlockPos floorAnchor,
            int startY,
            int targetY,
            List<ElevatorShaftHelper.CapturedBlockEntry> blocks
    ) {
        this.entityData.set(DATA_ANCHOR_RAIL, anchorRail);
        this.entityData.set(DATA_FLOOR_ANCHOR, floorAnchor);
        this.entityData.set(DATA_START_Y, startY);
        this.entityData.set(DATA_TARGET_Y, targetY);

        StringBuilder sb = new StringBuilder();
        for (ElevatorShaftHelper.CapturedBlockEntry b : blocks) {
            if (sb.length() > 0) sb.append(';');
            sb.append(b.relX()).append(',')
              .append(b.relY()).append(',')
              .append(b.relZ()).append(',')
              .append(Block.getId(b.state()));
        }
        this.entityData.set(DATA_BLOCKS, sb.toString());

        this.refreshParsedBlocks();
        this.setPos(floorAnchor.getX() + 0.5, startY, floorAnchor.getZ() + 0.5);
    }

    public BlockPos getAnchorRailPos() {
        return this.entityData.get(DATA_ANCHOR_RAIL);
    }

    public BlockPos getFloorAnchorPos() {
        return this.entityData.get(DATA_FLOOR_ANCHOR);
    }

    public int getTargetY() {
        return this.entityData.get(DATA_TARGET_Y);
    }

    public void setTargetY(int targetY) {
        this.entityData.set(DATA_TARGET_Y, targetY);
    }

    public void emergencyStop() {
        this.setTargetY((int) Math.round(this.getY()));
    }

    public int getMinRelX() { return minRelX; }
    public int getMaxRelX() { return maxRelX; }
    public int getMinRelZ() { return minRelZ; }
    public int getMaxRelZ() { return maxRelZ; }

    public List<ElevatorShaftHelper.CapturedBlockEntry> getParsedBlocks() {
        refreshParsedBlocks();
        return this.parsedBlocks;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_BLOCKS.equals(accessor) || DATA_FLOOR_ANCHOR.equals(accessor)) {
            this.refreshParsedBlocks();
        }
    }

    private void refreshParsedBlocks() {
        String data = this.entityData.get(DATA_BLOCKS);
        if (data.equals(this.lastParsedBlocksStr)) {
            return;
        }
        this.lastParsedBlocksStr = data;
        this.parsedBlocks.clear();
        minRelX = 0; maxRelX = 0; minRelZ = 0; maxRelZ = 0;

        if (data.isEmpty()) return;

        String[] parts = data.split(";");
        for (String p : parts) {
            String[] tokens = p.split(",");
            if (tokens.length >= 4) {
                try {
                    int rx = Integer.parseInt(tokens[0]);
                    int ry = Integer.parseInt(tokens[1]);
                    int rz = Integer.parseInt(tokens[2]);
                    int stateId = Integer.parseInt(tokens[3]);
                    BlockState st = Block.stateById(stateId);
                    this.parsedBlocks.add(new ElevatorShaftHelper.CapturedBlockEntry(rx, ry, rz, st));

                    if (rx < minRelX) minRelX = rx;
                    if (rx > maxRelX) maxRelX = rx;
                    if (rz < minRelZ) minRelZ = rz;
                    if (rz > maxRelZ) maxRelZ = rz;
                } catch (NumberFormatException ignored) {}
            }
        }
        this.setBoundingBox(this.makeBoundingBox());
    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        BlockPos anchor = this.getFloorAnchorPos();
        if (anchor == null || anchor.equals(BlockPos.ZERO) || this.parsedBlocks.isEmpty()) {
            return super.makeBoundingBox(position);
        }
        double boxMinX = anchor.getX() + minRelX;
        double boxMaxX = anchor.getX() + maxRelX + 1.0;
        double boxMinZ = anchor.getZ() + minRelZ;
        double boxMaxZ = anchor.getZ() + maxRelZ + 1.0;
        return new AABB(boxMinX, position.y, boxMinZ, boxMaxX, position.y + 1.0, boxMaxZ);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return !this.isRemoved();
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return !this.isRemoved() && !this.isPassengerOfSameVehicle(entity);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (!this.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ElevatorShaftHelper.openElevatorGuiFromPlatform(this.level(), this.blockPosition(), serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();

        double currentY = this.getY();
        double targetY = this.getTargetY();
        double diff = targetY - currentY;
        double speed = Config.ELEVATOR_SPEED.get();

        refreshParsedBlocks();

        BlockPos anchor = this.getFloorAnchorPos();
        double boxMinX = anchor.getX() + minRelX;
        double boxMaxX = anchor.getX() + maxRelX + 1.0;
        double boxMinZ = anchor.getZ() + minRelZ;
        double boxMaxZ = anchor.getZ() + maxRelZ + 1.0;

        if (Math.abs(diff) <= 0.05) {
            if (!this.level().isClientSide()) {
                finishDocking((int) Math.round(targetY));
            }
            return;
        }

        double step = Math.signum(diff) * Math.min(Math.abs(diff), speed);
        double nextY = currentY + step;

        // Advance platform position (makeBoundingBox updates the bounding box automatically)
        this.setPos(this.getX(), nextY, this.getZ());

        // Carry passengers & standing entities smoothly
        carryStandingEntities(boxMinX, boxMaxX, boxMinZ, boxMaxZ, currentY, nextY, step);
    }

    private void carryStandingEntities(
            double minX, double maxX, double minZ, double maxZ,
            double oldY, double newY, double step
    ) {
        double oldFloorSurfaceY = oldY + 1.0;
        double newFloorSurfaceY = newY + 1.0;

        // Expanded query box to detect entities standing on or near the moving platform
        AABB searchBox = new AABB(
                minX - 0.1,
                Math.min(oldY, newY) - 0.5,
                minZ - 0.1,
                maxX + 0.1,
                Math.max(oldY, newY) + 3.0,
                maxZ + 0.1
        );

        List<Entity> riders = this.level().getEntities(this, searchBox, e -> e.isAlive() && !e.isPassenger());
        for (Entity e : riders) {
            AABB eb = e.getBoundingBox();
            // Verify horizontal overlap with platform
            if (eb.maxX <= minX || eb.minX >= maxX || eb.maxZ <= minZ || eb.minZ >= maxZ) {
                continue;
            }

            double entityY = e.getY();
            boolean shouldCarry = false;

            if (step > 0) {
                // Moving UP:
                // Carry if entity was on the floor before this step or is being penetrated by the rising floor
                if (entityY >= oldFloorSurfaceY - 0.5 && entityY <= newFloorSurfaceY + 0.05) {
                    shouldCarry = true;
                }
            } else {
                // Moving DOWN:
                // Carry if entity was standing on the floor before this step or penetrated floor
                boolean wasStandingOnFloor = (entityY >= oldFloorSurfaceY - 0.25 && entityY <= oldFloorSurfaceY + 0.35);
                boolean penetratedFloor = (entityY < newFloorSurfaceY && entityY >= newFloorSurfaceY - 0.5);
                if (wasStandingOnFloor || penetratedFloor) {
                    shouldCarry = true;
                }
            }

            if (shouldCarry) {
                // Keep entity resting exactly on top of the solid floor
                double targetY = newFloorSurfaceY;

                // Move entity directly to the floor surface without double-stepping or teleport feedback loops
                e.setPos(e.getX(), targetY, e.getZ());

                // Reset downward velocity and fall distance so they don't fall through or take damage
                Vec3 vel = e.getDeltaMovement();
                double newVelY = Math.max(vel.y, 0.0);
                e.setDeltaMovement(vel.x, newVelY, vel.z);
                e.fallDistance = 0.0F;
                e.setOnGround(true);

                // Corrective resync only if ServerPlayer suffers massive desync (> 1.5 blocks)
                if (!this.level().isClientSide() && e instanceof ServerPlayer sp) {
                    if (Math.abs(sp.getY() - targetY) > 1.5) {
                        sp.teleportTo((ServerLevel) sp.level(), sp.getX(), targetY, sp.getZ(), Relative.ROTATION, 0.0F, 0.0F, true);
                    }
                }
            }
        }
    }

    private void finishDocking(int finalY) {
        BlockPos floorAnchor = this.getFloorAnchorPos();
        BlockPos newAnchor = new BlockPos(floorAnchor.getX(), finalY, floorAnchor.getZ());

        // Restore all captured blocks back to the world
        for (ElevatorShaftHelper.CapturedBlockEntry entry : this.parsedBlocks) {
            BlockPos targetPos = newAnchor.offset(entry.relX(), entry.relY(), entry.relZ());
            this.level().setBlock(targetPos, entry.state(), 3);
        }

        // Lift entities safely onto the placed floor surface
        AABB finalPlatformBox = new AABB(
                newAnchor.getX() + minRelX - 0.1, finalY - 0.2, newAnchor.getZ() + minRelZ - 0.1,
                newAnchor.getX() + maxRelX + 1.1, finalY + 3.0, newAnchor.getZ() + maxRelZ + 1.1
        );
        List<Entity> riders = this.level().getEntities(this, finalPlatformBox, e -> e.isAlive() && !e.isPassenger());
        for (Entity e : riders) {
            if (!this.level().isClientSide() && e instanceof ServerPlayer sp) {
                sp.teleportTo((ServerLevel) sp.level(), sp.getX(), finalY + 1.0, sp.getZ(), Relative.ROTATION, 0.0F, 0.0F, true);
            } else {
                e.setPos(e.getX(), finalY + 1.0, e.getZ());
            }
            e.fallDistance = 0.0F;
            e.setOnGround(true);
        }

        // Set DOCKED state on any adjacent station for this final floor
        BlockPos railPos = this.getAnchorRailPos();
        int railX = railPos.getX();
        int railZ = railPos.getZ();
        for (int checkY = finalY; checkY <= finalY + 3; checkY++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos candidate = new BlockPos(railX + dx, checkY, railZ + dz);
                    BlockState st = this.level().getBlockState(candidate);
                    if (st.hasProperty(IndustrialElevatorStationBlock.DOCKED)) {
                        int stationFloorY = candidate.getY() - 2;
                        if (candidate.getY() - 1 == finalY) stationFloorY = finalY;
                        else if (candidate.getY() == finalY) stationFloorY = finalY;

                        if (stationFloorY == finalY) {
                            this.level().setBlock(candidate, st.setValue(IndustrialElevatorStationBlock.DOCKED, true), 3);
                        }
                    }
                }
            }
        }

        // Clunk / Docking arrival sounds
        this.level().playSound(null, newAnchor, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.9F);
        this.level().playSound(null, newAnchor, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6F, 1.8F);

        this.discard();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        int railX = input.getIntOr("RailX", 0);
        int railY = input.getIntOr("RailY", 0);
        int railZ = input.getIntOr("RailZ", 0);
        this.entityData.set(DATA_ANCHOR_RAIL, new BlockPos(railX, railY, railZ));

        int anchorX = input.getIntOr("AnchorX", 0);
        int anchorY = input.getIntOr("AnchorY", 0);
        int anchorZ = input.getIntOr("AnchorZ", 0);
        this.entityData.set(DATA_FLOOR_ANCHOR, new BlockPos(anchorX, anchorY, anchorZ));

        this.entityData.set(DATA_TARGET_Y, input.getIntOr("TargetY", 0));
        this.entityData.set(DATA_START_Y, input.getIntOr("StartY", 0));
        this.entityData.set(DATA_BLOCKS, input.getStringOr("Blocks", ""));

        this.refreshParsedBlocks();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        BlockPos rail = this.getAnchorRailPos();
        output.putInt("RailX", rail.getX());
        output.putInt("RailY", rail.getY());
        output.putInt("RailZ", rail.getZ());

        BlockPos anchor = this.getFloorAnchorPos();
        output.putInt("AnchorX", anchor.getX());
        output.putInt("AnchorY", anchor.getY());
        output.putInt("AnchorZ", anchor.getZ());

        output.putInt("TargetY", this.getTargetY());
        output.putInt("StartY", this.entityData.get(DATA_START_Y));
        output.putString("Blocks", this.entityData.get(DATA_BLOCKS));
    }
}
