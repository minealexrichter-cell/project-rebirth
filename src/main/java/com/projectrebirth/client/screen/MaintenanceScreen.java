package com.projectrebirth.client.screen;

import java.util.LinkedHashMap;
import java.util.Map;

import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiEnergy;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSlots;
import com.projectrebirth.client.ui.UiSounds;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.Maintenance;
import com.projectrebirth.technology.MaintenanceBlockEntity;
import com.projectrebirth.technology.MaintenanceMenu;
import com.projectrebirth.technology.energy.EnergyUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Maintenance station window in the style "Industrial terminal" (1.8.0 block B): the materials on the left, a screen with
 * the serviced item and its scales and the step in progress, the serviced slot and energy on the right, the state, and the
 * repair plan: what a full repair still takes of each material (and whether it is in the slots), how long and how much
 * energy. The plan is counted on the client the same way the server repairs, step by step.
 */
public class MaintenanceScreen extends UiMachineScreen<MaintenanceMenu> {
    private static final int W = 360;
    private static final int H = 227;
    private static final int SCREEN_X = 36;
    private static final int SCREEN_Y = 36;
    private static final int SCREEN_W = 214;
    private static final int SCREEN_H = 74;
    private static final int RIGHT_X = 256;
    private static final int PLAN_X = 176;
    private static final int PLAN_Y = 133;

    private final UiEnergy energy = new UiEnergy();
    private final UiBars.Smooth progress = new UiBars.Smooth();
    private boolean wasWorking;
    private int ticks;

