package com.emumod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Asiento invisible: el jugador lo monta para quedar sentado frente al televisor mientras juega a la NES. */
public class AsientoEntity extends Entity {
	// Donde estaba parado el jugador antes de sentarse: ahí lo deja al levantarse (siempre es un lugar libre)
	private Vec3 bajada;

	public AsientoEntity(EntityType<?> tipo, Level level) {
		super(tipo, level);
		noPhysics = true;
	}

	void setBajada(Vec3 lugar) {
		bajada = lugar;
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity pasajero) {
		return bajada != null ? bajada : super.getDismountLocationForPassenger(pasajero);
	}

	@Override
	public void tick() {
		super.tick();
		// Sin nadie sentado (dejó de jugar, se desconectó) desaparece
		if (!level().isClientSide && getPassengers().isEmpty()) {
			discard();
		}
	}

	@Override
	public double getPassengersRidingOffset() {
		return 0;
	}

	// No se guarda con el mundo
	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void defineSynchedData() {
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
