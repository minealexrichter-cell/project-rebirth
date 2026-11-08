package com.projectrebirth.client.codex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.codex.CodexBook;
import com.projectrebirth.technology.FabricationRecipe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * Pages of the codex: how an entry's pages fall onto the paper (long text runs on to further pages; a page with a picture
 * keeps the picture on its first part), and how each kind of page is drawn.
 */
final class CodexPages {
    static final int LINE = 10;
    private static final int TITLE = 24;
    private static final Map<String, Entity> ENTITIES = new HashMap<>();

    /** One page of paper: maybe the entry's title, maybe the picture of its source page, and lines of text. */
    record Layout(String titleKey, boolean title, @Nullable CodexBook.Page page, boolean picture, List<FormattedCharSequence> lines,
                  boolean note) {
    }

    private CodexPages() {
    }

    static int pictureHeight(CodexBook.Page page) {
        return switch (page.type()) {
            case "item" -> 50;
            case "block" -> 84;
            case "entity" -> 92;
            case "recipe" -> 78;
            default -> 0;
        };
    }

    static List<Layout> layout(Font font, CodexBook.Entry entry, int width, int height) {
        List<Layout> pages = new ArrayList<>();
        boolean first = true;
        for (CodexBook.Page page : entry.pages()) {
            boolean note = "note".equals(page.type());
            List<FormattedCharSequence> lines = page.text() == null ? List.of()
                    : font.split(Component.translatable(page.text()), note ? width - 10 : width);
            int picture = pictureHeight(page);
            int start = 0;
            // plain text goes on below the text of the page before, if there is room for a few lines
            if ("text".equals(page.type()) && !pages.isEmpty()) {
                Layout last = pages.get(pages.size() - 1);
                int used = (last.title() ? TITLE : 0) + (last.picture() && last.page() != null ? pictureHeight(last.page()) : 0)
                        + (last.lines().size() + 1) * LINE;
                int room = (height - used) / LINE;
                if (!last.note() && room >= 3) {
                    int count = Math.min(lines.size(), room);
                    List<FormattedCharSequence> joined = new ArrayList<>(last.lines());
                    joined.add(FormattedCharSequence.EMPTY);
                    joined.addAll(lines.subList(0, count));
                    pages.set(pages.size() - 1, new Layout(last.titleKey(), last.title(), last.page(), last.picture(), joined, false));
                    start = count;
                    first = false;
                    if (start >= lines.size()) {
                        continue;
                    }
                }
            }
            boolean withPicture = picture > 0 && start == 0;
            do {
                int room = height - (first ? TITLE : 0) - (withPicture ? picture : 0) - (note ? 8 : 0);
                int count = Math.max(withPicture ? 0 : 1, Math.min(lines.size() - start, room / LINE));
                pages.add(new Layout(entry.titleKey(), first, page, withPicture, lines.subList(start, Math.min(lines.size(), start + count)), note));
                start += count;
                first = false;
                withPicture = false;
            } while (start < lines.size());
        }
        return pages;
    }

    static void draw(CodexScreen screen, GuiGraphics graphics, Font font, Layout page, int x, int y, int width, int height,
                     int mouseX, int mouseY, float partialTick) {
        if (page.title()) {
            Component title = Component.translatable(page.titleKey());
            graphics.drawString(font, title, x + (width - font.width(title)) / 2, y, CodexScreen.RED_INK, false);
            graphics.blit(CodexScreen.ATLAS, x + width / 2 - 48, y + 11, 0, 150, 96, 7);
            y += TITLE;
        }
        if (page.picture() && page.page() != null) {
            int h = pictureHeight(page.page());
            drawPicture(screen, graphics, font, page.page(), x, y, width, h, mouseX, mouseY, partialTick);
            y += h;
        }
        if (page.note() && !page.lines().isEmpty()) {
            int boxHeight = page.lines().size() * LINE + 6;
            graphics.fill(x, y, x + width, y + boxHeight, 0x22603010);
            graphics.fill(x, y, x + 2, y + boxHeight, 0xFF9A3A1A);
            x += 6;
            y += 4;
        }
        for (FormattedCharSequence line : page.lines()) {
            graphics.drawString(font, line, x, y, CodexScreen.INK, false);
            y += LINE;
        }
    }

    // ---------------------------------------------------------------- pictures

