package com.hlysine.create_power_loader.network;

import com.hlysine.create_power_loader.content.AbstractChunkLoaderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

import static com.hlysine.create_power_loader.CreatePowerLoader.MODID;

/**
 * C → S: Owner requests to remove a co-owner by UUID.
 */
public record C2SRemoveCoOwnerPacket(BlockPos pos, UUID targetUUID) implements CustomPacketPayload {

    public static final Type<C2SRemoveCoOwnerPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "remove_co_owner"));

    public static final StreamCodec<FriendlyByteBuf, C2SRemoveCoOwnerPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public C2SRemoveCoOwnerPacket decode(FriendlyByteBuf buf) {
                    return new C2SRemoveCoOwnerPacket(buf.readBlockPos(), buf.readUUID());
                }

                @Override
                public void encode(FriendlyByteBuf buf, C2SRemoveCoOwnerPacket packet) {
                    buf.writeBlockPos(packet.pos());
                    buf.writeUUID(packet.targetUUID());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SRemoveCoOwnerPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            var level = player.serverLevel();
            if (!(level.getBlockEntity(packet.pos()) instanceof AbstractChunkLoaderBlockEntity be)) return;

            // Only owner may remove co-owners
            if (!player.getUUID().equals(be.getOwnerUUID())) {
                player.sendSystemMessage(Component.literal("[PowerLoader] Only the owner can remove co-owners."));
                return;
            }

            if (!be.removeCoOwner(packet.targetUUID())) {
                player.sendSystemMessage(Component.literal("[PowerLoader] That player is not a co-owner."));
                return;
            }

            player.sendSystemMessage(Component.literal("[PowerLoader] Co-owner removed.")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));

            // Refresh screen
            be.openOwnerScreenFor(player);
        });
    }
}
