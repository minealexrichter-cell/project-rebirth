package com.projectrebirth.client.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.ArtificialBodyItem;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.BodyModificationBlockEntity;
import com.projectrebirth.technology.BodyModificationMenu;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeHost;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Body modification window: the packed body and its model on the left; on the right the gadgets with a button to
 * install each, and the installed ones with a button to take each out. The server checks everything again.
 */
public class BodyModificationScreen extends AbstractContainerScreen<BodyModificationMenu> {
    private static final int CELL = 18;
    private static final int BUTTON_HEIGHT = 10;
    private static final int INSTALL_BUTTON_Y = BodyModificationMenu.GADGET_Y + 17;
    private static final int INSTALLED_Y = INSTALL_BUTTON_Y + BUTTON_HEIGHT + 3;
    private static final int REMOVE_BUTTON_Y = INSTALLED_Y + 16 + 1;
    private static final int STATUS_X = BodyModificationMenu.GADGET_X - 1;
    private static final int STATUS_Y = REMOVE_BUTTON_Y + BUTTON_HEIGHT + 2;
    private static final int STATUS_WIDTH = 92;
    private static final int ENERGY_X = 156;
    private static final int ENERGY_W = 10;
    private static final int ENERGY_H = 92;

    private final List<Button> installButtons = new ArrayList<>();
    private final List<Button> removeButtons = new ArrayList<>();
    /** Only for drawing: never added to the world. */
    private final Map<BodyClass, ArtificialBodyEntity> models = new EnumMap<>(BodyClass.class);

    public BodyModificationScreen(BodyModificationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = BodyModificationMenu.INVENTORY_Y + 82;
        inventoryLabelY = BodyModificationMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        installButtons.clear();
        removeButtons.clear();
        for (int i = 0; i < BodyModificationBlockEntity.GADGET_SLOTS; i++) {
            int x = leftPos + BodyModificationMenu.GADGET_X - 1 + i * CELL;
            int install = i;
            installButtons.add(addRenderableWidget(Button.builder(Component.literal("+"), button -> click(install))
                    .bounds(x, topPos + INSTALL_BUTTON_Y, CELL, BUTTON_HEIGHT).build()));
            int remove = BodyModificationMenu.REMOVE_BUTTON + i;
            removeButtons.add(addRenderableWidget(Button.builder(Component.literal("−"), button -> click(remove))
                    .bounds(x, topPos + REMOVE_BUTTON_Y, CELL, BUTTON_HEIGHT).build()));
        }
        updateButtons();
    }

