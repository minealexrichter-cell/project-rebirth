package com.projectrebirth.client.jei;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.projectrebirth.technology.FabricationRecipe;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.StationBlockEntity;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.technology.TechItems;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/** One JEI category per production station: up to six counted ingredients, an arrow with the time, the result. */
public class StationRecipeCategory implements IRecipeCategory<FabricationRecipe> {
    private static final int WIDTH = 120;
    private static final int HEIGHT = 58;

    private final Station station;
    private final RecipeType<FabricationRecipe> type;
    private final IDrawable icon;
    private final IDrawable arrow;

    public StationRecipeCategory(IGuiHelper guiHelper, Station station, RecipeType<FabricationRecipe> type) {
        this.station = station;
        this.type = type;
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(TechItems.STATIONS.get(station).get()));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<FabricationRecipe> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.projectrebirth." + station.id());
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
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FabricationRecipe recipe, IFocusGroup focuses) {
        List<SizedIngredient> ingredients = recipe.ingredients();
        for (int i = 0; i < ingredients.size(); i++) {
            SizedIngredient ingredient = ingredients.get(i);
            List<ItemStack> stacks = Arrays.stream(ingredient.ingredient().getItems())
                    .map(stack -> stack.copyWithCount(ingredient.count()))
                    .toList();
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % 3) * 18, 1 + (i / 3) * 18)
                    .setStandardSlotBackground()
                    .addItemStacks(stacks);
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 19, 10)
                .setOutputSlotBackground()
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(FabricationRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 60, 10);
        float seconds = StationBlockEntity.productionTime(recipe) / 20.0f;
        Component time = Component.translatable("screen.projectrebirth.station.time",
                String.format(Locale.ROOT, "%.1f", seconds));
        graphics.drawString(Minecraft.getInstance().font, time, 0, 39, 0xFF808080, false);
        int perTick = StationBlockEntity.energyPerTick();
        if (perTick > 0) {
            Component energy = Component.translatable("screen.projectrebirth.station.energy",
                    EnergyUtil.format(StationBlockEntity.productionTime(recipe) * perTick));
            graphics.drawString(Minecraft.getInstance().font, energy, 0, 49, 0xFF808080, false);
        }
    }
}
