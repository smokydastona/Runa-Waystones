package com.example.waystoneinjector.client.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

import java.util.function.Consumer;

/**
 * Simple animated image button that renders frames from a vertical sprite sheet.
 *
 * This is used for the Ze Voidrobe icon button.
 */
@SuppressWarnings("null")
public final class AnimatedImageButton extends AbstractButton {

    private final @Nonnull ResourceLocation texture;
    private final int frameWidth;
    private final int frameHeight;
    private final int frameCount;
    private final long frameTimeMs;
    private final @Nonnull Consumer<AnimatedImageButton> onPress;

    /**
     * @param texture      Texture resource (vertical sprite sheet)
     * @param x            Button x
     * @param y            Button y
     * @param width        Button width
     * @param height       Button height
     * @param frameWidth   Frame width in texture
     * @param frameHeight  Frame height in texture
     * @param frameCount   Number of frames in the sheet
     * @param frameTimeMs  Duration per frame in ms
     * @param onPress      Click handler
     * @param message      Accessibility/tooltip message
     */
    public AnimatedImageButton(
        @Nonnull ResourceLocation texture,
        int x,
        int y,
        int width,
        int height,
        int frameWidth,
        int frameHeight,
        int frameCount,
        long frameTimeMs,
        @Nonnull Consumer<AnimatedImageButton> onPress,
        @Nonnull Component message
    ) {
        super(x, y, width, height, message);
        this.texture = texture;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.frameCount = Math.max(1, frameCount);
        this.frameTimeMs = Math.max(1L, frameTimeMs);
        this.onPress = onPress;
    }

    @Override
    public void onPress() {
        onPress.accept(this);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);

        int frame = (int) ((System.currentTimeMillis() / frameTimeMs) % frameCount);
        int u = 0;
        int v = frame * frameHeight;

        // Texture size matches the full sheet (frameHeight * frameCount).
        guiGraphics.blit(texture, getX(), getY(), u, v, width, height, frameWidth, frameHeight * frameCount);

        RenderSystem.disableBlend();
    }
}
