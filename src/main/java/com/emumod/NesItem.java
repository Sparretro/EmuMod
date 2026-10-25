package com.emumod;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Una consola de televisor (NES o N64): al fabricarla se explica en el chat cómo hacerla funcionar. */
public class NesItem extends BlockItem {
	// Prefijo de sus mensajes: mensaje.emumod.<avisos>.pasos_titulo, paso1...
	private final String avisos;

	public NesItem(Block block, String avisos, Properties properties) {
		super(block, properties);
		this.avisos = avisos;
	}

	@Override
	public void onCraftedBy(ItemStack stack, Level level, Player jugador) {
		super.onCraftedBy(stack, level, jugador);
		if (!level.isClientSide) {
			Avisos.pasos(jugador, avisos);
		}
	}
}
