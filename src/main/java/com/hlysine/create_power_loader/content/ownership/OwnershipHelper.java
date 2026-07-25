package com.hlysine.create_power_loader.content.ownership;

import com.hlysine.create_power_loader.config.CPLConfigs;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Static utilities for all ownership-related decisions:
 *  - Is any authorized player (owner + co-owners) still within the inactivity window?
 *  - Should ownership transfer to a co-owner?
 *  - Broadcast suppression notifications to online OPs.
 *  - Resolve display names from UUIDs.
 */
public final class OwnershipHelper {

    private OwnershipHelper() {
    }

    // -------------------------------------------------------------------------
    // Activity check
    // -------------------------------------------------------------------------

    /**
     * Returns true if at least one authorized player (owner or co-owner) has logged in
     * within the configured inactivity threshold, or if any authorized player is bypassed.
     *
     * @param ownerUUID  the primary owner UUID (null = unclaimed → always returns false)
     * @param coOwners   list of co-owner UUIDs (may be empty)
     * @param server     the running server
     */
    public static boolean hasActiveAuthorizedPlayer(
            @Nullable UUID ownerUUID,
            List<UUID> coOwners,
            MinecraftServer server) {

        if (ownerUUID == null) return false;

        PlayerActivityTracker tracker = PlayerActivityTracker.getOrCreate(server);
        long thresholdSeconds = (long) CPLConfigs.server().inactiveThresholdHours.get() * 3600L;

        if (isPlayerActive(ownerUUID, tracker, thresholdSeconds, server)) return true;
        for (UUID coOwner : coOwners) {
            if (isPlayerActive(coOwner, tracker, thresholdSeconds, server)) return true;
        }
        return false;
    }

    public static boolean isForceUnloaded(@Nullable UUID ownerUUID, MinecraftServer server) {
        if (ownerUUID == null) return false;
        return PlayerActivityTracker.getOrCreate(server).isForceUnloaded(ownerUUID);
    }

    private static boolean isPlayerActive(
            UUID uuid,
            PlayerActivityTracker tracker,
            long thresholdSeconds,
            MinecraftServer server) {

        if (!CPLConfigs.server().opBypassOwnerCheck.get()) {
            // Bypass entirely disabled — pure time check only
            return tracker.secondsSinceLastSeen(uuid) < thresholdSeconds;
        }

        // Manual bypass list
        if (tracker.isBypassed(uuid)) return true;

        // Live OP check (covers admins who may never have their UUID in the bypass list)
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null && server.getPlayerList().isOp(online.getGameProfile())) return true;

        // Time-based check
        return tracker.secondsSinceLastSeen(uuid) < thresholdSeconds;
    }

    // -------------------------------------------------------------------------
    // Ownership transfer
    // -------------------------------------------------------------------------

    /**
     * Finds the co-owner UUID that should receive ownership if the current owner has been
     * absent for >= ownershipTransferDays.  Returns null if no transfer is needed or possible.
     *
     * @param ownerUUID  current owner
     * @param coOwners   co-owner list (unmodified by this call)
     * @param server     running server
     */
    @Nullable
    public static UUID findTransferCandidate(
            @Nullable UUID ownerUUID,
            List<UUID> coOwners,
            MinecraftServer server) {

        if (ownerUUID == null || coOwners.isEmpty()) return null;

        PlayerActivityTracker tracker = PlayerActivityTracker.getOrCreate(server);
        long transferThreshold = (long) CPLConfigs.server().ownershipTransferDays.get() * 86400L;

        if (tracker.secondsSinceLastSeen(ownerUUID) < transferThreshold) return null;

        // Find the most recently seen co-owner
        UUID bestCandidate = null;
        long smallestGap = Long.MAX_VALUE;
        for (UUID coOwner : coOwners) {
            long gap = tracker.secondsSinceLastSeen(coOwner);
            if (gap < smallestGap) {
                smallestGap = gap;
                bestCandidate = coOwner;
            }
        }
        return bestCandidate;
    }

    // -------------------------------------------------------------------------
    // OP Notification
    // -------------------------------------------------------------------------

    /**
     * Sends a suppression notification to all online players with OP permission (level >= 2).
     *
     * @param ownerUUID   the suppressed owner (for name lookup)
     * @param loaderCount how many loaders were just suppressed
     * @param server      running server
     */
    public static void notifyOpsOfSuppression(
            UUID ownerUUID,
            int loaderCount,
            MinecraftServer server) {

        if (!CPLConfigs.server().notifyOpsOnSuppression.get()) return;

        String ownerName = getDisplayName(ownerUUID, server);
        int hours = CPLConfigs.server().inactiveThresholdHours.get();

        Component msg = Component.literal("[PowerLoader] ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(ownerName)
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("'s " + loaderCount + " loader(s) suppressed — inactive for ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(hours + "h")
                        .withStyle(ChatFormatting.RED))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY));

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2) || server.getPlayerList().isOp(player.getGameProfile())) {
                player.sendSystemMessage(msg);
            }
        }
    }

    /**
     * Sends an ownership-transfer notification to all online OPs.
     */
    public static void notifyOpsOfTransfer(
            UUID oldOwner,
            UUID newOwner,
            MinecraftServer server) {

        String oldName = getDisplayName(oldOwner, server);
        String newName = getDisplayName(newOwner, server);

        Component msg = Component.literal("[PowerLoader] Ownership of ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(oldName).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("'s loaders transferred to ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(newName).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" (owner inactive for " + CPLConfigs.server().ownershipTransferDays.get() + " days).").withStyle(ChatFormatting.GRAY));

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2) || server.getPlayerList().isOp(player.getGameProfile())) {
                player.sendSystemMessage(msg);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Name resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves a display name for a UUID.  Prefers online player name, then profile cache,
     * then falls back to a truncated UUID string.
     */
    public static String getDisplayName(UUID uuid, MinecraftServer server) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getName().getString();
        Optional<GameProfile> profile = server.getProfileCache().get(uuid);
        return profile.map(GameProfile::getName)
                .orElse(uuid.toString().substring(0, 8) + "...");
    }
}
