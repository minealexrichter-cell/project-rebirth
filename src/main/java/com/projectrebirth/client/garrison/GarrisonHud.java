package com.projectrebirth.client.garrison;

import java.util.ArrayList;
import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.client.HudLayout;
import com.projectrebirth.config.RebirthClientConfig;
import com.projectrebirth.garrison.ConsoleAlarm;
import com.projectrebirth.garrison.ConsoleNetwork;
import com.projectrebirth.garrison.TabletData;
import com.projectrebirth.garrison.TabletItem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The garrison's HUD (1.7.0, block I): the mark of a console alarm going on (the worst one, at the top right, while the server
 * keeps telling it), and the lines of the tactical tablet in hand (its mode, its console, its marks) at the left. Both follow the
 * HUD settings (key H). The bars over the machines are drawn with the machines ({@code MachineBars}).
 */
public final class GarrisonHud {
    /** Ticks a mark stays after the server last told it (it tells once a second). */
    private static final int MARK_TICKS = 50;
    private static ConsoleAlarm alarm;
    private static BlockPos alarmAt = BlockPos.ZERO;
    private static long alarmTime = -1000;

    private GarrisonHud() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(GarrisonHud::registerGuiLayers);
        ConsoleNetwork.alarmHandler = payload -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().level != null) {
                alarm = ConsoleAlarm.byId(payload.alarm());
                alarmAt = payload.console();
                alarmTime = Minecraft.getInstance().level.getGameTime();
            }
        });
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ProjectRebirth.id("garrison_hud"), GarrisonHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.options.hideGui || !HudLayout.enabled()) {
            return;
        }
        Font font = minecraft.font;
        if (RebirthClientConfig.HUD_CONSOLE_ALARM.get() && alarm != null && minecraft.level.getGameTime() - alarmTime <= MARK_TICKS) {
            Component text = Component.translatable("hud.projectrebirth.console_alarm", Component.translatable("console_alarm.projectrebirth." + alarm.id()),
                    alarmAt.getX(), alarmAt.getY(), alarmAt.getZ());
            int w = font.width(text) + 18;
            int x = graphics.guiWidth() - w - 4;
            int y = 4;
            boolean blink = (minecraft.level.getGameTime() / 10) % 2 == 0;
            graphics.fill(x, y, x + w, y + 14, 0xA0101010);
            graphics.fill(x + 2, y + 2, x + 12, y + 12, blink ? 0xFFE0352A : 0xFF8A2A22);
            graphics.drawCenteredString(font, "!", x + 7, y + 3, 0xFFFFFFFF);
            graphics.drawString(font, text, x + 15, y + 3, 0xFFFF8A7A, false);
        }
        if (RebirthClientConfig.HUD_TABLET_LINES.get()) {
            ItemStack tablet = player.getMainHandItem().getItem() instanceof TabletItem ? player.getMainHandItem()
                    : player.getOffhandItem().getItem() instanceof TabletItem ? player.getOffhandItem() : ItemStack.EMPTY;
            if (!tablet.isEmpty()) {
                tabletPanel(graphics, font, TabletItem.data(tablet));
                directions(graphics, font, player, delta.getGameTimeDeltaPartialTick(false));
            }
        }
    }

    /**
     * 1.8.0 block F: the tablet's panel at the bottom left, in the style "Industrial terminal": the console, the machines chosen,
     * the order waiting for its points and what to do next, the reason of an order that could not be given, and the keys.
     */
    private static void tabletPanel(GuiGraphics graphics, Font font, TabletData data) {
        List<Component> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        Component title = data.console().map(where -> (Component) Component.translatable("hud.projectrebirth.tablet.title_console",
                where.pos().getX(), where.pos().getY(), where.pos().getZ())).orElse(Component.translatable("hud.projectrebirth.tablet.no_console"));
        int chosen = TabletClient.selection().size();
        if (data.console().isPresent()) {
            lines.add(chosen == 0 ? Component.translatable("hud.projectrebirth.tablet.chosen_none")
                    : TabletClient.group() >= 0 ? Component.translatable("hud.projectrebirth.tablet.chosen_group", TabletClient.group() + 1, chosen)
                    : Component.translatable("hud.projectrebirth.tablet.chosen", chosen));
            colors.add(chosen == 0 ? com.projectrebirth.client.ui.UiColors.TEXT_DIM : com.projectrebirth.client.ui.UiColors.AMBER_LIGHT);
        }
        if (data.pending().isPresent()) {
            for (Component line : lines(data)) {
                lines.add(line);
                colors.add(com.projectrebirth.client.ui.UiColors.WARNING);
            }
        }
        Component refusal = TabletClient.refusal();
        if (refusal != null) {
            lines.add(Component.literal("✕ ").append(refusal));
            colors.add(com.projectrebirth.client.ui.UiColors.ERROR);
        }
        if (data.console().isPresent()) {
            lines.add(Component.translatable("hud.projectrebirth.tablet.keys", TabletClient.MENU_KEY.getTranslatedKeyMessage()));
            colors.add(com.projectrebirth.client.ui.UiColors.TEXT_SECONDARY);
        }
        int width = Math.max(font.width(title) + 10, lines.stream().mapToInt(font::width).max().orElse(0) + 10);
        width = Math.min(width, graphics.guiWidth() / 2);
        int height = 13 + lines.size() * 10 + 3;
        int x = 4;
        // at the middle of the left edge, above the chat (1.7 had its lines there too)
        int y = Math.max(20, graphics.guiHeight() / 2 - height / 2 - 20);
        com.projectrebirth.client.hud.HudDraw.panel(graphics, x, y, width, height);
        com.projectrebirth.client.ui.UiText.line(graphics, com.projectrebirth.client.ui.UiText.upper(title), x + 5, y + 3, width - 10,
                com.projectrebirth.client.ui.UiColors.AMBER);
        for (int i = 0; i < lines.size(); i++) {
            com.projectrebirth.client.ui.UiText.line(graphics, lines.get(i), x + 5, y + 14 + i * 10, width - 10, colors.get(i));
        }
    }

    /**
     * 1.8.0 block F: where the chosen machines are: a mark at the top of the screen in their direction (at the edge, with an arrow,
     * when they are behind or aside), with the name and distance.
     */
    private static void directions(GuiGraphics graphics, Font font, Player player, float partial) {
        com.projectrebirth.garrison.ConsoleView view = TabletClient.view();
        if (view == null || TabletClient.selection().isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        double aspect = minecraft.getWindow().getWidth() / (double) Math.max(1, minecraft.getWindow().getHeight());
        double halfFov = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(minecraft.options.fov().get() / 2.0)) * aspect));
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition(partial);
        int shown = 0;
        int centre = graphics.guiWidth() / 2;
        for (java.util.UUID id : TabletClient.selection()) {
            com.projectrebirth.garrison.ConsoleView.Row row = TabletClient.row(id);
            if (row == null || shown >= 8) {
                continue;
            }
            double dx = row.x() + 0.5 - eye.x;
            double dz = row.z() + 0.5 - eye.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < 6) {
                continue;
            }
            float bearing = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
            float diff = net.minecraft.util.Mth.wrapDegrees(bearing - player.getViewYRot(partial));
            boolean edge = Math.abs(diff) > halfFov * 0.9;
            int x = edge ? (diff < 0 ? 10 : graphics.guiWidth() - 10) : centre + (int) Math.round(diff / halfFov * centre);
            int y = 28 + shown * 0;
            Component label = Component.literal(edge ? (diff < 0 ? "◀ " : "") : "▼ ")
                    .append(Component.translatable("hud.projectrebirth.tablet.direction", ConsoleScreen.name(row), Math.round(distance)))
                    .append(edge && diff > 0 ? " ▶" : "");
            int w = font.width(label);
            int lx = net.minecraft.util.Mth.clamp(x - w / 2, 2, graphics.guiWidth() - w - 2);
            int ly = y + shown * 11;
            graphics.fill(lx - 2, ly - 1, lx + w + 2, ly + 9, 0x90101010);
            graphics.drawString(font, label, lx, ly, 0xFF000000 | ConsoleScreen.stateColor(row), false);
            shown++;
        }
    }

    /** The tablet's lines: the order that waits for its points and what to do next (1.7.1), the console, the marks it holds. */
    static List<Component> lines(TabletData data) {
        List<Component> lines = new ArrayList<>();
        if (data.pending().isPresent()) {
            TabletData.Pending pending = data.pending().get();
            lines.add(Component.translatable("hud.projectrebirth.tablet.pending", Component.translatable(pending.taskType().translationKey()),
                    TabletItem.target(pending)).withStyle(net.minecraft.ChatFormatting.YELLOW));
            int have = pending.points().size();
            String step = switch (pending.taskType()) {
                case PATROL -> have == 0 ? "route_first" : "route_more";
                case WORK -> have == 0 ? "corner_first" : have == 1 ? "corner_second" : "chest";
                default -> "guard";
            };
            lines.add(Component.translatable("hud.projectrebirth.tablet.step." + step, have).withStyle(net.minecraft.ChatFormatting.YELLOW));
            if (!step.equals("route_more")) {
                lines.add(Component.translatable("hud.projectrebirth.tablet.cancel"));
            }
            return lines;
        }
        lines.add(data.console().map(where -> Component.translatable("hud.projectrebirth.tablet.console", where.pos().getX(), where.pos().getY(), where.pos().getZ()))
                .orElse(Component.translatable("hud.projectrebirth.tablet.no_console")));
        data.guard().ifPresent(p -> lines.add(Component.translatable("hud.projectrebirth.tablet.guard", p.getX(), p.getY(), p.getZ())));
        if (!data.route().isEmpty()) {
            lines.add(Component.translatable("hud.projectrebirth.tablet.route", data.route().size()));
        }
        data.areaFrom().ifPresent(a -> lines.add(data.areaTo().map(b -> Component.translatable("hud.projectrebirth.tablet.area",
                Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getZ() - b.getZ()) + 1, Math.abs(a.getY() - b.getY()) + 1))
                .orElse(Component.translatable("hud.projectrebirth.tablet.area_half"))));
        data.chest().ifPresent(p -> lines.add(Component.translatable("hud.projectrebirth.tablet.chest", p.getX(), p.getY(), p.getZ())));
        return lines;
    }
}
