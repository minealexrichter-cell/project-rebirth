package com.projectrebirth.client.screen;

import java.util.List;

import com.projectrebirth.bodies.CapsuleNetwork;
import com.projectrebirth.bodies.CapsuleNetwork.Availability;
import com.projectrebirth.bodies.CapsuleNetwork.Entry;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import com.projectrebirth.client.TransferAnimation;

/**
 * Capsule window: the player's bodies with class, integrity, distance and state, and a transfer button for each.
 * It only shows what the server sent; the server checks every request again.
 */
public class CapsuleScreen extends Screen {
    private static final int WIDTH = 340;
    private static final int ROW_HEIGHT = 36;
    private static final int VISIBLE_ROWS = 5;
    private static final int LIST_TOP = 24;
    private static final int BUTTON_WIDTH = 86;

    private BlockPos capsule;
    private int radius;
    private List<Entry> bodies;
    private int scroll;
    private int left;
    private int top;

    public CapsuleScreen(CapsuleNetwork.OpenPayload data) {
        super(Component.translatable("screen.projectrebirth.capsule.title"));
        update(data);
    }

    public BlockPos capsule() {
        return capsule;
    }

    /** New data from the server (after a failed request or a new opening): rebuild the rows. */
    public void update(CapsuleNetwork.OpenPayload data) {
        capsule = data.capsule();
        radius = data.radius();
        bodies = data.bodies();
        scroll = Mth.clamp(scroll, 0, maxScroll());
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private int maxScroll() {
        return Math.max(0, bodies.size() - VISIBLE_ROWS);
    }

    private int panelHeight() {
        return LIST_TOP + Math.max(1, Math.min(VISIBLE_ROWS, bodies.size())) * ROW_HEIGHT + 22;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - panelHeight()) / 2;
        for (int i = 0; i < Math.min(VISIBLE_ROWS, bodies.size() - scroll); i++) {
            Entry entry = bodies.get(scroll + i);
            int y = top + LIST_TOP + i * ROW_HEIGHT + 7;
            Button button;
            if (entry.availability() == Availability.DESTROYED) {
                button = Button.builder(Component.translatable("screen.projectrebirth.capsule.forget"),
                        b -> send(entry, true)).bounds(left + WIDTH - BUTTON_WIDTH - 8, y, BUTTON_WIDTH, 20).build();
            } else {
                button = Button.builder(Component.translatable("screen.projectrebirth.capsule.transfer"),
                        b -> send(entry, false)).bounds(left + WIDTH - BUTTON_WIDTH - 8, y, BUTTON_WIDTH, 20).build();
                button.active = entry.availability() == Availability.AVAILABLE;
                if (!button.active) {
                    button.setTooltip(Tooltip.create(status(entry)));
                }
            }
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + WIDTH / 2 - 40, top + panelHeight() - 22, 80, 18).build());
    }

    private void send(Entry entry, boolean forget) {
        CapsuleNetwork.ActionPayload action = new CapsuleNetwork.ActionPayload(capsule, entry.id(), forget);
        if (forget) {
            PacketDistributor.sendToServer(action);
        } else {
            TransferAnimation.requestTransfer(() -> PacketDistributor.sendToServer(action));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int newScroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        if (newScroll != scroll) {
            scroll = newScroll;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GuiShapes.panel(graphics, left, top, WIDTH, panelHeight());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, left + 8, top + 7, 0x404040, false);
        Component range = Component.translatable("screen.projectrebirth.capsule.range", radius);
        graphics.drawString(font, range, left + WIDTH - 8 - font.width(range), top + 7, 0x606060, false);
        if (bodies.isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("screen.projectrebirth.capsule.empty"),
                    left + 8, top + LIST_TOP + 4, WIDTH - 16, 0x404040);
            return;
        }
        for (int i = 0; i < Math.min(VISIBLE_ROWS, bodies.size() - scroll); i++) {
            Entry entry = bodies.get(scroll + i);
            int y = top + LIST_TOP + i * ROW_HEIGHT;
            GuiShapes.sunken(graphics, left + 6, y, WIDTH - 12, ROW_HEIGHT - 1);
            int textX = left + 10;
            String name = Component.translatable(entry.bodyClass().translationKey()).getString();
            int nameWidth = WIDTH - BUTTON_WIDTH - 26;
            if (font.width(name) > nameWidth) {
                name = font.plainSubstrByWidth(name, nameWidth - font.width("…")) + "…";
            }
            graphics.drawString(font, name, textX, y + 4, 0xFFFFFF, true);
            graphics.drawString(font, details(entry), textX, y + 14, 0xE0E0E0, true);
            graphics.drawString(font, status(entry), textX, y + 24, statusColor(entry), true);
        }
        if (maxScroll() > 0) {
            Component more = Component.literal((scroll + 1) + "–" + Math.min(bodies.size(), scroll + VISIBLE_ROWS) + " / " + bodies.size());
            graphics.drawString(font, more, left + 8, top + panelHeight() - 17, 0x606060, false);
        }
    }

    private Component details(Entry entry) {
        Component integrity = Component.translatable("screen.projectrebirth.capsule.integrity",
                Mth.ceil(entry.health()), Mth.ceil(entry.maxHealth()));
        Component where = entry.distance() >= 0
                ? Component.translatable("screen.projectrebirth.capsule.distance", entry.distance())
                : Component.translatable("screen.projectrebirth.capsule.elsewhere", dimensionName(entry.dimension()),
                        entry.pos().getX(), entry.pos().getY(), entry.pos().getZ());
        return Component.empty().append(integrity).append("   ").append(where);
    }

    private static Component dimensionName(String id) {
        return switch (id) {
            case "minecraft:overworld" -> Component.translatable("screen.projectrebirth.capsule.dimension.overworld");
            case "minecraft:the_nether" -> Component.translatable("screen.projectrebirth.capsule.dimension.nether");
            case "minecraft:the_end" -> Component.translatable("screen.projectrebirth.capsule.dimension.end");
            default -> Component.literal(id);
        };
    }

    private Component status(Entry entry) {
        return switch (entry.availability()) {
            case AVAILABLE -> Component.translatable("screen.projectrebirth.capsule.state.available");
            case TOO_FAR -> Component.translatable("screen.projectrebirth.capsule.state.too_far", radius);
            case OTHER_DIMENSION -> Component.translatable("screen.projectrebirth.capsule.state.other_dimension");
            case OCCUPIED -> Component.translatable("screen.projectrebirth.capsule.state.occupied");
            case DESTROYED -> Component.translatable("screen.projectrebirth.capsule.state.destroyed");
        };
    }

    private static int statusColor(Entry entry) {
        return switch (entry.availability()) {
            case AVAILABLE -> 0x55FF55;
            case TOO_FAR, OTHER_DIMENSION -> 0xFFAA00;
            case OCCUPIED -> 0xFFFF55;
            case DESTROYED -> 0xFF5555;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
