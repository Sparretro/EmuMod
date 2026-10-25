package com.emumod.client;

import com.emumod.ConsolaItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.emumod.ModBlocks;
import com.emumod.ModEntidades;
import com.emumod.ModItems;
import com.emumod.TelevisorBlock;
import net.fabricmc.api.ClientModInitializer;
import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;

public class EmuModClient implements ClientModInitializer {
	// Tecla libre en Minecraft: abre o cierra las opciones de la GBA (se puede cambiar en Opciones > Controles)
	public static final KeyMapping TECLA_OPCIONES = new KeyMapping("key.emumod.opciones", GLFW.GLFW_KEY_O, "key.categories.emumod");

	@Override
	public void onInitializeClient() {
		EmuladorCliente.migrarCarpetaVieja();
		Creditos.registrar();
		TelevisorBlock.abrirPantalla = pos -> Minecraft.getInstance().setScreen(new TelevisorScreen());
		ConsolaItem.abrirPantalla = modelo -> Minecraft.getInstance().setScreen(new TelevisorScreen(modelo));
		// La GBA en la mano es su modelo 3D, con el juego en la pantalla si hay uno cargado
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.GBA, (stack, modo, pose, buffers, luz, overlay) -> {
			EmuladorCliente emulador = EmuladorCliente.get();
			ModeloGba3D.dibujar(pose, buffers, luz, overlay, 0, emulador.hayJuego() ? emulador.textura() : null, emulador.pausadoPorJugador());
		});
		// La 3DS, la GBA SP y la PSP igual: su modelo en la mano y apoyada, con el juego en sus pantallas
		for (PortatilDoble p : PortatilDoble.values()) {
			BuiltinItemRendererRegistry.INSTANCE.register(ModItems.de(p.modelo), (stack, modo, pose, buffers, luz, overlay) -> {
				EmuladorCliente emulador = EmuladorCliente.de(p.sistema);
				// Fuera de la primera persona (inventario, suelo, marco, mano de otro) se ve chica: va la versión liviana
				boolean lejos = modo != net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
						&& modo != net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
				ModeloDoble.dibujar(p, pose, buffers, luz, overlay, 0, null, emulador.hayJuego() ? emulador.textura() : null, emulador.pausadoPorJugador(),
						lejos);
			});
		}
		BlockEntityRenderers.register(ModBlocks.GBA_ENTIDAD, contexto -> new GbaBlockRenderer());
		for (PortatilDoble p : PortatilDoble.values()) {
			BlockEntityRenderers.register(ModBlocks.entidadAbierta(p.modelo), contexto -> new DobleBlockRenderer(p));
		}
		// NES, N64, PS2 y PS3: la consola, el televisor CRT, los controles y el asiento invisible para jugar sentado
		BlockEntityRenderers.register(ModBlocks.NES_ENTIDAD, contexto -> new NesRenderer<>(ConsolaTele.NES));
		BlockEntityRenderers.register(ModBlocks.N64_ENTIDAD, contexto -> new NesRenderer<>(ConsolaTele.N64));
		BlockEntityRenderers.register(ModBlocks.PS2_ENTIDAD, contexto -> new NesRenderer<>(ConsolaTele.PS2));
		BlockEntityRenderers.register(ModBlocks.PS2_SLIM_ENTIDAD, contexto -> new NesRenderer<>(ConsolaTele.PS2_SLIM));
		BlockEntityRenderers.register(ModBlocks.PS3_ENTIDAD, contexto -> new NesRenderer<>(ConsolaTele.PS3));
		BlockEntityRenderers.register(ModBlocks.PS3_SLIM_ENTIDAD, contexto -> new NesRenderer<>(ConsolaTele.PS3_SLIM));
		BlockEntityRenderers.register(ModBlocks.WII_ENTIDAD, contexto -> new NesRenderer<>(ConsolaTele.WII));
		BlockEntityRenderers.register(ModBlocks.TELE_CRT_ENTIDAD, contexto -> new TeleCrtRenderer());
		EntityRendererRegistry.register(ModEntidades.ASIENTO, NoopRenderer::new);
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.NES, (stack, modo, pose, buffers, luz, overlay) ->
				dibujarItem(pose, buffers, luz, overlay, ConsolaTele.NES.modelo, 180, 0));
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.N64, (stack, modo, pose, buffers, luz, overlay) ->
				dibujarItem(pose, buffers, luz, overlay, ConsolaTele.N64.modelo, 0, 0));
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.PS2, (stack, modo, pose, buffers, luz, overlay) ->
				dibujarItem(pose, buffers, luz, overlay, ConsolaTele.PS2.modelo, 0, 0));
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.PS2_SLIM, (stack, modo, pose, buffers, luz, overlay) ->
				dibujarItem(pose, buffers, luz, overlay, ConsolaTele.PS2_SLIM.modelo, 0, 0));
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.PS3, (stack, modo, pose, buffers, luz, overlay) ->
				dibujarItem(pose, buffers, luz, overlay, ConsolaTele.PS3.modelo, 180, 0));
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.PS3_SLIM, (stack, modo, pose, buffers, luz, overlay) ->
				dibujarItem(pose, buffers, luz, overlay, ConsolaTele.PS3_SLIM.modelo, 180, 0));
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.WII, (stack, modo, pose, buffers, luz, overlay) ->
				dibujarItem(pose, buffers, luz, overlay, ConsolaTele.WII.modelo, 0, 0, 0.19f));
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.TELE_CRT, (stack, modo, pose, buffers, luz, overlay) ->
				dibujarItem(pose, buffers, luz, overlay, TeleCrtRenderer.CRT.modelo(), 0, 0));
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.TELE_PLASMA, (stack, modo, pose, buffers, luz, overlay) ->
				dibujarItem(pose, buffers, luz, overlay, TeleCrtRenderer.PLASMA.modelo(), 0, 0));
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.CONTROL_NES, (stack, modo, pose, buffers, luz, overlay) ->
				dibujarItem(pose, buffers, luz, overlay, "control_nes", 0, 65));
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.CONTROL_N64, (stack, modo, pose, buffers, luz, overlay) ->
				dibujarItem(pose, buffers, luz, overlay, "control_n64", 0, 65));
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.CONTROL_PS2, (stack, modo, pose, buffers, luz, overlay) ->
				dibujarItem(pose, buffers, luz, overlay, ManosControl.PS2.modelo(), 0, 65));
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.CONTROL_PS3, (stack, modo, pose, buffers, luz, overlay) ->
				dibujarItem(pose, buffers, luz, overlay, ManosControl.PS3.modelo(), 0, 65));
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.CONTROL_WII, (stack, modo, pose, buffers, luz, overlay) ->
				dibujarItem(pose, buffers, luz, overlay, ManosControl.WII.modelo(), 0, 65));
		SesionNes.registrar();
		// Que la partida del cartucho quede guardada aunque se cierre Minecraft con un juego abierto
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> EmuladorCliente.todas().forEach(EmuladorCliente::cerrar));
		KeyBindingHelper.registerKeyBinding(TECLA_OPCIONES);
		// Con una portátil (GBA, SP, 3DS o PSP) en la mano y sin estar jugando, la tecla la sube a los ojos con las opciones abiertas
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (TECLA_OPCIONES.consumeClick()) {
				ConsolaItem.Modelo enLaMano = null;
				if (mc.player != null) {
					for (var mano : new net.minecraft.world.item.ItemStack[] {mc.player.getMainHandItem(), mc.player.getOffhandItem()}) {
						for (ConsolaItem.Modelo m : ConsolaItem.Modelo.values()) {
							if (enLaMano == null && mano.is(ModItems.de(m))) {
								enLaMano = m;
							}
						}
					}
				}
				if (mc.screen == null && enLaMano != null) {
					TelevisorScreen pantalla = new TelevisorScreen(enLaMano);
					pantalla.abrirConOpciones();
					mc.setScreen(pantalla);
				}
			}
		});
		DevCaptura.registrarSiCorresponde();
	}

	// Ítem con su modelo 3D centrado en el cubo del ítem, girado para que el frente mire a +z
	private static void dibujarItem(PoseStack pose, MultiBufferSource buffers, int luz, int overlay, String modelo, float giroY, float giroX) {
		dibujarItem(pose, buffers, luz, overlay, modelo, giroY, giroX, 0.95f);
	}

	// ancho: el de la consola en el ítem; la Wii, parada y finita, se achica para que entre a lo largo
	private static void dibujarItem(PoseStack pose, MultiBufferSource buffers, int luz, int overlay, String modelo, float giroY, float giroX,
			float ancho) {
		pose.pushPose();
		pose.translate(0.5f, 0.5f, 0.5f);
		pose.mulPose(Axis.YP.rotationDegrees(giroY));
		pose.mulPose(Axis.XP.rotationDegrees(giroX));
		Modelo3D m = Modelo3D.de(modelo);
		m.centrar(pose, ancho);
		m.dibujar(pose, buffers, luz, overlay, Modelo3D.Ajuste.NINGUNO);
		pose.popPose();
	}
}
