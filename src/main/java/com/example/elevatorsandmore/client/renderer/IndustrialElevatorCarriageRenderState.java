package com.example.elevatorsandmore.client.renderer;

import java.util.ArrayList;
import java.util.List;

import com.example.elevatorsandmore.util.ElevatorShaftHelper;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class IndustrialElevatorCarriageRenderState extends EntityRenderState {
    public BlockPos floorAnchor = BlockPos.ZERO;
    public final List<ElevatorShaftHelper.CapturedBlockEntry> blocks = new ArrayList<>();
    public @Nullable ClientLevel clientLevel = null;
}
