package com.projectrebirth.client.codex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.codex.CodexBook;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The Rebirth Codex. Two views: the map of a category (entries as cells, lines from what comes first to what follows, dragged
 * with the mouse) and the spread of an entry (two pages, turned with the buttons; right-click or Escape goes back to the map).
 * 1.8.0 block G (owner's wish): a rugged pocket computer in the mod's style, in the spirit of the field PDAs of the
 * S.T.A.L.K.E.R. games: a graphite metal body with rubber corners and an antenna, the categories on hardware keys at the left,
 * an amber screen with a status line, a speaker grille and lamps at the right, the back and page buttons under the screen. The
 * screen switches on when the Codex opens (not with "less motion"). It fits itself to the window. The texts are unchanged.
 */
public class CodexScreen extends Screen {
    static final ResourceLocation ATLAS = ProjectRebirth.id("textures/gui/codex.png");
    /** The text of the pages and the titles on the screen (1.8.0: light on the dark screen instead of ink on parchment). */
    static final int INK = com.projectrebirth.client.ui.UiColors.SCREEN_TEXT;
    static final int RED_INK = com.projectrebirth.client.ui.UiColors.AMBER;
    private static final int MAX_WIDTH = 455;
    private static final int MAX_HEIGHT = 256;
    /** The column of the category keys, the panel at the right, the row of buttons under the screen. */
    private static final int KEYS_W = 36;
    private static final int SIDE_W = 34;
    private static final int TOP = 14;
    private static final int BOTTOM = 30;
    private static final int STATUS = 12;
    private static final int KEY_W = 26;
    private static final int KEY_H = 22;
    private static final int NODE = 26;
    private static final int GRID = 30;
    private static final int SPINE = 10;
    private static final int BUTTON_W = 40;
    private static final int BUTTON_H = 16;
    /** When the screen was switched on (the short power-up of the screen). */
    private long openedAt = net.minecraft.Util.getMillis();

    /** Kept while the game runs, so the book opens where it was closed. */
    private static String category;
    @Nullable
    private static String openEntry;
    private static int spread;
    private static final Map<String, int[]> PAN = new HashMap<>();

    private final List<CodexBook.Category> categories = CodexBook.categories();
    private int left;
    private int top;
    private int bookWidth;
    private int bookHeight;
    private boolean dragging;
    private List<CodexPages.Layout> layout = List.of();
    @Nullable
    private ItemStack hoveredStack;
    @Nullable
    private Component hoveredText;

    /** The window the Codex was opened from by its "?" button (1.8.0); Escape and closing go back to it. */
    @Nullable
    private final net.minecraft.client.gui.screens.Screen parent;

    public CodexScreen() {
        this(null);
    }

    private CodexScreen(@Nullable net.minecraft.client.gui.screens.Screen parent) {
        super(Component.translatable("item.projectrebirth.rebirth_codex"));
        this.parent = parent;
        if (category == null || categories.stream().noneMatch(c -> c.id().equals(category))) {
            category = categories.isEmpty() ? "" : categories.get(0).id();
        }
    }

    @Override
    protected void init() {
        bookWidth = Math.min(MAX_WIDTH, width - 8);
        bookHeight = Math.min(MAX_HEIGHT, height - 12);
        left = (width - bookWidth) / 2;
        top = (height - bookHeight) / 2 + 3;
        if (openEntry != null) {
            CodexBook.Entry entry = CodexBook.entries().get(openEntry);
            if (entry == null) {
                openEntry = null;
            } else {
                layout = CodexPages.layout(font, entry, pageWidth(), pageHeight());
                spread = Mth.clamp(spread, 0, lastSpread());
            }
        }
    }

    /**
     * Opens the Codex on an entry from a window's "?" button (1.8.0). Closing the Codex returns to that window, which stays
     * open on the server meanwhile (as JEI does with its recipe view). An unknown entry opens the Codex where it was.
     */
    public static CodexScreen openFrom(net.minecraft.client.gui.screens.Screen parent, String entryId) {
        CodexScreen screen = new CodexScreen(parent);
        CodexBook.Entry entry = CodexBook.entries().get(entryId);
        if (entry != null) {
            category = entry.category();
            openEntry = entry.id();
            spread = 0;
            CodexReadState.markRead(entry.id());
        }
        return screen;
    }

    @Override
    public void onClose() {
        if (parent != null && minecraft != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- geometry

    /** The screen of the device (its glass, inside the bezel). */
    private int screenX() {
        return left + KEYS_W + 4;
    }

    private int screenY() {
        return top + TOP;
    }

    private int screenW() {
        return bookWidth - KEYS_W - SIDE_W - 8;
    }

    private int screenH() {
        return bookHeight - TOP - BOTTOM;
    }

    /** The part of the screen under the status line, where the map and the pages are. */
    private int innerLeft() {
        return screenX() + 3;
    }

    private int innerTop() {
        return screenY() + STATUS + 2;
    }

    private int innerWidth() {
        return screenW() - 6;
    }

    private int innerHeight() {
        return screenH() - STATUS - 5;
    }

    int pageWidth() {
        return (innerWidth() - SPINE) / 2 - 12;
    }

    int pageHeight() {
        return innerHeight() - 20;
    }

    private int pageLeft(int side) {
        return side == 0 ? innerLeft() + 8 : innerLeft() + innerWidth() / 2 + SPINE / 2 + 4;
    }

    private int lastSpread() {
        return Math.max(0, (layout.size() - 1) / 2);
    }

    private CodexBook.Category currentCategory() {
        return categories.stream().filter(c -> c.id().equals(category)).findFirst().orElse(categories.get(0));
    }

    /** Where the map of the current category is moved to; at first it is centred on its entries. */
    private int[] pan() {
        return PAN.computeIfAbsent(category, key -> {
            List<CodexBook.Entry> entries = currentCategory().entries();
            int minX = entries.stream().mapToInt(CodexBook.Entry::x).min().orElse(0);
            int maxX = entries.stream().mapToInt(CodexBook.Entry::x).max().orElse(0);
            int minY = entries.stream().mapToInt(CodexBook.Entry::y).min().orElse(0);
            int maxY = entries.stream().mapToInt(CodexBook.Entry::y).max().orElse(0);
            return new int[] {-(minX + maxX) * GRID / 2, -(minY + maxY) * GRID / 2 + 8};
        });
    }

    private int nodeX(CodexBook.Entry entry) {
        return innerLeft() + innerWidth() / 2 + entry.x() * GRID + pan()[0] - NODE / 2;
    }

    private int nodeY(CodexBook.Entry entry) {
        return innerTop() + innerHeight() / 2 + entry.y() * GRID + pan()[1] - NODE / 2;
    }

    static ItemStack stackOf(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        hoveredStack = null;
        hoveredText = null;
        drawDevice(graphics, mouseX, mouseY);
        // the screen: dark glass, the status line, then the map or the pages; it switches on from a line in the middle
        com.projectrebirth.client.ui.UiPanels.screen(graphics, screenX(), screenY(), screenW(), screenH());
        float power = power();
        int half = Math.max(1, Math.round(screenH() / 2.0f * power));
        int middle = screenY() + screenH() / 2;
        graphics.enableScissor(screenX() + 2, Math.max(screenY() + 2, middle - half), screenX() + screenW() - 2, Math.min(screenY() + screenH() - 2, middle + half));
        drawStatus(graphics);
        if (openEntry == null) {
            drawMap(graphics, mouseX, mouseY);
        } else {
            drawSpread(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
        if (power < 1.0f) {
            graphics.fill(screenX() + 2, middle - 1, screenX() + screenW() - 2, middle + 1, 0xC0FFE0A8);
        }
        drawButtons(graphics, mouseX, mouseY);
        if (hoveredStack != null && !hoveredStack.isEmpty()) {
            graphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
        } else if (hoveredText != null) {
            com.projectrebirth.client.ui.UiTooltip.of(hoveredText).render(graphics, mouseX, mouseY);
        }
    }

    /** How far the screen is switched on (0 to 1) in the first quarter of a second; at once with "less motion" or no animations. */
    private float power() {
        boolean animate = com.projectrebirth.config.RebirthClientConfig.CLIENT_SPEC.isLoaded()
                && com.projectrebirth.config.RebirthClientConfig.UI_ANIMATIONS.get() && !com.projectrebirth.config.RebirthClientConfig.UI_REDUCED_MOTION.get();
        return animate ? Mth.clamp((net.minecraft.Util.getMillis() - openedAt) / 260.0f, 0.0f, 1.0f) : 1.0f;
    }

    void hover(ItemStack stack) {
        hoveredStack = stack;
    }

    /**
     * The body of the device: graphite metal with rivets (the window of the mod's style), rubber corners, the antenna, the bezel
     * of the screen, the engraved name, the speaker grille and the lamps at the right, hazard stripes along the bottom, the keys.
     */
    private void drawDevice(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left;
        int y = top;
        int w = bookWidth;
        int h = bookHeight;
        // the antenna, behind the body at the top right
        int ax = x + w - SIDE_W + 6;
        graphics.fill(ax, y - 9, ax + 6, y + 4, 0xFF0B0C0D);
        graphics.fill(ax + 1, y - 8, ax + 5, y + 4, 0xFF3A3E42);
        graphics.fill(ax + 2, y - 8, ax + 3, y + 4, 0xFF55595E);
        graphics.fill(ax + 1, y - 11, ax + 5, y - 8, 0xFF0B0C0D);
        com.projectrebirth.client.ui.UiPanels.window(graphics, x, y, w, h);
        // rubber bumpers along both sides and on the corners, ridged for the grip
        for (int side = 0; side < 2; side++) {
            int bx = side == 0 ? x - 3 : x + w - 3;
            graphics.fill(bx, y + 14, bx + 6, y + h - 14, 0xFF0B0C0D);
            graphics.fill(bx + 1, y + 15, bx + 5, y + h - 15, 0xFF1C1F21);
            for (int ry = y + 18; ry < y + h - 18; ry += 4) {
                graphics.fill(bx + 1, ry, bx + 5, ry + 1, 0xFF121415);
            }
        }
        for (int[] corner : new int[][] {{x - 3, y - 3}, {x + w - 13, y - 3}, {x - 3, y + h - 13}, {x + w - 13, y + h - 13}}) {
            graphics.fill(corner[0], corner[1], corner[0] + 16, corner[1] + 16, 0xFF0B0C0D);
            graphics.fill(corner[0] + 1, corner[1] + 1, corner[0] + 15, corner[1] + 15, 0xFF1C1F21);
            for (int line = 4; line < 14; line += 3) {
                graphics.fill(corner[0] + 3, corner[1] + line, corner[0] + 13, corner[1] + line + 1, 0xFF121415);
            }
        }
        // the engraved name over the screen, the bezel around it
        Component brand = Component.translatable("codex.projectrebirth.pda_brand");
        com.projectrebirth.client.ui.UiText.line(graphics, com.projectrebirth.client.ui.UiText.upper(brand), screenX() + 2, y + 3, screenW() / 2,
                0xFF8A9096);
        graphics.fill(screenX() - 3, screenY() - 3, screenX() + screenW() + 3, screenY() + screenH() + 3, 0xFF0B0C0D);
        graphics.fill(screenX() - 2, screenY() - 2, screenX() + screenW() + 2, screenY() + screenH() + 2, 0xFF1B1E21);
        // four screws at the corners of the bezel
        for (int[] screw : new int[][] {{screenX() - 7, screenY() - 7}, {screenX() + screenW() + 4, screenY() - 7},
                {screenX() - 7, screenY() + screenH() + 4}, {screenX() + screenW() + 4, screenY() + screenH() + 4}}) {
            graphics.fill(screw[0], screw[1], screw[0] + 3, screw[1] + 3, 0xFF6B7177);
            graphics.fill(screw[0], screw[1] + 1, screw[0] + 3, screw[1] + 2, 0xFF2B2E31);
        }
        // the speaker grille and the lamps at the right
        int sx = x + w - SIDE_W + 4;
        int sw = SIDE_W - 12;
        for (int row = 0; row < 9; row++) {
            int gy = y + 22 + row * 5;
            graphics.fill(sx, gy, sx + sw, gy + 2, 0xFF0F1112);
            graphics.fill(sx, gy + 2, sx + sw, gy + 3, 0xFF3A3E42);
        }
        boolean unread = categories.stream().anyMatch(CodexReadState::anyUnread);
        boolean blink = (net.minecraft.Util.getMillis() / 500) % 2 == 0 || com.projectrebirth.client.ui.UiSounds.reducedMotion();
        lamp(graphics, sx + 2, y + 74, 0xFF7BD88F, true, Component.translatable("codex.projectrebirth.lamp_power"), mouseX, mouseY);
        lamp(graphics, sx + 12, y + 74, com.projectrebirth.client.ui.UiColors.AMBER, unread && blink,
                Component.translatable(unread ? "codex.projectrebirth.lamp_unread" : "codex.projectrebirth.lamp_read"), mouseX, mouseY);
        // a knob
        int kx = sx + sw / 2;
        int ky = y + 104;
        graphics.fill(kx - 7, ky - 7, kx + 7, ky + 7, 0xFF0B0C0D);
        graphics.fill(kx - 6, ky - 6, kx + 6, ky + 6, 0xFF3A3E42);
        graphics.fill(kx - 5, ky - 5, kx + 5, ky + 5, 0xFF2B2E31);
        graphics.fill(kx - 1, ky - 5, kx + 1, ky, 0xFFC7A46A);
        // hazard stripes at the bottom right corner of the body
        com.projectrebirth.client.ui.UiSprites.draw(graphics, com.projectrebirth.client.ui.UiSprites.HAZARD, x + w - SIDE_W - 60, y + h - 9, 56, 5);
        drawTabs(graphics, mouseX, mouseY);
    }

    private void lamp(GuiGraphics graphics, int x, int y, int color, boolean on, Component meaning, int mouseX, int mouseY) {
        graphics.fill(x - 1, y - 1, x + 6, y + 6, 0xFF0B0C0D);
        graphics.fill(x, y, x + 5, y + 5, on ? 0xFF000000 | color : 0xFF2A2E2B);
        if (on) {
            graphics.fill(x + 1, y + 1, x + 3, y + 2, 0xC0FFFFFF);
        }
        if (inside(mouseX, mouseY, x - 1, y - 1, 7, 7)) {
            hoveredText = meaning;
        }
    }

    /** The status line at the top of the screen: the system, the category or the entry, the time of day. */
    private void drawStatus(GuiGraphics graphics) {
        int x = screenX() + 4;
        int y = screenY() + 3;
        int w = screenW() - 8;
        String time = "";
        if (minecraft != null && minecraft.level != null) {
            long day = (minecraft.level.getDayTime() + 6000) % 24000;
            time = String.format(java.util.Locale.ROOT, "%02d:%02d", day / 1000, day % 1000 * 60 / 1000);
        }
        int timeW = font.width(time) + 14;
        Component where = openEntry == null ? Component.translatable(currentCategory().titleKey())
                : Component.translatable(currentCategory().titleKey()).copy().append(" › ").append(Component.translatable(CodexBook.entries().get(openEntry).titleKey()));
        Component line = Component.literal("REBIRTH OS · ").append(where);
        com.projectrebirth.client.ui.UiText.line(graphics, line, x, y, w - timeW - 4, com.projectrebirth.client.ui.UiColors.AMBER_DIM);
        graphics.drawString(font, time, x + w - timeW, y, com.projectrebirth.client.ui.UiColors.AMBER_DIM, false);
        // the battery
        int bx = x + w - 10;
        graphics.fill(bx, y + 1, bx + 9, y + 7, com.projectrebirth.client.ui.UiColors.AMBER_DIM);
        graphics.fill(bx + 1, y + 2, bx + 8, y + 6, 0xFF0E1311);
        graphics.fill(bx + 2, y + 3, bx + 7, y + 5, com.projectrebirth.client.ui.UiColors.AMBER_DIM);
        graphics.fill(screenX() + 3, screenY() + STATUS, screenX() + screenW() - 3, screenY() + STATUS + 1, 0x60FFB648);
    }

    /** The hardware buttons under the screen: back (on an entry), the previous and the next spread, the page counter. */
    private void drawButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = buttonY();
        if (openEntry != null) {
            button(graphics, backX(), y, BUTTON_W + 14, Component.translatable("codex.projectrebirth.back_button"), true, mouseX, mouseY,
                    Component.translatable("codex.projectrebirth.back"));
            button(graphics, prevX(), y, BUTTON_W, Component.literal("◀"), spread > 0, mouseX, mouseY, Component.translatable("codex.projectrebirth.prev"));
            button(graphics, nextX(), y, BUTTON_W, Component.literal("▶"), spread < lastSpread(), mouseX, mouseY,
                    Component.translatable("codex.projectrebirth.next"));
            Component pages = Component.literal((spread + 1) + " / " + (lastSpread() + 1));
            graphics.drawString(font, pages, prevX() + BUTTON_W + (nextX() - prevX() - BUTTON_W - font.width(pages)) / 2, y + 4,
                    com.projectrebirth.client.ui.UiColors.TEXT_SECONDARY, false);
        } else {
            Component hint = Component.translatable("codex.projectrebirth.map_hint");
            com.projectrebirth.client.ui.UiText.line(graphics, hint, screenX(), y + 4, screenW() - 64, com.projectrebirth.client.ui.UiColors.TEXT_DIM);
        }
    }

    private void button(GuiGraphics graphics, int x, int y, int w, Component text, boolean active, int mouseX, int mouseY, Component meaning) {
        boolean over = active && inside(mouseX, mouseY, x, y, w, BUTTON_H);
        com.projectrebirth.client.ui.UiSprites.draw(graphics, !active ? com.projectrebirth.client.ui.UiSprites.BUTTON_DISABLED
                : over ? com.projectrebirth.client.ui.UiSprites.BUTTON_HOVER : com.projectrebirth.client.ui.UiSprites.BUTTON, x, y, w, BUTTON_H);
        com.projectrebirth.client.ui.UiText.centred(graphics, text, x + w / 2, y + 4, w - 4,
                !active ? com.projectrebirth.client.ui.UiColors.TEXT_DIM : over ? com.projectrebirth.client.ui.UiColors.AMBER_LIGHT : com.projectrebirth.client.ui.UiColors.TEXT);
        if (over) {
            hoveredText = meaning;
        }
    }

    private int buttonY() {
        return screenY() + screenH() + 7;
    }

    private int tabY(int index) {
        // the keys keep a gap of 3 while they fit; with many categories or a short device they move closer together
        int step = categories.size() < 2 ? KEY_H + 3
                : Mth.clamp((bookHeight - TOP - 12 - KEY_H) / (categories.size() - 1), KEY_H - 2, KEY_H + 3);
        return top + TOP + index * step;
    }

    private int tabX() {
        return left + (KEYS_W - KEY_W) / 2 + 2;
    }

    /** The categories as hardware keys at the left: the chosen one pressed with its lamp lit; a blinking lamp on unread ones. */
    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < categories.size(); i++) {
            CodexBook.Category tab = categories.get(i);
            boolean active = tab.id().equals(category);
            int x = tabX();
            int y = tabY(i);
            boolean over = inside(mouseX, mouseY, x, y, KEY_W, KEY_H);
            graphics.fill(x - 1, y - 1, x + KEY_W + 1, y + KEY_H + 1, 0xFF0B0C0D);
            com.projectrebirth.client.ui.UiSprites.draw(graphics, active ? com.projectrebirth.client.ui.UiSprites.BUTTON_PRESSED
                    : over ? com.projectrebirth.client.ui.UiSprites.BUTTON_HOVER : com.projectrebirth.client.ui.UiSprites.BUTTON, x, y, KEY_W, KEY_H);
            graphics.renderItem(stackOf(tab.icon()), x + 5, y + 3 + (active ? 1 : 0));
            // the key's lamp: amber when chosen, blinking when something in it is unread
            boolean unread = CodexReadState.anyUnread(tab);
            boolean blink = (net.minecraft.Util.getMillis() / 500) % 2 == 0 || com.projectrebirth.client.ui.UiSounds.reducedMotion();
            int lamp = active ? com.projectrebirth.client.ui.UiColors.AMBER : unread && blink ? 0xFFFF8A7A : 0xFF2A2E2B;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(x - 4, y + KEY_H / 2 - 2, x - 1, y + KEY_H / 2 + 2, lamp);
            graphics.pose().popPose();
            if (over) {
                hoveredText = Component.translatable(tab.titleKey());
            }
        }
    }

    private void drawMap(GuiGraphics graphics, int mouseX, int mouseY) {
        CodexBook.Category current = currentCategory();
        graphics.enableScissor(innerLeft(), innerTop(), innerLeft() + innerWidth(), innerTop() + innerHeight());
        // faint ruling, moving with the map
        int startX = innerLeft() + Math.floorMod(pan()[0] + innerWidth() / 2, GRID) - GRID;
        int startY = innerTop() + Math.floorMod(pan()[1] + innerHeight() / 2, GRID) - GRID;
        for (int x = startX; x < innerLeft() + innerWidth(); x += GRID) {
            graphics.fill(x, innerTop(), x + 1, innerTop() + innerHeight(), 0x10FFB648);
        }
        for (int y = startY; y < innerTop() + innerHeight(); y += GRID) {
            graphics.fill(innerLeft(), y, innerLeft() + innerWidth(), y + 1, 0x10FFB648);
        }
        Map<String, CodexBook.Entry> entries = CodexBook.entries();
        for (CodexBook.Entry entry : current.entries()) {
            for (String parentId : entry.parents()) {
                CodexBook.Entry parent = entries.get(parentId);
                if (parent != null && parent.category().equals(entry.category())) {
                    connect(graphics, nodeX(parent) + NODE / 2, nodeY(parent) + NODE / 2, nodeX(entry) + NODE / 2, nodeY(entry) + NODE / 2);
                }
            }
        }
        for (CodexBook.Entry entry : current.entries()) {
            int x = nodeX(entry);
            int y = nodeY(entry);
            boolean hovered = !dragging && mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE
                    && mouseX >= innerLeft() && mouseY >= innerTop() && mouseX < innerLeft() + innerWidth() && mouseY < innerTop() + innerHeight();
            com.projectrebirth.client.ui.UiSprites.draw(graphics, hovered ? com.projectrebirth.client.ui.UiSprites.CARD_SELECTED
                    : com.projectrebirth.client.ui.UiSprites.CARD, x, y, NODE, NODE);
            if (entry.parents().isEmpty() && !hovered) {
                graphics.renderOutline(x, y, NODE, NODE, com.projectrebirth.client.ui.UiColors.AMBER_DIM);
            }
            graphics.renderItem(stackOf(entry.icon()), x + 5, y + 5);
            if (!CodexReadState.isRead(entry.id())) {
                unreadMark(graphics, x + NODE - 8, y + 1);
            }
            if (hovered) {
                hoveredText = Component.translatable(entry.titleKey());
            }
        }
        graphics.disableScissor();
        // the category's name on a strip at the top of the map
        Component title = com.projectrebirth.client.ui.UiText.upper(Component.translatable(current.titleKey()));
        int titleWidth = font.width(title) + 16;
        int titleX = innerLeft() + (innerWidth() - titleWidth) / 2;
        graphics.fill(titleX, innerTop() + 3, titleX + titleWidth, innerTop() + 16, 0xE0141A17);
        graphics.renderOutline(titleX, innerTop() + 3, titleWidth, 13, 0x80FFB648);
        graphics.drawString(font, title, titleX + 8, innerTop() + 6, RED_INK, false);
    }

    /** A small blinking amber lamp on what has not been read yet, drawn above the item icons. */
    private static void unreadMark(GuiGraphics graphics, int x, int y) {
        boolean on = (net.minecraft.Util.getMillis() / 500) % 2 == 0 || com.projectrebirth.client.ui.UiSounds.reducedMotion();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(x, y, x + 6, y + 6, 0xFF0B0C0D);
        graphics.fill(x + 1, y + 1, x + 5, y + 5, on ? com.projectrebirth.client.ui.UiColors.AMBER : 0xFF7A5A2A);
        graphics.fill(x + 1, y + 1, x + 3, y + 2, on ? 0xC0FFFFFF : 0x40FFFFFF);
        graphics.pose().popPose();
    }

    /** A line of ink from one medallion to another: across, then down, two pixels wide with a lighter core. */
    private static void connect(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        int midX = x2;
        line(graphics, Math.min(x1, midX), y1 - 1, Math.max(x1, midX) + 1, y1 + 2);
        line(graphics, midX - 1, Math.min(y1, y2), midX + 2, Math.max(y1, y2) + 1);
    }

    private static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        graphics.fill(x1, y1, x2, y2, 0xFF5A4520);
        if (x2 - x1 > y2 - y1) {
            graphics.fill(x1, y1 + 1, x2, y1 + 2, 0xFFC7A46A);
        } else {
            graphics.fill(x1 + 1, y1, x1 + 2, y2, 0xFFC7A46A);
        }
    }

    private void drawSpread(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int spineX = innerLeft() + innerWidth() / 2;
        for (int dy = 4; dy < innerHeight() - 4; dy += 4) {
            graphics.fill(spineX, innerTop() + dy, spineX + 1, innerTop() + dy + 2, 0x50FFB648);
        }
        for (int side = 0; side < 2; side++) {
            int index = spread * 2 + side;
            if (index < layout.size()) {
                CodexPages.draw(this, graphics, font, layout.get(index), pageLeft(side), innerTop() + 6, pageWidth(), pageHeight(), mouseX, mouseY, partialTick);
                Component number = Component.literal(String.valueOf(index + 1));
                int numberX = side == 0 ? pageLeft(0) : pageLeft(1) + pageWidth() - font.width(number);
                graphics.drawString(font, number, numberX, innerTop() + innerHeight() - 10, com.projectrebirth.client.ui.UiColors.TEXT_DIM, false);
            }
        }
    }

    private int prevX() {
        return screenX() + screenW() / 2 - BUTTON_W - 20;
    }

    private int nextX() {
        return screenX() + screenW() / 2 + 20;
    }

    private int backX() {
        return screenX();
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ---------------------------------------------------------------- input

    private void open(@Nullable CodexBook.Entry entry) {
        openEntry = entry == null ? null : entry.id();
        spread = 0;
        layout = entry == null ? List.of() : CodexPages.layout(font, entry, pageWidth(), pageHeight());
        if (entry != null) {
            CodexReadState.markRead(entry.id());
        }
    }

    /** Opens an entry by id (for links between entries); stays on the map if there is no such entry. */
    void openById(String id) {
        CodexBook.Entry entry = CodexBook.entries().get(id);
        if (entry != null) {
            category = entry.category();
            open(entry);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < categories.size(); i++) {
            if (inside(mouseX, mouseY, tabX(), tabY(i), KEY_W, KEY_H)) {
                category = categories.get(i).id();
                open(null);
                com.projectrebirth.client.ui.UiSounds.CLICK.play();
                return true;
            }
        }
        if (openEntry != null) {
            if (button == 1 || inside(mouseX, mouseY, backX(), buttonY(), BUTTON_W + 14, BUTTON_H)) {
                open(null);
                com.projectrebirth.client.ui.UiSounds.CLICK.play();
                return true;
            }
            if (spread > 0 && inside(mouseX, mouseY, prevX(), buttonY(), BUTTON_W, BUTTON_H)) {
                spread--;
                com.projectrebirth.client.ui.UiSounds.CLICK.play();
                return true;
            }
            if (spread < lastSpread() && inside(mouseX, mouseY, nextX(), buttonY(), BUTTON_W, BUTTON_H)) {
                spread++;
                com.projectrebirth.client.ui.UiSounds.CLICK.play();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0 && inside(mouseX, mouseY, innerLeft(), innerTop(), innerWidth(), innerHeight())) {
            for (CodexBook.Entry entry : currentCategory().entries()) {
                if (inside(mouseX, mouseY, nodeX(entry), nodeY(entry), NODE, NODE)) {
                    com.projectrebirth.client.ui.UiSounds.CLICK.play();
                    open(entry);
                    return true;
                }
            }
            dragging = false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (openEntry == null && button == 0) {
            dragging = true;
            int[] pan = pan();
            pan[0] = Mth.clamp(pan[0] + (int) Math.round(dragX), -GRID * 12, GRID * 12);
            pan[1] = Mth.clamp(pan[1] + (int) Math.round(dragY), -GRID * 8, GRID * 8);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (CodexPages.scroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (openEntry != null) {
            spread = Mth.clamp(spread - (int) Math.signum(scrollY), 0, lastSpread());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (openEntry != null) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && parent != null) {
                onClose();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                open(null);
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_D) {
                spread = Math.min(lastSpread(), spread + 1);
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_A) {
                spread = Math.max(0, spread - 1);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** For tests and the first page: the layout pages of the open entry. */
    List<CodexPages.Layout> layout() {
        return new ArrayList<>(layout);
    }

    static List<FormattedCharSequence> split(net.minecraft.client.gui.Font font, Component text, int width) {
        return font.split(text, width);
    }
}
