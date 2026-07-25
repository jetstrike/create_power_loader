package com.hlysine.create_power_loader.content;

import com.hlysine.create_power_loader.config.CPLConfigs;
import com.hlysine.create_power_loader.content.ownership.OwnershipHelper;
import com.hlysine.create_power_loader.content.ownership.PlayerActivityTracker;
import com.hlysine.create_power_loader.content.trains.CPLGlobalStation;
import com.hlysine.create_power_loader.content.trains.StationChunkLoader;
import com.hlysine.create_power_loader.network.S2COwnerScreenPacket;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.UUID;

import static com.hlysine.create_power_loader.content.AbstractChunkLoaderBlock.ATTACHED;
import static com.hlysine.create_power_loader.content.ChunkLoadManager.LoadedChunkPos;
import static com.hlysine.create_power_loader.content.ChunkLoadManager.unforceAllChunks;
import static com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING;

public abstract class AbstractChunkLoaderBlockEntity extends KineticBlockEntity implements ChunkLoader {

    public final LoaderType type;
    protected BlockPos lastBlockPos;
    protected boolean lastEnabled;
    protected int lastRange;
    protected boolean lastTickLoading;
    protected int chunkUpdateCooldown;
    protected int chunkUnloadCooldown;
    protected Set<LoadedChunkPos> forcedChunks = new HashSet<>();
    @Nullable
    private StationBlockEntity attachedStation = null;
    public boolean isLoaderActive = false;
    private boolean deferredEdgePoint = false;

    public boolean tickLoadingEnabled = false;

    // --- Ownership ---
    @Nullable
    private UUID ownerUUID = null;
    private final List<UUID> coOwners = new ArrayList<>();
    private static final int MAX_CO_OWNERS = 7;

    /** True when the loader is suppressed because no authorized player is active. */
    private boolean suppressedByInactivity = false;
    /** Whether suppression was notified to OPs this cycle (avoid repeat spam). */
    private boolean suppressionNotified = false;
    /** Countdown for periodic ownership transfer check (every ~6000 ticks / 5 min). */
    private int ownershipTransferCountdown = 6000;

