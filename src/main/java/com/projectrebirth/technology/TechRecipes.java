package com.projectrebirth.technology;

import java.util.Comparator;
import java.util.List;

import com.mojang.serialization.MapCodec;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TechRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ProjectRebirth.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<FabricationRecipe>> FABRICATION =
            TYPES.register("fabrication", () -> RecipeType.simple(ProjectRebirth.id("fabrication")));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FabricationRecipe>> FABRICATION_SERIALIZER =
            SERIALIZERS.register("fabrication", () -> new RecipeSerializer<>() {
                @Override
                public MapCodec<FabricationRecipe> codec() {
                    return FabricationRecipe.CODEC;
                }

                @Override
                public StreamCodec<RegistryFriendlyByteBuf, FabricationRecipe> streamCodec() {
                    return FabricationRecipe.STREAM_CODEC;
                }
            });

    private TechRecipes() {
    }

    /** The station's recipes in display order. Recipes are synced to clients, so both sides get the same list. */
    public static List<RecipeHolder<FabricationRecipe>> recipesFor(Level level, Station station) {
        return level.getRecipeManager().getAllRecipesFor(FABRICATION.get()).stream()
                .filter(holder -> holder.value().station() == station)
                .sorted(Comparator.comparingInt((RecipeHolder<FabricationRecipe> holder) -> holder.value().sort())
                        .thenComparing(holder -> holder.id().toString()))
                .toList();
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
        SERIALIZERS.register(modEventBus);
    }
}
