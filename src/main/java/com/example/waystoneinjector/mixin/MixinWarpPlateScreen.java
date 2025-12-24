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
 * Warp plates are a different UI than the normal waystone selection screen.
 * This mixin keeps the screen vanilla and only swaps the background texture.
 */
@Mixin(targets = "net.blay09.mods.waystones.client.gui.screen.WarpPlateScreen")
public abstract class MixinWarpPlateScreen {

        private static final @Nonnull ResourceLocation WARP_PLATE_BG = new ResourceLocation(
            "waystoneinjector",
            "textures/gui/menu/warp_plate.png"
    );

    @Inject(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V", at = @At("TAIL"))
    private void waystoneinjector_renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        // Draw our background after the original background, but before slots/labels render.
        // We avoid relying on container-screen fields (leftPos/topPos/imageWidth/height) because WarpPlateScreen
        // is not guaranteed to extend AbstractContainerScreen.
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        int texW = 256;
        int texH = 256;
        int x = (screenW - texW) / 2;
        int y = (screenH - texH) / 2;

        guiGraphics.blit(WARP_PLATE_BG, x, y, 0, 0, texW, texH, texW, texH);
    }
}
