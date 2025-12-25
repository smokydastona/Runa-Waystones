package com.example.waystoneinjector.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;

/**
 * When the server-side sister mod opens the per-player storage (previously "Vault"),
 * the client receives a normal ChestScreen (6 rows). This mixin swaps only the
 * background texture for that specific screen title, leaving slot layout vanilla.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.ChestScreen")
public abstract class MixinChestScreen {

    private static final @Nonnull ResourceLocation ZE_VOIDROBE_BG = new ResourceLocation(
        "waystoneinjector", "textures/gui/ze_voidrobe.png"
    );
    // The Ze Voidrobe background texture is a 2x-style GUI (352x442).
    // We must use the actual texture size in blit() so UVs map correctly.
    private static final int ZE_VOIDROBE_TEX_W = 352;
    private static final int ZE_VOIDROBE_TEX_H = 442;

    // Animated portal background (same sprite sheet used behind Waystone menus)
    private static final @Nonnull ResourceLocation PORTAL_ANIMATION = new ResourceLocation(
        "waystoneinjector", "textures/gui/portal_animation.png"
    );

    // Mystical portal overlay textures (26 frames)
    private static final ResourceLocation[] MYSTICAL_PORTALS = new ResourceLocation[26];
    static {
        for (int i = 1; i <= 26; i++) {
            MYSTICAL_PORTALS[i - 1] = new ResourceLocation("waystoneinjector", "textures/gui/mystical/mystic_" + i + ".png");
        }
    }

    private static final int PORTAL_FRAME_W = 256;
    private static final int PORTAL_FRAME_H = 256;
    private static final int PORTAL_SHEET_W = 256;
    private static final int PORTAL_SHEET_H = 4096;
    private static final int PORTAL_FRAMES = PORTAL_SHEET_H / PORTAL_FRAME_H;
    private static final long PORTAL_FRAME_TIME_MS = 100L;

    private static void blitAnimatedPortalSheet(GuiGraphics graphics, @Nonnull ResourceLocation texture, int x, int y) {
        int frame = (int) ((System.currentTimeMillis() / PORTAL_FRAME_TIME_MS) % PORTAL_FRAMES);
        int v = frame * PORTAL_FRAME_H;
        graphics.blit(texture, x, y,
            PORTAL_FRAME_W, PORTAL_FRAME_H,
            0.0F, (float) v,
            PORTAL_FRAME_W, PORTAL_FRAME_H,
            PORTAL_SHEET_W, PORTAL_SHEET_H);
    }

    @Inject(
        method = {
            // Dev (named) environment
            "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            // Runtime (obfuscated/official) environment
            "m_7286_(Lnet/minecraft/client/gui/GuiGraphics;FII)V"
        },
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void waystoneinjector_renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        // Title is rendered by the screen; the background render doesn't receive it.
        // We detect the Ze Voidrobe screen by the current screen title string.
        var screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return;
        }

        String title = screen.getTitle().getString();
        String normalized = title == null ? "" : title.trim();

        // Keep "Vault" support for older servers.
        if (!normalized.startsWith("Ze Voidrobe") && !normalized.startsWith("Vault")) {
            return;
        }

        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        // Render portal + mystical movement behind the GUI (matches the Waystones menu vibe).
        // The ze_voidrobe.png should include transparency where you want this to show through.
        int portalX = (screenW - 256) / 2;
        int portalY = (screenH - 256) / 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        blitAnimatedPortalSheet(guiGraphics, PORTAL_ANIMATION, portalX, portalY);
        int randomFrame = (int) ((System.currentTimeMillis() / 100L) % 26);
        ResourceLocation mystical = MYSTICAL_PORTALS[randomFrame];
        if (mystical != null) {
            guiGraphics.blit(mystical, portalX, portalY, 0, 0, 256, 256, 256, 256);
        }

        // ChestScreen uses the standard 6-row chest layout (176x222) for our storage.
        int imageW = 176;
        int imageH = 222;
        int x = (screenW - imageW) / 2;
        int y = (screenH - imageH) / 2;

        // Draw the full 2x texture scaled down into the vanilla chest GUI area.
        guiGraphics.blit(
            ZE_VOIDROBE_BG,
            x,
            y,
            imageW,
            imageH,
            0.0F,
            0.0F,
            ZE_VOIDROBE_TEX_W,
            ZE_VOIDROBE_TEX_H,
            ZE_VOIDROBE_TEX_W,
            ZE_VOIDROBE_TEX_H
        );

        RenderSystem.disableBlend();
        ci.cancel();
    }
}
