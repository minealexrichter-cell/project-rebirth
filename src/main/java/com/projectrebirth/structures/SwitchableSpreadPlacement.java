package com.projectrebirth.structures;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * The placement of the structures of 1.6.0: the vanilla random spread (spacing, separation, salt in the data), plus a switch
 * from the config. A switched-off structure is placed nowhere and {@code /locate} finds none; the data pack can still change
 * or remove the structure set as usual. Only chunks generated from now on are affected: old regions keep what they have.
 */
public class SwitchableSpreadPlacement extends RandomSpreadStructurePlacement {
    /** The spread fields and the switch, read flat beside the common placement fields. */
    private record Spread(int spacing, int separation, RandomSpreadType spreadType, String switchKey) {
        static final MapCodec<Spread> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(0, 4096).fieldOf("spacing").forGetter(Spread::spacing),
                Codec.intRange(0, 4096).fieldOf("separation").forGetter(Spread::separation),
                RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(Spread::spreadType),
                Codec.STRING.fieldOf("switch").forGetter(Spread::switchKey)).apply(instance, Spread::new));
    }

    public static final MapCodec<SwitchableSpreadPlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> placementCodec(instance)
            .and(Spread.CODEC.forGetter(p -> new Spread(p.spacing(), p.separation(), p.spreadType(), p.switchKey)))
            .apply(instance, (offset, reduction, frequency, salt, exclusion, spread) -> new SwitchableSpreadPlacement(offset, reduction, frequency,
                    salt, exclusion, spread.spacing(), spread.separation(), spread.spreadType(), spread.switchKey())));

    private final String switchKey;

    public SwitchableSpreadPlacement(Vec3i locateOffset, FrequencyReductionMethod reduction, float frequency, int salt,
                                     Optional<ExclusionZone> exclusionZone, int spacing, int separation, RandomSpreadType spreadType, String switchKey) {
        super(locateOffset, reduction, frequency, salt, exclusionZone, spacing, separation, spreadType);
        this.switchKey = switchKey;
    }

    /** The name of this structure's switch in the config ({@link StructureSwitches}). */
    public String switchKey() {
        return switchKey;
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int x, int z) {
        return StructureSwitches.enabled(switchKey) && StructureSwitches.outsideSafeArea(x, z) && super.isPlacementChunk(state, x, z);
    }

    @Override
    public StructurePlacementType<?> type() {
        return StructureRegistry.SWITCHABLE_SPREAD.get();
    }
}
