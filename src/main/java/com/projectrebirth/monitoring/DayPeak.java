package com.projectrebirth.monitoring;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The strongest radiation and toxins a monitoring station saw around it on one game day (1.6.0, block E). */
public record DayPeak(long day, float radiation, float toxin) {
    public static final StreamCodec<ByteBuf, DayPeak> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DayPeak::day,
            ByteBufCodecs.FLOAT, DayPeak::radiation,
            ByteBufCodecs.FLOAT, DayPeak::toxin,
            DayPeak::new);

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Day", day);
        tag.putFloat("Radiation", radiation);
        tag.putFloat("Toxin", toxin);
        return tag;
    }

    public static DayPeak load(CompoundTag tag) {
        return new DayPeak(tag.getLong("Day"), tag.getFloat("Radiation"), tag.getFloat("Toxin"));
    }
}
