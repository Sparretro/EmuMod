package com.emumod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.emumod.GbaBlock;
import com.emumod.GbaBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Dibuja la Game Boy Advance apoyada: acostada con la pantalla hacia arriba, colgada en la pared o en el techo. */
public class GbaBlockRenderer implements BlockEntityRenderer<GbaBlockEntity> {
	// 0,78 deja la consola de 12 píxeles de ancho, como su forma de colisión
	private static final float ESCALA = 0.78f;
	// Distancia del centro del modelo a su tapa trasera (el modelo va de -0,16 adelante a 0,207 atrás)
	private static final float ESPALDA = 0.207f * ModeloGba3D.ESCALA * ESCALA;

	@Override
	public void render(GbaBlockEntity consola, float partialTick, PoseStack pose, MultiBufferSource buffers, int luz, int overlay) {
		BlockState estado = consola.getBlockState();
		Direction mira = estado.getValue(GbaBlock.FACING);
		pose.pushPose();
		pose.translate(0.5f, 0.5f, 0.5f);
		switch (estado.getValue(GbaBlock.FACE)) {
			case FLOOR -> {
				// Acostada con la espalda en el piso y la parte de arriba apuntando para donde miraba el jugador
				pose.translate(0, -0.5f + ESPALDA, 0);
				pose.mulPose(Axis.YP.rotationDegrees(180 - mira.toYRot()));
				pose.mulPose(Axis.XP.rotationDegrees(-90));
			}
			case CEILING -> {
				pose.translate(0, 0.5f - ESPALDA, 0);
				pose.mulPose(Axis.YP.rotationDegrees(-mira.toYRot()));
				pose.mulPose(Axis.XP.rotationDegrees(90));
			}
			case WALL -> {
				// De pie con la espalda contra la pared y la pantalla hacia afuera
				pose.translate(-mira.getStepX() * (0.5f - ESPALDA), 0, -mira.getStepZ() * (0.5f - ESPALDA));
				pose.mulPose(Axis.YP.rotationDegrees(-mira.toYRot()));
			}
		}
		pose.scale(ESCALA, ESCALA, ESCALA);
		pose.translate(-0.5f, -0.5f, -0.5f);
		ModeloGba3D.dibujar(pose, buffers, luz, overlay, 0, null, false);
		pose.popPose();
	}
}
