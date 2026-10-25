package com.emumod.test;

import com.emumod.ModBlocks;
import com.emumod.ModItems;
import com.emumod.EmuMod;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;

public class TelevisorGameTests implements FabricGameTest {
	@GameTest(template = EMPTY_STRUCTURE)
	public void recetaCraftea(GameTestHelper helper) {
		Recipe<?> receta = helper.getLevel().getRecipeManager().byKey(EmuMod.id("televisor"))
				.orElseThrow(() -> new AssertionError("No se cargó la receta emumod:televisor"));
		ItemStack resultado = receta.getResultItem(helper.getLevel().registryAccess());
		helper.assertTrue(resultado.is(ModBlocks.TELEVISOR_ITEM), "La receta da " + resultado);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void recetasDeConsolas(GameTestHelper helper) {
		for (Item consola : new Item[] {ModItems.GBA, ModItems.GBA_SP}) {
			String nombre = BuiltInRegistries.ITEM.getKey(consola).getPath();
			Recipe<?> receta = helper.getLevel().getRecipeManager().byKey(EmuMod.id(nombre))
					.orElseThrow(() -> new AssertionError("No se cargó la receta emumod:" + nombre));
			ItemStack resultado = receta.getResultItem(helper.getLevel().registryAccess());
			helper.assertTrue(resultado.is(consola), "La receta " + nombre + " da " + resultado);
			helper.assertTrue(consola.getMaxStackSize() == 1, nombre + " debería apilarse de a 1");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void recetasDeLaNes(GameTestHelper helper) {
		for (Item cosa : new Item[] {ModItems.NES, ModItems.TELE_CRT, ModItems.CONTROL_NES, ModItems.N64, ModItems.CONTROL_N64,
				ModItems.PS2, ModItems.PS2_SLIM, ModItems.CONTROL_PS2,
				ModItems.PS3, ModItems.PS3_SLIM, ModItems.CONTROL_PS3}) {
			String nombre = BuiltInRegistries.ITEM.getKey(cosa).getPath();
			Recipe<?> receta = helper.getLevel().getRecipeManager().byKey(EmuMod.id(nombre))
					.orElseThrow(() -> new AssertionError("No se cargó la receta emumod:" + nombre));
			ItemStack resultado = receta.getResultItem(helper.getLevel().registryAccess());
			helper.assertTrue(resultado.is(cosa), "La receta " + nombre + " da " + resultado);
		}
		helper.assertTrue(ModBlocks.NES.asItem() == ModItems.NES && ModBlocks.TELE_CRT.asItem() == ModItems.TELE_CRT
				&& ModBlocks.N64.asItem() == ModItems.N64 && ModBlocks.PS2.asItem() == ModItems.PS2 && ModBlocks.PS2_SLIM.asItem() == ModItems.PS2_SLIM
				&& ModBlocks.PS3.asItem() == ModItems.PS3 && ModBlocks.PS3_SLIM.asItem() == ModItems.PS3_SLIM,
				"Los bloques de las consolas de televisor no están asociados a sus ítems");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void romperLaNesYLaTeleLasSuelta(GameTestHelper helper) {
		BlockPos nes = new BlockPos(1, 2, 1), tele = new BlockPos(2, 2, 1);
		helper.setBlock(nes, ModBlocks.NES);
		helper.setBlock(tele, ModBlocks.TELE_CRT);
		helper.getLevel().destroyBlock(helper.absolutePos(nes), true);
		helper.getLevel().destroyBlock(helper.absolutePos(tele), true);
		helper.succeedWhen(() -> {
			helper.assertItemEntityPresent(ModItems.NES, nes, 2.0);
			helper.assertItemEntityPresent(ModItems.TELE_CRT, tele, 2.0);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void romperLaN64LaSuelta(GameTestHelper helper) {
		BlockPos n64 = new BlockPos(1, 2, 1);
		helper.setBlock(n64, ModBlocks.N64);
		helper.assertTrue(helper.getBlockEntity(n64) instanceof com.emumod.N64BlockEntity, "La N64 no tiene su entidad");
		helper.getLevel().destroyBlock(helper.absolutePos(n64), true);
		helper.succeedWhen(() -> helper.assertItemEntityPresent(ModItems.N64, n64, 2.0));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void romperLasPs3LasSuelta(GameTestHelper helper) {
		BlockPos clasica = new BlockPos(1, 2, 1), slim = new BlockPos(3, 2, 1);
		helper.setBlock(clasica, ModBlocks.PS3);
		helper.setBlock(slim, ModBlocks.PS3_SLIM);
		helper.assertTrue(helper.getBlockEntity(clasica) instanceof com.emumod.ConsolaTeleBlockEntity e && e.avisos().equals("ps3"),
				"La PS3 no tiene su entidad con sus mensajes");
		helper.getLevel().destroyBlock(helper.absolutePos(clasica), true);
		helper.getLevel().destroyBlock(helper.absolutePos(slim), true);
		helper.succeedWhen(() -> {
			helper.assertItemEntityPresent(ModItems.PS3, clasica, 2.0);
			helper.assertItemEntityPresent(ModItems.PS3_SLIM, slim, 2.0);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void romperLasPs2LasSuelta(GameTestHelper helper) {
		BlockPos clasica = new BlockPos(1, 2, 1), slim = new BlockPos(3, 2, 1);
		helper.setBlock(clasica, ModBlocks.PS2);
		helper.setBlock(slim, ModBlocks.PS2_SLIM);
		helper.assertTrue(helper.getBlockEntity(clasica) instanceof com.emumod.ConsolaTeleBlockEntity
				&& helper.getBlockEntity(slim) instanceof com.emumod.ConsolaTeleBlockEntity, "Las PS2 no tienen su entidad");
		helper.getLevel().destroyBlock(helper.absolutePos(clasica), true);
		helper.getLevel().destroyBlock(helper.absolutePos(slim), true);
		helper.succeedWhen(() -> {
			helper.assertItemEntityPresent(ModItems.PS2, clasica, 2.0);
			helper.assertItemEntityPresent(ModItems.PS2_SLIM, slim, 2.0);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void romperSueltaElBloque(GameTestHelper helper) {
		LootTable tabla = helper.getLevel().getServer().getLootData().getLootTable(EmuMod.id("blocks/televisor"));
		helper.assertTrue(tabla != LootTable.EMPTY, "No se cargó la loot table");

		BlockPos pos = new BlockPos(1, 2, 1);
		helper.setBlock(pos, ModBlocks.TELEVISOR);
		helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
		helper.succeedWhen(() -> helper.assertItemEntityPresent(ModBlocks.TELEVISOR_ITEM, pos, 2.0));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void gbaApoyadaSueltaLaConsola(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 2, 1);
		helper.setBlock(pos.below(), Blocks.STONE);
		helper.setBlock(pos, ModBlocks.GBA);
		helper.assertTrue(helper.getBlockState(pos).canSurvive(helper.getLevel(), helper.absolutePos(pos)), "La GBA no se sostiene en el piso");
		helper.assertTrue(ModBlocks.GBA.asItem() == ModItems.GBA, "El bloque GBA no está asociado a su ítem");
		helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
		helper.succeedWhen(() -> helper.assertItemEntityPresent(ModItems.GBA, pos, 2.0));
	}
}
