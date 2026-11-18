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
 * The Rebirth Codex. Two views, like an old research book: the map of a category (entries as medallions, lines from what
 * comes first to what follows, dragged with the mouse; tabs of the categories on the left) and the spread of an entry (two
 * pages, turned with the arrows; right-click or Escape goes back to the map). The book fits itself to the window.
 */
public class CodexScreen extends Screen {
    static final ResourceLocation ATLAS = ProjectRebirth.id("textures/gui/codex.png");
    static final int INK = 0xFF3B2A1A;
    static final int RED_INK = 0xFF7A2414;
    private static final int MAX_WIDTH = 412;
    private static final int MAX_HEIGHT = 250;
    private static final int BORDER = 14;
    private static final int TAB_W = 30;
    private static final int TAB_H = 26;
    private static final int NODE = 26;
    private static final int GRID = 30;
    private static final int SPINE = 16;

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
        bookWidth = Math.min(MAX_WIDTH, width - TAB_W * 2 - 8);
        bookHeight = Math.min(MAX_HEIGHT, height - 16);
        left = (width - bookWidth) / 2 + TAB_W / 2;
        top = (height - bookHeight) / 2;
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

    private int innerLeft() {
        return left + BORDER;
    }

    private int innerTop() {
        return top + BORDER;
    }

    private int innerWidth() {
        return bookWidth - 2 * BORDER;
    }

    private int innerHeight() {
        return bookHeight - 2 * BORDER;
    }

    int pageWidth() {
        return (innerWidth() - SPINE) / 2 - 12;
    }

    int pageHeight() {
        return innerHeight() - 30;
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
        drawTabs(graphics, mouseX, mouseY);
        tileParchment(graphics, innerLeft(), innerTop(), innerWidth(), innerHeight());
        if (openEntry == null) {
            drawMap(graphics, mouseX, mouseY);
        } else {
            drawSpread(graphics, mouseX, mouseY, partialTick);
        }
        frame(graphics, left, top, bookWidth, bookHeight);
        if (openEntry != null) {
            // the back button sits on the frame
            boolean overBack = inside(mouseX, mouseY, backX(), backY(), 14, 14);
            graphics.blit(ATLAS, backX(), backY(), overBack ? 86 : 72, 108, 14, 14);
            if (overBack) {
                hoveredText = Component.translatable("codex.projectrebirth.back");
            }
        }
        if (hoveredStack != null && !hoveredStack.isEmpty()) {
            graphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
        } else if (hoveredText != null) {
            graphics.renderTooltip(font, hoveredText, mouseX, mouseY);
        }
    }

    void hover(ItemStack stack) {
        hoveredStack = stack;
    }

    private void tileParchment(GuiGraphics graphics, int x, int y, int w, int h) {
        for (int dy = 0; dy < h; dy += 64) {
            for (int dx = 0; dx < w; dx += 64) {
                graphics.blit(ATLAS, x + dx, y + dy, 160, 0, Math.min(64, w - dx), Math.min(64, h - dy));
            }
        }
    }

    /** The leather frame as a nine-slice of the 48x48 piece with corners of 16. */
    private void frame(GuiGraphics graphics, int x, int y, int w, int h) {
        int c = 16;
        graphics.blit(ATLAS, x, y, 0, 0, c, c);
        graphics.blit(ATLAS, x + w - c, y, 32, 0, c, c);
        graphics.blit(ATLAS, x, y + h - c, 0, 32, c, c);
        graphics.blit(ATLAS, x + w - c, y + h - c, 32, 32, c, c);
        for (int dx = c; dx < w - c; dx += c) {
            int part = Math.min(c, w - c - dx);
            graphics.blit(ATLAS, x + dx, y, 16, 0, part, c);
            graphics.blit(ATLAS, x + dx, y + h - c, 16, 32, part, c);
        }
        for (int dy = c; dy < h - c; dy += c) {
            int part = Math.min(c, h - c - dy);
            graphics.blit(ATLAS, x, y + dy, 0, 16, c, part);
            graphics.blit(ATLAS, x + w - c, y + dy, 32, 16, c, part);
        }
    }

