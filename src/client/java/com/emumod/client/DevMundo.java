package com.emumod.client;

import java.util.OptionalLong;

import com.emumod.ConsolaItem;
import com.emumod.GbaBlock;
import com.emumod.ModItems;
import com.emumod.NesBlock;
import com.emumod.TeleCrtBlock;
import com.emumod.client.emu.RomDePrueba;
import com.emumod.client.emu.RomDePruebaN64;
import com.emumod.client.emu.Sistema;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.emumod.ModBlocks;
import com.emumod.client.emu.Nucleo.Boton;
import com.emumod.ModItems;
import com.emumod.EmuMod;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

// Solo para desarrollo (-Demumod.dev.mundo): crea un mundo nuevo, pone las dos consolas en la barra y guarda
// capturas en run/screenshots: en primera persona, en tercera persona, jugando con cada consola (la GBA subiendo a los
// ojos y en las manos) y con tres GBA apoyadas en el mundo.
final class DevMundo {
	private int ticks = -1;
	private boolean mundoPedido;

	void tick(Minecraft mc) {
		if (!mundoPedido) {
			// Menú principal o, en el primer arranque, la pantalla de accesibilidad
			if (mc.screen != null && mc.getOverlay() == null) {
				mundoPedido = true;
				mc.createWorldOpenFlows().createFreshLevel("emumod_dev_" + System.currentTimeMillis(),
						new LevelSettings("Televisor dev", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
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
				jugador.getInventory().setItem(0, new ItemStack(ModItems.GBA));
				jugador.getInventory().setItem(1, new ItemStack(ModItems.GBA_SP));
				jugador.serverLevel().setDayTime(6000);
			});
			case 5 -> {
				mc.player.getInventory().selected = 0;
				mc.player.setXRot(20);
			}
			case 80 -> capturar(mc, "consola_mano_gba.png");
			case 81 -> mc.player.getInventory().selected = 1;
			case 120 -> capturar(mc, "consola_mano_sp.png");
			case 121 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			case 160 -> capturar(mc, "consola_tercera_sp.png");
			case 161 -> mc.player.getInventory().selected = 0;
			case 200 -> capturar(mc, "consola_tercera_gba.png");
			case 201 -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setXRot(0);
				EmuladorCliente.get().setFiltro(FiltroPantalla.valueOf(System.getProperty("emumod.dev.filtro", "ORIGINAL")));
				TelevisorScreen pantalla = new TelevisorScreen(ConsolaItem.Modelo.GBA);
				EmuladorCliente.get().cargarJuego(new byte[0x200], "cartucho_vacio.gba", pantalla::mostrar);
				mc.setScreen(pantalla);
			}
			// A mitad de camino hacia los ojos
			case 207 -> capturar(mc, "consola_subiendo.png");
			case 270 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.apretarParaCaptura(Boton.A.mascara() | Boton.DERECHA.mascara());
				}
			}
			case 280 -> capturar(mc, "consola_pantalla_gba.png");
			case 282 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.mostrarMenuParaCaptura();
				}
			}
			case 290 -> capturar(mc, "consola_menu_gba.png");
			case 291 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.ocultarMenuParaCaptura();
				}
				mc.setScreen(new TelevisorScreen(ConsolaItem.Modelo.GBA_SP));
			}
			case 320 -> capturar(mc, "consola_pantalla_sp.png");
			case 321 -> {
				mc.setScreen(null);
				apoyarConsolas(mc);
			}
			case 360 -> capturar(mc, "consola_apoyada.png");
			// ---- NES: escritorio con la NES y el televisor, cable, control y a jugar ----
			case 361 -> armarEscritorio(mc, ModBlocks.NES.defaultBlockState().setValue(NesBlock.FACING, Direction.NORTH));
			case 385 -> capturar(mc, "nes_apoyada.png");
			case 386 -> usarEnServidor(mc, true, ItemStack.EMPTY);
			case 400 -> capturar(mc, "nes_cable.png");
			case 401 -> {
				usarEnServidor(mc, false, ItemStack.EMPTY);
				usarEnServidor(mc, true, new ItemStack(ModItems.CONTROL_NES));
			}
			case 420 -> capturar(mc, "nes_conectada.png");
			case 421 -> usarEnServidor(mc, true, new ItemStack(ModItems.CONTROL_NES));
			case 426 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					EmuladorCliente.nes().setFiltro(FiltroPantalla.valueOf(System.getProperty("emumod.dev.filtro_nes", "CRT")));
					EmuladorCliente.nes().cargarJuego(RomDePrueba.roja(), "prueba_roja.nes", pantalla::mostrar);
				} else {
					EmuMod.LOGGER.error("Captura de desarrollo: no se abrió la pantalla de la NES");
				}
			}
			case 470 -> capturar(mc, "nes_jugando.png");
			case 471 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.apretarParaCaptura(Boton.A.mascara() | Boton.DERECHA.mascara());
				}
			}
			case 480 -> capturar(mc, "nes_botones.png");
			case 481 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.apretarParaCaptura(0);
					pantalla.mostrarMenuParaCaptura();
				}
			}
			case 490 -> capturar(mc, "nes_menu.png");
			case 491 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			case 505 -> capturar(mc, "nes_tercera.png");
			case 506 -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.ocultarMenuParaCaptura();
				}
				// Sin foco la ventana de prueba abriría el menú de pausa al cerrar la pantalla
				mc.options.pauseOnLostFocus = false;
				mc.setScreen(null);
			}
			case 512, 520, 528 -> estadoAlLevantarse(mc);
			case 513 -> mc.player.setDeltaMovement(0.4, 0.2, 0);
			case 530 -> capturar(mc, "nes_levantado.png");
			// ---- N64: el mismo escritorio con la N64 y el televisor, cable, control y a jugar ----
			case 531 -> armarEscritorio(mc, ModBlocks.N64.defaultBlockState().setValue(NesBlock.FACING, Direction.NORTH));
			case 555 -> capturar(mc, "n64_apoyada.png");
			case 556 -> usarEnServidor(mc, true, ItemStack.EMPTY);
			case 570 -> capturar(mc, "n64_cable.png");
			case 571 -> {
				usarEnServidor(mc, false, ItemStack.EMPTY);
				usarEnServidor(mc, true, new ItemStack(ModItems.CONTROL_N64));
			}
			case 590 -> capturar(mc, "n64_conectada.png");
			case 591 -> usarEnServidor(mc, true, new ItemStack(ModItems.CONTROL_N64));
			case 596 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					EmuladorCliente.de(Sistema.N64).cargarJuego(RomDePruebaN64.azul(), "prueba_azul.z64", pantalla::mostrar);
				} else {
					EmuMod.LOGGER.error("Captura de desarrollo: no se abrió la pantalla de la N64");
				}
			}
			case 640 -> capturar(mc, "n64_jugando.png");
			case 641 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.apretarParaCaptura(BotonN64.A.mascara | BotonN64.C_DERECHA.mascara | BotonN64.START.mascara | BotonN64.DERECHA.mascara);
				}
			}
			case 650 -> capturar(mc, "n64_botones.png");
			case 651 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.apretarParaCaptura(0);
					pantalla.mostrarMenuParaCaptura();
				}
			}
			case 660 -> capturar(mc, "n64_menu.png");
			case 661 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			case 675 -> capturar(mc, "n64_tercera.png");
			case 676 -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.ocultarMenuParaCaptura();
				}
				mc.setScreen(null);
			}
			case 682 -> estadoAlLevantarse(mc);
			case 690 -> capturar(mc, "n64_levantado.png");
			// ---- PS2: la clásica con el televisor, cable, control y a jugar (con la BIOS de -PdevBiosPs2, el menú de la PS2) ----
			case 691 -> armarEscritorio(mc, ModBlocks.PS2.defaultBlockState().setValue(NesBlock.FACING, Direction.NORTH));
			case 715 -> capturar(mc, "ps2_apoyada.png");
			case 716 -> usarEnServidor(mc, true, ItemStack.EMPTY);
			case 730 -> capturar(mc, "ps2_cable.png");
			case 731 -> {
				usarEnServidor(mc, false, ItemStack.EMPTY);
				usarEnServidor(mc, true, new ItemStack(ModItems.CONTROL_PS2));
			}
			case 750 -> capturar(mc, "ps2_conectada.png");
			case 751 -> usarEnServidor(mc, true, new ItemStack(ModItems.CONTROL_PS2));
			case 756 -> {
				String bios = System.getProperty("emumod.dev.bios_ps2", "");
				if (mc.screen instanceof TelevisorScreen pantalla && !bios.isEmpty()) {
					EmuladorCliente ps2 = EmuladorCliente.de(Sistema.PS2);
					pantalla.mostrar(ps2.instalarBios(java.nio.file.Path.of(bios)));
					ps2.encenderSinJuego(pantalla::mostrar);
				} else if (!(mc.screen instanceof TelevisorScreen)) {
					EmuMod.LOGGER.error("Captura de desarrollo: no se abrió la pantalla de la PS2");
				}
			}
			case 800 -> capturar(mc, "ps2_arrancando.png");
			case 1100 -> capturar(mc, "ps2_jugando.png");
			case 1101 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.apretarParaCaptura(BotonPs2.EQUIS.mascara | BotonPs2.TRIANGULO.mascara | BotonPs2.DERECHA.mascara);
				}
			}
			case 1110 -> capturar(mc, "ps2_botones.png");
			case 1111 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.apretarParaCaptura(0);
					pantalla.mostrarMenuParaCaptura();
				}
			}
			case 1120 -> capturar(mc, "ps2_menu.png");
			case 1121 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			case 1135 -> capturar(mc, "ps2_tercera.png");
			case 1136 -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.ocultarMenuParaCaptura();
				}
				mc.setScreen(null);
			}
			case 1150 -> capturar(mc, "ps2_levantado.png");
			case 1151 -> armarEscritorio(mc, ModBlocks.PS2_SLIM.defaultBlockState().setValue(NesBlock.FACING, Direction.NORTH));
			case 1175 -> capturar(mc, "ps2_slim_apoyada.png");
			// ---- PS3: la clásica con el televisor, cable, control y sentado (sin juego: el mod no trae ninguno) ----
			case 1176 -> armarEscritorio(mc, ModBlocks.PS3.defaultBlockState().setValue(NesBlock.FACING, Direction.NORTH));
			case 1200 -> capturar(mc, "ps3_apoyada.png");
			case 1201 -> usarEnServidor(mc, true, ItemStack.EMPTY);
			case 1205 -> {
				usarEnServidor(mc, false, ItemStack.EMPTY);
				usarEnServidor(mc, true, new ItemStack(ModItems.CONTROL_PS3));
			}
			case 1215 -> capturar(mc, "ps3_conectada.png");
			case 1216 -> usarEnServidor(mc, true, new ItemStack(ModItems.CONTROL_PS3));
			case 1240 -> capturar(mc, "ps3_sentado.png");
			case 1241 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.apretarParaCaptura(BotonPs2.CIRCULO.mascara | BotonPs2.IZQUIERDA.mascara);
				}
			}
			case 1250 -> capturar(mc, "ps3_botones.png");
			case 1251 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.apretarParaCaptura(0);
					pantalla.mostrarMenuParaCaptura();
				}
			}
			case 1260 -> capturar(mc, "ps3_menu.png");
			case 1261 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.ocultarMenuParaCaptura();
				}
				mc.setScreen(null);
			}
			case 1280 -> armarEscritorio(mc, ModBlocks.PS3_SLIM.defaultBlockState().setValue(NesBlock.FACING, Direction.NORTH));
			case 1300 -> capturar(mc, "ps3_slim_apoyada.png");
			// ---- Wii: la consola parada junto al televisor, cable, Wiimote y el juego de -PdevJuegoWii ----
			case 1301 -> armarEscritorio(mc, ModBlocks.WII.defaultBlockState().setValue(NesBlock.FACING, Direction.NORTH),
					ModBlocks.TELE_PLASMA.defaultBlockState().setValue(TeleCrtBlock.FACING, Direction.NORTH));
			case 1325 -> capturar(mc, "wii_apoyada.png");
			case 1326 -> usarEnServidor(mc, true, ItemStack.EMPTY);
			case 1330 -> {
				usarEnServidor(mc, false, ItemStack.EMPTY);
				usarEnServidor(mc, true, new ItemStack(ModItems.CONTROL_WII));
			}
			case 1340 -> capturar(mc, "wii_conectada.png");
			case 1341 -> usarEnServidor(mc, true, new ItemStack(ModItems.CONTROL_WII));
			case 1346 -> {
				String juego = System.getProperty("emumod.dev.juego_wii", "");
				if (mc.screen instanceof TelevisorScreen pantalla && !juego.isEmpty()) {
					EmuladorCliente.de(Sistema.WII).cargarRom(java.nio.file.Path.of(juego), pantalla::mostrar);
				}
			}
			case 1800 -> capturar(mc, "wii_jugando.png");
			case 1801 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.apretarParaCaptura(BotonWii.DOS.mascara | BotonWii.DERECHA.mascara);
				}
			}
			case 1810 -> capturar(mc, "wii_botones.png");
			case 1811 -> {
				if (mc.screen instanceof TelevisorScreen pantalla) {
					pantalla.apretarParaCaptura(0);
					pantalla.mostrarMenuParaCaptura();
				}
			}
			case 1820 -> capturar(mc, "wii_menu.png");
			case 1821 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			case 1835 -> capturar(mc, "wii_tercera.png");
			case 1836 -> mc.stop();
			default -> {
			}
		}
	}

	// Delante del jugador (mirando al norte): una en el piso, otra sobre un bloque y otra colgada en una pared
	private static void apoyarConsolas(Minecraft mc) {
		mc.player.setYRot(180);
		mc.player.setXRot(35);
		BlockPos pies = mc.player.blockPosition();
		mc.getSingleplayerServer().execute(() -> {
			ServerLevel mundo = mc.getSingleplayerServer().overworld();
			for (int x = -2; x <= 2; x++) {
				for (int z = -4; z <= -1; z++) {
					mundo.setBlockAndUpdate(pies.offset(x, -1, z), Blocks.STONE.defaultBlockState());
					for (int y = 0; y <= 2; y++) {
						mundo.setBlockAndUpdate(pies.offset(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
			BlockState gba = ModBlocks.GBA.defaultBlockState().setValue(GbaBlock.FACING, Direction.NORTH);
			mundo.setBlockAndUpdate(pies.offset(-1, 0, -2), gba.setValue(GbaBlock.FACE, AttachFace.FLOOR));
			mundo.setBlockAndUpdate(pies.offset(1, 0, -3), Blocks.OAK_PLANKS.defaultBlockState());
			mundo.setBlockAndUpdate(pies.offset(1, 1, -3), gba.setValue(GbaBlock.FACE, AttachFace.FLOOR));
			mundo.setBlockAndUpdate(pies.offset(0, 1, -4), Blocks.STONE.defaultBlockState());
			mundo.setBlockAndUpdate(pies.offset(0, 1, -3),
					gba.setValue(GbaBlock.FACE, AttachFace.WALL).setValue(GbaBlock.FACING, Direction.SOUTH));
		});
	}

	// Mirando al sur: dos tablones como escritorio, la consola arriba de uno y el televisor CRT arriba del otro, pegados
	private static void armarEscritorio(Minecraft mc, BlockState consola) {
		armarEscritorio(mc, consola, ModBlocks.TELE_CRT.defaultBlockState().setValue(TeleCrtBlock.FACING, Direction.NORTH));
	}

	private static void armarEscritorio(Minecraft mc, BlockState consola, BlockState tele) {
		mc.player.setYRot(0);
		mc.player.setXRot(25);
		BlockPos pies = mc.player.blockPosition();
		mc.getSingleplayerServer().execute(() -> {
			ServerLevel mundo = mc.getSingleplayerServer().overworld();
			for (int x = -2; x <= 2; x++) {
				for (int z = 1; z <= 4; z++) {
					mundo.setBlockAndUpdate(pies.offset(x, -1, z), Blocks.STONE.defaultBlockState());
					for (int y = 0; y <= 2; y++) {
						mundo.setBlockAndUpdate(pies.offset(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
			mundo.setBlockAndUpdate(pies.offset(-1, 0, 3), Blocks.OAK_PLANKS.defaultBlockState());
			mundo.setBlockAndUpdate(pies.offset(0, 0, 3), Blocks.OAK_PLANKS.defaultBlockState());
			mundo.setBlockAndUpdate(pies.offset(-1, 1, 3), consola);
			mundo.setBlockAndUpdate(pies.offset(0, 1, 3), tele);
		});
	}

	// Hace click derecho en la consola (o en el televisor) del escritorio con ese ítem en la mano, como lo haría el jugador
	private static void usarEnServidor(Minecraft mc, boolean enLaNes, ItemStack enLaMano) {
		BlockPos pies = mc.player.blockPosition();
		mc.getSingleplayerServer().execute(() -> {
			ServerPlayer jugador = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
			jugador.getInventory().setItem(jugador.getInventory().selected, enLaMano.copy());
			BlockPos pos = enLaNes ? pies.offset(-1, 1, 3) : pies.offset(0, 1, 3);
			BlockState estado = jugador.level().getBlockState(pos);
			InteractionResult r = estado.use(jugador.level(), jugador, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
			EmuMod.LOGGER.info("Captura de desarrollo: click en {} con {}: {}", estado.getBlock(), enLaMano, r);
		});
	}

	// Después de dejar de jugar: el jugador tiene que estar bajado del asiento y poder moverse
	private static void estadoAlLevantarse(Minecraft mc) {
		ServerPlayer enServidor = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
		EmuMod.LOGGER.info("Captura de desarrollo: al levantarse cliente montado={} vehículo={} pos={} | servidor montado={} pos={} | pantalla={} pausa={}",
				mc.player.isPassenger(), mc.player.getVehicle(), mc.player.position(), enServidor.isPassenger(), enServidor.position(),
				mc.screen, mc.isPaused());
	}

	private static void capturar(Minecraft mc, String nombre) {
		EmuMod.LOGGER.info("Captura de desarrollo: {} a {} FPS", nombre, mc.getFps());
		Screenshot.grab(mc.gameDirectory, nombre, mc.getMainRenderTarget(),
				mensaje -> EmuMod.LOGGER.info("Captura de desarrollo: {}", mensaje.getString()));
	}
}
