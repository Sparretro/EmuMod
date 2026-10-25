package com.emumod;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
	public static final Block TELEVISOR = Registry.register(BuiltInRegistries.BLOCK, EmuMod.id("televisor"),
			new TelevisorBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GRAY)
					.strength(2.0f, 6.0f)
					.sound(SoundType.METAL)
					.requiresCorrectToolForDrops()));

	public static final Item TELEVISOR_ITEM = Registry.register(BuiltInRegistries.ITEM, EmuMod.id("televisor"),
			new BlockItem(TELEVISOR, new Item.Properties()));

	// La Game Boy Advance apoyada; su ítem es ModItems.GBA
	public static final GbaBlock GBA = Registry.register(BuiltInRegistries.BLOCK, EmuMod.id("gba"),
			new GbaBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLUE)
					.strength(0.3f)
					.sound(SoundType.STONE)
					.noOcclusion()
					.pushReaction(PushReaction.DESTROY)));

	public static final BlockEntityType<GbaBlockEntity> GBA_ENTIDAD = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			EmuMod.id("gba"), BlockEntityType.Builder.of(GbaBlockEntity::new, GBA).build(null));

	// La Nintendo 3DS y la Game Boy Advance SP apoyadas (abiertas); sus ítems son ModItems.N3DS y ModItems.GBA_SP
	public static final ConsolaAbiertaBlock N3DS = Registry.register(BuiltInRegistries.BLOCK, EmuMod.id("n3ds"),
			new ConsolaAbiertaBlock(ConsolaItem.Modelo.N3DS, BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.strength(0.3f)
					.sound(SoundType.STONE)
					.noOcclusion()
					.pushReaction(PushReaction.DESTROY)));

	public static final BlockEntityType<ConsolaAbiertaBlockEntity> N3DS_ENTIDAD = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			EmuMod.id("n3ds"), BlockEntityType.Builder.of(ConsolaAbiertaBlockEntity::new, N3DS).build(null));

	public static final ConsolaAbiertaBlock GBA_SP = Registry.register(BuiltInRegistries.BLOCK, EmuMod.id("gba_sp"),
			new ConsolaAbiertaBlock(ConsolaItem.Modelo.GBA_SP, BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.strength(0.3f)
					.sound(SoundType.STONE)
					.noOcclusion()
					.pushReaction(PushReaction.DESTROY)));

	public static final BlockEntityType<ConsolaAbiertaBlockEntity> GBA_SP_ENTIDAD = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			EmuMod.id("gba_sp"), BlockEntityType.Builder.of(ConsolaAbiertaBlockEntity::new, GBA_SP).build(null));

	// La NES y su televisor CRT; sus ítems están en ModItems
	public static final NesBlock NES = Registry.register(BuiltInRegistries.BLOCK, EmuMod.id("nes"),
			new NesBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_LIGHT_GRAY)
					.strength(0.6f)
					.sound(SoundType.STONE)
					.noOcclusion()));

	public static final BlockEntityType<NesBlockEntity> NES_ENTIDAD = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			EmuMod.id("nes"), BlockEntityType.Builder.of(NesBlockEntity::new, NES).build(null));

	// La Nintendo 64 usa el mismo televisor CRT; sus ítems están en ModItems
	public static final N64Block N64 = Registry.register(BuiltInRegistries.BLOCK, EmuMod.id("n64"),
			new N64Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.strength(0.6f)
					.sound(SoundType.STONE)
					.noOcclusion()));

	public static final BlockEntityType<N64BlockEntity> N64_ENTIDAD = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			EmuMod.id("n64"), BlockEntityType.Builder.of(N64BlockEntity::new, N64).build(null));

	// PlayStation 2 clásica (30 x 7,8 x 18 cm) y Slim (23 x 2,8 x 15 cm), en la escala de la NES; también usan el televisor CRT
	public static final ConsolaTeleBlock PS2 = Registry.register(BuiltInRegistries.BLOCK, EmuMod.id("ps2"),
			new ConsolaTeleBlock(propiedadesConsola(), Block.box(1, 0, 3.6, 15, 3.8, 12.4), Block.box(3.6, 0, 1, 12.4, 3.8, 15),
					() -> ModBlocks.PS2_ENTIDAD, () -> ModItems.CONTROL_PS2, "ps2"));

	public static final ConsolaTeleBlock PS2_SLIM = Registry.register(BuiltInRegistries.BLOCK, EmuMod.id("ps2_slim"),
			new ConsolaTeleBlock(propiedadesConsola(), Block.box(2.6, 0, 4.3, 13.4, 1.5, 11.7), Block.box(4.3, 0, 2.6, 11.7, 1.5, 13.4),
					() -> ModBlocks.PS2_SLIM_ENTIDAD, () -> ModItems.CONTROL_PS2, "ps2"));

	// PlayStation 3 clásica (32,5 x 9,8 x 27,4 cm) y Slim (29 x 6,5 x 29 cm)
	public static final ConsolaTeleBlock PS3 = Registry.register(BuiltInRegistries.BLOCK, EmuMod.id("ps3"),
			new ConsolaTeleBlock(propiedadesConsola(), Block.box(0.4, 0, 1.6, 15.6, 4.7, 14.4), Block.box(1.6, 0, 0.4, 14.4, 4.7, 15.6),
					() -> ModBlocks.PS3_ENTIDAD, () -> ModItems.CONTROL_PS3, "ps3"));

	public static final ConsolaTeleBlock PS3_SLIM = Registry.register(BuiltInRegistries.BLOCK, EmuMod.id("ps3_slim"),
			new ConsolaTeleBlock(propiedadesConsola(), Block.box(1.2, 0, 1.2, 14.8, 3, 14.8), Block.box(1.2, 0, 1.2, 14.8, 3, 14.8),
					() -> ModBlocks.PS3_SLIM_ENTIDAD, () -> ModItems.CONTROL_PS3, "ps3"));

	// Wii parada en su base (4,4 x 15,7 x 21,5 cm)
	public static final ConsolaTeleBlock WII = Registry.register(BuiltInRegistries.BLOCK, EmuMod.id("wii"),
			new ConsolaTeleBlock(propiedadesConsola().mapColor(MapColor.SNOW), Block.box(7, 0, 3, 9, 7.4, 13), Block.box(3, 0, 7, 13, 7.4, 9),
					() -> ModBlocks.WII_ENTIDAD, () -> ModItems.CONTROL_WII, "wii"));

	public static final BlockEntityType<ConsolaTeleBlockEntity> PS2_ENTIDAD = tipoConsola("ps2", PS2, () -> ModBlocks.PS2_ENTIDAD);
	public static final BlockEntityType<ConsolaTeleBlockEntity> PS2_SLIM_ENTIDAD = tipoConsola("ps2_slim", PS2_SLIM, () -> ModBlocks.PS2_SLIM_ENTIDAD);
	public static final BlockEntityType<ConsolaTeleBlockEntity> PS3_ENTIDAD = tipoConsola("ps3", PS3, () -> ModBlocks.PS3_ENTIDAD);
	public static final BlockEntityType<ConsolaTeleBlockEntity> PS3_SLIM_ENTIDAD = tipoConsola("ps3_slim", PS3_SLIM, () -> ModBlocks.PS3_SLIM_ENTIDAD);
	public static final BlockEntityType<ConsolaTeleBlockEntity> WII_ENTIDAD = tipoConsola("wii", WII, () -> ModBlocks.WII_ENTIDAD);

	public static final TeleCrtBlock TELE_CRT = Registry.register(BuiltInRegistries.BLOCK, EmuMod.id("tele_crt"),
			new TeleCrtBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.strength(1.0f)
					.sound(SoundType.STONE)
					.noOcclusion()));

	// Tele de plasma de 49": 1,8 bloques de ancho, 1,06 de alto y finita; usa la misma entidad que el CRT
	public static final TeleCrtBlock TELE_PLASMA = Registry.register(BuiltInRegistries.BLOCK, EmuMod.id("tele_plasma"),
			new TeleCrtBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0f).sound(SoundType.METAL).noOcclusion(),
					Block.box(-6.4, 0, 7, 22.4, 17, 9.2), Block.box(7, 0, -6.4, 9.2, 17, 22.4)));

	public static final BlockEntityType<TeleCrtBlockEntity> TELE_CRT_ENTIDAD = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			EmuMod.id("tele_crt"), BlockEntityType.Builder.of(TeleCrtBlockEntity::new, TELE_CRT, TELE_PLASMA).build(null));

	private ModBlocks() {
	}

	private static BlockBehaviour.Properties propiedadesConsola() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.6f).sound(SoundType.STONE).noOcclusion();
	}

	// El tipo se pasa a sí mismo a cada entidad que crea
	private static BlockEntityType<ConsolaTeleBlockEntity> tipoConsola(String nombre, Block bloque,
			java.util.function.Supplier<BlockEntityType<ConsolaTeleBlockEntity>> tipo) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, EmuMod.id(nombre),
				BlockEntityType.Builder.<ConsolaTeleBlockEntity>of((pos, estado) -> new ConsolaTeleBlockEntity(tipo.get(), pos, estado), bloque).build(null));
	}

	// Forzar la carga de la clase registra los campos estáticos
	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(TELEVISOR_ITEM));
	}
}
