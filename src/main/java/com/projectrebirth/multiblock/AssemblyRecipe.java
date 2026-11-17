package com.projectrebirth.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.gametest.GameTestHooks;

/**
 * A recipe of a multiblock (1.7.0, block A): the item it starts from (if any), then stages, each with its materials, time,
 * energy per tick and the stage of the model it shows; at the end the result. {@code keep} says what the result keeps of the
 * item it was made from: nothing, its upgrades (a better body keeps the gadgets), or all its data.
 */
public record AssemblyRecipe(ResourceLocation multiblock, Optional<Ingredient> input, String keep, List<Stage> stages, ItemStack result, int sort,
                             boolean testOnly) implements Recipe<AssemblyRecipe.Input> {
    public static final String KEEP_NONE = "none";
    public static final String KEEP_UPGRADES = "upgrades";
    public static final String KEEP_ALL = "all";
    /** 1.7.0, block E: the body keeps everything and takes the AI core and the module of the materials: an android. */
    public static final String KEEP_ANDROID = "android";
    /** The android gives its core and module back and is a body again. */
    public static final String KEEP_STRIP_ANDROID = "strip_android";
    public static final int MAX_MATERIALS = 9;

    /**
     * One stage: its name (frame, actuators, electronics, plating, ai_core, calibration), materials, ticks, energy per tick and
     * the stage of the model under assembly it brings out.
     */
    public record Stage(String name, List<SizedIngredient> materials, int time, int energy, int modelStage) {
        public static final Codec<Stage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Stage::name),
                SizedIngredient.FLAT_CODEC.listOf().optionalFieldOf("materials", List.of()).forGetter(Stage::materials),
                ExtraCodecs.POSITIVE_INT.fieldOf("time").forGetter(Stage::time),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("energy", 0).forGetter(Stage::energy),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("model_stage", 0).forGetter(Stage::modelStage)).apply(i, Stage::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Stage> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Stage::name,
                SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), Stage::materials,
                ByteBufCodecs.VAR_INT, Stage::time,
                ByteBufCodecs.VAR_INT, Stage::energy,
                ByteBufCodecs.VAR_INT, Stage::modelStage,
                Stage::new);

        /** The materials that the stacks do not cover, with the counts missing; each item counted once. */
        public List<SizedIngredient> missing(List<ItemStack> stacks) {
            List<ItemStack> left = new ArrayList<>();
            stacks.forEach(stack -> left.add(stack.copy()));
            List<SizedIngredient> out = new ArrayList<>();
            for (SizedIngredient material : materials) {
                int needed = take(left, material);
                if (needed > 0) {
                    out.add(new SizedIngredient(material.ingredient(), needed));
                }
            }
            return out;
        }

        /** Takes the materials out of the stacks (which hold them) and returns what was taken. */
        public List<ItemStack> consume(List<ItemStack> stacks) {
            List<ItemStack> taken = new ArrayList<>();
            for (SizedIngredient material : materials) {
                int needed = material.count();
                for (ItemStack stack : stacks) {
                    if (needed == 0) {
                        break;
                    }
                    if (!stack.isEmpty() && material.ingredient().test(stack)) {
                        int used = Math.min(needed, stack.getCount());
                        taken.add(stack.copyWithCount(used));
                        stack.shrink(used);
                        needed -= used;
                    }
                }
            }
            return taken;
        }

        private static int take(List<ItemStack> stacks, SizedIngredient material) {
            int needed = material.count();
            for (ItemStack stack : stacks) {
                if (needed == 0) {
                    break;
                }
                if (!stack.isEmpty() && material.ingredient().test(stack)) {
                    int used = Math.min(needed, stack.getCount());
                    stack.shrink(used);
                    needed -= used;
                }
            }
            return needed;
        }
    }

    /** What the controller holds: the item it starts from and the material slots. */
    public record Input(ItemStack base, List<ItemStack> materials) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return index == 0 ? base : materials.get(index - 1);
        }

        @Override
        public int size() {
            return 1 + materials.size();
        }
    }

    public static final MapCodec<AssemblyRecipe> CODEC = RecordCodecBuilder.<AssemblyRecipe>mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("multiblock").forGetter(AssemblyRecipe::multiblock),
            Ingredient.CODEC_NONEMPTY.optionalFieldOf("input").forGetter(AssemblyRecipe::input),
            Codec.STRING.optionalFieldOf("keep", KEEP_NONE).forGetter(AssemblyRecipe::keep),
            Stage.CODEC.listOf().fieldOf("stages").forGetter(AssemblyRecipe::stages),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(AssemblyRecipe::result),
            Codec.INT.optionalFieldOf("sort", 0).forGetter(AssemblyRecipe::sort),
            Codec.BOOL.optionalFieldOf("test_only", false).forGetter(AssemblyRecipe::testOnly)).apply(i, AssemblyRecipe::new))
            .validate(AssemblyRecipe::check);

    private static DataResult<AssemblyRecipe> check(AssemblyRecipe recipe) {
        if (recipe.stages.isEmpty()) {
            return DataResult.error(() -> "An assembly needs at least one stage");
        }
        for (Stage stage : recipe.stages) {
            if (stage.materials().size() > MAX_MATERIALS) {
                return DataResult.error(() -> "A stage takes at most " + MAX_MATERIALS + " materials");
            }
        }
        if (!List.of(KEEP_NONE, KEEP_UPGRADES, KEEP_ALL, KEEP_ANDROID, KEEP_STRIP_ANDROID).contains(recipe.keep)) {
            return DataResult.error(() -> "keep is none, upgrades, all, android or strip_android, got " + recipe.keep);
        }
        return DataResult.success(recipe);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblyRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, recipe) -> {
                ResourceLocation.STREAM_CODEC.encode(buf, recipe.multiblock());
                ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC).encode(buf, recipe.input());
                ByteBufCodecs.STRING_UTF8.encode(buf, recipe.keep());
                Stage.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, recipe.stages());
                ItemStack.STREAM_CODEC.encode(buf, recipe.result());
                ByteBufCodecs.VAR_INT.encode(buf, recipe.sort());
                ByteBufCodecs.BOOL.encode(buf, recipe.testOnly());
            },
            buf -> new AssemblyRecipe(ResourceLocation.STREAM_CODEC.decode(buf), ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC).decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), Stage.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf), ItemStack.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf)));

    /** Whether the recipe may be used now: a recipe of the tests only while game tests are on. */
    public boolean available() {
        return !testOnly || GameTestHooks.isGametestEnabled();
    }

    /** Whether the item fits as the one this recipe starts from (a recipe without one takes none). */
    public boolean acceptsBase(ItemStack stack) {
        boolean android = stack.has(com.projectrebirth.garrison.GarrisonRegistry.ANDROID_PARTS.get());
        if (KEEP_ANDROID.equals(keep) && android || KEEP_STRIP_ANDROID.equals(keep) && !android) {
            return false;
        }
        return input.map(ingredient -> ingredient.test(stack)).orElse(stack.isEmpty());
    }

    public int totalTime() {
        return stages.stream().mapToInt(Stage::time).sum();
    }

    @Override
    public boolean matches(Input input, Level level) {
        return acceptsBase(input.base());
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
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

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MultiblockRegistry.ASSEMBLY_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return MultiblockRegistry.ASSEMBLY.get();
    }
}
