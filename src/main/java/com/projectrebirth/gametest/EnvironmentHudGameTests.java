package com.projectrebirth.gametest;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.CatastrophePayload;
import com.projectrebirth.environment.EnvironmentState;
import com.projectrebirth.environment.EnvironmentStatusPayload;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EventPhase;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.environment.HumanBody;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What the server tells the HUD (block F of plan 1.3). The drawing itself is checked in the game. */
@GameTestHolder(PlanTests.ATMOSPHERE)
@PrefixGameTestTemplate(false)
public class EnvironmentHudGameTests {
    private static final String TEMPLATE = "floor_5x5";

    @GameTest(template = TEMPLATE)
    public static void theOrdinaryBackgroundIsNotMarkedDangerous(GameTestHelper helper) {
        helper.assertTrue(HumanBody.dangerMask(1.0f, 1.0f, 0.0f) == 0, "The background of the overworld is no danger");
        float radiation = RebirthConfig.HUD_DANGER_RADIATION.get().floatValue() + 0.1f;
        float pollution = RebirthConfig.HUD_DANGER_POLLUTION.get().floatValue() + 0.1f;
        int all = HumanBody.dangerMask(radiation, pollution, 0.5f);
        EnvironmentStatusPayload payload = new EnvironmentStatusPayload(radiation, pollution, 0.5f, 0.0f, 0.0f, all, 0, 0.0f);
        for (Hazard hazard : Hazard.values()) {
            helper.assertTrue(payload.dangerous(hazard), "Marked: " + hazard);
        }
        EnvironmentStatusPayload onlyAcid = new EnvironmentStatusPayload(0.0f, 0.0f, 0.5f, 0.0f, 0.0f, HumanBody.dangerMask(0.0f, 0.0f, 0.5f), 0, 0.0f);
        helper.assertTrue(onlyAcid.dangerous(Hazard.ACID) && !onlyAcid.dangerous(Hazard.POLLUTION) && !onlyAcid.dangerous(Hazard.RADIATION), "Only acid");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theStatusReachesTheClientWhole(GameTestHelper helper) {
        EnvironmentStatusPayload sent = new EnvironmentStatusPayload(12.5f, 3.25f, 0.75f, 140.0f, 61.5f, 5, 2, 7.5f);
        ByteBuf buffer = Unpooled.buffer();
        EnvironmentStatusPayload.STREAM_CODEC.encode(buffer, sent);
        helper.assertTrue(sent.equals(EnvironmentStatusPayload.STREAM_CODEC.decode(buffer)), "Status");
        EnvironmentState state = new EnvironmentState();
        state.startWarning(Environment.TOXIC_STORM, 1.3f, 200, 95);
        CatastrophePayload catastrophe = CatastrophePayload.of(state);
        ByteBuf other = Unpooled.buffer();
        CatastrophePayload.STREAM_CODEC.encode(other, catastrophe);
        CatastrophePayload back = CatastrophePayload.STREAM_CODEC.decode(other);
        helper.assertTrue(back.equals(catastrophe) && back.phase() == EventPhase.WARNING && back.secondsLeft() == 95, "Catastrophe: " + back);
        helper.assertTrue(CatastrophePayload.of(new EnvironmentState()) == CatastrophePayload.CALM, "Calm says nothing");
        helper.succeed();
    }
}
