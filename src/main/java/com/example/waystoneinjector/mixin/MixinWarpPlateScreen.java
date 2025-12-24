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
        "waystoneinjector", "textures/gui/menu/warp_plate.png"
    );

    @Inject(
        method = {
            // Dev (named) environment
            "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            // Runtime (obfuscated/official) environment
            "m_7286_(Lnet/minecraft/client/gui/GuiGraphics;FII)V"
        },
        at = @At("TAIL"),
        require = 0
    )
    private void waystoneinjector_renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
    // Draw our background after the original background.
    // We avoid relying on container-screen fields because, without a refmap, field names are not stable.
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        int texW = 256;
        int texH = 256;
        int x = (screenW - texW) / 2;
        int y = (screenH - texH) / 2;

        guiGraphics.blit(WARP_PLATE_BG, x, y, 0, 0, texW, texH, texW, texH);
    }
}
