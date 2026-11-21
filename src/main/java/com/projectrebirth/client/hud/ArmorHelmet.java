package com.projectrebirth.client.hud;

import java.util.ArrayList;
import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.client.Interference;
import com.projectrebirth.client.PowerArmorClient;
import com.projectrebirth.client.WeaponClient;
import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.config.RebirthClientConfig;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.TabletData;
import com.projectrebirth.garrison.TabletItem;
import com.projectrebirth.powerarmor.ArmorAbility;
import com.projectrebirth.powerarmor.ArmorUpgradeEffects;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.HelmetMarks;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.weapons.WeaponDefinition;
import com.projectrebirth.weapons.Weapons;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The helmet of a power armor (1.8.0 block C), shown while the player pilots one. The frame of the helmet at the edges by
 * the armor's kind (combat: heavy armored corners; mobile: narrow slanted bars; worker: the glass of the cab with marks),
 * thin and translucent, off in [ui] helmetFrame. Then the elements, each movable in the HUD editor: the compass at the top
 * with marks (own bodies, the console and guard point of a tablet), the hull (silhouette and the two scales), the weapon
 * (rounds as display digits, reserve, reload), the systems (floodlight, mining mode, ability, upgrades), warnings. Entering
 * starts the systems; a shut-down armor shows the emergency mode: a red frame and the countdown of the hatch key.
 */
public final class ArmorHelmet {
    private static final HudBoot BOOT = new HudBoot(List.of("hud.projectrebirth.boot.armor.pilot", "hud.projectrebirth.boot.armor.hydraulics",
            "hud.projectrebirth.boot.armor.weapons", "hud.projectrebirth.boot.armor.optics"));

