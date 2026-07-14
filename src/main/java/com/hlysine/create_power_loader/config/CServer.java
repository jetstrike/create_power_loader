package com.hlysine.create_power_loader.config;

import com.hlysine.create_power_loader.content.AbstractChunkLoaderBlock;
import com.hlysine.create_power_loader.content.LoaderType;
import net.createmod.catnip.config.ConfigBase;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleSupplier;

public class CServer extends ConfigBase {
    public final CLoader andesite = nested(0, () -> new CLoader(LoaderType.ANDESITE), Comments.andesite);

    public final CLoader brass = nested(0, () -> new CLoader(LoaderType.BRASS), Comments.brass);

    // --- Ownership / Inactivity System ---
    public final ConfigInt inactiveThresholdHours = i(72, 1, 8760, "inactiveThresholdHours",
            Comments.inactiveThresholdHours);

    public final ConfigInt ownershipTransferDays = i(7, 1, 365, "ownershipTransferDays",
            Comments.ownershipTransferDays);

    public final ConfigBool opBypassOwnerCheck = b(true, "opBypassOwnerCheck",
            Comments.opBypassOwnerCheck);

    public final ConfigBool notifyOpsOnSuppression = b(true, "notifyOpsOnSuppression",
            Comments.notifyOpsOnSuppression);

    public CLoader getFor(LoaderType type) {
        return switch (type) {
            case ANDESITE -> andesite;
            case BRASS -> brass;
        };
    }

    @Nullable
    public DoubleSupplier getImpact(Block block) {
        if (!(block instanceof AbstractChunkLoaderBlock loader)) return null;
        return getFor(loader.loaderType).stressImpact::get;
    }

    @Override
    public String getName() {
        return "server";
    }

    private static class Comments {
        static String andesite = "Configure the Andesite Chunk Loader";
        static String brass = "Configure the Brass Chunk Loader";
        static String inactiveThresholdHours = "Hours since a player's last login before their chunk loaders are suppressed. Default: 72 (3 days)";
        static String ownershipTransferDays = "Days since the owner's last login before ownership transfers to the most-recently-active co-owner. Default: 7";
        static String opBypassOwnerCheck = "If true, players with OP permission (level >= 2) or on the bypass list always bypass inactivity suppression";
        static String notifyOpsOnSuppression = "If true, all online OPs are notified in chat when a player's loaders are suppressed due to inactivity";
    }
}
