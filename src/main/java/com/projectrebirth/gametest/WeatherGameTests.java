package com.projectrebirth.gametest;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EnvironmentState;
import com.projectrebirth.environment.EventPhase;
import com.projectrebirth.weather.Forecast;
import com.projectrebirth.weather.ForecastPayload;
import com.projectrebirth.weather.WeatherRegistry;
import com.projectrebirth.weather.WeatherStationBlockEntity;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The weather station (1.6.0, block D): the next catastrophe is decided when the calm begins and comes as planned; the last
 * ones are remembered; a forecast sees only within its horizon, roughly or exactly; the station needs the sky and energy, gives
 * its signals and reaches the players around it.
 */
@GameTestHolder(PlanTests.SCOUTING)
@PrefixGameTestTemplate(false)
public class WeatherGameTests {
    private static final String TEMPLATE = "floor_5x5";

    private static RandomSource random() {
        return RandomSource.create(42L);
    }

    @GameTest(template = TEMPLATE, batch = "scouting_weather_plan")
    public static void theNextCatastropheIsDecidedAheadAndComesAsPlanned(GameTestHelper helper) {
        EnvironmentState state = new EnvironmentState();
        state.startCalm(100);
        state.tickSecond(random(), true);
        String planned = state.plannedEvent();
        helper.assertTrue(planned != null && Environment.EVENTS.contains(planned), "Planned at once: " + planned);
        float intensity = state.plannedIntensity();
        int warning = RebirthConfig.ENVIRONMENT_WARNING_SECONDS.get();
        helper.assertTrue(state.secondsUntilComing() == 99 + warning, "The calm and the warning: " + state.secondsUntilComing());
        helper.assertTrue(planned.equals(state.comingEvent()), "Coming");
        EnvironmentState.Change change = EnvironmentState.Change.NONE;
        for (int i = 0; i < 99 && change == EnvironmentState.Change.NONE; i++) {
            change = state.tickSecond(random(), true);
        }
        helper.assertTrue(change == EnvironmentState.Change.WARNING && planned.equals(state.event()) && state.intensity() == intensity,
                "The planned one comes: " + state.event());
        helper.assertTrue(planned.equals(state.comingEvent()) && state.secondsUntilComing() == warning, "Still foreseen in the warning");
        state.startCalm(10);
        state.tickSecond(random(), false);
        helper.assertTrue(state.plannedEvent() == null, "Nothing planned where there are no catastrophes");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_weather_history")
    public static void theLastCatastrophesAreRememberedAndSaved(GameTestHelper helper) {
        EnvironmentState state = new EnvironmentState();
        for (int day = 1; day <= 7; day++) {
            state.remember(Environment.TOXIC_STORM, 0.5f, 300, day);
        }
        state.startCalm(500);
        state.tickSecond(random(), true);
        helper.assertTrue(state.history().size() == EnvironmentState.HISTORY && state.history().get(0).day() == 7, "The newest five");
        EnvironmentState back = EnvironmentState.load(state.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(back.history().equals(state.history()), "History saved");
        helper.assertTrue(state.plannedEvent() != null && state.plannedEvent().equals(back.plannedEvent())
                && back.plannedIntensity() == state.plannedIntensity() && back.plannedActiveSeconds() == state.plannedActiveSeconds(), "The plan saved");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_weather_forecast")
    public static void aForecastSeesOnlyWithinItsHorizon(GameTestHelper helper) {
        int warning = RebirthConfig.ENVIRONMENT_WARNING_SECONDS.get();
        EnvironmentState state = new EnvironmentState();
        int far = RebirthConfig.WEATHER_STATION_FORECAST_MINUTES.get() * 60 + 60;
        state.startCalm(far - warning + 1);
        state.tickSecond(random(), true);
        helper.assertTrue(state.secondsUntilComing() == far, "Until: " + state.secondsUntilComing());
        helper.assertTrue(Forecast.of(state, false) == null, "Too far for a short forecast");
        Forecast exact = Forecast.of(state, true);
        helper.assertTrue(exact != null && exact.exact() && exact.event().equals(state.plannedEvent()) && exact.secondsUntil() == far,
                "The long range one sees it");
        EnvironmentState near = new EnvironmentState();
        near.startCalm(7 * 60 - warning + 1);
        near.tickSecond(random(), true);
        Forecast rough = Forecast.of(near, false);
        int step = RebirthConfig.WEATHER_STATION_ROUND_MINUTES.get();
        helper.assertTrue(rough != null && !rough.exact() && rough.roughLow() == 7 / step * step && rough.roughHigh() == rough.roughLow() + step,
                "Roughly: " + (rough == null ? "none" : rough.roughLow() + "-" + rough.roughHigh()));
        ForecastPayload payload = ForecastPayload.of(rough);
        helper.assertTrue(payload.secondsUntil() == -1 && payload.intensity() == 0.0f, "A short forecast does not send the exact time");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ForecastPayload sent = ForecastPayload.of(exact);
        ForecastPayload.STREAM_CODEC.encode(buffer, sent);
        helper.assertTrue(sent.equals(ForecastPayload.STREAM_CODEC.decode(buffer)), "The payload");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_weather_station", skyAccess = true)
    public static void theStationNeedsTheSkyAndEnergyAndGivesItsSignals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EnvironmentState state = EnvironmentState.get(level);
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, WeatherRegistry.WEATHER_STATION.get());
        WeatherStationBlockEntity station = (WeatherStationBlockEntity) helper.getBlockEntity(pos);
        try {
            state.startCalm(200);
            state.tickSecond(random(), true);
            station.update(level);
            helper.assertTrue(station.state() == WeatherStationBlockEntity.STATE_NO_ENERGY, "Without energy: " + station.state());
            station.energy().load(station.energy().getMaxEnergyStored());
            station.update(level);
            helper.assertTrue(station.state() == WeatherStationBlockEntity.STATE_FORECAST && station.forecast() != null
                    && station.forecast().event().equals(state.plannedEvent()), "A forecast: " + station.state());
            helper.assertTrue(station.signal() == 0 && station.comparator() >= 1 && station.comparator() <= 14,
                    "Far yet: signal " + station.signal() + ", comparator " + station.comparator());
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            BlockPos at = helper.absolutePos(pos);
            player.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 3.5);
            helper.assertTrue(station.reaches(player), "A player near it gets the forecast");
            player.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + RebirthConfig.WEATHER_STATION_RADIUS.get() + 20.0);
            helper.assertFalse(station.reaches(player), "Far away a stranger does not");
            station.setOwner(player.getUUID());
            helper.assertTrue(station.reaches(player), "Its owner does, anywhere in the dimension");
            state.startActive(Environment.TOXIC_STORM, 0.5f, 300);
            station.update(level);
            helper.assertTrue(station.state() == WeatherStationBlockEntity.STATE_ACTIVE && station.signal() == 15 && station.comparator() == 15,
                    "During it a full signal");
            helper.assertTrue(level.hasNeighborSignal(helper.absolutePos(pos.east())), "Redstone next to it is powered");
            helper.setBlock(new BlockPos(2, 4, 2), Blocks.STONE);
            station.update(level);
            helper.assertTrue(station.state() == WeatherStationBlockEntity.STATE_NO_SKY && station.signal() == 0, "Under a roof it sees nothing");
            helper.setBlock(new BlockPos(2, 4, 2), Blocks.AIR);
            station.items().setStackInSlot(WeatherStationBlockEntity.MODULE_SLOT, new ItemStack(WeatherRegistry.LONG_RANGE_MODULE.get()));
            helper.assertTrue(station.longRange(), "The module is in");
        } finally {
            state.clear();
        }
        helper.succeed();
    }
}
