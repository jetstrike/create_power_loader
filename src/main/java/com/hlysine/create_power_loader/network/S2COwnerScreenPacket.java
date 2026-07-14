package com.hlysine.create_power_loader.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.hlysine.create_power_loader.CreatePowerLoader.MODID;

/**
 * S → C: Send (or refresh) the owner management screen.
 *
 * <p>Carries the full owner data so the client can build or update the GUI without
 * any additional round-trips.  The client handler either opens a new screen or, if
 * the owner screen is already open, refreshes its state in place.
 */
public record S2COwnerScreenPacket(
        BlockPos pos,
        @Nullable UUID ownerUUID,
        String ownerName,
        long ownerLastSeenEpoch,
        List<OwnerEntry> coOwners,
        boolean tickLoadingEnabled,
        boolean viewerIsOwner,
        boolean isSuppressed
) implements CustomPacketPayload {

    /**
     * A co-owner entry sent to the client.
     */
    public record OwnerEntry(UUID uuid, String name, long lastSeenEpoch) {
    }

    public static final Type<S2COwnerScreenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "owner_screen"));

    public static final StreamCodec<FriendlyByteBuf, S2COwnerScreenPacket> STREAM_CODEC =
            new StreamCodec<>() {

                @Override
                public S2COwnerScreenPacket decode(FriendlyByteBuf buf) {
                    BlockPos pos = buf.readBlockPos();

                    boolean hasOwner = buf.readBoolean();
                    UUID ownerUUID = hasOwner ? buf.readUUID() : null;
                    String ownerName = buf.readUtf(64);
                    long ownerLastSeen = buf.readLong();

                    int coOwnerCount = buf.readVarInt();
                    List<OwnerEntry> coOwners = new ArrayList<>(coOwnerCount);
                    for (int i = 0; i < coOwnerCount; i++) {
                        UUID uuid = buf.readUUID();
                        String name = buf.readUtf(64);
                        long lastSeen = buf.readLong();
                        coOwners.add(new OwnerEntry(uuid, name, lastSeen));
                    }

                    boolean tickLoading = buf.readBoolean();
                    boolean viewerIsOwner = buf.readBoolean();
                    boolean isSuppressed = buf.readBoolean();

                    return new S2COwnerScreenPacket(pos, ownerUUID, ownerName, ownerLastSeen,
                            coOwners, tickLoading, viewerIsOwner, isSuppressed);
                }

                @Override
                public void encode(FriendlyByteBuf buf, S2COwnerScreenPacket packet) {
                    buf.writeBlockPos(packet.pos());

                    boolean hasOwner = packet.ownerUUID() != null;
                    buf.writeBoolean(hasOwner);
                    if (hasOwner) buf.writeUUID(packet.ownerUUID());
                    buf.writeUtf(packet.ownerName(), 64);
                    buf.writeLong(packet.ownerLastSeenEpoch());

                    buf.writeVarInt(packet.coOwners().size());
                    for (OwnerEntry entry : packet.coOwners()) {
                        buf.writeUUID(entry.uuid());
                        buf.writeUtf(entry.name(), 64);
                        buf.writeLong(entry.lastSeenEpoch());
                    }

                    buf.writeBoolean(packet.tickLoadingEnabled());
                    buf.writeBoolean(packet.viewerIsOwner());
                    buf.writeBoolean(packet.isSuppressed());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handler — delegates to {@link com.hlysine.create_power_loader.client.CPLClientPacketHandlers}
     * so that references to {@code Minecraft} only exist in client-only code.
     */
    public static void handle(S2COwnerScreenPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                com.hlysine.create_power_loader.client.CPLClientPacketHandlers.handleOwnerScreen(packet));
    }
}
