package com.projectrebirth.upgrades;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * The upgrades in one body or armor. The items themselves are kept, so that what comes out is exactly what went in
 * (a banner keeps its pattern). Never changed in place: the same value can be in an entity and in an item component.
 */
public final class InstalledUpgrades {
    public static final InstalledUpgrades EMPTY = new InstalledUpgrades(List.of());

    public static final Codec<InstalledUpgrades> CODEC = Installed.CODEC.listOf().xmap(InstalledUpgrades::new, InstalledUpgrades::entries);
    public static final StreamCodec<RegistryFriendlyByteBuf, InstalledUpgrades> STREAM_CODEC =
            Installed.STREAM_CODEC.apply(ByteBufCodecs.list()).map(InstalledUpgrades::new, InstalledUpgrades::entries);

    private final List<Installed> entries;

    private InstalledUpgrades(List<Installed> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<Installed> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public InstalledUpgrades with(Installed entry) {
        List<Installed> list = new ArrayList<>(entries);
        list.add(entry);
        return new InstalledUpgrades(list);
    }

    public InstalledUpgrades without(int index) {
        List<Installed> list = new ArrayList<>(entries);
        list.remove(index);
        return new InstalledUpgrades(list);
    }

    /** Index of the upgrade in an armor slot, or -1. */
    public int indexOf(ArmorSlot slot) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).slot().filter(slot::equals).isPresent()) {
                return i;
            }
        }
        return -1;
    }

    /** Item components are compared by value, so this must not compare the stacks by identity. */
    @Override
    public boolean equals(Object other) {
        return other instanceof InstalledUpgrades upgrades && entries.equals(upgrades.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "InstalledUpgrades" + entries;
    }

    /** One installed piece. {@code slot} is the armor part; a body has plain slots, so there it is empty. */
    public record Installed(Optional<ArmorSlot> slot, ItemStack stack) {
        static final Codec<Installed> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ArmorSlot.CODEC.optionalFieldOf("slot").forGetter(Installed::slot),
                ItemStack.CODEC.fieldOf("item").forGetter(Installed::stack)
        ).apply(instance, Installed::new));
        static final StreamCodec<RegistryFriendlyByteBuf, Installed> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(ArmorSlot.STREAM_CODEC), Installed::slot,
                ItemStack.STREAM_CODEC, Installed::stack,
                Installed::new);

        @Override
        public boolean equals(Object other) {
            return other instanceof Installed installed && slot.equals(installed.slot) && ItemStack.matches(stack, installed.stack);
        }

        @Override
        public int hashCode() {
            return 31 * slot.hashCode() + ItemStack.hashItemAndComponents(stack);
        }
    }
}
