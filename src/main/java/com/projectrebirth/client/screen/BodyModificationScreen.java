package com.projectrebirth.client.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.ArtificialBodyItem;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiButton;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiEnergy;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSlots;
import com.projectrebirth.client.ui.UiSounds;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.client.ui.UiTooltip;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.BodyModificationBlockEntity;
import com.projectrebirth.technology.BodyModificationMenu;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeHost;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Body modification window in the style "Industrial terminal" (1.8.0 block B): the packed body on the left; in the middle
 * the body's model with its gadget slots around it (an installed gadget with a "−" button to take it out); on the right the
 * gadgets to install, each with a "+" button that is grey with the reason when it cannot go in; energy, state, and a line
 * of the installing. The server checks every request again.
 */
public class BodyModificationScreen extends UiMachineScreen<BodyModificationMenu> {
    private static final int W = 380;
    private static final int H = 249;
    private static final int SCREEN_X = 44;
    private static final int SCREEN_Y = 36;
    private static final int SCREEN_W = 200;
    private static final int SCREEN_H = 96;
    private static final int RIGHT_X = 250;

    private final List<UiButton> installButtons = new ArrayList<>();
    private final List<UiButton> removeButtons = new ArrayList<>();
    /** Only for drawing: never added to the world. */
    private final Map<BodyClass, ArtificialBodyEntity> models = new EnumMap<>(BodyClass.class);
    private final UiEnergy energy = new UiEnergy();
    private final UiBars.Smooth progress = new UiBars.Smooth();
    private boolean wasWorking;

