package com.projectrebirth.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.projectrebirth.client.multiblock.MultiblockClient;
import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiButton;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSlots;
import com.projectrebirth.client.ui.UiSounds;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.client.ui.UiTooltip;
import com.projectrebirth.multiblock.AssemblyRecipe;
import com.projectrebirth.multiblock.MultiblockControllerBlockEntity;
import com.projectrebirth.multiblock.MultiblockMenu;
import com.projectrebirth.multiblock.MultiblockRegistry;
import com.projectrebirth.multiblock.MultiblockSchema;
import com.projectrebirth.multiblock.MultiblockStatusPayload;
import com.projectrebirth.technology.energy.EnergyUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * A controller's window in the style "Industrial terminal" (1.8.0 block B). Left: the state of the construction with its
 * errors and the hologram button. Middle: the recipe (arrows), the ribbon of stages with marks for the done ones, the
 * current stage with its materials (have/need) and progress, and the energy. Right: nine material slots, the base and the
 * result. Bottom: the inventory and what the controller does now, what to do, and how long until the end.
 */
public class MultiblockScreen extends UiMachineScreen<MultiblockMenu> {
    private static final int W = 440;
    private static final int H = 250;
    private static final int LEFT_W = 150;
    private static final int MID_X = 163;
    private static final int MID_W = 181;
    private static final int RIGHT_X = 350;
    private static final int RIBBON_Y = 44;
    private static final int STAGE_Y = 66;
    private static final int STAGE_H = 66;
    private static final int INFO_X = 176;
    private static final int INFO_Y = 156;

    private final UiBars.Smooth progress = new UiBars.Smooth();
    private int lastState = -1;

