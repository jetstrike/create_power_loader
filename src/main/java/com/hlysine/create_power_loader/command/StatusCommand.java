package com.hlysine.create_power_loader.command;

import com.hlysine.create_power_loader.config.CPLConfigs;
import com.hlysine.create_power_loader.content.ownership.OwnershipHelper;
import com.hlysine.create_power_loader.content.ownership.PlayerActivityTracker;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class StatusCommand {

    public static ArgumentBuilder<CommandSourceStack, ?> register() {
        return Commands.literal("status")
                .executes(ctx -> handleSelf(ctx))
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(cs -> cs.hasPermission(2))
                        .executes(ctx -> handleTarget(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))));
    }

    private static int handleSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return handleTarget(ctx.getSource(), player);
    }

    private static int handleTarget(CommandSourceStack source, ServerPlayer target) {
        MinecraftServer server = source.getServer();
        UUID owner = target.getUUID();
        String name = target.getGameProfile().getName();

        PlayerActivityTracker tracker = PlayerActivityTracker.getOrCreate(server);
        int configuredChunks = OwnershipHelper.getTotalConfiguredChunksFor(owner);
        int tickLoaders = OwnershipHelper.countTickLoadingLoadersFor(owner);
        int tickLimit = CPLConfigs.server().maxTickLoadingLoadersPerPlayer.get();

        long primarySeconds = OwnershipHelper.getEffectiveThresholdSeconds(owner, server);
        long coOwnerSeconds = (long) (primarySeconds * CPLConfigs.server().coOwnerActivityMultiplier.get());

        boolean isBypassed = tracker.isBypassed(owner) || (server.getPlayerList().isOp(target.getGameProfile()) && CPLConfigs.server().opBypassOwnerCheck.get());
        boolean isForceUnloaded = tracker.isForceUnloaded(owner);

        source.sendSuccess(() -> Component.literal("\n=== PowerLoader Status: " + name + " ===").withStyle(ChatFormatting.GOLD), false);

        if (isForceUnloaded) {
            source.sendSuccess(() -> Component.literal(" [WARNING] Machines are administratively force-unloaded!").withStyle(ChatFormatting.RED), false);
        } else if (isBypassed) {
            source.sendSuccess(() -> Component.literal(" [STATUS] Operator Bypass / Permanent Loading Enabled").withStyle(ChatFormatting.GREEN), false);
        }

        source.sendSuccess(() -> Component.literal(" · Configured Capacity: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(configuredChunks + " chunk(s)").withStyle(ChatFormatting.YELLOW)), false);

        ChatFormatting limitStyle = (tickLimit >= 0 && tickLoaders >= tickLimit) ? ChatFormatting.RED : ChatFormatting.GREEN;
        String limitStr = tickLimit < 0 ? "Unlimited" : String.valueOf(tickLimit);
        source.sendSuccess(() -> Component.literal(" · Tick Loading Machines: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(tickLoaders + " / " + limitStr + " max").withStyle(limitStyle)), false);

        source.sendSuccess(() -> Component.literal(" · Primary Cooldown (After Logout): ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formatTime(primarySeconds)).withStyle(ChatFormatting.AQUA)), false);
        source.sendSuccess(() -> Component.literal(" · Co-Owner Cooldown (After Logout): ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formatTime(coOwnerSeconds)).withStyle(ChatFormatting.DARK_AQUA)), false);

        return 1;
    }

    private static String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }
}