    public AbstractChunkLoaderBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state, LoaderType type) {
        super(typeIn, pos, state);
        this.type = type;
    }

    // =========================================================================
    // Ownership API
    // =========================================================================

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public List<UUID> getCoOwners() {
        return Collections.unmodifiableList(coOwners);
    }

    /** Returns true if the UUID is the owner OR a co-owner. */
    public boolean isAuthorizedUser(UUID uuid) {
        return uuid.equals(ownerUUID) || coOwners.contains(uuid);
    }

    /**
     * Called when a player right-clicks an unclaimed loader.
     * Records them as owner and timestamps their activity.
     */
    public void claimLoader(Player player) {
        this.ownerUUID = player.getUUID();
        this.suppressedByInactivity = false;
        this.suppressionNotified = false;
        // Record the claimer as seen so the 72h timer starts from now
        MinecraftServer server = level.getServer();
        if (server != null) {
            PlayerActivityTracker.getOrCreate(server).recordSeen(ownerUUID);
        }
        setChanged();
        notifyUpdate();
        if (player instanceof ServerPlayer sp) {
            openOwnerScreenFor(sp);
        }
    }

    /**
     * Adds a co-owner.  Returns false if the list is already at max capacity
     * or the UUID is already present.
     */
    public boolean addCoOwner(UUID uuid) {
        if (coOwners.size() >= MAX_CO_OWNERS) return false;
        if (coOwners.contains(uuid)) return false;
        coOwners.add(uuid);
        setChanged();
        notifyUpdate();
        return true;
    }

    /**
     * Removes a co-owner.  Returns false if the UUID is not in the list.
     */
    public boolean removeCoOwner(UUID uuid) {
        boolean removed = coOwners.remove(uuid);
        if (removed) {
            setChanged();
            notifyUpdate();
        }
        return removed;
    }

    /**
     * Builds and sends the S2COwnerScreenPacket to a specific player.
     * Safe to call from server-side packet handlers.
     */
    public void openOwnerScreenFor(ServerPlayer player) {
        MinecraftServer server = level.getServer();
        if (server == null) return;

        PlayerActivityTracker tracker = PlayerActivityTracker.getOrCreate(server);

        String ownerName = ownerUUID != null ? OwnershipHelper.getDisplayName(ownerUUID, server) : "";
        long ownerLastSeen = ownerUUID != null ? tracker.getLastSeenEpoch(ownerUUID) : 0L;

        List<S2COwnerScreenPacket.OwnerEntry> entries = new ArrayList<>();
        for (UUID co : coOwners) {
            entries.add(new S2COwnerScreenPacket.OwnerEntry(
                    co,
                    OwnershipHelper.getDisplayName(co, server),
                    tracker.getLastSeenEpoch(co)
            ));
        }

        boolean viewerIsOwner = player.getUUID().equals(ownerUUID)
                || player.hasPermissions(2);

        PacketDistributor.sendToPlayer(player, new S2COwnerScreenPacket(
                getBlockPos(),
                ownerUUID,
                ownerName,
                ownerLastSeen,
                entries,
                tickLoadingEnabled,
                viewerIsOwner,
                suppressedByInactivity
        ));
    }

    @Override
    public @NotNull Set<LoadedChunkPos> getForcedChunks() {
        return forcedChunks;
    }

    @Override
    public LoaderMode getLoaderMode() {
        return LoaderMode.STATIC;
    }

    @Override
    public LoaderType getLoaderType() {
        return type;
    }

    @Override
    public @Nullable Pair<ResourceLocation, BlockPos> getLocation() {
        return Pair.of(getLevel().dimension().location(), getBlockPos());
    }

    public void updateAttachedStation(StationBlockEntity be) {
        if (attachedStation != null) {
            if (attachedStation.getStation() instanceof CPLGlobalStation station) {
                station.getLoader().removeAttachment(getBlockPos());
            }
        } else {
            removeFromManager();
        }
        attachedStation = be;
        if (attachedStation != null) {
            if (attachedStation.getStation() instanceof CPLGlobalStation station) {
                station.getLoader().addAttachment(type, getBlockPos());
            } else {
                deferredEdgePoint = true; // The GlobalStation is only created in the next tick after the station block is placed
            }
        } else {
            if (!level.isClientSide())
                addToManager();
        }
    }

    public StationBlockEntity getAttachedStation() {
        return attachedStation;
    }

    @Override
    public void initialize() {
        super.initialize();
        if (getLevel() != null && getBlockState().getValue(ATTACHED)) {
            BlockEntity be = getLevel().getBlockEntity(getBlockPos().relative(getBlockState().getValue(FACING).getOpposite()));
            if (!(be instanceof StationBlockEntity sbe)) return;
            updateAttachedStation(sbe);
        } else {
            if (!level.isClientSide())
                addToManager();
        }
    }

    public void reclaimChunks(Set<LoadedChunkPos> forcedChunks) {
        this.forcedChunks.addAll(forcedChunks);
    }

    public void toggleTickLoading() {
        tickLoadingEnabled = !tickLoadingEnabled;
        if (!level.isClientSide()) {
            updateForcedChunks();
            setChanged();
            notifyUpdate();
        }
    }

    public boolean isTickLoadingEnabled() {
        return tickLoadingEnabled && CPLConfigs.server().getFor(type).enableRandomTicks.get();
    }

    @Override
    public void tick() {
        super.tick();

        boolean server = (!level.isClientSide || isVirtual()) && (level instanceof ServerLevel);

        if (!server) {
            spawnParticles();
        }

        if (server && chunkUpdateCooldown-- <= 0) {
            chunkUpdateCooldown = CPLConfigs.server().getFor(type).chunkUpdateInterval.get();
            if (needsUpdate()) {
                // Detect suppression state transitions and notify OPs
                if (level.getServer() != null && ownerUUID != null) {
                    boolean inactive = !OwnershipHelper.hasActiveAuthorizedPlayer(ownerUUID, coOwners, level.getServer());
                    boolean forced = OwnershipHelper.isForceUnloaded(ownerUUID, level.getServer());
                    boolean nowSuppressed = inactive || forced;
                    if (nowSuppressed && !suppressedByInactivity) {
                        suppressedByInactivity = true;
                        if (!suppressionNotified && inactive && !forced) {
                            suppressionNotified = true;
                            OwnershipHelper.notifyOpsOfSuppression(ownerUUID, 1, level.getServer());
                        }
                        notifyUpdate();
                    } else if (!nowSuppressed && suppressedByInactivity) {
                        suppressedByInactivity = false;
                        suppressionNotified = false;
                        notifyUpdate();
                    }
                }
                setChanged();
                updateForcedChunks();
            }
        }

        if (server) {
            // Periodic ownership transfer check
            if (--ownershipTransferCountdown <= 0) {
                ownershipTransferCountdown = 6000;
                checkOwnershipTransfer();
            }
        }

        if (server) {
            if (deferredEdgePoint) {
                if (attachedStation.getStation() instanceof CPLGlobalStation station) {
                    station.getLoader().addAttachment(type, getBlockPos());
                    deferredEdgePoint = false;
                }
            }
            boolean wasLoaderActive = isLoaderActive;
            isLoaderActive = StationChunkLoader.isEnabledForStation(type) &&
                    attachedStation != null &&
                    attachedStation.getStation() != null &&
                    attachedStation.getStation().getPresentTrain() != null;
            if (wasLoaderActive != isLoaderActive) {
                notifyUpdate();
            }
        }
    }

    private boolean needsUpdate() {
        if (lastBlockPos == null) return true;
        return !lastBlockPos.equals(getBlockPos()) || lastEnabled != canLoadChunks() || lastRange != getLoadingRange() || lastTickLoading != isTickLoadingEnabled() || chunkUnloadCooldown > 0;
    }

    protected void updateForcedChunks() {
        boolean resetStates = true;
        if (canLoadChunks()) {
            ChunkLoadManager.updateForcedChunks(level.getServer(), new LoadedChunkPos(getLevel(), getBlockPos()), getBlockPos(), getLoadingRange(), forcedChunks, isTickLoadingEnabled());
        } else if (chunkUnloadCooldown >= CPLConfigs.server().getFor(type).unloadGracePeriod.get()) {
            unforceAllChunks(level.getServer(), getBlockPos(), forcedChunks);
        } else {
            chunkUnloadCooldown += CPLConfigs.server().getFor(type).chunkUpdateInterval.get();
            resetStates = false;
        }
        if (resetStates) {
            chunkUnloadCooldown = 0;
            lastBlockPos = getBlockPos().immutable();
            lastEnabled = canLoadChunks();
            lastRange = getLoadingRange();
            lastTickLoading = isTickLoadingEnabled();
        }
    }

    public boolean canLoadChunks() {
        if (!isSpeedRequirementFulfilled()) return false;
        if (!CPLConfigs.server().getFor(type).enableStatic.get()) return false;
        // Unclaimed loaders never load chunks
        if (ownerUUID == null) return false;
        // Inactivity check
        if (level != null && level.isClientSide()) {
            return !suppressedByInactivity;
        }
        MinecraftServer server = level != null ? level.getServer() : null;
        if (server != null) {
            if (OwnershipHelper.isForceUnloaded(ownerUUID, server)) return false;
            return OwnershipHelper.hasActiveAuthorizedPlayer(ownerUUID, coOwners, server);
        }
        return false;
    }

    @Override
    public boolean isSpeedRequirementFulfilled() {
        if (!super.isSpeedRequirementFulfilled())
            return false;

        BlockState state = getBlockState();
        if (!(getBlockState().getBlock() instanceof IRotate))
            return true;
        IRotate def = (IRotate) state.getBlock();
        IRotate.SpeedLevel minimumRequiredSpeedLevel = def.getMinimumRequiredSpeedLevel();
        float minSpeed = minimumRequiredSpeedLevel.getSpeedValue();

        double requirement = minSpeed * (float) Math.pow(2, getLoadingRange()) * CPLConfigs.server().getFor(type).speedMultiplier.get();
        return Math.abs(getSpeed()) >= requirement;
    }

    @Override
    public void destroy() {
        super.destroy();
        boolean server = (!level.isClientSide || isVirtual()) && (level instanceof ServerLevel);
        if (server)
            unforceAllChunks(level.getServer(), getBlockPos(), forcedChunks);
        updateAttachedStation(null);
        removeFromManager();
    }

    @Override
    public void remove() {
        super.remove();
        boolean server = (!level.isClientSide || isVirtual()) && (level instanceof ServerLevel);
        if (server)
            unforceAllChunks(level.getServer(), getBlockPos(), forcedChunks);
        updateAttachedStation(null);
        removeFromManager();
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        isLoaderActive = compound.getBoolean("CoreActive");
        tickLoadingEnabled = compound.getBoolean("TickLoading");
        suppressedByInactivity = compound.getBoolean("Suppressed");

        // Owner UUID
        if (compound.contains("OwnerUUID", Tag.TAG_STRING)) {
            try {
                ownerUUID = UUID.fromString(compound.getString("OwnerUUID"));
            } catch (IllegalArgumentException e) {
                ownerUUID = null;
            }
        } else {
            ownerUUID = null;
        }

        // Co-owners
        coOwners.clear();
        ListTag coOwnerList = compound.getList("CoOwners", Tag.TAG_STRING);
        for (int i = 0; i < coOwnerList.size(); i++) {
            try {
                coOwners.add(UUID.fromString(coOwnerList.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putBoolean("CoreActive", isLoaderActive);
        compound.putBoolean("TickLoading", tickLoadingEnabled);
        compound.putBoolean("Suppressed", suppressedByInactivity);

        if (ownerUUID != null) {
            compound.putString("OwnerUUID", ownerUUID.toString());
        }

        ListTag coOwnerList = new ListTag();
        for (UUID co : coOwners) {
            coOwnerList.add(StringTag.valueOf(co.toString()));
        }
        compound.put("CoOwners", coOwnerList);

        super.write(compound, registries, clientPacket);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Checks whether the owner has been absent long enough to trigger a co-owner
     * promotion.  Called every ~6000 ticks from the BE tick loop.
     */
    private void checkOwnershipTransfer() {
        MinecraftServer server = level != null ? level.getServer() : null;
        if (server == null || ownerUUID == null || coOwners.isEmpty()) return;

        UUID candidate = OwnershipHelper.findTransferCandidate(ownerUUID, coOwners, server);
        if (candidate == null) return;

        UUID previousOwner = ownerUUID;
        ownerUUID = candidate;
        coOwners.remove(candidate);
        suppressionNotified = false; // reset so new owner gets a clean slate

        setChanged();
        notifyUpdate();
        OwnershipHelper.notifyOpsOfTransfer(previousOwner, ownerUUID, server);
    }

    public abstract int getLoadingRange();

    protected void spawnParticles() {
        if (level == null)
            return;
        if (!canLoadChunks())
            return;

        RandomSource r = level.getRandom();

        Vec3 c = VecHelper.getCenterOf(worldPosition);

        if (r.nextInt(4) != 0)
            return;

        double speed = .0625f;
        Vec3 normal = Vec3.atLowerCornerOf(getBlockState().getValue(BlockStateProperties.FACING).getNormal());
        Vec3 v2 = c.add(VecHelper.offsetRandomly(Vec3.ZERO, r, .5f)
                        .multiply(1, 1, 1)
                        .normalize()
                        .scale((.25f) + r.nextDouble() * .125f))
                .add(normal.scale(0.5f));

        Vec3 motion = normal.scale(speed);
        level.addParticle(ParticleTypes.PORTAL, v2.x, v2.y, v2.z, motion.x, motion.y, motion.z);
    }

    public static int forceUpdateLoadersFor(UUID owner, MinecraftServer server) {
        int count = 0;
        for (WeakCollection<ChunkLoader> collection : ChunkLoadManager.allLoaders.values()) {
            for (ChunkLoader loader : collection) {
                if (loader instanceof AbstractChunkLoaderBlockEntity be) {
                    if (owner.equals(be.getOwnerUUID())) {
                        if (be.getLevel() == null || be.getLevel().isClientSide()) continue;
                        be.chunkUpdateCooldown = 0;
                        if (!be.canLoadChunks()) {
                            be.chunkUnloadCooldown = CPLConfigs.server().getFor(be.type).unloadGracePeriod.get();
                        }
                        be.updateForcedChunks();
                        be.setChanged();
                        be.notifyUpdate();
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
