package com.projectrebirth.technology.energy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * A group of connected cables and the machine sides they touch. Built on first use and thrown away whenever a
 * cable or a block next to one changes, so nothing has to be saved and cables never tick.
 */
final class CableNetwork {
    /** A network bigger than this is cut off; it keeps a broken build from freezing the server. */
    private static final int MAX_CABLES = 4096;
    private static final Map<Level, Map<BlockPos, CableNetwork>> CACHE = new WeakHashMap<>();
    /** Guards against another mod's cable sending the energy straight back into this network. */
    private static boolean delivering;

    private record Endpoint(BlockPos pos, Direction side) {
    }

    private final List<Endpoint> endpoints;

    private CableNetwork(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    static void invalidate(Level level) {
        if (!level.isClientSide) {
            CACHE.remove(level);
        }
    }

    static CableNetwork of(Level level, BlockPos cable) {
        Map<BlockPos, CableNetwork> networks = CACHE.computeIfAbsent(level, key -> new HashMap<>());
        CableNetwork network = networks.get(cable);
        if (network == null) {
            Set<BlockPos> cables = new HashSet<>();
            network = build(level, cable, cables);
            for (BlockPos member : cables) {
                networks.put(member, network);
            }
        }
        return network;
    }

    private static CableNetwork build(Level level, BlockPos start, Set<BlockPos> cables) {
        List<Endpoint> endpoints = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        cables.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (level.getBlockState(next).getBlock() instanceof CableBlock) {
                    if (cables.size() < MAX_CABLES && cables.add(next)) {
                        queue.add(next);
                    }
                } else if (level.getCapability(Capabilities.EnergyStorage.BLOCK, next, direction.getOpposite()) != null) {
                    endpoints.add(new Endpoint(next.immutable(), direction.getOpposite()));
                }
            }
        }
        return new CableNetwork(endpoints);
    }

    /** The life support blocks of this network, for a backup battery on it. */
    List<IEnergyStorage> lifeSupport(Level level) {
        List<IEnergyStorage> found = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            if (level.getBlockEntity(endpoint.pos()) instanceof LifeSupport support) {
                found.add(support.lifeSupportEnergy());
            }
        }
        return found;
    }

    /**
     * Shares the energy between the machines of the network that take energy, except the block it came from. Life
     * support blocks come first; the others get energy only once every life support block is charged
     * ([energy] lifeSupportChargedShare). Within each group: an equal part each first, then what is left in turn.
     * Returns what was taken.
     */
    int deliver(Level level, @Nullable BlockPos source, int amount, boolean simulate) {
        if (delivering) {
            return 0;
        }
        delivering = true;
        try {
            List<IEnergyStorage> support = new ArrayList<>();
            List<IEnergyStorage> others = new ArrayList<>();
            for (Endpoint endpoint : endpoints) {
                if (endpoint.pos().equals(source)) {
                    continue;
                }
                IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, endpoint.pos(), endpoint.side());
                if (target != null && target.canReceive()) {
                    (level.getBlockEntity(endpoint.pos()) instanceof LifeSupport ? support : others).add(target);
                }
            }
            int left = Math.min(amount, RebirthConfig.CABLE_TRANSFER.get());
            int delivered = share(support, left, simulate);
            left -= delivered;
            double charged = RebirthConfig.LIFE_SUPPORT_CHARGED_SHARE.get();
            boolean othersMayHave = support.stream().allMatch(target -> LifeSupport.charged(target, charged));
            if (simulate && !support.isEmpty() && !othersMayHave) {
                // a simulation does not fill the life support, so it may only count on what they take
                return delivered;
            }
            if (othersMayHave && left > 0) {
                delivered += share(others, left, simulate);
            }
            return delivered;
        } finally {
            delivering = false;
        }
    }

    private static int share(List<IEnergyStorage> targets, int amount, boolean simulate) {
        int left = amount;
        int delivered = 0;
        if (simulate) {
            for (IEnergyStorage target : targets) {
                delivered += target.receiveEnergy(left - delivered, true);
            }
            return Math.min(delivered, left);
        }
        for (int round = 0; round < 2 && left > 0 && !targets.isEmpty(); round++) {
            int share = round == 0 ? Math.max(1, left / targets.size()) : left;
            for (IEnergyStorage target : targets) {
                if (left <= 0) {
                    break;
                }
                int accepted = target.receiveEnergy(Math.min(share, left), false);
                left -= accepted;
                delivered += accepted;
            }
        }
        return delivered;
    }

    /** The network a cable belongs to; for blocks of this package. */
    static CableNetwork at(Level level, BlockPos cable) {
        return of(level, cable);
    }
}
