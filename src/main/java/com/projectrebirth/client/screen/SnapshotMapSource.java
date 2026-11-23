package com.projectrebirth.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.projectrebirth.client.ContaminationMapView;
import com.projectrebirth.client.ui.UiMap;
import com.projectrebirth.monitoring.Snapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * A contamination snapshot as the data of the shared map widget (1.8.0 block D): radiation and toxins as two layers
 * (bits 1 and 2), the station, shelters, sources, ruins and players as marks (bit 4). Used by the monitoring station and
 * the printed map. The snapshot may be swapped for a newer one; {@link #set} tells whether the map must be repainted.
 */
public final class SnapshotMapSource implements UiMap.Source {
    public static final int RADIATION = 1;
    public static final int TOXIN = 2;
    public static final int MARKS = 4;

    @Nullable
    private Snapshot snapshot;

    /** Returns true if the snapshot is a new one. */
    public boolean set(@Nullable Snapshot next) {
        boolean changed = next != snapshot;
        snapshot = next;
        return changed;
    }

    @Nullable
    public Snapshot snapshot() {
        return snapshot;
    }

    @Override
    public int side() {
        return snapshot == null ? 1 : snapshot.side();
    }

    @Override
    public int minChunkX() {
        return snapshot == null ? 0 : snapshot.centerX() - snapshot.radius();
    }

    @Override
    public int minChunkZ() {
        return snapshot == null ? 0 : snapshot.centerZ() - snapshot.radius();
    }

    @Override
    public int color(int layers, int chunkX, int chunkZ) {
        if (snapshot == null) {
            return 0;
        }
        int dx = chunkX - snapshot.centerX();
        int dz = chunkZ - snapshot.centerZ();
        float radiation = (layers & RADIATION) != 0 ? snapshot.radiationAt(dx, dz) : 0.0f;
        float toxin = (layers & TOXIN) != 0 ? snapshot.toxinAt(dx, dz) : 0.0f;
        return radiation <= 0.0f && toxin <= 0.0f ? 0 : ContaminationMapView.cellColor(radiation, toxin);
    }

    @Override
    public Component describe(int layers, int chunkX, int chunkZ) {
        if (snapshot == null) {
            return null;
        }
        int dx = chunkX - snapshot.centerX();
        int dz = chunkZ - snapshot.centerZ();
        if (Math.abs(dx) > snapshot.radius() || Math.abs(dz) > snapshot.radius()) {
            return null;
        }
        return Component.translatable("screen.projectrebirth.contamination.values",
                String.format(Locale.ROOT, "%.1f", snapshot.radiationAt(dx, dz)), String.format(Locale.ROOT, "%.1f", snapshot.toxinAt(dx, dz)));
    }

    /** The marks of the snapshot, if their layer is on. */
    public List<UiMap.Mark> marks(int layers) {
        List<UiMap.Mark> marks = new ArrayList<>();
        if (snapshot == null || (layers & MARKS) == 0) {
            return marks;
        }
        String self = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getGameProfile().getName();
        for (Snapshot.Marker marker : snapshot.markers()) {
            int color;
            Component label;
            switch (marker.kind()) {
                case Snapshot.STATION -> {
                    color = ContaminationMapView.STATION;
                    label = Component.translatable("screen.projectrebirth.contamination.legend.station");
                }
                case Snapshot.SHELTER -> {
                    color = ContaminationMapView.SHELTER;
                    label = Component.translatable("screen.projectrebirth.contamination.legend.shelter");
                }
                case Snapshot.SOURCE -> {
                    color = ContaminationMapView.SOURCE;
                    label = Component.translatable("screen.projectrebirth.contamination.legend.source");
                }
                case Snapshot.MIND -> {
                    color = ContaminationMapView.MIND;
                    label = ContaminationMapView.mindLabel(marker);
                }
                case Snapshot.STRUCTURE -> {
                    color = ContaminationMapView.RUIN;
                    label = Component.translatable("screen.projectrebirth.contamination.legend.ruin");
                }
                default -> {
                    boolean you = marker.name().equals(self);
                    color = you ? ContaminationMapView.YOU : ContaminationMapView.PLAYER;
                    label = Component.literal(marker.name());
                }
            }
            marks.add(new UiMap.Mark(marker.x(), marker.z(), color, null, label, 4));
        }
        return marks;
    }
}
