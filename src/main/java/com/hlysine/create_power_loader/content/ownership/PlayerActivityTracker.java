package com.hlysine.create_power_loader.content.ownership;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;

/**
 * Persistent (SavedData) store of the last login epoch (seconds) for every player UUID,
 * and the set of UUIDs that are manually bypassed from inactivity checks.
 *
 * Stored on the overworld so it survives restarts automatically.
 */
public class PlayerActivityTracker extends SavedData {

    public record GlobalLoaderPos(@NotNull ResourceLocation dimension, @NotNull BlockPos pos) {
    }

    private static final String DATA_NAME = "create_power_loader_activity";
    private static final String TAG_PLAYERS = "Players";
    private static final String TAG_UUID = "UUID";
    private static final String TAG_LAST_SEEN = "LastSeen";
    private static final String TAG_BYPASSED = "BypassedUUIDs";
    private static final String TAG_FORCE_UNLOADED = "ForceUnloadedUUIDs";
    private static final String TAG_TRACKED_LOADERS = "TrackedLoaders";
    private static final String TAG_LOADER_LIST = "Loaders";
    private static final String TAG_DIMENSION = "Dim";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";

    /** Epoch-second of the most-recent login for each UUID. */
    private final Map<UUID, Long> lastSeenMap = new HashMap<>();

    /** UUIDs permanently exempted from inactivity suppression via admin command. */
    private final Set<UUID> bypassedPlayers = new HashSet<>();

    /** UUIDs manually suppressed/unloaded by an operator command. */
    private final Set<UUID> forceUnloadedPlayers = new HashSet<>();

    /** Coordinates of chunk loaders associated with each player UUID (owners and co-owners). */
    private final Map<UUID, Set<GlobalLoaderPos>> trackedLoaders = new HashMap<>();

    public static final SavedData.Factory<PlayerActivityTracker> FACTORY = new SavedData.Factory<>(
            PlayerActivityTracker::new,
            PlayerActivityTracker::load,
            null
    );

    public PlayerActivityTracker() {
    }

    // -------------------------------------------------------------------------
    // Accessor
    // -------------------------------------------------------------------------

    public static PlayerActivityTracker getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // -------------------------------------------------------------------------
    // Activity recording
    // -------------------------------------------------------------------------

    /** Call on PlayerLoggedInEvent — resets the 72-hour clock and lifts forced unload for this player. */
    public void recordSeen(UUID uuid) {
        lastSeenMap.put(uuid, Instant.now().getEpochSecond());
        forceUnloadedPlayers.remove(uuid);
        setDirty();
    }

    /** Returns the stored last-seen epoch, or 0 if the player was never seen. */
    public long getLastSeenEpoch(UUID uuid) {
        return lastSeenMap.getOrDefault(uuid, 0L);
    }

    /**
     * Returns how many seconds have elapsed since this player last logged in.
     * Returns {@link Long#MAX_VALUE} if the player has never been seen by this tracker.
     */
    public long secondsSinceLastSeen(UUID uuid) {
        long lastSeen = lastSeenMap.getOrDefault(uuid, -1L);
        if (lastSeen < 0) return Long.MAX_VALUE;
        return Instant.now().getEpochSecond() - lastSeen;
    }

    // -------------------------------------------------------------------------
    // Bypass management
    // -------------------------------------------------------------------------

    public void addBypass(UUID uuid) {
        bypassedPlayers.add(uuid);
        setDirty();
    }

    public void removeBypass(UUID uuid) {
        bypassedPlayers.remove(uuid);
        setDirty();
    }

    public boolean isBypassed(UUID uuid) {
        return bypassedPlayers.contains(uuid);
    }

    public Set<UUID> getBypassedPlayers() {
        return Collections.unmodifiableSet(bypassedPlayers);
    }

    /** Read-only view of the last-seen map (for commands/display). */
    public Map<UUID, Long> getLastSeenMap() {
        return Collections.unmodifiableMap(lastSeenMap);
    }

    // -------------------------------------------------------------------------
    // Force unload management
    // -------------------------------------------------------------------------

    public void forceUnload(UUID uuid) {
        forceUnloadedPlayers.add(uuid);
        setDirty();
    }

