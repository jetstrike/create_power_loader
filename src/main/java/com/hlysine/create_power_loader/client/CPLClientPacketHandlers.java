package com.hlysine.create_power_loader.client;

import com.hlysine.create_power_loader.client.screen.ChunkLoaderOwnerScreen;
import com.hlysine.create_power_loader.network.S2COwnerScreenPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only handlers for S2C packets.
 * This class is referenced by packet records but is only ever invoked on the client dist,
 * keeping all {@code Minecraft.*} calls safely isolated from the server class loader.
 */
@OnlyIn(Dist.CLIENT)
public final class CPLClientPacketHandlers {

    private CPLClientPacketHandlers() {
    }

    /**
     * Opens the {@link ChunkLoaderOwnerScreen} if it isn't already showing,
     * or refreshes the data in the currently-open screen if it is.
     */
    public static void handleOwnerScreen(S2COwnerScreenPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ChunkLoaderOwnerScreen existing
                && existing.getBlockPos().equals(packet.pos())) {
            // Refresh the open screen in-place
            existing.refreshData(packet);
        } else {
            mc.setScreen(new ChunkLoaderOwnerScreen(packet));
        }
    }
}
