package com.projectrebirth.client.garrison;

import java.util.Locale;
import java.util.Optional;

import javax.annotation.Nullable;

import com.projectrebirth.client.ContaminationMapView;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiMap;
import com.projectrebirth.factions.ClientFactionCache;
import com.projectrebirth.factions.FactionView;
import com.projectrebirth.garrison.ConsoleView;
import com.projectrebirth.monitoring.Snapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The cells of the console's map (1.8.0, block E): the land within the console's range, tinted with the colour of the faction that
 * owns it (by the same rule as the server), and where a monitoring station stands near, its contamination over it. Client only.
 */
final class ConsoleMapSource implements UiMap.Source {
    static final int TERRITORY = 1;
    static final int CONTAMINATION = 2;

    private final BlockPos console;
    private int radius = 16;
    @Nullable
    private ConsoleView view;

    ConsoleMapSource(BlockPos console) {
        this.console = console;
    }

    /** A new view came; returns whether the cells must be painted again (they are, once a second, a few hundred chunks). */
    boolean set(@Nullable ConsoleView next) {
        boolean changed = next != view;
        view = next;
        if (next != null) {
            radius = Math.max(4, next.range() / 16 + 1);
        }
        return changed;
    }

    @Override
    public int side() {
        return radius * 2 + 1;
    }

    @Override
    public int minChunkX() {
        return (console.getX() >> 4) - radius;
    }

    @Override
    public int minChunkZ() {
        return (console.getZ() >> 4) - radius;
    }

    @Nullable
    private Snapshot snapshot() {
        return view == null ? null : view.map().orElse(null);
    }

    private static Optional<FactionView> owner(int chunkX, int chunkZ) {
        var player = Minecraft.getInstance().player;
        return player == null ? Optional.empty()
                : ClientFactionCache.territoryAt(player.level().dimension(), new BlockPos(chunkX * 16 + 8, 64, chunkZ * 16 + 8));
    }

    @Override
    public int color(int layers, int chunkX, int chunkZ) {
        Snapshot snapshot = snapshot();
        if ((layers & CONTAMINATION) != 0 && snapshot != null) {
            int dx = chunkX - snapshot.centerX();
            int dz = chunkZ - snapshot.centerZ();
            if (Math.abs(dx) <= snapshot.radius() && Math.abs(dz) <= snapshot.radius()) {
                float radiation = snapshot.radiationAt(dx, dz);
                float toxin = snapshot.toxinAt(dx, dz);
                if (radiation > 0.0f || toxin > 0.0f) {
                    return ContaminationMapView.cellColor(radiation, toxin);
                }
            }
        }
        if ((layers & TERRITORY) != 0) {
            Optional<FactionView> owner = owner(chunkX, chunkZ);
            if (owner.isPresent()) {
                Integer rgb = owner.get().color().getColor();
                return UiColors.withAlpha(rgb == null ? 0xFFFFFF : rgb, 0x58);
            }
        }
        return 0;
    }

    @Override
    public Component describe(int layers, int chunkX, int chunkZ) {
        Component land = owner(chunkX, chunkZ).map(faction -> (Component) Component.literal(faction.name()).withStyle(faction.color()))
                .orElse(null);
        Snapshot snapshot = snapshot();
        if (snapshot != null) {
            int dx = chunkX - snapshot.centerX();
            int dz = chunkZ - snapshot.centerZ();
            if (Math.abs(dx) <= snapshot.radius() && Math.abs(dz) <= snapshot.radius()) {
                Component values = Component.translatable("screen.projectrebirth.contamination.values",
                        String.format(Locale.ROOT, "%.1f", snapshot.radiationAt(dx, dz)), String.format(Locale.ROOT, "%.1f", snapshot.toxinAt(dx, dz)));
                return land == null ? values : land.copy().append(" · ").append(values);
            }
        }
        return land;
    }
}