    private ArmorHelmet() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ArmorHelmet::registerLayers);
        NeoForge.EVENT_BUS.addListener(ArmorHelmet::tick);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ProjectRebirth.id("armor_helmet_frame"), ArmorHelmet::renderFrame);
        event.registerAbove(VanillaGuiLayers.HOTBAR, ProjectRebirth.id("armor_helmet"), ArmorHelmet::renderElements);
    }

    private static PowerArmorEntity armor(Player player) {
        return player != null && player.getVehicle() instanceof PowerArmorEntity armor ? armor : null;
    }

    private static void tick(ClientTickEvent.Post event) {
        BOOT.tick(armor(Minecraft.getInstance().player) != null);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("hud.projectrebirth.helmet." + key, args);
    }

    private static boolean hidden(Minecraft minecraft) {
        return minecraft.options.hideGui || !com.projectrebirth.client.HudLayout.enabled();
    }

    // ---------------------------------------------------------------- frame and emergency

    private static void renderFrame(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        PowerArmorEntity armor = armor(minecraft.player);
        if (armor == null || hidden(minecraft)) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        if (RebirthClientConfig.UI_HELMET_FRAME.get()) {
            switch (armor.getVariant()) {
                case COMBAT -> combatFrame(graphics, w, h);
                case MOBILE -> mobileFrame(graphics, w, h);
                case WORKER -> workerFrame(graphics, w, h);
            }
        }
        if (armor.isDisabled()) {
            int alpha = HudDraw.blinkOn(500) ? 200 : 110;
            int red = UiColors.withAlpha(UiColors.ERROR, alpha);
            graphics.fill(0, 0, w, 3, red);
            graphics.fill(0, h - 3, w, h, red);
            graphics.fill(0, 3, 3, h - 3, red);
            graphics.fill(w - 3, 3, w, h - 3, red);
            graphics.fill(0, 0, w, h, 0x20FF2010);
        }
        BOOT.draw(graphics, UiColors.AMBER, UiColors.AMBER_DIM);
    }

    private static final int SHELL = 0xC70A0C0C;
    private static final int SHELL_LINE = 0x99FFB648;

    /** Combat: heavy dark corners top and bottom with an amber line. */
    private static void combatFrame(GuiGraphics graphics, int w, int h) {
        wedge(graphics, 0, 0, 70, 16, 26, true, true);
        wedge(graphics, w, 0, 70, 16, 26, false, true);
        wedge(graphics, 0, h, 56, 10, 20, true, false);
        wedge(graphics, w, h, 56, 10, 20, false, false);
        graphics.fill(14, 16, 56, 17, SHELL_LINE);
        graphics.fill(w - 56, 16, w - 14, 17, SHELL_LINE);
    }

    /** A dark corner piece: full height at the edge, sloping to the given depth. */
    private static void wedge(GuiGraphics graphics, int x, int y, int length, int depth, int edge, boolean left, boolean top) {
        for (int i = 0; i < length; i++) {
            int d = i < 14 ? edge - (edge - depth) * i / 14 : depth;
            if (i > length - 14) {
                d = depth * (length - i) / 14;
            }
            int px = left ? x + i : x - i - 1;
            int y0 = top ? y : y - d;
            graphics.fill(px, y0, px + 1, y0 + d, SHELL);
        }
    }

    /** Mobile: narrow slanted bars at the sides, like a visor slit. */
    private static void mobileFrame(GuiGraphics graphics, int w, int h) {
        for (int y = 0; y < h; y++) {
            int depth = 6 + Math.abs(y - h / 2) * 18 / Math.max(1, h / 2);
            graphics.fill(0, y, depth, y + 1, SHELL);
            graphics.fill(w - depth, y, w, y + 1, SHELL);
        }
        graphics.fill(0, 0, w, 6, SHELL);
        graphics.fill(0, h - 6, w, h, SHELL);
        graphics.fill(26, 6, w - 26, 7, SHELL_LINE);
    }

    /** Worker: the glass of the cab: thin frame lines and measuring marks, no dark parts. */
    private static void workerFrame(GuiGraphics graphics, int w, int h) {
        int line = 0x50E6F2F0;
        graphics.fill(4, 4, w - 4, 5, line);
        graphics.fill(4, h - 5, w - 4, h - 4, line);
        graphics.fill(4, 4, 5, h - 4, line);
        graphics.fill(w - 5, 4, w - 4, h - 4, line);
        for (int x = 20; x < w - 20; x += 20) {
            graphics.fill(x, 5, x + 1, 8, line);
            graphics.fill(x, h - 8, x + 1, h - 5, line);
        }
        graphics.fill(w / 2 - 30, h / 2, w / 2 - 20, h / 2 + 1, line);
        graphics.fill(w / 2 + 20, h / 2, w / 2 + 30, h / 2 + 1, line);
    }

    // ---------------------------------------------------------------- the elements

    private static void renderElements(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        PowerArmorEntity armor = armor(player);
        if (armor == null || hidden(minecraft) || !BOOT.elementsUp()) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        draw(graphics, HudElement.COMPASS, w, h, () -> compass(graphics, player));
        draw(graphics, HudElement.ARMOR_HULL, w, h, () -> hull(graphics, armor));
        draw(graphics, HudElement.ARMOR_SYSTEMS, w, h, () -> systems(graphics, player, armor));
        if (WeaponClient.holdsWeapon(player)) {
            draw(graphics, HudElement.ARMOR_WEAPON, w, h, () -> weapon(graphics, player, armor));
        }
        if (armor.isDisabled()) {
            emergency(graphics, armor, w, h);
        } else {
            Warnings.draw(graphics, warnings(armor), Warnings.Look.TERMINAL);
        }
    }

    private static void draw(GuiGraphics graphics, HudElement element, int w, int h, Runnable drawing) {
        if (!element.visible()) {
            return;
        }
        int[] at = element.position(w, h);
        graphics.pose().pushPose();
        graphics.pose().translate(at[0], at[1], 0.0f);
        graphics.pose().scale(element.scale(), element.scale(), 1.0f);
        drawing.run();
        graphics.pose().popPose();
    }

    static List<Component> warnings(PowerArmorEntity armor) {
        List<Component> list = new ArrayList<>();
        if (armor.getMaxCritical() > 0 && armor.getCritical() / armor.getMaxCritical() <= 0.25f) {
            list.add(tr("warning.critical"));
        }
        if (armor.getMaxStructural() > 0 && armor.getStructural() / armor.getMaxStructural() <= 0.25f) {
            list.add(tr("warning.structural"));
        }
        if (Interference.active()) {
            list.add(tr("warning.interference"));
        }
        return list;
    }

    /** The emergency mode: what happened and the countdown of holding the hatch key. */
    private static void emergency(GuiGraphics graphics, PowerArmorEntity armor, int w, int h) {
        Font font = HudDraw.font();
        int x = w / 2 - 110;
        int y = Math.round(h * 0.6f);
        HudDraw.panel(graphics, x, y, 220, 40);
        graphics.fill(x, y, x + 220, y + 1, UiColors.ERROR);
        UiSprites.draw(graphics, UiSprites.ICON_ALARM, x + 5, y + 5, 7, 7);
        UiText.line(graphics, tr("emergency.title"), x + 16, y + 4, 200, UiColors.ERROR_TEXT);
        float progress = armor.getHatchProgress();
        float seconds = RebirthConfig.ARMOR_HATCH_RELEASE_SECONDS.get() * (1.0f - progress);
        int kx = x + 5;
        kx += HudDraw.key(graphics, PowerArmorClient.hatchKey().getTranslatedKeyMessage(), kx, y + 16);
        UiText.line(graphics, progress > 0 ? tr("emergency.holding", String.format(java.util.Locale.ROOT, "%.1f", seconds))
                : tr("emergency.hint", RebirthConfig.ARMOR_HATCH_RELEASE_SECONDS.get()), kx, y + 16, x + 215 - kx, UiColors.TEXT);
        UiBars.progress(graphics, x + 5, y + 30, 210, 5, progress, UiColors.ERROR);
    }

    /** Hull: a silhouette coloured by the state, the kind, the two scales and a word of the state. */
    private static void hull(GuiGraphics graphics, PowerArmorEntity armor) {
        int width = HudElement.ARMOR_HULL.width;
        int height = HudElement.ARMOR_HULL.height;
        HudDraw.panel(graphics, 0, 0, width, height);
        float structural = armor.getMaxStructural() > 0 ? armor.getStructural() / armor.getMaxStructural() : 0.0f;
        float critical = armor.getMaxCritical() > 0 ? armor.getCritical() / armor.getMaxCritical() : 0.0f;
        int torso = armor.isDisabled() ? UiColors.ERROR : UiColors.integrity(critical);
        int limbs = armor.isDisabled() ? UiColors.ERROR : UiColors.integrity(structural);
        // the silhouette, 24 x 46
        int sx = 5;
        int sy = 6;
        graphics.fill(sx + 8, sy, sx + 16, sy + 7, torso);
        graphics.fill(sx + 10, sy + 3, sx + 14, sy + 5, 0xFF0E1311);
        graphics.fill(sx + 5, sy + 8, sx + 19, sy + 24, torso);
        graphics.fill(sx, sy + 9, sx + 4, sy + 24, limbs);
        graphics.fill(sx + 20, sy + 9, sx + 24, sy + 24, limbs);
        graphics.fill(sx + 6, sy + 25, sx + 11, sy + 44, limbs);
        graphics.fill(sx + 13, sy + 25, sx + 18, sy + 44, limbs);
        int tx = 34;
        int tw = width - tx - 4;
        UiText.line(graphics, Component.translatable(armor.getVariant().translationKey()), tx, 4, tw, UiColors.AMBER_LIGHT);
        scale(graphics, tx, 15, tw, tr("structural"), structural, armor.getStructural(), armor.getMaxStructural());
        scale(graphics, tx, 31, tw, tr("critical"), critical, armor.getCritical(), armor.getMaxCritical());
        Component state = armor.isDisabled() ? tr("state.disabled") : critical <= 0.25f ? tr("state.critical")
                : structural <= 0.25f ? tr("state.shutdown_near") : tr("state.ok");
        int stateColor = armor.isDisabled() || critical <= 0.25f ? UiColors.ERROR : structural <= 0.25f ? UiColors.WARNING : UiColors.OK;
        UiText.line(graphics, state, tx, 47, tw, stateColor);
    }

    private static void scale(GuiGraphics graphics, int x, int y, int width, Component name, float fraction, float value, float max) {
        Font font = HudDraw.font();
        String numbers = Interference.scramble(Mth.ceil(value) + " / " + Mth.ceil(max));
        UiText.line(graphics, name, x, y, width - font.width(numbers) - 4, UiColors.TEXT_SECONDARY);
        graphics.drawString(font, numbers, x + width - font.width(numbers), y, UiColors.TEXT, false);
        UiBars.horizontal(graphics, x, y + 9, width, 6, Interference.jitter(fraction), UiColors.integrity(fraction));
    }

    /** Systems: keys of the floodlight and the mining mode, the ability, the installed upgrades. */
    private static void systems(GuiGraphics graphics, Player player, PowerArmorEntity armor) {
        int width = HudElement.ARMOR_SYSTEMS.width;
        List<Runnable> rows = new ArrayList<>();
        Font font = HudDraw.font();
        int[] y = {14};
        if (ArmorUpgradeEffects.hasFloodlight(armor)) {
            boolean on = armor.isFloodlightOn() && armor.isActive();
            rows.add(() -> keyRow(graphics, PowerArmorClient.floodlightKey().getTranslatedKeyMessage(), tr(on ? "floodlight_on" : "floodlight_off"),
                    y[0], width, on ? UiColors.OK : UiColors.TEXT_SECONDARY));
        }
        if (armor.getVariant() == ArmorVariant.WORKER) {
            rows.add(() -> keyRow(graphics, PowerArmorClient.miningKey().getTranslatedKeyMessage(),
                    tr(armor.isWideMining() ? "mining_wide" : "mining_single"), y[0], width, UiColors.AMBER_LIGHT));
        }
        ItemStack ability = player.getInventory().getItem(ArmorAbility.SLOT);
        if (ArmorAbility.isAbility(ability)) {
            rows.add(() -> keyRow(graphics, Component.literal(String.valueOf(ArmorAbility.SLOT + 1)), ability.getHoverName(), y[0], width, UiColors.TEXT));
        }
        InstalledUpgrades upgrades = armor.upgrades();
        for (InstalledUpgrades.Installed entry : upgrades.entries()) {
            rows.add(() -> {
                UiSprites.draw(graphics, UiSprites.ICON_CHECK, 4, y[0] + 1, 7, 7);
                UiText.line(graphics, entry.stack().getHoverName(), 14, y[0], width - 18, UiColors.OK_TEXT);
            });
        }
        int height = 16 + rows.size() * 11;
        HudDraw.panel(graphics, 0, 0, width, height);
        UiText.line(graphics, UiText.upper(tr("systems")), 4, 3, width - 8, UiColors.AMBER);
        for (Runnable row : rows) {
            row.run();
            y[0] += 11;
        }
    }

    private static void keyRow(GuiGraphics graphics, Component key, Component text, int y, int width, int color) {
        int x = 4 + HudDraw.key(graphics, key, 4, y);
        UiText.line(graphics, text, x, y, width - x - 4, color);
    }

    /** Weapon: its name, rounds as big digits, the magazine as cells, the reserve, the reload, the keys. */
    private static void weapon(GuiGraphics graphics, Player player, PowerArmorEntity armor) {
        ItemStack stack = player.getMainHandItem();
        RebirthConfig.WeaponStats stats = Weapons.statsOf(stack.getItem());
        WeaponDefinition definition = Weapons.definition(player.registryAccess(), stack.getItem()).orElse(null);
        if (stats == null || definition == null) {
            return;
        }
        int width = HudElement.ARMOR_WEAPON.width;
        int height = HudElement.ARMOR_WEAPON.height;
        HudDraw.panel(graphics, 0, 0, width, height);
        Font font = HudDraw.font();
        UiText.line(graphics, UiText.upper(stack.getHoverName()), 4, 3, width - 8, UiColors.AMBER);
        int rounds = Weapons.ammoIn(stack);
        int magazine = stats.magazine().get();
        int reserve = player.getInventory().countItem(definition.ammo().value());
        Component big = Component.literal(Interference.scramble(String.valueOf(rounds)));
        UiText.scaled(graphics, big, 4, 13, 2.0f, rounds > 0 ? 0xFFFFE2B0 : UiColors.ERROR);
        int bigWidth = font.width(big) * 2;
        graphics.drawString(font, "/ " + magazine, 6 + bigWidth, 20, UiColors.TEXT_SECONDARY, false);
        String reserveText = String.valueOf(reserve);
        graphics.drawString(font, reserveText, width - 4 - font.width(reserveText), 13, reserve > 0 ? UiColors.TEXT : UiColors.ERROR, false);
        UiText.line(graphics, tr("reserve"), width - 50, 22, 46, UiColors.TEXT_DIM);
        // the magazine as cells (at most 30; a bigger magazine shows by thirtieths)
        int cells = Math.min(30, Math.max(1, magazine));
        int cellW = Math.max(1, (width - 8 - (cells - 1)) / cells);
        int filled = Math.round(cells * (magazine <= 0 ? 0.0f : rounds / (float) magazine));
        for (int i = 0; i < cells; i++) {
            int cx = 4 + i * (cellW + 1);
            graphics.fill(cx, 32, cx + cellW, 36, i < filled ? UiColors.AMBER : 0xFF4A3A1E);
        }
        long left = armor.weaponReloadEnd() - player.level().getGameTime();
        int x = 4;
        if (armor.weaponReloadEnd() > 0 && left > 0) {
            float done = 1.0f - Math.min(1.0f, left / (float) stats.reloadTicks().get());
            UiBars.progress(graphics, 4, 40, width - 8, 5, done, UiColors.AMBER);
        } else if (rounds == 0 && reserve > 0 && HudDraw.blinkOn(500)) {
            x += HudDraw.key(graphics, WeaponClient.reloadKey().getTranslatedKeyMessage(), x, 40);
            UiText.line(graphics, tr("reload"), x, 40, width - x - 4, UiColors.AMBER_LIGHT);
        } else {
            UiText.line(graphics, definition.ammo().value().getDescription(), 4, 40, width - 8, UiColors.TEXT_DIM);
        }
    }

    /** The compass: the directions around the view, ticks every 15 degrees, and the marks. */
    private static void compass(GuiGraphics graphics, Player player) {
        int width = HudElement.COMPASS.width;
        int height = HudElement.COMPASS.height;
        graphics.fill(0, 0, width, height, 0x990E1311);
        graphics.fill(0, height - 1, width, height, 0x8CFFB648);
        Font font = HudDraw.font();
        float yaw = Mth.wrapDegrees(player.getYRot());
        float span = 120.0f;
        int centre = width / 2;
        for (int deg = -180; deg < 180; deg += 15) {
            float off = Mth.wrapDegrees(deg - yaw);
            if (Math.abs(off) > span / 2) {
                continue;
            }
            int x = centre + Math.round(off / span * width);
            boolean cardinal = deg % 90 == 0;
            graphics.fill(x, height - (cardinal ? 5 : 3), x + 1, height - 1, cardinal ? UiColors.AMBER : 0x80FFB648);
            if (cardinal) {
                // Minecraft: yaw 0 looks south, 90 west, 180 north, -90 east
                String key = switch (deg) {
                    case 0 -> "south";
                    case 90 -> "west";
                    case -90 -> "east";
                    default -> "north";
                };
                Component letter = tr("compass." + key);
                graphics.drawString(font, letter, x - font.width(letter) / 2, 1, deg == 180 || deg == -180 ? UiColors.AMBER : 0xFFD9C9A8, false);
            }
        }
        for (Mark mark : marks(player)) {
            double dx = mark.x + 0.5 - player.getX();
            double dz = mark.z + 0.5 - player.getZ();
            float bearing = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
            float off = Mth.wrapDegrees(bearing - yaw);
            boolean edge = Math.abs(off) > span / 2;
            int x = centre + Math.round(Mth.clamp(off, -span / 2, span / 2) / span * width);
            graphics.fill(x - 2, 5, x + 2, 9, mark.color);
            if (!edge && Math.abs(off) < span / 4) {
                int distance = (int) Math.sqrt(dx * dx + dz * dz);
                String text = mark.label.getString() + " " + (distance >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fк", distance / 1000.0f) : String.valueOf(distance));
                float scale = 0.75f;
                UiText.scaled(graphics, Component.literal(text), x + 4, 4, scale, mark.color);
            }
        }
        graphics.fill(centre, 0, centre + 1, height, 0xFFFFE2B0);
    }

    private record Mark(int x, int z, int color, Component label) {
    }

    /** Own bodies (from the server), the console and the guard point of a tablet the pilot carries. */
    private static List<Mark> marks(Player player) {
        List<Mark> marks = new ArrayList<>();
        for (HelmetMarks.Mark mark : HelmetMarks.received) {
            marks.add(new Mark(mark.x(), mark.z(), 0xFFC9B7FF, tr("mark.body")));
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(GarrisonRegistry.TABLET.get())) {
                TabletData data = TabletItem.data(stack);
                data.console().filter(pos -> pos.dimension().equals(player.level().dimension()))
                        .ifPresent(pos -> marks.add(new Mark(pos.pos().getX(), pos.pos().getZ(), UiColors.AMBER_LIGHT, tr("mark.console"))));
                data.guard().ifPresent(pos -> marks.add(new Mark(pos.getX(), pos.getZ(), 0xFF9FDCFF, tr("mark.guard"))));
                break;
            }
        }
        return marks;
    }
}
