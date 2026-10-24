package com.example.elevatorsandmore.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.example.elevatorsandmore.Config;
import com.example.elevatorsandmore.ElevatorsAndMore;
import com.example.elevatorsandmore.block.IndustrialControlPedestalBlock;
import com.example.elevatorsandmore.block.IndustrialElevatorFloorBlock;
import com.example.elevatorsandmore.block.IndustrialElevatorStationBlock;
import com.example.elevatorsandmore.block.IndustrialGuideRailBlock;
import com.example.elevatorsandmore.block.IndustrialSafetyRailingBlock;
import com.example.elevatorsandmore.block.entity.IndustrialElevatorStationBlockEntity;
import com.example.elevatorsandmore.entity.IndustrialElevatorCarriageEntity;
import com.example.elevatorsandmore.network.ElevatorActionPayload;
import com.example.elevatorsandmore.network.ElevatorSyncPayload;
import com.example.elevatorsandmore.network.FloorInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

public class ElevatorShaftHelper {

    public record DiscoveredStation(BlockPos pos, IndustrialElevatorStationBlockEntity entity, int floorY) {}

    /**
     * Finds the guide rail column associated with an origin (station, pedestal, or floor block).
     * Searches adjacent blocks, nearby blocks (up to 4 blocks), or flood-fills across connected elevator floor blocks.
     */
    public static @Nullable BlockPos findAdjacentRail(Level level, BlockPos origin) {
        if (level.getBlockState(origin).getBlock() instanceof IndustrialGuideRailBlock) {
            return origin;
        }

        // Direct horizontal neighbors
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = origin.relative(dir);
            if (level.getBlockState(neighbor).getBlock() instanceof IndustrialGuideRailBlock) {
                return neighbor;
            }
        }

        // If on or near an elevator floor, flood-fill across the platform to locate the guide rail
        BlockPos floorCandidate = origin;
        if (!(level.getBlockState(floorCandidate).getBlock() instanceof IndustrialElevatorFloorBlock)) {
            if (level.getBlockState(origin.below()).getBlock() instanceof IndustrialElevatorFloorBlock) {
                floorCandidate = origin.below();
            } else if (level.getBlockState(origin.below(2)).getBlock() instanceof IndustrialElevatorFloorBlock) {
                floorCandidate = origin.below(2);
            }
        }

        if (level.getBlockState(floorCandidate).getBlock() instanceof IndustrialElevatorFloorBlock) {
            Set<BlockPos> visited = new HashSet<>();
            Queue<BlockPos> queue = new ArrayDeque<>();
            visited.add(floorCandidate);
            queue.add(floorCandidate);

            while (!queue.isEmpty() && visited.size() <= 49) {
                BlockPos current = queue.poll();
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos next = current.relative(dir);
                    if (level.getBlockState(next).getBlock() instanceof IndustrialGuideRailBlock) {
                        return next;
                    }
                    if (!visited.contains(next) && level.getBlockState(next).getBlock() instanceof IndustrialElevatorFloorBlock) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }

        // Horizontal box search around origin within 4 blocks horizontally and 2 blocks vertically
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (level.getBlockState(candidate).getBlock() instanceof IndustrialGuideRailBlock) {
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Scans the vertical guide rail column and finds all elevator stations within 4 blocks horizontally.
     */
    public static List<DiscoveredStation> findStationsAlongRail(Level level, BlockPos railPos) {
        List<DiscoveredStation> stations = new ArrayList<>();
        if (!(level.getBlockState(railPos).getBlock() instanceof IndustrialGuideRailBlock)) {
            return stations;
        }

        int maxSpan = Config.MAX_SHAFT_HEIGHT.get();
        int minSearchY = Math.max(level.getMinY(), railPos.getY() - maxSpan);
        int maxSearchY = Math.min(level.getMaxY(), railPos.getY() + maxSpan);

        int railX = railPos.getX();
        int railZ = railPos.getZ();

        Set<BlockPos> foundPositions = new HashSet<>();

        for (int y = minSearchY; y <= maxSearchY; y++) {
            BlockPos currentRail = new BlockPos(railX, y, railZ);
            if (!(level.getBlockState(currentRail).getBlock() instanceof IndustrialGuideRailBlock)) {
                continue;
            }

            // Search in a 4x4 horizontal radius around the rail for stations
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos stationCandidate = new BlockPos(railX + dx, y, railZ + dz);
                    if (foundPositions.contains(stationCandidate)) continue;

                    BlockEntity be = level.getBlockEntity(stationCandidate);
                    if (be instanceof IndustrialElevatorStationBlockEntity stationBE) {
                        foundPositions.add(stationCandidate);

                        // If the station was placed at eye level (e.g. Y=65 above floor Y=64),
                        // detect if there's a floor block directly at Y-1 or Y-2
                        int resolvedFloorY = y;
                        if (hasFloorNearby(level, railPos, y - 1)) {
                            resolvedFloorY = y - 1;
                        } else if (hasFloorNearby(level, railPos, y - 2)) {
                            resolvedFloorY = y - 2;
                        } else if (hasFloorNearby(level, railPos, y)) {
                            resolvedFloorY = y;
                        } else {
                            // Default: station is at eye level, floor is Y-1
                            resolvedFloorY = stationCandidate.getY() - 1;
                        }

                        stations.add(new DiscoveredStation(stationCandidate, stationBE, resolvedFloorY));
                    }
                }
            }
        }

        stations.sort(Comparator.comparingInt(DiscoveredStation::floorY).reversed());
        return stations;
    }

