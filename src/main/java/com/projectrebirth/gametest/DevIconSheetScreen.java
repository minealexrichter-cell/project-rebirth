package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.core.ModCreativeTabs;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Development only (1.8.0 block G): every item and block icon of the mod on one sheet, by creative tab, twice the size, on the
 * dark and on a light ground, to compare them (angle, light, outline, palette). {@code /rbdev ui icons [page]}.
 */
public class DevIconSheetScreen extends Screen {
    private static int page;

    public DevIconSheetScreen() {
        super(Component.literal("Icons"));
    }

    static void page(int value) {
        page = value;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF20242A);
        if (page == 2) {
            tooltips(graphics);
            return;
        }
        int cell = 36;
        int columns = Math.max(1, (width - 12) / cell);
        int x0 = 6;
        int y = 6;
        ModCreativeTabs.Kind[] kinds = ModCreativeTabs.Kind.values();
        int first = page == 0 ? 0 : 4;
        int last = page == 0 ? 4 : kinds.length;
        for (int k = first; k < last; k++) {
            List<ItemStack> items = ModCreativeTabs.contents(kinds[k]);
            UiPanels.label(graphics, Component.literal(kinds[k].id() + " · " + items.size()), x0, y, 200);
            y += 11;
            for (int i = 0; i < items.size(); i++) {
                int x = x0 + (i % columns) * cell;
                int cy = y + (i / columns) * cell;
                boolean light = (i / columns + i % columns) % 2 == 1;
                graphics.fill(x, cy, x + cell - 2, cy + cell - 2, light ? 0xFF8A9096 : UiColors.SCREEN);
                graphics.pose().pushPose();
                graphics.pose().translate(x + 1, cy + 1, 0);
                graphics.pose().scale(2.0f, 2.0f, 1.0f);
                graphics.renderItem(items.get(i), 0, 0);
                graphics.pose().popPose();
            }
            y += ((items.size() + columns - 1) / columns) * cell + 4;
        }
    }

    /** Page 2: the tooltips of a few items of different sections, as the game draws them (with Shift pressed or not). */
    private void tooltips(GuiGraphics graphics) {
        String[] ids = {"assault_rifle", "improved_robot_body", "hazmat_suit", "electronics_fabricator", "ai_core", "titanium_ingot"};
        int x = 8;
        int y = 8;
        for (String id : ids) {
            ItemStack stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(com.projectrebirth.ProjectRebirth.id(id)));
            graphics.renderItem(stack, x, y);
            graphics.renderTooltip(font, stack, x + 8, y + 22);
            x += width / 3;
            if (x > width - 100) {
                x = 8;
                y += height / 2;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
