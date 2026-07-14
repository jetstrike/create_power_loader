package com.hlysine.create_power_loader.network;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import static com.hlysine.create_power_loader.CreatePowerLoader.MODID;

/**
 * Registers all custom network payloads for Create: Power Loader.
 * Register via the mod event bus in {@code CreatePowerLoader}.
 */
public final class CPLNetwork {

    private CPLNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(CPLNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // Client → Server
        registrar.playToServer(
                C2SClaimLoaderPacket.TYPE,
                C2SClaimLoaderPacket.STREAM_CODEC,
                C2SClaimLoaderPacket::handle);

        registrar.playToServer(
                C2SAddCoOwnerPacket.TYPE,
                C2SAddCoOwnerPacket.STREAM_CODEC,
                C2SAddCoOwnerPacket::handle);

        registrar.playToServer(
                C2SRemoveCoOwnerPacket.TYPE,
                C2SRemoveCoOwnerPacket.STREAM_CODEC,
                C2SRemoveCoOwnerPacket::handle);

        registrar.playToServer(
                C2SToggleTickLoadingPacket.TYPE,
                C2SToggleTickLoadingPacket.STREAM_CODEC,
                C2SToggleTickLoadingPacket::handle);

        // Server → Client
        registrar.playToClient(
                S2COwnerScreenPacket.TYPE,
                S2COwnerScreenPacket.STREAM_CODEC,
                S2COwnerScreenPacket::handle);
    }
}
