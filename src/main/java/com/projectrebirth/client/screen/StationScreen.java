package com.projectrebirth.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiButton;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSearchField;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.technology.FabricationRecipe;
import com.projectrebirth.technology.StationBlockEntity;
import com.projectrebirth.technology.StationMenu;
import com.projectrebirth.technology.energy.EnergyUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * Station window in the style "Industrial terminal" (1.8.0, docs/ui_mockups/01_station_A.html, docs/UI_STYLE.md).
 * Left: the recipe list with search and the "can make" filter, each recipe with whether its materials are at hand.
 * Middle: the inputs (a missing ingredient is a dashed red slot with its ghost and "have/need"), the process screen
 * (what is made, an animated picture, percent, time left, progress) and a plate saying what is wrong and what to do.
 * Right: the output, the energy gauge with stock, income and use, and the state in a word. Bottom: the inventory and the
 * description of the chosen recipe. Everything the server does is unchanged; only the drawing is new.
 */
public class StationScreen extends AbstractContainerScreen<StationMenu> {
    public static final int WIDTH = 440;
    public static final int HEIGHT = 248;

    // recipe list
    private static final int LIST_X = 7;
    private static final int LIST_Y = 25;
    private static final int LIST_W = 145;
    private static final int LIST_H = 125;
    private static final int ROWS_X = LIST_X + 4;
    private static final int ROWS_Y = 56;
    private static final int ROW_W = 133;
    private static final int ROW_H = 18;
    private static final int ROWS = 5;
    // middle
    private static final int MID_X = 158;
    private static final int MID_RIGHT = 344;
    public static final int PROCESS_X = 201;
    public static final int PROCESS_Y = 36;
    public static final int PROCESS_W = MID_RIGHT - PROCESS_X;
    public static final int PROCESS_H = 95;
    private static final int PLATE_Y = 136;
    /** Width of the process picture. */
    private static final int ART_W = 64;
    // right column
    private static final int RIGHT_X = 350;
    private static final int RIGHT_W = WIDTH - 7 - RIGHT_X;
    private static final int GAUGE_Y = 78;
    private static final int GAUGE_W = 12;
    private static final int GAUGE_H = 52;
    // bottom
    private static final int INFO_X = 175;
    private static final int INFO_Y = 156;

    /** The filter is kept while the game runs. */
    private static boolean canMakeOnly;

    private final UiText.Hints hints = new UiText.Hints();
    private final UiBars.Smooth energyShown = new UiBars.Smooth();
    private final UiBars.Smooth progressShown = new UiBars.Smooth();
    /** Energy of the last 21 ticks, to tell the income (the server does not send it). */
    private final int[] energyHistory = new int[21];
    private final int[] progressHistory = new int[21];
    private int ticks;
    private UiSearchField search;
    private String lastQuery = "";
    /** Indices (in the menu's list) of the recipes shown, after search and filter. */
    private List<Integer> shown = new ArrayList<>();
    /** Per recipe of the menu: the first missing ingredient counting inputs and inventory, or null if all is at hand. */
    private SizedIngredient[] lacking = new SizedIngredient[0];
    private int startRow;
    private boolean scrolledToSelected;
    private boolean wasRunning;

    public StationScreen(StationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        search = new UiSearchField(leftPos + ROWS_X, topPos + LIST_Y + 4, ROW_W + 4, 12,
                Component.translatable("screen.projectrebirth.ui.search_recipe"));
        search.setValue(lastQuery);
        search.setResponder(text -> refreshList());
        addRenderableWidget(search);

        Component all = Component.translatable("screen.projectrebirth.station.filter.all");
        int allWidth = Math.max(26, font.width(all) + 10);
        int chipY = topPos + LIST_Y + 18;
        addRenderableWidget(new UiButton(leftPos + ROWS_X, chipY, ROW_W + 4 - allWidth - 3, 11,
                Component.translatable("screen.projectrebirth.station.filter.can_make"), UiSprites.ICON_CHECK, 7, UiButton.Look.CHIP,
                () -> setFilter(true)).selected(() -> canMakeOnly));
        addRenderableWidget(new UiButton(leftPos + ROWS_X + ROW_W + 4 - allWidth, chipY, allWidth, 11, all, null, 0, UiButton.Look.CHIP,
                () -> setFilter(false)).selected(() -> !canMakeOnly));

        int buttonY = topPos + 4;
        addRenderableWidget(UiButton.help(leftPos + WIDTH - 37, buttonY, this, menu.station().id()));
        addRenderableWidget(UiButton.icon(leftPos + WIDTH - 21, buttonY, 13, UiSprites.ICON_CLOSE, 7,
                Component.translatable("screen.projectrebirth.ui.close"), this::onClose));
        refreshAvailability();
        refreshList();
    }

