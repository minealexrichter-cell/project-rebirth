package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiButton;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiDropdown;
import com.projectrebirth.client.ui.UiList;
import com.projectrebirth.client.ui.UiMap;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSearchField;
import com.projectrebirth.client.ui.UiSlots;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiTable;
import com.projectrebirth.client.ui.UiTabs;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.client.ui.UiToast;
import com.projectrebirth.client.ui.UiToggle;
import com.projectrebirth.client.ui.UiTooltip;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Development only (1.8.0 block A): the showcase of the widget library in the style "Industrial terminal", on two tabs —
 * "Elements" (buttons, chips, switches, drop-down list, search, slots, bars, plates, notices) and "Lists and map" (cards,
 * table, the shared map). For the owner's acceptance and for /rbdev ui check. Opened with /rbdev ui widgets.
 */
public class DevUiWidgetsScreen extends Screen {
    private static final int W = 440;
    private static final int H = 248;
    private static int page;

    private final UiText.Hints hints = new UiText.Hints();
    private final UiBars.Smooth charge = new UiBars.Smooth();
    private boolean monsters = true;
    private boolean enemies = true;
    private String sort = "distance";
    private int layers = 3;
    private String chosen = "Мех охраны «Бастион»";
    private int left;
    private int top;
    private UiMap map;
    private UiDropdown<String> dropdown;

    public DevUiWidgetsScreen() {
        super(Component.literal("Витрина виджетов"));
    }

    static void page(int value) {
        page = value;
    }

    private static Component t(String text) {
        return Component.literal(text);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (map != null) {
            map.close();
            map = null;
        }
        dropdown = null;
        addRenderableWidget(new UiTabs(left + 8, top + 23, 200, List.of(UiTabs.Tab.of(t("Элементы")), UiTabs.Tab.of(t("Списки и карта"))),
                () -> page, p -> {
                    page = p;
                    rebuildWidgets();
                }));
        addRenderableWidget(UiButton.help(left + W - 37, top + 4, this, "keys"));
        addRenderableWidget(UiButton.icon(left + W - 21, top + 4, 13, UiSprites.ICON_CLOSE, 7, Component.translatable("screen.projectrebirth.ui.close"), this::onClose));
        if (page == 0) {
            initElements();
        } else {
            initLists();
        }
    }

    private void initElements() {
        int x = left + 12;
        int y = top + 52;
        addRenderableWidget(new UiButton(x, y, 64, 14, t("Собрать"), null, 0, UiButton.Look.METAL, () -> { }));
        UiButton off = new UiButton(x + 68, y, 64, 14, t("Разобрать"), null, 0, UiButton.Look.METAL, () -> { });
        off.disable(t("Нельзя: машина в бою"));
        addRenderableWidget(off);
        addRenderableWidget(new UiButton(x, y + 18, 64, 11, t("Могу сделать"), UiSprites.ICON_CHECK, 7, UiButton.Look.CHIP, () -> { }).selected(() -> true));
        addRenderableWidget(new UiButton(x + 68, y + 18, 64, 11, t("Все"), null, 0, UiButton.Look.CHIP, () -> { }));
        addRenderableWidget(new UiToggle(x, y + 36, 132, () -> monsters, v -> monsters = v,
                on -> t(on ? "Монстры и мутанты — бить" : "Монстры и мутанты — не бить")));
        addRenderableWidget(new UiToggle(x, y + 49, 132, () -> enemies, v -> enemies = v,
                on -> t(on ? "Враги по войне — бить" : "Враги по войне — не бить")));
        UiToggle intruders = new UiToggle(x, y + 62, 132, () -> false, v -> { }, on -> t("Чужие на нашей земле — не бить"));
        intruders.disable(t("Режим «нарушители» запрещён на этом сервере"));
        addRenderableWidget(intruders);
        addRenderableWidget(new UiSearchField(x, y + 78, 132, 12, t("Поиск машины")));
        addRenderableWidget(new UiButton(left + 300, top + 216, 128, 14, t("Показать уведомление"), null, 0, UiButton.Look.METAL,
                () -> UiToast.show(UiSprites.Tone.ERROR, t("Тревога пульта"), t("Мех «Бастион»: нет поста рядом"))));
        dropdown = new UiDropdown<>(x, y + 108, 132, List.of("distance", "charge", "class"),
                key -> t(switch (key) {
                    case "distance" -> "Сортировка: по расстоянию";
                    case "charge" -> "Сортировка: по заряду";
                    default -> "Сортировка: по классу";
                }), () -> sort, v -> sort = v);
        addRenderableWidget(dropdown);
    }

