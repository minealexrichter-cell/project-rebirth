package com.projectrebirth.upgrades;

import javax.annotation.Nullable;

import com.projectrebirth.bodies.ArtificialBodyItem;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorItem;

import net.minecraft.world.item.ItemStack;

/** Anything that holds upgrades: a body or armor standing in the world, or one packed into an item. */
public interface UpgradeHost {
    InstalledUpgrades upgrades();

    void setUpgrades(InstalledUpgrades upgrades);

    UpgradeLayout layout();

    /** The upgrades of a packed body or armor, or null for any other item. Changes go straight into the stack. */
    @Nullable
    static UpgradeHost ofPacked(ItemStack stack) {
        UpgradeLayout layout;
        if (stack.getItem() instanceof ArtificialBodyItem body) {
            layout = new UpgradeLayout.Counted(RebirthConfig.bodyStats(body.bodyClass()).upgradeSlots().get());
        } else if (stack.getItem() instanceof PowerArmorItem) {
            layout = UpgradeLayout.ArmorParts.INSTANCE;
        } else {
            return null;
        }
        return new UpgradeHost() {
            @Override
            public InstalledUpgrades upgrades() {
                return Upgrades.ofStack(stack);
            }

            @Override
            public void setUpgrades(InstalledUpgrades upgrades) {
                Upgrades.putOnStack(stack, upgrades);
            }

            @Override
            public UpgradeLayout layout() {
                return layout;
            }
        };
    }
}