    public BodyModificationScreen(BodyModificationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "body_modification");
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.body_modification." + key, args);
    }

    /** Where the installed gadget i stands around the model: three on the left, two on the right (item position). */
    private int installedX(int i) {
        return leftPos + SCREEN_X + (i < 3 ? 10 : SCREEN_W - 28);
    }

    private int installedY(int i) {
        return topPos + SCREEN_Y + 10 + (i < 3 ? i : i - 3) * 28;
    }

    @Override
    protected void init() {
        super.init();
        installButtons.clear();
        removeButtons.clear();
        for (int i = 0; i < BodyModificationBlockEntity.GADGET_SLOTS; i++) {
            int install = i;
            installButtons.add(addRenderableWidget(new UiButton(leftPos + BodyModificationMenu.GADGET_X + i * BodyModificationMenu.GADGET_STEP - 1,
                    topPos + BodyModificationMenu.GADGET_Y + 18, 18, 10, Component.literal("+"), null, 0, UiButton.Look.METAL, () -> click(install))));
            int remove = BodyModificationMenu.REMOVE_BUTTON + i;
            removeButtons.add(addRenderableWidget(new UiButton(installedX(i) - 1, installedY(i) + 18, 18, 9, Component.literal("−"), null, 0,
                    UiButton.Look.METAL, () -> click(remove))));
        }
        updateButtons();
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
        boolean working = menu.working();
        energy.tick(menu.energy(), working && !menu.emiPaused() ? BodyModificationBlockEntity.energyPerTick() : 0);
        if (working != wasWorking) {
            (working ? UiSounds.WORK_START : UiSounds.WORK_END).play();
        }
        wasWorking = working;
    }

    private void updateButtons() {
        ItemStack body = menu.body();
        InstalledUpgrades installed = Upgrades.ofStack(body);
        for (int i = 0; i < BodyModificationBlockEntity.GADGET_SLOTS; i++) {
            UiButton install = installButtons.get(i);
            ItemStack gadget = menu.gadget(i);
            install.visible = !gadget.isEmpty();
            Upgrades.Problem problem = problemWith(body, gadget);
            install.disable(menu.working() ? tr("busy") : problem != null ? Component.translatable(problem.translationKey()) : null);
            if (install.active) {
                install.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("install")));
            }
            UiButton remove = removeButtons.get(i);
            remove.visible = i < installed.entries().size();
            remove.disable(menu.working() ? tr("busy") : null);
            if (remove.active) {
                remove.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("remove")));
            }
        }
    }

    private Upgrades.Problem problemWith(ItemStack body, ItemStack gadget) {
        UpgradeHost host = UpgradeHost.ofPacked(body);
        if (host == null) {
            return Upgrades.Problem.WRONG_HOST;
        }
        return gadget.isEmpty() || minecraft == null || minecraft.level == null ? null
                : Upgrades.problem(minecraft.level.registryAccess(), host, gadget, null);
    }

    private int slots() {
        return menu.body().getItem() instanceof ArtificialBodyItem item ? RebirthConfig.bodyStats(item.bodyClass()).upgradeSlots().get() : 0;
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        ItemStack body = menu.body();
        boolean hasBody = body.getItem() instanceof ArtificialBodyItem;
        // the body
        UiPanels.label(graphics, tr("label.body"), x + 8, y + 26, 34);
        UiSlots.frame(graphics, UiSlots.Kind.OUTPUT, x + BodyModificationMenu.BODY_X, y + BodyModificationMenu.BODY_Y);

        // the silhouette
        ArtificialBodyEntity model = null;
        if (body.getItem() instanceof ArtificialBodyItem item && minecraft != null) {
            model = models.computeIfAbsent(item.bodyClass(), bodyClass -> BodyEntities.type(bodyClass).create(minecraft.level));
            if (model != null) {
                model.setUpgrades(Upgrades.ofStack(body));
            }
        }
        Silhouette.draw(graphics, x + SCREEN_X, y + SCREEN_Y, SCREEN_W, SCREEN_H, model, 28, mouseX, mouseY);
        if (!hasBody) {
            UiText.wrapped(graphics, tr("empty"), x + SCREEN_X + 40, y + SCREEN_Y + 40, SCREEN_W - 80, 30, UiColors.AMBER_DIM);
        }
        List<InstalledUpgrades.Installed> entries = Upgrades.ofStack(body).entries();
        int cells = Math.min(BodyModificationBlockEntity.GADGET_SLOTS, Math.max(slots(), entries.size()));
        int cx = x + SCREEN_X + SCREEN_W / 2;
        for (int i = 0; i < cells; i++) {
            int sx = installedX(i);
            int sy = installedY(i);
            boolean left = i < 3;
            Silhouette.leader(graphics, left ? sx + 17 : sx - 2, sy + 8, left ? cx - 22 : cx + 22, sy + 8);
            UiSlots.frame(graphics, UiSlots.Kind.UPGRADE, sx, sy);
            if (i < entries.size()) {
                ItemStack gadget = entries.get(i).stack();
                graphics.renderItem(gadget, sx, sy);
                tooltip(sx, sy, 16, 16, () -> UiTooltip.of(gadget.getHoverName()).text(tr("installed"), UiColors.TEXT_SECONDARY));
            } else {
                UiSlots.ghostLabel(graphics, Component.literal("+"), sx, sy);
            }
        }

        // the gadgets to install
        int rx = x + RIGHT_X;
        UiPanels.label(graphics, tr("label.gadgets"), rx, y + 26, W - 7 - RIGHT_X);
        for (int i = 0; i < BodyModificationBlockEntity.GADGET_SLOTS; i++) {
            UiSlots.frame(graphics, UiSlots.Kind.INPUT, x + BodyModificationMenu.GADGET_X + i * BodyModificationMenu.GADGET_STEP, y + BodyModificationMenu.GADGET_Y);
        }

        // energy and state
        UiPanels.label(graphics, Component.translatable("screen.projectrebirth.station.label.energy"), rx, y + 72, W - 7 - RIGHT_X);
        energy.draw(this, graphics, rx, y + 82, W - 7 - RIGHT_X, 32, menu.energy(), menu.energyCapacity());
        UiSprites.Tone tone;
        Component badge;
        Component plate;
        if (!hasBody) {
            tone = UiSprites.Tone.INFO;
            badge = tr("state.idle");
            plate = tr("empty");
        } else if (menu.working() && menu.emiPaused()) {
            tone = UiSprites.Tone.WARNING;
            badge = Component.translatable("screen.projectrebirth.station.status.emi");
            plate = Component.translatable("screen.projectrebirth.station.plate.emi");
        } else if (menu.working() && menu.energy() < BodyModificationBlockEntity.energyPerTick()) {
            tone = UiSprites.Tone.WARNING;
            badge = Component.translatable("screen.projectrebirth.station.status.no_energy");
            plate = Component.translatable("screen.projectrebirth.station.plate.no_energy");
        } else if (menu.working()) {
            tone = UiSprites.Tone.OK;
            badge = tr("state.installing");
            plate = tr("installing", Mth.floor(menu.progress() * 100));
        } else {
            tone = UiSprites.Tone.INFO;
            badge = tr("state.ready");
            plate = tr("slots", entries.size(), slots());
        }
        badge(graphics, tone, badge, rx, y + 120, W - 7 - RIGHT_X);
        plate(graphics, tone == UiSprites.Tone.OK ? UiSprites.Tone.INFO : tone, plate, x + 7, y + 137, W - 14);
        if (menu.working()) {
            UiBars.progress(graphics, x + SCREEN_X + 6, y + SCREEN_Y + SCREEN_H - 7, SCREEN_W - 12, 4, progress.follow(menu.progress()), UiColors.AMBER);
        }

        // inventory and help
        inventory(graphics, x + BodyModificationMenu.INVENTORY_X, y + BodyModificationMenu.INVENTORY_Y);
        int ix = x + 176;
        int iy = y + BodyModificationMenu.INVENTORY_Y - 11;
        int iw = W - 7 - 176;
        UiPanels.screen(graphics, ix, iy, iw, H - 7 - (BodyModificationMenu.INVENTORY_Y - 11));
        text(graphics, UiText.upper(hasBody ? body.getHoverName() : title), ix + 5, iy + 5, iw - 10, UiColors.AMBER);
        if (hasBody) {
            text(graphics, tr("slots", entries.size(), slots()), ix + 5, iy + 17, iw - 10, UiColors.SCREEN_TEXT);
        }
        UiText.wrapped(graphics, tr("help"), ix + 5, iy + (hasBody ? 29 : 17), iw - 10, hasBody ? 54 : 66, UiColors.TEXT_SECONDARY);
    }

    @Override
    protected void drawOver(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (menu.body().isEmpty()) {
            UiSlots.ghostLabel(graphics, tr("ghost"), x + BodyModificationMenu.BODY_X, y + BodyModificationMenu.BODY_Y);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
