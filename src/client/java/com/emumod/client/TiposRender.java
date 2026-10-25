package com.emumod.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Tipos de render propios. Extiende RenderType solo para poder usar sus clases protegidas; nunca se instancia.
 */
final class TiposRender extends RenderType {
	private TiposRender() {
		super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {
		}, () -> {
		});
	}

	private static final java.util.Map<ResourceLocation, RenderType> SOLIDOS = new java.util.HashMap<>();
	private static final java.util.Map<ResourceLocation, RenderType> TRANSLUCIDOS = new java.util.HashMap<>();

	/**
	 * Como RenderType.entityCutoutNoCull o entityTranslucent, pero de triángulos: los modelos 3D vienen en triángulos y
	 * así no hace falta repetir un vértice por triángulo (25 % menos de vértices por cuadro).
	 */
	static RenderType entidadTriangulos(ResourceLocation textura, boolean translucida) {
		return (translucida ? TRANSLUCIDOS : SOLIDOS).computeIfAbsent(textura, t -> create(
				"televisor_triangulos_" + (translucida ? "translucidos" : "solidos"), DefaultVertexFormat.NEW_ENTITY,
				// Sin ordenar al subir: el orden de translúcidos de Minecraft supone cuadriláteros (son solo las etiquetas de la NES)
				VertexFormat.Mode.TRIANGLES, 256, true, false,
				CompositeState.builder()
						.setShaderState(translucida ? RENDERTYPE_ENTITY_TRANSLUCENT_SHADER : RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
						.setTextureState(new TextureStateShard(t, false, false))
						.setTransparencyState(translucida ? TRANSLUCENT_TRANSPARENCY : NO_TRANSPARENCY)
						.setCullState(NO_CULL)
						.setLightmapState(LIGHTMAP)
						.setOverlayState(OVERLAY)
						.createCompositeState(true)));
	}

	/** Como RenderType.text (sin sombreado y con lightmap) pero eligiendo si la textura se filtra lineal. */
	static RenderType pantalla(String nombre, ResourceLocation textura, boolean suave) {
		return create(nombre, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
				CompositeState.builder()
						.setShaderState(RENDERTYPE_TEXT_SHADER)
						.setTextureState(new TextureStateShard(textura, suave, false))
						.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
						.setLightmapState(LIGHTMAP)
						.createCompositeState(false));
	}
}
