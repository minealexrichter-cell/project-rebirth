package com.projectrebirth.monitoring;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.environment.ContaminationMap;
import com.projectrebirth.environment.ContaminationZones;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * A picture of the contamination around a place (1.6.0, block E): radiation and toxins of a square of chunks, with marks of the
 * station, the shelters and the players, on one game day. Shown live by the monitoring station and kept on a printed map.
 *
 * @param centerX   the chunk in the middle
 * @param centerZ   the chunk in the middle
 * @param radius    chunks each way from the middle
 * @param radiation per chunk, row by row from the north-west, in tenths (0 to 25.5)
 * @param toxin     the same for toxins
 * @param day       the game day it was taken on
 * @param markers   what stands on the map
 */
public record Snapshot(int centerX, int centerZ, int radius, byte[] radiation, byte[] toxin, long day, List<Marker> markers) {
    public static final int STATION = 0;
    public static final int SHELTER = 1;
    public static final int PLAYER = 2;
    /** 1.6.0, block F: a radiation or toxin source block, and a ruin of the mod (the sensors mark them). */
    public static final int SOURCE = 3;
    public static final int STRUCTURE = 4;

    /** A mark on the map: its kind, block coordinates, a name for a player. */
    public record Marker(int kind, int x, int z, String name) {
        public static final Codec<Marker> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("kind").forGetter(Marker::kind),
                Codec.INT.fieldOf("x").forGetter(Marker::x),
                Codec.INT.fieldOf("z").forGetter(Marker::z),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Marker::name)).apply(i, Marker::new));
        public static final StreamCodec<ByteBuf, Marker> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Marker::kind,
                ByteBufCodecs.VAR_INT, Marker::x,
                ByteBufCodecs.VAR_INT, Marker::z,
                ByteBufCodecs.STRING_UTF8, Marker::name,
                Marker::new);
    }

    private static final Codec<byte[]> BYTES = Codec.BYTE_BUFFER.xmap(buffer -> {
        byte[] out = new byte[buffer.remaining()];
        buffer.duplicate().get(out);
        return out;
    }, ByteBuffer::wrap);

    public static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("center_x").forGetter(Snapshot::centerX),
            Codec.INT.fieldOf("center_z").forGetter(Snapshot::centerZ),
            Codec.INT.fieldOf("radius").forGetter(Snapshot::radius),
            BYTES.fieldOf("radiation").forGetter(Snapshot::radiation),
            BYTES.fieldOf("toxin").forGetter(Snapshot::toxin),
            Codec.LONG.fieldOf("day").forGetter(Snapshot::day),
            Marker.CODEC.listOf().optionalFieldOf("markers", List.of()).forGetter(Snapshot::markers)).apply(i, Snapshot::new));

    public static final StreamCodec<ByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(
            (buf, snapshot) -> {
                ByteBufCodecs.VAR_INT.encode(buf, snapshot.centerX());
                ByteBufCodecs.VAR_INT.encode(buf, snapshot.centerZ());
                ByteBufCodecs.VAR_INT.encode(buf, snapshot.radius());
                ByteBufCodecs.BYTE_ARRAY.encode(buf, snapshot.radiation());
                ByteBufCodecs.BYTE_ARRAY.encode(buf, snapshot.toxin());
                ByteBufCodecs.VAR_LONG.encode(buf, snapshot.day());
                Marker.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, snapshot.markers());
            },
            buf -> new Snapshot(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BYTE_ARRAY.decode(buf), ByteBufCodecs.BYTE_ARRAY.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                    Marker.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)));

    /** Reads the chunks around a chunk now. */
    public static Snapshot scan(ServerLevel level, int centerX, int centerZ, int radius, List<Marker> markers) {
        int side = 2 * radius + 1;
        byte[] radiation = new byte[side * side];
        byte[] toxin = new byte[side * side];
        if (ContaminationZones.enabled()) {
            ContaminationMap map = ContaminationMap.get(level);
            long now = level.getGameTime();
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    ContaminationMap.Reading reading = map.readingAt(centerX + dx, centerZ + dz, now);
                    int at = (dz + radius) * side + dx + radius;
                    radiation[at] = pack(reading.radiation());
                    toxin[at] = pack(reading.toxin());
                }
            }
        }
        return new Snapshot(centerX, centerZ, radius, radiation, toxin, day(level), List.copyOf(markers));
    }

    public static long day(ServerLevel level) {
        return level.getDayTime() / 24000L + 1L;
    }

    static byte pack(float value) {
        return (byte) Mth.clamp(Math.round(value * 10.0f), 0, 255);
    }

    public int side() {
        return 2 * radius + 1;
    }

    /** Radiation of a chunk on the map (dx, dz from the middle), 0 outside it. */
    public float radiationAt(int dx, int dz) {
        return value(radiation, dx, dz);
    }

    public float toxinAt(int dx, int dz) {
        return value(toxin, dx, dz);
    }

    private float value(byte[] values, int dx, int dz) {
        if (Math.abs(dx) > radius || Math.abs(dz) > radius) {
            return 0.0f;
        }
        int at = (dz + radius) * side() + dx + radius;
        return at < values.length ? (values[at] & 0xFF) / 10.0f : 0.0f;
    }

    /** The strongest contamination within this many chunks of the middle. */
    public float strongestWithin(int chunks) {
        float most = 0.0f;
        int r = Math.min(chunks, radius);
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                most = Math.max(most, Math.max(radiationAt(dx, dz), toxinAt(dx, dz)));
            }
        }
        return most;
    }

    /** The peaks of the whole map: radiation, toxins. */
    public float[] peaks() {
        float rad = 0.0f, tox = 0.0f;
        for (int i = 0; i < radiation.length; i++) {
            rad = Math.max(rad, (radiation[i] & 0xFF) / 10.0f);
            tox = Math.max(tox, (toxin[i] & 0xFF) / 10.0f);
        }
        return new float[] {rad, tox};
    }

    /** Without the players: what a printed map keeps. */
    public Snapshot withoutPlayers() {
        List<Marker> kept = new ArrayList<>();
        for (Marker marker : markers) {
            if (marker.kind() != PLAYER) {
                kept.add(marker);
            }
        }
        return new Snapshot(centerX, centerZ, radius, radiation, toxin, day, List.copyOf(kept));
    }
}
