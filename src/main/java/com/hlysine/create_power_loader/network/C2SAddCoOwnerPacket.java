package com.hlysine.create_power_loader.network;

import com.hlysine.create_power_loader.content.AbstractChunkLoaderBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

import static com.hlysine.create_power_loader.CreatePowerLoader.MODID;

/**
 * C → S: Owner requests to add a co-owner by username string.
 * The server looks up the profile in its cache and responds with an updated S2COwnerScreenPacket.
 */
public record C2SAddCoOwnerPacket(BlockPos pos, String username) implements CustomPacketPayload {

    public static final Type<C2SAddCoOwnerPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "add_co_owner"));

    public static final StreamCodec<FriendlyByteBuf, C2SAddCoOwnerPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public C2SAddCoOwnerPacket decode(FriendlyByteBuf buf) {
                    return new C2SAddCoOwnerPacket(buf.readBlockPos(), buf.readUtf(64));
                }

                @Override
                public void encode(FriendlyByteBuf buf, C2SAddCoOwnerPacket packet) {
                    buf.writeBlockPos(packet.pos());
                    buf.writeUtf(packet.username(), 64);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SAddCoOwnerPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var server = player.getServer();
            if (server == null) return;

            var level = player.serverLevel();
            if (!(level.getBlockEntity(packet.pos()) instanceof AbstractChunkLoaderBlockEntity be)) return;

            // Only owner may add co-owners
            if (!player.getUUID().equals(be.getOwnerUUID())) {
                player.sendSystemMessage(Component.literal("[PowerLoader] Only the owner can add co-owners."));
                return;
            }

            // Resolve username → UUID via server profile cache
            var profileOpt = server.getProfileCache().get(packet.username());
            if (profileOpt.isEmpty()) {
                player.sendSystemMessage(Component.literal(
                        "[PowerLoader] Player '" + packet.username() + "' not found. They must have joined this server at least once."));
                return;
            }

            var profile = profileOpt.get();
            var uuid = profile.getId();

            if (uuid == null) {
                player.sendSystemMessage(Component.literal("[PowerLoader] Could not resolve UUID for that player."));
                return;
            }

            if (uuid.equals(be.getOwnerUUID())) {
                player.sendSystemMessage(Component.literal("[PowerLoader] That player is already the owner."));
                return;
            }

            if (!be.addCoOwner(uuid)) {
                player.sendSystemMessage(Component.literal("[PowerLoader] Co-owner list is full (max 7 co-owners)."));
                return;
            }

            player.sendSystemMessage(Component.literal(
                    "[PowerLoader] Added " + profile.getName() + " as a co-owner.").withStyle(net.minecraft.ChatFormatting.GREEN));

            // Re-send the owner screen to the requesting player
            be.openOwnerScreenFor(player);
        });
    }
}
