package com.projectrebirth.upgrades;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What an upgrade is and where it fits, read from data/&lt;namespace&gt;/projectrebirth/upgrade/*.json, so that new
 * upgrades and other rules need no code here. How strong an upgrade is belongs to the server config.
 *
 * @param items            the items that are this upgrade; a tag is allowed (all vanilla banners are one upgrade)
 * @param kind             plating, jetpack, ...; {@code incompatibleWith} and {@link BodyFit#oneMaterialOnly} refer to it
 * @param material         copper, iron, titanium, ... if the kind has materials
 * @param body             how it goes on an artificial body; absent = not on bodies
 * @param armorSlots       the armor parts it goes on; empty = not on power armor
 * @param incompatibleWith kinds that cannot be on the same body or armor; it is enough if one of the two names the other
 * @param protectsFrom     what the upgrade keeps out of a body: drowning, acid, dust (the dust storm comes in 1.5.0)
 */
public record UpgradeDefinition(HolderSet<Item> items, String kind, Optional<String> material, Optional<BodyFit> body,
                                List<ArmorSlot> armorSlots, List<String> incompatibleWith, List<String> protectsFrom) {
    public static final Codec<UpgradeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("items").forGetter(UpgradeDefinition::items),
            Codec.STRING.fieldOf("kind").forGetter(UpgradeDefinition::kind),
            Codec.STRING.optionalFieldOf("material").forGetter(UpgradeDefinition::material),
            BodyFit.CODEC.optionalFieldOf("body").forGetter(UpgradeDefinition::body),
            ArmorSlot.CODEC.listOf().optionalFieldOf("armor_slots", List.of()).forGetter(UpgradeDefinition::armorSlots),
            Codec.STRING.listOf().optionalFieldOf("incompatible_with", List.of()).forGetter(UpgradeDefinition::incompatibleWith),
            Codec.STRING.listOf().optionalFieldOf("protects_from", List.of()).forGetter(UpgradeDefinition::protectsFrom)
    ).apply(instance, UpgradeDefinition::new));

    public boolean matches(ItemStack stack) {
        return stack.is(items);
    }

    /**
     * @param slots           how many body slots one piece takes
     * @param maxCount        how many pieces fit on one body
     * @param oneMaterialOnly pieces of this kind but another material cannot be added: the plates of a body are all alike
     */
    public record BodyFit(int slots, int maxCount, boolean oneMaterialOnly) {
        public static final Codec<BodyFit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("slots", 1).forGetter(BodyFit::slots),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_count", 1).forGetter(BodyFit::maxCount),
                Codec.BOOL.optionalFieldOf("one_material_only", false).forGetter(BodyFit::oneMaterialOnly)
        ).apply(instance, BodyFit::new));
    }
}
