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

    /** The tests of version 1.4.1 "Codex" (docs/PLAN_1.4.1.md). */
    public static final String CODEX = ProjectRebirth.MOD_ID + "_codex";

    /** The tests of version 1.5.0 "Technical limits" (docs/PLAN_1.5.md). */
    public static final String POWER = ProjectRebirth.MOD_ID + "_power";

    /** The tests of the power armor strikes of 1.5.2 (the cooldown of the empty armored hands). */
    public static final String ARMOR = ProjectRebirth.MOD_ID + "_armor";

    /** The tests of version 1.6.0 "Scouting": contaminated zones and what follows (docs/PLAN_1.6.md). */
    public static final String SCOUTING = ProjectRebirth.MOD_ID + "_scouting";

    private PlanTests() {
    }
}
