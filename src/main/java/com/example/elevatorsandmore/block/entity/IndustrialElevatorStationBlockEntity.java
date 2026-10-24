package com.example.elevatorsandmore.block.entity;

import com.example.elevatorsandmore.ElevatorsAndMore;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class IndustrialElevatorStationBlockEntity extends BlockEntity {
    private String floorName = "";
    private boolean redstonePowered = false;

    public IndustrialElevatorStationBlockEntity(BlockPos pos, BlockState blockState) {
        super(ElevatorsAndMore.INDUSTRIAL_ELEVATOR_STATION_BLOCK_ENTITY.get(), pos, blockState);
        this.floorName = "Floor Y=" + (pos.getY() - 2);
    }

    public boolean isRedstonePowered() {
        return this.redstonePowered;
    }

    public void setRedstonePowered(boolean redstonePowered) {
        this.redstonePowered = redstonePowered;
        this.setChanged();
    }

    public String getFloorName() {
        if (this.floorName == null || this.floorName.trim().isEmpty() || this.floorName.equals("Floor Y=" + this.worldPosition.getY())) {
            return "Floor Y=" + (this.worldPosition.getY() - 2);
        }
        return this.floorName;
    }

    public void setFloorName(String newName) {
        String cleaned = newName == null ? "" : newName.trim();
        if (cleaned.length() > 32) {
            cleaned = cleaned.substring(0, 32);
        }
        this.floorName = cleaned.isEmpty() ? ("Floor Y=" + (this.worldPosition.getY() - 2)) : cleaned;
        this.setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("FloorName", this.floorName);
        output.putBoolean("RedstonePowered", this.redstonePowered);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.floorName = input.getStringOr("FloorName", "Floor Y=" + (this.worldPosition.getY() - 2));
        this.redstonePowered = input.getBooleanOr("RedstonePowered", false);
    }
}
