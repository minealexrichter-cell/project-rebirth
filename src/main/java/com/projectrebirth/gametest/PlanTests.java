package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;

/**
 * Groups of game tests. Each group is a test namespace with its own copy of the test floor, so that one group can run
 * alone: {@code .\gradlew runGameTestServer -PgameTests=projectrebirth_atmosphere}. Without the property all run.
 */
public final class PlanTests {
    /** The tests of version 1.3.0 "Atmosphere" (docs/PLAN_1.3.md). */
    public static final String ATMOSPHERE = ProjectRebirth.MOD_ID + "_atmosphere";

    /** The tests of version 1.4.0 "Shelter" (docs/PLAN_1.4.md). */
    public static final String SHELTER = ProjectRebirth.MOD_ID + "_shelter";

    private PlanTests() {
    }
}
