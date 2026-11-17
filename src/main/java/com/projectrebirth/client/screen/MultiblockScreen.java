package com.projectrebirth.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.client.multiblock.MultiblockClient;
import com.projectrebirth.multiblock.AssemblyRecipe;
import com.projectrebirth.multiblock.MultiblockControllerBlockEntity;
import com.projectrebirth.multiblock.MultiblockMenu;
import com.projectrebirth.multiblock.MultiblockRegistry;
import com.projectrebirth.multiblock.MultiblockSchema;
import com.projectrebirth.multiblock.MultiblockStatusPayload;
import com.projectrebirth.technology.energy.EnergyUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import static com.projectrebirth.client.screen.GuiShapes.panel;
import static com.projectrebirth.client.screen.GuiShapes.playerInventory;
import static com.projectrebirth.client.screen.GuiShapes.slot;

/**
 * A controller's window (1.7.0, block A): the item to start from, the materials, the result, the recipe of the multiblock with
 * arrows, energy, and a status: the construction's errors, the missing materials, or the stage and its progress.
 */
public class MultiblockScreen extends AbstractContainerScreen<MultiblockMenu> {
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private static final int RECIPE_Y = 76;
    private static final int ENERGY_Y = 92;
    private static final int STATUS_Y = 100;
    private static final int TEXT = 0x404040;
    private static final int WARN = 0xB02020;
    private static final int STATUS_ROWS = 3;

    /** The whole status, for the tooltip over the status lines. */
    private List<Component> status = List.of();

