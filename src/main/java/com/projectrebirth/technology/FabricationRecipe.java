package com.projectrebirth.technology;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * A recipe made at a production station: up to six ingredients with counts, in any input slots, one result and
 * a base time in ticks. {@code sort} orders the recipes in the station's list.
 */
public record FabricationRecipe(Station station, List<SizedIngredient> ingredients, ItemStack result, int time, int sort)
        implements Recipe<FabricationInput> {
    public static final int MAX_INGREDIENTS = 6;

    public static final MapCodec<FabricationRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Station.CODEC.fieldOf("station").forGetter(FabricationRecipe::station),
            SizedIngredient.FLAT_CODEC.listOf().validate(FabricationRecipe::checkSize).fieldOf("ingredients")
                    .forGetter(FabricationRecipe::ingredients),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(FabricationRecipe::result),
            ExtraCodecs.POSITIVE_INT.fieldOf("time").forGetter(FabricationRecipe::time),
            Codec.INT.optionalFieldOf("sort", 0).forGetter(FabricationRecipe::sort)
    ).apply(instance, FabricationRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FabricationRecipe> STREAM_CODEC = StreamCodec.composite(
            Station.STREAM_CODEC, FabricationRecipe::station,
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), FabricationRecipe::ingredients,
            ItemStack.STREAM_CODEC, FabricationRecipe::result,
            ByteBufCodecs.VAR_INT, FabricationRecipe::time,
            ByteBufCodecs.VAR_INT, FabricationRecipe::sort,
            FabricationRecipe::new);

    private static DataResult<List<SizedIngredient>> checkSize(List<SizedIngredient> list) {
        return list.isEmpty() || list.size() > MAX_INGREDIENTS
                ? DataResult.error(() -> "A station recipe needs 1 to " + MAX_INGREDIENTS + " ingredients, got " + list.size())
                : DataResult.success(list);
    }

    @Override
    public boolean matches(FabricationInput input, Level level) {
        return input.station() == station && missing(input.items()).isEmpty();
    }

    /**
     * Ingredients (with the missing counts) that the given stacks do not cover. Each item is counted only once,
     * so two ingredients never share the same items.
     */
    public List<SizedIngredient> missing(List<ItemStack> stacks) {
        List<ItemStack> left = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            left.add(stack.copy());
        }
        List<SizedIngredient> missing = new ArrayList<>();
        for (SizedIngredient ingredient : ingredients) {
            int needed = take(left, ingredient);
            if (needed > 0) {
                missing.add(new SizedIngredient(ingredient.ingredient(), needed));
            }
        }
        return missing;
    }

    /** Removes the ingredients from the stacks, which must contain them. */
    public void consume(List<ItemStack> stacks) {
        for (SizedIngredient ingredient : ingredients) {
            take(stacks, ingredient);
        }
    }

    /** Takes up to the ingredient's count from the stacks; returns how many are still missing. */
    private static int take(List<ItemStack> stacks, SizedIngredient ingredient) {
        int needed = ingredient.count();
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

    @Override
    public ItemStack assemble(FabricationInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    /** Kept out of the vanilla recipe book, which has no category for station recipes. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TechRecipes.FABRICATION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return TechRecipes.FABRICATION.get();
    }
}
