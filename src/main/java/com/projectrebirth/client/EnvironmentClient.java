package com.projectrebirth.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.EnvironmentRegistry;
import com.projectrebirth.environment.EnvironmentStatusPayload;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.environment.HumanBody;
import com.projectrebirth.environment.Protection;
import com.projectrebirth.powerarmor.PowerArmorEntity;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The client side of the environment: it only shows what the server sends. The human panel in the status panel of the HUD
 * (moved and sized with the HUD settings): dose (with a dosimeter only), toxic load, filter, and the hazards now. An
 * artificial body and a pilot get only the catastrophe line, see {@link CatastropheClient}.
 */
public final class EnvironmentClient {
    private static final int LABEL_WIDTH = 52;
    private static final int BAR_WIDTH = HudLayout.BAR_WIDTH;
    private static final int ROW = 12;
    private static final int FIRST_ROW = 12;
    private static final int GREY = 0xFF9E9E9E;
    private static EnvironmentStatusPayload last;

    private EnvironmentClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(EnvironmentClient::registerGuiLayers);
        EnvironmentStatusPayload.clientHandler = payload -> Minecraft.getInstance().execute(() -> last = payload);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ProjectRebirth.id("human_environment"), EnvironmentClient::renderHumanPanel);
    }

    /** The radiation reaching the player now, as the server last said; 0 before it said anything. */
    static float radiation() {
        return last == null ? 0.0f : last.radiation();
    }

    /** Whether the server says the player is inside a sealed room now (its own air; no storm inside). */
    static boolean inSealedRoom() {
        return last != null && (last.shelter() == EnvironmentStatusPayload.SHELTER_CLEAN || last.shelter() == EnvironmentStatusPayload.SHELTER_POLLUTED);
    }

    static boolean carriesDosimeter(Player player) {
        return player.getInventory().contains(stack -> stack.is(EnvironmentRegistry.DOSIMETER.get()));
    }

    /** Whether the human panel is for this player now: a human on foot who can be hurt. */
    static boolean showsHumanPanel(Minecraft minecraft, Player player) {
        return player != null && last != null && RebirthConfig.SERVER_SPEC.isLoaded() && !TransferService.isInArtificialBody(player)
                && !(player.getVehicle() instanceof PowerArmorEntity) && minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer();
    }

    private static void renderHumanPanel(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.options.hideGui || !HudLayout.enabled() || !showsHumanPanel(minecraft, player)) {
            return;
        }
        Font font = minecraft.font;
        HudLayout.pushPanel(graphics);
        int y = FIRST_ROW;
        // Dose: nobody feels radiation; only a dosimeter tells.
        if (carriesDosimeter(player)) {
            int stage = HumanBody.doseStage(last.dose());
            bar(graphics, font, y, Component.translatable("hud.projectrebirth.human.dose"), last.dose() / RebirthConfig.HUMAN_DOSE_DAMAGE.get().floatValue(),
                    stageColor(stage, 4), number(last.dose()) + stageText(stage));
        } else {
            graphics.drawString(font, Component.translatable("hud.projectrebirth.human.dose"), 0, y, 0xFFFFFFFF);
            graphics.drawString(font, Component.translatable("hud.projectrebirth.human.no_dosimeter"), LABEL_WIDTH, y, GREY);
        }
        y += ROW;
        int toxinStage = HumanBody.toxinStage(last.toxin());
        bar(graphics, font, y, Component.translatable("hud.projectrebirth.human.toxin"), last.toxin() / RebirthConfig.HUMAN_TOXIN_DAMAGE.get().floatValue(),
                stageColor(toxinStage, 3), number(last.toxin()) + stageText(toxinStage));
        y += ROW;
        if (!Float.isNaN(last.temperature())) {
            // temperature (1.5.0, temporary until the HUD of block G): the place, and the heat or cold stress as a bar
            int thermalStage = com.projectrebirth.environment.Temperature.stage(last.thermal());
            boolean hot = last.thermal() > 0.0f;
            String text = Math.round(last.temperature()) + "°C" + (thermalStage > 0 ? " · " + Component.translatable(hot
                    ? "hud.projectrebirth.human.hot" : "hud.projectrebirth.human.cold").getString() + stageText(thermalStage) : "");
            bar(graphics, font, y, Component.translatable("hud.projectrebirth.human.temperature"),
                    Math.abs(last.thermal()) / com.projectrebirth.environment.Temperature.MAX_STRESS,
                    thermalStage == 0 ? 0xFF90A4AE : hot ? 0xFFFF7043 : 0xFF4FC3F7, text);
            y += ROW;
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (Protection.breathes(head)) {
            float share = Mth.clamp(Protection.filterLeft(head) / RebirthConfig.PROTECTION_FILTER_LIFE.get().floatValue(), 0.0f, 1.0f);
            bar(graphics, font, y, Component.translatable("hud.projectrebirth.human.filter"), share,
                    share > 0.25f ? 0xFF7FE8FF : 0xFFFFA726, share > 0.0f ? Math.round(share * 100.0f) + "%" : Component.translatable("hud.projectrebirth.human.filter_empty").getString());
        } else {
            graphics.drawString(font, Component.translatable("hud.projectrebirth.human.filter"), 0, y, 0xFFFFFFFF);
            graphics.drawString(font, Component.translatable("hud.projectrebirth.human.no_respirator"), LABEL_WIDTH, y, GREY);
        }
        y += ROW;
        renderHazards(graphics, font, y, carriesDosimeter(player));
        y += ROW;
        renderShelter(graphics, font, y);
        graphics.pose().popPose();
    }

    /** The shelter the player is in (only inside one) and the contamination on them (only if there is any to speak of). */
    private static void renderShelter(GuiGraphics graphics, Font font, int y) {
        if (last.shelter() != EnvironmentStatusPayload.SHELTER_NONE) {
            int state = last.shelter();
            int color = state == EnvironmentStatusPayload.SHELTER_CLEAN ? 0xFF81C784 : state == EnvironmentStatusPayload.SHELTER_POLLUTED ? 0xFFFFA726 : 0xFFFF5555;
            graphics.drawString(font, Component.translatable("hud.projectrebirth.shelter"), 0, y, 0xFFFFFFFF);
            GuiAtlas.sealIcon(graphics, LABEL_WIDTH, y - 1);
            graphics.drawString(font, Component.translatable("hud.projectrebirth.shelter." + state), LABEL_WIDTH + 11, y, color);
            y += ROW;
        }
        if (last.contamination() >= 0.5f) {
            graphics.drawString(font, Component.translatable("hud.projectrebirth.contamination"), 0, y, 0xFFFFFFFF);
            graphics.drawString(font, number(last.contamination()), LABEL_WIDTH, y, last.contamination() >= 30.0f ? 0xFFFFA726 : 0xFFD4E157);
        }
    }

    /** What is dangerous here now, after protection. */
    private static void renderHazards(GuiGraphics graphics, Font font, int y, boolean dosimeter) {
        graphics.drawString(font, Component.translatable("hud.projectrebirth.human.air"), 0, y, 0xFFFFFFFF);
        List<Hazard> hazards = new ArrayList<>();
        List<Component> parts = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (last.dangerous(Hazard.POLLUTION)) {
            hazards.add(Hazard.POLLUTION);
            parts.add(Component.translatable("hud.projectrebirth.hazard.pollution"));
            colors.add(0xFFD4E157);
        }
        if (last.dangerous(Hazard.DUST)) {
            hazards.add(Hazard.DUST);
            parts.add(Component.translatable("hud.projectrebirth.hazard.dust"));
            colors.add(0xFFD7B377);
        }
        if (last.dangerous(Hazard.ACID)) {
            hazards.add(Hazard.ACID);
            parts.add(Component.translatable("hud.projectrebirth.hazard.acid"));
            colors.add(0xFF9CCC65);
        }
        // Radiation is felt by no one: without a dosimeter it is not shown.
        if (dosimeter && last.dangerous(Hazard.RADIATION)) {
            hazards.add(Hazard.RADIATION);
            parts.add(Component.translatable("hud.projectrebirth.hazard.radiation_value", number(last.radiation())));
            colors.add(0xFFFF5555);
        }
        int x = LABEL_WIDTH;
        if (parts.isEmpty()) {
            graphics.drawString(font, Component.translatable("hud.projectrebirth.human.safe"), x, y, 0xFF81C784);
            return;
        }
        for (int i = 0; i < parts.size(); i++) {
            GuiAtlas.hazardMark(graphics, x, y - 1, hazards.get(i));
            x += 11;
            graphics.drawString(font, parts.get(i), x, y, colors.get(i));
            x += font.width(parts.get(i)) + 6;
        }
    }

    private static void bar(GuiGraphics graphics, Font font, int y, Component label, float fraction, int color, String text) {
        graphics.drawString(font, label, 0, y, 0xFFFFFFFF);
        GuiAtlas.bar(graphics, LABEL_WIDTH, y, BAR_WIDTH, 9, Mth.clamp(fraction, 0.0f, 1.0f), color);
        graphics.drawString(font, text, LABEL_WIDTH + BAR_WIDTH + 4, y, 0xFFFFFFFF);
    }

    private static int stageColor(int stage, int last) {
        return stage == 0 ? 0xFF81C784 : stage >= last ? 0xFFE53935 : stage >= last - 1 ? 0xFFFFA726 : 0xFFFFD54F;
    }

    private static String stageText(int stage) {
        return stage == 0 ? "" : " (" + Component.translatable("hud.projectrebirth.human.stage", stage).getString() + ")";
    }

    private static String number(float value) {
        return String.format(Locale.ROOT, value < 10.0f ? "%.1f" : "%.0f", value);
    }
}