    public MultiblockScreen(MultiblockMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, GuiShapes.fit(title, 160));
        imageWidth = 176;
        imageHeight = MultiblockMenu.INVENTORY_Y + 82;
        inventoryLabelY = MultiblockMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> click(MultiblockControllerBlockEntity.BUTTON_PREVIOUS))
                .bounds(leftPos + 7, topPos + RECIPE_Y - 2, 12, 12).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> click(MultiblockControllerBlockEntity.BUTTON_NEXT))
                .bounds(leftPos + imageWidth - 19, topPos + RECIPE_Y - 2, 12, 12).build());
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (status.size() > 1 && isHovering(8, STATUS_Y, imageWidth - 16, STATUS_ROWS * 10, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, status, mouseX, mouseY);
        } else if (isHovering(8, ENERGY_Y, imageWidth - 16, 6, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("screen.projectrebirth.energy", EnergyUtil.format(menu.energy()),
                    EnergyUtil.format(menu.energyCapacity())), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        panel(graphics, x, y, imageWidth, imageHeight);
        slot(graphics, x + MultiblockMenu.BASE_X, y + MultiblockMenu.SLOT_Y);
        for (int i = 0; i < MultiblockControllerBlockEntity.MATERIALS; i++) {
            slot(graphics, x + MultiblockMenu.MATERIALS_X + (i % 3) * 18, y + MultiblockMenu.SLOT_Y + (i / 3) * 18);
        }
        slot(graphics, x + MultiblockMenu.OUTPUT_X, y + MultiblockMenu.OUTPUT_Y);
        playerInventory(graphics, x + 8, y + MultiblockMenu.INVENTORY_Y);
        int time = menu.get(MultiblockControllerBlockEntity.DATA_STAGE_TIME);
        int width = time <= 0 ? 0 : Mth.ceil(24.0f * menu.get(MultiblockControllerBlockEntity.DATA_TICKS) / time);
        if (width > 0) {
            graphics.blitSprite(ARROW, 24, 16, 0, 0, x + 100, y + MultiblockMenu.OUTPUT_Y, Math.min(24, width), 16);
        }
        GuiAtlas.energyHorizontal(graphics, x + 8, y + ENERGY_Y, imageWidth - 16, 6, menu.energy(), menu.energyCapacity());
    }

    private static Component state(int state) {
        String key = switch (state) {
            case MultiblockControllerBlockEntity.STATE_INCOMPLETE -> "incomplete";
            case MultiblockControllerBlockEntity.STATE_IDLE -> "idle";
            case MultiblockControllerBlockEntity.STATE_NO_BASE -> "no_base";
            case MultiblockControllerBlockEntity.STATE_MATERIALS -> "materials";
            case MultiblockControllerBlockEntity.STATE_NO_ENERGY -> "no_energy";
            case MultiblockControllerBlockEntity.STATE_WORKING -> "working";
            case MultiblockControllerBlockEntity.STATE_OUTPUT_FULL -> "output_full";
            case MultiblockControllerBlockEntity.STATE_OFF -> "off";
            default -> "no_plan";
        };
        return Component.translatable("screen.projectrebirth.multiblock.state." + key);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        // the recipe: its result and name between the arrows
        List<RecipeHolder<AssemblyRecipe>> recipes = recipes();
        int index = menu.get(MultiblockControllerBlockEntity.DATA_SELECTED);
        AssemblyRecipe recipe = index >= 0 && index < recipes.size() ? recipes.get(index).value() : null;
        if (recipe != null) {
            graphics.renderFakeItem(recipe.result(), 22, RECIPE_Y - 4);
            line(graphics, recipe.result().getHoverName(), 42, RECIPE_Y, imageWidth - 64, TEXT);
        } else {
            line(graphics, Component.translatable(recipes.isEmpty() ? "screen.projectrebirth.multiblock.no_recipes" : "screen.projectrebirth.multiblock.choose"),
                    24, RECIPE_Y, imageWidth - 48, 0x808080);
        }
        // the status
        int state = menu.get(MultiblockControllerBlockEntity.DATA_STATE);
        List<Component> lines = new ArrayList<>();
        int stage = menu.get(MultiblockControllerBlockEntity.DATA_STAGE);
        int stages = menu.get(MultiblockControllerBlockEntity.DATA_STAGES);
        if (state == MultiblockControllerBlockEntity.STATE_INCOMPLETE) {
            lines.add(state(state));
            MultiblockStatusPayload status = MultiblockClient.status(menu.pos());
            if (status != null) {
                for (MultiblockStatusPayload.Entry entry : status.problems()) {
                    lines.add(problem(entry));
                }
            }
        } else if (recipe != null && stage >= 0 && stage < stages && recipe.stages().size() == stages) {
            AssemblyRecipe.Stage current = recipe.stages().get(stage);
            int time = menu.get(MultiblockControllerBlockEntity.DATA_STAGE_TIME);
            int percent = time <= 0 ? 0 : Math.min(100, 100 * menu.get(MultiblockControllerBlockEntity.DATA_TICKS) / time);
            lines.add(Component.translatable("screen.projectrebirth.multiblock.stage", stage + 1, stages,
                    Component.translatable("screen.projectrebirth.multiblock.stage." + current.name()), percent));
            if (state != MultiblockControllerBlockEntity.STATE_WORKING) {
                lines.add(state(state));
            }
            if (state == MultiblockControllerBlockEntity.STATE_MATERIALS && stage + 1 < stages) {
                lines.add(missing(recipe.stages().get(stage + 1)));
            }
        } else {
            lines.add(state(state));
            if (state == MultiblockControllerBlockEntity.STATE_MATERIALS && recipe != null) {
                lines.add(missing(recipe.stages().get(0)));
            }
        }
        status = lines;
        // long lines wrap; what does not fit is in the tooltip over the status
        int row = 0;
        int shown = 0;
        for (int i = 0; i < lines.size() && row < STATUS_ROWS; i++) {
            boolean warn = i == 0 && (state == MultiblockControllerBlockEntity.STATE_INCOMPLETE || state == MultiblockControllerBlockEntity.STATE_NO_ENERGY);
            for (var part : font.split(lines.get(i), imageWidth - 16)) {
                if (row >= STATUS_ROWS) {
                    break;
                }
                graphics.drawString(font, part, 8, STATUS_Y + row * 10, warn ? WARN : TEXT, false);
                row++;
            }
            shown++;
        }
        if (lines.size() > shown) {
            Component more = Component.translatable("screen.projectrebirth.multiblock.more", lines.size() - shown);
            graphics.drawString(font, more, imageWidth - 8 - font.width(more), inventoryLabelY, 0x808080, false);
        }
    }

    private List<RecipeHolder<AssemblyRecipe>> recipes() {
        if (minecraft == null || minecraft.level == null || !(minecraft.level.getBlockEntity(menu.pos()) instanceof MultiblockControllerBlockEntity controller)
                || controller.schemaId() == null) {
            return List.of();
        }
        return MultiblockRegistry.recipesFor(minecraft.level, controller.schemaId());
    }

    private Component problem(MultiblockStatusPayload.Entry entry) {
        MultiblockSchema.ProblemKind kind = MultiblockSchema.ProblemKind.values()[Mth.clamp(entry.kind(), 0, MultiblockSchema.ProblemKind.values().length - 1)];
        BlockPos pos = entry.pos();
        Component block = BuiltInRegistries.BLOCK.get(entry.block()).getName();
        return Component.translatable("screen.projectrebirth.multiblock.problem." + kind.name().toLowerCase(Locale.ROOT), block,
                pos.getX(), pos.getY(), pos.getZ());
    }

    private Component missing(AssemblyRecipe.Stage stage) {
        if (minecraft == null || minecraft.level == null || !(minecraft.level.getBlockEntity(menu.pos()) instanceof MultiblockControllerBlockEntity)) {
            return Component.empty();
        }
        List<ItemStack> held = new ArrayList<>();
        for (int i = MultiblockControllerBlockEntity.FIRST_MATERIAL; i < MultiblockControllerBlockEntity.OUTPUT_SLOT; i++) {
            held.add(menu.getSlot(i).getItem());
        }
        StringBuilder text = new StringBuilder();
        for (SizedIngredient material : stage.missing(held)) {
            ItemStack[] options = material.ingredient().getItems();
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(material.count()).append("× ").append(options.length == 0 ? "?" : options[0].getHoverName().getString());
        }
        return Component.translatable("screen.projectrebirth.multiblock.missing", text.toString());
    }

    private void line(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), width), x, y, color, false);
    }
}
