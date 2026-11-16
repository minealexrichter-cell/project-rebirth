package com.projectrebirth.client;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.client.screen.ContaminationMapScreen;
import com.projectrebirth.monitoring.ContaminationMapItem;
import com.projectrebirth.monitoring.MonitoringPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** The last scan of the monitoring station whose window is open, and the opening of printed maps (1.6.0, block E). Client only. */
public final class MonitoringClient {
    @Nullable
    private static MonitoringPayload last;

    private MonitoringClient() {
    }

    static void register() {
        MonitoringPayload.clientHandler = payload -> Minecraft.getInstance().execute(() -> last = payload);
        ContaminationMapItem.opener = snapshot -> Minecraft.getInstance().setScreen(new ContaminationMapScreen(snapshot));
    }

    /** The last scan of the station at that place, or null. */
    @Nullable
    public static MonitoringPayload of(BlockPos pos) {
        return last != null && last.pos().equals(pos) ? last : null;
    }
}
