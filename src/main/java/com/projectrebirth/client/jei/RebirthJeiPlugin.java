package com.projectrebirth.client.jei;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.client.screen.StationScreen;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.FabricationRecipe;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.TechItems;
import com.projectrebirth.technology.TechRecipes;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * JEI integration: shows the recipes of each production station. JEI finds this class by its annotation; without
 * JEI nothing ever loads it, so the mod does not need JEI.
 */
@JeiPlugin
public class RebirthJeiPlugin implements IModPlugin {
    private static final Map<Station, RecipeType<FabricationRecipe>> TYPES = new EnumMap<>(Station.class);

    static {
        for (Station station : Station.values()) {
            TYPES.put(station, RecipeType.create(ProjectRebirth.MOD_ID, station.id(), FabricationRecipe.class));
        }
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ProjectRebirth.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        for (Station station : Station.values()) {
            registration.addRecipeCategories(new StationRecipeCategory(registration.getJeiHelpers().getGuiHelper(), station, TYPES.get(station)));
        }
    }

    /** Recipes are synced to the client with the server's data packs, so data pack changes show up here too. */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        for (Station station : Station.values()) {
            registration.addRecipes(TYPES.get(station), TechRecipes.recipesFor(Minecraft.getInstance().level, station).stream()
                    .map(RecipeHolder::value)
                    .toList());
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (Station station : Station.values()) {
            registration.addRecipeCatalyst(new ItemStack(TechItems.STATIONS.get(station).get()), TYPES.get(station));
        }
    }

    /** The ability icons of the armor slot are not real items: keep them out of the item list. */
    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, java.util.Arrays.stream(ArmorVariant.values())
                .map(variant -> new ItemStack(PowerArmorRegistry.abilityItem(variant))).toList());
    }

    /** Clicking the progress arrow in a station window opens the recipes of that station. */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(StationScreen.class, new IGuiContainerHandler<>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(StationScreen screen, double mouseX, double mouseY) {
                return List.of(IGuiClickableArea.createBasic(StationScreen.ARROW_X, StationScreen.ARROW_Y, 24, 16,
                        TYPES.get(screen.getMenu().station())));
            }
        });
    }
}
