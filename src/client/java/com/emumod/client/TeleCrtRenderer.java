package com.emumod.client;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.emumod.ModBlocks;
import com.emumod.TeleCrtBlock;
import com.emumod.TeleCrtBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Los televisores (el CRT y el plasma) con su modelo 3D. Mientras se juega a la consola conectada (NES o N64), su
 * pantalla muestra el juego.
 */
public class TeleCrtRenderer implements BlockEntityRenderer<TeleCrtBlockEntity> {
	/**
	 * Un modelo de emumod: cuánto mide en bloques, la pantalla (en coordenadas del modelo, mirando a +z) y la entrada de
	 * video atrás, donde llega el cable de la consola.
	 */
	record Tele(String modelo, float ancho, float giroFrente, float pantallaIzquierda, float pantallaDerecha, float pantallaAbajo,
			float pantallaArriba, float pantallaZ, float[] entradaVideo, float baseModelo, float anchoModelo) {
		/** Altura del centro de la pantalla sobre la base del bloque, para mirarla al sentarse. */
		float alturaPantalla() {
			return ((pantallaAbajo + pantallaArriba) / 2 - baseModelo) * ancho / anchoModelo;
		}
	}

	// CRT de aut2 (CC BY 4.0), un poco más grande que un bloque: 1,3 de ancho y 1,25 de alto. La pantalla era verde croma
	static final Tele CRT = new Tele("tele_crt", 1.3f, 0, -0.347f, 0.347f, 0.2427f, 0.7662f, 1.012f, new float[] {0f, 0.3f, 0.14f}, 0.020f, 0.878f);
	// Plasma de 49" de CN Entertainment (CC BY 4.0), de 1,8 bloques de ancho: la pantalla es una pieza plana en z = 0
	static final Tele PLASMA = new Tele("tele_plasma", 1.8f, 0, 2.0f, 3.1f, -1.0f, -0.37f, 0.003f, new float[] {2.55f, -0.7f, -0.062f}, -1.021f, 1.12f);

	static Tele de(BlockState estado) {
		return estado.is(ModBlocks.TELE_PLASMA) ? PLASMA : CRT;
	}

	// Uno por consola: cada una tiene su textura
	private static final Map<ResourceLocation, RenderType> TIPOS_PANTALLA = new HashMap<>();

	@Override
	public void render(TeleCrtBlockEntity tele, float partialTick, PoseStack pose, MultiBufferSource buffers, int luz, int overlay) {
		Tele t = de(tele.getBlockState());
		Direction mira = tele.getBlockState().getValue(TeleCrtBlock.FACING);
		pose.pushPose();
		pose.translate(0.5f, 0, 0.5f);
		pose.mulPose(Axis.YP.rotationDegrees(t.giroFrente() - mira.toYRot()));
		Modelo3D modelo = Modelo3D.de(t.modelo());
		modelo.apoyar(pose, t.ancho());
		modelo.dibujar(pose, buffers, luz, overlay, Modelo3D.Ajuste.NINGUNO);

		EmuladorCliente emulador = SesionNes.consola().emulador();
		if (tele.getBlockPos().equals(SesionNes.teleActiva()) && emulador.hayJuego()) {
			// La pantalla brilla sola: sin sombreado y a luz máxima
			VertexConsumer v = buffers.getBuffer(TIPOS_PANTALLA.computeIfAbsent(emulador.textura(),
					textura -> TiposRender.pantalla("emumod_pantalla_crt", textura, true)));
			Matrix4f m = pose.last().pose();
			int brillo = LightTexture.FULL_BRIGHT;
			int c = emulador.pausadoPorJugador() ? 90 : 255;
			float x0 = t.pantallaIzquierda(), x1 = t.pantallaDerecha(), y0 = t.pantallaAbajo(), y1 = t.pantallaArriba(), z = t.pantallaZ();
			v.vertex(m, x0, y1, z).color(c, c, c, 255).uv(0, 0).uv2(brillo).endVertex();
			v.vertex(m, x0, y0, z).color(c, c, c, 255).uv(0, 1).uv2(brillo).endVertex();
			v.vertex(m, x1, y0, z).color(c, c, c, 255).uv(1, 1).uv2(brillo).endVertex();
			v.vertex(m, x1, y1, z).color(c, c, c, 255).uv(1, 0).uv2(brillo).endVertex();
		}
		pose.popPose();
	}
}
