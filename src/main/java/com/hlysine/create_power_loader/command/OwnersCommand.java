package com.hlysine.create_power_loader.command;

import com.hlysine.create_power_loader.content.AbstractChunkLoaderBlockEntity;
import com.hlysine.create_power_loader.content.ChunkLoadManager;
import com.hlysine.create_power_loader.content.ChunkLoader;
import com.hlysine.create_power_loader.content.WeakCollection;
import com.hlysine.create_power_loader.content.ownership.OwnershipHelper;
import com.hlysine.create_power_loader.content.ownership.PlayerActivityTracker;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.time.Instant;
import java.util.*;

public class OwnersCommand {

    public static ArgumentBuilder<CommandSourceStack, ?> registerOwners() {
        return Commands.literal("owners")
                .requires(cs -> cs.hasPermission(2))
                .executes(ctx -> handleListOwners(ctx.getSource()));
    }

    public static ArgumentBuilder<CommandSourceStack, ?> registerActive() {
        return Commands.literal("active")
                .requires(cs -> cs.hasPermission(2))
                .executes(ctx -> handleListOwners(ctx.getSource()));
    }

    private static int handleListOwners(CommandSourceStack source) {
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

        List<UUID> sortedOwners = new ArrayList<>(activeLoaders.keySet());
        sortedOwners.sort((a, b) -> Integer.compare(activeChunks.getOrDefault(b, 0), activeChunks.getOrDefault(a, 0)));

        source.sendSuccess(() -> Component.literal("\n-+------<< Active Chunk Loader Owners >>------+-" )
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("Showing " + sortedOwners.size() + " active player(s), sorted by chunk count:")
                .withStyle(style -> style.withColor(0xD3DEDC)), false);

        long now = Instant.now().getEpochSecond();

        for (UUID owner : sortedOwners) {
            String name = OwnershipHelper.getDisplayName(owner, server);
            int loadersCount = activeLoaders.get(owner);
            int chunksCount = activeChunks.get(owner);

            boolean isOnline = server.getPlayerList().getPlayer(owner) != null;
            long maxCooldown = OwnershipHelper.getEffectiveThresholdSeconds(owner, server);
            long maxHours = maxCooldown / 3600;
            long maxMinutes = (maxCooldown % 3600) / 60;
            String maxStr = maxHours + "h " + maxMinutes + "m";

            MutableComponent statusComp;
            if (isOnline) {
                statusComp = Component.literal(" [ONLINE | Max Cooldown: " + maxStr + "]").withStyle(ChatFormatting.GREEN);
            } else {
                long lastSeen = tracker.getLastSeenEpoch(owner);
                long diff = Math.max(0, now - lastSeen);
                long hours = diff / 3600;
                long minutes = (diff % 3600) / 60;
                String timeStr = hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
                if (lastSeen == 0) timeStr = "?";

                long timeLeft = Math.max(0, maxCooldown - diff);
                long remHours = timeLeft / 3600;
                long remMinutes = (timeLeft % 3600) / 60;
                String remStr = remHours + "h " + remMinutes + "m left";
                if (timeLeft == 0) remStr = "EXPIRED";

                statusComp = Component.literal(" [Offline " + timeStr + " | " + remStr + "]").withStyle(ChatFormatting.GRAY);
            }

            String unloadCmd = "/powerloader unload confirm " + owner.toString();
            MutableComponent button = Component.literal(" [Unload]")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.RED)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, unloadCmd))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to unload " + name + "'s loaders")))
                            .withInsertion(unloadCmd));

            source.sendSuccess(() -> Component.literal("  · ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                    .append(statusComp)
                    .append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(loadersCount + " loaders, " + chunksCount + " chunks").withStyle(style -> style.withColor(0xFFAD60)))
                    .append(button), false);
        }

        source.sendSuccess(() -> Component.literal("-+--------------------------------------------+-\n")
                .withStyle(ChatFormatting.WHITE), false);

        return 1;
    }
}
