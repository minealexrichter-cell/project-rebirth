package com.projectrebirth.garrison;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a tactical tablet holds (1.7.0, block D): the console it is linked to, the mark it sets now, and the marks: a point to guard,
 * a patrol route, the two corners of a work area, the chest the work is handed in.
 */
public record TabletData(Optional<GlobalPos> console, int mode, Optional<BlockPos> guard, List<BlockPos> route, Optional<BlockPos> areaFrom,
                         Optional<BlockPos> areaTo, Optional<BlockPos> chest) {
    public static final int GUARD = 0;
    public static final int ROUTE = 1;
    public static final int AREA = 2;
    public static final int CHEST = 3;
    public static final int MODES = 4;

    public static final TabletData EMPTY = new TabletData(Optional.empty(), GUARD, Optional.empty(), List.of(), Optional.empty(),
            Optional.empty(), Optional.empty());

    public static final Codec<TabletData> CODEC = RecordCodecBuilder.create(i -> i.group(
            GlobalPos.CODEC.optionalFieldOf("console").forGetter(TabletData::console),
            Codec.INT.optionalFieldOf("mode", GUARD).forGetter(TabletData::mode),
            BlockPos.CODEC.optionalFieldOf("guard").forGetter(TabletData::guard),
            BlockPos.CODEC.listOf().optionalFieldOf("route", List.of()).forGetter(TabletData::route),
            BlockPos.CODEC.optionalFieldOf("area_from").forGetter(TabletData::areaFrom),
            BlockPos.CODEC.optionalFieldOf("area_to").forGetter(TabletData::areaTo),
            BlockPos.CODEC.optionalFieldOf("chest").forGetter(TabletData::chest)).apply(i, TabletData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public TabletData withConsole(GlobalPos where) {
        return new TabletData(Optional.of(where), mode, guard, route, areaFrom, areaTo, chest);
    }

    public TabletData withMode(int next) {
        return new TabletData(console, Math.floorMod(next, MODES), guard, route, areaFrom, areaTo, chest);
    }

    public TabletData withGuard(BlockPos pos) {
        return new TabletData(console, mode, Optional.of(pos.immutable()), route, areaFrom, areaTo, chest);
    }

    /** One more route point (the oldest goes when the route is full). */
    public TabletData withRoutePoint(BlockPos pos, int max) {
        List<BlockPos> next = new ArrayList<>(route);
        next.add(pos.immutable());
        while (next.size() > max) {
            next.remove(0);
        }
        return new TabletData(console, mode, guard, List.copyOf(next), areaFrom, areaTo, chest);
    }

    public TabletData withoutRoute() {
        return new TabletData(console, mode, guard, List.of(), areaFrom, areaTo, chest);
    }

    /** The first corner, then the second; a third click starts a new area. */
    public TabletData withAreaCorner(BlockPos pos) {
        if (areaFrom.isEmpty() || areaTo.isPresent()) {
            return new TabletData(console, mode, guard, route, Optional.of(pos.immutable()), Optional.empty(), chest);
        }
        return new TabletData(console, mode, guard, route, areaFrom, Optional.of(pos.immutable()), chest);
    }

    public TabletData withChest(BlockPos pos) {
        return new TabletData(console, mode, guard, route, areaFrom, areaTo, Optional.of(pos.immutable()));
    }
}
