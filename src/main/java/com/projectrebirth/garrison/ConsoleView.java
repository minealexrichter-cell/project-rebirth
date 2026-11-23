package com.projectrebirth.garrison;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.monitoring.Snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * What the window of a console shows (1.7.0, block D), sent to the players who have it open: energy, the switches, the machines
 * as last heard, the groups, the alarm log and, when a monitoring station stands near, its contamination map under the machines.
 */
public record ConsoleView(int energy, int capacity, boolean powered, boolean intruderAllowed, boolean intruderMode, boolean weatherLink,
                          boolean sheltering, List<Row> rows, List<Group> groups, List<Line> log, Optional<Snapshot> map, int range,
                          int rules, List<Site> sites, List<Zone> zones, int searchRadius) {
    /**
     * 1.8.0: the facts of a machine for the console's cards and map: energy share (-1: none), rounds (-1: none), how far it shoots,
     * a work android, the points of its order (guard point, route, area corners and chest, area centre) and its alarms going on.
     */
    public record Facts(float energy, int ammo, int range, boolean worker, List<BlockPos> points, List<Integer> alarms) {
        public static final Facts NONE = new Facts(-1.0f, -1, 0, false, List.of(), List.of());
        public static final Codec<Facts> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("energy").forGetter(Facts::energy),
                Codec.INT.fieldOf("ammo").forGetter(Facts::ammo),
                Codec.INT.fieldOf("range").forGetter(Facts::range),
                Codec.BOOL.fieldOf("worker").forGetter(Facts::worker),
                BlockPos.CODEC.listOf().fieldOf("points").forGetter(Facts::points),
                Codec.INT.listOf().fieldOf("alarms").forGetter(Facts::alarms)).apply(i, Facts::new));
    }

    /** 1.8.0, block J: a zone of the console where work androids touch nothing (block columns, both corners included). */
    public record Zone(int x1, int z1, int x2, int z2) {
        public static final Codec<Zone> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x1").forGetter(Zone::x1),
                Codec.INT.fieldOf("z1").forGetter(Zone::z1),
                Codec.INT.fieldOf("x2").forGetter(Zone::x2),
                Codec.INT.fieldOf("z2").forGetter(Zone::z2)).apply(i, Zone::new));

        public boolean contains(int x, int z) {
            return x >= Math.min(x1, x2) && x <= Math.max(x1, x2) && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
        }
    }

    /**
     * 1.8.0: a place on the map near the console: a service post (kind 0), a charging pad (1); block J: a storage (2), the mine of a
     * work android (3), the forest (4) and the fields (5) it found; 1.9.0: a node of the machine mind of stage I to V (6 to 10).
     */
    public record Site(int kind, int x, int y, int z) {
        public static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("kind").forGetter(Site::kind),
                Codec.INT.fieldOf("x").forGetter(Site::x),
                Codec.INT.fieldOf("y").forGetter(Site::y),
                Codec.INT.fieldOf("z").forGetter(Site::z)).apply(i, Site::new));
    }

    /** The points of an order the map shows: the guard point or area centre, the route, the work area's corners and chest. */
    public static List<BlockPos> taskPoints(MachineTask task) {
        return switch (task.type()) {
            case GUARD, AREA_PATROL -> task.point().map(List::of).orElse(List.of());
            case PATROL -> task.route();
            case WORK -> task.from().isPresent() && task.to().isPresent() && task.chest().isPresent()
                    ? List.of(task.from().get(), task.to().get(), task.chest().get()) : List.of();
            default -> List.of();
        };
    }

    /** One machine: id, name, kind, group (-1: none), state, both bars, where (block x, z, and whether it is in this dimension), its task, seconds since it was heard. */
    public record Row(UUID id, String name, String kind, int group, int state, float structural, float maxStructural, float critical,
                      float maxCritical, int x, int y, int z, int task, int heardSeconds, Facts facts) {
        public static final Codec<Row> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Row::id),
                Codec.STRING.fieldOf("name").forGetter(Row::name),
                Codec.STRING.fieldOf("kind").forGetter(Row::kind),
                Codec.INT.fieldOf("group").forGetter(Row::group),
                Codec.INT.fieldOf("state").forGetter(Row::state),
                Codec.FLOAT.fieldOf("s").forGetter(Row::structural),
                Codec.FLOAT.fieldOf("ms").forGetter(Row::maxStructural),
                Codec.FLOAT.fieldOf("c").forGetter(Row::critical),
                Codec.FLOAT.fieldOf("mc").forGetter(Row::maxCritical),
                Codec.INT.fieldOf("x").forGetter(Row::x),
                Codec.INT.fieldOf("y").forGetter(Row::y),
                Codec.INT.fieldOf("z").forGetter(Row::z),
                Codec.INT.fieldOf("task").forGetter(Row::task),
                Codec.INT.fieldOf("heard").forGetter(Row::heardSeconds),
                Facts.CODEC.optionalFieldOf("facts", Facts.NONE).forGetter(Row::facts)).apply(i, Row::new));
    }

    /** One group: how many machines, whether its intruder mode is on, its target rules (1.8.0). */
    public record Group(int members, boolean intruders, int rules) {
        public static final Codec<Group> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("members").forGetter(Group::members),
                Codec.BOOL.fieldOf("intruders").forGetter(Group::intruders),
                Codec.INT.optionalFieldOf("rules", OrderRules.DEFAULT_RULES).forGetter(Group::rules)).apply(i, Group::new));
    }

    /** One line of the alarm log: the alarm, whether it ended, the machine, where, seconds ago. */
    public record Line(int alarm, boolean ended, String name, int x, int y, int z, int secondsAgo) {
        public static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("alarm").forGetter(Line::alarm),
                Codec.BOOL.fieldOf("ended").forGetter(Line::ended),
                Codec.STRING.fieldOf("name").forGetter(Line::name),
                Codec.INT.fieldOf("x").forGetter(Line::x),
                Codec.INT.fieldOf("y").forGetter(Line::y),
                Codec.INT.fieldOf("z").forGetter(Line::z),
                Codec.INT.fieldOf("ago").forGetter(Line::secondsAgo)).apply(i, Line::new));
    }

    public static final Codec<ConsoleView> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("energy").forGetter(ConsoleView::energy),
            Codec.INT.fieldOf("capacity").forGetter(ConsoleView::capacity),
            Codec.BOOL.fieldOf("powered").forGetter(ConsoleView::powered),
            Codec.BOOL.fieldOf("intruder_allowed").forGetter(ConsoleView::intruderAllowed),
            Codec.BOOL.fieldOf("intruder_mode").forGetter(ConsoleView::intruderMode),
            Codec.BOOL.fieldOf("weather_link").forGetter(ConsoleView::weatherLink),
            Codec.BOOL.fieldOf("sheltering").forGetter(ConsoleView::sheltering),
            Row.CODEC.listOf().fieldOf("rows").forGetter(ConsoleView::rows),
            Group.CODEC.listOf().fieldOf("groups").forGetter(ConsoleView::groups),
            Line.CODEC.listOf().fieldOf("log").forGetter(ConsoleView::log),
            Snapshot.CODEC.optionalFieldOf("map").forGetter(ConsoleView::map),
            Codec.INT.fieldOf("range").forGetter(ConsoleView::range),
            Codec.INT.optionalFieldOf("rules", OrderRules.DEFAULT_RULES).forGetter(ConsoleView::rules),
            Site.CODEC.listOf().optionalFieldOf("sites", List.of()).forGetter(ConsoleView::sites),
            Zone.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(ConsoleView::zones),
            Codec.INT.optionalFieldOf("search_radius", 0).forGetter(ConsoleView::searchRadius)).apply(i, ConsoleView::new));
}