    private void click(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    private void updateButtons() {
        ItemStack body = menu.body();
        InstalledUpgrades installed = Upgrades.ofStack(body);
        for (int i = 0; i < BodyModificationBlockEntity.GADGET_SLOTS; i++) {
            Button install = installButtons.get(i);
            ItemStack gadget = menu.gadget(i);
            install.visible = !gadget.isEmpty();
            Upgrades.Problem problem = problemWith(body, gadget);
            install.active = !menu.working() && problem == null;
            install.setTooltip(Tooltip.create(Component.translatable(problem != null ? problem.translationKey() : "screen.projectrebirth.body_modification.install")));

            Button remove = removeButtons.get(i);
            remove.visible = i < installed.entries().size();
            remove.active = !menu.working();
            remove.setTooltip(Tooltip.create(Component.translatable("screen.projectrebirth.body_modification.remove")));
        }
    }

    private Upgrades.Problem problemWith(ItemStack body, ItemStack gadget) {
        UpgradeHost host = UpgradeHost.ofPacked(body);
        if (host == null) {
            return Upgrades.Problem.WRONG_HOST;
        }
        return gadget.isEmpty() ? null : Upgrades.problem(minecraft.level.registryAccess(), host, gadget, null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (GuiShapes.inside(mouseX, mouseY, leftPos + ENERGY_X, topPos + BodyModificationMenu.GADGET_Y, ENERGY_W, ENERGY_H)) {
            graphics.renderComponentTooltip(font, StationScreen.energyTooltip(menu.energy(), menu.energyCapacity(),
                    BodyModificationBlockEntity.energyPerTick()), mouseX, mouseY);
        }
        List<InstalledUpgrades.Installed> entries = Upgrades.ofStack(menu.body()).entries();
        for (int i = 0; i < Math.min(entries.size(), BodyModificationBlockEntity.GADGET_SLOTS); i++) {
            if (GuiShapes.inside(mouseX, mouseY, leftPos + BodyModificationMenu.GADGET_X + i * CELL, topPos + INSTALLED_Y, 16, 16)) {
                graphics.renderTooltip(font, entries.get(i).stack(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        GuiShapes.panel(graphics, x, y, imageWidth, imageHeight);
        GuiShapes.playerInventory(graphics, x + 8, y + BodyModificationMenu.INVENTORY_Y);
        GuiAtlas.outputSlot(graphics, x + BodyModificationMenu.BODY_X - 5, y + BodyModificationMenu.BODY_Y - 5);
        for (int i = 0; i < BodyModificationBlockEntity.GADGET_SLOTS; i++) {
            GuiShapes.slot(graphics, x + BodyModificationMenu.GADGET_X + i * CELL, y + BodyModificationMenu.GADGET_Y);
        }
        GuiAtlas.energyVertical(graphics, x + ENERGY_X, y + BodyModificationMenu.GADGET_Y, ENERGY_W, ENERGY_H, menu.energy(), menu.energyCapacity());
        renderInstalled(graphics, x, y);
        renderModel(graphics, x, y, mouseX, mouseY);
    }

    private void renderInstalled(GuiGraphics graphics, int x, int y) {
        ItemStack body = menu.body();
        if (!(body.getItem() instanceof ArtificialBodyItem item)) {
            return;
        }
        List<InstalledUpgrades.Installed> entries = Upgrades.ofStack(body).entries();
        int slots = RebirthConfig.bodyStats(item.bodyClass()).upgradeSlots().get();
        int cells = Math.min(BodyModificationBlockEntity.GADGET_SLOTS, Math.max(slots, entries.size()));
        for (int i = 0; i < cells; i++) {
            GuiShapes.slot(graphics, x + BodyModificationMenu.GADGET_X + i * CELL, y + INSTALLED_Y);
            if (i < entries.size()) {
                graphics.renderItem(entries.get(i).stack(), x + BodyModificationMenu.GADGET_X + i * CELL, y + INSTALLED_Y);
            }
        }
    }

    private void renderModel(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (menu.body().getItem() instanceof ArtificialBodyItem item) {
            ArtificialBodyEntity model = models.computeIfAbsent(item.bodyClass(), bodyClass -> BodyEntities.type(bodyClass).create(minecraft.level));
            if (model != null) {
                model.setUpgrades(Upgrades.ofStack(menu.body()));
                GuiAtlas.model(graphics, x + 8, y + 40, x + 58, y + 104, 26, 0.0625f, mouseX, mouseY, model);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        int lineY = STATUS_Y;
        for (FormattedCharSequence line : font.split(status(), STATUS_WIDTH)) {
            graphics.drawString(font, line, STATUS_X, lineY, 0x404040, false);
            lineY += font.lineHeight;
        }
    }

    private Component status() {
        if (!(menu.body().getItem() instanceof ArtificialBodyItem item)) {
            return Component.translatable("screen.projectrebirth.body_modification.empty");
        }
        if (menu.working()) {
            if (menu.emiPaused()) {
                return Component.translatable("screen.projectrebirth.station.emi");
            }
            if (menu.energy() < BodyModificationBlockEntity.energyPerTick()) {
                return Component.translatable("screen.projectrebirth.station.no_energy");
            }
            return Component.translatable("screen.projectrebirth.body_modification.installing", Mth.floor(menu.progress() * 100));
        }
        int installed = Upgrades.ofStack(menu.body()).entries().size();
        return Component.translatable("screen.projectrebirth.body_modification.slots", installed, RebirthConfig.bodyStats(item.bodyClass()).upgradeSlots().get());
    }
}
