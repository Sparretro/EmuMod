package com.emumod;

import java.util.function.Consumer;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Consola portátil: con click derecho en la mano se abre la misma pantalla de juego que el televisor. */
public class ConsolaItem extends Item {
	public enum Modelo {
		GBA, GBA_SP, N3DS, PSP
	}

	// El cliente lo reemplaza al iniciar para abrir la pantalla; en el servidor no hace nada
	public static Consumer<Modelo> abrirPantalla = modelo -> {
	};

	private final Modelo modelo;

	public ConsolaItem(Modelo modelo, Properties properties) {
		super(properties);
		this.modelo = modelo;
	}

	public Modelo modelo() {
		return modelo;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide) {
			abrirPantalla.accept(modelo);
		}
		return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
	}
}
