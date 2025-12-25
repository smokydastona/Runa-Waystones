package com.example.waystoneinjector.client.serverside;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Server -> Client packet containing server-icon.png bytes.
 *
 * This only works when:
 * - server has the server-side mod installed
 * - client has THIS client mod installed
 */
public record ServerIconS2CPacket(byte[] pngBytes) {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MAX_BYTES = 128 * 1024;

    public static void encode(ServerIconS2CPacket msg, FriendlyByteBuf buf) {
        byte[] bytes = msg.pngBytes == null ? new byte[0] : msg.pngBytes;
        if (bytes.length > MAX_BYTES) {
            byte[] truncated = new byte[MAX_BYTES];
            System.arraycopy(bytes, 0, truncated, 0, MAX_BYTES);
            bytes = truncated;
        }
        buf.writeVarInt(bytes.length);
        buf.writeByteArray(bytes);
    }

    public static ServerIconS2CPacket decode(FriendlyByteBuf buf) {
        int len = buf.readVarInt();
        if (len <= 0 || len > MAX_BYTES) {
            return new ServerIconS2CPacket(new byte[0]);
        }
        return new ServerIconS2CPacket(buf.readByteArray(len));
    }

    public static void handle(ServerIconS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.pngBytes != null && msg.pngBytes.length > 0) {
                ServerIconCache.set(msg.pngBytes);
                LOGGER.debug("Received server icon bytes: {}", msg.pngBytes.length);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
