package com.emumod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntidades {
	public static final EntityType<AsientoEntity> ASIENTO = Registry.register(BuiltInRegistries.ENTITY_TYPE, EmuMod.id("asiento"),
			FabricEntityTypeBuilder.<AsientoEntity>create(MobCategory.MISC, AsientoEntity::new)
					.dimensions(EntityDimensions.fixed(0.1f, 0.1f)).disableSummon().trackRangeBlocks(10).build());

	private ModEntidades() {
	}

	// Forzar la carga de la clase registra los campos estáticos
	public static void register() {
	}
}