    private record Machine(String name, float structural, float critical, float charge, UiSprites.Tone tone, String state) {
    }

    private static final List<Machine> MACHINES = List.of(
            new Machine("Мех охраны «Бастион»", 0.92f, 1.0f, 0.74f, UiSprites.Tone.OK, "Охраняет пост"),
            new Machine("Малый робот №2", 0.41f, 0.88f, 0.22f, UiSprites.Tone.WARNING, "Мало заряда"),
            new Machine("Боевой андроид «Скаут»", 0.15f, 0.36f, 0.61f, UiSprites.Tone.ERROR, "Нужен ремонт"),
            new Machine("Рабочий андроид", 1.0f, 1.0f, 0.95f, UiSprites.Tone.INFO, "Рубит лес"),
            new Machine("Турель обороны", 0.66f, 0.9f, 0.5f, UiSprites.Tone.OK, "Сектор 90°"));

    private void initLists() {
        List<String> names = MACHINES.stream().map(Machine::name).toList();
        addRenderableWidget(new UiList<>(left + 8, top + 40, 150, 132, 26, () -> names, (graphics, name, x, y, w, h, selected, hovered) -> {
            Machine m = MACHINES.get(names.indexOf(name));
            UiPanels.card(graphics, x, y, w, h - 2, selected);
            graphics.renderItem(new ItemStack(Items.IRON_GOLEM_SPAWN_EGG), x + 3, y + 4);
            hints.line(graphics, t(m.name), x + 22, y + 3, w - 26, selected ? UiColors.AMBER_LIGHT : UiColors.TEXT);
            UiSprites.draw(graphics, m.tone.dot, x + 22, y + 14, 5, 5);
            hints.line(graphics, t(m.state), x + 30, y + 13, w - 70, m.tone.text);
            UiBars.integrity(graphics, x + w - 38, y + 11, 34, m.structural, m.critical);
        }, () -> chosen, v -> chosen = v));
        map = new UiMap(left + 164, top + 40, 268, 132, new FakeSource(), () -> layers, () -> List.of(
                UiMap.Mark.dot(8, 8, UiColors.AMBER, t("Пульт")),
                UiMap.Mark.dot(70, -40, 0xFFFF6B5D, t("Мех «Бастион» (группа 1)")),
                UiMap.Mark.dot(-60, 30, 0xFF5FB8FF, t("Убежище «Север»")),
                UiMap.Mark.dot(120, 90, UiColors.ERROR, t("Тревога: нарушитель"))));
        map.centreOn(16, 0);
        addRenderableWidget(map);
        addRenderableWidget(new UiButton(left + 164, top + 176, 80, 11, t("Территория"), null, 0, UiButton.Look.CHIP,
                () -> layers ^= 1).selected(() -> (layers & 1) != 0));
        addRenderableWidget(new UiButton(left + 248, top + 176, 80, 11, t("Заражение"), null, 0, UiButton.Look.CHIP,
                () -> layers ^= 2).selected(() -> (layers & 2) != 0));
    }

    /** A made-up map: a territory around the centre and a contaminated spot to the east. */
    private static final class FakeSource implements UiMap.Source {
        @Override
        public int side() {
            return 48;
        }

        @Override
        public int minChunkX() {
            return -24;
        }

        @Override
        public int minChunkZ() {
            return -24;
        }

        @Override
        public int color(int layers, int x, int z) {
            int color = 0;
            if ((layers & 2) != 0) {
                double d = Math.hypot(x - 9, z + 4);
                if (d < 7) {
                    int strength = (int) (40 + (7 - d) * 22);
                    color = 0xFF000000 | (strength * 9 / 10) << 16 | strength << 8 | 0x10;
                }
            }
            if ((layers & 1) != 0 && Math.abs(x) <= 5 && Math.abs(z) <= 4) {
                color = color == 0 ? 0xFF3A3018 : color;
                if (Math.abs(x) == 5 || Math.abs(z) == 4) {
                    color = 0xFFB07E2E;
                }
            }
            return color;
        }

