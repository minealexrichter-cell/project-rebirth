package com.projectrebirth.environment;

import java.util.List;
import java.util.Locale;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A piece of a respirator or a protective suit. A head piece shows how much of its filter is left. */
public class ProtectiveArmorItem extends ArmorItem {
    public ProtectiveArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (getType() == Type.HELMET && Protection.breathes(stack) && RebirthConfig.SERVER_SPEC.isLoaded()) {
            float share = Protection.filterLeft(stack) / RebirthConfig.PROTECTION_FILTER_LIFE.get().floatValue();
            tooltip.add(share > 0.0f
                    ? Component.translatable("tooltip.projectrebirth.filter.left", String.format(Locale.ROOT, "%.0f", share * 100.0f)).withStyle(ChatFormatting.AQUA)
                    : Component.translatable("tooltip.projectrebirth.filter.none").withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.translatable("tooltip.projectrebirth.protection." + Protection.KITS.stream()
                .filter(kit -> stack.is(kit.items())).map(Protection.Kit::id).findFirst().orElse("none")).withStyle(ChatFormatting.GRAY));
    }
}
