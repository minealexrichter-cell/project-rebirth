package com.projectrebirth.garrison;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.monitoring.Snapshot;

import net.minecraft.core.UUIDUtil;

/**
 * What the window of a console shows (1.7.0, block D), sent to the players who have it open: energy, the switches, the machines
 * as last heard, the groups, the alarm log and, when a monitoring station stands near, its contamination map under the machines.
 */
public record ConsoleView(int energy, int capacity, boolean powered, boolean intruderAllowed, boolean intruderMode, boolean weatherLink,
                          boolean sheltering, List<Row> rows, List<Group> groups, List<Line> log, Optional<Snapshot> map, int range) {
    /** One machine: id, name, kind, group (-1: none), state, both bars, where (block x, z, and whether it is in this dimension), its task, seconds since it was heard. */
    public record Row(UUID id, String name, String kind, int group, int state, float structural, float maxStructural, float critical,
                      float maxCritical, int x, int y, int z, int task, int heardSeconds) {
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
                Codec.INT.fieldOf("heard").forGetter(Row::heardSeconds)).apply(i, Row::new));
    }

    /** One group: how many machines, whether its intruder mode is on. */
    public record Group(int members, boolean intruders) {
        public static final Codec<Group> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("members").forGetter(Group::members),
                Codec.BOOL.fieldOf("intruders").forGetter(Group::intruders)).apply(i, Group::new));
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
            Codec.INT.fieldOf("range").forGetter(ConsoleView::range)).apply(i, ConsoleView::new));
}
