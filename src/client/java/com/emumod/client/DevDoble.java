package com.emumod.client;

import java.nio.file.Path;
import java.util.OptionalLong;

import com.emumod.ConsolaItem;
import com.emumod.GbaBlock;
import com.emumod.ModBlocks;
import com.emumod.ModItems;
import com.emumod.EmuMod;
import com.emumod.client.emu.Nucleo.Boton;
import com.emumod.client.emu.Sistema;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

// Solo para desarrollo (-Demumod.dev.mundo -Demumod.dev.solo=n3ds, gba_sp o psp): como DevMundo pero solo con esa portátil.
// Crea un mundo, la pone en la mano, juega el programa de prueba (-Demumod.dev.juego_doble; la PSP sin eso usa
// RomDePruebaPsp), aprieta botones, toca la pantalla, abre el menú, la apoya y guarda capturas en run/screenshots.
final class DevDoble {
	private final PortatilDoble consola;
	private final String nombre;

	DevDoble(PortatilDoble consola) {
		this.consola = consola;
		nombre = consola.nombreModelo;
	}

	private int ticks = -1;
	private boolean mundoPedido;

	void tick(Minecraft mc) {
		if (!mundoPedido) {
			if (mc.screen != null && mc.getOverlay() == null) {
				mundoPedido = true;
				mc.createWorldOpenFlows().createFreshLevel("emumod_" + nombre + "_" + System.currentTimeMillis(),
						new LevelSettings("EmuMod " + nombre, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
								WorldDataConfiguration.DEFAULT),
						new WorldOptions(1234, false, false).withSeed(OptionalLong.of(1234)), WorldPresets::createNormalWorldDimensions);
			}
			return;
		}
		if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
			return;
		}
		ticks++;
		switch (ticks) {
			case 1 -> mc.getSingleplayerServer().execute(() -> {
				ServerPlayer jugador = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
				jugador.getInventory().setItem(0, new ItemStack(ModItems.de(consola.modelo)));
				jugador.serverLevel().setDayTime(6000);
			});
			case 5 -> {
				mc.player.getInventory().selected = 0;
				mc.player.setXRot(20);
				mc.options.pauseOnLostFocus = false;
			}
			case 60 -> capturar(mc, nombre + "_mano.png");
			case 61 -> {
				mc.player.setXRot(0);
				TelevisorScreen pantalla = new TelevisorScreen(consola.modelo);
				mc.setScreen(pantalla);
				String juego = System.getProperty("emumod.dev.juego_doble", "");
				// La PSP, sin juego indicado, con el programa de prueba propio (RomDePruebaPsp)
				if (juego.isEmpty() && consola.sistema == com.emumod.client.emu.Sistema.PSP) {
					try {
						Path elf = mc.gameDirectory.toPath().resolve("pruebapsp.elf");
						java.nio.file.Files.write(elf, com.emumod.client.emu.RomDePruebaPsp.elf());
						juego = elf.toString();
					} catch (java.io.IOException e) {
						throw new java.io.UncheckedIOException(e);
					}
				}
				if (!juego.isEmpty()) {
					EmuladorCliente.de(consola.sistema).cargarRom(Path.of(juego), m -> {
						pantalla.mostrar(m);
						EmuMod.LOGGER.info("Captura de desarrollo: {}", m.texto().getString());
					});
				}
			}
			case 67 -> capturar(mc, nombre + "_subiendo.png");
			case 150 -> capturar(mc, nombre + "_jugando.png");
			case 151 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.apretarParaCaptura(Boton.A.mascara() | Boton.DERECHA.mascara());
				}
			}
			case 165 -> capturar(mc, nombre + "_botones.png");
			case 166 -> {
				if (mc.screen instanceof TelevisorScreen pantalla && ManosDoble.matrizGui() != null) {
					pantalla.apretarParaCaptura(0);
					// Click de verdad (por mouseClicked) donde se ve un punto de la pantalla táctil a un cuarto desde la
					// izquierda y a tres cuartos hacia abajo: ahí tiene que aparecer el cuadrado blanco del programa de prueba
					// (sin pantalla táctil, en el botón A: tiene que verse hundido)
					float[][] e = consola.abajo;
					float x = e != null ? e[0][0] + (e[1][0] - e[0][0]) * 0.25f : consola.botones[0].x(), z = e != null ? e[0][2] + (e[3][2] - e[0][2]) * 0.75f : consola.botones[0].z();
					org.joml.Vector4f enPantalla = ModeloDoble.transformar(consola, ManosDoble.matrizGui(), x, consola.superficieY(), z);
					boolean tomado = pantalla.mouseClicked(enPantalla.x, enPantalla.y, 0);
					float[] vuelta = ModeloDoble.puntoEnLaBase(consola, ManosDoble.matrizGui(), enPantalla.x, enPantalla.y);
					EmuMod.LOGGER.info("Captura de desarrollo: click en ({}, {}) tomado={}, punto de la base ({}, {}) esperado ({}, {})",
							enPantalla.x, enPantalla.y, tomado, vuelta[0], vuelta[1], x, z);
				}
			}
			case 180 -> capturar(mc, nombre + "_tocando.png");
			case 181 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.mouseReleased(0, 0, 0);
					pantalla.mostrarMenuParaCaptura();
				}
			}
			case 195 -> capturar(mc, nombre + "_menu.png");
			case 196 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.ocultarMenuParaCaptura();
				}
				mc.setScreen(null);
				apoyar(mc);
			}
			case 240 -> capturar(mc, nombre + "_apoyada.png");
			case 241 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			case 270 -> capturar(mc, nombre + "_tercera.png");
			case 271 -> mc.stop();
			default -> {
			}
		}
	}

	// En una plataforma en el aire (el mundo de prueba puede empezar en el agua), mirando al norte: una consola en el piso y
	// otra arriba de un bloque de madera
	private void apoyar(Minecraft mc) {
		mc.getSingleplayerServer().execute(() -> {
			ServerLevel mundo = mc.getSingleplayerServer().overworld();
			ServerPlayer jugador = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
			BlockPos pies = new BlockPos(jugador.getBlockX(), 150, jugador.getBlockZ());
			for (int x = -3; x <= 3; x++) {
				for (int z = -4; z <= 2; z++) {
					mundo.setBlockAndUpdate(pies.offset(x, -1, z), Blocks.STONE.defaultBlockState());
				}
			}
			jugador.teleportTo(mundo, pies.getX() + 0.5, pies.getY(), pies.getZ() + 0.5, 180, 40);
			var bloque = ModBlocks.abierta(consola.modelo).defaultBlockState().setValue(GbaBlock.FACING, Direction.NORTH);
			mundo.setBlockAndUpdate(pies.offset(-1, 0, -2), bloque);
			mundo.setBlockAndUpdate(pies.offset(1, 0, -2), Blocks.OAK_PLANKS.defaultBlockState());
			mundo.setBlockAndUpdate(pies.offset(1, 1, -2), bloque);
		});
	}

	private static void capturar(Minecraft mc, String nombre) {
		EmuMod.LOGGER.info("Captura de desarrollo: {} a {} FPS", nombre, mc.getFps());
		Screenshot.grab(mc.gameDirectory, nombre, mc.getMainRenderTarget(),
				mensaje -> EmuMod.LOGGER.info("Captura de desarrollo: {}", mensaje.getString()));
	}
}