    /** Once the chosen recipe has come from the server, the list scrolls to it. */
    private void scrollToSelected() {
        scrolledToSelected = true;
        int at = shown.indexOf(menu.selectedIndex());
        if (at >= startRow + ROWS || at >= 0 && at < startRow) {
            startRow = Mth.clamp(at - ROWS / 2, 0, maxStartRow());
        }
    }

    private void setFilter(boolean canMake) {
        canMakeOnly = canMake;
        startRow = 0;
        refreshList();
    }

    // ---------------------------------------------------------------- state

    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;
        if (!scrolledToSelected && menu.selectedIndex() >= 0) {
            refreshList();
            scrollToSelected();
        }
        energyHistory[ticks % energyHistory.length] = menu.energy();
        progressHistory[ticks % progressHistory.length] = Math.round(menu.progress() * 10000);
        boolean nowRunning = running();
        if (ticks > progressHistory.length + 1 && nowRunning != wasRunning) {
            (nowRunning ? com.projectrebirth.client.ui.UiSounds.WORK_START : com.projectrebirth.client.ui.UiSounds.WORK_END).play();
        }
        wasRunning = nowRunning;
        if (ticks % 10 == 0) {
            refreshAvailability();
            if (canMakeOnly) {
                refreshList();
            }
        }
    }

    @Nullable
    private FabricationRecipe selectedRecipe() {
        int index = menu.selectedIndex();
        return index >= 0 && index < menu.recipes().size() ? menu.recipes().get(index).value() : null;
    }

    private List<ItemStack> inputs() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < StationBlockEntity.INPUT_SLOTS; i++) {
            list.add(menu.getSlot(i).getItem());
        }
        return list;
    }

    /** Whether the station made progress during the last second (the client only sees progress and energy). */
    private boolean running() {
        int now = progressHistory[ticks % progressHistory.length];
        int before = progressHistory[(ticks + 1) % progressHistory.length];
        return ticks > progressHistory.length && now != before || menu.progress() > 0 && ticks <= progressHistory.length;
    }

    /** Energy use per tick while working. */
    private int use() {
        return running() ? StationBlockEntity.energyPerTick() : 0;
    }

    /** Income per tick, averaged over the last second: the change of the stock plus what was used. */
    private int income() {
        if (ticks < energyHistory.length) {
            return 0;
        }
        int now = energyHistory[ticks % energyHistory.length];
        int before = energyHistory[(ticks + 1) % energyHistory.length];
        return Math.max(0, Math.round((now - before) / (float) (energyHistory.length - 1)) + use());
    }

    private void refreshAvailability() {
        List<ItemStack> stacks = new ArrayList<>(inputs());
        if (minecraft != null && minecraft.player != null) {
            stacks.addAll(minecraft.player.getInventory().items);
        }
        List<RecipeHolder<FabricationRecipe>> recipes = menu.recipes();
        lacking = new SizedIngredient[recipes.size()];
        for (int i = 0; i < recipes.size(); i++) {
            List<SizedIngredient> missing = recipes.get(i).value().missing(stacks);
            lacking[i] = missing.isEmpty() ? null : missing.get(0);
        }
    }

    private boolean atHand(int index) {
        return index < lacking.length && lacking[index] == null;
    }

    private void refreshList() {
        lastQuery = search == null ? "" : search.getValue();
        String query = lastQuery.trim().toLowerCase(Locale.ROOT);
        List<Integer> list = new ArrayList<>();
        List<RecipeHolder<FabricationRecipe>> recipes = menu.recipes();
        for (int i = 0; i < recipes.size(); i++) {
            boolean selected = i == menu.selectedIndex();
            boolean matches = query.isEmpty() || recipes.get(i).value().result().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
            if (selected && query.isEmpty() || matches && (!canMakeOnly || atHand(i) || selected)) {
                list.add(i);
            }
        }
        shown = list;
        startRow = Mth.clamp(startRow, 0, maxStartRow());
    }

    /**
     * Per ingredient of the recipe: how many the inputs hold for this cycle and how many the next cycle would still lack
     * (taken in the same order as the server takes them).
     */
    private record Cover(SizedIngredient ingredient, int have, int nextMissing) {
    }

    private List<Cover> cover(FabricationRecipe recipe) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack stack : inputs()) {
            left.add(stack.copy());
        }
        List<Cover> list = new ArrayList<>();
        List<Integer> have = new ArrayList<>();
        for (SizedIngredient ingredient : recipe.ingredients()) {
            have.add(ingredient.count() - take(left, ingredient, ingredient.count()));
        }
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            SizedIngredient ingredient = recipe.ingredients().get(i);
            list.add(new Cover(ingredient, have.get(i), take(left, ingredient, ingredient.count())));
        }
        return list;
    }

    private static int take(List<ItemStack> stacks, SizedIngredient ingredient, int count) {
        int needed = count;
        for (ItemStack stack : stacks) {
            if (needed == 0) {
                break;
            }
            if (!stack.isEmpty() && ingredient.ingredient().test(stack)) {
                int used = Math.min(needed, stack.getCount());
                stack.shrink(used);
                needed -= used;
            }
        }
        return needed;
    }

    /** What the station does now, in a word for the badge, a sentence for the plate, and a tone. */
    private record State(UiSprites.Tone tone, Component badge, @Nullable Component plate) {
    }

    private State state() {
        FabricationRecipe recipe = selectedRecipe();
        if (recipe == null) {
            if (menu.movedRecipe()) {
                return new State(UiSprites.Tone.ERROR, tr("status.moved"), tr("moved"));
            }
            return new State(UiSprites.Tone.INFO, tr("status.idle"), tr("plate.select"));
        }
        List<SizedIngredient> missing = recipe.missing(inputs());
        if (!missing.isEmpty()) {
            return new State(UiSprites.Tone.ERROR, tr("status.missing"), tr("plate.missing", list(missing)));
        }
        ItemStack output = menu.getSlot(StationBlockEntity.OUTPUT_SLOT).getItem();
        boolean fits = output.isEmpty() || ItemStack.isSameItemSameComponents(output, recipe.result())
                && output.getCount() + recipe.result().getCount() <= output.getMaxStackSize();
        if (!fits) {
            return new State(UiSprites.Tone.WARNING, tr("status.output_full"), tr("plate.output_full"));
        }
        if (menu.emiPaused()) {
            return new State(UiSprites.Tone.WARNING, tr("status.emi"), tr("plate.emi"));
        }
        if (menu.energy() < StationBlockEntity.energyPerTick()) {
            return new State(UiSprites.Tone.WARNING, tr("status.no_energy"), tr("plate.no_energy"));
        }
        List<SizedIngredient> next = new ArrayList<>();
        for (Cover cover : cover(recipe)) {
            if (cover.nextMissing > 0) {
                next.add(new SizedIngredient(cover.ingredient.ingredient(), cover.nextMissing));
            }
        }
        return new State(UiSprites.Tone.OK, tr("status.working"), next.isEmpty() ? null : tr("plate.next", list(next)));
    }

    /** "2 × processor, 1 × bronze ingot". */
    private static Component list(List<SizedIngredient> ingredients) {
        StringBuilder text = new StringBuilder();
        for (SizedIngredient ingredient : ingredients) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(Component.translatable("screen.projectrebirth.station.ingredient", ingredient.count(),
                    displayStack(ingredient).getHoverName()).getString().toLowerCase(Locale.ROOT));
        }
        return Component.literal(text.toString());
    }

    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.station." + key, args);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hints.clear();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderGhosts(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
        renderOwnTooltips(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        UiPanels.window(graphics, x, y, WIDTH, HEIGHT);
        if (UiPanels.title(graphics, x, y, WIDTH, title, UiSprites.ICON_STATION, x + WIDTH - 42)) {
            hints.add(x + 23, y + 5, 200, 11, title);
        }
        State state = state();
        renderList(graphics, mouseX, mouseY);
        renderInputs(graphics);
        renderProcess(graphics);
        if (state.plate != null) {
            UiSprites.Tone tone = state.tone == UiSprites.Tone.OK ? UiSprites.Tone.ERROR : state.tone;
            if (UiPanels.plate(graphics, tone, state.plate, x + MID_X, y + PLATE_Y, MID_RIGHT - MID_X)) {
                hints.add(x + MID_X, y + PLATE_Y, MID_RIGHT - MID_X, UiPanels.PLATE_HEIGHT, state.plate);
            }
        }
        renderRightColumn(graphics, state);
        renderBottom(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // every label is drawn by renderBg in the style's own way
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        UiPanels.screen(graphics, x + LIST_X, y + LIST_Y, LIST_W, LIST_H);
        List<RecipeHolder<FabricationRecipe>> recipes = menu.recipes();
        int hovered = recipeAt(mouseX, mouseY);
        boolean running = running();
        for (int row = 0; row < ROWS; row++) {
            int at = startRow + row;
            if (at >= shown.size()) {
                break;
            }
            int index = shown.get(at);
            int rx = x + ROWS_X;
            int ry = y + ROWS_Y + row * ROW_H;
            boolean selected = index == menu.selectedIndex();
            if (selected) {
                UiSprites.draw(graphics, UiSprites.ROW_SELECTED, rx, ry, ROW_W, ROW_H);
            } else if (index == hovered) {
                UiSprites.draw(graphics, UiSprites.ROW_HOVER, rx, ry, ROW_W, ROW_H);
            }
            graphics.fill(rx + 1, ry + 1, rx + 17, ry + 17, UiColors.SLOT);
            ItemStack result = recipes.get(index).value().result();
            graphics.renderItem(result, rx + 1, ry + 1);
            Component name = result.getHoverName();
            hints.line(graphics, name, rx + 20, ry + 1, ROW_W - 22, selected ? UiColors.AMBER_LIGHT : UiColors.TEXT);
            SizedIngredient lack = index < lacking.length ? lacking[index] : null;
            Component status;
            if (lack == null) {
                UiSprites.draw(graphics, UiSprites.ICON_CHECK, rx + 20, ry + 10, 7, 7);
                status = tr(selected && running ? "row.enough_running" : "row.enough");
            } else {
                UiSprites.draw(graphics, UiSprites.ICON_CROSS, rx + 20, ry + 10, 7, 7);
                status = tr("row.lacks", lack.count(), displayStack(lack).getHoverName().getString().toLowerCase(Locale.ROOT));
            }
            hints.line(graphics, status, rx + 30, ry + 10, ROW_W - 32, lack == null ? UiColors.OK : UiColors.ERROR);
        }
        if (shown.isEmpty()) {
            UiText.wrapped(graphics, tr(canMakeOnly ? "list.none_at_hand" : "list.none"), x + ROWS_X + 2, y + ROWS_Y + 2, ROW_W - 4, 36, UiColors.TEXT_DIM);
        }
        int maxRow = maxStartRow();
        if (maxRow > 0) {
            int trackX = x + LIST_X + LIST_W - 4;
            int trackH = ROWS * ROW_H;
            graphics.fill(trackX, y + ROWS_Y, trackX + 2, y + ROWS_Y + trackH, 0xFF1F2623);
            int thumbH = Math.max(8, trackH * ROWS / Math.max(ROWS, shown.size()));
            int thumbY = (trackH - thumbH) * startRow / maxRow;
            graphics.fill(trackX, y + ROWS_Y + thumbY, trackX + 2, y + ROWS_Y + thumbY + thumbH, UiColors.AMBER);
        }
    }

    private void renderInputs(GuiGraphics graphics) {
        UiPanels.label(graphics, tr("label.input"), leftPos + MID_X, topPos + 26, 40);
        FabricationRecipe recipe = selectedRecipe();
        List<Integer> ghostSlots = ghostSlots(recipe);
        for (int i = 0; i < StationBlockEntity.INPUT_SLOTS; i++) {
            com.projectrebirth.client.ui.UiSlots.frame(graphics, ghostSlots.contains(i) ? com.projectrebirth.client.ui.UiSlots.Kind.MISSING
                    : com.projectrebirth.client.ui.UiSlots.Kind.INPUT, leftPos + StationMenu.inputX(i), topPos + StationMenu.inputY(i));
        }
    }

    private void renderProcess(GuiGraphics graphics) {
        int x = leftPos + PROCESS_X;
        int y = topPos + PROCESS_Y;
        UiPanels.screen(graphics, x, y, PROCESS_W, PROCESS_H);
        FabricationRecipe recipe = selectedRecipe();
        boolean running = running();
        Component header = recipe == null
                ? Component.literal("> ").append(tr("process.idle"))
                : Component.literal("> ").append(tr("process." + menu.station().id())).append(": ")
                        .append(UiText.upper(recipe.result().getHoverName()));
        hints.line(graphics, header, x + 6, y + 6, PROCESS_W - 12, recipe == null ? UiColors.AMBER_DIM : UiColors.AMBER);

        long time = System.currentTimeMillis();
        int artX = x + 5;
        int artY = y + 20;
        renderArt(graphics, artX, artY, recipe, running, time);

        if (recipe != null) {
            float progress = progressShown.follow(menu.progress());
            Component percent = Component.literal(Mth.floor(progress * 100) + "%");
            UiText.scaled(graphics, percent, x + ART_W + 12, y + 30, 2.0f, UiColors.AMBER);
            Component left = tr("left", clock(menu.remainingTicks()));
            hints.line(graphics, left, x + ART_W + 12, y + 52, PROCESS_W - ART_W - 16, UiColors.AMBER_DIM);
            UiBars.progress(graphics, x + 6, y + PROCESS_H - 13, PROCESS_W - 12, 6, progress, UiColors.AMBER);
        } else if ((time / 500) % 2 == 0) {
            graphics.fill(x + ART_W + 12, y + 44, x + ART_W + 18, y + 46, UiColors.AMBER_DIM);
        }
    }

    /** The picture of the process: each station has its own (StationArt). */
    private void renderArt(GuiGraphics graphics, int x, int y, @Nullable FabricationRecipe recipe, boolean running, long time) {
        StationArt.draw(graphics, menu.station(), x, y, recipe == null ? null : recipe.result(), running, time);
    }

    private void renderRightColumn(GuiGraphics graphics, State state) {
        int x = leftPos + RIGHT_X;
        int y = topPos;
        UiPanels.label(graphics, tr("label.output"), x, y + 26, RIGHT_W);
        UiSprites.draw(graphics, UiSprites.SLOT_OUTPUT, leftPos + StationMenu.OUTPUT_X - 5, topPos + StationMenu.OUTPUT_Y - 5, 26, 26);

        UiPanels.label(graphics, tr("label.energy"), x, y + 67, RIGHT_W);
        int capacity = menu.energyCapacity();
        float fraction = capacity <= 0 ? 0.0f : energyShown.follow(Math.min(1.0f, menu.energy() / (float) capacity));
        UiBars.vertical(graphics, x, y + GAUGE_Y, GAUGE_W, GAUGE_H, fraction, UiColors.ENERGY, UiColors.ENERGY_DARK);
        int tx = x + GAUGE_W + 5;
        int tw = RIGHT_W - GAUGE_W - 5;
        hints.line(graphics, Component.literal(EnergyUtil.format(menu.energy())), tx, y + GAUGE_Y + 1, tw, UiColors.ENERGY_TEXT);
        hints.line(graphics, tr("energy.of", EnergyUtil.format(capacity)), tx, y + GAUGE_Y + 11, tw, UiColors.TEXT_SECONDARY);
        hints.line(graphics, tr("energy.in", EnergyUtil.format(income())), tx, y + GAUGE_Y + 25, tw, UiColors.OK);
        hints.line(graphics, tr("energy.out", EnergyUtil.format(use())), tx, y + GAUGE_Y + 35, tw, use() > 0 ? UiColors.WARNING : UiColors.TEXT_DIM);

        if (UiPanels.badge(graphics, state.tone, state.badge, x, y + PLATE_Y, RIGHT_W)) {
            hints.add(x, y + PLATE_Y, RIGHT_W, UiPanels.PLATE_HEIGHT, state.badge);
        }
    }

    private void renderBottom(GuiGraphics graphics) {
        int x = leftPos;
        int y = topPos;
        UiPanels.label(graphics, playerInventoryTitle, x + StationMenu.INVENTORY_X - 1, y + INFO_Y, 160);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                UiSprites.draw(graphics, UiSprites.SLOT, x + StationMenu.INVENTORY_X - 1 + col * 18, y + StationMenu.INVENTORY_Y - 1 + row * 18, 18, 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            UiSprites.draw(graphics, UiSprites.SLOT, x + StationMenu.INVENTORY_X - 1 + col * 18, y + StationMenu.INVENTORY_Y + 57, 18, 18);
        }

        int ix = x + INFO_X;
        int iy = y + INFO_Y;
        int iw = WIDTH - 7 - INFO_X;
        int ih = StationMenu.INVENTORY_Y + 75 - INFO_Y;
        UiPanels.screen(graphics, ix, iy, iw, ih);
        FabricationRecipe recipe = selectedRecipe();
        if (recipe == null) {
            hints.line(graphics, UiText.upper(title), ix + 6, iy + 6, iw - 12, UiColors.AMBER);
            UiText.wrapped(graphics, tr("info.idle"), ix + 6, iy + 18, iw - 12, 45, UiColors.SCREEN_TEXT);
        } else {
            hints.line(graphics, UiText.upper(recipe.result().getHoverName()), ix + 6, iy + 6, iw - 12, UiColors.AMBER);
            UiText.line(graphics, tr("needs"), ix + 6, iy + 18, iw - 12, UiColors.TEXT_SECONDARY);
            List<Cover> covers = cover(recipe);
            int colW = (iw - 12) / 2;
            for (int i = 0; i < covers.size(); i++) {
                Cover cover = covers.get(i);
                int lx = ix + 6 + (i % 2) * colW;
                int ly = iy + 28 + (i / 2) * 10;
                boolean enough = cover.have >= cover.ingredient.count();
                UiSprites.draw(graphics, enough ? UiSprites.ICON_CHECK : UiSprites.ICON_CROSS, lx, ly + 1, 7, 7);
                Component line = Component.literal(cover.have + "/" + cover.ingredient.count() + "  ")
                        .append(displayStack(cover.ingredient).getHoverName());
                hints.line(graphics, line, lx + 10, ly, colW - 14, enough ? UiColors.SCREEN_TEXT : UiColors.ERROR);
            }
            int time = StationBlockEntity.productionTime(recipe);
            int perTick = StationBlockEntity.energyPerTick();
            Component cycle = perTick > 0
                    ? tr("info.cycle", clock(time), EnergyUtil.format(perTick), EnergyUtil.format(time * perTick))
                    : tr("info.cycle_free", clock(time));
            hints.line(graphics, cycle, ix + 6, iy + 60, iw - 12, UiColors.TEXT_SECONDARY);
        }
        hints.line(graphics, tr("info.automation"), ix + 6, iy + ih - 13, iw - 12, UiColors.TEXT_DIM);
    }

    // ---------------------------------------------------------------- missing ingredients

    /** The free input slots that show a missing ingredient, in the order of {@link FabricationRecipe#missing}. */
    private List<Integer> ghostSlots(@Nullable FabricationRecipe recipe) {
        List<Integer> slots = new ArrayList<>();
        if (recipe == null) {
            return slots;
        }
        int count = recipe.missing(inputs()).size();
        for (int slot = 0; slot < StationBlockEntity.INPUT_SLOTS && slots.size() < count; slot++) {
            if (!menu.getSlot(slot).hasItem()) {
                slots.add(slot);
            }
        }
        return slots;
    }

    /** Each missing ingredient as a faint ghost in a free input slot, with "have/need" in red. */
    private void renderGhosts(GuiGraphics graphics, int mouseX, int mouseY) {
        FabricationRecipe recipe = selectedRecipe();
        if (recipe == null) {
            return;
        }
        List<SizedIngredient> missing = recipe.missing(inputs());
        List<Integer> slots = ghostSlots(recipe);
        for (int i = 0; i < Math.min(missing.size(), slots.size()); i++) {
            SizedIngredient lack = missing.get(i);
            int need = need(recipe, lack);
            int x = leftPos + StationMenu.inputX(slots.get(i));
            int y = topPos + StationMenu.inputY(slots.get(i));
            com.projectrebirth.client.ui.UiSlots.ghost(graphics, displayStack(lack), x, y);
            com.projectrebirth.client.ui.UiSlots.count(graphics, Component.literal((need - lack.count()) + "/" + need), x, y, UiColors.ERROR);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16 && menu.getCarried().isEmpty()) {
                hints.add(x, y, 16, 16, tr("ghost", need - lack.count(), need, displayStack(lack).getHoverName()));
            }
        }
    }

    /** How many of a missing ingredient the recipe needs in all. */
    private static int need(FabricationRecipe recipe, SizedIngredient lack) {
        for (SizedIngredient ingredient : recipe.ingredients()) {
            if (ingredient.ingredient().equals(lack.ingredient())) {
                return ingredient.count();
            }
        }
        return lack.count();
    }

    /** One of the ingredient's items, switching every second for tags with several items. */
    private static ItemStack displayStack(SizedIngredient ingredient) {
        ItemStack[] items = ingredient.ingredient().getItems();
        if (items.length == 0) {
            return ItemStack.EMPTY;
        }
        long second = System.currentTimeMillis() / 1000L;
        return items[(int) (second % items.length)];
    }

    // ---------------------------------------------------------------- tooltips

    private void renderOwnTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null && hoveredSlot.hasItem() || !menu.getCarried().isEmpty()) {
            return;
        }
        int index = recipeAt(mouseX, mouseY);
        if (index >= 0) {
            graphics.renderComponentTooltip(font, recipeTooltip(menu.recipes().get(index).value()), mouseX, mouseY);
            return;
        }
        if (mouseX >= leftPos + RIGHT_X && mouseX < leftPos + RIGHT_X + GAUGE_W && mouseY >= topPos + GAUGE_Y && mouseY < topPos + GAUGE_Y + GAUGE_H) {
            List<Component> lines = energyTooltip(menu.energy(), menu.energyCapacity(), StationBlockEntity.energyPerTick());
            lines.add(tr("energy.income_tip", EnergyUtil.format(income())).withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        Component hint = hints.at(mouseX, mouseY);
        if (hint != null) {
            graphics.renderTooltip(font, hint, mouseX, mouseY);
        }
    }

    private List<Component> recipeTooltip(FabricationRecipe recipe) {
        List<Component> lines = new ArrayList<>();
        lines.add(recipe.result().getHoverName());
        lines.add(tr("needs").withStyle(ChatFormatting.GRAY));
        for (SizedIngredient ingredient : recipe.ingredients()) {
            lines.add(tr("ingredient", ingredient.count(), displayStack(ingredient).getHoverName()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(tr("time", seconds(StationBlockEntity.productionTime(recipe))).withStyle(ChatFormatting.DARK_GRAY));
        if (StationBlockEntity.energyPerTick() > 0) {
            lines.add(tr("energy", EnergyUtil.format(StationBlockEntity.productionTime(recipe) * StationBlockEntity.energyPerTick()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    /** "Energy: 1 200 / 20 000 FE", plus the use per tick when there is one. */
    static List<Component> energyTooltip(int energy, int capacity, int perTick) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.projectrebirth.energy", EnergyUtil.format(energy), EnergyUtil.format(capacity)));
        if (perTick > 0) {
            lines.add(Component.translatable("screen.projectrebirth.energy.use", perTick).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0f);
    }

    /** "1:50". */
    private static String clock(int ticks) {
        int seconds = (ticks + 19) / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    // ---------------------------------------------------------------- input

    private int maxStartRow() {
        return Math.max(0, shown.size() - ROWS);
    }

    /** Index (in the menu's list) of the recipe under the mouse, or -1. */
    private int recipeAt(double mouseX, double mouseY) {
        double rx = mouseX - leftPos - ROWS_X;
        double ry = mouseY - topPos - ROWS_Y;
        if (rx < 0 || ry < 0 || rx >= ROW_W || ry >= ROWS * ROW_H) {
            return -1;
        }
        int at = startRow + (int) ry / ROW_H;
        return at < shown.size() ? shown.get(at) : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = recipeAt(mouseX, mouseY);
        if (index >= 0 && minecraft != null && minecraft.gameMode != null) {
            com.projectrebirth.client.ui.UiSounds.CLICK.play();
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
            search.setFocused(false);
            return true;
        }
        if (!search.isMouseOver(mouseX, mouseY)) {
            search.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxRow = maxStartRow();
        if (maxRow > 0 && mouseX >= leftPos + LIST_X && mouseX < leftPos + LIST_X + LIST_W
                && mouseY >= topPos + LIST_Y && mouseY < topPos + LIST_Y + LIST_H) {
            startRow = Mth.clamp(startRow - (int) Math.signum(scrollY), 0, maxRow);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
