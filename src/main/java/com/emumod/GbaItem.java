package com.emumod;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Consola portátil que se puede apoyar (la Game Boy Advance, la SP y la 3DS): click derecho para jugar; agachado (Shift) sobre un bloque la apoya ahí. */
public class GbaItem extends BlockItem {
	private final ConsolaItem.Modelo modelo;

	public GbaItem(Block block, Properties properties) {
		this(block, ConsolaItem.Modelo.GBA, properties);
	}

	public GbaItem(Block block, ConsolaItem.Modelo modelo, Properties properties) {
		super(block, properties);
		this.modelo = modelo;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide) {
			ConsolaItem.abrirPantalla.accept(modelo);
		}
		return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player != null && player.isSecondaryUseActive()) {
			return super.useOn(context);
		}
		if (player != null) {
			return use(context.getLevel(), player, context.getHand()).getResult();
		}
		return InteractionResult.PASS;
	}
}
