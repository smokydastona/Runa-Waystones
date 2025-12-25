package com.example.waystoneinjector.mixin;

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

    private static final @Nonnull ResourceLocation VOID_CLOSET_BG = new ResourceLocation(
        "waystoneinjector", "textures/gui/void_closet.png"
    );

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
        // We detect the vault/void-closet screen by the current screen title string.
        var screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return;
        }

        String title = screen.getTitle().getString();
        if (!"Void Closet".equals(title) && !"Vault".equals(title)) {
            return;
        }

        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        // ChestScreen uses the standard 6-row chest layout (176x222) for our storage.
        int imageW = 176;
        int imageH = 222;
        int x = (screenW - imageW) / 2;
        int y = (screenH - imageH) / 2;

        // The vanilla chest GUI texture is 256x256; we match that convention.
        guiGraphics.blit(VOID_CLOSET_BG, x, y, 0, 0, imageW, imageH, 256, 256);
        ci.cancel();
    }
}
