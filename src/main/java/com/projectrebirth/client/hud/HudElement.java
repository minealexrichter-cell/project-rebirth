package com.projectrebirth.client.hud;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.projectrebirth.client.HudLayout;
import com.projectrebirth.config.RebirthClientConfig;

import net.minecraft.util.Mth;

/**
 * The movable parts of the mod's HUD (1.8.0 block C) and where each one stands. Every element has a default place (an
 * anchor on the screen and its size), and the HUD editor (key H) moves it by an offset, sizes it and turns it off.
 * The status panel and the body's visor keep their old settings (panelX/panelY and barOffsetX/barOffsetY of [hud]), so
 * the places set before 1.8.0 stay; the other elements are stored in hudElements.
 */
public enum HudElement {
    /** The status panel in the top-left corner (human, catastrophe, machines). */
    STATUS_PANEL(HudLayout.PANEL_WIDTH, HudLayout.PANEL_HEIGHT),
    /** The body's readings where the hearts were. */
    BODY_VISOR(160, 58),
    /** Warnings of the body or armor in the middle of the screen. */
    WARNINGS(220, 30),
    /** The armor's hull: silhouette and its two scales. */
    ARMOR_HULL(150, 58),
    /** The armor's weapon: rounds, reserve, reload. */
    ARMOR_WEAPON(130, 52),
    /** The armor's systems: floodlight, mining mode, ability, upgrades. */
    ARMOR_SYSTEMS(120, 60),
    /** The compass at the top of the helmet. */
    COMPASS(200, 16);

    public final int width;
    public final int height;

    HudElement(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Where an element stands relative to its default place. */
    public record Placement(int dx, int dy, float scale, boolean visible) {
        public static final Placement DEFAULT = new Placement(0, 0, 1.0f, true);
    }

    private static Map<HudElement, Placement> cache;
    private static List<? extends String> cachedFrom;

    private static Map<HudElement, Placement> placements() {
        List<? extends String> stored = RebirthClientConfig.HUD_ELEMENTS.get();
        if (cache != null && cachedFrom == stored) {
            return cache;
        }
        Map<HudElement, Placement> map = new EnumMap<>(HudElement.class);
        for (String entry : stored) {
            String[] parts = entry.trim().split("\\s+");
            if (parts.length < 5) {
                continue;
            }
            try {
                HudElement element = HudElement.valueOf(parts[0].toUpperCase(Locale.ROOT));
                map.put(element, new Placement(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                        Mth.clamp(Float.parseFloat(parts[3]), 0.5f, 2.5f), Boolean.parseBoolean(parts[4])));
            } catch (IllegalArgumentException ignored) {
                // an unknown or broken line is skipped
            }
        }
        cache = map;
        cachedFrom = stored;
        return map;
    }

    public Placement placement() {
        Placement stored = placements().getOrDefault(this, Placement.DEFAULT);
        return switch (this) {
            case STATUS_PANEL -> new Placement(RebirthClientConfig.HUD_PANEL_X.get(), RebirthClientConfig.HUD_PANEL_Y.get(), stored.scale, stored.visible);
            case BODY_VISOR -> new Placement(RebirthClientConfig.HUD_BAR_OFFSET_X.get(), RebirthClientConfig.HUD_BAR_OFFSET_Y.get(), stored.scale, stored.visible);
            default -> stored;
        };
    }

    public boolean visible() {
        return HudLayout.enabled() && placement().visible;
    }

    /** The size of the element on the screen: the HUD scale of [hud] times its own. */
    public float scale() {
        return HudLayout.scale() * placement().scale;
    }

    /** Stores a new placement (the HUD editor); the panel and the visor keep their old keys. */
    public void set(Placement placement) {
        switch (this) {
            case STATUS_PANEL -> {
                RebirthClientConfig.HUD_PANEL_X.set(Math.max(0, placement.dx));
                RebirthClientConfig.HUD_PANEL_Y.set(Math.max(0, placement.dy));
            }
            case BODY_VISOR -> {
                RebirthClientConfig.HUD_BAR_OFFSET_X.set(placement.dx);
                RebirthClientConfig.HUD_BAR_OFFSET_Y.set(placement.dy);
            }
            default -> {
            }
        }
        Map<HudElement, Placement> map = new EnumMap<>(placements());
        map.put(this, placement);
        List<String> lines = new ArrayList<>();
        for (Map.Entry<HudElement, Placement> entry : map.entrySet()) {
            Placement p = entry.getValue();
            lines.add(entry.getKey().id() + " " + p.dx + " " + p.dy + " " + String.format(Locale.ROOT, "%.2f", p.scale) + " " + p.visible);
        }
        RebirthClientConfig.HUD_ELEMENTS.set(lines);
    }

    /** Back to the default places, sizes and visibility. */
    public static void resetAll() {
        RebirthClientConfig.HUD_ELEMENTS.set(List.of());
        RebirthClientConfig.HUD_PANEL_X.set(4);
        RebirthClientConfig.HUD_PANEL_Y.set(4);
        RebirthClientConfig.HUD_BAR_OFFSET_X.set(0);
        RebirthClientConfig.HUD_BAR_OFFSET_Y.set(0);
        RebirthClientConfig.HUD_SCALE.set(1.0);
    }

    /** The default top-left corner of the element on a screen of this size (GUI pixels). */
    public int[] anchor(int guiWidth, int guiHeight) {
        int w = Math.round(width * scale());
        int h = Math.round(height * scale());
        return switch (this) {
            case STATUS_PANEL -> new int[] {0, 0};
            case BODY_VISOR -> new int[] {guiWidth / 2 - 91, guiHeight - 30 - h};
            case WARNINGS -> new int[] {(guiWidth - w) / 2, Math.round(guiHeight * 0.62f)};
            // above the chat, which takes the bottom-left corner
            case ARMOR_HULL -> new int[] {6, guiHeight / 2 + 4};
            case ARMOR_WEAPON -> new int[] {guiWidth - 6 - w, guiHeight - 6 - h};
            case ARMOR_SYSTEMS -> new int[] {6, guiHeight / 2 - 4 - h};
            case COMPASS -> new int[] {(guiWidth - w) / 2, 4};
        };
    }

    /** The element's top-left corner now: its default place moved by its offset, kept on the screen. */
    public int[] position(int guiWidth, int guiHeight) {
        int[] anchor = anchor(guiWidth, guiHeight);
        Placement placement = placement();
        int w = Math.round(width * scale());
        int h = Math.round(height * scale());
        int x = this == STATUS_PANEL ? placement.dx : anchor[0] + placement.dx;
        int y = this == STATUS_PANEL ? placement.dy : anchor[1] + placement.dy;
        return new int[] {Mth.clamp(x, 0, Math.max(0, guiWidth - w)), Mth.clamp(y, 0, Math.max(0, guiHeight - h))};
    }
}
