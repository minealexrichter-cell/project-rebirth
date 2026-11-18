package com.projectrebirth.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.technology.FabricationRecipe;
import com.projectrebirth.technology.StationBlockEntity;
import com.projectrebirth.technology.StationMenu;
import com.projectrebirth.technology.energy.EnergyUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import static com.projectrebirth.client.screen.GuiShapes.panel;
import static com.projectrebirth.client.screen.GuiShapes.playerInventory;
import static com.projectrebirth.client.screen.GuiShapes.slot;
import static com.projectrebirth.client.screen.GuiShapes.sunken;

/**
 * Station window: the mod's own window parts (see GuiAtlas) plus vanilla recipe button sprites.
 * Top: recipe list. Middle: status line, input slots with "ghost" images of the missing ingredients, progress
 * arrow and output. Bottom: player inventory.
 */
public class StationScreen extends AbstractContainerScreen<StationMenu> {
    private static final ResourceLocation RECIPE_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/recipe");
    private static final ResourceLocation RECIPE_SELECTED_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/recipe_selected");
    private static final ResourceLocation RECIPE_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/recipe_highlighted");
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/scroller");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/scroller_disabled");

    private static final int GRID_X = 8;
    private static final int GRID_Y = 17;
    private static final int COLS = 8;
    private static final int ROWS = 2;
    private static final int CELL = 18;
    private static final int SCROLL_X = 156;
    private static final int STATUS_Y = 57;
    public static final int ARROW_X = 90;
    public static final int ARROW_Y = StationMenu.OUTPUT_Y;
    private static final int ENERGY_X = 158;
    private static final int ENERGY_Y = 62;
    private static final int ENERGY_W = 10;
    private static final int ENERGY_H = 54;

    private int startRow;

    public StationScreen(StationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, GuiShapes.fit(title, 160));
        imageWidth = 176;
        imageHeight = StationMenu.INVENTORY_Y + 82;
        inventoryLabelY = StationMenu.INVENTORY_Y - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderGhosts(graphics);
        renderTooltip(graphics, mouseX, mouseY);
        renderRecipeTooltip(graphics, mouseX, mouseY);
        renderArrowTooltip(graphics, mouseX, mouseY);
        if (GuiShapes.inside(mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_Y, ENERGY_W, ENERGY_H)) {
            graphics.renderComponentTooltip(font, energyTooltip(menu.energy(), menu.energyCapacity(), StationBlockEntity.energyPerTick()), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        panel(graphics, x, y, imageWidth, imageHeight);
        for (int i = 0; i < StationBlockEntity.INPUT_SLOTS; i++) {
            slot(graphics, x + StationMenu.INPUT_X + (i % 3) * 18, y + StationMenu.INPUT_Y + (i / 3) * 18);
        }
        GuiAtlas.outputSlot(graphics, x + StationMenu.OUTPUT_X - 5, y + StationMenu.OUTPUT_Y - 5);
        playerInventory(graphics, x + 8, y + StationMenu.INVENTORY_Y);
        GuiAtlas.arrow(graphics, x + ARROW_X, y + ARROW_Y, menu.progress());
        GuiShapes.energyBar(graphics, x + ENERGY_X, y + ENERGY_Y, ENERGY_W, ENERGY_H, menu.energy(), menu.energyCapacity());
        renderRecipes(graphics, mouseX, mouseY);
        renderScrollbar(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        Component status = status();
        graphics.drawString(font, status, (imageWidth - font.width(status)) / 2, STATUS_Y, 0x404040, false);
    }

    private Component status() {
        FabricationRecipe recipe = selectedRecipe();
        if (recipe == null) {
            return Component.translatable(menu.movedRecipe() ? "screen.projectrebirth.station.moved" : "screen.projectrebirth.station.select");
        }
        if (!recipe.missing(inputs()).isEmpty()) {
            return Component.translatable("screen.projectrebirth.station.missing");
        }
        ItemStack output = menu.getSlot(StationBlockEntity.OUTPUT_SLOT).getItem();
        boolean fits = output.isEmpty() || ItemStack.isSameItemSameComponents(output, recipe.result())
                && output.getCount() + recipe.result().getCount() <= output.getMaxStackSize();
        if (!fits) {
            return Component.translatable("screen.projectrebirth.station.output_full");
        }
        if (menu.emiPaused()) {
            return Component.translatable("screen.projectrebirth.station.emi");
        }
        if (menu.energy() < StationBlockEntity.energyPerTick()) {
            return Component.translatable("screen.projectrebirth.station.no_energy");
        }
        return Component.translatable("screen.projectrebirth.station.working", Mth.floor(menu.progress() * 100));
    }

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

    // ---------- recipe list ----------

    private void renderRecipes(GuiGraphics graphics, int mouseX, int mouseY) {
        List<RecipeHolder<FabricationRecipe>> recipes = menu.recipes();
        int hovered = recipeAt(mouseX, mouseY);
        for (int cell = 0; cell < COLS * ROWS; cell++) {
            int index = startRow * COLS + cell;
            int cx = leftPos + GRID_X + (cell % COLS) * CELL;
            int cy = topPos + GRID_Y + (cell / COLS) * CELL;
            if (index >= recipes.size()) {
                sunken(graphics, cx, cy, CELL, CELL);
                continue;
            }
            ResourceLocation sprite = index == menu.selectedIndex() ? RECIPE_SELECTED_SPRITE
                    : index == hovered ? RECIPE_HIGHLIGHTED_SPRITE : RECIPE_SPRITE;
            graphics.blitSprite(sprite, cx, cy, CELL, CELL);
            ItemStack result = recipes.get(index).value().result();
            graphics.renderItem(result, cx + 1, cy + 1);
            graphics.renderItemDecorations(font, result, cx + 1, cy + 1);
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int maxRow = maxStartRow();
        int trackHeight = ROWS * CELL;
        sunken(graphics, leftPos + SCROLL_X, topPos + GRID_Y, 14, trackHeight);
        int thumbY = maxRow == 0 ? 0 : (trackHeight - 15) * startRow / maxRow;
        graphics.blitSprite(maxRow == 0 ? SCROLLER_DISABLED_SPRITE : SCROLLER_SPRITE,
                leftPos + SCROLL_X + 1, topPos + GRID_Y + thumbY, 12, 15);
    }

    private int maxStartRow() {
        int rows = (menu.recipes().size() + COLS - 1) / COLS;
        return Math.max(0, rows - ROWS);
    }

    /** Index of the recipe under the mouse, or -1. */
    private int recipeAt(double mouseX, double mouseY) {
        double rx = mouseX - leftPos - GRID_X;
        double ry = mouseY - topPos - GRID_Y;
        if (rx < 0 || ry < 0 || rx >= COLS * CELL || ry >= ROWS * CELL) {
            return -1;
        }
        int index = (startRow + (int) ry / CELL) * COLS + (int) rx / CELL;
        return index < menu.recipes().size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = recipeAt(mouseX, mouseY);
        if (index >= 0 && minecraft != null && minecraft.gameMode != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0f));
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxRow = maxStartRow();
        if (maxRow > 0) {
            startRow = Mth.clamp(startRow - (int) Math.signum(scrollY), 0, maxRow);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderRecipeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int index = recipeAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        FabricationRecipe recipe = menu.recipes().get(index).value();
        List<Component> lines = new ArrayList<>();
        lines.add(recipe.result().getHoverName());
        lines.add(Component.translatable("screen.projectrebirth.station.needs").withStyle(ChatFormatting.GRAY));
        for (SizedIngredient ingredient : recipe.ingredients()) {
            lines.add(Component.translatable("screen.projectrebirth.station.ingredient", ingredient.count(),
                    displayStack(ingredient).getHoverName()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("screen.projectrebirth.station.time", seconds(StationBlockEntity.productionTime(recipe)))
                .withStyle(ChatFormatting.DARK_GRAY));
        if (StationBlockEntity.energyPerTick() > 0) {
            lines.add(Component.translatable("screen.projectrebirth.station.energy",
                    EnergyUtil.format(StationBlockEntity.productionTime(recipe) * StationBlockEntity.energyPerTick())).withStyle(ChatFormatting.DARK_GRAY));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    // ---------- progress ----------

    private void renderArrowTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + ARROW_X;
        int y = topPos + ARROW_Y;
        if (mouseX >= x && mouseX < x + 24 && mouseY >= y && mouseY < y + 16 && selectedRecipe() != null) {
            graphics.renderTooltip(font, Component.translatable("screen.projectrebirth.station.remaining",
                    seconds(menu.remainingTicks())), mouseX, mouseY);
        }
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
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0f);
    }

    // ---------- missing ingredients ----------

    /** Shows each missing ingredient, with the missing count, in a free input slot. */
    private void renderGhosts(GuiGraphics graphics) {
        FabricationRecipe recipe = selectedRecipe();
        if (recipe == null) {
            return;
        }
        List<SizedIngredient> missing = recipe.missing(inputs());
        int slot = 0;
        for (SizedIngredient ingredient : missing) {
            while (slot < StationBlockEntity.INPUT_SLOTS && menu.getSlot(slot).hasItem()) {
                slot++;
            }
            if (slot >= StationBlockEntity.INPUT_SLOTS) {
                return;
            }
            int x = leftPos + StationMenu.INPUT_X + (slot % 3) * 18;
            int y = topPos + StationMenu.INPUT_Y + (slot / 3) * 18;
            ItemStack stack = displayStack(ingredient).copyWithCount(ingredient.count());
            graphics.renderFakeItem(stack, x, y);
            graphics.fill(RenderType.guiGhostRecipeOverlay(), x, y, x + 16, y + 16, 0x80888888);
            graphics.renderItemDecorations(font, stack, x, y);
            slot++;
        }
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
}