    public MultiblockScreen(MultiblockMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "multiblocks");
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new UiButton(leftPos + MID_X, topPos + 25, 13, 13, Component.literal("<"), null, 0, UiButton.Look.METAL,
                () -> click(MultiblockControllerBlockEntity.BUTTON_PREVIOUS)));
        addRenderableWidget(new UiButton(leftPos + MID_X + MID_W - 13, topPos + 25, 13, 13, Component.literal(">"), null, 0, UiButton.Look.METAL,
                () -> click(MultiblockControllerBlockEntity.BUTTON_NEXT)));
        addRenderableWidget(new UiButton(leftPos + 11, topPos + 133, LEFT_W - 8, 12, tr("hologram"), null, 0, UiButton.Look.CHIP,
                () -> MultiblockClient.toggle(menu.pos())).selected(() -> MultiblockClient.showing(menu.pos())));
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int state = menu.get(MultiblockControllerBlockEntity.DATA_STATE);
        if (lastState >= 0 && state != lastState) {
            if (state == MultiblockControllerBlockEntity.STATE_WORKING) {
                UiSounds.WORK_START.play();
            } else if (state == MultiblockControllerBlockEntity.STATE_OUTPUT_FULL || state == MultiblockControllerBlockEntity.STATE_IDLE
                    && lastState == MultiblockControllerBlockEntity.STATE_WORKING) {
                UiSounds.WORK_END.play();
            } else if (state == MultiblockControllerBlockEntity.STATE_INCOMPLETE || state == MultiblockControllerBlockEntity.STATE_NO_ENERGY) {
                UiSounds.ERROR.play();
            }
        }
        lastState = state;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.multiblock." + key, args);
    }

    private List<RecipeHolder<AssemblyRecipe>> recipes() {
        if (minecraft == null || minecraft.level == null || !(minecraft.level.getBlockEntity(menu.pos()) instanceof MultiblockControllerBlockEntity controller)
                || controller.schemaId() == null) {
            return List.of();
        }
        return MultiblockRegistry.recipesFor(minecraft.level, controller.schemaId());
    }

    @Nullable
    private RecipeHolder<AssemblyRecipe> selected() {
        List<RecipeHolder<AssemblyRecipe>> recipes = recipes();
        int index = menu.get(MultiblockControllerBlockEntity.DATA_SELECTED);
        return index >= 0 && index < recipes.size() ? recipes.get(index) : null;
    }

    /** The material slots as they are now. */
    private List<ItemStack> held() {
        List<ItemStack> held = new ArrayList<>();
        for (int i = MultiblockControllerBlockEntity.FIRST_MATERIAL; i < MultiblockControllerBlockEntity.OUTPUT_SLOT; i++) {
            held.add(menu.getSlot(i).getItem());
        }
        return held;
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int state = menu.get(MultiblockControllerBlockEntity.DATA_STATE);
        int stage = menu.get(MultiblockControllerBlockEntity.DATA_STAGE);
        int stages = menu.get(MultiblockControllerBlockEntity.DATA_STAGES);
        RecipeHolder<AssemblyRecipe> holder = selected();
        AssemblyRecipe recipe = holder == null ? null : holder.value();

        drawConstruction(graphics, x + 7, y + 25, state);
        drawRecipe(graphics, x + MID_X, y + 25, holder);
        drawRibbon(graphics, x + MID_X, y + RIBBON_Y, recipe, stage, stages, state);
        drawStage(graphics, x + MID_X, y + STAGE_Y, recipe, stage, stages, state);

        // energy, as a meter under the stage
        int capacity = menu.energyCapacity();
        float fraction = capacity <= 0 ? 0.0f : Math.min(1.0f, menu.energy() / (float) capacity);
        UiBars.meter(graphics, UiSprites.ICON_INFO, x + MID_X, y + 137, MID_W, fraction, UiColors.ENERGY,
                Component.literal(Math.round(fraction * 100) + "%"));
        tooltip(x + MID_X, y + 137, MID_W, 9, () -> UiTooltip.of(Component.translatable("screen.projectrebirth.station.label.energy"))
                .row(Component.translatable("screen.projectrebirth.ui.energy.stock"),
                        Component.literal(EnergyUtil.format(menu.energy()) + " / " + EnergyUtil.format(capacity) + " FE"), UiColors.ENERGY_TEXT));

        // slots
        UiPanels.label(graphics, tr("label.materials"), x + RIGHT_X, y + 26, W - 7 - RIGHT_X);
        for (int i = 0; i < MultiblockControllerBlockEntity.MATERIALS; i++) {
            UiSlots.frame(graphics, UiSlots.Kind.INPUT, x + MultiblockMenu.MATERIALS_X + (i % 3) * MultiblockMenu.MATERIAL_STEP,
                    y + MultiblockMenu.MATERIALS_Y + (i / 3) * MultiblockMenu.MATERIAL_STEP);
        }
        UiPanels.label(graphics, tr("label.base_result"), x + RIGHT_X, y + 99, W - 7 - RIGHT_X);
        UiSlots.frame(graphics, UiSlots.Kind.INPUT, x + MultiblockMenu.BASE_X, y + MultiblockMenu.BASE_Y);
        UiSlots.frame(graphics, UiSlots.Kind.OUTPUT, x + MultiblockMenu.OUTPUT_X, y + MultiblockMenu.OUTPUT_Y);
        badge(graphics, tone(state), tr("badge." + stateKey(state)), x + RIGHT_X, y + 137, W - 7 - RIGHT_X);

        inventory(graphics, x + MultiblockMenu.INVENTORY_X, y + MultiblockMenu.INVENTORY_Y);
        drawInfo(graphics, x + INFO_X, y + INFO_Y, W - 7 - INFO_X, H - 7 - INFO_Y, recipe, stage, stages, state);
    }

    private void drawConstruction(GuiGraphics graphics, int x, int y, int state) {
        UiPanels.screen(graphics, x, y, LEFT_W, 125);
        text(graphics, UiText.upper(tr("label.construction")), x + 5, y + 5, LEFT_W - 10, UiColors.AMBER);
        boolean incomplete = state == MultiblockControllerBlockEntity.STATE_INCOMPLETE || state == MultiblockControllerBlockEntity.STATE_NO_PLAN;
        UiSprites.draw(graphics, incomplete ? UiSprites.ICON_CROSS : UiSprites.ICON_CHECK, x + 5, y + 18, 7, 7);
        text(graphics, incomplete ? tr("state." + stateKey(state)) : tr("built"), x + 15, y + 17, LEFT_W - 20, incomplete ? UiColors.ERROR : UiColors.OK);
        if (state == MultiblockControllerBlockEntity.STATE_INCOMPLETE) {
            MultiblockStatusPayload status = MultiblockClient.status(menu.pos());
            int row = 0;
            if (status != null) {
                // two lines a problem: the block, then what is wrong and where
                int shown = 0;
                for (MultiblockStatusPayload.Entry entry : status.problems()) {
                    if (row >= 8) {
                        text(graphics, tr("more_problems", status.problems().size() - shown), x + 5, y + 30 + row * 10, LEFT_W - 10, UiColors.TEXT_DIM);
                        break;
                    }
                    Component full = problem(entry);
                    int lineY = y + 30 + row * 10;
                    UiSprites.draw(graphics, UiSprites.ICON_CROSS, x + 5, lineY + 1, 7, 7);
                    text(graphics, BuiltInRegistries.BLOCK.get(entry.block()).getName(), x + 15, lineY, LEFT_W - 20, UiColors.ERROR_TEXT);
                    text(graphics, problemShort(entry), x + 15, lineY + 9, LEFT_W - 20, UiColors.TEXT_SECONDARY);
                    hints.add(x + 5, lineY, LEFT_W - 10, 18, full);
                    row += 2;
                    shown++;
                }
            }
            if (row == 0) {
                UiText.wrapped(graphics, tr("hologram_hint"), x + 5, y + 30, LEFT_W - 10, 70, UiColors.TEXT_SECONDARY);
            }
        } else {
            UiText.wrapped(graphics, tr("built_hint"), x + 5, y + 30, LEFT_W - 10, 70, UiColors.TEXT_SECONDARY);
        }
    }

    private void drawRecipe(GuiGraphics graphics, int x, int y, @Nullable RecipeHolder<AssemblyRecipe> holder) {
        UiPanels.screen(graphics, x + 15, y, MID_W - 30, 15);
        if (holder != null) {
            ItemStack result = holder.value().result();
            graphics.pose().pushPose();
            graphics.pose().translate(x + 18, y + 1.5f, 0.0f);
            graphics.pose().scale(0.75f, 0.75f, 1.0f);
            graphics.renderItem(result, 0, 0);
            graphics.pose().popPose();
            text(graphics, recipeName(holder), x + 33, y + 4, MID_W - 50, UiColors.AMBER_LIGHT);
        } else {
            text(graphics, recipes().isEmpty() ? tr("no_recipes") : tr("choose"), x + 20, y + 4, MID_W - 40, UiColors.TEXT_DIM);
        }
    }

    /** One box for each stage: done ones with a check, the current one lit amber, the rest dim. */
    private void drawRibbon(GuiGraphics graphics, int x, int y, @Nullable AssemblyRecipe recipe, int stage, int stages, int state) {
        if (recipe == null || recipe.stages().isEmpty()) {
            return;
        }
        int count = recipe.stages().size();
        int gap = 3;
        int box = (MID_W - (count - 1) * gap) / count;
        boolean running = stage >= 0 && stage < stages && stages == count;
        for (int i = 0; i < count; i++) {
            int bx = x + i * (box + gap);
            boolean done = running && i < stage;
            boolean current = running && i == stage;
            UiSprites.draw(graphics, current ? UiSprites.ROW_SELECTED : done ? UiSprites.Tone.OK.plate : UiSprites.CHIP_OFF, bx, y, box, 17);
            if (done) {
                UiSprites.draw(graphics, UiSprites.ICON_CHECK, bx + 3, y + 5, 7, 7);
            }
            UiText.centred(graphics, Component.literal(String.valueOf(i + 1)), bx + box / 2 + (done ? 3 : 0), y + 5, box - 4,
                    current ? UiColors.AMBER_LIGHT : done ? UiColors.OK_TEXT : UiColors.TEXT_SECONDARY);
            AssemblyRecipe.Stage s = recipe.stages().get(i);
            int number = i + 1;
            tooltip(bx, y, box, 17, () -> UiTooltip.of(tr("stage_n", number, stageName(s)))
                    .row(tr("time"), Component.literal(clock(s.time())), UiColors.TEXT)
                    .row(Component.translatable("screen.projectrebirth.station.label.energy"),
                            Component.translatable("screen.projectrebirth.station.energy.out", s.energy()), UiColors.WARNING)
                    .text(materials(s), UiColors.TEXT_SECONDARY));
        }
    }

    private void drawStage(GuiGraphics graphics, int x, int y, @Nullable AssemblyRecipe recipe, int stage, int stages, int state) {
        UiPanels.screen(graphics, x, y, MID_W, STAGE_H);
        if (recipe == null || recipe.stages().isEmpty()) {
            text(graphics, Component.literal("> ").append(tr("process.idle")), x + 5, y + 5, MID_W - 10, UiColors.AMBER_DIM);
            return;
        }
        boolean running = stage >= 0 && stage < stages && stages == recipe.stages().size();
        // the stage shown: the current one, or the first while it waits to start
        int shown = running ? stage : 0;
        AssemblyRecipe.Stage s = recipe.stages().get(shown);
        text(graphics, Component.literal("> ").append(tr("stage_n", shown + 1, UiText.upper(stageName(s)))), x + 5, y + 5, MID_W - 10, UiColors.AMBER);
        // its materials: have/need
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack stack : held()) {
            left.add(stack.copy());
        }
        int row = 0;
        boolean waitingForNext = state == MultiblockControllerBlockEntity.STATE_MATERIALS && running && stage + 1 < stages;
        AssemblyRecipe.Stage listed = waitingForNext ? recipe.stages().get(stage + 1) : s;
        if (waitingForNext) {
            text(graphics, tr("next_needs", stage + 2), x + 5, y + 16, MID_W - 10, UiColors.TEXT_SECONDARY);
            row = 1;
        }
        int columnW = (MID_W - 10) / 2;
        int index = 0;
        for (SizedIngredient material : listed.materials()) {
            int have = material.count() - take(left, material);
            boolean enough = have >= material.count() || running && !waitingForNext && shown == stage;
            int lx = x + 5 + (index % 2) * columnW;
            int ly = y + 17 + (row + index / 2) * 10;
            if (ly > y + STAGE_H - 22) {
                break;
            }
            UiSprites.draw(graphics, enough ? UiSprites.ICON_CHECK : UiSprites.ICON_CROSS, lx, ly + 1, 7, 7);
            ItemStack[] items = material.ingredient().getItems();
            Component name = items.length == 0 ? Component.literal("?") : items[0].getHoverName();
            text(graphics, Component.literal((enough && running && shown == stage && !waitingForNext ? material.count() : have) + "/" + material.count() + " ")
                    .append(name), lx + 9, ly, columnW - 12, enough ? UiColors.SCREEN_TEXT : UiColors.ERROR);
            index++;
        }
        // progress of the stage
        int time = menu.get(MultiblockControllerBlockEntity.DATA_STAGE_TIME);
        int ticks = menu.get(MultiblockControllerBlockEntity.DATA_TICKS);
        float fraction = running && time > 0 ? progress.follow(Math.min(1.0f, ticks / (float) time)) : 0.0f;
        UiBars.progress(graphics, x + 5, y + STAGE_H - 10, MID_W - 50, 5, fraction, UiColors.AMBER);
        text(graphics, Component.literal(Mth.floor(fraction * 100) + "%"), x + MID_W - 40, y + STAGE_H - 12, 35, UiColors.AMBER);
    }

    private void drawInfo(GuiGraphics graphics, int x, int y, int width, int height, @Nullable AssemblyRecipe recipe, int stage, int stages, int state) {
        UiPanels.screen(graphics, x, y, width, height);
        UiSprites.Tone tone = tone(state);
        if (tone.icon != null) {
            UiSprites.draw(graphics, tone.icon, x + 5, y + 6, 7, 7);
        }
        text(graphics, tr("state." + stateKey(state)), x + 15, y + 5, width - 20, tone.text);
        UiText.wrapped(graphics, tr("todo." + stateKey(state)), x + 5, y + 18, width - 10, 36, UiColors.SCREEN_TEXT);
        if (recipe != null && !recipe.stages().isEmpty()) {
            boolean running = stage >= 0 && stage < stages && stages == recipe.stages().size();
            int from = running ? stage : 0;
            int ticksLeft = 0;
            long energyLeft = 0;
            for (int i = from; i < recipe.stages().size(); i++) {
                AssemblyRecipe.Stage s = recipe.stages().get(i);
                int done = running && i == stage ? menu.get(MultiblockControllerBlockEntity.DATA_TICKS) : 0;
                ticksLeft += Math.max(0, s.time() - done);
                energyLeft += (long) Math.max(0, s.time() - done) * s.energy();
            }
            text(graphics, tr("until_end", clock(ticksLeft), EnergyUtil.format((int) Math.min(Integer.MAX_VALUE, energyLeft))),
                    x + 5, y + height - 24, width - 10, UiColors.TEXT_SECONDARY);
        }
        text(graphics, tr("automation"), x + 5, y + height - 12, width - 10, UiColors.TEXT_DIM);
    }

    private static UiSprites.Tone tone(int state) {
        return switch (state) {
            case MultiblockControllerBlockEntity.STATE_WORKING -> UiSprites.Tone.OK;
            case MultiblockControllerBlockEntity.STATE_INCOMPLETE, MultiblockControllerBlockEntity.STATE_NO_PLAN,
                 MultiblockControllerBlockEntity.STATE_OFF -> UiSprites.Tone.ERROR;
            case MultiblockControllerBlockEntity.STATE_NO_ENERGY, MultiblockControllerBlockEntity.STATE_MATERIALS,
                 MultiblockControllerBlockEntity.STATE_OUTPUT_FULL, MultiblockControllerBlockEntity.STATE_NO_BASE -> UiSprites.Tone.WARNING;
            default -> UiSprites.Tone.INFO;
        };
    }

    private static String stateKey(int state) {
        return switch (state) {
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
    }

    private static Component stageName(AssemblyRecipe.Stage stage) {
        return Component.translatable("screen.projectrebirth.multiblock.stage." + stage.name());
    }

    private static Component materials(AssemblyRecipe.Stage stage) {
        StringBuilder text = new StringBuilder();
        for (SizedIngredient material : stage.materials()) {
            ItemStack[] items = material.ingredient().getItems();
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(material.count()).append(" × ").append(items.length == 0 ? "?" : items[0].getHoverName().getString());
        }
        return Component.literal(text.toString());
    }

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

    /**
     * The recipe's own name (assembly.projectrebirth.&lt;recipe id&gt;) when it has one: the recipes of the body workshop all make a
     * body, so only their names tell a body from a combat or a worker android. Otherwise the result's name.
     */
    public static Component recipeName(RecipeHolder<AssemblyRecipe> holder) {
        String key = "assembly." + holder.id().getNamespace() + "." + holder.id().getPath();
        return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : holder.value().result().getHoverName();
    }

    private Component problem(MultiblockStatusPayload.Entry entry) {
        MultiblockSchema.ProblemKind kind = MultiblockSchema.ProblemKind.values()[Mth.clamp(entry.kind(), 0, MultiblockSchema.ProblemKind.values().length - 1)];
        BlockPos pos = entry.pos();
        Component block = BuiltInRegistries.BLOCK.get(entry.block()).getName();
        return Component.translatable("screen.projectrebirth.multiblock.problem." + kind.name().toLowerCase(Locale.ROOT), block,
                pos.getX(), pos.getY(), pos.getZ());
    }

    /** "missing · 62 -61 -12": what is wrong in a word and where. */
    private Component problemShort(MultiblockStatusPayload.Entry entry) {
        MultiblockSchema.ProblemKind kind = MultiblockSchema.ProblemKind.values()[Mth.clamp(entry.kind(), 0, MultiblockSchema.ProblemKind.values().length - 1)];
        BlockPos pos = entry.pos();
        return tr("problem_short." + kind.name().toLowerCase(Locale.ROOT), pos.getX(), pos.getY(), pos.getZ());
    }

    private static String clock(int ticks) {
        int seconds = (ticks + 19) / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
