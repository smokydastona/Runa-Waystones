package com.example.waystoneinjector.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

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

        // Prefer atlas if present
        ResourceLocation atlas = atlasTexture(resolvedType);
        if (resourceExists(atlas)) {
            return new Sprite(atlas, ATLAS_W, ATLAS_H, BG_U, BG_V, BG_W, BG_H);
        }

        // Legacy background PNGs
        ResourceLocation legacy = legacyBackgroundTexture(resolvedType);
        if (resourceExists(legacy)) {
            return new Sprite(legacy, 256, 256, 0, 0, 256, 256);
        }

        // Final fallback
        ResourceLocation fallback = legacyBackgroundTexture("regular");
        return new Sprite(fallback, 256, 256, 0, 0, 256, 256);
    }

    public static Sprite overlay(String type) {
        String resolvedType = normalizeType(type);

        ResourceLocation atlas = atlasTexture(resolvedType);
        if (resourceExists(atlas)) {
            return new Sprite(atlas, ATLAS_W, ATLAS_H, OVERLAY_U, OVERLAY_V, OVERLAY_W, OVERLAY_H);
        }

        ResourceLocation legacy = legacyOverlayTexture(resolvedType);
        if (resourceExists(legacy)) {
            return new Sprite(legacy, OVERLAY_W, OVERLAY_H, 0, 0, OVERLAY_W, OVERLAY_H);
        }

        // Some types never had overlays; return null by signaling missing texture via a null ResourceLocation.
        // Callers should handle null.
        return null;
    }

    public static Sprite button(String type) {
        String resolvedType = normalizeType(type);

        ResourceLocation atlas = atlasTexture(resolvedType);
        if (resourceExists(atlas)) {
            return new Sprite(atlas, ATLAS_W, ATLAS_H, BUTTON_U, BUTTON_V, BUTTON_W, BUTTON_H);
        }

        ResourceLocation legacy = legacyButtonTexture(resolvedType);
        if (resourceExists(legacy)) {
            return new Sprite(legacy, BUTTON_W, BUTTON_H, 0, 0, BUTTON_W, BUTTON_H);
        }

        // Fallback to regular button
        ResourceLocation fallback = legacyButtonTexture("regular");
        return new Sprite(fallback, BUTTON_W, BUTTON_H, 0, 0, BUTTON_W, BUTTON_H);
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) return "regular";
        return type;
    }

    private static ResourceLocation atlasTexture(String type) {
        return new ResourceLocation("waystoneinjector", "textures/gui/atlases/menu_" + type + ".png");
    }

    private static ResourceLocation legacyBackgroundTexture(String type) {
        return switch (type) {
            case "sharestone" -> new ResourceLocation("waystoneinjector", "textures/gui/sharestone.png");
            case "mossy" -> new ResourceLocation("waystoneinjector", "textures/gui/waystone_mossy.png");
            case "blackstone" -> new ResourceLocation("waystoneinjector", "textures/gui/waystone_blackstone.png");
            case "deepslate" -> new ResourceLocation("waystoneinjector", "textures/gui/waystone_deepslate.png");
            case "endstone" -> new ResourceLocation("waystoneinjector", "textures/gui/waystone_endstone.png");
            case "sandy" -> new ResourceLocation("waystoneinjector", "textures/gui/waystone_sandy.png");
            case "warp_plate" -> new ResourceLocation("waystoneinjector", "textures/gui/warp_plate.png");
            case "portstone" -> new ResourceLocation("waystoneinjector", "textures/gui/portstone.png");
            case "warp_scroll" -> new ResourceLocation("waystoneinjector", "textures/gui/warp_scroll.png");
            case "bound_scroll" -> new ResourceLocation("waystoneinjector", "textures/gui/bound_scroll.png");
            case "warp_stone" -> new ResourceLocation("waystoneinjector", "textures/gui/warp_stone.png");
            case "return_scroll" -> new ResourceLocation("waystoneinjector", "textures/gui/return_scroll.png");
            default -> new ResourceLocation("waystoneinjector", "textures/gui/waystone_regular.png");
        };
    }

    private static ResourceLocation legacyOverlayTexture(String type) {
        return switch (type) {
            case "mossy" -> new ResourceLocation("waystoneinjector", "textures/gui/overlays/mossy.png");
            case "blackstone" -> new ResourceLocation("waystoneinjector", "textures/gui/overlays/blackstone.png");
            case "deepslate" -> new ResourceLocation("waystoneinjector", "textures/gui/overlays/deepslate.png");
            case "endstone" -> new ResourceLocation("waystoneinjector", "textures/gui/overlays/endstone.png");
            case "sandy" -> new ResourceLocation("waystoneinjector", "textures/gui/overlays/sandy.png");
            case "sharestone" -> new ResourceLocation("waystoneinjector", "textures/gui/overlays/sharestone.png");
            case "warp_scroll" -> new ResourceLocation("waystoneinjector", "textures/gui/overlays/warp_scroll.png");
            case "warp_stone" -> new ResourceLocation("waystoneinjector", "textures/gui/overlays/warp_stone.png");
            case "portstone" -> new ResourceLocation("waystoneinjector", "textures/gui/overlays/portstone.png");
            case "regular" -> new ResourceLocation("waystoneinjector", "textures/gui/overlays/regular.png");
            default -> new ResourceLocation("waystoneinjector", "textures/gui/overlays/regular.png");
        };
    }

    private static ResourceLocation legacyButtonTexture(String type) {
        return new ResourceLocation("waystoneinjector", "textures/gui/buttons/" + type + ".png");
    }

    private static boolean resourceExists(ResourceLocation location) {
        try {
            return Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
        } catch (Exception ignored) {
            return false;
        }
    }
}
