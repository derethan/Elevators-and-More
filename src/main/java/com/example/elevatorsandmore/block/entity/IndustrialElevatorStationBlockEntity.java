package com.example.elevatorsandmore.block.entity;

import com.example.elevatorsandmore.ElevatorsAndMore;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class IndustrialElevatorStationBlockEntity extends BlockEntity {
    private String floorName = "";

    public IndustrialElevatorStationBlockEntity(BlockPos pos, BlockState blockState) {
        super(ElevatorsAndMore.INDUSTRIAL_ELEVATOR_STATION_BLOCK_ENTITY.get(), pos, blockState);
        this.floorName = "Floor Y=" + pos.getY();
    }

    public String getFloorName() {
        if (this.floorName == null || this.floorName.trim().isEmpty()) {
            return "Floor Y=" + this.worldPosition.getY();
        }
        return this.floorName;
    }

    public void setFloorName(String newName) {
        String cleaned = newName == null ? "" : newName.trim();
        if (cleaned.length() > 32) {
            cleaned = cleaned.substring(0, 32);
        }
        this.floorName = cleaned.isEmpty() ? ("Floor Y=" + this.worldPosition.getY()) : cleaned;
        this.setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("FloorName", this.floorName);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.floorName = input.getStringOr("FloorName", "Floor Y=" + this.worldPosition.getY());
    }
}