    private static void drawPicture(CodexScreen screen, GuiGraphics graphics, Font font, CodexBook.Page page, int x, int y, int width, int height,
                                    int mouseX, int mouseY, float partialTick) {
        String value = page.value() == null ? "" : page.value();
        switch (page.type()) {
            case "item" -> {
                ItemStack stack = CodexScreen.stackOf(value);
                PoseStack pose = graphics.pose();
                pose.pushPose();
                pose.translate(x + width / 2.0f - 24, y + 2, 0);
                pose.scale(3.0f, 3.0f, 1.0f);
                graphics.renderItem(stack, 0, 0);
                pose.popPose();
                if (mouseX >= x + width / 2 - 24 && mouseX < x + width / 2 + 24 && mouseY >= y && mouseY < y + 50) {
                    screen.hover(stack);
                }
            }
            case "block" -> drawBlock(graphics, value, x + width / 2, y + height / 2, partialTick);
            case "entity" -> {
                Entity entity = ENTITIES.computeIfAbsent(value, id -> EntityType.byString(id)
                        .map(type -> type.create(Minecraft.getInstance().level)).orElse(null));
                if (entity instanceof LivingEntity living) {
                    InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + width / 2 - 40, y, x + width / 2 + 40, y + height - 2,
                            entityScale(living, height), 0.0625f, mouseX, mouseY, living);
                }
            }
            case "recipe" -> drawRecipe(screen, graphics, font, value, x, y, width, mouseX, mouseY);
            default -> {
            }
        }
    }

    private static int entityScale(LivingEntity entity, int height) {
        return Math.max(10, Math.round((height - 12) / Math.max(1.0f, entity.getBbHeight()) * 0.85f));
    }

    /** The block as a small model on a turntable; a block of two halves is drawn whole. */
    private static void drawBlock(GuiGraphics graphics, String id, int centerX, int centerY, float partialTick) {
        BlockState state = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState();
        boolean twoHigh = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
        Minecraft minecraft = Minecraft.getInstance();
        float angle = ((minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTick) * 1.2f;
        float size = twoHigh ? 27.0f : 40.0f;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 150);
        pose.scale(size, -size, size);
        pose.mulPose(Axis.XP.rotationDegrees(25));
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-0.5, twoHigh ? -1.0 : -0.5, -0.5);
        Lighting.setupFor3DItems();
        var blocks = minecraft.getBlockRenderer();
        if (twoHigh) {
            blocks.renderSingleBlock(state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER), pose, graphics.bufferSource(),
                    0xF000F0, OverlayTexture.NO_OVERLAY);
            pose.translate(0, 1, 0);
            blocks.renderSingleBlock(state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), pose, graphics.bufferSource(),
                    0xF000F0, OverlayTexture.NO_OVERLAY);
        } else {
            blocks.renderSingleBlock(state, pose, graphics.bufferSource(), 0xF000F0, OverlayTexture.NO_OVERLAY);
        }
        graphics.flush();
        pose.popPose();
    }

    /** The name over a recipe in red ink, made smaller if it is wider than the room. */
    private static void label(GuiGraphics graphics, Font font, Component name, int x, int y, int room) {
        int width = font.width(name);
        if (width <= room) {
            graphics.drawString(font, name, x, y, CodexScreen.RED_INK, false);
            return;
        }
        float scale = room / (float) width;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y + (1.0f - scale) * font.lineHeight / 2.0f, 0.0f);
        pose.scale(scale, scale, 1.0f);
        graphics.drawString(font, name, 0, 0, CodexScreen.RED_INK, false);
        pose.popPose();
    }

    private static ItemStack cycle(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return ItemStack.EMPTY;
        }
        long time = System.currentTimeMillis() / 1000;
        return items[(int) (time % items.length)];
    }

    private static void slot(CodexScreen screen, GuiGraphics graphics, Font font, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        graphics.blit(CodexScreen.ATLAS, x, y, 0, 128, 18, 18);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(font, stack, x + 1, y + 1);
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                screen.hover(stack);
            }
        }
    }

    private static void drawRecipe(CodexScreen screen, GuiGraphics graphics, Font font, String id, int x, int y, int width, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        Optional<RecipeHolder<?>> holder = minecraft.level == null ? Optional.empty()
                : minecraft.level.getRecipeManager().byKey(ResourceLocation.parse(id));
        if (holder.isEmpty()) {
            graphics.drawString(font, Component.translatable("codex.projectrebirth.no_recipe"), x, y + 20, CodexScreen.RED_INK, false);
            return;
        }
        Recipe<?> recipe = holder.get().value();
        ItemStack result = recipe.getResultItem(minecraft.level.registryAccess());
        int gridX = x + (width - 112) / 2;
        if (recipe instanceof FabricationRecipe fabrication) {
            ItemStack station = CodexScreen.stackOf(ProjectRebirth.MOD_ID + ":" + fabrication.station().id());
            graphics.renderItem(station, gridX, y + 1);
            label(graphics, font, station.getHoverName(), gridX + 20, y + 5, x + width - gridX - 20);
            List<SizedIngredient> ingredients = fabrication.ingredients();
            for (int i = 0; i < ingredients.size(); i++) {
                ItemStack stack = cycle(ingredients.get(i).ingredient()).copyWithCount(ingredients.get(i).count());
                slot(screen, graphics, font, stack, gridX + (i % 3) * 18, y + 20 + (i / 3) * 18, mouseX, mouseY);
            }
            graphics.blit(CodexScreen.ATLAS, gridX + 60, y + 29, 24, 128, 22, 15);
            slot(screen, graphics, font, result, gridX + 88, y + 28, mouseX, mouseY);
            Component time = Component.translatable("codex.projectrebirth.recipe_time", fabrication.time() / 20);
            graphics.drawString(font, time, gridX + 60, y + 50, 0xFF7A6248, false);
            return;
        }
        List<Ingredient> ingredients = recipe.getIngredients();
        int columns = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        // where it is made: a crafting table, or a furnace for smelting
        ItemStack where = new ItemStack(recipe instanceof net.minecraft.world.item.crafting.BlastingRecipe ? net.minecraft.world.item.Items.BLAST_FURNACE
                : recipe instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe ? net.minecraft.world.item.Items.FURNACE
                : net.minecraft.world.item.Items.CRAFTING_TABLE);
        graphics.renderItem(where, gridX, y + 1);
        label(graphics, font, where.getHoverName(), gridX + 20, y + 5, x + width - gridX - 20);
        for (int i = 0; i < ingredients.size(); i++) {
            slot(screen, graphics, font, cycle(ingredients.get(i)), gridX + (i % columns) * 18, y + 20 + (i / columns) * 18, mouseX, mouseY);
        }
        graphics.blit(CodexScreen.ATLAS, gridX + 60, y + 38, 24, 128, 22, 15);
        slot(screen, graphics, font, result, gridX + 88, y + 37, mouseX, mouseY);
    }
}
