package com.emumod.client;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.emumod.EmuMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Cables de la NES (al televisor y al control): una cinta oscura que cuelga un poco entre los dos extremos. */
final class Cables {
	private static final ResourceLocation BLANCO = EmuMod.id("textures/modelo/blanco.png");
	private static final int TRAMOS = 16;
	private static final float GROSOR = 0.022f;

	private Cables() {
	}

	/** Dibuja el cable entre a y b, en las coordenadas de la pose actual. */
	static void dibujar(PoseStack pose, MultiBufferSource buffers, int luz, Vec3 a, Vec3 b) {
		VertexConsumer v = buffers.getBuffer(RenderType.entitySolid(BLANCO));
		Matrix4f m = pose.last().pose();
		Matrix3f n = pose.last().normal();
		double largo = a.distanceTo(b);
		double caida = 0.05 + largo * 0.12;
		Vec3 anterior = a;
		for (int i = 1; i <= TRAMOS; i++) {
			double t = i / (double) TRAMOS;
			Vec3 actual = a.lerp(b, t).subtract(0, caida * 4 * t * (1 - t), 0);
			// Dos cintas cruzadas para que se vea grueso desde cualquier lado
			tramo(v, m, n, luz, anterior, actual, new Vec3(0, GROSOR, 0));
			Vec3 dir = actual.subtract(anterior);
			Vec3 lado = new Vec3(-dir.z, 0, dir.x);
			lado = lado.lengthSqr() < 1e-8 ? new Vec3(GROSOR, 0, 0) : lado.normalize().scale(GROSOR);
			tramo(v, m, n, luz, anterior, actual, lado);
			anterior = actual;
		}
	}

	private static void tramo(VertexConsumer v, Matrix4f m, Matrix3f n, int luz, Vec3 p, Vec3 q, Vec3 ancho) {
		Vec3 a = p.subtract(ancho.scale(0.5)), b = p.add(ancho.scale(0.5)), c = q.add(ancho.scale(0.5)), d = q.subtract(ancho.scale(0.5));
		for (Vec3 punto : new Vec3[] {a, b, c, d}) {
			v.vertex(m, (float) punto.x, (float) punto.y, (float) punto.z).color(38, 38, 42, 255).uv(0.5f, 0.5f)
					.overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
		}
	}

	/** Un punto del modelo de un bloque (coordenadas del modelo) pasado al mundo, con la misma pose que su renderer. */
	static Vec3 puntoDeBloque(BlockPos pos, Direction mira, String modelo, float ancho, float giroFrente, float x, float y, float z) {
		PoseStack p = new PoseStack();
		p.translate(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		p.mulPose(Axis.YP.rotationDegrees(giroFrente - mira.toYRot()));
		Modelo3D.de(modelo).apoyar(p, ancho);
		Vector4f r = p.last().pose().transform(new Vector4f(x, y, z, 1));
		return new Vec3(r.x, r.y, r.z);
	}

	/** Dónde tiene las manos un jugador, aproximado, en coordenadas del mundo. */
	static Vec3 manos(Player jugador, float partialTick, boolean derecha) {
		Vec3 mirada = jugador.getViewVector(partialTick);
		Vec3 costado = mirada.cross(new Vec3(0, 1, 0));
		costado = costado.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : costado.normalize();
		Minecraft mc = Minecraft.getInstance();
		boolean primeraPersona = jugador == mc.player && mc.options.getCameraType().isFirstPerson();
		if (primeraPersona) {
			return jugador.getEyePosition(partialTick).add(mirada.scale(0.45)).add(costado.scale(derecha ? 0.25 : 0)).subtract(0, 0.32, 0);
		}
		return jugador.getPosition(partialTick).add(0, jugador.getBbHeight() * 0.45, 0).add(costado.scale(derecha ? 0.35 : 0))
				.add(mirada.multiply(1, 0, 1).scale(0.3));
	}
}
