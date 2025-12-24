package com.example.waystoneinjector.client.gui.widget;

import com.example.waystoneinjector.client.gui.GuiThemeAtlas;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom button that renders with waystone-type themed background textures and optional server icon
 */
@SuppressWarnings("null")
public class ThemedButton extends Button {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation WIDGETS_TEXTURE = new ResourceLocation("textures/gui/widgets.png");

    private static final Map<String, ResourceLocation> SERVER_ICON_CACHE = new ConcurrentHashMap<>();

    private static final Pattern IPV4_UNDERSCORE_PORT = Pattern.compile("^(\\d{1,3}(?:\\.\\d{1,3}){3})_(\\d{1,5})$");
    
    private final Supplier<String> waystoneTypeSupplier;
    private final String serverAddress;
    private final boolean mirrorThemedBackground;
    private ResourceLocation serverIcon;

    private boolean loggedMissingServerAddress;
    private boolean loggedServerIconUnavailable;
    private boolean loggedServerIconRenderFailure;
    
    public ThemedButton(int x, int y, int width, int height, Component message, 
                       OnPress onPress, Supplier<String> waystoneTypeSupplier, String side, 
                       int buttonIndex, int totalButtons, String serverAddress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.waystoneTypeSupplier = waystoneTypeSupplier;
        this.serverAddress = serverAddress;
        this.serverIcon = null;
        this.mirrorThemedBackground = "right".equalsIgnoreCase(side);
    }

    private static String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String normalizeServerAddress(String address) {
        if (address == null) return "";
        String s = address.trim().toLowerCase();
        // Strip common scheme-ish prefixes if present
        if (s.startsWith("minecraft://")) s = s.substring("minecraft://".length());
        if (s.startsWith("mc://")) s = s.substring("mc://".length());
        // Strip trailing path/query fragments (just in case)
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        return s;
    }

    private static String normalizeUnderscorePort(String address) {
        String s = normalizeServerAddress(address);
        if (s.indexOf(':') >= 0) return s;
        Matcher m = IPV4_UNDERSCORE_PORT.matcher(s);
        if (m.matches()) {
            return m.group(1) + ":" + m.group(2);
        }
        return s;
    }

    private static String hostPart(String address) {
        String s = normalizeUnderscorePort(address);
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(0, colon) : s;
    }

    private static String portPartOrEmpty(String address) {
        String s = normalizeUnderscorePort(address);
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : "";
    }

    private static String getServerIconB64(ServerData serverData) {
        // Keep this reflection-based so we don't depend on a specific SRG/mapped name.
        // Most environments expose getIconB64(), but if not, fall back to a field lookup.
        try {
            Object icon = serverData.getClass().getMethod("getIconB64").invoke(serverData);
            return icon instanceof String ? (String) icon : "";
        } catch (Exception ignored) {
        }
        try {
            // Some mappings may name it differently; try a generic getter.
            Object icon = serverData.getClass().getMethod("getIcon").invoke(serverData);
            return icon instanceof String ? (String) icon : "";
        } catch (Exception ignored) {
        }
        try {
            var field = serverData.getClass().getDeclaredField("iconB64");
            field.setAccessible(true);
            Object icon = field.get(serverData);
            return icon instanceof String ? (String) icon : "";
        } catch (Exception ignored) {
        }
        try {
            var field = serverData.getClass().getDeclaredField("icon");
            field.setAccessible(true);
            Object icon = field.get(serverData);
            return icon instanceof String ? (String) icon : "";
        } catch (Exception ignored) {
        }
        return "";
    }

