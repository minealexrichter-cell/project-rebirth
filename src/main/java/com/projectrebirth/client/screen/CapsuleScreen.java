package com.projectrebirth.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.bodies.CapsuleNetwork;
import com.projectrebirth.bodies.CapsuleNetwork.Availability;
import com.projectrebirth.bodies.CapsuleNetwork.Entry;
import com.projectrebirth.client.TransferAnimation;
import com.projectrebirth.client.codex.CodexScreen;
import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiButton;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiDropdown;
import com.projectrebirth.client.ui.UiList;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSounds;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Capsule window in the style "Industrial terminal" (1.8.0 block B): the player's bodies as cards (class, integrity, charge,
 * wear, where it is and its state) with a filter and a sort; the chosen body on the right with its model and scales, and
 * the "Transfer" button, which asks again for a body far away. It only shows what the server sent; the server checks
 * every request again.
 */
public class CapsuleScreen extends Screen {
    private static final int W = 400;
    private static final int H = 232;
    private static final int LIST_W = 240;
    private static final int ROW = 34;
    /** A body this far or farther needs a second press of "Transfer". */
    private static final int CONFIRM_DISTANCE = 100;
    private static final long CONFIRM_MS = 4000L;

    private enum Sort { DISTANCE, INTEGRITY, CLASS }

    private static boolean availableOnly;
    private static Sort sort = Sort.DISTANCE;

    private final UiText.Hints hints = new UiText.Hints();
    private final Map<BodyClass, ArtificialBodyEntity> models = new EnumMap<>(BodyClass.class);
    private BlockPos capsule;
    private int radius;
    private List<Entry> bodies;
    @Nullable
    private UUID chosen;
    private long confirmUntil;
    private int left;
    private int top;
    private UiButton action;
    private UiDropdown<Sort> sortBox;

    public CapsuleScreen(CapsuleNetwork.OpenPayload data) {
        super(Component.translatable("screen.projectrebirth.capsule.title"));
        update(data);
    }

    public BlockPos capsule() {
        return capsule;
    }

    /** New data from the server (after a failed request or a new opening). */
    public void update(CapsuleNetwork.OpenPayload data) {
        capsule = data.capsule();
        radius = data.radius();
        bodies = data.bodies();
        if (chosen == null || bodies.stream().noneMatch(entry -> entry.id().equals(chosen))) {
            List<Entry> shown = shown();
            chosen = shown.isEmpty() ? null : shown.get(0).id();
        }
        confirmUntil = 0L;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.capsule." + key, args);
    }

    private List<Entry> shown() {
        List<Entry> list = new ArrayList<>();
        for (Entry entry : bodies) {
            if (!availableOnly || entry.availability() == Availability.AVAILABLE) {
                list.add(entry);
            }
        }
        Comparator<Entry> order = switch (sort) {
            case DISTANCE -> Comparator.comparingInt(entry -> entry.distance() < 0 ? Integer.MAX_VALUE : entry.distance());
            case INTEGRITY -> Comparator.comparingDouble(entry -> -(entry.maxHealth() <= 0 ? 0 : entry.health() / entry.maxHealth()));
            case CLASS -> Comparator.comparingInt(entry -> -entry.bodyClass().ordinal());
        };
        list.sort(order);
        return list;
    }

    @Nullable
    private Entry chosenEntry() {
        for (Entry entry : bodies) {
            if (entry.id().equals(chosen)) {
                return entry;
            }
        }
        return null;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(UiButton.icon(left + W - 37, top + 4, 13, UiSprites.ICON_HELP, 7, Component.translatable("screen.projectrebirth.ui.help"),
                () -> minecraft.setScreen(CodexScreen.openFrom(this, "consciousness"))));
        addRenderableWidget(UiButton.icon(left + W - 21, top + 4, 13, UiSprites.ICON_CLOSE, 7, Component.translatable("screen.projectrebirth.ui.close"), this::onClose));
        addRenderableWidget(new UiButton(left + 7, top + 25, 50, 11, tr("filter.all"), null, 0, UiButton.Look.CHIP, () -> availableOnly = false)
                .selected(() -> !availableOnly));
        addRenderableWidget(new UiButton(left + 60, top + 25, 74, 11, tr("filter.available"), UiSprites.ICON_CHECK, 7, UiButton.Look.CHIP,
                () -> availableOnly = true).selected(() -> availableOnly));
        addRenderableWidget(new UiList<>(left + 7, top + 40, LIST_W, 2 + 5 * ROW + 2, ROW, this::shown, this::drawRow,
                this::chosenEntry, entry -> {
                    chosen = entry.id();
                    confirmUntil = 0L;
                }));
        action = addRenderableWidget(new UiButton(left + LIST_W + 13, top + 194, W - LIST_W - 20, 16, tr("transfer"), null, 0,
                UiButton.Look.METAL, this::act));
        sortBox = addRenderableWidget(new UiDropdown<>(left + 140, top + 25, LIST_W - 133, List.of(Sort.values()),
                value -> tr("sort." + value.name().toLowerCase(java.util.Locale.ROOT)), () -> sort, value -> sort = value));
    }