    public MaintenanceScreen(MaintenanceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "maintenance_station");
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.maintenance." + key, args);
    }

    private boolean working() {
        ItemStack stack = menu.serviced();
        return Maintenance.needsRepair(stack) && Maintenance.materialFor(stack, menu::hasMaterial) != null && !menu.emiPaused()
                && menu.energy() >= MaintenanceBlockEntity.energyPerTick();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean working = working();
        energy.tick(menu.energy(), working ? MaintenanceBlockEntity.energyPerTick() : 0);
        if (++ticks > 20 && working != wasWorking) {
            (working ? UiSounds.WORK_START : UiSounds.WORK_END).play();
        }
        wasWorking = working;
    }

    /** What a full repair still takes: units of each material, in the order the station uses them. */
    private static Map<Item, Integer> plan(ItemStack stack) {
        Map<Item, Integer> need = new LinkedHashMap<>();
        ItemStack copy = stack.copy();
        for (int step = 0; step < 500 && Maintenance.needsRepair(copy); step++) {
            Item material = Maintenance.materialFor(copy, item -> true);
            if (material == null) {
                break;
            }
            Maintenance.apply(copy, material);
            need.merge(material, 1, Integer::sum);
        }
        return need;
    }

    private int held(Item item) {
        int count = 0;
        for (int i = 1; i <= MaintenanceBlockEntity.MATERIAL_SLOTS; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private record State(UiSprites.Tone tone, Component badge, Component plate) {
    }

    private State state() {
        ItemStack stack = menu.serviced();
        Maintenance.Kind kind = Maintenance.kind(stack);
        if (kind == Maintenance.Kind.NONE) {
            return new State(UiSprites.Tone.INFO, tr("state.idle"), tr("empty"));
        }
        if (!Maintenance.needsRepair(stack)) {
            return new State(UiSprites.Tone.OK, tr("state.done"), tr("plate.done"));
        }
        if (Maintenance.materialFor(stack, menu::hasMaterial) == null) {
            return new State(UiSprites.Tone.ERROR, Component.translatable("screen.projectrebirth.station.status.missing"),
                    Component.translatable(needKey(stack, kind)));
        }
        if (menu.emiPaused()) {
            return new State(UiSprites.Tone.WARNING, Component.translatable("screen.projectrebirth.station.status.emi"),
                    Component.translatable("screen.projectrebirth.station.plate.emi"));
        }
        if (menu.energy() < MaintenanceBlockEntity.energyPerTick()) {
            return new State(UiSprites.Tone.WARNING, Component.translatable("screen.projectrebirth.station.status.no_energy"),
                    Component.translatable("screen.projectrebirth.station.plate.no_energy"));
        }
        return new State(UiSprites.Tone.OK, tr("state.repairing"), tr("plate.repairing"));
    }

    /** The material that is missing for the next step, named exactly. */
    private String needKey(ItemStack stack, Maintenance.Kind kind) {
        return switch (kind) {
            case ARMOR -> {
                boolean plates = Maintenance.armorCritical(stack) < Maintenance.maxCritical(stack) && !menu.hasMaterial(PowerArmorRegistry.ARMOR_PLATE.get());
                boolean iron = Maintenance.armorStructural(stack) < Maintenance.maxStructural(stack) && !menu.hasMaterial(Items.IRON_INGOT);
                yield plates && iron ? "screen.projectrebirth.maintenance.need_armor"
                        : plates ? "screen.projectrebirth.maintenance.need_plates" : "screen.projectrebirth.maintenance.need_iron";
            }
            case MACHINE -> "screen.projectrebirth.maintenance.need_machine";
            case SYNTHETIC_BODY -> Maintenance.bodyIntegrity(stack) < Maintenance.maxBodyIntegrity(stack)
                    ? "screen.projectrebirth.maintenance.need_fiber" : "screen.projectrebirth.maintenance.need_board";
            default -> Maintenance.bodyIntegrity(stack) < Maintenance.maxBodyIntegrity(stack)
                    ? "screen.projectrebirth.maintenance.need_iron" : "screen.projectrebirth.maintenance.need_actuator";
        };
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        ItemStack stack = menu.serviced();
        Maintenance.Kind kind = Maintenance.kind(stack);
        State state = state();

        // materials
        UiPanels.label(graphics, tr("label.materials"), x + 8, y + 26, 26);
        tooltip(x + 8, y + 26, 26, 9, () -> com.projectrebirth.client.ui.UiTooltip.of(tr("label.materials_full")));
        for (int i = 0; i < MaintenanceBlockEntity.MATERIAL_SLOTS; i++) {
            UiSlots.frame(graphics, UiSlots.Kind.INPUT, x + MaintenanceMenu.MATERIAL_X, y + MaintenanceMenu.MATERIAL_Y + i * MaintenanceMenu.MATERIAL_STEP);
        }

        // the repair screen
        int sx = x + SCREEN_X;
        int sy = y + SCREEN_Y;
        UiPanels.screen(graphics, sx, sy, SCREEN_W, SCREEN_H);
        Component header = kind == Maintenance.Kind.NONE ? Component.literal("> ").append(tr("process.idle"))
                : Component.literal("> ").append(tr("process.repair")).append(": ").append(UiText.upper(stack.getHoverName()));
        text(graphics, header, sx + 6, sy + 5, SCREEN_W - 12, kind == Maintenance.Kind.NONE ? UiColors.AMBER_DIM : UiColors.AMBER);
        if (kind != Maintenance.Kind.NONE) {
            drawModel(graphics, stack, kind, sx + 3, sy + 15, 44, SCREEN_H - 29, mouseX, mouseY);
            drawScales(graphics, stack, kind, sx + 50, sy + 20, SCREEN_W - 56);
            float step = progress.follow(menu.progress());
            UiBars.progress(graphics, sx + 6, sy + SCREEN_H - 11, SCREEN_W - 66, 6, step, UiColors.AMBER);
            text(graphics, tr("step", Mth.floor(step * 100)), sx + SCREEN_W - 56, sy + SCREEN_H - 12, 50, UiColors.AMBER_DIM);
        }

        // serviced slot, energy, state
        UiPanels.label(graphics, tr("label.serviced"), x + RIGHT_X, y + 26, W - 7 - RIGHT_X);
        UiSlots.frame(graphics, UiSlots.Kind.OUTPUT, x + MaintenanceMenu.SERVICE_X, y + MaintenanceMenu.SERVICE_Y);
        energy.draw(this, graphics, x + RIGHT_X, y + 66, W - 7 - RIGHT_X, 26, menu.energy(), menu.energyCapacity());
        badge(graphics, state.tone, state.badge, x + RIGHT_X, y + 96, W - 7 - RIGHT_X);
        plate(graphics, state.tone == UiSprites.Tone.OK ? UiSprites.Tone.INFO : state.tone, state.plate, x + 7, y + 114, W - 14);

        // inventory and the repair plan
        inventory(graphics, x + MaintenanceMenu.INVENTORY_X, y + MaintenanceMenu.INVENTORY_Y);
        drawPlan(graphics, stack, kind, x + PLAN_X, y + PLAN_Y, W - 7 - PLAN_X, H - 7 - PLAN_Y);
    }

    /**
     * The serviced thing as its 3D model (armor, body or machine), turning after the mouse; the item's icon only if there is
     * no model. The models are made once per kind and never added to the world.
     */
    private void drawModel(GuiGraphics graphics, ItemStack stack, Maintenance.Kind kind, int x, int y, int width, int height, int mouseX, int mouseY) {
        net.minecraft.world.entity.LivingEntity model = model(stack, kind);
        if (model == null) {
            graphics.pose().pushPose();
            graphics.pose().translate(x + width / 2.0f - 16, y + height / 2.0f - 16, 0.0f);
            graphics.pose().scale(2.0f, 2.0f, 1.0f);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();
            return;
        }
        // the size of the entity's kind: a model made only for drawing may not have its own size yet
        var size = model.getType().getDimensions();
        float tall = Math.max(Math.max(size.height(), model.getBbHeight()), size.width() * 0.9f);
        int scale = Math.max(6, Math.round(height * 0.85f / Math.max(0.5f, tall)));
        // a model made only for drawing has never lived: give it its health, or it is drawn fallen like a dead entity
        if (model.getHealth() <= 0.0f || model.deathTime > 0) {
            model.setHealth(Math.max(1.0f, model.getMaxHealth()));
            model.deathTime = 0;
        }
        // turned a little to the viewer, not after the mouse: the mouse is usually far from this small box
        com.projectrebirth.client.GuiAtlas.model(graphics, x, y, x + width, y + height, scale, 0.0625f, x + width / 2.0f + 30, y + height / 2.0f, model);
    }

    private final java.util.Map<Object, net.minecraft.world.entity.LivingEntity> models = new java.util.HashMap<>();

    @javax.annotation.Nullable
    private net.minecraft.world.entity.LivingEntity model(ItemStack stack, Maintenance.Kind kind) {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        switch (kind) {
            case ARMOR -> {
                if (!(stack.getItem() instanceof com.projectrebirth.powerarmor.PowerArmorItem item)) {
                    return null;
                }
                return models.computeIfAbsent(item.variant(), variant -> {
                    var armor = com.projectrebirth.powerarmor.PowerArmorRegistry.POWER_ARMOR.get().create(minecraft.level);
                    if (armor != null) {
                        armor.setVariant(item.variant());
                    }
                    return armor;
                });
            }
            case ROBOT_BODY, SYNTHETIC_BODY -> {
                if (!(stack.getItem() instanceof com.projectrebirth.bodies.ArtificialBodyItem item)) {
                    return null;
                }
                net.minecraft.world.entity.LivingEntity body = models.computeIfAbsent(item.bodyClass(),
                        bodyClass -> com.projectrebirth.bodies.BodyEntities.type(item.bodyClass()).create(minecraft.level));
                if (body instanceof com.projectrebirth.bodies.ArtificialBodyEntity artificial) {
                    artificial.setUpgrades(com.projectrebirth.upgrades.Upgrades.ofStack(stack));
                }
                return body;
            }
            case MACHINE -> {
                if (!(stack.getItem() instanceof com.projectrebirth.garrison.PackedMachineItem item)) {
                    return null;
                }
                return models.computeIfAbsent(item, key -> item.entityType().create(minecraft.level));
            }
            default -> {
                return null;
            }
        }
    }

    private void drawScales(GuiGraphics graphics, ItemStack stack, Maintenance.Kind kind, int x, int y, int width) {
        switch (kind) {
            case ARMOR -> {
                scale(graphics, x, y, width, tr("scale.structural"), Maintenance.armorStructural(stack), Maintenance.maxStructural(stack), false);
                scale(graphics, x, y + 13, width, tr("scale.critical"), Maintenance.armorCritical(stack), Maintenance.maxCritical(stack), false);
            }
            case MACHINE -> {
                var packed = com.projectrebirth.garrison.PackedMachineItem.packed(stack);
                scale(graphics, x, y, width, tr("scale.structural"), packed.structural(), packed.kind().maxStructural(), false);
                scale(graphics, x, y + 13, width, tr("scale.critical"), packed.critical(), packed.kind().maxCritical(), false);
                scale(graphics, x, y + 26, width, tr("wear"), packed.wear() * 100.0f, 100.0f, true);
            }
            default -> {
                scale(graphics, x, y, width, tr("integrity"), Maintenance.bodyIntegrity(stack), Maintenance.maxBodyIntegrity(stack), false);
                scale(graphics, x, y + 13, width, tr("wear"), Maintenance.bodyWear(stack), 100.0f, true);
                if (Maintenance.maxAndroidCritical(stack) > 0) {
                    scale(graphics, x, y + 26, width, tr("scale.critical"), Maintenance.androidCritical(stack), Maintenance.maxAndroidCritical(stack), false);
                }
            }
        }
    }

    /** A line "name  [bar]  value / max"; wear fills with the wear, so it is coloured as a fault. */
    private void scale(GuiGraphics graphics, int x, int y, int width, Component name, float value, float max, boolean wear) {
        float fraction = max > 0 ? Mth.clamp(value / max, 0.0f, 1.0f) : 0.0f;
        int nameWidth = 62;
        text(graphics, name, x, y + 1, nameWidth - 2, UiColors.SCREEN_TEXT);
        int color = wear ? UiColors.WEAR : UiColors.integrity(fraction);
        UiBars.horizontal(graphics, x + nameWidth, y, width - nameWidth - 46, 9, fraction, color);
        text(graphics, Component.literal(Mth.ceil(value) + " / " + Mth.ceil(max)), x + width - 43, y + 1, 43, color);
    }

    private void drawPlan(GuiGraphics graphics, ItemStack stack, Maintenance.Kind kind, int x, int y, int width, int height) {
        UiPanels.screen(graphics, x, y, width, height);
        text(graphics, UiText.upper(tr("label.plan")), x + 5, y + 5, width - 10, UiColors.AMBER);
        if (kind == Maintenance.Kind.NONE) {
            UiText.wrapped(graphics, tr("plan.empty"), x + 5, y + 17, width - 10, height - 22, UiColors.SCREEN_TEXT);
            return;
        }
        Map<Item, Integer> plan = plan(stack);
        if (plan.isEmpty()) {
            UiText.wrapped(graphics, tr("plate.done"), x + 5, y + 17, width - 10, height - 22, UiColors.OK);
            return;
        }
        int row = 0;
        int steps = 0;
        for (Map.Entry<Item, Integer> entry : plan.entrySet()) {
            int ry = y + 16 + row * 17;
            ItemStack icon = new ItemStack(entry.getKey());
            graphics.renderItem(icon, x + 5, ry);
            int have = held(entry.getKey());
            boolean enough = have >= entry.getValue();
            UiSprites.draw(graphics, enough ? UiSprites.ICON_CHECK : UiSprites.ICON_CROSS, x + 24, ry + 5, 7, 7);
            text(graphics, Component.literal(Math.min(have, entry.getValue()) + "/" + entry.getValue() + "  ").append(icon.getHoverName()),
                    x + 34, ry + 4, width - 39, enough ? UiColors.SCREEN_TEXT : UiColors.ERROR);
            steps += entry.getValue();
            row++;
            if (row >= 3) {
                break;
            }
        }
        int ticks = steps * MaintenanceBlockEntity.stepTicks();
        Component cost = tr("plan.cost", clock(ticks), EnergyUtil.format(ticks * MaintenanceBlockEntity.energyPerTick()));
        text(graphics, cost, x + 5, y + height - 12, width - 10, UiColors.TEXT_SECONDARY);
    }

    @Override
    protected void drawOver(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (menu.serviced().isEmpty()) {
            UiSlots.ghostLabel(graphics, tr("ghost"), x + MaintenanceMenu.SERVICE_X, y + MaintenanceMenu.SERVICE_Y);
        }
    }

    private static String clock(int ticks) {
        int seconds = (ticks + 19) / 20;
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
