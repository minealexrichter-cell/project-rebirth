package com.projectrebirth.garrison;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * An order for a machine (1.7.0, block D; the concept's section 7.3), given at the console or with the tactical tablet. The
 * console keeps it and the machine takes it over when it reports; what a machine does with it comes with the machines (androids
 * in blocks E and F, turrets in G, the mech in H).
 *
 * @param type   what to do
 * @param point  the point to guard or to hold
 * @param route  the patrol route (two points or more)
 * @param from   one corner of the work area
 * @param to     the other corner
 * @param chest  where the work is handed in
 * @param escort whom to escort
 * @param work   the work: mine, lumber, farm, or mine_ore (the whole area is mined, only ores are kept) (for WORK)
 */
public record MachineTask(Type type, Optional<BlockPos> point, List<BlockPos> route, Optional<BlockPos> from, Optional<BlockPos> to,
                          Optional<BlockPos> chest, Optional<UUID> escort, String work) {
    public enum Type {
        // 1.7.2: AREA_PATROL at the end (tablets keep an order's type by its place in this list)
        STAND_DOWN, GUARD, PATROL, ESCORT, HOLD, WORK, TO_POST, SHELTER, AREA_PATROL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Type byId(int id) {
            Type[] values = values();
            return id >= 0 && id < values.length ? values[id] : STAND_DOWN;
        }

        public String translationKey() {
            return "machine_task.projectrebirth." + id();
        }
    }

    public static final List<String> WORKS = List.of("mine", "lumber", "farm", "mine_ore");

    public static final MachineTask STAND_DOWN = new MachineTask(Type.STAND_DOWN, Optional.empty(), List.of(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), "");

    public static final Codec<MachineTask> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(id -> {
                for (Type type : Type.values()) {
                    if (type.id().equals(id)) {
                        return type;
                    }
                }
                return Type.STAND_DOWN;
            }, Type::id).fieldOf("type").forGetter(MachineTask::type),
            BlockPos.CODEC.optionalFieldOf("point").forGetter(MachineTask::point),
            BlockPos.CODEC.listOf().optionalFieldOf("route", List.of()).forGetter(MachineTask::route),
            BlockPos.CODEC.optionalFieldOf("from").forGetter(MachineTask::from),
            BlockPos.CODEC.optionalFieldOf("to").forGetter(MachineTask::to),
            BlockPos.CODEC.optionalFieldOf("chest").forGetter(MachineTask::chest),
            UUIDUtil.CODEC.optionalFieldOf("escort").forGetter(MachineTask::escort),
            Codec.STRING.optionalFieldOf("work", "").forGetter(MachineTask::work)).apply(i, MachineTask::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineTask> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static MachineTask simple(Type type) {
        return new MachineTask(type, Optional.empty(), List.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), "");
    }

    public static MachineTask at(Type type, BlockPos point) {
        return new MachineTask(type, Optional.of(point.immutable()), List.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), "");
    }

    public static MachineTask patrol(List<BlockPos> route) {
        return new MachineTask(Type.PATROL, Optional.empty(), List.copyOf(route), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), "");
    }

    /**
     * 1.7.2, block D: patrol the area round a centre without a route; the machine walks the sector {@code sector} of {@code sectors}
     * of the circle (kept in the work field as "sector/sectors"). The radius is the config's.
     */
    public static MachineTask area(BlockPos centre, int sector, int sectors) {
        return new MachineTask(Type.AREA_PATROL, Optional.of(centre.immutable()), List.of(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), sector + "/" + Math.max(1, sectors));
    }

    /** For an area patrol: its sector (0-based) and the number of sectors; {0, 1} when it cannot be read. */
    public int[] sector() {
        String[] parts = work.split("/");
        try {
            int sectors = Math.max(1, Integer.parseInt(parts[1]));
            return new int[] {Math.floorMod(Integer.parseInt(parts[0]), sectors), sectors};
        } catch (RuntimeException e) {
            return new int[] {0, 1};
        }
    }

    public static MachineTask escort(UUID whom) {
        return new MachineTask(Type.ESCORT, Optional.empty(), List.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(whom), "");
    }

    /** 1.8.0, block J: work without an area: the android finds its work and its storage itself. */
    public static MachineTask workOwn(String work) {
        return new MachineTask(Type.WORK, Optional.empty(), List.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), work);
    }

    public static MachineTask work(String work, BlockPos from, BlockPos to, BlockPos chest) {
        return new MachineTask(Type.WORK, Optional.empty(), List.of(), Optional.of(from.immutable()), Optional.of(to.immutable()),
                Optional.of(chest.immutable()), Optional.empty(), work);
    }
}