    private static ServerData tryGetActiveServerData(Minecraft mc) {
        // Prefer the official accessor.
        ServerData current = mc.getCurrentServer();
        if (current != null) return current;

        // Some join paths (e.g. Direct Connect) may not populate getCurrentServer();
        // attempt to reflectively ask the connection for ServerData.
        var conn = mc.getConnection();
        if (conn == null) return null;
        try {
            Object sd = conn.getClass().getMethod("getServerData").invoke(conn);
            return sd instanceof ServerData ? (ServerData) sd : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void ensureServerIconLoaded() {
        if (serverIcon != null) {
            return;
        }
        if (serverAddress == null || serverAddress.isEmpty()) {
            if (!loggedMissingServerAddress) {
                loggedMissingServerAddress = true;
                LOGGER.debug("Server icon skipped: missing serverAddress");
            }
            return;
        }

        String normalizedKey = normalizeUnderscorePort(serverAddress);
        ResourceLocation cached = SERVER_ICON_CACHE.get(normalizedKey);
        if (cached != null) {
            serverIcon = cached;
            return;
        }

        try {
            Minecraft mc = Minecraft.getInstance();

            String keyHash = sha1Hex(normalizedKey);

            // If the target server is the current server, use that data directly.
            ServerData current = tryGetActiveServerData(mc);
            if (current != null) {
                String currentNorm = normalizeUnderscorePort(current.ip);
                if (!currentNorm.isEmpty()) {
                    String targetHost = hostPart(serverAddress);
                    String currentHost = hostPart(currentNorm);
                    if (!targetHost.isEmpty() && targetHost.equalsIgnoreCase(currentHost)) {
                        String iconB64 = getServerIconB64(current);
                        ResourceLocation icon = registerFaviconTextureIfPresent(mc, normalizedKey, iconB64);
                        if (icon != null) {
                            SERVER_ICON_CACHE.put(normalizedKey, icon);
                            serverIcon = icon;
                            return;
                        }

                        if (!loggedServerIconUnavailable) {
                            loggedServerIconUnavailable = true;
                            LOGGER.info("Server icon unavailable for current server (keyHash={})", keyHash);
                        }
                    }
                }
            }

            ServerList serverList = new ServerList(mc);
            serverList.load();

            ServerData match = null;
            String targetNorm = normalizeUnderscorePort(serverAddress);
            String targetHost = hostPart(targetNorm);
            String targetPort = portPartOrEmpty(targetNorm);
            for (int i = 0; i < serverList.size(); i++) {
                ServerData server = serverList.get(i);
                if (server == null || server.ip == null) continue;
                String candidateNorm = normalizeUnderscorePort(server.ip);
                if (candidateNorm.equalsIgnoreCase(targetNorm)) {
                    match = server;
                    break;
                }

                // Host-only match (handles default port differences like "example.com" vs "example.com:25565")
                String candidateHost = hostPart(candidateNorm);
                if (!targetHost.isEmpty() && targetHost.equalsIgnoreCase(candidateHost)) {
                    String candidatePort = portPartOrEmpty(candidateNorm);
                    boolean portCompatible = targetPort.isEmpty() || candidatePort.isEmpty() || targetPort.equals(candidatePort);
                    if (portCompatible) {
                        match = server;
                        break;
                    }
                }

                // Fallback substring match for uncommon formats
                if (!targetNorm.isEmpty() && (candidateNorm.contains(targetNorm) || targetNorm.contains(candidateNorm))) {
                    match = server;
                    break;
                }
            }
            if (match == null) {
                if (!loggedServerIconUnavailable) {
                    loggedServerIconUnavailable = true;
                    LOGGER.info("Server icon unavailable: no matching server entry (keyHash={})", keyHash);
                }
                return;
            }

            String iconB64 = getServerIconB64(match);
            ResourceLocation icon = registerFaviconTextureIfPresent(mc, normalizedKey, iconB64);
            if (icon != null) {
                SERVER_ICON_CACHE.put(normalizedKey, icon);
                serverIcon = icon;
            } else if (!loggedServerIconUnavailable) {
                loggedServerIconUnavailable = true;
                LOGGER.info("Server icon unavailable: missing/invalid favicon data (keyHash={})", keyHash);
            }
        } catch (Exception ignored) {
            if (!loggedServerIconUnavailable) {
                loggedServerIconUnavailable = true;
                // Never log server addresses; include only a hashed key.
                LOGGER.warn("Server icon load failed unexpectedly (keyHash={})", sha1Hex(normalizeUnderscorePort(serverAddress)));
            }
        }
    }

    private static ResourceLocation registerFaviconTextureIfPresent(Minecraft mc, String normalizedKey, String iconB64) {
        if (iconB64 == null || iconB64.isBlank()) {
            return null;
        }

        try {
            // Stored value may be raw base64 or a data URL.
            String b64 = iconB64;
            int comma = b64.indexOf(',');
            if (comma >= 0) {
                b64 = b64.substring(comma + 1);
            }

            byte[] pngBytes = Base64.getDecoder().decode(b64);
            NativeImage image;
            try (ByteArrayInputStream in = new ByteArrayInputStream(pngBytes)) {
                image = NativeImage.read(in);
            }

            // Vanilla expects 64x64 favicons. If it's some other size, just skip.
            if (image.getWidth() != 64 || image.getHeight() != 64) {
                image.close();
                return null;
            }

            String id = "server_icons/" + sha1Hex(normalizedKey);
            ResourceLocation iconId = new ResourceLocation("waystoneinjector", id);

            TextureManager textures = mc.getTextureManager();
            textures.register(iconId, new DynamicTexture(image));
            return iconId;
        } catch (Exception ignored) {
            return null;
        }
    }
    
    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render order:
        // 1. Server icon (optional) or default background
        // 2. Themed button texture
        // 3. Button text on top

        ensureServerIconLoaded();

        if (serverIcon != null) {
            renderServerIcon(graphics);
        } else {
            renderDefaultButtonBackground(graphics);
        }
        
        // Render themed overlay texture on top (has transparent spots)
        renderThemedBackground(graphics);
        
        // Render button text on top of everything
        int textColor = this.active ? 0xFFFFFF : 0xA0A0A0;
        graphics.drawCenteredString(
            net.minecraft.client.Minecraft.getInstance().font,
            this.getMessage(),
            getX() + width / 2,
            getY() + (height - 8) / 2,
            textColor
        );
    }
    
    private void renderDefaultButtonBackground(GuiGraphics graphics) {
        // Render a safe vanilla-style button background.
        // In 1.20.1, the classic widgets texture is always present, whereas direct sprite PNG paths
        // (e.g. textures/gui/sprites/widget/button.png) may not exist and can spam FileNotFoundException.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Match the classic 200x20 button region in widgets.png, then stretch to our size.
        // v=46: normal, v=66: hovered. (Active/disabled variants are handled by alpha/text color elsewhere.)
        int v = this.isHoveredOrFocused() ? 66 : 46;

        int half = width / 2;
        graphics.blit(WIDGETS_TEXTURE, getX(), getY(), 0, v, half, height);
        graphics.blit(WIDGETS_TEXTURE, getX() + half, getY(), 200 - (width - half), v, width - half, height);
    }

    private void renderServerIcon(GuiGraphics graphics) {
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            // Scale the 64x64 server icon to fill button (keeping aspect ratio)
            int iconSize = Math.min(width, height);
            int iconX = getX() + (width - iconSize) / 2;
            int iconY = getY() + (height - iconSize) / 2;

            // Scale the full 64x64 favicon to our iconSize (avoid cropping).
            graphics.blit(serverIcon, iconX, iconY, iconSize, iconSize, 0.0F, 0.0F, 64, 64, 64, 64);
        } catch (Exception e) {
            if (!loggedServerIconRenderFailure) {
                loggedServerIconRenderFailure = true;
                LOGGER.warn("Server icon render failed; falling back to default background");
            }
            renderDefaultButtonBackground(graphics);
        }
    }
    
    private void renderThemedBackground(GuiGraphics graphics) {
        GuiThemeAtlas.Sprite sprite = GuiThemeAtlas.button(waystoneTypeSupplier.get());
        if (sprite == null) return;
        
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        // Render the full button region scaled to the widget size.
        // If the button is placed on the right side, mirror the atlas region horizontally so the design faces inward.
        if (mirrorThemedBackground) {
            // Negative scaling flips winding; some render states can cull the quad.
            // Disable culling for this draw so the mirrored button always renders.
            RenderSystem.disableCull();
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + (double) width, getY(), 0.0D);
            graphics.pose().scale(-1.0F, 1.0F, 1.0F);
            graphics.blit(sprite.texture(),
                    0, 0,
                    width, height,
                    (float) sprite.u(), (float) sprite.v(),
                    sprite.w(), sprite.h(),
                    sprite.textureW(), sprite.textureH());
            graphics.pose().popPose();
            RenderSystem.enableCull();
        } else {
            graphics.blit(sprite.texture(),
                    getX(), getY(),
                    width, height,
                    (float) sprite.u(), (float) sprite.v(),
                    sprite.w(), sprite.h(),
                    sprite.textureW(), sprite.textureH());
        }
    }
}
