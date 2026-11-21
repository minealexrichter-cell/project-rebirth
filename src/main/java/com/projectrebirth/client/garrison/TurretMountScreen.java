package com.projectrebirth.client.garrison;

import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiEnergy;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSlots;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.client.ui.UiTooltip;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.TurretMountMenu;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The window of a turret mount in the style "Industrial terminal" (1.8.0, block E): the turret's circle of fire seen from above,
 * the nine slots of ammunition, the mount's energy, the turret's integrity and upgrades, and whether it can shoot now.
 */
public class TurretMountScreen extends UiMachineScreen<TurretMountMenu> {
    private static final int W = 320;
    private static final int H = TurretMountMenu.INVENTORY_Y + 82;
    private final UiEnergy energy = new UiEnergy();

    public TurretMountScreen(TurretMountMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "defence_turrets");
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.turret_mount." + key, args);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        energy.tick(menu.energy(), 0);
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int turret = menu.get(TurretMountMenu.DATA_TURRET);
        boolean rocket = turret == 2;
        int range = menu.get(TurretMountMenu.DATA_RANGE);
        // the circle of fire
        int sx = x + 7, sy = y + 25, sw = 150, sh = 96;
        UiPanels.screen(graphics, sx, sy, sw, sh);
        text(graphics, Component.literal("> ").append(UiText.upper(tr("sector"))), sx + 5, sy + 5, sw - 10, UiColors.AMBER_DIM);
        int cx = sx + sw / 2, cy = sy + 52;
        int radius = 32;
        for (int i = 0; i < 96; i += 2) {
            double angle = Math.PI * 2 * i / 96;
            int px = (int) Math.round(cx + Math.cos(angle) * radius);
            int py = (int) Math.round(cy + Math.sin(angle) * radius);
            graphics.fill(px, py, px + 1, py + 1, turret == 0 ? UiColors.TEXT_DIM : UiColors.AMBER);
        }
        graphics.fill(cx - radius, cy, cx + radius, cy + 1, 0x30FFB648);
        graphics.fill(cx, cy - radius, cx + 1, cy + radius, 0x30FFB648);
        if (turret > 0) {
            graphics.renderItem(new ItemStack(rocket ? GarrisonRegistry.ROCKET_TURRET_ITEM.get() : GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get()), cx - 8, cy - 8);
            text(graphics, tr("range", range), sx + 5, sy + sh - 12, sw - 10, UiColors.SCREEN_TEXT);
        } else {
            UiText.centred(graphics, tr("no_turret_short"), cx, cy - 4, sw - 20, UiColors.AMBER_DIM);
        }
        // ammunition and energy
        int rx = x + 163;
        UiPanels.label(graphics, tr("ammo"), x + TurretMountMenu.AMMO_X, y + 26, 56);
        for (int i = 0; i < 9; i++) {
            UiSlots.frame(graphics, UiSlots.Kind.INPUT, x + TurretMountMenu.AMMO_X + (i % 3) * 18, y + TurretMountMenu.AMMO_Y + (i / 3) * 18);
        }
        int ex = x + TurretMountMenu.AMMO_X + 60;
        int ew = x + W - 7 - ex;
        UiPanels.label(graphics, Component.translatable("screen.projectrebirth.station.label.energy"), ex, y + 26, ew);
        energy.draw(this, graphics, ex, y + 37, ew, 26, menu.energy(), menu.energyCapacity());
        int rounds = menu.get(TurretMountMenu.DATA_ROUNDS);
        int rockets = menu.get(TurretMountMenu.DATA_ROCKETS);
        text(graphics, tr("stock", rounds, rockets), x + TurretMountMenu.AMMO_X, y + 93, x + W - 7 - (x + TurretMountMenu.AMMO_X), UiColors.TEXT_SECONDARY);
        // the turret
        int rw = W - 7 - (rx - x);
        if (turret > 0) {
            float structural = menu.get(TurretMountMenu.DATA_MAX_STRUCTURAL) <= 0 ? 0
                    : menu.get(TurretMountMenu.DATA_STRUCTURAL) / (float) menu.get(TurretMountMenu.DATA_MAX_STRUCTURAL);
            UiBars.meter(graphics, null, rx + 7, y + 103, rw - 7, structural, 0xFF000000 | UiColors.integrity(structural),
                    Component.literal(Mth.ceil(menu.get(TurretMountMenu.DATA_STRUCTURAL) / 10.0f) + "/" + Mth.ceil(menu.get(TurretMountMenu.DATA_MAX_STRUCTURAL) / 10.0f)));
            text(graphics, tr(rocket ? "kind.rocket" : "kind.gun").copy().append(" · ").append(tr("upgrades", menu.get(TurretMountMenu.DATA_UPGRADES))),
                    rx + 7, y + 113, rw - 7, UiColors.TEXT_SECONDARY);
            tooltip(rx + 7, y + 101, rw - 7, 10, () -> UiTooltip.of(tr("integrity")).text(tr("integrity_hint"), UiColors.TEXT_SECONDARY));
        }
        // the state
        int perShot = menu.get(TurretMountMenu.DATA_PER_SHOT);
        UiSprites.Tone tone;
        String state;
        if (turret == 0) {
            tone = UiSprites.Tone.INFO;
            state = "no_turret";
        } else if (menu.energy() < Math.max(1, perShot)) {
            tone = UiSprites.Tone.WARNING;
            state = "no_energy";
        } else if ((rocket ? rockets : rounds) <= 0) {
            tone = UiSprites.Tone.WARNING;
            state = rocket ? "no_rockets" : "no_rounds";
        } else {
            tone = UiSprites.Tone.OK;
            state = "ready";
        }
        plate(graphics, tone, tr("state." + state), x + 7, y + 124, W - 14);
        inventory(graphics, x + TurretMountMenu.INVENTORY_X, y + TurretMountMenu.INVENTORY_Y);
        int hx = x + 176;
        int hy = y + TurretMountMenu.INVENTORY_Y - 11;
        int hw = W - 7 - 176;
        UiPanels.screen(graphics, hx, hy, hw, H - 7 - (TurretMountMenu.INVENTORY_Y - 11));
        UiText.wrapped(graphics, tr("help"), hx + 5, hy + 5, hw - 10, H - 7 - (TurretMountMenu.INVENTORY_Y - 11) - 10, UiColors.TEXT_SECONDARY);
    }

    @Override
    protected void drawOver(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (menu.getSlot(0).getItem().isEmpty() && menu.get(TurretMountMenu.DATA_TURRET) > 0) {
            boolean rocket = menu.get(TurretMountMenu.DATA_TURRET) == 2;
            UiSlots.ghost(graphics, new ItemStack(rocket ? GarrisonRegistry.ROCKET.get() : WeaponRegistry.ASSAULT_RIFLE_AMMO.get()),
                    x + TurretMountMenu.AMMO_X, y + TurretMountMenu.AMMO_Y);
        }
    }
}
