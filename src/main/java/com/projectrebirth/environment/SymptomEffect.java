package com.projectrebirth.environment;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/** An icon that shows a symptom. Milk does not cure it: only what lowers the dose or the toxic load does (see {@link HumanBody}). */
public class SymptomEffect extends MobEffect {
    public SymptomEffect(int color) {
        super(MobEffectCategory.HARMFUL, color);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
    }
}
