package com.projectrebirth.scouting;

import java.util.ArrayList;
import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyGadgets;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.ContaminationMap;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.RadiationSources;
import com.projectrebirth.monitoring.Snapshot;
import com.projectrebirth.powerarmor.ArmorUpgradeEffects;
import com.projectrebirth.powerarmor.PowerArmorEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Scouting in the field (1.6.0, block F). The field analyzer, held in either hand, shows the contamination of the chunk the player
 * stands in and of a small grid around it, and clicks near radiation sources. The sensor module of an artificial body (its reach
 * grows with the body class, from the worst to the best) and the sensor unit on the helmet of power armor show a small map of the
 * contamination around with the source blocks and the ruins of the mod marked. The server reads it all and sends it every few
 * seconds; numbers in the config ([scouting]).
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class Scouting {
    /** The kind of the sensor upgrades in the upgrade data. */
    public static final String SENSOR = "sensor";
    public static final int ANALYZER = 1;
    public static final int SENSOR_TOOL = 2;

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    public static final DeferredItem<FieldAnalyzerItem> FIELD_ANALYZER = ITEMS.register("field_analyzer",
            () -> new FieldAnalyzerItem(new Item.Properties().stacksTo(1)));

    private Scouting() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ScoutPayload.register(modEventBus);
    }

    public static boolean enabled() {
        return RebirthConfig.SERVER_SPEC.isLoaded() && RebirthConfig.SCOUTING_ENABLED.get();
    }

    public static boolean holdsAnalyzer(ServerPlayer player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).is(FIELD_ANALYZER.get())
                || player.getItemInHand(InteractionHand.OFF_HAND).is(FIELD_ANALYZER.get());
    }

    /** How many chunks each way the player's sensor shows: by the body class, by the armor, or 0 without one. Public for tests. */
    public static int sensorRange(ServerPlayer player) {
        if (player.getVehicle() instanceof PowerArmorEntity armor && ArmorUpgradeEffects.hasSensor(armor)) {
            return RebirthConfig.ARMOR_SENSOR_RANGE.get();
        }
        if (TransferService.isInArtificialBody(player) && BodyGadgets.has(player.registryAccess(), BodyGadgets.of(player), SENSOR)) {
            BodyClass bodyClass = TransferService.state(player).body().orElse(null);
            return bodyClass == null ? 0 : RebirthConfig.BODY_SENSOR_RANGE.get(bodyClass).get();
        }
        return 0;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !enabled()
                || (player.level().getGameTime() + player.getId()) % (20L * RebirthConfig.SCOUTING_UPDATE_SECONDS.get()) != 0) {
            return;
        }
        ScoutPayload payload = read(player);
        if (payload != null && player.connection.hasChannel(ScoutPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** What the player's analyzer and sensor read now, or null without either. Public for tests. */
    public static ScoutPayload read(ServerPlayer player) {
        boolean analyzer = holdsAnalyzer(player);
        int range = sensorRange(player);
        if (!analyzer && range <= 0) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        int cx = player.chunkPosition().x, cz = player.chunkPosition().z;
        int radius = Math.max(range, analyzer ? RebirthConfig.ANALYZER_RADIUS.get() : 0);
        List<Snapshot.Marker> markers = new ArrayList<>();
        markers.add(new Snapshot.Marker(Snapshot.PLAYER, player.getBlockX(), player.getBlockZ(), player.getGameProfile().getName()));
        if (range > 0) {
            for (BlockPos source : RadiationSources.sourcesNear(level, cx, cz, range)) {
                markers.add(new Snapshot.Marker(Snapshot.SOURCE, source.getX(), source.getZ(), ""));
            }
            markers.addAll(ruinsNear(level, cx, cz, range));
        }
        Snapshot snapshot = Snapshot.scan(level, cx, cz, radius, markers);
        ContaminationMap.Reading sources = Environment.isEnabled() ? RadiationSources.readingAt(level, player.blockPosition()) : ContaminationMap.Reading.NONE;
        return new ScoutPayload((analyzer ? ANALYZER : 0) | (range > 0 ? SENSOR_TOOL : 0), snapshot, sources.radiation(), sources.toxin());
    }

    /** The ruins of the mod whose start lies in a loaded chunk of the square, marked at their middle. */
    static List<Snapshot.Marker> ruinsNear(ServerLevel level, int cx, int cz, int radius) {
        List<Snapshot.Marker> out = new ArrayList<>();
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (var entry : chunk.getAllStarts().entrySet()) {
                    Structure structure = entry.getKey();
                    StructureStart start = entry.getValue();
                    ResourceLocation id = structures.getKey(structure);
                    if (id != null && id.getNamespace().equals(ProjectRebirth.MOD_ID) && start.isValid()) {
                        BlockPos centre = start.getBoundingBox().getCenter();
                        out.add(new Snapshot.Marker(Snapshot.STRUCTURE, centre.getX(), centre.getZ(), id.getPath()));
                    }
                }
            }
        }
        return out;
    }
}
