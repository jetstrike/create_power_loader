package com.hlysine.create_power_loader.command;

import com.hlysine.create_power_loader.content.AbstractChunkLoaderBlockEntity;
import com.hlysine.create_power_loader.content.ownership.OwnershipHelper;
import com.hlysine.create_power_loader.content.ownership.PlayerActivityTracker;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * /powerloader bypass add <player>
 * /powerloader bypass remove <player>
 * /powerloader bypass list
 *
 * Permanently exempts a player from the inactivity suppression check.
 * Requires OP permission level 2.
 */
public class BypassCommand {

    public static ArgumentBuilder<CommandSourceStack, ?> register() {
        return Commands.literal("bypass")
                .requires(cs -> cs.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    add(ctx.getSource(), target.getUUID(), target.getName().getString());
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    remove(ctx.getSource(), target.getUUID(), target.getName().getString());
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            list(ctx.getSource());
                            return 1;
                        })
                );
    }

    private static void add(CommandSourceStack source, UUID uuid, String name) {
        PlayerActivityTracker tracker = PlayerActivityTracker.getOrCreate(source.getServer());
        if (tracker.isBypassed(uuid)) {
            source.sendFailure(Component.literal(name + " is already on the bypass list."));
            return;
        }
        tracker.addBypass(uuid);
        AbstractChunkLoaderBlockEntity.forceUpdateLoadersFor(uuid, source.getServer());
        source.sendSuccess(() -> Component.literal("[PowerLoader] Added " + name + " to the inactivity bypass list.")
                .withStyle(net.minecraft.ChatFormatting.GREEN), true);
    }

    private static void remove(CommandSourceStack source, UUID uuid, String name) {
        PlayerActivityTracker tracker = PlayerActivityTracker.getOrCreate(source.getServer());
        if (!tracker.isBypassed(uuid)) {
            source.sendFailure(Component.literal(name + " is not on the bypass list."));
            return;
        }
        tracker.removeBypass(uuid);
        AbstractChunkLoaderBlockEntity.forceUpdateLoadersFor(uuid, source.getServer());
        source.sendSuccess(() -> Component.literal("[PowerLoader] Removed " + name + " from the inactivity bypass list.")
                .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
    }

    private static void list(CommandSourceStack source) {
        PlayerActivityTracker tracker = PlayerActivityTracker.getOrCreate(source.getServer());
        var bypassed = tracker.getBypassedPlayers();

        source.sendSuccess(() -> Component.literal("[PowerLoader] Bypass list (" + bypassed.size() + " entries):"), false);
        if (bypassed.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  (empty)").withStyle(net.minecraft.ChatFormatting.GRAY), false);
            return;
        }
        for (UUID uuid : bypassed) {
            String displayName = OwnershipHelper.getDisplayName(uuid, source.getServer());
            source.sendSuccess(() -> Component.literal("  · " + displayName + "  (" + uuid + ")")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        }
    }
}
