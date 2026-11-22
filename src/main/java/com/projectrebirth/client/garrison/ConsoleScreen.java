package com.projectrebirth.client.garrison;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiButton;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiDropdown;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiMap;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSounds;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiTabs;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.client.ui.UiToggle;
import com.projectrebirth.client.ui.UiTooltip;
import com.projectrebirth.config.RebirthClientConfig;
import com.projectrebirth.garrison.ConsoleAlarm;
import com.projectrebirth.garrison.ConsoleBlockEntity;
import com.projectrebirth.garrison.ConsoleMenu;
import com.projectrebirth.garrison.ConsoleNetwork;
import com.projectrebirth.garrison.ConsoleView;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.MachineCore;
import com.projectrebirth.garrison.MachineKind;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.garrison.OrderRules;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The console's window in the style "Industrial terminal" (1.8.0, block E): the garrison around a map, as in strategy games.
 * <ul>
 *   <li>groups as tabs on top (a colour and a number, keys 1-8, 0 for all);</li>
 *   <li>left: the machines as cards (kind, name, group, both bars, energy, rounds, state or task), a filter; a click chooses, Shift
 *   adds, a double click opens the machine's window;</li>
 *   <li>centre: the map with the faction's land, a monitoring station's contamination, posts, turrets with their circles, the
 *   machines with their routes and areas; a left frame chooses several, the right button gives an order there (on a post: "to the
 *   post"), Shift+right marks route points, dragging the right button marks a work area; the wheel zooms, the middle button
 *   moves;</li>
 *   <li>right: whom to shoot (the target rules of the group, in words) and the alarms with what to do and "show on the map";</li>
 *   <li>bottom: the orders the chosen machines can carry out (the others grey, with the reason).</li>
 * </ul>
 * A short lesson covers the window the first time; "?" shows it again. Every order still goes to the server, which checks it.
 */
public class ConsoleScreen extends UiMachineScreen<ConsoleMenu> {
    private static final int W = 455;
    private static final int H = 256;
    private static final int LEFT_X = 7;
    private static final int LEFT_W = 116;
    private static final int CARDS_Y = 55;
    private static final int CARD_H = 25;
    private static final int CARDS = 6;
    private static final int MAP_X = 127;
    private static final int MAP_Y = 40;
    private static final int MAP_W = 172;
    private static final int MAP_H = 152;
    private static final int RIGHT_X = 303;
    private static final int RIGHT_W = W - 7 - RIGHT_X;
    private static final int ALARMS_Y = 129;
    private static final int ALARM_H = 29;
    private static final int ORDERS_Y = 220;
    /** The orders of the panel, in the mock-up's order; WORK takes the kind of work chosen below. */
    private static final List<MachineTask.Type> ORDERS = List.of(MachineTask.Type.GUARD, MachineTask.Type.PATROL, MachineTask.Type.AREA_PATROL,
            MachineTask.Type.ESCORT, MachineTask.Type.HOLD, MachineTask.Type.WORK, MachineTask.Type.TO_POST, MachineTask.Type.SHELTER,
            MachineTask.Type.STAND_DOWN);
    /** The colours of the groups 1-8 (always shown with their number). */
    static final int[] GROUP_COLORS = {0xFFB648, 0x4FC3FF, 0x7BD88F, 0xFF8A7A, 0xC792EA, 0x4FD1C5, 0xF78C6C, 0xD8D0FF};
    private static final int FILTER_ALL = 0, FILTER_FIGHT = 1, FILTER_WORK = 2, FILTER_TROUBLE = 3;

    /** Kept while the game runs: the group tab (-1: all), the filter, the kind of work, the map's zoom. */
    private static int groupTab = -1;
    private static int filter = FILTER_ALL;
    private static int workKind;
    private static int mapCell = -1;

    /** The kind of work last chosen (the tablet's round menu uses it too). */
    static int workKind() {
        return workKind;
    }

    private final Set<UUID> selected = new LinkedHashSet<>();
    private final ConsoleMapSource source;
    private UiMap map;
    @Nullable
    private UiTabs tabs;
    private final List<UiButton> orderButtons = new ArrayList<>();
    private int cardScroll;
    private int alarmScroll;
    /** The order waiting for its place on the map (armed by its button), or null. */
    @Nullable
    private MachineTask.Type armed;
    /** Route points marked with Shift+right click (blocks; only x and z count). */
    private final List<BlockPos> route = new ArrayList<>();
    /** The two corners of a work area dragged with the right button, or null. */
    @Nullable
    private BlockPos areaFrom;
    @Nullable
    private BlockPos areaTo;
    /** A press of the left or right button on the map: where it began (screen), for frames and areas. */
    private double pressX = -1;
    private double pressY = -1;
    private int pressButton = -1;
    private boolean pressShift;
    private double mouseNowX;
    private double mouseNowY;
    private long lastCardClick;
    @Nullable
    private UUID lastCardClicked;
    /** The lesson's step (0-4), or -1 when it is not shown. */
    private int lesson = -1;
    @Nullable
    private Component status;
    private boolean statusBad;

    public ConsoleScreen(ConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "console");
        source = new ConsoleMapSource(menu.pos());
        if (RebirthClientConfig.CLIENT_SPEC.isLoaded() && RebirthClientConfig.UI_CONSOLE_TUTORIAL.get()) {
            lesson = 0;
        }
    }

    static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.console." + key, args);
    }

    // ------------------------------------------------------------------ building

    /** Zooms and centres the map on the console and its machines (the first time the window is opened). */
    private void fitMap() {
        ConsoleView view = menu.view();
        double minX = menu.pos().getX(), maxX = minX, minZ = menu.pos().getZ(), maxZ = minZ;
        if (view != null) {
            for (ConsoleView.Row row : view.rows()) {
                minX = Math.min(minX, row.x());
                maxX = Math.max(maxX, row.x());
                minZ = Math.min(minZ, row.z());
                maxZ = Math.max(maxZ, row.z());
            }
        }
        double span = Math.max(Math.max(maxX - minX, maxZ - minZ) + 24, 32);
        map.setCell((int) (Math.min(MAP_W, MAP_H) * 16 / span));
        map.centreOn((minX + maxX) / 2 + 0.5, (minZ + maxZ) / 2 + 0.5);
        if (view != null) {
            mapCell = map.cell();
        }
    }

    /** A fresh view came: the cards, map and tabs follow it. */
    public void viewChanged() {
        if (mapCell < 0 && map != null && menu.view() != null) {
            fitMap();
        }
        if (source.set(menu.view()) && map != null) {
            map.invalidate();
        }
        selected.removeIf(id -> row(id) == null);
        addTabs();
    }

    @Override
    protected void init() {
        super.init();
        orderButtons.clear();
        if (map != null) {
            mapCell = map.cell();
            map.close();
        }
        source.set(menu.view());
        map = addRenderableWidget(new UiMap(leftPos + MAP_X, topPos + MAP_Y, MAP_W, MAP_H, source,
                () -> ConsoleMapSource.TERRITORY | ConsoleMapSource.CONTAMINATION, this::marks)).leftDragPans(false).overlay(this::overlay);
        map.maxCell(48);
        if (mapCell > 0) {
            map.setCell(mapCell);
            map.centreOn(menu.pos().getX() + 0.5, menu.pos().getZ() + 0.5);
        } else {
            fitMap();
        }
        addTabs();
        addRenderableWidget(new UiDropdown<>(leftPos + LEFT_X, topPos + 40, LEFT_W, List.of(FILTER_ALL, FILTER_FIGHT, FILTER_WORK, FILTER_TROUBLE),
                value -> tr("filter." + value, count(value)), () -> filter, value -> {
                    filter = value;
                    cardScroll = 0;
                }));
        // whom to shoot: the rules of the chosen group, or of the machines without one; the weather link of the console
        int ry = topPos + 50;
        addRenderableWidget(new UiToggle(leftPos + RIGHT_X, ry, RIGHT_W, () -> rule(OrderRules.MONSTERS), on -> setRule(OrderRules.MONSTERS, on),
                on -> tr(on ? "rule.monsters_on" : "rule.monsters_off")));
        addRenderableWidget(new UiToggle(leftPos + RIGHT_X, ry + 11, RIGHT_W, () -> rule(OrderRules.WAR), on -> setRule(OrderRules.WAR, on),
                on -> tr(on ? "rule.war_on" : "rule.war_off")));
        UiToggle intruders = addRenderableWidget(new UiToggle(leftPos + RIGHT_X, ry + 22, RIGHT_W, this::intruders, this::setIntruders,
                on -> tr(on ? "rule.intruders_on" : "rule.intruders_off")));
        ConsoleView view = menu.view();
        if (view != null && !view.intruderAllowed() && !intruders()) {
            intruders.disable(tr("intruders_forbidden"));
        }
        addRenderableWidget(new UiToggle(leftPos + RIGHT_X, ry + 33, RIGHT_W, () -> rule(OrderRules.RETALIATE), on -> setRule(OrderRules.RETALIATE, on),
                on -> tr(on ? "rule.retaliate_on" : "rule.retaliate_off")));
        // 1.8.0, block I: focus fire (a bit set to switch it off, see OrderRules.NO_FOCUS)
        addRenderableWidget(new UiToggle(leftPos + RIGHT_X, ry + 44, RIGHT_W, () -> !rule(OrderRules.NO_FOCUS), on -> setRule(OrderRules.NO_FOCUS, !on),
                on -> tr(on ? "rule.focus_on" : "rule.focus_off")));
        addRenderableWidget(new UiToggle(leftPos + RIGHT_X, ry + 55, RIGHT_W, () -> menu.view() != null && menu.view().weatherLink(),
                on -> send(ConsoleNetwork.WEATHER, null, -1, on ? 1 : 0), on -> tr(on ? "rule.weather_on" : "rule.weather_off")));
        // the orders
        int bw = (W - 14 - 8 * 2) / ORDERS.size();
        for (int i = 0; i < ORDERS.size(); i++) {
            MachineTask.Type type = ORDERS.get(i);
            UiButton button = addRenderableWidget(new UiButton(leftPos + 7 + i * (bw + 2), topPos + ORDERS_Y, bw, 14,
                    Component.translatable(type.translationKey()), null, 0, UiButton.Look.METAL, () -> press(type)));
            orderButtons.add(button);
        }
        addRenderableWidget(new UiDropdown<>(leftPos + W - 7 - 110, topPos + ORDERS_Y + 17, 110, List.of(0, 1, 2, 3),
                value -> tr("work_kind", Component.translatable("work_kind.projectrebirth." + MachineTask.WORKS.get(value))), () -> workKind,
                value -> workKind = value));
        // 1.8.0, block J: how far work androids look for work round their storage (0: the server's)
        addRenderableWidget(new UiDropdown<>(leftPos + W - 7 - 110 - 4 - 92, topPos + ORDERS_Y + 17, 92, List.of(0, 16, 24, 32, 48, 64),
                value -> value == 0 ? tr("search_radius_default") : tr("search_radius", value),
                () -> menu.view() == null ? 0 : menu.view().searchRadius(), value -> send(ConsoleNetwork.SEARCH_RADIUS, null, -1, value)));
        // the map's zoom buttons, in its corner
        addRenderableWidget(UiButton.icon(leftPos + MAP_X + MAP_W - 15, topPos + MAP_Y + 3, 12, UiSprites.ICON_ARROW_UP, 7, tr("zoom_in"),
                () -> map.mouseScrolled(map.getX() + MAP_W / 2.0, map.getY() + MAP_H / 2.0, 0, 1)));
        addRenderableWidget(UiButton.icon(leftPos + MAP_X + MAP_W - 15, topPos + MAP_Y + 16, 12, UiSprites.ICON_ARROW_DOWN, 7, tr("zoom_out"),
                () -> map.mouseScrolled(map.getX() + MAP_W / 2.0, map.getY() + MAP_H / 2.0, 0, -1)));
    }

    /** The group tabs, made again when the counts change. */
    private void addTabs() {
        if (tabs != null) {
            removeWidget(tabs);
        }
        ConsoleView view = menu.view();
        List<UiTabs.Tab> list = new ArrayList<>();
        list.add(UiTabs.Tab.of(tr("tab_all", view == null ? 0 : view.rows().size())));
        for (int g = 0; g < ConsoleBlockEntity.GROUPS; g++) {
            int members = view == null ? 0 : view.groups().get(g).members();
            list.add(new UiTabs.Tab(tr("tab_group", g + 1, members), GROUP_COLORS[g]));
        }
        tabs = addRenderableWidget(new UiTabs(leftPos + 7, topPos + 23, W - 14, list, () -> groupTab + 1, index -> chooseGroup(index - 1)));
    }

    @Override
    public void removed() {
        if (map != null) {
            mapCell = map.cell();
            map.close();
        }
        super.removed();
    }

    @Override
    protected void help() {
        lesson = 0;
    }

    // ------------------------------------------------------------------ the data

    @Nullable
    private ConsoleView.Row row(UUID id) {
        ConsoleView view = menu.view();
        if (view != null) {
            for (ConsoleView.Row row : view.rows()) {
                if (row.id().equals(id)) {
                    return row;
                }
            }
        }
        return null;
    }

    private static MachineKind kind(ConsoleView.Row row) {
        return MachineKind.byId(row.kind());
    }

    private static boolean has(ConsoleView.Row row, ConsoleAlarm alarm) {
        return row.facts().alarms().contains(alarm.ordinal());
    }

    /** The worst alarm going on for a machine (a lost or shut one counts by its state), or null. */
    @Nullable
    private static ConsoleAlarm worst(ConsoleView.Row row) {
        ConsoleAlarm worst = null;
        for (int id : row.facts().alarms()) {
            ConsoleAlarm alarm = ConsoleAlarm.byId(id);
            if (alarm != ConsoleAlarm.WORK_DONE && (worst == null || alarm.signal() > worst.signal())) {
                worst = alarm;
            }
        }
        return worst;
    }

    private static boolean trouble(ConsoleView.Row row) {
        if (row.state() != MachineCore.State.ACTIVE.ordinal()) {
            return true;
        }
        ConsoleAlarm worst = worst(row);
        return worst != null && worst != ConsoleAlarm.COMBAT;
    }

    private static boolean passes(ConsoleView.Row row, int value) {
        return switch (value) {
            case FILTER_FIGHT -> has(row, ConsoleAlarm.COMBAT);
            case FILTER_WORK -> row.task() == MachineTask.Type.WORK.ordinal();
            case FILTER_TROUBLE -> trouble(row);
            default -> true;
        };
    }

    private int count(int value) {
        ConsoleView view = menu.view();
        if (view == null) {
            return 0;
        }
        int count = 0;
        for (ConsoleView.Row row : view.rows()) {
            if ((groupTab < 0 || row.group() == groupTab) && passes(row, value)) {
                count++;
            }
        }
        return count;
    }

    /** The cards shown: the group's (or all), through the filter. */
    private List<ConsoleView.Row> cards() {
        ConsoleView view = menu.view();
        List<ConsoleView.Row> out = new ArrayList<>();
        if (view != null) {
            for (ConsoleView.Row row : view.rows()) {
                if ((groupTab < 0 || row.group() == groupTab) && passes(row, filter)) {
                    out.add(row);
                }
            }
        }
        return out;
    }

    private List<ConsoleView.Row> chosenRows() {
        List<ConsoleView.Row> out = new ArrayList<>();
        for (UUID id : selected) {
            ConsoleView.Row row = row(id);
            if (row != null) {
                out.add(row);
            }
        }
        return out;
    }

    private void chooseGroup(int group) {
        groupTab = group;
        cardScroll = 0;
        selected.clear();
        ConsoleView view = menu.view();
        if (view != null && group >= 0) {
            for (ConsoleView.Row row : view.rows()) {
                if (row.group() == group) {
                    selected.add(row.id());
                }
            }
        }
    }

    // ------------------------------------------------------------------ rules

    private boolean rule(int bit) {
        ConsoleView view = menu.view();
        if (view == null) {
            return true;
        }
        int rules = groupTab >= 0 ? view.groups().get(groupTab).rules() : view.rules();
        return (rules & bit) != 0;
    }

    private void setRule(int bit, boolean on) {
        ConsoleView view = menu.view();
        if (view == null) {
            return;
        }
        int rules = groupTab >= 0 ? view.groups().get(groupTab).rules() : view.rules();
        send(ConsoleNetwork.SET_RULES, null, groupTab >= 0 ? groupTab : ConsoleBlockEntity.ALL, on ? rules | bit : rules & ~bit);
    }

    private boolean intruders() {
        ConsoleView view = menu.view();
        return view != null && (groupTab >= 0 ? view.groups().get(groupTab).intruders() : view.intruderMode());
    }

    private void setIntruders(boolean on) {
        if (groupTab >= 0) {
            send(ConsoleNetwork.GROUP_INTRUDERS, null, groupTab, on ? 1 : 0);
        } else {
            send(ConsoleNetwork.CONSOLE_INTRUDERS, null, -1, on ? 1 : 0);
        }
    }

    private void send(int action, @Nullable UUID machine, int group, int value) {
        ConsoleOrders.send(menu.pos(), action, machine, group, value, 0, false);
    }

    // ------------------------------------------------------------------ orders

    /** Why none of the chosen machines can carry out this order, or null when at least one can. */
    @Nullable
    private Component unavailable(MachineTask.Type type) {
        List<ConsoleView.Row> rows = chosenRows();
        if (rows.isEmpty()) {
            return tr("choose_first");
        }
        String problem = null;
        for (ConsoleView.Row row : rows) {
            String why = OrderRules.problem(kind(row), row.facts().worker(), type);
            if (why == null) {
                return null;
            }
            problem = why;
        }
        return Component.translatable(problem);
    }

    private void press(MachineTask.Type type) {
        switch (type) {
            case GUARD -> {
                armed = MachineTask.Type.GUARD;
                say(tr("arm.guard"), false);
            }
            case PATROL -> {
                if (route.size() >= 2) {
                    order(MachineTask.Type.PATROL, List.copyOf(route));
                    route.clear();
                    armed = null;
                } else {
                    armed = MachineTask.Type.PATROL;
                    say(tr("arm.patrol"), false);
                }
            }
            case WORK -> {
                if (areaFrom != null && areaTo != null) {
                    order(MachineTask.Type.WORK, List.of(areaFrom, areaTo));
                    areaFrom = null;
                    areaTo = null;
                    armed = null;
                } else if (hasShiftDown()) {
                    // Shift: the area is marked on the map next
                    armed = MachineTask.Type.WORK;
                    say(tr("arm.work"), false);
                } else {
                    // 1.8.0, block J: no area: they find their work (and their storage) themselves
                    armed = null;
                    order(MachineTask.Type.WORK, List.of());
                }
            }
            default -> {
                armed = null;
                order(type, List.of());
            }
        }
    }

    private void order(MachineTask.Type type, List<BlockPos> points) {
        if (selected.isEmpty()) {
            say(tr("choose_first"), true);
            return;
        }
        PacketDistributor.sendToServer(new ConsoleNetwork.OrderManyPayload(menu.pos(), List.copyOf(selected), type.ordinal(), workKind, points));
        UiSounds.CLICK.play();
        say(tr("sent", Component.translatable(type.translationKey()), selected.size()), false);
    }

    private void say(Component text, boolean bad) {
        status = text;
        statusBad = bad;
        if (bad) {
            UiSounds.ERROR.play();
        }
    }

    // ------------------------------------------------------------------ the map

    private List<UiMap.Mark> marks() {
        List<UiMap.Mark> marks = new ArrayList<>();
        ConsoleView view = menu.view();
        marks.add(new UiMap.Mark(menu.pos().getX() + 0.5, menu.pos().getZ() + 0.5, 0xFFFFFFFF, null, tr("map.console"), 4));
        if (view == null) {
            return marks;
        }
        for (ConsoleView.Site site : view.sites()) {
            int color = switch (site.kind()) {
                case 2 -> 0xFFE0B33A;
                case 3 -> 0xFFB0B8C0;
                case 4 -> 0xFF5FBF3A;
                case 5 -> 0xFFD8C24A;
                default -> 0xFF37C6E8;
            };
            marks.add(new UiMap.Mark(site.x() + 0.5, site.z() + 0.5, color, null, tr("map.site." + site.kind()), 4));
        }
        view.map().ifPresent(snapshot -> {
            for (var marker : snapshot.markers()) {
                if (marker.kind() == com.projectrebirth.monitoring.Snapshot.SHELTER) {
                    marks.add(new UiMap.Mark(marker.x(), marker.z(), 0xFF4FD1C5, null, tr("map.shelter"), 3));
                }
            }
        });
        for (ConsoleView.Row row : view.rows()) {
            boolean chosen = selected.contains(row.id());
            int color = 0xFF000000 | stateColor(row);
            Component label = Component.literal(name(row)).append(" · ").append(stateText(row));
            if (chosen) {
                marks.add(new UiMap.Mark(row.x() + 0.5, row.z() + 0.5, UiColors.AMBER, null, label, 7));
            }
            marks.add(new UiMap.Mark(row.x() + 0.5, row.z() + 0.5, color, null, label, chosen ? 4 : 4));
        }
        return marks;
    }

    /** Over the cells: turret circles, the orders of the chosen machines (routes, guard points, areas), what is being marked. */
    private void overlay(GuiGraphics graphics, UiMap map) {
        ConsoleView view = menu.view();
        if (view == null) {
            return;
        }
        for (ConsoleView.Zone zone : view.zones()) {
            map.rect(graphics, Math.min(zone.x1(), zone.x2()), Math.min(zone.z1(), zone.z2()), Math.max(zone.x1(), zone.x2()) + 1,
                    Math.max(zone.z1(), zone.z2()) + 1, 0xC0E05A4A);
        }
        for (ConsoleView.Row row : view.rows()) {
            if (row.facts().range() > 0) {
                map.circle(graphics, row.x() + 0.5, row.z() + 0.5, row.facts().range(), selected.contains(row.id()) ? 0xC0FFB648 : 0x70FF8A7A);
            }
        }
        for (ConsoleView.Row row : view.rows()) {
            boolean chosen = selected.contains(row.id());
            int color = chosen ? 0xFFFFB648 : 0x80C9D0D6;
            List<BlockPos> points = row.facts().points();
            MachineTask.Type type = MachineTask.Type.byId(row.task());
            if (points.isEmpty() || !chosen && selected.size() > 0) {
                continue;
            }
            switch (type) {
                case PATROL -> {
                    for (int i = 0; i < points.size(); i++) {
                        BlockPos a = points.get(i);
                        BlockPos b = points.get((i + 1) % points.size());
                        map.line(graphics, a.getX() + 0.5, a.getZ() + 0.5, b.getX() + 0.5, b.getZ() + 0.5, color);
                    }
                }
                case GUARD -> {
                    BlockPos p = points.get(0);
                    map.line(graphics, row.x() + 0.5, row.z() + 0.5, p.getX() + 0.5, p.getZ() + 0.5, color & 0x60FFFFFF);
                    cross(graphics, map, p, color);
                }
                case WORK -> {
                    map.rect(graphics, points.get(0).getX(), points.get(0).getZ(), points.get(1).getX(), points.get(1).getZ(), color);
                    cross(graphics, map, points.get(2), 0xFF4FC3FF);
                }
                case AREA_PATROL -> {
                    int radius = com.projectrebirth.config.RebirthConfig.SERVER_SPEC.isLoaded()
                            ? com.projectrebirth.config.RebirthConfig.AREA_PATROL_RADIUS.get() : 16;
                    map.circle(graphics, points.get(0).getX() + 0.5, points.get(0).getZ() + 0.5, radius, color);
                }
                default -> {
                }
            }
        }
        // what the player is marking now
        for (int i = 0; i < route.size(); i++) {
            BlockPos a = route.get(i);
            cross(graphics, map, a, UiColors.AMBER_LIGHT);
            if (i + 1 < route.size()) {
                BlockPos b = route.get(i + 1);
                map.line(graphics, a.getX() + 0.5, a.getZ() + 0.5, b.getX() + 0.5, b.getZ() + 0.5, UiColors.AMBER_LIGHT);
            }
        }
        BlockPos from = areaFrom;
        BlockPos to = pressButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT && dragged() ? mapBlock(mouseNowX, mouseNowY) : areaTo;
        if (pressButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT && dragged()) {
            from = mapBlock(pressX, pressY);
        }
        if (from != null && to != null) {
            map.rect(graphics, from.getX(), from.getZ(), to.getX(), to.getZ(), UiColors.AMBER_LIGHT);
        }
    }

    private static void cross(GuiGraphics graphics, UiMap map, BlockPos at, int color) {
        int x = (int) Math.round(map.screenX(at.getX() + 0.5));
        int y = (int) Math.round(map.screenY(at.getZ() + 0.5));
        graphics.fill(x - 2, y, x + 3, y + 1, color);
        graphics.fill(x, y - 2, x + 1, y + 3, color);
    }

    private boolean onMap(double mouseX, double mouseY) {
        return mouseX >= leftPos + MAP_X + 2 && mouseX < leftPos + MAP_X + MAP_W - 2 && mouseY >= topPos + MAP_Y + 2 && mouseY < topPos + MAP_Y + MAP_H - 2;
    }

    private BlockPos mapBlock(double mouseX, double mouseY) {
        return BlockPos.containing(map.blockX(mouseX), menu.pos().getY(), map.blockZ(mouseY));
    }

    private boolean dragged() {
        return pressButton >= 0 && Math.abs(mouseNowX - pressX) + Math.abs(mouseNowY - pressY) > 4;
    }

    /** The machine under the mouse on the map, or null. */
    @Nullable
    private ConsoleView.Row machineAt(double mouseX, double mouseY) {
        ConsoleView view = menu.view();
        if (view == null) {
            return null;
        }
        for (ConsoleView.Row row : view.rows()) {
            if (Math.abs(map.screenX(row.x() + 0.5) - mouseX) <= 3 && Math.abs(map.screenY(row.z() + 0.5) - mouseY) <= 3) {
                return row;
            }
        }
        return null;
    }

    /** A service post or charging pad under the mouse on the map, or null. */
    @Nullable
    private ConsoleView.Site siteAt(double mouseX, double mouseY) {
        ConsoleView view = menu.view();
        if (view == null) {
            return null;
        }
        for (ConsoleView.Site site : view.sites()) {
            if (Math.abs(map.screenX(site.x() + 0.5) - mouseX) <= 4 && Math.abs(map.screenY(site.z() + 0.5) - mouseY) <= 4) {
                return site;
            }
        }
        return null;
    }

    /** "Show on the map": centres the map on a place. */
    void showOnMap(int x, int z) {
        map.centreOn(x + 0.5, z + 0.5);
        UiSounds.CLICK.play();
    }

    // ------------------------------------------------------------------ the mouse and keys

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (lesson >= 0) {
            return lessonClick(mouseX, mouseY);
        }
        // dropdowns open over other widgets: let them have the click first
        for (var child : children()) {
            if (child instanceof UiDropdown<?> dropdown && dropdown.isOpen()) {
                boolean taken = dropdown.mouseClicked(mouseX, mouseY, button);
                dropdown.closeIfOutside(mouseX, mouseY);
                if (taken) {
                    return true;
                }
            }
        }
        if (onMap(mouseX, mouseY) && !overWidgetOtherThanMap(mouseX, mouseY)) {
            pressX = mouseX;
            pressY = mouseY;
            mouseNowX = mouseX;
            mouseNowY = mouseY;
            pressButton = button;
            pressShift = hasShiftDown();
            setDragging(true);
            return true;
        }
        if (cardClick(mouseX, mouseY, button) || alarmClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean overWidgetOtherThanMap(double mouseX, double mouseY) {
        for (var child : children()) {
            if (child != map && child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible
                    && widget.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pressButton >= 0) {
            mouseNowX = mouseX;
            mouseNowY = mouseY;
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                map.pan(dragX, dragY);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pressButton >= 0 && button == pressButton) {
            mouseNowX = mouseX;
            mouseNowY = mouseY;
            boolean drag = dragged();
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                mapLeft(mouseX, mouseY, drag);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                mapRight(mouseX, mouseY, drag);
            }
            pressButton = -1;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Left on the map: a click chooses the machine there (Shift adds), a frame chooses all inside it; a double click opens it. */
    private void mapLeft(double mouseX, double mouseY, boolean drag) {
        ConsoleView view = menu.view();
        if (view == null) {
            return;
        }
        if (!pressShift) {
            selected.clear();
        }
        if (drag) {
            double x1 = Math.min(pressX, mouseX), x2 = Math.max(pressX, mouseX), y1 = Math.min(pressY, mouseY), y2 = Math.max(pressY, mouseY);
            for (ConsoleView.Row row : view.rows()) {
                double sx = map.screenX(row.x() + 0.5), sy = map.screenY(row.z() + 0.5);
                if (sx >= x1 && sx <= x2 && sy >= y1 && sy <= y2) {
                    selected.add(row.id());
                }
            }
            UiSounds.CLICK.play();
            return;
        }
        ConsoleView.Row row = machineAt(mouseX, mouseY);
        if (row != null) {
            selectCard(row, pressShift);
        }
    }

    /**
     * Right on the map: a click gives "guard here" (on a post: "to the post"), or the armed order there; with Shift it marks a
     * route point; a drag marks a work area (and orders the work if it was armed).
     */
    private void mapRight(double mouseX, double mouseY, boolean drag) {
        if (hasControlDown()) {
            // 1.8.0, block J: Ctrl + drag marks a zone «do not touch», Ctrl + click takes the zones there away
            BlockPos a = mapBlock(pressX, pressY), b = mapBlock(mouseX, mouseY);
            if (drag) {
                int x = Math.min(a.getX(), b.getX()), z = Math.min(a.getZ(), b.getZ());
                int sx = Math.min(256, Math.abs(a.getX() - b.getX())), sz = Math.min(256, Math.abs(a.getZ() - b.getZ()));
                ConsoleOrders.send(menu.pos(), ConsoleNetwork.ADD_ZONE, null, sx * 512 + sz, x, z, false);
                say(tr("zone_marked"), false);
            } else {
                ConsoleOrders.send(menu.pos(), ConsoleNetwork.REMOVE_ZONE, null, -1, b.getX(), b.getZ(), false);
                say(tr("zone_removed"), false);
            }
            UiSounds.CLICK.play();
            return;
        }
        if (drag) {
            areaFrom = mapBlock(pressX, pressY);
            areaTo = mapBlock(mouseX, mouseY);
            if (armed == MachineTask.Type.WORK) {
                press(MachineTask.Type.WORK);
            } else {
                say(tr("area_marked"), false);
            }
            return;
        }
        BlockPos at = mapBlock(mouseX, mouseY);
        if (pressShift || armed == MachineTask.Type.PATROL) {
            route.add(at);
            say(tr("route_points", route.size()), false);
            UiSounds.CLICK.play();
            return;
        }
        if (selected.isEmpty()) {
            say(tr("choose_first"), true);
            return;
        }
        if (siteAt(mouseX, mouseY) != null) {
            order(MachineTask.Type.TO_POST, List.of());
            return;
        }
        Component why = unavailable(MachineTask.Type.GUARD);
        if (why != null) {
            say(why, true);
            return;
        }
        armed = null;
        order(MachineTask.Type.GUARD, List.of(at));
    }

    private boolean cardClick(double mouseX, double mouseY, int button) {
        int x = leftPos + LEFT_X, y = topPos + CARDS_Y;
        if (mouseX < x || mouseX >= x + LEFT_W || mouseY < y || mouseY >= y + CARDS * CARD_H) {
            return false;
        }
        List<ConsoleView.Row> cards = cards();
        int index = cardScroll + (int) (mouseY - y) / CARD_H;
        if (index >= cards.size()) {
            if (!hasShiftDown()) {
                selected.clear();
            }
            return true;
        }
        ConsoleView.Row row = cards.get(index);
        long now = net.minecraft.Util.getMillis();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && row.id().equals(lastCardClicked) && now - lastCardClick < 350) {
            openMachine(row.id());
            return true;
        }
        lastCardClicked = row.id();
        lastCardClick = now;
        selectCard(row, hasShiftDown());
        return true;
    }

    private void selectCard(ConsoleView.Row row, boolean add) {
        if (!add) {
            selected.clear();
            selected.add(row.id());
        } else if (!selected.remove(row.id())) {
            selected.add(row.id());
        }
        UiSounds.CLICK.play();
    }

    void openMachine(UUID id) {
        if (minecraft != null && row(id) != null) {
            minecraft.pushGuiLayer(new MachineWindow(this, menu.pos(), id));
        }
    }

    /** The machine as this window knows it now (for its own window). */
    @Nullable
    ConsoleView.Row machine(UUID id) {
        return row(id);
    }

    ConsoleView view() {
        return menu.view();
    }

    private List<ConsoleView.Line> alarms() {
        ConsoleView view = menu.view();
        if (view == null) {
            return List.of();
        }
        List<ConsoleView.Line> lines = new ArrayList<>(view.log());
        java.util.Collections.reverse(lines);
        lines.removeIf(line -> ConsoleAlarm.byId(line.alarm()) == ConsoleAlarm.WORK_DONE && line.ended());
        return lines;
    }

    private boolean alarmClick(double mouseX, double mouseY) {
        int x = leftPos + RIGHT_X, y = topPos + ALARMS_Y;
        if (mouseX < x || mouseX >= x + RIGHT_W || mouseY < y || mouseY >= y + 3 * ALARM_H) {
            return false;
        }
        List<ConsoleView.Line> lines = alarms();
        int index = alarmScroll + (int) (mouseY - y) / ALARM_H;
        if (index < lines.size()) {
            ConsoleView.Line line = lines.get(index);
            showOnMap(line.x(), line.z());
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (lesson < 0) {
            int lx = leftPos + LEFT_X, ly = topPos + CARDS_Y;
            if (mouseX >= lx && mouseX < lx + LEFT_W && mouseY >= ly && mouseY < ly + CARDS * CARD_H) {
                cardScroll = Mth.clamp(cardScroll - (int) Math.signum(scrollY), 0, Math.max(0, cards().size() - CARDS));
                return true;
            }
            int ax = leftPos + RIGHT_X, ay = topPos + ALARMS_Y;
            if (mouseX >= ax && mouseX < ax + RIGHT_W && mouseY >= ay && mouseY < ay + 3 * ALARM_H) {
                alarmScroll = Mth.clamp(alarmScroll - (int) Math.signum(scrollY), 0, Math.max(0, alarms().size() - 3));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (lesson >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                endLesson();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_RIGHT) {
                nextLesson();
                return true;
            }
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_8 && getFocused() == null) {
            chooseGroup(keyCode - GLFW.GLFW_KEY_1);
            UiSounds.CLICK.play();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_0 && getFocused() == null) {
            chooseGroup(-1);
            UiSounds.CLICK.play();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !route.isEmpty()) {
            route.remove(route.size() - 1);
            say(tr("route_points", route.size()), false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (armed != null || !route.isEmpty() || areaFrom != null)) {
            armed = null;
            route.clear();
            areaFrom = null;
            areaTo = null;
            say(tr("marking_cancelled"), false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ drawing

    static int stateColor(ConsoleView.Row row) {
        return switch (MachineCore.State.byId(row.state())) {
            case ACTIVE -> worst(row) == ConsoleAlarm.COMBAT ? 0xFFB648 : trouble(row) ? 0xFFB648 : 0x7BD88F;
            case DISABLED -> 0xFFB648;
            case WRECK -> 0xFF8A7A;
        };
    }

    /** The state of a machine in words: a wreck, shut down, its worst alarm, or its task. */
    static Component stateText(ConsoleView.Row row) {
        MachineCore.State state = MachineCore.State.byId(row.state());
        if (state != MachineCore.State.ACTIVE) {
            return Component.translatable("machine_state.projectrebirth." + state.name().toLowerCase(Locale.ROOT));
        }
        ConsoleAlarm worst = worst(row);
        if (worst != null) {
            return Component.translatable("console_alarm_short.projectrebirth." + worst.id());
        }
        if (kind(row) == MachineKind.TURRET) {
            return tr("turret_task");
        }
        return Component.translatable(MachineTask.Type.byId(row.task()).translationKey());
    }

    static UiSprites.Tone tone(ConsoleView.Row row) {
        MachineCore.State state = MachineCore.State.byId(row.state());
        if (state == MachineCore.State.WRECK) {
            return UiSprites.Tone.ERROR;
        }
        if (state == MachineCore.State.DISABLED || trouble(row) || has(row, ConsoleAlarm.COMBAT)) {
            return UiSprites.Tone.WARNING;
        }
        return UiSprites.Tone.OK;
    }

    static String name(ConsoleView.Row row) {
        return ConsoleBlockEntity.display(row.name()).getString();
    }

    static ItemStack icon(MachineKind kind) {
        return switch (kind) {
            case ANDROID -> new ItemStack(GarrisonRegistry.AI_CORE.get());
            case TURRET -> new ItemStack(GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get());
            case MECH -> new ItemStack(GarrisonRegistry.GUARD_MECH_ITEM.get());
            case ROBOT -> new ItemStack(GarrisonRegistry.SMALL_ROBOT_ITEM.get());
            case TEST -> new ItemStack(GarrisonRegistry.TEST_MACHINE_ITEM.get());
        };
    }

    /** The second line of a card: energy and rounds, as known. */
    static Component supplies(ConsoleView.Row row) {
        StringBuilder text = new StringBuilder();
        if (row.facts().energy() >= 0) {
            text.append("⚡").append(Math.round(row.facts().energy() * 100)).append('%');
        }
        if (row.facts().ammo() >= 0) {
            if (!text.isEmpty()) {
                text.append(" · ");
            }
            text.append("▮").append(row.facts().ammo());
        }
        if (text.isEmpty()) {
            return row.heardSeconds() < 0 ? tr("never_heard") : Component.empty();
        }
        return Component.literal(text.toString());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (source.set(menu.view()) && map != null) {
            map.invalidate();
        }
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        ConsoleView view = menu.view();
        // the title bar: link and energy
        if (view != null) {
            Component link = view.powered() ? tr("link", view.range(), view.capacity() <= 0 ? 0 : view.energy() * 100 / view.capacity())
                    : tr("no_energy");
            int width = Math.min(150, font.width(link));
            graphics.fill(x + W - 50 - width, y + 5, x + W - 43, y + 16, UiColors.BODY);
            text(graphics, link, x + W - 46 - width, y + 7, width, view.powered() ? UiColors.TEXT_SECONDARY : UiColors.ERROR);
        }
        drawCards(graphics, x, y, mouseX, mouseY);
        // under the map: what the mouse does there, or what is being marked
        Component under = armed != null || !route.isEmpty() ? tr("arm." + (armed == null ? "patrol" : armed.id())) : tr("map_help");
        text(graphics, under, x + MAP_X, y + MAP_Y + MAP_H + 3, MAP_W, armed != null || !route.isEmpty() ? UiColors.AMBER_LIGHT : UiColors.TEXT_DIM);
        tooltip(x + MAP_X, y + MAP_Y + MAP_H + 2, MAP_W, 11, () -> UiTooltip.of(tr("map_help_title")).text(tr("map_help_full"), UiColors.TEXT_SECONDARY));
        if (view == null) {
            UiText.centred(graphics, tr("waiting"), x + MAP_X + MAP_W / 2, y + MAP_Y + MAP_H / 2, MAP_W - 10, UiColors.AMBER_DIM);
        }
        // right: rules, alarms
        Component whose = groupTab >= 0 ? tr("rules_group", groupTab + 1) : tr("rules_all");
        if (view != null && view.sheltering()) {
            // 1.8.0: in place of the label (the six switches fill the room under it)
            text(graphics, tr("sheltering"), x + RIGHT_X, y + 40, RIGHT_W, UiColors.ENERGY_TEXT);
        } else {
            UiPanels.label(graphics, whose, x + RIGHT_X, y + 40, RIGHT_W);
        }
        drawAlarms(graphics, x, y, mouseX, mouseY);
        // bottom: whom the orders are for, the state of the order buttons
        List<ConsoleView.Row> chosen = chosenRows();
        Component who;
        if (chosen.isEmpty()) {
            who = tr("chosen_none");
        } else {
            StringBuilder names = new StringBuilder();
            for (ConsoleView.Row row : chosen) {
                if (names.length() > 60) {
                    names.append(", …");
                    break;
                }
                if (!names.isEmpty()) {
                    names.append(", ");
                }
                names.append(name(row));
            }
            who = tr("chosen", chosen.size(), names.toString());
        }
        UiPanels.divider(graphics, x + 7, y + ORDERS_Y - 13, W - 14);
        text(graphics, who, x + 8, y + ORDERS_Y - 10, W - 16, UiColors.TEXT_SECONDARY);
        Component firstProblem = null;
        for (int i = 0; i < ORDERS.size() && i < orderButtons.size(); i++) {
            MachineTask.Type type = ORDERS.get(i);
            Component why = unavailable(type);
            UiButton button = orderButtons.get(i);
            button.disable(why);
            button.selected(() -> armed == type);
            if (why != null && button.isMouseOver(mouseX, mouseY)) {
                firstProblem = why;
            }
        }
        Component line = firstProblem != null ? tr("unavailable", firstProblem) : status;
        if (line != null) {
            text(graphics, line, x + 8, y + ORDERS_Y + 19, W - 16 - 116, firstProblem != null || statusBad ? UiColors.ERROR : UiColors.SCREEN_TEXT);
        }
    }

    private void drawCards(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int cx = x + LEFT_X;
        int cy = y + CARDS_Y;
        UiPanels.screen(graphics, cx, cy - 1, LEFT_W, CARDS * CARD_H + 2);
        List<ConsoleView.Row> cards = cards();
        cardScroll = Mth.clamp(cardScroll, 0, Math.max(0, cards.size() - CARDS));
        if (cards.isEmpty()) {
            UiText.wrapped(graphics, menu.view() == null ? tr("waiting") : tr(menu.view().rows().isEmpty() ? "no_machines" : "no_cards"),
                    cx + 5, cy + 5, LEFT_W - 10, 60, UiColors.AMBER_DIM);
        }
        for (int i = 0; i < CARDS && cardScroll + i < cards.size(); i++) {
            ConsoleView.Row row = cards.get(cardScroll + i);
            int ry = cy + i * CARD_H;
            boolean chosen = selected.contains(row.id());
            boolean hovered = mouseX >= cx && mouseX < cx + LEFT_W && mouseY >= ry && mouseY < ry + CARD_H;
            UiPanels.card(graphics, cx + 1, ry + 1, LEFT_W - 5, CARD_H - 1, chosen);
            if (hovered && !chosen) {
                graphics.fill(cx + 2, ry + 2, cx + LEFT_W - 5, ry + CARD_H - 1, 0x10FFFFFF);
            }
            graphics.pose().pushPose();
            graphics.pose().translate(cx + 3, ry + 4, 0);
            graphics.pose().scale(0.75f, 0.75f, 1.0f);
            graphics.renderItem(icon(kind(row)), 0, 0);
            graphics.pose().popPose();
            int gx = cx + LEFT_W - 16;
            if (row.group() >= 0) {
                graphics.fill(gx, ry + 4, gx + 4, ry + 8, 0xFF000000 | GROUP_COLORS[row.group()]);
                graphics.drawString(font, String.valueOf(row.group() + 1), gx + 6, ry + 3, UiColors.TEXT_SECONDARY, false);
            }
            text(graphics, Component.literal(name(row)), cx + 18, ry + 3, gx - cx - 20, chosen ? UiColors.AMBER_LIGHT : UiColors.TEXT);
            UiSprites.Tone tone = tone(row);
            Component supplies = supplies(row);
            int supplyWidth = Math.min(48, font.width(supplies));
            text(graphics, supplies, cx + 18, ry + 12, supplyWidth, UiColors.TEXT_SECONDARY);
            int sx = cx + 18 + supplyWidth + 4;
            text(graphics, stateText(row), sx, ry + 12, cx + LEFT_W - 7 - sx, tone.text);
            float structural = row.maxStructural() <= 0 ? 0 : row.structural() / row.maxStructural();
            float critical = row.maxCritical() <= 0 ? 0 : row.critical() / row.maxCritical();
            UiBars.horizontal(graphics, cx + 18, ry + 21, LEFT_W - 26, 1, structural, 0xFF000000 | UiColors.integrity(structural));
            UiBars.horizontal(graphics, cx + 18, ry + 23, LEFT_W - 26, 1, critical, UiColors.ERROR);
            if (hovered) {
                tooltip(cx, ry, LEFT_W, CARD_H, () -> UiTooltip.of(Component.literal(name(row)))
                        .row(tr("kind." + kind(row).id()), stateText(row), tone.text)
                        .row(tr("card.structural"), Component.literal(Mth.ceil(row.structural()) + "/" + Mth.ceil(row.maxStructural())), UiColors.SCREEN_TEXT)
                        .row(tr("card.critical"), Component.literal(Mth.ceil(row.critical()) + "/" + Mth.ceil(row.maxCritical())), UiColors.SCREEN_TEXT)
                        .row(tr("card.where"), Component.literal(row.x() + " " + row.y() + " " + row.z()), UiColors.SCREEN_TEXT)
                        .row(tr("card.heard"), ConsoleOrders.heard(row.heardSeconds()), UiColors.SCREEN_TEXT)
                        .text(tr("card.hint"), UiColors.TEXT_DIM));
            }
        }
        if (cards.size() > CARDS) {
            int trackH = CARDS * CARD_H;
            int thumbH = Math.max(8, trackH * CARDS / cards.size());
            int thumbY = (trackH - thumbH) * cardScroll / Math.max(1, cards.size() - CARDS);
            graphics.fill(cx + LEFT_W - 3, cy, cx + LEFT_W - 1, cy + trackH, 0xFF1F2623);
            graphics.fill(cx + LEFT_W - 3, cy + thumbY, cx + LEFT_W - 1, cy + thumbY + thumbH, UiColors.AMBER);
        }
    }

    private void drawAlarms(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        List<ConsoleView.Line> lines = alarms();
        long going = lines.stream().filter(line -> !line.ended()).count();
        UiPanels.label(graphics, tr("alarms", going), x + RIGHT_X, y + ALARMS_Y - 10, RIGHT_W);
        int ax = x + RIGHT_X;
        int ay = y + ALARMS_Y;
        UiPanels.screen(graphics, ax, ay - 1, RIGHT_W, 3 * ALARM_H + 2);
        alarmScroll = Mth.clamp(alarmScroll, 0, Math.max(0, lines.size() - 3));
        if (lines.isEmpty()) {
            UiText.wrapped(graphics, tr("log_empty"), ax + 4, ay + 4, RIGHT_W - 8, 40, UiColors.TEXT_DIM);
        }
        for (int i = 0; i < 3 && alarmScroll + i < lines.size(); i++) {
            ConsoleView.Line line = lines.get(alarmScroll + i);
            ConsoleAlarm alarm = ConsoleAlarm.byId(line.alarm());
            int ly = ay + i * ALARM_H;
            UiSprites.Tone tone = line.ended() ? UiSprites.Tone.OK : alarm.signal() >= 9 ? UiSprites.Tone.ERROR
                    : alarm.signal() >= 3 ? UiSprites.Tone.WARNING : UiSprites.Tone.INFO;
            boolean hovered = mouseX >= ax && mouseX < ax + RIGHT_W && mouseY >= ly && mouseY < ly + ALARM_H;
            if (hovered) {
                graphics.fill(ax + 1, ly, ax + RIGHT_W - 1, ly + ALARM_H - 1, 0x10FFFFFF);
            }
            UiSprites.draw(graphics, tone.icon, ax + 3, ly + 2, 7, 7);
            Component time = ConsoleOrders.ago(line.secondsAgo());
            int timeWidth = font.width(time);
            Component what = Component.translatable("console_alarm.projectrebirth." + alarm.id() + (line.ended() ? "_ended" : ""))
                    .append(": ").append(ConsoleBlockEntity.display(line.name()));
            text(graphics, what, ax + 12, ly + 2, RIGHT_W - 18 - timeWidth, tone.text);
            graphics.drawString(font, time, ax + RIGHT_W - 3 - timeWidth, ly + 2, UiColors.TEXT_DIM, false);
            Component todo = line.ended() ? tr("alarm_over") : Component.translatable("console_alarm_todo.projectrebirth." + alarm.id());
            text(graphics, todo, ax + 12, ly + 11, RIGHT_W - 15, UiColors.TEXT_SECONDARY);
            text(graphics, tr("show_on_map"), ax + 12, ly + 19, RIGHT_W - 15, hovered ? UiColors.AMBER_LIGHT : UiColors.AMBER_DIM);
            if (hovered) {
                tooltip(ax, ly, RIGHT_W, ALARM_H, () -> UiTooltip.of(what).text(todo, UiColors.TEXT_SECONDARY)
                        .row(tr("card.where"), Component.literal(line.x() + " " + line.y() + " " + line.z()), UiColors.SCREEN_TEXT)
                        .text(tr("alarm_hint"), UiColors.TEXT_DIM));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (pressButton >= 0) {
            mouseNowX = mouseX;
            mouseNowY = mouseY;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        // a selection frame being dragged on the map
        if (pressButton == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragged()) {
            int x1 = (int) Math.min(pressX, mouseX), x2 = (int) Math.max(pressX, mouseX);
            int y1 = (int) Math.min(pressY, mouseY), y2 = (int) Math.max(pressY, mouseY);
            graphics.fill(x1, y1, x2, y2, 0x20FFB648);
            graphics.renderOutline(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1), UiColors.AMBER);
        } else if (lesson < 0 && pressButton < 0 && onMap(mouseX, mouseY)) {
            Component hovered = map.hovered(mouseX, mouseY);
            if (hovered != null && !overWidgetOtherThanMap(mouseX, mouseY)) {
                UiTooltip.of(hovered).render(graphics, mouseX, mouseY);
            }
        }
        if (lesson >= 0) {
            drawLesson(graphics, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ the lesson

    private static final int LESSON_STEPS = 5;

    /** The area each step points at (relative to the window). */
    private int[] lessonArea(int step) {
        return switch (step) {
            case 0 -> new int[] {LEFT_X, CARDS_Y - 16, LEFT_W, CARDS * CARD_H + 17};
            case 1 -> new int[] {MAP_X, MAP_Y, MAP_W, MAP_H + 12};
            case 2 -> new int[] {7, 22, W - 14, 16};
            case 3 -> new int[] {RIGHT_X - 1, 38, RIGHT_W + 2, 78};
            // (the rules: their label and six switches)
            default -> new int[] {RIGHT_X - 1, ALARMS_Y - 12, RIGHT_W + 2, 3 * ALARM_H + 13};
        };
    }

    private int[] lessonBox(int step) {
        int[] area = lessonArea(step);
        int bw = 184;
        int bh = 82;
        int bx = area[0] + area[2] + 6 + bw <= W ? area[0] + area[2] + 6 : area[0] - bw - 6;
        if (bx < 0) {
            bx = (W - bw) / 2;
        }
        int by = Mth.clamp(area[1] + 8, 22, H - bh - 4);
        if (step == 2) {
            by = 44;
            bx = (W - bw) / 2;
        }
        return new int[] {leftPos + bx, topPos + by, bw, bh};
    }

    private void drawLesson(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 400.0f);
        int[] area = lessonArea(lesson);
        int ax = leftPos + area[0], ay = topPos + area[1];
        // darken everything but the area
        int color = 0xA0000000;
        graphics.fill(leftPos, topPos, leftPos + W, ay, color);
        graphics.fill(leftPos, ay + area[3], leftPos + W, topPos + H, color);
        graphics.fill(leftPos, ay, ax, ay + area[3], color);
        graphics.fill(ax + area[2], ay, leftPos + W, ay + area[3], color);
        graphics.renderOutline(ax - 1, ay - 1, area[2] + 2, area[3] + 2, UiColors.AMBER);
        int[] box = lessonBox(lesson);
        UiPanels.window(graphics, box[0], box[1], box[2], box[3]);
        UiText.line(graphics, UiText.upper(tr("lesson." + lesson + ".title")), box[0] + 7, box[1] + 6, box[2] - 40, UiColors.AMBER);
        graphics.drawString(font, (lesson + 1) + "/" + LESSON_STEPS, box[0] + box[2] - 25, box[1] + 6, UiColors.TEXT_DIM, false);
        UiText.wrapped(graphics, tr("lesson." + lesson + ".text"), box[0] + 7, box[1] + 17, box[2] - 14, 46, UiColors.TEXT);
        boolean overSkip = in(mouseX, mouseY, box[0] + 7, box[1] + box[3] - 16, 60, 12);
        boolean overNext = in(mouseX, mouseY, box[0] + box[2] - 67, box[1] + box[3] - 16, 60, 12);
        UiSprites.draw(graphics, overSkip ? UiSprites.BUTTON_HOVER : UiSprites.BUTTON, box[0] + 7, box[1] + box[3] - 16, 60, 12);
        UiText.centred(graphics, tr("lesson.skip"), box[0] + 37, box[1] + box[3] - 14, 56, UiColors.TEXT_SECONDARY);
        UiSprites.draw(graphics, overNext ? UiSprites.BUTTON_HOVER : UiSprites.BUTTON, box[0] + box[2] - 67, box[1] + box[3] - 16, 60, 12);
        UiText.centred(graphics, tr(lesson + 1 < LESSON_STEPS ? "lesson.next" : "lesson.done"), box[0] + box[2] - 37, box[1] + box[3] - 14, 56,
                UiColors.AMBER_LIGHT);
        graphics.pose().popPose();
    }

    private static boolean in(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private boolean lessonClick(double mouseX, double mouseY) {
        int[] box = lessonBox(lesson);
        if (in(mouseX, mouseY, box[0] + 7, box[1] + box[3] - 16, 60, 12)) {
            endLesson();
        } else if (in(mouseX, mouseY, box[0] + box[2] - 67, box[1] + box[3] - 16, 60, 12)) {
            nextLesson();
        }
        return true;
    }

    private void nextLesson() {
        UiSounds.CLICK.play();
        if (++lesson >= LESSON_STEPS) {
            endLesson();
        }
    }

    private void endLesson() {
        lesson = -1;
        if (RebirthClientConfig.CLIENT_SPEC.isLoaded() && RebirthClientConfig.UI_CONSOLE_TUTORIAL.get()) {
            RebirthClientConfig.UI_CONSOLE_TUTORIAL.set(false);
        }
    }

    /** For the layout check of development: shows a step of the lesson (or -1 none). */
    public void showLesson(int step) {
        lesson = step;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
