package com.example.waystoneinjector.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

/**
 * Atlas-backed texture lookup for the themed Waystones GUIs.
 *
 * Layout (default 256x384):
 * - Background: (0,0) size 256x256
 * - List overlay: (0,256) size 220x36
 * - Button: (0,292) size 64x32
 *
 * All lookups fall back to legacy per-element PNGs when an atlas (or region) is not available.
 */
public final class GuiThemeAtlas {

    private GuiThemeAtlas() {}

    public static final int ATLAS_W = 256;
    public static final int ATLAS_H = 384;

    public static final int BG_U = 0;
    public static final int BG_V = 0;
    public static final int BG_W = 256;
    public static final int BG_H = 256;

    public static final int OVERLAY_U = 0;
    public static final int OVERLAY_V = 256;
    public static final int OVERLAY_W = 220;
    public static final int OVERLAY_H = 36;

    public static final int BUTTON_U = 0;
    public static final int BUTTON_V = 292;
    public static final int BUTTON_W = 64;
    public static final int BUTTON_H = 32;

    public record Sprite(ResourceLocation texture, int textureW, int textureH, int u, int v, int w, int h) {}

    public static Sprite background(String type) {
        String resolvedType = normalizeType(type);

        ResourceLocation atlas = atlasTextureOrRegular(resolvedType);
        return new Sprite(atlas, ATLAS_W, ATLAS_H, BG_U, BG_V, BG_W, BG_H);
    }

    public static Sprite overlay(String type) {
        String resolvedType = normalizeType(type);

        ResourceLocation atlas = atlasTextureOrRegular(resolvedType);
        return new Sprite(atlas, ATLAS_W, ATLAS_H, OVERLAY_U, OVERLAY_V, OVERLAY_W, OVERLAY_H);
    }

    public static Sprite button(String type) {
        String resolvedType = normalizeType(type);

        ResourceLocation atlas = atlasTextureOrRegular(resolvedType);
        return new Sprite(atlas, ATLAS_W, ATLAS_H, BUTTON_U, BUTTON_V, BUTTON_W, BUTTON_H);
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) return "regular";
        return type;
    }

    private static ResourceLocation atlasTextureOrRegular(String type) {
        ResourceLocation candidate = new ResourceLocation("waystoneinjector", "textures/gui/atlases/menu_" + type + ".png");
        if (resourceExists(candidate)) {
            return candidate;
        }
        return new ResourceLocation("waystoneinjector", "textures/gui/atlases/menu_regular.png");
    }

    private static boolean resourceExists(@Nonnull ResourceLocation location) {
        try {
            return Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
        } catch (Exception ignored) {
            return false;
        }
    }
}
