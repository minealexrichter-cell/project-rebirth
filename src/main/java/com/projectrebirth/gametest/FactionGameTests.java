package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionData;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side checks for factions and friendly fire. Faction data is shared by all tests on the server, so every
 * test uses its own faction names and disbands its factions at the end.
 */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class FactionGameTests {
    private static final String TEMPLATE = "floor_5x5";

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static Faction factionOf(MinecraftServer server, ServerPlayer player) {
        return FactionData.get(server).of(player.getUUID()).orElseThrow();
    }

    /** The test world is kept between runs: remove what an earlier, failed run may have left. */
    private static void clean(MinecraftServer server, String... names) {
        for (String name : names) {
            FactionService.adminDisband(server, name);
        }
    }

    /** Hits the target once (ignoring invulnerability frames) and tells whether it lost health. */
    private static boolean hurts(LivingEntity target, DamageSource source) {
        target.invulnerableTime = 0;
        float before = target.getHealth();
        target.hurt(source, 4.0f);
        return target.getHealth() < before;
    }

    @GameTest(template = TEMPLATE)
    public static void factionLifecycle(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        clean(server, "Life Alpha");
        ServerPlayer leader = player(helper);
        ServerPlayer recruit = player(helper);

        helper.assertTrue(FactionService.create(leader, "Life Alpha", ChatFormatting.RED).success(), "Creating a faction must work");
        helper.assertFalse(FactionService.create(recruit, "life alpha", ChatFormatting.BLUE).success(), "Names are unique, ignoring case");
        helper.assertFalse(FactionService.create(recruit, "x", ChatFormatting.BLUE).success(), "Too short a name is refused");
        Faction faction = factionOf(server, leader);

        helper.assertFalse(FactionService.accept(recruit, faction.id()).success(), "Joining without an invitation is refused");
        faction.invited().add(recruit.getUUID());
        helper.assertTrue(FactionService.accept(recruit, faction.id()).success(), "An invited player can join");
        helper.assertTrue(faction.isMember(recruit.getUUID()) && faction.members().size() == 2, "The recruit must be a member");
        helper.assertFalse(FactionService.kick(recruit, leader.getUUID()).success(), "Only the leader may kick");

        FactionData reloaded = FactionData.reload(FactionData.get(server), helper.getLevel().registryAccess());
        Faction copy = reloaded.of(recruit.getUUID()).orElseThrow();
        helper.assertTrue(copy.name().equals("Life Alpha") && copy.color() == ChatFormatting.RED
                && copy.leader().equals(leader.getUUID()) && copy.members().size() == 2, "The faction must survive saving");

        helper.assertTrue(FactionService.leave(leader).success(), "The leader can leave");
        helper.assertTrue(faction.leader().equals(recruit.getUUID()), "Leadership passes to the remaining member");
        helper.assertTrue(FactionService.leave(recruit).success(), "The last member can leave");
        helper.assertTrue(FactionData.get(server).byName("Life Alpha").isEmpty(), "An empty faction is removed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void friendlyFireBetweenPlayers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer leader = player(helper);
        ServerPlayer mate = player(helper);
        ServerPlayer stranger = player(helper);
        clean(server, "Fire Beta", "Fire Gamma");
        FactionService.create(leader, "Fire Beta", ChatFormatting.GREEN);
        FactionService.create(stranger, "Fire Gamma", ChatFormatting.YELLOW);
        Faction faction = factionOf(server, leader);
        faction.invited().add(mate.getUUID());
        FactionService.accept(mate, faction.id());
        FactionService.setFriendlyFire(leader, false);

        // The test server has PvP off, so the faction rule itself is checked for player targets;
        // real damage is checked on bodies and armor in the next test.
        helper.assertTrue(FactionService.blocksDamage(mate, mate.damageSources().playerAttack(leader)),
                "A faction mate must be protected with friendly fire off");
        helper.assertFalse(FactionService.blocksDamage(stranger, stranger.damageSources().playerAttack(leader)),
                "Other factions are not protected");
        helper.assertFalse(FactionService.blocksDamage(mate, mate.damageSources().generic()), "Damage from the world is not affected");

        helper.assertTrue(FactionService.setFriendlyFire(leader, true).success(), "The leader can turn friendly fire on");
        helper.assertFalse(FactionService.blocksDamage(mate, mate.damageSources().playerAttack(leader)),
                "With friendly fire on, mates are not protected");

        FactionService.adminDisband(server, "Fire Beta");
        FactionService.adminDisband(server, "Fire Gamma");
        helper.succeed();
    }

    /** Armor (piloted or not), artificial bodies and arrows follow the same rule. */
    @GameTest(template = TEMPLATE)
    public static void friendlyFireCoversArmorBodiesAndArrows(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer shooter = player(helper);
        ServerPlayer owner = player(helper);
        clean(server, "Fire Delta");
        FactionService.create(shooter, "Fire Delta", ChatFormatting.BLUE);
        Faction faction = factionOf(server, shooter);
        faction.invited().add(owner.getUUID());
        FactionService.accept(owner, faction.id());
        FactionService.setFriendlyFire(shooter, false);

        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), new BlockPos(1, 2, 1));
        armor.setOwner(owner.getUUID());
        float structural = armor.getStructural();
        armor.invulnerableTime = 0;
        armor.hurt(armor.damageSources().playerAttack(shooter), 10.0f);
        helper.assertTrue(armor.getStructural() == structural, "A mate's empty armor must not be damaged");

        helper.assertTrue(owner.startRiding(armor, true), "The owner must be able to get in");
        armor.invulnerableTime = 0;
        armor.hurt(armor.damageSources().playerAttack(shooter), 10.0f);
        helper.assertTrue(armor.getStructural() == structural, "A mate's piloted armor must not be damaged");
        owner.stopRiding();

        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), new BlockPos(3, 2, 3));
        body.setOwner(owner.getUUID());
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setOwner(shooter);
        helper.assertFalse(hurts(body, body.damageSources().arrow(arrow, shooter)), "A mate's arrow must not hurt the body");
        helper.assertFalse(hurts(body, body.damageSources().playerAttack(shooter)), "A mate's blow must not hurt the body");

        FactionService.setFriendlyFire(shooter, true);
        helper.assertTrue(hurts(body, body.damageSources().playerAttack(shooter)), "With friendly fire on, the body can be hurt");

        FactionService.adminDisband(server, "Fire Delta");
        helper.succeed();
    }
}
