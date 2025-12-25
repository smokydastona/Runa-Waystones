package com.example.waystoneinjector.client.serverside;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server request to open the per-player Vault.
 *
 * Payload is empty; server will decide whether to honor it.
 */
public final class OpenVaultC2SPacket {

    public static void encode(OpenVaultC2SPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenVaultC2SPacket decode(FriendlyByteBuf buf) {
        return new OpenVaultC2SPacket();
    }
}
