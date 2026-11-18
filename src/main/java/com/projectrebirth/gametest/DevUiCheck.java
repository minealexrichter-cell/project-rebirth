package com.projectrebirth.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.client.codex.CodexScreen;
import com.projectrebirth.client.screen.FactionScreen;
import com.projectrebirth.client.screen.HudSettingsScreen;
import com.projectrebirth.client.ui.UiText;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Development only (the gametest package is not in the jar), 1.8.0 block A: the layout check of every window.
 * <p>
 * {@code /rbdev ui check [languages] [scales]} (client command; languages like {@code ru_ru,en_us}, scales like
 * {@code 2,3,4,0} where 0 is "Auto") puts the bench of blocks next to the player (server: {@code /rbdev ui bench}), then for
 * each language, each window and each GUI scale opens the window, waits a few frames and writes down every text the
 * library had to cut (UiText.audit) and whether the window fits the screen; it saves a picture of the window into
 * {@code <game dir>/ui_check/<language>/<scale>/<window>.png} and the whole report into {@code ui_check/report.txt}.
 * Client-only windows (Codex, HUD settings, faction, the widget showcase) are checked too. The player needs to be an
 * operator, the game should run in a window of the size to check (1920 x 1080 or 1366 x 768).
 * <p>
 * {@code /rbdev ui widgets} opens the showcase of the widget library (DevUiWidgetsScreen).
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID, value = Dist.CLIENT)
public final class DevUiCheck {
    private static final int WAIT_OPEN = 60;
    private static final int SETTLE = 6;

    private record Target(String name, BlockPos pos, Supplier<Screen> screen) {
    }

    private static Run run;

    private DevUiCheck() {
    }

