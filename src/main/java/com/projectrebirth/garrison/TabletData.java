package com.projectrebirth.garrison;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a tactical tablet holds: the console it is linked to, the order that waits for its points (1.7.1), and the marks of the last
 * orders given with points (a point to guard, a patrol route, the corners of a work area, the chest of the work), drawn in the world
 * while the tablet is in hand.
 */
public record TabletData(Optional<GlobalPos> console, Optional<Pending> pending, Optional<BlockPos> guard, List<BlockPos> route,
                         Optional<BlockPos> areaFrom, Optional<BlockPos> areaTo, Optional<BlockPos> chest) {
    public static final TabletData EMPTY = new TabletData(Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Optional.empty(),
            Optional.empty(), Optional.empty());

    /**
     * 1.7.1: an order chosen on the tablet (or at the console) that needs points in the world: the guard point, the route, or the
     * work area and its chest. The player right-clicks blocks; when the points are there, the order goes out by itself.
     * {@code group} is -1 for one machine, {@link ConsoleBlockEntity#ALL} for every machine, else a group; {@code label} names the
     * target for the player.
     */
    public record Pending(Optional<UUID> machine, int group, int type, int work, String label, List<BlockPos> points, List<UUID> machines) {
        /** 1.8.0 block F: {@code machines} — the machines chosen with the tablet in the world (then machine is empty and group -1). */
        public Pending(Optional<UUID> machine, int group, int type, int work, String label, List<BlockPos> points) {
            this(machine, group, type, work, label, points, List.of());
        }

        public static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.optionalFieldOf("machine").forGetter(Pending::machine),
                Codec.INT.optionalFieldOf("group", -1).forGetter(Pending::group),
                Codec.INT.fieldOf("type").forGetter(Pending::type),
                Codec.INT.optionalFieldOf("work", 0).forGetter(Pending::work),
                Codec.STRING.optionalFieldOf("label", "").forGetter(Pending::label),
                BlockPos.CODEC.listOf().optionalFieldOf("points", List.of()).forGetter(Pending::points),
                UUIDUtil.CODEC.listOf().optionalFieldOf("machines", List.of()).forGetter(Pending::machines)).apply(i, Pending::new));

        public MachineTask.Type taskType() {
            return MachineTask.Type.byId(type);
        }

        public Pending withPoint(BlockPos pos) {
            List<BlockPos> next = new ArrayList<>(points);
            next.add(pos.immutable());
            return new Pending(machine, group, type, work, label, List.copyOf(next), machines);
        }

        public Pending withoutLastPoint() {
            return points.isEmpty() ? this : new Pending(machine, group, type, work, label, List.copyOf(points.subList(0, points.size() - 1)), machines);
        }
    }

    public static final Codec<TabletData> CODEC = RecordCodecBuilder.create(i -> i.group(
            GlobalPos.CODEC.optionalFieldOf("console").forGetter(TabletData::console),
            Pending.CODEC.optionalFieldOf("pending").forGetter(TabletData::pending),
            BlockPos.CODEC.optionalFieldOf("guard").forGetter(TabletData::guard),
            BlockPos.CODEC.listOf().optionalFieldOf("route", List.of()).forGetter(TabletData::route),
            BlockPos.CODEC.optionalFieldOf("area_from").forGetter(TabletData::areaFrom),
            BlockPos.CODEC.optionalFieldOf("area_to").forGetter(TabletData::areaTo),
            BlockPos.CODEC.optionalFieldOf("chest").forGetter(TabletData::chest)).apply(i, TabletData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public TabletData withConsole(GlobalPos where) {
        return new TabletData(Optional.of(where), Optional.empty(), guard, route, areaFrom, areaTo, chest);
    }

    public TabletData withPending(Pending next) {
        return new TabletData(console, Optional.of(next), guard, route, areaFrom, areaTo, chest);
    }

    public TabletData withoutPending() {
        return new TabletData(console, Optional.empty(), guard, route, areaFrom, areaTo, chest);
    }

    public TabletData withGuard(BlockPos pos) {
        return new TabletData(console, pending, Optional.of(pos.immutable()), route, areaFrom, areaTo, chest);
    }

    public TabletData withRoute(List<BlockPos> points) {
        return new TabletData(console, pending, guard, List.copyOf(points), areaFrom, areaTo, chest);
    }

    public TabletData withArea(BlockPos from, BlockPos to, BlockPos chestPos) {
        return new TabletData(console, pending, guard, route, Optional.of(from.immutable()), Optional.of(to.immutable()), Optional.of(chestPos.immutable()));
    }
}
