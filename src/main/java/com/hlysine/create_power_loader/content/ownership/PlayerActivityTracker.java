package com.hlysine.create_power_loader.content.ownership;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.Instant;
import java.util.*;

/**
 * Persistent (SavedData) store of the last login epoch (seconds) for every player UUID,
 * and the set of UUIDs that are manually bypassed from inactivity checks.
 *
 * Stored on the overworld so it survives restarts automatically.
 */
public class PlayerActivityTracker extends SavedData {

    private static final String DATA_NAME = "create_power_loader_activity";
    private static final String TAG_PLAYERS = "Players";
    private static final String TAG_UUID = "UUID";
    private static final String TAG_LAST_SEEN = "LastSeen";
    private static final String TAG_BYPASSED = "BypassedUUIDs";

    /** Epoch-second of the most-recent login for each UUID. */
    private final Map<UUID, Long> lastSeenMap = new HashMap<>();

    /** UUIDs permanently exempted from inactivity suppression via admin command. */
    private final Set<UUID> bypassedPlayers = new HashSet<>();

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

    /** Call on PlayerLoggedInEvent — resets the 72-hour clock for this player. */
    public void recordSeen(UUID uuid) {
        lastSeenMap.put(uuid, Instant.now().getEpochSecond());
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

        return tag;
    }
}
