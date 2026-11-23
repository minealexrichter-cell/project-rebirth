package com.projectrebirth.wanderers;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

/**
 * 1.9.0, block E: what wanderers are, read from data so a data pack can change it.
 * <ul>
 *   <li>{@code projectrebirth/wanderer_groups/*.json}: the make-up of a group ({@link GroupSpec});</li>
 *   <li>{@code projectrebirth/wanderer_trades/*.json}: one offer a group may have ({@link Trade}).</li>
 * </ul>
 */
public final class WandererData {
    public static final ResourceKey<Registry<GroupSpec>> GROUPS = ResourceKey.createRegistryKey(ProjectRebirth.id("wanderer_groups"));
    public static final ResourceKey<Registry<Trade>> TRADES = ResourceKey.createRegistryKey(ProjectRebirth.id("wanderer_trades"));

    private WandererData() {
    }

    /** How many: {"min": .., "max": ..}. */
    public record Count(int min, int max) {
        public static final Count ONE = new Count(1, 1);
        public static final Codec<Count> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("min", 1).forGetter(Count::min),
                Codec.INT.optionalFieldOf("max", 1).forGetter(Count::max)
        ).apply(instance, Count::new));
    }

    /**
     * One kind of member of a group.
     *
     * @param kind     armored, android, survivor or robot
     * @param count    how many of it
     * @param leader   the head of the group (the one who trades); the first such member
     * @param variants armored: armor variants (combat, mobile, worker); android: body classes (basic_robot_body...); survivor: skins
     * @param weapons  item ids, one is given (armored and robot carry their own gun and may leave it empty)
     * @param offhand  item ids for the other hand (a shield), one or none
     * @param kits     survivor: protective kits (respirator, hazmat, heavy_suit), one is worn
     */
    public record MemberSpec(String kind, Count count, boolean leader, List<String> variants, List<String> weapons, List<String> offhand,
                             List<String> kits) {
        public static final Codec<MemberSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("kind").forGetter(MemberSpec::kind),
                Count.CODEC.optionalFieldOf("count", Count.ONE).forGetter(MemberSpec::count),
                Codec.BOOL.optionalFieldOf("leader", false).forGetter(MemberSpec::leader),
                Codec.STRING.listOf().optionalFieldOf("variants", List.of()).forGetter(MemberSpec::variants),
                Codec.STRING.listOf().optionalFieldOf("weapons", List.of()).forGetter(MemberSpec::weapons),
                Codec.STRING.listOf().optionalFieldOf("offhand", List.of()).forGetter(MemberSpec::offhand),
                Codec.STRING.listOf().optionalFieldOf("kits", List.of()).forGetter(MemberSpec::kits)
        ).apply(instance, MemberSpec::new));
    }

    /** The make-up of a group: its members, how often it comes, from which stage of the Wasteland. */
    public record GroupSpec(List<MemberSpec> members, int weight, int minStage) {
        public static final Codec<GroupSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MemberSpec.CODEC.listOf().fieldOf("members").forGetter(GroupSpec::members),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(GroupSpec::weight),
                Codec.INT.optionalFieldOf("min_stage", 1).forGetter(GroupSpec::minStage)
        ).apply(instance, GroupSpec::new));
    }

    /** One offer: what the player gives, what he gets, how often a group has it, from which stage. */
    public record Trade(List<ItemStack> cost, ItemStack result, int weight, int minStage) {
        public static final Codec<Trade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.listOf().fieldOf("cost").forGetter(Trade::cost),
                ItemStack.CODEC.fieldOf("result").forGetter(Trade::result),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Trade::weight),
                Codec.INT.optionalFieldOf("min_stage", 1).forGetter(Trade::minStage)
        ).apply(instance, Trade::new));
    }
}