    public boolean resume(UUID uuid) {
        boolean removed = forceUnloadedPlayers.remove(uuid);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public boolean isForceUnloaded(UUID uuid) {
        return forceUnloadedPlayers.contains(uuid);
    }

    public Set<UUID> getForceUnloadedPlayers() {
        return Collections.unmodifiableSet(forceUnloadedPlayers);
    }

    // -------------------------------------------------------------------------
    // Loader location tracking
    // -------------------------------------------------------------------------

    public void trackLoader(UUID user, ResourceLocation dimension, BlockPos pos) {
        if (user == null || dimension == null || pos == null) return;
        boolean added = trackedLoaders.computeIfAbsent(user, k -> new HashSet<>()).add(new GlobalLoaderPos(dimension, pos.immutable()));
        if (added) {
            setDirty();
        }
    }

    public void untrackLoader(UUID user, ResourceLocation dimension, BlockPos pos) {
        if (user == null || dimension == null || pos == null) return;
        Set<GlobalLoaderPos> set = trackedLoaders.get(user);
        if (set != null && set.remove(new GlobalLoaderPos(dimension, pos))) {
            if (set.isEmpty()) {
                trackedLoaders.remove(user);
            }
            setDirty();
        }
    }

    public Set<GlobalLoaderPos> getTrackedLoaders(UUID user) {
        Set<GlobalLoaderPos> set = trackedLoaders.get(user);
        return set != null ? Collections.unmodifiableSet(new HashSet<>(set)) : Collections.emptySet();
    }

    // -------------------------------------------------------------------------
    // SavedData serialization
    // -------------------------------------------------------------------------

    public static PlayerActivityTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerActivityTracker tracker = new PlayerActivityTracker();

        ListTag players = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            try {
                UUID uuid = UUID.fromString(entry.getString(TAG_UUID));
                long lastSeen = entry.getLong(TAG_LAST_SEEN);
                tracker.lastSeenMap.put(uuid, lastSeen);
            } catch (IllegalArgumentException ignored) {
            }
        }

        ListTag bypassed = tag.getList(TAG_BYPASSED, Tag.TAG_STRING);
        for (int i = 0; i < bypassed.size(); i++) {
            try {
                tracker.bypassedPlayers.add(UUID.fromString(bypassed.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }

        ListTag forceUnloaded = tag.getList(TAG_FORCE_UNLOADED, Tag.TAG_STRING);
        for (int i = 0; i < forceUnloaded.size(); i++) {
            try {
                tracker.forceUnloadedPlayers.add(UUID.fromString(forceUnloaded.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }

        ListTag trackedList = tag.getList(TAG_TRACKED_LOADERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < trackedList.size(); i++) {
            CompoundTag entry = trackedList.getCompound(i);
            try {
                UUID uuid = UUID.fromString(entry.getString(TAG_UUID));
                ListTag loaders = entry.getList(TAG_LOADER_LIST, Tag.TAG_COMPOUND);
                Set<GlobalLoaderPos> set = new HashSet<>();
                for (int j = 0; j < loaders.size(); j++) {
                    CompoundTag lTag = loaders.getCompound(j);
                    ResourceLocation dim = ResourceLocation.tryParse(lTag.getString(TAG_DIMENSION));
                    if (dim != null) {
                        BlockPos pos = new BlockPos(lTag.getInt(TAG_X), lTag.getInt(TAG_Y), lTag.getInt(TAG_Z));
                        set.add(new GlobalLoaderPos(dim, pos));
                    }
                }
                if (!set.isEmpty()) {
                    tracker.trackedLoaders.put(uuid, set);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        return tracker;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Long> entry : lastSeenMap.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putString(TAG_UUID, entry.getKey().toString());
            playerTag.putLong(TAG_LAST_SEEN, entry.getValue());
            players.add(playerTag);
        }
        tag.put(TAG_PLAYERS, players);

        ListTag bypassed = new ListTag();
        for (UUID uuid : bypassedPlayers) {
            bypassed.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put(TAG_BYPASSED, bypassed);

        ListTag forceUnloaded = new ListTag();
        for (UUID uuid : forceUnloadedPlayers) {
            forceUnloaded.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put(TAG_FORCE_UNLOADED, forceUnloaded);

        ListTag trackedList = new ListTag();
        for (Map.Entry<UUID, Set<GlobalLoaderPos>> entry : trackedLoaders.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag playerTag = new CompoundTag();
            playerTag.putString(TAG_UUID, entry.getKey().toString());
            ListTag loaders = new ListTag();
            for (GlobalLoaderPos loaderPos : entry.getValue()) {
                CompoundTag lTag = new CompoundTag();
                lTag.putString(TAG_DIMENSION, loaderPos.dimension().toString());
                lTag.putInt(TAG_X, loaderPos.pos().getX());
                lTag.putInt(TAG_Y, loaderPos.pos().getY());
                lTag.putInt(TAG_Z, loaderPos.pos().getZ());
                loaders.add(lTag);
            }
            playerTag.put(TAG_LOADER_LIST, loaders);
            trackedList.add(playerTag);
        }
        tag.put(TAG_TRACKED_LOADERS, trackedList);

        return tag;
    }
}
