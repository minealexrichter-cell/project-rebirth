package com.projectrebirth.upgrades;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The rules of installing and removing upgrades, one set of code for bodies and armors. Every change moves the item:
 * it leaves the source before it appears in the host, and leaves the host before it is handed out, so there is never
 * a second copy. Callers check who may do this; here is only what fits where.
 */
public final class Upgrades {
    private static final String TAG = "Upgrades";

    /** Why an upgrade does not fit. The key names the message message.projectrebirth.upgrade.&lt;key&gt;. */
    public enum Problem {
        NOT_AN_UPGRADE("not_an_upgrade"),
        WRONG_HOST("wrong_host"),
        NO_SLOT_CHOSEN("no_slot_chosen"),
        WRONG_SLOT("wrong_slot"),
        SLOT_TAKEN("slot_taken"),
        NO_FREE_SLOTS("no_free_slots"),
        TOO_MANY("too_many"),
        OTHER_MATERIAL("other_material"),
        INCOMPATIBLE("incompatible");

        private final String key;

        Problem(String key) {
            this.key = key;
        }

        public String translationKey() {
            return "message.projectrebirth.upgrade." + key;
        }
    }

    private Upgrades() {
    }

    public static Optional<UpgradeDefinition> definition(RegistryAccess access, ItemStack stack) {
        return access.registryOrThrow(UpgradeRegistry.UPGRADES).stream().filter(definition -> definition.matches(stack)).findFirst();
    }

    /** Why the upgrade cannot go into this host (into the given armor part; null for a body), or null if it can. */
    @Nullable
    public static Problem problem(RegistryAccess access, UpgradeHost host, ItemStack stack, @Nullable ArmorSlot slot) {
        Optional<UpgradeDefinition> definition = definition(access, stack);
        if (definition.isEmpty()) {
            return Problem.NOT_AN_UPGRADE;
        }
        return problem(installed -> definition(access, installed), definition.get(), stack, host.upgrades(), host.layout(), slot);
    }

    /** The rules themselves; {@code lookup} finds the definitions of what is already installed. */
    @Nullable
    public static Problem problem(Function<ItemStack, Optional<UpgradeDefinition>> lookup, UpgradeDefinition definition, ItemStack stack,
                                  InstalledUpgrades installed, UpgradeLayout layout, @Nullable ArmorSlot slot) {
        Problem specific = switch (layout) {
            case UpgradeLayout.Counted counted -> bodyProblem(lookup, definition, stack, installed, counted, slot);
            case UpgradeLayout.ArmorParts parts -> armorProblem(definition, installed, slot);
        };
        if (specific != null) {
            return specific;
        }
        for (InstalledUpgrades.Installed entry : installed.entries()) {
            Optional<UpgradeDefinition> other = lookup.apply(entry.stack());
            if (other.isPresent() && (definition.incompatibleWith().contains(other.get().kind())
                    || other.get().incompatibleWith().contains(definition.kind()))) {
                return Problem.INCOMPATIBLE;
            }
        }
        return null;
    }

    @Nullable
    private static Problem bodyProblem(Function<ItemStack, Optional<UpgradeDefinition>> lookup, UpgradeDefinition definition, ItemStack stack,
                                       InstalledUpgrades installed, UpgradeLayout.Counted layout, @Nullable ArmorSlot slot) {
        if (definition.body().isEmpty() || slot != null) {
            return Problem.WRONG_HOST;
        }
        UpgradeDefinition.BodyFit fit = definition.body().get();
        int same = 0;
        int used = 0;
        for (InstalledUpgrades.Installed entry : installed.entries()) {
            Optional<UpgradeDefinition> other = lookup.apply(entry.stack());
            // An upgrade whose definition a data pack has removed still takes its place until it is taken out.
            used += other.flatMap(UpgradeDefinition::body).map(UpgradeDefinition.BodyFit::slots).orElse(1);
            if (ItemStack.isSameItem(entry.stack(), stack)) {
                same++;
            } else if (fit.oneMaterialOnly() && other.filter(o -> o.kind().equals(definition.kind())).isPresent()) {
                return Problem.OTHER_MATERIAL;
            }
        }
        if (same >= fit.maxCount()) {
            return Problem.TOO_MANY;
        }
        return used + fit.slots() > layout.slots() ? Problem.NO_FREE_SLOTS : null;
    }