    private int tabY(int index) {
        // tabs keep a gap of 4 while they fit; with many categories or a short book they move closer together
        int step = categories.size() < 2 ? TAB_H + 4
                : Mth.clamp((bookHeight - 32 - TAB_H) / (categories.size() - 1), TAB_H - 4, TAB_H + 4);
        return top + 16 + index * step;
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < categories.size(); i++) {
            CodexBook.Category tab = categories.get(i);
            boolean active = tab.id().equals(category);
            int x = left - TAB_W + (active ? 4 : 8);
            int y = tabY(i);
            graphics.blit(ATLAS, x, y, active ? 30 : 0, 80, TAB_W, TAB_H);
            graphics.renderItem(stackOf(tab.icon()), x + 7, y + 5);
            if (CodexReadState.anyUnread(tab)) {
                unreadMark(graphics, x + 3, y + 3);
            }
            if (mouseX >= x && mouseX < x + TAB_W - 6 && mouseY >= y && mouseY < y + TAB_H) {
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
            graphics.fill(x, innerTop(), x + 1, innerTop() + innerHeight(), 0x14000000);
        }
        for (int y = startY; y < innerTop() + innerHeight(); y += GRID) {
            graphics.fill(innerLeft(), y, innerLeft() + innerWidth(), y + 1, 0x14000000);
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
            int u = hovered ? 26 : entry.parents().isEmpty() ? 52 : 0;
            graphics.blit(ATLAS, x, y, u, 48, NODE, NODE);
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
        Component title = Component.translatable(current.titleKey());
        int titleWidth = font.width(title) + 16;
        int titleX = innerLeft() + (innerWidth() - titleWidth) / 2;
        graphics.fill(titleX, innerTop() + 4, titleX + titleWidth, innerTop() + 17, 0xC0E8D6AE);
        graphics.blit(ATLAS, innerLeft() + innerWidth() / 2 - 48, innerTop() + 18, 0, 150, 96, 7);
        graphics.drawString(font, title, titleX + 8, innerTop() + 7, RED_INK, false);
        Component hint = Component.translatable("codex.projectrebirth.map_hint");
        graphics.drawString(font, hint, innerLeft() + innerWidth() - font.width(hint) - 6, innerTop() + innerHeight() - 11, 0x907A6248, false);
    }

    /** A small glowing seal on what has not been read yet, drawn above the item icons. */
    private static void unreadMark(GuiGraphics graphics, int x, int y) {
        float pulse = 0.5f + 0.5f * Mth.sin((System.currentTimeMillis() % 1600L) / 1600.0f * Mth.TWO_PI);
        int glow = (int) (0x50 + 0x70 * pulse) << 24 | 0xFFC040;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(x - 1, y - 1, x + 7, y + 7, glow);
        graphics.fill(x, y, x + 6, y + 6, 0xFF5A1A0A);
        graphics.fill(x + 1, y + 1, x + 5, y + 5, 0xFFD8402A);
        graphics.fill(x + 2, y + 1, x + 4, y + 2, 0xFFF08060);
        graphics.pose().popPose();
    }

    /** A line of ink from one medallion to another: across, then down, two pixels wide with a lighter core. */
    private static void connect(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        int midX = x2;
        line(graphics, Math.min(x1, midX), y1 - 1, Math.max(x1, midX) + 1, y1 + 2);
        line(graphics, midX - 1, Math.min(y1, y2), midX + 2, Math.max(y1, y2) + 1);
    }

    private static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        graphics.fill(x1, y1, x2, y2, 0xFF6B4A2A);
        if (x2 - x1 > y2 - y1) {
            graphics.fill(x1, y1 + 1, x2, y1 + 2, 0xFFB08860);
        } else {
            graphics.fill(x1 + 1, y1, x1 + 2, y2, 0xFFB08860);
        }
    }

    private void drawSpread(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int spineX = innerLeft() + innerWidth() / 2 - SPINE / 2;
        for (int dy = 0; dy < innerHeight(); dy += 128) {
            graphics.blit(ATLAS, spineX, innerTop() + dy, 128, 0, SPINE, Math.min(128, innerHeight() - dy));
        }
        for (int side = 0; side < 2; side++) {
            int index = spread * 2 + side;
            if (index < layout.size()) {
                CodexPages.draw(this, graphics, font, layout.get(index), pageLeft(side), innerTop() + 10, pageWidth(), pageHeight(), mouseX, mouseY, partialTick);
                Component number = Component.literal(String.valueOf(index + 1));
                int numberX = side == 0 ? pageLeft(0) : pageLeft(1) + pageWidth() - font.width(number);
                graphics.drawString(font, number, numberX, innerTop() + innerHeight() - 12, 0x907A6248, false);
            }
        }
        int arrowY = innerTop() + innerHeight() - 13;
        if (spread > 0) {
            boolean over = inside(mouseX, mouseY, prevX(), arrowY, 18, 10);
            graphics.blit(ATLAS, prevX(), arrowY, over ? 54 : 18, 112, 18, 10);
        }
        if (spread < lastSpread()) {
            boolean over = inside(mouseX, mouseY, nextX(), arrowY, 18, 10);
            graphics.blit(ATLAS, nextX(), arrowY, over ? 36 : 0, 112, 18, 10);
        }
    }

    private int prevX() {
        return innerLeft() + innerWidth() / 2 - SPINE / 2 - 30;
    }

    private int nextX() {
        return innerLeft() + innerWidth() / 2 + SPINE / 2 + 12;
    }

    private int backX() {
        return left + 2;
    }

    private int backY() {
        return top + 2;
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
            if (inside(mouseX, mouseY, left - TAB_W + 4, tabY(i), TAB_W - 4, TAB_H)) {
                category = categories.get(i).id();
                open(null);
                return true;
            }
        }
        if (openEntry != null) {
            if (button == 1 || inside(mouseX, mouseY, backX(), backY(), 14, 14)) {
                open(null);
                return true;
            }
            int arrowY = innerTop() + innerHeight() - 13;
            if (spread > 0 && inside(mouseX, mouseY, prevX(), arrowY, 18, 10)) {
                spread--;
                return true;
            }
            if (spread < lastSpread() && inside(mouseX, mouseY, nextX(), arrowY, 18, 10)) {
                spread++;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0 && inside(mouseX, mouseY, innerLeft(), innerTop(), innerWidth(), innerHeight())) {
            for (CodexBook.Entry entry : currentCategory().entries()) {
                if (inside(mouseX, mouseY, nodeX(entry), nodeY(entry), NODE, NODE)) {
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
