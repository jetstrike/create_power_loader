package com.hlysine.create_power_loader.network;

import com.hlysine.create_power_loader.config.CPLConfigs;
import com.hlysine.create_power_loader.content.AbstractChunkLoaderBlockEntity;
import com.hlysine.create_power_loader.content.ownership.OwnershipHelper;
import com.hlysine.create_power_loader.content.ownership.PlayerActivityTracker;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.hlysine.create_power_loader.CreatePowerLoader.MODID;

/**
 * C → S: Toggle tick-loading on a chunk loader (moved from the block's Shift+RClick into the GUI).
 * The server validates that the requester is an authorized user (owner or co-owner).
 */
public record C2SToggleTickLoadingPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<C2SToggleTickLoadingPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "toggle_tick_loading"));

    public static final StreamCodec<ByteBuf, C2SToggleTickLoadingPacket> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(C2SToggleTickLoadingPacket::new, C2SToggleTickLoadingPacket::pos);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SToggleTickLoadingPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            var level = player.serverLevel();
            if (!(level.getBlockEntity(packet.pos()) instanceof AbstractChunkLoaderBlockEntity be)) return;

            // Only owner or co-owner may toggle this
            if (!be.isAuthorizedUser(player.getUUID())) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[PowerLoader] You are not authorized to configure this loader.").withStyle(ChatFormatting.RED));
                return;
            }

            if (!be.tickLoadingEnabled) {
                int limit = CPLConfigs.server().maxTickLoadingLoadersPerPlayer.get();
                boolean bypass = false;
                if (be.getOwnerUUID() != null) {
                    PlayerActivityTracker tracker = PlayerActivityTracker.getOrCreate(level.getServer());
                    bypass = tracker.isBypassed(be.getOwnerUUID()) || (level.getServer().getPlayerList().isOp(player.getGameProfile()) && CPLConfigs.server().opBypassOwnerCheck.get());
                }
                if (!bypass && limit >= 0) {
                    int count = OwnershipHelper.countTickLoadingLoadersFor(be.getOwnerUUID());
                    if (count >= limit) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "[PowerLoader] Cannot enable tick loading: maximum limit reached (" + limit + " per player).")
                                .withStyle(ChatFormatting.RED));
                        return;
                    }
                }
            }

            be.toggleTickLoading();
            // Refresh owner screen for the requesting player
            be.openOwnerScreenFor(player);
        });
    }
}
