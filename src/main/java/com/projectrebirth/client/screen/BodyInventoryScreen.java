package com.projectrebirth.client.screen;

import java.util.List;

import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeDefinition;
import com.projectrebirth.upgrades.UpgradeRegistry;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The inventory of a player in an artificial body. It is the vanilla inventory (slots, recipe book, effects) with the
 * armor slots closed (see BodyInventory) and, in their place, the body: its upgrade slots around the model, and above
 * the window its class, integrity and how many upgrade slots are taken.
 */
public class BodyInventoryScreen extends InventoryScreen {
    /** Crafting arrow, between the crafting grid and its result. */
    public static final int ARROW_X = 136;
    public static final int ARROW_Y = 31;
    public static final int ARROW_WIDTH = 16;
    public static final int ARROW_HEIGHT = 11;

    /** How far the panel reaches above the vanilla window. It leaves the window where the recipe book expects it. */
    private static final int HEADER = 26;
    private static final int SLOT_SIZE = 18;
    /** Upgrade slots stand on both sides of the model, where armor slots used to be. */
    private static final int[][] CELLS = {{8, 8}, {8, 26}, {8, 44}, {8, 62}, {77, 8}, {77, 26}, {77, 44}};

    private final Player player;

    public BodyInventoryScreen(Player player) {
        super(player);
        this.player = player;
    }

    /** Replaces the vanilla inventory while in a body, except in creative mode, which has its own window. */
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getNewScreen() != null && event.getNewScreen().getClass() == InventoryScreen.class
                && minecraft.player != null && minecraft.gameMode != null && !minecraft.gameMode.hasInfiniteItems()
                && TransferService.isInArtificialBody(minecraft.player)) {
            event.setNewScreen(new BodyInventoryScreen(minecraft.player));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        GuiAtlas.panel(graphics, x, y - HEADER, imageWidth, imageHeight + HEADER);
        renderHeader(graphics, x, y);
        renderSlotFrames(graphics, x, y);
        GuiAtlas.smallArrow(graphics, x + ARROW_X, y + ARROW_Y);
        renderUpgradeCells(graphics, x, y);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + 26, y + 8, x + 75, y + 78, 30, 0.0625f, mouseX, mouseY, player);
    }

    private void renderHeader(GuiGraphics graphics, int x, int y) {
        Component name = TransferService.state(player).body().map(BodyClass::translationKey).map(Component::translatable)
                .map(Component.class::cast).orElse(Component.empty());
        graphics.drawString(font, name, x + 8, y - HEADER + 5, 0x404040, false);

        float max = player.getMaxHealth();
        float fraction = max > 0 ? Mth.clamp(player.getHealth() / max, 0.0f, 1.0f) : 0.0f;
        int color = fraction > 0.5f ? 0xFF4FC3F7 : fraction > 0.25f ? 0xFFFFC107 : 0xFFE53935;
        int barWidth = 88;
        int barY = y - 11;
        GuiAtlas.bar(graphics, x + 8, barY, barWidth, 9, fraction, color);
        String integrity = Mth.ceil(player.getHealth()) + " / " + Mth.ceil(max);
        graphics.drawString(font, integrity, x + 8 + (barWidth - font.width(integrity)) / 2, barY + 1, 0xFFFFFF, true);

        Component slots = Component.translatable("screen.projectrebirth.body.slots", usedSlots(), totalSlots());
        graphics.drawString(font, slots, x + imageWidth - 8 - font.width(slots), barY + 1, 0x404040, false);
    }

    private static void renderSlotFrames(GuiGraphics graphics, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                GuiAtlas.slot(graphics, x + 7 + col * SLOT_SIZE, y + 83 + row * SLOT_SIZE);
            }
        }
        for (int col = 0; col < 9; col++) {
            GuiAtlas.slot(graphics, x + 7 + col * SLOT_SIZE, y + 141);
        }
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                GuiAtlas.slot(graphics, x + 97 + col * SLOT_SIZE, y + 17 + row * SLOT_SIZE);
            }
        }
        GuiAtlas.slot(graphics, x + 153, y + 27);
        GuiAtlas.slot(graphics, x + 76, y + 61);
    }

    private void renderUpgradeCells(GuiGraphics graphics, int x, int y) {
        List<InstalledUpgrades.Installed> entries = upgrades().entries();
        int cells = Math.min(CELLS.length, entries.size() + Math.max(0, totalSlots() - usedSlots()));
        for (int i = 0; i < cells; i++) {
            GuiAtlas.slot(graphics, x + CELLS[i][0] - 1, y + CELLS[i][1] - 1);
            if (i < entries.size()) {
                graphics.renderItem(entries.get(i).stack(), x + CELLS[i][0], y + CELLS[i][1]);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (getRecipeBookComponent().isVisible() && width < 379) {
            return;
        }
        List<InstalledUpgrades.Installed> entries = upgrades().entries();
        int cells = Math.min(CELLS.length, entries.size() + Math.max(0, totalSlots() - usedSlots()));
        for (int i = 0; i < cells; i++) {
            if (GuiShapes.inside(mouseX, mouseY, leftPos + CELLS[i][0], topPos + CELLS[i][1], 16, 16)) {
                if (i < entries.size()) {
                    graphics.renderTooltip(font, entries.get(i).stack(), mouseX, mouseY);
                } else {
                    graphics.renderTooltip(font, Component.translatable("screen.projectrebirth.body.free_slot"), mouseX, mouseY);
                }
            }
        }
    }

    private InstalledUpgrades upgrades() {
        return player.getData(UpgradeRegistry.CONTROLLED_BODY);
    }

    private int totalSlots() {
        return TransferService.state(player).body().map(bodyClass -> RebirthConfig.bodyStats(bodyClass).upgradeSlots().get()).orElse(0);
    }

    private int usedSlots() {
        int used = 0;
        for (InstalledUpgrades.Installed entry : upgrades().entries()) {
            used += Upgrades.definition(player.level().registryAccess(), entry.stack())
                    .flatMap(UpgradeDefinition::body).map(UpgradeDefinition.BodyFit::slots).orElse(1);
        }
        return used;
    }
}