    @SubscribeEvent
    static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rbdev").then(Commands.literal("ui")
                .then(Commands.literal("widgets").executes(ctx -> widgets(0))
                        .then(Commands.argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1))
                                .executes(ctx -> widgets(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("check")
                        .executes(ctx -> start("ru_ru,en_us", "2,3,4,0"))
                        .then(Commands.argument("languages", StringArgumentType.word())
                                .executes(ctx -> start(StringArgumentType.getString(ctx, "languages"), "2,3,4,0"))
                                .then(Commands.argument("scales", StringArgumentType.word())
                                        .executes(ctx -> start(StringArgumentType.getString(ctx, "languages"),
                                                StringArgumentType.getString(ctx, "scales"))))))));
    }

    private static int widgets(int page) {
        Minecraft minecraft = Minecraft.getInstance();
        DevUiWidgetsScreen.page(page);
        minecraft.tell(() -> minecraft.setScreen(new DevUiWidgetsScreen()));
        return 1;
    }

    private static int start(String languages, String scales) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || run != null) {
            return 0;
        }
        List<Integer> scaleList = new ArrayList<>();
        for (String scale : scales.split(",")) {
            scaleList.add(Integer.parseInt(scale.trim()));
        }
        run = new Run(List.of(languages.split(",")), scaleList, minecraft.player.blockPosition().offset(2, 0, 3));
        return 1;
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (run != null && run.step()) {
            run = null;
        }
    }

    /** The check as a state machine, one step a tick. */
    private static final class Run {
        private final List<String> languages;
        private final List<Integer> scales;
        private final BlockPos origin;
        private final List<Target> targets = new ArrayList<>();
        private final List<String> report = new ArrayList<>();
        private final Path out;
        private final String startLanguage;
        private final int startScale;
        private int language;
        private int target;
        private int scale;
        private int wait;
        private int problems;
        private Stage stage = Stage.BENCH;
        private java.util.concurrent.CompletableFuture<Void> reload;

        private enum Stage { BENCH, LANGUAGE, RELOADING, MOVE, OPEN, WAIT_OPEN, SCALE, SETTLE, NEXT, DONE }

        Run(List<String> languages, List<Integer> scales, BlockPos origin) {
            Minecraft minecraft = Minecraft.getInstance();
            this.languages = languages;
            this.scales = scales;
            this.origin = origin;
            this.out = minecraft.gameDirectory.toPath().resolve("ui_check");
            this.startLanguage = minecraft.options.languageCode;
            this.startScale = minecraft.options.guiScale().get();
            for (int i = 0; i < DevUiBench.BLOCKS.size(); i++) {
                targets.add(new Target(DevUiBench.BLOCKS.get(i), DevUiBench.place(origin, i), null));
            }
            targets.add(new Target("codex", null, CodexScreen::new));
            targets.add(new Target("hud_settings", null, () -> new HudSettingsScreen(null)));
            targets.add(new Target("faction", null, FactionScreen::new));
            targets.add(new Target("widgets", null, DevUiWidgetsScreen::new));
            say("ui check: " + targets.size() + " windows × " + languages.size() + " languages × " + scales.size() + " scales");
        }

        /** Returns true when the check is over. */
        boolean step() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                finish();
                return true;
            }
            if (wait > 0) {
                wait--;
                return false;
            }
            switch (stage) {
                case BENCH -> {
                    command("rbdev ui bench " + origin.getX() + " " + origin.getY() + " " + origin.getZ());
                    wait = 20;
                    stage = Stage.LANGUAGE;
                }
                case LANGUAGE -> {
                    String code = languages.get(language);
                    if (!code.equals(minecraft.options.languageCode)) {
                        minecraft.options.languageCode = code;
                        minecraft.getLanguageManager().setSelected(code);
                        reload = minecraft.reloadResourcePacks();
                        stage = Stage.RELOADING;
                    } else {
                        stage = Stage.MOVE;
                    }
                }
                case RELOADING -> {
                    if (reload.isDone() && minecraft.getOverlay() == null) {
                        wait = 10;
                        stage = Stage.MOVE;
                    }
                }
                case MOVE -> {
                    Target t = targets.get(target);
                    if (t.pos != null) {
                        command("tp @s " + (t.pos.getX() + 0.5) + " " + t.pos.getY() + " " + (t.pos.getZ() - 2.5) + " 0 30");
                        wait = 8;
                    }
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    Target t = targets.get(target);
                    UiText.audit = new LinkedHashSet<>();
                    if (t.pos != null) {
                        command("rbdev use " + t.pos.getX() + " " + t.pos.getY() + " " + t.pos.getZ());
                    } else {
                        minecraft.setScreen(t.screen.get());
                    }
                    wait = 0;
                    openWait = 0;
                    stage = Stage.WAIT_OPEN;
                }
                case WAIT_OPEN -> {
                    if (minecraft.screen != null) {
                        scale = 0;
                        stage = Stage.SCALE;
                    } else if (++openWait > WAIT_OPEN) {
                        line(targets.get(target).name, "-", "no window opened");
                        stage = Stage.NEXT;
                    }
                }
                case SCALE -> {
                    minecraft.options.guiScale().set(scales.get(scale));
                    minecraft.resizeDisplay();
                    UiText.audit = new LinkedHashSet<>();
                    wait = SETTLE;
                    stage = Stage.SETTLE;
                }
                case SETTLE -> {
                    inspect(minecraft);
                    if (++scale < scales.size()) {
                        stage = Stage.SCALE;
                    } else {
                        stage = Stage.NEXT;
                    }
                }
                case NEXT -> {
                    if (minecraft.screen instanceof AbstractContainerScreen<?>) {
                        minecraft.player.closeContainer();
                    } else {
                        minecraft.setScreen(null);
                    }
                    wait = 4;
                    if (++target < targets.size()) {
                        stage = Stage.MOVE;
                    } else if (++language < languages.size()) {
                        target = 0;
                        stage = Stage.LANGUAGE;
                    } else {
                        stage = Stage.DONE;
                    }
                }
                case DONE -> {
                    finish();
                    return true;
                }
            }
            return false;
        }

        private int openWait;

        private void inspect(Minecraft minecraft) {
            Target t = targets.get(target);
            String scaleName = scales.get(scale) == 0 ? "auto(" + (int) minecraft.getWindow().getGuiScale() + ")" : String.valueOf(scales.get(scale));
            Screen screen = minecraft.screen;
            List<String> found = new ArrayList<>();
            int x = 0;
            int y = 0;
            int w = minecraft.getWindow().getGuiScaledWidth();
            int h = minecraft.getWindow().getGuiScaledHeight();
            if (screen instanceof AbstractContainerScreen<?> container) {
                x = container.getGuiLeft();
                y = container.getGuiTop();
                w = container.getXSize();
                h = container.getYSize();
                if (x < 0 || y < 0 || x + w > screen.width || y + h > screen.height) {
                    found.add("window " + w + "x" + h + " does not fit the screen " + screen.width + "x" + screen.height);
                }
            }
            Set<String> cut = UiText.audit == null ? Set.of() : UiText.audit;
            for (String text : cut) {
                found.add("cut: \"" + text + "\"");
            }
            String screenName = screen == null ? "none" : screen.getClass().getSimpleName();
            if (found.isEmpty()) {
                line(t.name, scaleName, "ok (" + screenName + ")");
            } else {
                problems += found.size();
                for (String problem : found) {
                    line(t.name, scaleName, problem + " (" + screenName + ")");
                }
            }
            save(minecraft, t.name, scaleName, x, y, w, h);
        }

        private void save(Minecraft minecraft, String name, String scaleName, int x, int y, int w, int h) {
            double gui = minecraft.getWindow().getGuiScale();
            try (NativeImage frame = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                int px = Math.max(0, (int) (x * gui) - 4);
                int py = Math.max(0, (int) (y * gui) - 4);
                int pw = Math.min(frame.getWidth() - px, (int) (w * gui) + 8);
                int ph = Math.min(frame.getHeight() - py, (int) (h * gui) + 8);
                try (NativeImage crop = new NativeImage(pw, ph, false)) {
                    frame.copyRect(crop, px, py, 0, 0, pw, ph, false, false);
                    Path dir = out.resolve(languages.get(language)).resolve(scaleName.replace("(", "_").replace(")", ""));
                    Files.createDirectories(dir);
                    crop.writeToFile(dir.resolve(name + ".png"));
                }
            } catch (IOException | RuntimeException e) {
                ProjectRebirth.LOGGER.warn("ui check: could not save the picture of {}", name, e);
            }
        }

        private void line(String window, String scaleName, String text) {
            String line = languages.get(language) + "  scale " + scaleName + "  " + window + ": " + text;
            report.add(line);
            ProjectRebirth.LOGGER.info("[ui check] {}", line);
        }

        private void finish() {
            Minecraft minecraft = Minecraft.getInstance();
            UiText.audit = null;
            minecraft.options.guiScale().set(startScale);
            minecraft.resizeDisplay();
            if (!startLanguage.equals(minecraft.options.languageCode)) {
                minecraft.options.languageCode = startLanguage;
                minecraft.getLanguageManager().setSelected(startLanguage);
                minecraft.reloadResourcePacks();
            }
            minecraft.options.save();
            try {
                Files.createDirectories(out);
                Files.write(out.resolve("report.txt"), report);
            } catch (IOException e) {
                ProjectRebirth.LOGGER.warn("ui check: could not write the report", e);
            }
            say("ui check done: " + report.size() + " lines, " + problems + " problems; report in ui_check/report.txt");
        }

        private static void command(String command) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getConnection() != null) {
                minecraft.getConnection().sendCommand(command);
            }
        }

        private static void say(String text) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(text), false);
            }
        }
    }
}
