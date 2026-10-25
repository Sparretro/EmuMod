package com.emumod.client;

import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.emumod.NesBlock;
import com.emumod.NesBlockEntity;
import com.emumod.TeleCrtBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Una consola de televisor (NES o N64) con su modelo 3D, el cable al televisor y, mientras alguien lo lleva en la mano,
 * el cable que va de la consola a su mano. Jugando, otro cable va del puerto de control a las manos.
 */
public class NesRenderer<T extends NesBlockEntity> implements BlockEntityRenderer<T> {
	private final ConsolaTele consola;

	NesRenderer(ConsolaTele consola) {
		this.consola = consola;
	}

	@Override
	public void render(T nes, float partialTick, PoseStack pose, MultiBufferSource buffers, int luz, int overlay) {
		BlockState estado = nes.getBlockState();
		Direction mira = estado.getValue(NesBlock.FACING);
		pose.pushPose();
		pose.translate(0.5f, 0, 0.5f);
		pose.mulPose(Axis.YP.rotationDegrees(consola.giroFrente - mira.toYRot()));
		Modelo3D.de(consola.modelo).apoyar(pose, consola.ancho);
		Modelo3D.de(consola.modelo).dibujar(pose, buffers, luz, overlay, Modelo3D.Ajuste.NINGUNO);
		pose.popPose();

		BlockPos pos = nes.getBlockPos();
		Vec3 origen = Vec3.atLowerCornerOf(pos);
		Vec3 video = conector(pos, mira, consola.salidaVideo).subtract(origen);
		BlockPos tele = nes.tele();
		if (tele != null && nes.getLevel() != null && nes.getLevel().getBlockState(tele).getBlock() instanceof TeleCrtBlock) {
			Direction miraTele = nes.getLevel().getBlockState(tele).getValue(TeleCrtBlock.FACING);
			TeleCrtRenderer.Tele t = TeleCrtRenderer.de(nes.getLevel().getBlockState(tele));
			Vec3 entrada = Cables.puntoDeBloque(tele, miraTele, t.modelo(), t.ancho(), t.giroFrente(),
					t.entradaVideo()[0], t.entradaVideo()[1], t.entradaVideo()[2]);
			Cables.dibujar(pose, buffers, luz, video, entrada.subtract(origen));
		}
		UUID quien = nes.cableEnManoDe();
		if (quien != null && nes.getLevel() != null) {
			Player jugador = nes.getLevel().getPlayerByUUID(quien);
			if (jugador != null) {
				Cables.dibujar(pose, buffers, luz, video, Cables.manos(jugador, partialTick, true).subtract(origen));
			}
		}
		if (consola.puertoControl != null && pos.equals(SesionNes.nesActiva()) && Minecraft.getInstance().player != null) {
			Vec3 puerto = conector(pos, mira, consola.puertoControl).subtract(origen);
			Cables.dibujar(pose, buffers, luz, puerto, Cables.manos(Minecraft.getInstance().player, partialTick, false).subtract(origen));
		}
	}

	private Vec3 conector(BlockPos pos, Direction mira, float[] punto) {
		return Cables.puntoDeBloque(pos, mira, consola.modelo, consola.ancho, consola.giroFrente, punto[0], punto[1], punto[2]);
	}
}