        @Override
        public Component describe(int layers, int x, int z) {
            return Math.abs(x) <= 5 && Math.abs(z) <= 4 ? t("наша территория") : t("ничья земля");
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiPanels.window(graphics, left, top, W, H);
        UiPanels.title(graphics, left, top, W, title, UiSprites.ICON_STATION, left + W - 42);
        graphics.fill(left + 7, top + 36, left + W - 7, top + 37, 0xFF16181A);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hints.clear();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (page == 0) {
            renderElements(graphics, mouseX, mouseY);
        } else {
            renderLists(graphics, mouseX, mouseY);
        }
        Component hint = hints.at(mouseX, mouseY);
        if (hint != null) {
            graphics.renderTooltip(font, hint, mouseX, mouseY);
        }
    }

    private void renderElements(GuiGraphics graphics, int mouseX, int mouseY) {
        UiPanels.label(graphics, t("Кнопки и переключатели"), left + 12, top + 42, 140);
        UiPanels.label(graphics, t("Выпадающий список"), left + 12, top + 150, 140);
        // slots
        int sx = left + 162;
        UiPanels.inner(graphics, sx - 4, top + 40, 136, 58);
        UiPanels.label(graphics, t("Ячейки"), sx, top + 44, 120);
        int sy = top + 58;
        UiSlots.frame(graphics, UiSlots.Kind.INPUT, sx + 1, sy + 1);
        graphics.renderItem(new ItemStack(Items.IRON_INGOT, 14), sx + 1, sy + 1);
        graphics.renderItemDecorations(font, new ItemStack(Items.IRON_INGOT, 14), sx + 1, sy + 1);
        UiSlots.frame(graphics, UiSlots.Kind.MISSING, sx + 21, sy + 1);
        UiSlots.ghost(graphics, new ItemStack(Items.COPPER_INGOT), sx + 21, sy + 1);
        UiSlots.count(graphics, t("0/2"), sx + 21, sy + 1, UiColors.ERROR);
        UiSlots.frame(graphics, UiSlots.Kind.UPGRADE, sx + 41, sy + 1);
        UiSlots.ghostLabel(graphics, t("УЛ"), sx + 41, sy + 1);
        UiSlots.frame(graphics, UiSlots.Kind.FUEL, sx + 61, sy + 1);
        UiSlots.ghost(graphics, new ItemStack(Items.COAL), sx + 61, sy + 1);
        UiSlots.frame(graphics, UiSlots.Kind.DISABLED, sx + 81, sy + 1);
        UiSlots.frame(graphics, UiSlots.Kind.OUTPUT, sx + 109, sy + 5);
        graphics.renderItem(new ItemStack(Items.IRON_BLOCK, 3), sx + 109, sy + 5);
        graphics.renderItemDecorations(font, new ItemStack(Items.IRON_BLOCK, 3), sx + 109, sy + 5);
        UiText.line(graphics, t("вход · нет · улучш. · топливо · закрыта · выход"), sx, sy + 26, 128, UiColors.TEXT_DIM);

        // bars
        UiPanels.inner(graphics, sx - 4, top + 102, 136, 104);
        UiPanels.label(graphics, t("Полосы"), sx, top + 106, 120);
        float wave = (float) (0.5 + 0.45 * Math.sin(System.currentTimeMillis() / 1500.0));
        UiBars.vertical(graphics, sx, top + 118, 12, 52, charge.follow(wave), UiColors.ENERGY, UiColors.ENERGY_DARK);
        int mx = sx + 18;
        UiBars.meter(graphics, UiSprites.ICON_INFO, mx, top + 118, 106, 0.74f, UiColors.ENERGY, t("74 %"));
        UiBars.meter(graphics, UiSprites.ICON_WARNING_AMBER, mx, top + 131, 106, 0.38f, UiColors.HEAT, t("38 °"));
        UiBars.meter(graphics, UiSprites.ICON_CROSS, mx, top + 144, 106, 0.75f, UiColors.WEAR, t("75 %"));
        UiBars.integrity(graphics, mx, top + 158, 106, 0.82f, 0.35f);
        UiBars.progress(graphics, sx, top + 176, 124, 6, wave, UiColors.AMBER);
        UiText.line(graphics, t("заряд · нагрев · износ · целостность"), sx, top + 188, 128, UiColors.TEXT_DIM);

        // plates
        int px = left + 300;
        UiPanels.label(graphics, t("Плашки и состояния"), px, top + 42, 128);
        int py = top + 54;
        if (UiPanels.plate(graphics, UiSprites.Tone.OK, t("Конструкция собрана"), px, py, 128)) {
            hints.add(px, py, 128, 14, t("Конструкция собрана"));
        }
        line(graphics, UiSprites.Tone.WARNING, "Выход заполнен → заберите готовое", px, py + 18);
        line(graphics, UiSprites.Tone.ERROR, "Нет энергии → подключите генератор кабелем", px, py + 36);
        line(graphics, UiSprites.Tone.INFO, "Воронки: материалы — во вход", px, py + 54);
        Component alarm = t("Тревога: нарушитель у поста");
        if (UiPanels.alarm(graphics, alarm, px, py + 72, 128)) {
            hints.add(px, py + 72, 128, 14, alarm);
        }
        UiPanels.badge(graphics, UiSprites.Tone.OK, t("Работает"), px, py + 96, 62);
        UiPanels.badge(graphics, UiSprites.Tone.WARNING, t("Ждёт энергию"), px + 66, py + 96, 62);
        UiPanels.badge(graphics, UiSprites.Tone.ERROR, t("Отключена"), px, py + 114, 62);
        UiPanels.badge(graphics, UiSprites.Tone.INFO, t("Простой"), px + 66, py + 114, 62);
        UiText.wrapped(graphics, t("Наведите на полосы — подсказка в стиле мода."), px, top + 196, 128, 18, UiColors.TEXT_DIM);

        if (dropdown != null && dropdown.isOpen()) {
            return;
        }
        if (mouseX >= mx && mouseX < mx + 106 && mouseY >= top + 118 && mouseY < top + 170) {
            UiTooltip.of(t("Боевой андроид «Скаут»"))
                    .row(UiSprites.ICON_INFO, t("Заряд"), t("74 %"), UiColors.ENERGY_TEXT)
                    .row(UiSprites.ICON_WARNING_AMBER, t("Нагрев"), t("38 °"), UiColors.HEAT)
                    .row(UiSprites.ICON_CROSS, t("Износ"), t("75 %"), UiColors.WEAR)
                    .row(t("Структурная целостность"), t("82 %"), UiColors.OK)
                    .row(t("Критическая целостность"), t("35 %"), UiColors.WARNING)
                    .text(t("При износе 75 % удары слабее. Почините на станции обслуживания."), UiColors.TEXT_SECONDARY)
                    .render(graphics, mouseX, mouseY);
        }
    }

