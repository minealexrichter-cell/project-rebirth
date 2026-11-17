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
                List<Component> lines = lines(TabletItem.data(tablet));
                int y = graphics.guiHeight() / 2 - lines.size() * 5;
                int width = lines.stream().mapToInt(font::width).max().orElse(0) + 8;
                graphics.fill(2, y - 3, 2 + width, y + lines.size() * 10 + 1, 0x80101010);
                for (Component line : lines) {
                    graphics.drawString(font, line, 6, y, 0xFFDDF6FF, false);
                    y += 10;
                }
            }
        }
    }

    /** The tablet's lines: mode, console, and the marks it holds. */
    static List<Component> lines(TabletData data) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hud.projectrebirth.tablet.mode", Component.translatable("tablet_mode.projectrebirth." + data.mode())));
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