    private void act() {
        Entry entry = chosenEntry();
        if (entry == null) {
            return;
        }
        if (entry.availability() == Availability.DESTROYED) {
            PacketDistributor.sendToServer(new CapsuleNetwork.ActionPayload(capsule, entry.id(), true));
            return;
        }
        if (entry.distance() >= CONFIRM_DISTANCE && System.currentTimeMillis() > confirmUntil) {
            confirmUntil = System.currentTimeMillis() + CONFIRM_MS;
            UiSounds.ERROR.play();
            return;
        }
        confirmUntil = 0L;
        CapsuleNetwork.ActionPayload request = new CapsuleNetwork.ActionPayload(capsule, entry.id(), false);
        TransferAnimation.requestTransfer(() -> PacketDistributor.sendToServer(request));
    }

    @Override
    public void tick() {
        Entry entry = chosenEntry();
        boolean confirming = System.currentTimeMillis() <= confirmUntil;
        if (entry == null) {
            action.visible = false;
            return;
        }
        action.visible = true;
        if (entry.availability() == Availability.DESTROYED) {
            action.setMessage(tr("forget"));
            action.disable(null);
            action.color(UiColors.TEXT);
        } else {
            action.setMessage(confirming ? tr("confirm", entry.distance()) : tr("transfer"));
            action.color(confirming ? UiColors.AMBER_LIGHT : UiColors.TEXT);
            action.disable(entry.availability() == Availability.AVAILABLE ? null : status(entry));
        }
    }

    // ---------------------------------------------------------------- drawing

    private void drawRow(GuiGraphics graphics, Entry entry, int x, int y, int w, int h, boolean selected, boolean hovered) {
        UiPanels.card(graphics, x, y, w, h - 2, selected);
        graphics.renderItem(new ItemStack(BodyItems.item(entry.bodyClass())), x + 4, y + 8);
        int tx = x + 24;
        hints.line(graphics, Component.translatable(entry.bodyClass().translationKey()), tx, y + 3, w - 100, selected ? UiColors.AMBER_LIGHT : UiColors.TEXT);
        hints.line(graphics, where(entry), tx, y + 13, w - 100, UiColors.TEXT_SECONDARY);
        float integrity = entry.maxHealth() <= 0 ? 0.0f : entry.health() / entry.maxHealth();
        UiBars.horizontal(graphics, tx, y + 23, w - 100, 6, integrity, UiColors.integrity(integrity));
        // the state on the right, a word with its dot
        UiSprites.Tone tone = tone(entry);
        UiSprites.draw(graphics, tone.dot, x + w - 72, y + 5, 5, 5);
        hints.line(graphics, status(entry), x + w - 64, y + 3, 62, tone.text);
        if (entry.charge() >= 0.0f) {
            hints.line(graphics, tr("charge_short", Math.round(entry.charge() * 100)), x + w - 72, y + 13, 70, UiColors.ENERGY_TEXT);
        }
        if (entry.wear() >= 0.0f) {
            hints.line(graphics, tr("wear_short", Math.round(entry.wear())), x + w - 72, y + 22, 70, UiColors.WEAR);
        }
    }

    private Component where(Entry entry) {
        return entry.distance() >= 0
                ? tr("distance", entry.distance())
                : tr("elsewhere", dimensionName(entry.dimension()), entry.pos().getX(), entry.pos().getY(), entry.pos().getZ());
    }

    private static Component dimensionName(String id) {
        return switch (id) {
            case "minecraft:overworld" -> tr("dimension.overworld");
            case "minecraft:the_nether" -> tr("dimension.nether");
            case "minecraft:the_end" -> tr("dimension.end");
            default -> Component.literal(id);
        };
    }

