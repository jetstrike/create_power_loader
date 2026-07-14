package com.hlysine.create_power_loader.network;

import com.hlysine.create_power_loader.content.AbstractChunkLoaderBlockEntity;
import com.hlysine.create_power_loader.content.ownership.OwnershipHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

import static com.hlysine.create_power_loader.CreatePowerLoader.MODID;

/**
 * C → S: Player requests to claim an unclaimed chunk loader at {@code pos}.
 */
public record C2SClaimLoaderPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<C2SClaimLoaderPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "claim_loader"));

    public static final StreamCodec<ByteBuf, C2SClaimLoaderPacket> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(C2SClaimLoaderPacket::new, C2SClaimLoaderPacket::pos);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SClaimLoaderPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            var level = player.serverLevel();
            if (!(level.getBlockEntity(packet.pos()) instanceof AbstractChunkLoaderBlockEntity be)) return;

            // Validate range (must be within 10 blocks)
            if (player.blockPosition().distSqr(packet.pos()) > 100) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[PowerLoader] You are too far away to claim this loader."));
                return;
            }

            if (be.getOwnerUUID() != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[PowerLoader] This loader is already claimed."));
                return;
            }

            be.claimLoader(player);
        });
    }
}
