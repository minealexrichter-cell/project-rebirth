package com.projectrebirth.upgrades;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

/** The named upgrade slots of a power armor: one for each part of the hull, the same for all variants. */
public enum ArmorSlot implements StringRepresentable {
    HEAD("head"),
    CHEST_FRONT("chest_front"),
    CHEST_BACK("chest_back"),
    RIGHT_ARM("right_arm"),
    LEFT_ARM("left_arm"),
    RIGHT_LEG("right_leg"),
    LEFT_LEG("left_leg");

    public static final Codec<ArmorSlot> CODEC = StringRepresentable.fromEnum(ArmorSlot::values);
    public static final StreamCodec<ByteBuf, ArmorSlot> STREAM_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(ArmorSlot::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), ArmorSlot::ordinal);

    private final String id;

    ArmorSlot(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
