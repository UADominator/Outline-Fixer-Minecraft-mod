package dev.dominator.outline_fixer.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.SortedSet;

@Mixin(value = LevelRenderer.class)
public abstract class MixinLevelRenderer {

	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow private ClientLevel level;

	@Shadow
	@Final
	private RenderBuffers renderBuffers;

	@Shadow
	@Final
	private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

	@Shadow
	private static void renderShape(PoseStack pPoseStack, VertexConsumer pConsumer, VoxelShape pShape, double pX,
		double pY, double pZ, float pRed, float pGreen, float pBlue, float pAlpha) {
	}

	@Inject(
		method = "renderHitOutline",
		at = @At("HEAD"),
		cancellable = true
	)
	private void stopOriginalOutline(CallbackInfo ci) {
		ci.cancel();
	}

	@Inject(
		method = "renderLevel",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
			ordinal = 3
		)
	)
	private void renderCustomOutlineAndCrumbling$fixer(PoseStack pPoseStack, float pPartialTick, long pFinishNanoTime,
		boolean pRenderBlockOutline, Camera pCamera, GameRenderer pGameRenderer, LightTexture pLightTexture,
		Matrix4f pProjectionMatrix, CallbackInfo ci) {

		// (Outline)
		this.renderBlockOutline$fixer(pPoseStack, pCamera, pRenderBlockOutline);

		// (Crumbling)
		this.renderCrumblingSecondPass$fixer(pPoseStack, pCamera);
	}

	@Unique
	private void renderBlockOutline$fixer(PoseStack poseStack, Camera camera, boolean shouldRender) {
		HitResult hitresult = this.minecraft.hitResult;
		if (shouldRender && hitresult != null && hitresult.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = ((BlockHitResult) hitresult).getBlockPos();
			BlockState state = this.level.getBlockState(pos);
			Vec3 camPos = camera.getPosition();

			RenderSystem.enableDepthTest();
			RenderSystem.depthMask(false);

			VertexConsumer consumer = this.renderBuffers.bufferSource().getBuffer(RenderType.lines());

			this.renderHitOutline$fixer(
				poseStack,
				consumer,
				camera.getEntity(),
				camPos.x,
				camPos.y,
				camPos.z,
				pos,
				state
			);

			this.renderBuffers.bufferSource().endBatch(RenderType.lines());
		}
	}

	@Unique
	private void renderHitOutline$fixer(PoseStack pPoseStack, VertexConsumer pConsumer, Entity pEntity, double pCamX,
		double pCamY, double pCamZ, BlockPos pPos, BlockState pState) {
		renderShape(
			pPoseStack,
			pConsumer,
			pState.getShape(this.level, pPos, CollisionContext.of(pEntity)),
			(double) pPos.getX() - pCamX,
			(double) pPos.getY() - pCamY,
			(double) pPos.getZ() - pCamZ,
			0.0F, 0.0F, 0.0F, 0.4F
		);
	}

	@Unique
	private void renderCrumblingSecondPass$fixer(PoseStack pPoseStack, Camera pCamera) {
		ProfilerFiller profilerfiller = this.level.getProfiler();
		Vec3 vec3 = pCamera.getPosition();
		double d0 = vec3.x(), d1 = vec3.y(), d2 = vec3.z();

		profilerfiller.popPush("destroyProgress");

		for (Long2ObjectMap.Entry<SortedSet<BlockDestructionProgress>> entry : this.destructionProgress.long2ObjectEntrySet()) {
			BlockPos blockpos2 = BlockPos.of(entry.getLongKey());
			double d3 = (double) blockpos2.getX() - d0;
			double d4 = (double) blockpos2.getY() - d1;
			double d5 = (double) blockpos2.getZ() - d2;

			if (d3 * d3 + d4 * d4 + d5 * d5 > 1024.0D) continue;

			SortedSet<BlockDestructionProgress> sortedset1 = entry.getValue();
			if (sortedset1 != null && !sortedset1.isEmpty()) {
				int k = sortedset1.last().getProgress();

				pPoseStack.pushPose();
				pPoseStack.translate(d3, d4, d5);

				PoseStack.Pose lastPose = pPoseStack.last();
				VertexConsumer vertexconsumer1 = new SheetedDecalTextureGenerator(
					this.renderBuffers.crumblingBufferSource().getBuffer(ModelBakery.DESTROY_TYPES.get(k)),
					lastPose.pose(),
					lastPose.normal(),
					1.0F
				);

				ModelData modelData = level.getModelDataManager().getAt(blockpos2);

				this.minecraft.getBlockRenderer().renderBreakingTexture(
					this.level.getBlockState(blockpos2),
					blockpos2,
					this.level,
					pPoseStack,
					vertexconsumer1,
					modelData == null ? ModelData.EMPTY : modelData
				);
				pPoseStack.popPose();
			}
			this.renderBuffers.crumblingBufferSource().endBatch();
		}
	}
}
