package com.example.waystoneinjector.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;

    @Inject(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V", at = @At("TAIL"))
    private void waystoneinjector_renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        // Draw our background after the original background, but before slots/labels render.
        guiGraphics.blit(WARP_PLATE_BG, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }
}