    private static boolean hasFloorNearby(Level level, BlockPos railPos, int checkY) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos cand = new BlockPos(railPos.getX(), checkY, railPos.getZ()).relative(dir);
            if (level.getBlockState(cand).getBlock() instanceof IndustrialElevatorFloorBlock) {
                return true;
            }
        }
        return false;
    }

    public static @Nullable IndustrialElevatorCarriageEntity findMovingCarriage(Level level, BlockPos railPos) {
        int maxSpan = Config.MAX_SHAFT_HEIGHT.get();
        AABB shaftBox = new AABB(
                railPos.getX() - 8, level.getMinY(), railPos.getZ() - 8,
                railPos.getX() + 8, level.getMaxY(), railPos.getZ() + 8
        );
        List<IndustrialElevatorCarriageEntity> carriages = level.getEntitiesOfClass(
                IndustrialElevatorCarriageEntity.class,
                shaftBox,
                c -> c.isAlive() && c.getAnchorRailPos().getX() == railPos.getX() && c.getAnchorRailPos().getZ() == railPos.getZ()
        );
        return carriages.isEmpty() ? null : carriages.get(0);
    }

    /**
     * Finds where the elevator floor platform currently is along the rail.
     */
    public static @Nullable Integer findCurrentPlatformY(Level level, BlockPos railPos) {
        int maxSpan = Config.MAX_SHAFT_HEIGHT.get();
        int minSearchY = Math.max(level.getMinY(), railPos.getY() - maxSpan);
        int maxSearchY = Math.min(level.getMaxY(), railPos.getY() + maxSpan);

        int railX = railPos.getX();
        int railZ = railPos.getZ();

        for (int y = minSearchY; y <= maxSearchY; y++) {
            if (!(level.getBlockState(new BlockPos(railX, y, railZ)).getBlock() instanceof IndustrialGuideRailBlock)) {
                continue;
            }
            if (hasFloorNearby(level, railPos, y)) {
                return y;
            }
        }
        return null;
    }

    public static List<CapturedBlockEntry> gatherPlatformBlocks(Level level, BlockPos railPos, int platformY) {
        List<CapturedBlockEntry> captured = new ArrayList<>();
        BlockPos floorAnchor = findFloorAnchor(level, railPos, platformY);

        if (floorAnchor == null) {
            return captured;
        }

        int maxBlocks = Config.MAX_PLATFORM_BLOCKS.get();
        int maxSpan = Config.MAX_HORIZONTAL_SPAN.get();

        Set<BlockPos> visitedFloors = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        visitedFloors.add(floorAnchor);
        queue.add(floorAnchor);

        while (!queue.isEmpty() && visitedFloors.size() <= maxBlocks) {
            BlockPos current = queue.poll();
            BlockState floorState = level.getBlockState(current);

            // Record floor block
            captured.add(new CapturedBlockEntry(
                    current.getX() - floorAnchor.getX(),
                    0,
                    current.getZ() - floorAnchor.getZ(),
                    floorState
            ));

            // Check if there is an accessory (Safety Railing or Control Pedestal) directly on top
            BlockPos topPos = current.above();
            BlockState topState = level.getBlockState(topPos);
            if (topState.getBlock() instanceof IndustrialSafetyRailingBlock ||
                topState.getBlock() instanceof IndustrialControlPedestalBlock) {
                captured.add(new CapturedBlockEntry(
                        topPos.getX() - floorAnchor.getX(),
                        1,
                        topPos.getZ() - floorAnchor.getZ(),
                        topState
                ));
            }

            // Expand flood fill to horizontal neighbor floor blocks
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(dir);
                if (visitedFloors.contains(next)) continue;

                int dx = Math.abs(next.getX() - floorAnchor.getX());
                int dz = Math.abs(next.getZ() - floorAnchor.getZ());
                if (dx > maxSpan || dz > maxSpan) continue;

                if (level.getBlockState(next).getBlock() instanceof IndustrialElevatorFloorBlock) {
                    visitedFloors.add(next);
                    queue.add(next);
                }
            }
        }

        return captured;
    }

    public static @Nullable BlockPos findFloorAnchor(Level level, BlockPos railPos, int platformY) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos cand = new BlockPos(railPos.getX(), platformY, railPos.getZ()).relative(dir);
            if (level.getBlockState(cand).getBlock() instanceof IndustrialElevatorFloorBlock) {
                return cand;
            }
        }
        return null;
    }

    public static void openElevatorGuiFromStation(Level level, BlockPos stationPos, ServerPlayer player) {
        BlockPos railPos = findAdjacentRail(level, stationPos);
        if (railPos == null) {
            player.sendSystemMessage(Component.literal("§c[Elevators & More] No Industrial Guide Rail found near this station! Make sure a Guide Rail column is within 4 blocks."));
            return;
        }
        syncAndOpenGui(level, stationPos, railPos, player);
    }

    public static void openElevatorGuiFromPlatform(Level level, BlockPos originPos, ServerPlayer player) {
        BlockPos railPos = findAdjacentRail(level, originPos);
        if (railPos == null) {
            player.sendSystemMessage(Component.literal("§c[Elevators & More] No Industrial Guide Rail found connected to this elevator platform!"));
            return;
        }
        syncAndOpenGui(level, originPos, railPos, player);
    }

    private static void syncAndOpenGui(Level level, BlockPos originPos, BlockPos railPos, ServerPlayer player) {
        List<DiscoveredStation> stations = findStationsAlongRail(level, railPos);
        IndustrialElevatorCarriageEntity movingCarriage = findMovingCarriage(level, railPos);

        Integer stationaryY = findCurrentPlatformY(level, railPos);
        int currentY = movingCarriage != null ? (int) Math.round(movingCarriage.getY()) : (stationaryY != null ? stationaryY : originPos.getY());
        boolean isMoving = movingCarriage != null;

        List<FloorInfo> floorInfos = new ArrayList<>();
        for (DiscoveredStation ds : stations) {
            boolean docked = !isMoving && Math.abs(ds.floorY() - currentY) <= 1;
            floorInfos.add(new FloorInfo(ds.pos(), ds.entity().getFloorName(), ds.floorY(), docked));
        }

        PacketDistributor.sendToPlayer(player, new ElevatorSyncPayload(
                originPos,
                railPos,
                floorInfos,
                currentY,
                isMoving
        ));
    }

    public static void handlePlayerAction(ServerPlayer player, ElevatorActionPayload payload) {
        ServerLevel level = player.level();
        BlockPos railPos = findAdjacentRail(level, payload.originPos());
        if (railPos == null) {
            player.sendSystemMessage(Component.literal("§c[Elevators & More] Could not find the Guide Rail column!"));
            return;
        }

        switch (payload.actionType()) {
            case ElevatorActionPayload.ACTION_TRAVEL -> {
                startElevator(level, railPos, payload.originPos(), payload.targetY(), player);
            }
            case ElevatorActionPayload.ACTION_CALL -> {
                // Find floor Y for this station
                int callFloorY = payload.originPos().getY();
                if (hasFloorNearby(level, railPos, payload.originPos().getY() - 1)) {
                    callFloorY = payload.originPos().getY() - 1;
                } else if (hasFloorNearby(level, railPos, payload.originPos().getY() - 2)) {
                    callFloorY = payload.originPos().getY() - 2;
                } else {
                    callFloorY = payload.originPos().getY() - 1; // Default floor height below eye-level station
                }
                startElevator(level, railPos, payload.originPos(), callFloorY, player);
            }
            case ElevatorActionPayload.ACTION_RENAME -> {
                BlockEntity be = level.getBlockEntity(payload.originPos());
                if (be instanceof IndustrialElevatorStationBlockEntity stationBE) {
                    stationBE.setFloorName(payload.newName());
                    player.sendSystemMessage(Component.literal("§aFloor renamed to: " + stationBE.getFloorName()));
                }
            }
            case ElevatorActionPayload.ACTION_EMERGENCY_STOP -> {
                IndustrialElevatorCarriageEntity carriage = findMovingCarriage(level, railPos);
                if (carriage != null) {
                    carriage.emergencyStop();
                    player.sendSystemMessage(Component.literal("§eElevator Emergency Stop triggered!"));
                }
            }
        }
    }

    public static boolean startElevator(ServerLevel level, BlockPos railPos, BlockPos originPos, int targetFloorY, @Nullable ServerPlayer initiator) {
        IndustrialElevatorCarriageEntity movingCarriage = findMovingCarriage(level, railPos);
        if (movingCarriage != null) {
            // Already moving - redirect target Y
            movingCarriage.setTargetY(targetFloorY);
            if (initiator != null) {
                initiator.sendSystemMessage(Component.literal("§eElevator redirected to Y=" + targetFloorY));
            }
            return true;
        }

        // Find where the stationary platform is located along the rail
        Integer currentPlatformY = findCurrentPlatformY(level, railPos);
        if (currentPlatformY == null) {
            if (initiator != null) {
                initiator.sendSystemMessage(Component.literal("§c[Elevators & More] No elevator platform found! Place at least one 'Industrial Elevator Floor' block touching the Guide Rail column."));
            }
            return false;
        }

        int departureY = currentPlatformY;
        BlockPos floorAnchor = findFloorAnchor(level, railPos, departureY);

        if (floorAnchor == null) {
            if (initiator != null) {
                initiator.sendSystemMessage(Component.literal("§c[Elevators & More] Platform floor anchor could not be resolved!"));
            }
            return false;
        }

        if (departureY == targetFloorY) {
            if (initiator != null) {
                initiator.sendSystemMessage(Component.literal("§7Elevator is already at this floor."));
            }
            return false;
        }

        // Gather all connected floor and accessory blocks
        List<CapturedBlockEntry> blocks = gatherPlatformBlocks(level, railPos, departureY);
        if (blocks.isEmpty()) {
            if (initiator != null) {
                initiator.sendSystemMessage(Component.literal("§cPlatform blocks could not be gathered."));
            }
            return false;
        }

        // Remove blocks from the world cleanly
        for (CapturedBlockEntry entry : blocks) {
            BlockPos worldPos = floorAnchor.offset(entry.relX(), entry.relY(), entry.relZ());
            level.setBlock(worldPos, Blocks.AIR.defaultBlockState(), 3);
        }

        // Update station docked states
        List<DiscoveredStation> stations = findStationsAlongRail(level, railPos);
        for (DiscoveredStation ds : stations) {
            BlockState st = level.getBlockState(ds.pos());
            if (st.hasProperty(IndustrialElevatorStationBlock.DOCKED) && st.getValue(IndustrialElevatorStationBlock.DOCKED)) {
                level.setBlock(ds.pos(), st.setValue(IndustrialElevatorStationBlock.DOCKED, false), 3);
            }
        }

        // Spawn carriage entity
        IndustrialElevatorCarriageEntity carriage = new IndustrialElevatorCarriageEntity(
                ElevatorsAndMore.INDUSTRIAL_ELEVATOR_CARRIAGE_ENTITY.get(),
                level
        );
        carriage.setupPlatform(railPos, floorAnchor, departureY, targetFloorY, blocks);
        level.addFreshEntity(carriage);

        // Play departure sound
        level.playSound(null, floorAnchor, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, floorAnchor, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0F, 0.9F);

        return true;
    }

    public record CapturedBlockEntry(int relX, int relY, int relZ, BlockState state) {}
}
