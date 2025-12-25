package com.example.waystoneinjector.client.serverside;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

/**
 * Optional bridge to the server-side sister mod.
 *
 * Requirements:
 * - Client mod remains CLIENT-only and must still connect to servers without the server mod.
 * - Server mod remains SERVER-only and must still accept vanilla/modded clients without this client mod.
 *
 * How it works:
 * - If the server has the channel present, we can send C2S requests (like open vault) and receive S2C data (like server icon).
 * - If absent, all calls no-op (optionally with a friendly chat message).
 */
@SuppressWarnings("null")
public final class ServerSideNetwork {

    private static final Logger LOGGER = LogUtils.getLogger();

    // This must match the server mod's channel id.
    private static final ResourceLocation CHANNEL_ID = new ResourceLocation("waystoneinjector_server", "main");
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        CHANNEL_ID,
        () -> PROTOCOL,
        PROTOCOL::equals,
        PROTOCOL::equals
    );

    private static int nextId = 0;

    private ServerSideNetwork() {
    }

    public static void init() {
        CHANNEL.messageBuilder(OpenVaultC2SPacket.class, nextId++)
            .encoder(OpenVaultC2SPacket::encode)
            .decoder(OpenVaultC2SPacket::decode)
            // Client should never receive this packet.
            .consumerMainThread((msg, ctx) -> ctx.get().setPacketHandled(true))
            .add();

        CHANNEL.messageBuilder(ServerIconS2CPacket.class, nextId++)
            .encoder(ServerIconS2CPacket::encode)
            .decoder(ServerIconS2CPacket::decode)
            .consumerMainThread(ServerIconS2CPacket::handle)
            .add();

        LOGGER.info("ServerSideNetwork initialized (channel={})", CHANNEL_ID);
    }

    public static boolean isServerSideModPresent() {
        Minecraft mc = Minecraft.getInstance();
        var packetListener = mc.getConnection();
        if (packetListener == null) {
            return false;
        }

        Connection connection = packetListener.getConnection();
        if (connection == null) {
            return false;
        }
        return CHANNEL.isRemotePresent(connection);
    }

    public static void requestOpenVault(boolean chatIfUnavailable) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) {
            return;
        }

        if (!isServerSideModPresent()) {
            if (chatIfUnavailable) {
                //noinspection ConstantConditions
                player.sendSystemMessage(Component.literal("Ze Voidrobe server mod not installed on this server."));
            }
            return;
        }

        CHANNEL.sendToServer(new OpenVaultC2SPacket());
    }
}
