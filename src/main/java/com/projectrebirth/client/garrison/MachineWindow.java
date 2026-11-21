package com.projectrebirth.client.garrison;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiButton;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.client.ui.UiTooltip;
import com.projectrebirth.garrison.ConsoleAlarm;
import com.projectrebirth.garrison.ConsoleBlockEntity;
import com.projectrebirth.garrison.ConsoleNetwork;
import com.projectrebirth.garrison.ConsoleView;
import com.projectrebirth.garrison.MachineKind;
import com.projectrebirth.garrison.MachineTask;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The window of one machine over the console (1.8.0, block E; a double click on its card): its model (when it is loaded near the
 * player), both integrities, energy and rounds, its group, what it carries and which upgrades it has (asked of the server), its name
 * (to rename) and its lines of the console's log. "Show on the map" and "Let go" act through the console. Client only.
 */
public class MachineWindow extends Screen {
    private static final int W = 330;
    private static final int H = 222;

    /** What the server last told about a machine's gear and upgrades (one machine's window is open at a time). */
    @Nullable
    private static ConsoleNetwork.DetailsPayload details;

    private final ConsoleScreen console;
    private final BlockPos pos;
    private final UUID id;
    private int left;
    private int top;
    private EditBox name;
    private boolean confirmUnlink;
    private final List<Area> areas = new ArrayList<>();

    private record Area(int x, int y, int w, int h, ItemStack stack) {
    }

    public MachineWindow(ConsoleScreen console, BlockPos pos, UUID id) {
        super(Component.translatable("screen.projectrebirth.console.machine_window"));
        this.console = console;
        this.pos = pos;
        this.id = id;
        details = null;
        PacketDistributor.sendToServer(new ConsoleNetwork.DetailsRequestPayload(pos, id));
    }

    /** Set up once: the server's answer reaches the open window. */
    public static void register() {
        ConsoleNetwork.detailsHandler = payload -> Minecraft.getInstance().execute(() -> details = payload);
    }

    private static Component tr(String key, Object... args) {
        return ConsoleScreen.tr(key, args);
    }

    @Nullable
    private ConsoleView.Row row() {
        return console.machine(id);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        ConsoleView.Row row = row();
        String typed = name == null ? row == null ? "" : ConsoleScreen.name(row) : name.getValue();
        name = new EditBox(font, left + 128, top + 25, W - 128 - 62, 14, tr("name"));
        name.setMaxLength(ConsoleNetwork.NAME_LENGTH);
        name.setValue(typed);
        addRenderableWidget(name);
        addRenderableWidget(new UiButton(left + W - 58, top + 25, 51, 14, tr("rename"), null, 0, UiButton.Look.METAL, () -> {
            String value = name.getValue().trim();
            if (!value.isEmpty()) {
                PacketDistributor.sendToServer(new ConsoleNetwork.RenamePayload(pos, id, value));
            }
        }));
        // the group: none, 1-8
        int gy = top + 117;
        addRenderableWidget(new UiButton(left + 128, gy, 22, 11, Component.literal("—"), null, 0, UiButton.Look.CHIP, () -> setGroup(-1))
                .selected(() -> row() != null && row().group() < 0));
        for (int g = 0; g < ConsoleBlockEntity.GROUPS; g++) {
            int group = g;
            addRenderableWidget(new UiButton(left + 152 + g * 21, gy, 19, 11, Component.literal(String.valueOf(g + 1)), null, 0, UiButton.Look.CHIP,
                    () -> setGroup(group)).selected(() -> row() != null && row().group() == group).color(0xFF000000 | ConsoleScreen.GROUP_COLORS[g]));
        }
        int by = top + H - 21;
        addRenderableWidget(new UiButton(left + 7, by, 96, 14, tr("show_on_map"), null, 0, UiButton.Look.METAL, () -> {
            ConsoleView.Row now = row();
            if (now != null) {
                console.showOnMap(now.x(), now.z());
            }
            onClose();
        }));
        addRenderableWidget(new UiButton(left + 107, by, 96, 14, tr("unlink"), null, 0, UiButton.Look.METAL, () -> {
            if (!confirmUnlink) {
                confirmUnlink = true;
                return;
            }
            com.projectrebirth.client.garrison.ConsoleOrders.send(pos, ConsoleNetwork.UNLINK, id, -1, 0, 0, false);
            onClose();
        }).color(UiColors.ERROR));
        addRenderableWidget(new UiButton(left + W - 7 - 96, by, 96, 14, tr("back"), null, 0, UiButton.Look.METAL, this::onClose));
        addRenderableWidget(UiButton.icon(left + W - 21, top + 4, 13, UiSprites.ICON_CLOSE, 7, Component.translatable("screen.projectrebirth.ui.close"),
                this::onClose));
    }

