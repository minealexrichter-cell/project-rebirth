package com.projectrebirth.client.jei;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.client.multiblock.MultiblockView;
import com.projectrebirth.multiblock.AssemblyRecipe;
import com.projectrebirth.multiblock.MultiblockRegistry;
import com.projectrebirth.multiblock.MultiblockSchema;
import com.projectrebirth.technology.energy.EnergyUtil;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * JEI: "Multiblock assembly" (1.7.0, block A): the item to start from and the result, the plan of the multiblock layer by layer
 * (the layers change by themselves), and each stage with its materials, time and energy.
 */
public class AssemblyRecipeCategory implements IRecipeCategory<AssemblyRecipe> {
    public static final RecipeType<AssemblyRecipe> TYPE = RecipeType.create(ProjectRebirth.MOD_ID, "assembly", AssemblyRecipe.class);
    private static final int WIDTH = 176;
    private static final int TOP = 52;
    private static final int ROW = 18;
    private static final int PER_ROW = 6;
    private static final int MAX_ROWS = 6;

    private final IDrawable icon;
    private final IDrawable arrow;

    public AssemblyRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Items.IRON_BLOCK));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<AssemblyRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.projectrebirth.assembly");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return TOP + MAX_ROWS * ROW;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AssemblyRecipe recipe, IFocusGroup focuses) {
        recipe.input().ifPresent(input -> builder.addSlot(RecipeIngredientRole.INPUT, 1, 16).setStandardSlotBackground().addIngredients(input));
        builder.addSlot(RecipeIngredientRole.OUTPUT, 52, 16).setOutputSlotBackground().addItemStack(recipe.result());
        int y = TOP;
        for (AssemblyRecipe.Stage stage : recipe.stages()) {
            List<SizedIngredient> materials = stage.materials();
            for (int i = 0; i < materials.size(); i++) {
                SizedIngredient material = materials.get(i);
                List<ItemStack> stacks = Arrays.stream(material.ingredient().getItems()).map(stack -> stack.copyWithCount(material.count())).toList();
                builder.addSlot(RecipeIngredientRole.INPUT, 66 + (i % PER_ROW) * 18, y + 1 + (i / PER_ROW) * ROW).setStandardSlotBackground().addItemStacks(stacks);
            }
            y += ROW * Math.max(1, (materials.size() + PER_ROW - 1) / PER_ROW);
            if (y >= TOP + MAX_ROWS * ROW) {
                break;
            }
        }
    }

    @Override
    public void draw(AssemblyRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        arrow.draw(graphics, 22, 17);
        float seconds = recipe.totalTime() / 20.0f;
        graphics.drawString(font, Component.translatable("jei.projectrebirth.assembly.total", String.format(Locale.ROOT, "%.0f", seconds)), 0, 38, 0xFF808080, false);
        // the plan, a layer at a time
        MultiblockSchema schema = minecraft.level == null ? null : MultiblockRegistry.schema(minecraft.level.registryAccess(), recipe.multiblock());
        if (schema != null) {
            int layer = (int) ((minecraft.level.getGameTime() / 40) % schema.height());
            int cell = Math.max(4, Math.min(8, 40 / Math.max(schema.width(), schema.depth())));
            int px = WIDTH - schema.width() * cell - 2;
            MultiblockView.drawLayer(graphics, schema, layer, px, 2, cell);
            Component label = Component.translatable("jei.projectrebirth.assembly.layer", layer + 1, schema.height());
            graphics.drawString(font, label, WIDTH - font.width(label) - 2, 4 + schema.depth() * cell, 0xFF808080, false);
        }
        int y = TOP;
        int n = 1;
        for (AssemblyRecipe.Stage stage : recipe.stages()) {
            Component name = Component.translatable("jei.projectrebirth.assembly.stage", n++, Component.translatable("screen.projectrebirth.multiblock.stage." + stage.name()));
            graphics.drawString(font, font.plainSubstrByWidth(name.getString(), 64), 0, y + 1, 0xFF404040, false);
            String cost = String.format(Locale.ROOT, "%.0f s", stage.time() / 20.0f) + (stage.energy() > 0 ? ", " + EnergyUtil.format(stage.energy() * stage.time()) + " FE" : "");
            graphics.drawString(font, font.plainSubstrByWidth(cost, 64), 0, y + 10, 0xFF808080, false);
            y += ROW * Math.max(1, (stage.materials().size() + PER_ROW - 1) / PER_ROW);
            if (y >= TOP + MAX_ROWS * ROW) {
                break;
            }
        }
    }
}
