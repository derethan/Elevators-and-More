package com.example.elevatorsandmore.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.example.elevatorsandmore.Config;
import com.example.elevatorsandmore.block.IndustrialControlPedestalBlock;
import com.example.elevatorsandmore.block.IndustrialElevatorStationBlock;
import com.example.elevatorsandmore.block.IndustrialSafetyRailingBlock;
import com.example.elevatorsandmore.util.ElevatorShaftHelper;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
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

    public static class CarriedMinecartInfo {
        public final UUID uuid;
        public final double relX;
        public final double relZ;
        public final float yRot;
        public final double velX;
        public final double velZ;
        public final boolean hasRail;

        public CarriedMinecartInfo(UUID uuid, double relX, double relZ, float yRot, double velX, double velZ, boolean hasRail) {
            this.uuid = uuid;
            this.relX = relX;
            this.relZ = relZ;
            this.yRot = yRot;
            this.velX = velX;
            this.velZ = velZ;
            this.hasRail = hasRail;
        }
    }

    private final List<ElevatorShaftHelper.CapturedBlockEntry> serverCapturedBlocks = new ArrayList<>();
    private final Map<UUID, CarriedMinecartInfo> carriedCarts = new HashMap<>();
    private final List<ElevatorShaftHelper.CapturedBlockEntry> parsedBlocks = new ArrayList<>();
    private String lastParsedBlocksStr = "";
    private int minRelX = 0, maxRelX = 0, minRelZ = 0, maxRelZ = 0;
    private boolean allowClientPosUpdate = false;
    private long lastMovedGameTime = -1L;
    private double lastStep = 0.0;

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

        this.serverCapturedBlocks.clear();
        this.serverCapturedBlocks.addAll(blocks);

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

        if (!this.level().isClientSide()) {
            scanAndCaptureMinecarts(floorAnchor, startY);
        }
    }

    public boolean isRailAtRelative(double relX, double relZ) {
        int rx = (int) Math.floor(relX);
        int rz = (int) Math.floor(relZ);
        List<ElevatorShaftHelper.CapturedBlockEntry> blocks = this.serverCapturedBlocks.isEmpty() ? getParsedBlocks() : this.serverCapturedBlocks;
        for (ElevatorShaftHelper.CapturedBlockEntry b : blocks) {
            if (b.relX() == rx && b.relZ() == rz && b.relY() == 1) {
                if (b.state().getBlock() instanceof BaseRailBlock) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isRailUnderCart(AbstractMinecart cart) {
        BlockPos anchor = this.getFloorAnchorPos();
        return isRailAtRelative(cart.getX() - anchor.getX(), cart.getZ() - anchor.getZ());
    }

    public @Nullable CarriedMinecartInfo getCarriedCartInfo(UUID uuid) {
        return this.carriedCarts.get(uuid);
    }

    private void scanAndCaptureMinecarts(BlockPos floorAnchor, int startY) {
        this.carriedCarts.clear();
        double boxMinX = floorAnchor.getX() + minRelX;
        double boxMaxX = floorAnchor.getX() + maxRelX + 1.0;
        double boxMinZ = floorAnchor.getZ() + minRelZ;
        double boxMaxZ = floorAnchor.getZ() + maxRelZ + 1.0;

        AABB queryBox = new AABB(
                boxMinX - 0.2, startY + 0.5, boxMinZ - 0.2,
                boxMaxX + 0.2, startY + 2.5, boxMaxZ + 0.2
        );

        List<AbstractMinecart> carts = this.level().getEntitiesOfClass(
                AbstractMinecart.class,
                queryBox,
                c -> c.isAlive()
        );

        for (AbstractMinecart cart : carts) {
            double relX = cart.getX() - floorAnchor.getX();
            double relZ = cart.getZ() - floorAnchor.getZ();
            Vec3 vel = cart.getDeltaMovement();
            boolean hasRail = isRailAtRelative(relX, relZ);
            this.carriedCarts.put(cart.getUUID(), new CarriedMinecartInfo(
                    cart.getUUID(),
                    relX,
                    relZ,
                    cart.getYRot(),
                    vel.x,
                    vel.z,
                    hasRail
            ));
        }
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
    public long getLastMovedGameTime() { return lastMovedGameTime; }
    public double getLastStep() { return lastStep; }

    @Override
    public void setPos(double x, double y, double z) {
        if (this.level() != null && this.level().isClientSide() && !this.allowClientPosUpdate && this.tickCount > 2) {
            // Ignore delayed server delta position packets unless there is a major desync (> 1.5 blocks),
            // so client-side tick() interpolation stays 100% smooth without 3-tick packet snapping.
            if (Math.abs(y - this.getY()) < 1.5 && Math.abs(x - this.getX()) < 0.5 && Math.abs(z - this.getZ()) < 0.5) {
                return;
            }
        }
        super.setPos(x, y, z);
    }

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
            // Only allow opening the GUI on a moving platform by right-clicking the Elevator Control Pedestal
            BlockPos anchor = this.getFloorAnchorPos();
            boolean clickedPedestal = false;
            for (ElevatorShaftHelper.CapturedBlockEntry entry : this.parsedBlocks) {
                if (entry.state().getBlock() instanceof IndustrialControlPedestalBlock
                        || (entry.state().getBlock() instanceof IndustrialSafetyRailingBlock
                            && entry.state().getValue(IndustrialSafetyRailingBlock.PEDESTAL))) {
                    BlockPos pedestalPos = new BlockPos(
                            anchor.getX() + entry.relX(),
                            (int) Math.round(this.getY()) + entry.relY(),
                            anchor.getZ() + entry.relZ()
                    );
                    if (location.distanceToSqr(Vec3.atCenterOf(pedestalPos)) <= 3.0) {
                        clickedPedestal = true;
                        break;
                    }
                }
            }
            if (clickedPedestal) {
                ElevatorShaftHelper.openElevatorGuiFromPlatform(this.level(), this.blockPosition(), serverPlayer);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
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

        // Guard against moving off the Guide Rail or into solid ground blocks
        BlockPos railPos = this.getAnchorRailPos();
        if (step < 0) {
            int nextIntY = (int) Math.floor(nextY);
            int curIntY = (int) Math.floor(currentY);
            if (nextIntY < curIntY && !ElevatorShaftHelper.canPlatformOccupyY(this.level(), railPos, anchor, this.parsedBlocks, nextIntY)) {
                int safeDockY = curIntY;
                this.setTargetY(safeDockY);
                if (!this.level().isClientSide()) {
                    finishDocking(safeDockY);
                }
                return;
            }
        } else if (step > 0) {
            int nextIntY = (int) Math.ceil(nextY);
            int curIntY = (int) Math.ceil(currentY);
            if (nextIntY > curIntY && !ElevatorShaftHelper.canPlatformOccupyY(this.level(), railPos, anchor, this.parsedBlocks, nextIntY)) {
                int safeDockY = curIntY;
                this.setTargetY(safeDockY);
                if (!this.level().isClientSide()) {
                    finishDocking(safeDockY);
                }
                return;
            }
        }

        // Advance platform position and lock both yo and yOld for sub-tick interpolation
        this.yo = currentY;
        this.yOld = currentY;
        this.allowClientPosUpdate = true;
        try {
            this.setPos(this.getX(), nextY, this.getZ());
        } finally {
            this.allowClientPosUpdate = false;
        }
        this.lastMovedGameTime = this.level().getGameTime();
        this.lastStep = step;

        // Carry passengers & standing entities smoothly
        carryStandingEntities(boxMinX, boxMaxX, boxMinZ, boxMaxZ, currentY, nextY, step);
    }

    private void carryStandingEntities(
            double minX, double maxX, double minZ, double maxZ,
            double oldY, double newY, double step
    ) {
        double oldFloorSurfaceY = oldY + 1.0;
        double newFloorSurfaceY = newY + 1.0;
        BlockPos anchor = this.getFloorAnchorPos();

        // Expanded query box to detect entities standing on or near the moving platform
        AABB searchBox = new AABB(
                minX - 0.2,
                Math.min(oldY, newY) - 0.5,
                minZ - 0.2,
                maxX + 0.2,
                Math.max(oldY, newY) + 3.0,
                maxZ + 0.2
        );

        // 1. Process AbstractMinecart / Locomotives
        List<AbstractMinecart> foundCarts = this.level().getEntitiesOfClass(
                AbstractMinecart.class,
                searchBox,
                c -> c.isAlive()
        );
        for (AbstractMinecart cart : foundCarts) {
            AABB cb = cart.getBoundingBox();
            if (cb.maxX <= minX || cb.minX >= maxX || cb.maxZ <= minZ || cb.minZ >= maxZ) {
                continue;
            }
            if (!this.carriedCarts.containsKey(cart.getUUID())) {
                double relX = cart.getX() - anchor.getX();
                double relZ = cart.getZ() - anchor.getZ();
                Vec3 vel = cart.getDeltaMovement();
                boolean hasRail = isRailAtRelative(relX, relZ);
                this.carriedCarts.put(cart.getUUID(), new CarriedMinecartInfo(
                        cart.getUUID(),
                        relX,
                        relZ,
                        cart.getYRot(),
                        vel.x,
                        vel.z,
                        hasRail
                ));
            }
        }

        // Lock position & sync vertical movement for all carried minecarts / locomotives
        for (CarriedMinecartInfo info : this.carriedCarts.values()) {
            Entity entity = null;
            if (this.level() instanceof ServerLevel sl) {
                entity = sl.getEntity(info.uuid);
            } else {
                for (AbstractMinecart c : foundCarts) {
                    if (c.getUUID().equals(info.uuid)) {
                        entity = c;
                        break;
                    }
                }
            }

            if (entity instanceof AbstractMinecart cart && cart.isAlive()) {
                double targetX = anchor.getX() + info.relX;
                double targetZ = anchor.getZ() + info.relZ;
                double targetY = newFloorSurfaceY + (info.hasRail ? 0.0625 : 0.0);
                double oldCartY = oldFloorSurfaceY + (info.hasRail ? 0.0625 : 0.0);

                cart.setPos(targetX, targetY, targetZ);
                cart.yo = oldCartY;
                cart.yOld = oldCartY;
                cart.xo = targetX;
                cart.xOld = targetX;
                cart.zo = targetZ;
                cart.zOld = targetZ;
                cart.setYRot(info.yRot);
                cart.yRotO = info.yRot;
                cart.setDeltaMovement(0.0, step, 0.0);
                cart.fallDistance = 0.0F;
                cart.setOnGround(true);

                // Sync vertical camera & interpolation for passengers riding inside this cart
                for (Entity passenger : cart.getPassengers()) {
                    passenger.fallDistance = 0.0F;
                    passenger.setOnGround(true);
                    passenger.yo = passenger.getY() - step;
                    passenger.yOld = passenger.getY() - step;
                }
            }
        }

        // 2. Process other entities (Players and mobs not in vehicles)
        List<Entity> riders = this.level().getEntities(this, searchBox, e -> e.isAlive() && !e.isPassenger() && !(e instanceof AbstractMinecart));
        for (Entity e : riders) {
            AABB eb = e.getBoundingBox();
            if (eb.maxX <= minX || eb.minX >= maxX || eb.maxZ <= minZ || eb.minZ >= maxZ) {
                continue;
            }

            double entityY = e.getY();
            boolean shouldCarry = false;

            if (step > 0) {
                if (entityY >= oldFloorSurfaceY - 0.5 && entityY <= newFloorSurfaceY + 0.1) {
                    shouldCarry = true;
                }
            } else {
                if (entityY >= newFloorSurfaceY - 0.5 && entityY <= oldFloorSurfaceY + 1.5) {
                    if (e.getDeltaMovement().y > 0.15 && entityY > newFloorSurfaceY + 0.1) {
                        shouldCarry = false;
                    } else {
                        shouldCarry = true;
                    }
                }
            }

            if (shouldCarry) {
                double targetY = newFloorSurfaceY;
                e.setPos(e.getX(), targetY, e.getZ());
                e.yo = oldFloorSurfaceY;
                e.yOld = oldFloorSurfaceY;

                Vec3 vel = e.getDeltaMovement();
                double newVelY = step < 0 ? step : Math.max(vel.y, 0.0);
                e.setDeltaMovement(vel.x, newVelY, vel.z);
                e.fallDistance = 0.0F;
                e.setOnGround(true);

                if (!this.level().isClientSide() && e instanceof ServerPlayer sp) {
                    if (Math.abs(sp.getY() - targetY) > 1.5) {
                        sp.teleportTo((ServerLevel) sp.level(), sp.getX(), targetY, sp.getZ(), Relative.ROTATION, 0.0F, 0.0F, true);
                    }
                }
            }
        }
    }

    public static @Nullable IndustrialElevatorCarriageEntity getCarriageBeneath(Entity entity) {
        AABB box = entity.getBoundingBox().inflate(0.1, 1.0, 0.1);
        List<IndustrialElevatorCarriageEntity> list = entity.level().getEntitiesOfClass(
                IndustrialElevatorCarriageEntity.class,
                box,
                c -> c.isAlive() && c.isEntityOnPlatform(entity)
        );
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean isEntityOnPlatform(Entity e) {
        AABB eb = e.getBoundingBox();
        BlockPos anchor = this.getFloorAnchorPos();
        double minX = anchor.getX() + minRelX;
        double maxX = anchor.getX() + maxRelX + 1.0;
        double minZ = anchor.getZ() + minRelZ;
        double maxZ = anchor.getZ() + maxRelZ + 1.0;

        if (eb.maxX <= minX || eb.minX >= maxX || eb.maxZ <= minZ || eb.minZ >= maxZ) {
            return false;
        }

        double floorTop = this.getY() + 1.0;
        return e.getY() >= floorTop - 0.5 && e.getY() <= floorTop + 2.0;
    }

    private void finishDocking(int finalY) {
        BlockPos railPos = this.getAnchorRailPos();
        BlockPos floorAnchor = this.getFloorAnchorPos();
        List<ElevatorShaftHelper.CapturedBlockEntry> blocksToPlace = this.serverCapturedBlocks.isEmpty() ? this.parsedBlocks : this.serverCapturedBlocks;

        // Ensure finalY is on the Guide Rail and does not overwrite solid world blocks
        if (!ElevatorShaftHelper.canPlatformOccupyY(this.level(), railPos, floorAnchor, blocksToPlace, finalY)) {
            if (ElevatorShaftHelper.canPlatformOccupyY(this.level(), railPos, floorAnchor, blocksToPlace, finalY + 1)) {
                finalY = finalY + 1;
            } else if (ElevatorShaftHelper.canPlatformOccupyY(this.level(), railPos, floorAnchor, blocksToPlace, finalY - 1)) {
                finalY = finalY - 1;
            }
        }

        BlockPos newAnchor = new BlockPos(floorAnchor.getX(), finalY, floorAnchor.getZ());

        // Pass 1: Restore relY == 0 floor blocks FIRST
        for (ElevatorShaftHelper.CapturedBlockEntry entry : blocksToPlace) {
            if (entry.relY() == 0) {
                BlockPos targetPos = newAnchor.offset(entry.relX(), 0, entry.relZ());
                if (ElevatorShaftHelper.isElevatorOrReplaceable(this.level().getBlockState(targetPos))) {
                    this.level().setBlock(targetPos, entry.state(), 3);
                }
            }
        }

        // Pass 2: Restore relY == 1 surface blocks, rails, and accessories SECOND (with BlockEntity NBT)
        for (ElevatorShaftHelper.CapturedBlockEntry entry : blocksToPlace) {
            if (entry.relY() == 1) {
                BlockPos targetPos = newAnchor.offset(entry.relX(), 1, entry.relZ());
                BlockState existing = this.level().getBlockState(targetPos);
                if (existing.isAir() || existing.canBeReplaced() || ElevatorShaftHelper.isElevatorOrReplaceable(existing)) {
                    this.level().setBlock(targetPos, entry.state(), 3);
                    if (entry.nbt() != null) {
                        BlockEntity be = this.level().getBlockEntity(targetPos);
                        if (be != null) {
                            be.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, this.level().registryAccess(), entry.nbt()));
                            be.setChanged();
                        }
                    }
                }
            }
        }

        // Pass 3: Re-evaluate and reconnect platform rails & adjacent floor rails
        ElevatorShaftHelper.reconnectPlatformRails(this.level(), newAnchor, blocksToPlace);

        // Pass 4: Restore carried minecarts and locomotives onto the rails
        for (CarriedMinecartInfo info : this.carriedCarts.values()) {
            Entity entity = this.level() instanceof ServerLevel sl ? sl.getEntity(info.uuid) : null;
            if (entity instanceof AbstractMinecart cart && cart.isAlive()) {
                double finalCartX = newAnchor.getX() + info.relX;
                double finalCartZ = newAnchor.getZ() + info.relZ;
                double finalCartY = finalY + 1.0 + (info.hasRail ? 0.0625 : 0.0);

                cart.setPos(finalCartX, finalCartY, finalCartZ);
                cart.yo = finalCartY;
                cart.yOld = finalCartY;
                cart.xo = finalCartX;
                cart.xOld = finalCartX;
                cart.zo = finalCartZ;
                cart.zOld = finalCartZ;
                cart.setYRot(info.yRot);
                cart.yRotO = info.yRot;
                cart.setOnRails(info.hasRail);
                cart.setDeltaMovement(info.velX, 0.0, info.velZ);
                cart.fallDistance = 0.0F;
                cart.setOnGround(true);
            }
        }

        // Pass 5: Lift standing entities safely onto the placed floor surface
        AABB finalPlatformBox = new AABB(
                newAnchor.getX() + minRelX - 0.1, finalY - 0.2, newAnchor.getZ() + minRelZ - 0.1,
                newAnchor.getX() + maxRelX + 1.1, finalY + 3.0, newAnchor.getZ() + maxRelZ + 1.1
        );
        List<Entity> riders = this.level().getEntities(this, finalPlatformBox, e -> e.isAlive() && !e.isPassenger() && !(e instanceof AbstractMinecart));
        for (Entity e : riders) {
            if (!this.level().isClientSide() && e instanceof ServerPlayer sp) {
                sp.teleportTo((ServerLevel) sp.level(), sp.getX(), finalY + 1.0, sp.getZ(), Relative.ROTATION, 0.0F, 0.0F, true);
            } else {
                e.setPos(e.getX(), finalY + 1.0, e.getZ());
            }
            e.yo = finalY + 1.0;
            e.yOld = finalY + 1.0;
            Vec3 dm = e.getDeltaMovement();
            e.setDeltaMovement(dm.x, 0.0, dm.z);
            e.fallDistance = 0.0F;
            e.setOnGround(true);
        }

        // Pass 6: Set DOCKED state on any station within 2 blocks of the outside perimeter of the floor
        int stationMinX = newAnchor.getX() + minRelX - 2;
        int stationMaxX = newAnchor.getX() + maxRelX + 2;
        int stationMinZ = newAnchor.getZ() + minRelZ - 2;
        int stationMaxZ = newAnchor.getZ() + maxRelZ + 2;
        for (int checkY = finalY; checkY <= finalY + 3; checkY++) {
            for (int sx = stationMinX; sx <= stationMaxX; sx++) {
                for (int sz = stationMinZ; sz <= stationMaxZ; sz++) {
                    BlockPos candidate = new BlockPos(sx, checkY, sz);
                    BlockState st = this.level().getBlockState(candidate);
                    if (st.hasProperty(IndustrialElevatorStationBlock.DOCKED)) {
                        int stationFloorY = ElevatorShaftHelper.resolveStationFloorY(this.level(), railPos, candidate);
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

        this.serverCapturedBlocks.clear();
        int blockCount = input.getIntOr("ServerBlockCount", 0);
        for (int i = 0; i < blockCount; i++) {
            int rx = input.getIntOr("SB_rx_" + i, 0);
            int ry = input.getIntOr("SB_ry_" + i, 0);
            int rz = input.getIntOr("SB_rz_" + i, 0);
            int stId = input.getIntOr("SB_st_" + i, 0);
            String nbtStr = input.getStringOr("SB_nb_" + i, "");
            CompoundTag nbt = null;
            if (!nbtStr.isEmpty()) {
                try {
                    nbt = TagParser.parseCompoundFully(nbtStr);
                } catch (CommandSyntaxException ignored) {}
            }
            this.serverCapturedBlocks.add(new ElevatorShaftHelper.CapturedBlockEntry(rx, ry, rz, Block.stateById(stId), nbt));
        }

        this.carriedCarts.clear();
        int cartCount = input.getIntOr("CarriedCartCount", 0);
        for (int i = 0; i < cartCount; i++) {
            String uuidStr = input.getStringOr("Cart_uuid_" + i, "");
            if (!uuidStr.isEmpty()) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    double relX = input.getDoubleOr("Cart_rx_" + i, 0.0);
                    double relZ = input.getDoubleOr("Cart_rz_" + i, 0.0);
                    float yRot = input.getFloatOr("Cart_rot_" + i, 0.0F);
                    double vx = input.getDoubleOr("Cart_vx_" + i, 0.0);
                    double vz = input.getDoubleOr("Cart_vz_" + i, 0.0);
                    boolean hr = input.getBooleanOr("Cart_hr_" + i, false);
                    this.carriedCarts.put(uuid, new CarriedMinecartInfo(uuid, relX, relZ, yRot, vx, vz, hr));
                } catch (IllegalArgumentException ignored) {}
            }
        }

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

        output.putInt("ServerBlockCount", this.serverCapturedBlocks.size());
        for (int i = 0; i < this.serverCapturedBlocks.size(); i++) {
            ElevatorShaftHelper.CapturedBlockEntry b = this.serverCapturedBlocks.get(i);
            output.putInt("SB_rx_" + i, b.relX());
            output.putInt("SB_ry_" + i, b.relY());
            output.putInt("SB_rz_" + i, b.relZ());
            output.putInt("SB_st_" + i, Block.getId(b.state()));
            if (b.nbt() != null) {
                output.putString("SB_nb_" + i, b.nbt().toString());
            }
        }

        output.putInt("CarriedCartCount", this.carriedCarts.size());
        int cartIdx = 0;
        for (CarriedMinecartInfo info : this.carriedCarts.values()) {
            output.putString("Cart_uuid_" + cartIdx, info.uuid.toString());
            output.putDouble("Cart_rx_" + cartIdx, info.relX);
            output.putDouble("Cart_rz_" + cartIdx, info.relZ);
            output.putFloat("Cart_rot_" + cartIdx, info.yRot);
            output.putDouble("Cart_vx_" + cartIdx, info.velX);
            output.putDouble("Cart_vz_" + cartIdx, info.velZ);
            output.putBoolean("Cart_hr_" + cartIdx, info.hasRail);
            cartIdx++;
        }
    }
}