    private void setGroup(int group) {
        ConsoleOrders.send(pos, ConsoleNetwork.SET_GROUP, id, -1, group, 0, false);
    }

    /** The machine itself, when the player's world has it loaded (then its model, in its own gear, is drawn). */
    @Nullable
    private LivingEntity entity() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity.getUUID().equals(id) && entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiPanels.window(graphics, left, top, W, H);
        ConsoleView.Row row = row();
        Component title = row == null ? this.title : Component.literal(ConsoleScreen.name(row));
        UiPanels.title(graphics, left, top, W, title, UiSprites.ICON_STATION, left + W - 26);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        areas.clear();
        ConsoleView.Row row = row();
        if (row == null) {
            UiText.centred(graphics, tr("machine_gone"), left + W / 2, top + 90, W - 20, UiColors.AMBER_DIM);
            return;
        }
        MachineKind kind = MachineKind.byId(row.kind());
        // the model
        int mx = left + 7, my = top + 25, mw = 114, mh = 120;
        UiPanels.screen(graphics, mx, my, mw, mh);
        LivingEntity entity = entity();
        if (entity != null) {
            float tall = Math.max(entity.getBbHeight(), entity.getBbWidth() * 0.9f);
            int scale = Math.max(6, Math.round(mh * 0.8f / Math.max(0.5f, tall)));
            graphics.enableScissor(mx + 2, my + 2, mx + mw - 2, my + mh - 2);
            GuiAtlas.model(graphics, mx, my + 4, mx + mw, my + mh - 4, scale, 0.0625f, mouseX, mouseY, entity);
            graphics.disableScissor();
        } else {
            graphics.pose().pushPose();
            graphics.pose().translate(mx + mw / 2.0f - 24, my + 30, 0);
            graphics.pose().scale(3.0f, 3.0f, 1.0f);
            graphics.renderItem(ConsoleScreen.icon(kind), 0, 0);
            graphics.pose().popPose();
            UiText.wrapped(graphics, tr("not_in_view"), mx + 5, my + mh - 30, mw - 10, 26, UiColors.TEXT_DIM);
        }
        // the facts
        int rx = left + 128, rw = W - 7 - 128;
        UiSprites.Tone tone = ConsoleScreen.tone(row);
        UiText.line(graphics, tr("kind." + kind.id()).copy().append(" · ").append(ConsoleScreen.stateText(row)), rx, top + 44, rw, tone.text);
        float structural = row.maxStructural() <= 0 ? 0 : row.structural() / row.maxStructural();
        float critical = row.maxCritical() <= 0 ? 0 : row.critical() / row.maxCritical();
        UiPanels.label(graphics, tr("card.structural"), rx, top + 55, rw);
        UiBars.meter(graphics, null, rx, top + 64, rw, structural, 0xFF000000 | UiColors.integrity(structural),
                Component.literal(Math.round(structural * 100) + "%"));
        UiPanels.label(graphics, tr("card.critical"), rx, top + 75, rw);
        UiBars.meter(graphics, null, rx, top + 84, rw, critical, UiColors.ERROR, Component.literal(Math.round(critical * 100) + "%"));
        Component supplies = ConsoleScreen.supplies(row);
        Component task = Component.translatable(MachineTask.Type.byId(row.task()).translationKey());
        UiText.line(graphics, tr("task_line", task).copy().append("  ").append(supplies), rx, top + 96, rw, UiColors.SCREEN_TEXT);
        UiPanels.label(graphics, tr("group_label"), rx, top + 107, rw);
        // gear and upgrades
        int gy = top + 132;
        UiPanels.label(graphics, tr("gear"), rx, gy, rw);
        ConsoleNetwork.DetailsPayload known = details != null && details.machine().equals(id) ? details : null;
        if (known == null) {
            UiText.line(graphics, tr("asking"), rx, gy + 11, rw, UiColors.TEXT_DIM);
        } else if (!known.loaded()) {
            UiText.line(graphics, tr("not_loaded"), rx, gy + 11, rw, UiColors.TEXT_DIM);
        } else {
            items(graphics, known.gear(), rx, gy + 9, rw, tr("gear_none"));
            UiPanels.label(graphics, tr("upgrades"), rx, gy + 27, rw);
            items(graphics, known.upgrades(), rx, gy + 36, rw, tr("upgrades_none"));
        }
        // the journal: this machine's lines of the log
        int jy = top + 150;
        UiPanels.label(graphics, tr("journal"), left + 7, jy, 114);
        UiPanels.screen(graphics, left + 7, jy + 9, 114, 40);
        int ly = jy + 12;
        ConsoleView view = console.view();
        int shown = 0;
        if (view != null) {
            List<ConsoleView.Line> lines = new ArrayList<>(view.log());
            java.util.Collections.reverse(lines);
            for (ConsoleView.Line line : lines) {
                if (!line.name().equals(row.name()) || shown >= 3) {
                    continue;
                }
                Component text = ConsoleOrders.ago(line.secondsAgo()).copy().append(" ")
                        .append(Component.translatable("console_alarm.projectrebirth." + ConsoleAlarm.byId(line.alarm()).id() + (line.ended() ? "_ended" : "")));
                UiText.line(graphics, text, left + 10, ly, 108, line.ended() ? UiColors.OK_TEXT : UiColors.ERROR_TEXT);
                ly += 11;
                shown++;
            }
        }
        if (shown == 0) {
            UiText.line(graphics, tr("journal_empty"), left + 10, ly, 108, UiColors.TEXT_DIM);
        }
        if (confirmUnlink) {
            UiText.line(graphics, tr("unlink_confirm"), left + 107, top + H - 31, 200, UiColors.ERROR);
        }
        for (Area area : areas) {
            if (mouseX >= area.x && mouseX < area.x + area.w && mouseY >= area.y && mouseY < area.y + area.h) {
                graphics.renderTooltip(font, area.stack, mouseX, mouseY);
                break;
            }
        }
        if (entity != null && mouseX >= mx && mouseX < mx + mw && mouseY >= my && mouseY < my + mh) {
            UiTooltip.of(tr("model_hint")).render(graphics, mouseX, mouseY);
        }
    }

    private void items(GuiGraphics graphics, List<ItemStack> stacks, int x, int y, int width, Component none) {
        if (stacks.isEmpty()) {
            UiText.line(graphics, none, x, y + 2, width, UiColors.TEXT_DIM);
            return;
        }
        int ix = x;
        for (ItemStack stack : stacks) {
            if (ix + 16 > x + width) {
                break;
            }
            graphics.renderItem(stack, ix, y);
            graphics.renderItemDecorations(font, stack, ix, y);
            areas.add(new Area(ix, y, 16, 16, stack));
            ix += 17;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (name.isFocused() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            String value = name.getValue().trim();
            if (!value.isEmpty()) {
                PacketDistributor.sendToServer(new ConsoleNetwork.RenamePayload(pos, id, value));
            }
            return true;
        }
        if (name.isFocused() && keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            return name.keyPressed(keyCode, scanCode, modifiers) || true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.popGuiLayer();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
