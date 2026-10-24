package com.example.elevatorsandmore.client.renderer;

import com.example.elevatorsandmore.entity.IndustrialElevatorCarriageEntity;
import com.example.elevatorsandmore.util.ElevatorShaftHelper;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class IndustrialElevatorCarriageRenderer extends EntityRenderer<IndustrialElevatorCarriageEntity, IndustrialElevatorCarriageRenderState> {

    public IndustrialElevatorCarriageRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public IndustrialElevatorCarriageRenderState createRenderState() {
        return new IndustrialElevatorCarriageRenderState();
    }

    @Override
    public void extractRenderState(IndustrialElevatorCarriageEntity entity, IndustrialElevatorCarriageRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.floorAnchor = entity.getFloorAnchorPos();
        state.blocks.clear();
        state.blocks.addAll(entity.getParsedBlocks());
        if (entity.level() instanceof ClientLevel clientLevel) {
            state.clientLevel = clientLevel;
        }
    }

    @Override
    public void submit(
            IndustrialElevatorCarriageRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera
    ) {
        super.submit(state, poseStack, submitNodeCollector, camera);

        if (state.blocks.isEmpty()) return;

        BlockPos anchor = state.floorAnchor;

        for (ElevatorShaftHelper.CapturedBlockEntry entry : state.blocks) {
            if (entry.state().getRenderShape() != RenderShape.MODEL) {
                continue;
            }

            BlockPos targetPos = anchor.offset(entry.relX(), entry.relY(), entry.relZ());

            MovingBlockRenderState blockRenderState = new MovingBlockRenderState();
            blockRenderState.blockPos = targetPos;
            blockRenderState.randomSeedPos = targetPos;
            blockRenderState.blockState = entry.state();

            if (state.clientLevel != null) {
                blockRenderState.biome = state.clientLevel.getBiome(targetPos);
                blockRenderState.cardinalLighting = state.clientLevel.cardinalLighting();
                blockRenderState.lightEngine = state.clientLevel.getLightEngine();
            }

            poseStack.pushPose();
            // Centered offset relative to entity origin (anchor.getX() + 0.5, entity.getY(), anchor.getZ() + 0.5)
            poseStack.translate(entry.relX() - 0.5, entry.relY(), entry.relZ() - 0.5);
            submitNodeCollector.submitMovingBlock(poseStack, blockRenderState, state.outlineColor);
            poseStack.popPose();
        }
    }
}