    @Nullable
    private static Problem armorProblem(UpgradeDefinition definition, InstalledUpgrades installed, @Nullable ArmorSlot slot) {
        if (definition.armorSlots().isEmpty()) {
            return Problem.WRONG_HOST;
        }
        if (slot == null) {
            return Problem.NO_SLOT_CHOSEN;
        }
        if (!definition.armorSlots().contains(slot)) {
            return Problem.WRONG_SLOT;
        }
        return installed.indexOf(slot) >= 0 ? Problem.SLOT_TAKEN : null;
    }

    /** Moves one piece from {@code source} into the host, if it fits. Returns the problem, or null on success. */
    @Nullable
    public static Problem install(RegistryAccess access, UpgradeHost host, ItemStack source, @Nullable ArmorSlot slot) {
        Problem problem = problem(access, host, source, slot);
        if (problem == null) {
            ItemStack piece = source.split(1);
            host.setUpgrades(host.upgrades().with(new InstalledUpgrades.Installed(Optional.ofNullable(slot), piece)));
        }
        return problem;
    }

    /** Takes the piece at this index out of the host; the caller must give it to someone. */
    public static ItemStack remove(UpgradeHost host, int index) {
        InstalledUpgrades upgrades = host.upgrades();
        if (index < 0 || index >= upgrades.entries().size()) {
            return ItemStack.EMPTY;
        }
        ItemStack piece = upgrades.entries().get(index).stack();
        host.setUpgrades(upgrades.without(index));
        return piece.copy();
    }

    /** Takes everything out of the host, for dropping it when the body or armor is destroyed. */
    public static List<ItemStack> takeAll(UpgradeHost host) {
        List<ItemStack> pieces = new ArrayList<>();
        for (InstalledUpgrades.Installed entry : host.upgrades().entries()) {
            pieces.add(entry.stack().copy());
        }
        host.setUpgrades(InstalledUpgrades.EMPTY);
        return pieces;
    }

    public static InstalledUpgrades ofStack(ItemStack stack) {
        return stack.getOrDefault(UpgradeRegistry.INSTALLED.get(), InstalledUpgrades.EMPTY);
    }

    public static void putOnStack(ItemStack stack, InstalledUpgrades upgrades) {
        if (upgrades.isEmpty()) {
            stack.remove(UpgradeRegistry.INSTALLED.get());
        } else {
            stack.set(UpgradeRegistry.INSTALLED.get(), upgrades);
        }
    }

    /** The lines of a packed body or armor that show what is installed. */
    public static void addTooltip(ItemStack stack, List<Component> tooltip) {
        for (InstalledUpgrades.Installed entry : ofStack(stack).entries()) {
            tooltip.add(Component.translatable("tooltip.projectrebirth.upgrade.installed", entry.stack().getHoverName())
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    public static void save(CompoundTag tag, HolderLookup.Provider registries, InstalledUpgrades upgrades) {
        if (!upgrades.isEmpty()) {
            InstalledUpgrades.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), upgrades)
                    .resultOrPartial(ProjectRebirth.LOGGER::error)
                    .ifPresent(saved -> tag.put(TAG, saved));
        }
    }

    public static InstalledUpgrades load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(TAG)) {
            return InstalledUpgrades.EMPTY;
        }
        return InstalledUpgrades.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get(TAG))
                .resultOrPartial(ProjectRebirth.LOGGER::error)
                .orElse(InstalledUpgrades.EMPTY);
    }
}
