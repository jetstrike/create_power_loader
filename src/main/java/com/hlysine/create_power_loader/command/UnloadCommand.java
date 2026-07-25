package com.hlysine.create_power_loader.command;

import com.hlysine.create_power_loader.content.AbstractChunkLoaderBlockEntity;
import com.hlysine.create_power_loader.content.ChunkLoadManager;
import com.hlysine.create_power_loader.content.ChunkLoader;
import com.hlysine.create_power_loader.content.WeakCollection;
import com.hlysine.create_power_loader.content.ownership.OwnershipHelper;
import com.hlysine.create_power_loader.content.ownership.PlayerActivityTracker;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.util.*;

public class UnloadCommand {

    public static ArgumentBuilder<CommandSourceStack, ?> registerUnload() {
        return Commands.literal("unload")
                .requires(cs -> cs.hasPermission(2))
                .then(Commands.literal("confirm")
                        .then(Commands.argument("uuid", StringArgumentType.word())
                                .executes(ctx -> {
                                    String uuidStr = StringArgumentType.getString(ctx, "uuid");
                                    return handleConfirmUnload(ctx.getSource(), uuidStr);
                                })
                        )
                )
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            return executeUnload(ctx.getSource(), target.getUUID(), target.getName().getString());
                        })
                )
                .executes(ctx -> handleFindStaleUnload(ctx.getSource()));
    }

    public static ArgumentBuilder<CommandSourceStack, ?> registerResume() {
        return Commands.literal("resume")
                .requires(cs -> cs.hasPermission(2))
                .then(Commands.literal("confirm")
                        .then(Commands.argument("uuid", StringArgumentType.word())
                                .executes(ctx -> {
                                    String uuidStr = StringArgumentType.getString(ctx, "uuid");
                                    return handleConfirmResume(ctx.getSource(), uuidStr);
                                })
                        )
                )
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            return executeResume(ctx.getSource(), target.getUUID(), target.getName().getString());
                        })
                );
    }

    private static int handleFindStaleUnload(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        PlayerActivityTracker tracker = PlayerActivityTracker.getOrCreate(server);
        
        Map<UUID, Integer> activeLoaders = new HashMap<>();
        Map<UUID, Integer> activeChunks = new HashMap<>();

        for (WeakCollection<ChunkLoader> collection : ChunkLoadManager.allLoaders.values()) {
            for (ChunkLoader loader : collection) {
                if (loader instanceof AbstractChunkLoaderBlockEntity be) {
                    if (!be.getForcedChunks().isEmpty() && be.getOwnerUUID() != null) {
                        UUID owner = be.getOwnerUUID();
                        activeLoaders.put(owner, activeLoaders.getOrDefault(owner, 0) + 1);
                        activeChunks.put(owner, activeChunks.getOrDefault(owner, 0) + be.getForcedChunks().size());
                    }
                }
            }
        }

        if (activeLoaders.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[PowerLoader] No active player chunk loaders currently running.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        UUID oldestOfflineOwner = null;
        long oldestTimestamp = Long.MAX_VALUE;
        boolean anyOffline = false;

        for (UUID owner : activeLoaders.keySet()) {
            if (server.getPlayerList().getPlayer(owner) == null) {
                anyOffline = true;
                long lastSeen = tracker.getLastSeenEpoch(owner);
                if (lastSeen < oldestTimestamp) {
                    oldestTimestamp = lastSeen;
                    oldestOfflineOwner = owner;
                }
            }
        }

        if (!anyOffline || oldestOfflineOwner == null) {
            source.sendSuccess(() -> Component.literal("[PowerLoader] All active chunk loaders belong to players who are currently online.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        String name = OwnershipHelper.getDisplayName(oldestOfflineOwner, server);
        int loadersCount = activeLoaders.get(oldestOfflineOwner);
        int chunksCount = activeChunks.get(oldestOfflineOwner);

        long now = Instant.now().getEpochSecond();
        long diff = Math.max(0, now - oldestTimestamp);
        long hours = diff / 3600;
        long minutes = (diff % 3600) / 60;
        String timeStr = hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
        if (oldestTimestamp == 0) timeStr = "unknown time";

        UUID targetUuid = oldestOfflineOwner;
        String finalTimeStr = timeStr;
        source.sendSuccess(() -> {
            MutableComponent msg = Component.literal("[PowerLoader] Most stale offline owner: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n  · Inactive for ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(finalTimeStr).withStyle(ChatFormatting.RED))
                    .append(Component.literal(" (" + loadersCount + " loaders | " + chunksCount + " chunks)\n  ").withStyle(ChatFormatting.GRAY));

            String command = "/powerloader unload confirm " + targetUuid.toString();
            MutableComponent button = Component.literal("[Click to Confirm Unloading]")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.RED)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to unload all chunk loaders belonging to " + name)))
                            .withInsertion(command));
            return msg.append(button);
        }, false);

        return 1;
    }

    private static int handleConfirmUnload(CommandSourceStack source, String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            String name = OwnershipHelper.getDisplayName(uuid, source.getServer());
            return executeUnload(source, uuid, name);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Invalid UUID format: " + uuidStr));
            return 0;
        }
    }

    private static int handleConfirmResume(CommandSourceStack source, String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            String name = OwnershipHelper.getDisplayName(uuid, source.getServer());
            return executeResume(source, uuid, name);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Invalid UUID format: " + uuidStr));
            return 0;
        }
    }

    private static int executeUnload(CommandSourceStack source, UUID uuid, String name) {
        MinecraftServer server = source.getServer();
        PlayerActivityTracker tracker = PlayerActivityTracker.getOrCreate(server);
        if (tracker.isForceUnloaded(uuid)) {
            source.sendSuccess(() -> Component.literal("[PowerLoader] " + name + "'s loaders are already force-unloaded.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        tracker.forceUnload(uuid);
        int updated = AbstractChunkLoaderBlockEntity.forceUpdateLoadersFor(uuid, server);
        source.sendSuccess(() -> Component.literal("[PowerLoader] Unloaded " + updated + " chunk loader(s) belonging to " + name + ". (Will reload upon next login).")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int executeResume(CommandSourceStack source, UUID uuid, String name) {
        MinecraftServer server = source.getServer();
        PlayerActivityTracker tracker = PlayerActivityTracker.getOrCreate(server);
        if (!tracker.isForceUnloaded(uuid)) {
            source.sendSuccess(() -> Component.literal("[PowerLoader] " + name + "'s loaders were not force-unloaded.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        tracker.resume(uuid);
        int updated = AbstractChunkLoaderBlockEntity.forceUpdateLoadersFor(uuid, server);
        source.sendSuccess(() -> Component.literal("[PowerLoader] Resumed " + updated + " chunk loader(s) belonging to " + name + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
