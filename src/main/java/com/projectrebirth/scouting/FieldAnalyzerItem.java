package com.projectrebirth.scouting;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** The field analyzer (1.6.0, block F): held in either hand, it shows the contamination around and clicks near sources. */
public class FieldAnalyzerItem extends Item {
    public FieldAnalyzerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.projectrebirth.field_analyzer").withStyle(ChatFormatting.GRAY));
    }
}
