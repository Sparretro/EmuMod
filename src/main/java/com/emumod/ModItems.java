package com.emumod;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModItems {
	public static final Item GBA = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("gba"),
			new GbaItem(ModBlocks.GBA, new Item.Properties().stacksTo(1)));

	// La Nintendo 3DS: se juega en las manos como la GBA y se apoya abierta
	public static final Item N3DS = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("n3ds"),
			new GbaItem(ModBlocks.N3DS, ConsolaItem.Modelo.N3DS, new Item.Properties().stacksTo(1)));

	// La Game Boy Advance SP: abierta, igual que la 3DS (usa el mismo emulador que la GBA)
	public static final Item GBA_SP = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("gba_sp"),
			new GbaItem(ModBlocks.GBA_SP, ConsolaItem.Modelo.GBA_SP, new Item.Properties().stacksTo(1)));

	// La PSP: se juega en las manos y se apoya acostada, como la GBA
	public static final Item PSP = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("psp"),
			new GbaItem(ModBlocks.PSP, ConsolaItem.Modelo.PSP, new Item.Properties().stacksTo(1)));

	/** El ítem de cada portátil. */
	public static Item de(ConsolaItem.Modelo modelo) {
		return switch (modelo) {
			case GBA -> GBA;
			case GBA_SP -> GBA_SP;
			case N3DS -> N3DS;
			case PSP -> PSP;
		};
	}

	public static final Item NES = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("nes"),
			new NesItem(ModBlocks.NES, "nes", new Item.Properties().stacksTo(1)));

	public static final Item TELE_CRT = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("tele_crt"),
			new BlockItem(ModBlocks.TELE_CRT, new Item.Properties()));

	public static final Item TELE_PLASMA = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("tele_plasma"),
			new BlockItem(ModBlocks.TELE_PLASMA, new Item.Properties()));

	public static final Item CONTROL_NES = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("control_nes"),
			new Item(new Item.Properties().stacksTo(1)));

	public static final Item N64 = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("n64"),
			new NesItem(ModBlocks.N64, "n64", new Item.Properties().stacksTo(1)));

	public static final Item CONTROL_N64 = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("control_n64"),
			new Item(new Item.Properties().stacksTo(1)));

	public static final Item PS2 = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("ps2"),
			new NesItem(ModBlocks.PS2, "ps2", new Item.Properties().stacksTo(1)));

	public static final Item PS2_SLIM = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("ps2_slim"),
			new NesItem(ModBlocks.PS2_SLIM, "ps2", new Item.Properties().stacksTo(1)));

	public static final Item CONTROL_PS2 = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("control_ps2"),
			new Item(new Item.Properties().stacksTo(1)));

	public static final Item PS3 = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("ps3"),
			new NesItem(ModBlocks.PS3, "ps3", new Item.Properties().stacksTo(1)));

	public static final Item PS3_SLIM = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("ps3_slim"),
			new NesItem(ModBlocks.PS3_SLIM, "ps3", new Item.Properties().stacksTo(1)));

	public static final Item CONTROL_PS3 = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("control_ps3"),
			new Item(new Item.Properties().stacksTo(1)));

	public static final Item WII = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("wii"),
			new NesItem(ModBlocks.WII, "wii", new Item.Properties().stacksTo(1)));

	public static final Item CONTROL_WII = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("control_wii"),
			new Item(new Item.Properties().stacksTo(1)));

	private ModItems() {
	}

	// Pestaña creativa propia con todo lo del mod (además de estar en las pestañas de siempre)
	public static final CreativeModeTab PESTANIA = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, EmuMod.id("emumod"),
			FabricItemGroup.builder()
					.title(Component.translatable("itemGroup.emumod"))
					.icon(() -> new ItemStack(GBA))
					.displayItems((parametros, entradas) -> BuiltInRegistries.ITEM.entrySet().stream()
							.filter(e -> e.getKey().location().getNamespace().equals(EmuMod.MOD_ID))
							.map(java.util.Map.Entry::getValue)
							.sorted(java.util.Comparator.comparing(item -> BuiltInRegistries.ITEM.getId(item)))
							.forEach(entradas::accept))
					.build());

	// Forzar la carga de la clase registra los campos estáticos
	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
			entries.accept(GBA);
			entries.accept(GBA_SP);
			entries.accept(N3DS);
			entries.accept(PSP);
			entries.accept(CONTROL_NES);
			entries.accept(CONTROL_N64);
			entries.accept(CONTROL_PS2);
			entries.accept(CONTROL_PS3);
			entries.accept(CONTROL_WII);
		});
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
			entries.accept(NES);
			entries.accept(N64);
			entries.accept(PS2);
			entries.accept(PS2_SLIM);
			entries.accept(PS3);
			entries.accept(PS3_SLIM);
			entries.accept(WII);
			entries.accept(TELE_CRT);
			entries.accept(TELE_PLASMA);
		});
	}
}
