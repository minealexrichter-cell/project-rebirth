package com.projectrebirth.garrison;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The stand-in machine of the automated tests (1.7.0, block C): a box with no mind of its own, on which the machines' base is
 * tested before the real machines come (androids in block E, turrets in G, the mech in H). Never placed in a played world: its
 * item has no recipe and is not in the creative tab.
 */
public class TestMachineEntity extends MachineEntity {
    public TestMachineEntity(EntityType<? extends TestMachineEntity> type, Level level) {
        super(type, level, MachineKind.TEST);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected Item packedItem() {
        return GarrisonRegistry.TEST_MACHINE_ITEM.get();
    }
}