    private Component status(Entry entry) {
        return switch (entry.availability()) {
            case AVAILABLE -> tr("state.available");
            case TOO_FAR -> tr("state.too_far", radius);
            case OTHER_DIMENSION -> tr("state.other_dimension");
            case OCCUPIED -> tr("state.occupied");
            case DESTROYED -> tr("state.destroyed");
        };
    }

    private static UiSprites.Tone tone(Entry entry) {
        return switch (entry.availability()) {
            case AVAILABLE -> UiSprites.Tone.OK;
            case TOO_FAR, OTHER_DIMENSION -> UiSprites.Tone.WARNING;
            case OCCUPIED -> UiSprites.Tone.INFO;
            case DESTROYED -> UiSprites.Tone.ERROR;
        };
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiPanels.window(graphics, left, top, W, H);
        if (UiPanels.title(graphics, left, top, W, title, UiSprites.ICON_STATION, left + W - 42)) {
            hints.add(left + 23, top + 5, W - 70, 11, title);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hints.clear();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (bodies.isEmpty()) {
            UiText.wrapped(graphics, tr("empty"), left + 14, top + 48, LIST_W - 14, 40, UiColors.AMBER_DIM);
        } else if (shown().isEmpty()) {
            UiText.wrapped(graphics, tr("none_available"), left + 14, top + 48, LIST_W - 14, 40, UiColors.AMBER_DIM);
        }
        drawChosen(graphics, mouseX, mouseY);
        int footY = top + H - 18;
        hints.line(graphics, tr("range", radius), left + 7, footY, 120, UiColors.TEXT_SECONDARY);
        hints.line(graphics, tr("androids"), left + 130, footY, W - 137, UiColors.TEXT_DIM);
        if (sortBox != null && sortBox.isOpen()) {
            return;
        }
        Component hint = hints.at(mouseX, mouseY);
        if (hint != null) {
            graphics.renderTooltip(font, hint, mouseX, mouseY);
        }
    }

    private void drawChosen(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + LIST_W + 13;
        int y = top + 40;
        int w = W - LIST_W - 20;
        UiPanels.screen(graphics, x, y, w, 150);
        Entry entry = chosenEntry();
        if (entry == null) {
            return;
        }
        ArtificialBodyEntity model = models.computeIfAbsent(entry.bodyClass(), bodyClass -> BodyEntities.type(bodyClass).create(minecraft.level));
        if (model != null) {
            com.projectrebirth.client.GuiAtlas.model(graphics, x + 20, y + 4, x + w - 20, y + 76, 30, 0.0625f, mouseX, mouseY, model);
        }
        hints.line(graphics, UiText.upper(Component.translatable(entry.bodyClass().translationKey())), x + 5, y + 80, w - 10, UiColors.AMBER);
        float integrity = entry.maxHealth() <= 0 ? 0.0f : entry.health() / entry.maxHealth();
        UiBars.meter(graphics, null, x + 5, y + 92, w - 10, integrity, UiColors.integrity(integrity), Component.literal(Mth.ceil(entry.health()) + "/" + Mth.ceil(entry.maxHealth())));
        if (entry.charge() >= 0.0f) {
            UiBars.meter(graphics, null, x + 5, y + 104, w - 10, entry.charge(), UiColors.ENERGY, Component.literal(Math.round(entry.charge() * 100) + "%"));
        } else {
            hints.line(graphics, tr("charge_unknown"), x + 5, y + 105, w - 10, UiColors.TEXT_DIM);
        }
        if (entry.wear() >= 0.0f) {
            UiBars.meter(graphics, null, x + 5, y + 116, w - 10, entry.wear() / 100.0f, UiColors.WEAR, Component.literal(Math.round(entry.wear()) + "%"));
        }
        hints.line(graphics, where(entry), x + 5, y + 130, w - 10, UiColors.TEXT_SECONDARY);
        hints.add(x + 5, y + 92, w - 10, 34, tr("scales"));
        if (System.currentTimeMillis() <= confirmUntil) {
            UiPanels.plate(graphics, UiSprites.Tone.WARNING, tr("confirm_hint"), x, y + 136, w);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sortBox != null) {
            if (sortBox.isOpen() && sortBox.isMouseOver(mouseX, mouseY)) {
                return sortBox.mouseClicked(mouseX, mouseY, button);
            }
            sortBox.closeIfOutside(mouseX, mouseY);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
