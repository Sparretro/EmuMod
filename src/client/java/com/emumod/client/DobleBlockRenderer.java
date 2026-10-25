package com.emumod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.emumod.ConsolaAbiertaBlockEntity;
import com.emumod.GbaBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;

/** Dibuja la 3DS o la DS apoyada: abierta en el piso o en una mesa, con la pantalla táctil del lado de quien la apoyó. */
public class DobleBlockRenderer implements BlockEntityRenderer<ConsolaAbiertaBlockEntity> {
	// 12 píxeles de ancho, como su forma de colisión
	private static final float ESCALA = 0.75f / 0.95f;

	private final PortatilDoble consola;

	DobleBlockRenderer(PortatilDoble consola) {
		this.consola = consola;
	}

	@Override
	public void render(ConsolaAbiertaBlockEntity entidad, float partialTick, PoseStack pose, MultiBufferSource buffers, int luz, int overlay) {
		Direction mira = entidad.getBlockState().getValue(GbaBlock.FACING);
		// Del centro del cubo del ítem al piso del modelo
		float piso = (consola.pisoY - consola.centroY) * consola.escala * ESCALA;
		pose.pushPose();
		pose.translate(0.5f, -piso, 0.5f);
		// El lado del jugador del modelo (+z en el cubo del ítem) hacia quien la apoyó
		pose.mulPose(Axis.YP.rotationDegrees(180 - mira.toYRot()));
		pose.scale(ESCALA, ESCALA, ESCALA);
		pose.translate(-0.5f, -0.5f, -0.5f);
		// Apoyada está apagada, como la GBA
		ModeloDoble.dibujar(consola, pose, buffers, luz, overlay, 0, null, null, false);
		pose.popPose();
	}
}
