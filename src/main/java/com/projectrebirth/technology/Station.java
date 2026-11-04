package com.projectrebirth.technology;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Production stations. They all share one block class, block entity, menu and screen; a recipe names the station
 * that makes it, so a new station only needs a new entry here, a block and its recipes.
 */
public enum Station implements StringRepresentable {
    /** Circuit boards, processors, energy cells. */
    ELECTRONICS_FABRICATOR("electronics_fabricator"),
    /** Actuators, synthetic fiber, artificial bodies, transfer capsule. */
    ASSEMBLY_STATION("assembly_station"),
    /** Armor plates and power armor. */
    ARMOR_WORKSTATION("armor_workstation"),
    /** Gadgets for artificial bodies. */
    IMPLANT_FABRICATOR("implant_fabricator");

    public static final Codec<Station> CODEC = StringRepresentable.fromEnum(Station::values);
    public static final StreamCodec<ByteBuf, Station> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Station::ordinal);

    private final String id;

    Station(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