    private void line(GuiGraphics graphics, UiSprites.Tone tone, String text, int x, int y) {
        if (UiPanels.plate(graphics, tone, t(text), x, y, 128)) {
            hints.add(x, y, 128, 14, t(text));
        }
    }

    private void renderLists(GuiGraphics graphics, int mouseX, int mouseY) {
        UiPanels.screen(graphics, left + 8, top + 192, 424, 48);
        UiTable table = new UiTable()
                .column(t("Роль"), 100).column(t("Строить"), 80).column(t("Ломать"), 80).column(t("Машины"), 80).column(t("Войны"), 80);
        table.row(UiTable.Cell.text(t("Лидер")), UiTable.Cell.yes(t("да")), UiTable.Cell.yes(t("да")), UiTable.Cell.yes(t("да")), UiTable.Cell.yes(t("да")));
        table.row(UiTable.Cell.text(t("Офицер")), UiTable.Cell.yes(t("да")), UiTable.Cell.yes(t("да")), UiTable.Cell.yes(t("да")), UiTable.Cell.no(t("нет")));
        table.row(UiTable.Cell.text(t("Новобранец")), UiTable.Cell.yes(t("да")), UiTable.Cell.no(t("нет")), UiTable.Cell.no(t("нет")), UiTable.Cell.no(t("нет")));
        table.render(graphics, left + 12, top + 194, 11, hints);
        UiText.line(graphics, t("ЛКМ — тянуть · колесо — масштаб"), left + 340, top + 178, 92, UiColors.TEXT_DIM);
        if (map != null) {
            Component hovered = map.hovered(mouseX, mouseY);
            if (hovered != null) {
                UiTooltip.of(hovered).render(graphics, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dropdown != null && page == 0) {
            if (dropdown.isOpen() && dropdown.isMouseOver(mouseX, mouseY)) {
                return dropdown.mouseClicked(mouseX, mouseY, button);
            }
            dropdown.closeIfOutside(mouseX, mouseY);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        if (map != null) {
            map.close();
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
