package com.projectrebirth.gametest;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.DynamicOps;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionData;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.powerarmor.ArmorAbility;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.Metal;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.TechItems;
import com.projectrebirth.technology.TechRecipes;
import com.projectrebirth.weapons.WeaponDefinition;
import com.projectrebirth.weapons.WeaponItem;
import com.projectrebirth.weapons.WeaponRegistry;
import com.projectrebirth.weapons.Weapons;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Guns: only a pilot can use them, and the server decides everything. Run with {@code .\gradlew runGameTestServer}. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class WeaponGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos SEAT = new BlockPos(1, 2, 1);
    private static final BlockPos TARGET = new BlockPos(3, 2, 3);
    /** A full set of iron armor. */
    private static final float IRON_ARMOR_POINTS = 15.0f;

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** A pilot of a working armor holding the weapon, its magazine filled with the given rounds. */
    private static ServerPlayer pilot(GameTestHelper helper, Item weapon, int rounds) {
        ServerPlayer pilot = player(helper);
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), SEAT);
        armor.setVariant(ArmorVariant.COMBAT);
        pilot.startRiding(armor, true);
        ArmorAbility.sync(pilot);
        give(pilot, weapon, rounds);
        return pilot;
    }

    private static void give(ServerPlayer player, Item weapon, int rounds) {
        ItemStack stack = new ItemStack(weapon);
        Weapons.setAmmo(stack, rounds);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, stack);
    }

    private static Mob target(GameTestHelper helper) {
        // A villager has no armor points of its own, so the damage reported is the damage taken.
        return helper.spawnWithNoFreeWill(EntityType.VILLAGER, TARGET);
    }

    private static void aimAt(ServerPlayer pilot, net.minecraft.world.entity.Entity target) {
        pilot.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
    }

    private static RebirthConfig.WeaponStats stats(String id) {
        return RebirthConfig.weapon(id);
    }

    @GameTest(template = TEMPLATE)
    public static void outsideArmorShootingAimingAndReloadingAreRefused(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        give(player, WeaponRegistry.ASSAULT_RIFLE.get(), 5);
        player.getInventory().add(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 10));
        helper.assertTrue(Weapons.pressTrigger(player) == null, "No shot outside an armor");
        helper.assertTrue(Weapons.ammoIn(player.getMainHandItem()) == 5, "No round is spent");
        helper.assertFalse(Weapons.setAiming(player, true), "No aiming outside an armor");
        helper.assertFalse(Weapons.startReload(player), "No reloading outside an armor");
        helper.assertTrue(player.getInventory().countItem(WeaponRegistry.ASSAULT_RIFLE_AMMO.get()) == 10, "The rounds stay in the inventory");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void shutDownArmorWreckAndAbilitySlotDoNotShoot(GameTestHelper helper) {
        ServerPlayer pilot = pilot(helper, WeaponRegistry.ASSAULT_RIFLE.get(), 5);
        PowerArmorEntity armor = (PowerArmorEntity) pilot.getVehicle();
        pilot.getInventory().selected = ArmorAbility.SLOT;
        helper.assertTrue(Weapons.pressTrigger(pilot) == null, "The ability slot is not a weapon");
        Weapons.releaseTrigger(pilot);
        pilot.getInventory().selected = 0;
        armor.setIntegrity(0.0f, armor.getCritical());
        helper.assertTrue(armor.isDisabled(), "The armor must be shut down");
        helper.assertTrue(Weapons.pressTrigger(pilot) == null, "A shut-down armor does not shoot");
        helper.assertFalse(Weapons.startReload(pilot), "A shut-down armor does not reload");
        helper.assertFalse(Weapons.setAiming(pilot, true), "A shut-down armor does not aim");
        helper.assertTrue(Weapons.ammoIn(pilot.getMainHandItem()) == 5, "No round is spent");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aHitDamagesTheTargetInThePilotsNameAndSpendsARound(GameTestHelper helper) {
        ServerPlayer pilot = pilot(helper, WeaponRegistry.BATTLE_RIFLE.get(), 3);
        Mob target = target(helper);
        float health = target.getHealth();
        aimAt(pilot, target);
        Weapons.Shot shot = Weapons.pressTrigger(pilot);
        helper.assertTrue(shot != null && shot.hits() == 1, "The shot must hit, got " + shot);
        helper.assertTrue(Weapons.ammoIn(pilot.getMainHandItem()) == 2, "One round is spent");
        helper.assertTrue(target.getHealth() < health, "The target is hurt");
        helper.assertTrue(target.getLastHurtByMob() == pilot, "The damage counts for the pilot");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aBlockStopsTheBulletAndIsNotBroken(GameTestHelper helper) {
        ServerPlayer pilot = pilot(helper, WeaponRegistry.BATTLE_RIFLE.get(), 3);
        Mob target = target(helper);
        float health = target.getHealth();
        aimAt(pilot, target);
        Vec3 eye = pilot.getEyePosition();
        Vec3 middle = eye.add(target.getBoundingBox().getCenter().subtract(eye).scale(0.75));
        BlockPos wall = BlockPos.containing(middle);
        helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        Weapons.Shot shot = Weapons.pressTrigger(pilot);
        helper.assertTrue(shot != null && shot.hits() == 0, "The wall takes the shot, got " + shot);
        helper.assertTrue(target.getHealth() == health, "The target stays whole");
        helper.assertTrue(helper.getLevel().getBlockState(wall).is(Blocks.STONE), "Bullets do not break blocks");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aHitOnAnArmorGoesToItsIntegrityAndNotToThePilot(GameTestHelper helper) {
        ServerPlayer shooter = pilot(helper, WeaponRegistry.BATTLE_RIFLE.get(), 3);
        ServerPlayer other = player(helper);
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), new BlockPos(4, 2, 4));
        armor.setVariant(ArmorVariant.COMBAT);
        other.startRiding(armor, true);
        float structural = armor.getStructural();
        float health = other.getHealth();
        aimAt(shooter, armor);
        Weapons.Shot shot = Weapons.pressTrigger(shooter);
        helper.assertTrue(shot != null && shot.hits() == 1, "The shot must hit, got " + shot);
        helper.assertTrue(armor.getStructural() < structural, "The armor loses integrity");
        helper.assertTrue(other.getHealth() == health, "Its pilot stays whole");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void reloadTakesOnlyWhatIsThereAndCreatesNoRounds(GameTestHelper helper) {
        ServerPlayer pilot = pilot(helper, WeaponRegistry.ASSAULT_RIFLE.get(), 2);
        Item ammo = WeaponRegistry.ASSAULT_RIFLE_AMMO.get();
        pilot.getInventory().add(new ItemStack(ammo, 10));
        pilot.getInventory().add(new ItemStack(WeaponRegistry.BATTLE_RIFLE_AMMO.get(), 40));
        pilot.getInventory().add(new ItemStack(ammo, 3));
        helper.assertTrue(Weapons.startReload(pilot), "The reload must start");
        helper.assertFalse(Weapons.startReload(pilot), "Only one reload at a time");
        helper.assertTrue(Weapons.pressTrigger(pilot) == null, "No shots during a reload");
        Weapons.releaseTrigger(pilot);
        Weapons.tick(pilot);
        helper.assertTrue(Weapons.ammoIn(pilot.getMainHandItem()) == 2, "It takes time");
        helper.runAfterDelay(stats("assault_rifle").reloadTicks().get() + 1, () -> {
            Weapons.tick(pilot);
            helper.assertFalse(Weapons.isReloading(pilot), "The reload is over");
            helper.assertTrue(Weapons.ammoIn(pilot.getMainHandItem()) == 15, "2 + 13 rounds, got " + Weapons.ammoIn(pilot.getMainHandItem()));
            helper.assertTrue(pilot.getInventory().countItem(ammo) == 0, "All its rounds were used");
            helper.assertTrue(pilot.getInventory().countItem(WeaponRegistry.BATTLE_RIFLE_AMMO.get()) == 40, "Rounds of another weapon are not taken");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void reloadLeavesWhatDoesNotFitAndForeignRoundsDoNotLoad(GameTestHelper helper) {
        ServerPlayer pilot = pilot(helper, WeaponRegistry.BATTLE_RIFLE.get(), 8);
        pilot.getInventory().add(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 20));
        helper.assertFalse(Weapons.startReload(pilot), "Foreign rounds do not load");
        Item ammo = WeaponRegistry.BATTLE_RIFLE_AMMO.get();
        pilot.getInventory().add(new ItemStack(ammo, 16));
        helper.assertTrue(Weapons.startReload(pilot), "The reload must start");
        helper.runAfterDelay(stats("battle_rifle").reloadTicks().get() + 1, () -> {
            Weapons.tick(pilot);
            helper.assertTrue(Weapons.ammoIn(pilot.getMainHandItem()) == stats("battle_rifle").magazine().get(), "The magazine is full");
            helper.assertTrue(pilot.getInventory().countItem(ammo) == 16 - 2, "Only two rounds are taken");
            helper.assertFalse(Weapons.startReload(pilot), "A full magazine does not reload");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void changingTheItemInterruptsAReloadAndLosesNothing(GameTestHelper helper) {
        ServerPlayer pilot = pilot(helper, WeaponRegistry.ASSAULT_RIFLE.get(), 2);
        Item ammo = WeaponRegistry.ASSAULT_RIFLE_AMMO.get();
        pilot.getInventory().add(new ItemStack(ammo, 10));
        helper.assertTrue(Weapons.startReload(pilot), "The reload must start");
        pilot.getInventory().selected = 2;
        Weapons.tick(pilot);
        helper.assertFalse(Weapons.isReloading(pilot), "Another item stops the reload");
        pilot.getInventory().selected = 0;
        helper.assertTrue(Weapons.ammoIn(pilot.getMainHandItem()) == 2 && pilot.getInventory().countItem(ammo) == 10, "No rounds are lost or made");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void shotgunLoadsRoundByRound(GameTestHelper helper) {
        ServerPlayer pilot = pilot(helper, WeaponRegistry.COMBAT_SHOTGUN.get(), 0);
        Item ammo = WeaponRegistry.SHOTGUN_SHELL.get();
        pilot.getInventory().add(new ItemStack(ammo, 2));
        helper.assertTrue(Weapons.startReload(pilot), "The reload must start");
        int each = stats("combat_shotgun").reloadTicks().get();
        helper.runAfterDelay(each + 1, () -> {
            Weapons.tick(pilot);
            helper.assertTrue(Weapons.ammoIn(pilot.getMainHandItem()) == 1 && Weapons.isReloading(pilot), "One shell, and it goes on");
        });
        helper.runAfterDelay(2L * each + 3, () -> {
            Weapons.tick(pilot);
            helper.assertTrue(Weapons.ammoIn(pilot.getMainHandItem()) == 2 && !Weapons.isReloading(pilot), "Out of shells, so it ends");
            helper.assertTrue(pilot.getInventory().countItem(ammo) == 0, "All shells are used");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void frequentRequestsDoNotBeatTheRateOfFire(GameTestHelper helper) {
        ServerPlayer pilot = pilot(helper, WeaponRegistry.BATTLE_RIFLE.get(), 10);
        int shots = 0;
        for (int i = 0; i < 50; i++) {
            if (Weapons.pressTrigger(pilot) != null) {
                shots++;
            }
            Weapons.releaseTrigger(pilot);
        }
        helper.assertTrue(shots == 1, "One shot within a tick, got " + shots);
        helper.assertTrue(Weapons.ammoIn(pilot.getMainHandItem()) == 9, "One round spent");
        helper.runAfterDelay(stats("battle_rifle").shotIntervalTicks().get(), () -> {
            helper.assertTrue(Weapons.pressTrigger(pilot) != null, "After the interval it fires again");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void automaticFiresWhileHeldSemiAutomaticOnlyPerPress(GameTestHelper helper) {
        ServerPlayer automatic = pilot(helper, WeaponRegistry.ASSAULT_RIFLE.get(), 30);
        ServerPlayer semi = pilot(helper, WeaponRegistry.BATTLE_RIFLE.get(), 10);
        Weapons.pressTrigger(automatic);
        Weapons.pressTrigger(semi);
        int interval = Math.max(stats("assault_rifle").shotIntervalTicks().get(), stats("battle_rifle").shotIntervalTicks().get());
        helper.runAfterDelay(interval, () -> {
            Weapons.tick(automatic);
            Weapons.tick(semi);
            helper.assertTrue(Weapons.ammoIn(automatic.getMainHandItem()) == 28, "A held automatic fires on");
            helper.assertTrue(Weapons.ammoIn(semi.getMainHandItem()) == 9, "A held semi-automatic does not");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void shotgunFiresItsPelletsAndHurtsLessFarAway(GameTestHelper helper) {
        ServerPlayer pilot = pilot(helper, WeaponRegistry.COMBAT_SHOTGUN.get(), 6);
        Mob target = target(helper);
        // Tough enough to survive a full shot, so that all of its damage can be counted.
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        target.setHealth(1000.0f);
        float health = target.getHealth();
        aimAt(pilot, target);
        RebirthConfig.WeaponStats stats = stats("combat_shotgun");
        Weapons.Shot shot = Weapons.pressTrigger(pilot);
        helper.assertTrue(shot != null && shot.pellets() == stats.pellets().get(), "The configured number of pellets, got " + shot);
        helper.assertTrue(Weapons.ammoIn(pilot.getMainHandItem()) == 5, "One shell per shot, not per pellet");
        // Each pellet that hit counts, also when several hit at once.
        helper.assertTrue(Math.abs(health - target.getHealth() - shot.damage()) < 0.01f,
                "All hits land together: lost " + (health - target.getHealth()) + ", dealt " + shot.damage());
        helper.assertTrue(Weapons.damageAt(stats, 0.0) > Weapons.damageAt(stats, stats.range().get()), "Damage falls with distance");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void friendlyFireProtectsTheArmorOfAFactionMate(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer shooter = pilot(helper, WeaponRegistry.BATTLE_RIFLE.get(), 3);
        ServerPlayer mate = player(helper);
        String name = "Gun Mates";
        FactionData.get(server).byName(name).ifPresent(old -> FactionService.adminDisband(server, name));
        FactionService.create(shooter, name, ChatFormatting.BLUE);
        Faction faction = FactionData.get(server).of(shooter.getUUID()).orElseThrow();
        faction.invited().add(mate.getUUID());
        FactionService.accept(mate, faction.id());
        FactionService.setFriendlyFire(shooter, false);

        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), new BlockPos(4, 2, 4));
        armor.setOwner(mate.getUUID());
        float structural = armor.getStructural();
        aimAt(shooter, armor);
        Weapons.Shot shot = Weapons.pressTrigger(shooter);
        helper.assertTrue(shot != null && shot.hits() == 1, "The shot must reach the armor, got " + shot);
        helper.assertTrue(armor.getStructural() == structural, "Friendly fire off: no damage");
        FactionService.adminDisband(server, name);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aimingIsShownOnTheArmorAndSlowsItDown(GameTestHelper helper) {
        ServerPlayer pilot = pilot(helper, WeaponRegistry.BATTLE_RIFLE.get(), 3);
        PowerArmorEntity armor = (PowerArmorEntity) pilot.getVehicle();
        float speed = armor.pilotSpeed(pilot);
        helper.assertTrue(Weapons.setAiming(pilot, true), "A pilot can aim");
        helper.assertTrue(armor.isAimingWeapon(), "Everyone sees it");
        float expected = speed * stats("battle_rifle").aimSpeedMultiplier().get().floatValue();
        helper.assertTrue(Math.abs(armor.pilotSpeed(pilot) - expected) < 0.0001f, "Aiming slows the armor, got " + armor.pilotSpeed(pilot));
        pilot.getInventory().selected = 3;
        Weapons.tick(pilot);
        helper.assertFalse(armor.isAimingWeapon(), "Without a weapon in hand the aim ends");
        helper.assertTrue(Math.abs(armor.pilotSpeed(pilot) - speed) < 0.0001f, "And the speed comes back");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void leavingTheArmorEndsTheReloadWithoutLoss(GameTestHelper helper) {
        ServerPlayer pilot = pilot(helper, WeaponRegistry.ASSAULT_RIFLE.get(), 2);
        PowerArmorEntity armor = (PowerArmorEntity) pilot.getVehicle();
        Item ammo = WeaponRegistry.ASSAULT_RIFLE_AMMO.get();
        pilot.getInventory().add(new ItemStack(ammo, 10));
        helper.assertTrue(Weapons.startReload(pilot), "The reload must start");
        helper.assertTrue(armor.weaponReloadEnd() > 0L, "Everyone sees it");
        pilot.stopRiding();
        Weapons.tick(pilot);
        helper.assertFalse(Weapons.isReloading(pilot), "Getting out stops it");
        helper.assertTrue(Weapons.ammoIn(pilot.getMainHandItem()) == 2 && pilot.getInventory().countItem(ammo) == 10, "No rounds are lost or made");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(armor.weaponReloadEnd() == 0L, "The empty armor shows no reload");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void everyWeaponHasItsThreeSounds(GameTestHelper helper) {
        for (String id : List.of("assault_rifle", "battle_rifle", "combat_shotgun")) {
            WeaponRegistry.WeaponSounds sounds = WeaponRegistry.sounds(id);
            helper.assertTrue(sounds != null && sounds.shot().get() != null && sounds.reload().get() != null && sounds.empty().get() != null, "Sounds of " + id);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aCarriedWeaponSaysItIsTooHeavyOnlyNowAndThen(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        helper.assertFalse(Weapons.hintTooHeavy(player), "Nothing to say with empty hands");
        give(player, WeaponRegistry.ASSAULT_RIFLE.get(), 5);
        helper.assertTrue(Weapons.hintTooHeavy(player), "The first attempt gets the hint");
        helper.assertFalse(Weapons.hintTooHeavy(player), "Not again right away");
        helper.runAfterDelay(RebirthConfig.WEAPON_HEAVY_HINT_INTERVAL.get(), () -> {
            helper.assertTrue(Weapons.hintTooHeavy(player), "After a while it can be said again");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void aPilotOfAShutDownArmorIsNotToldItIsTooHeavy(GameTestHelper helper) {
        ServerPlayer pilot = pilot(helper, WeaponRegistry.ASSAULT_RIFLE.get(), 5);
        helper.assertFalse(Weapons.hintTooHeavy(pilot), "In an armor weight is not the problem");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aWeaponGivesNoMeleeBonusAndHasNoEffectOnWalkingByDefault(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        give(player, WeaponRegistry.BATTLE_RIFLE.get(), 5);
        Weapons.updateHeavyWalk(player, RebirthConfig.WEAPON_HEAVY_WALK_MULTIPLIER.getDefault());
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed, "No slower by default");
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == attack, "A blow is a blow of an empty hand");
        Weapons.updateHeavyWalk(player, 0.5);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed * 0.5) < 0.0001, "The option slows the carrier");
        player.getInventory().selected = 3;
        Weapons.updateHeavyWalk(player, 0.5);
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed, "Without the weapon in hand the speed comes back");
        helper.succeed();
    }

    /** Default numbers, so that the balance in docs/STATUS.md cannot drift from the config unnoticed. */
    private static double secondsToKill(GameTestHelper helper, RebirthConfig.WeaponStats stats, float armor) {
        var source = helper.getLevel().damageSources().source(WeaponRegistry.BULLET);
        float hit = stats.damage().getDefault().floatValue() * stats.pellets().getDefault();
        float dealt = CombatRules.getDamageAfterAbsorb(helper.makeMockPlayer(GameType.SURVIVAL), hit, source, armor, 0.0f);
        int shots = (int) Math.ceil(20.0f / dealt);
        return (shots - 1) * stats.shotIntervalTicks().getDefault() / 20.0;
    }

    @GameTest(template = TEMPLATE)
    public static void noWeaponKillsAPlayerInIronArmorWithinASecond(GameTestHelper helper) {
        for (String id : List.of("assault_rifle", "battle_rifle", "combat_shotgun")) {
            double seconds = secondsToKill(helper, stats(id), IRON_ARMOR_POINTS);
            helper.assertTrue(seconds >= 1.0, id + " kills in " + seconds + " s, all shots hitting");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theShotgunComesFirstAndRiflesNeedTitaniumAndProcessors(GameTestHelper helper) {
        var recipes = TechRecipes.recipesFor(helper.getLevel(), Station.WEAPON_WORKSHOP);
        ItemStack processor = new ItemStack(TechItems.PROCESSOR.get());
        ItemStack actuator = new ItemStack(TechItems.ACTUATOR.get());
        ItemStack titanium = new ItemStack(TechItems.INGOTS.get(Metal.TITANIUM).get());
        ItemStack gunpowder = new ItemStack(Items.GUNPOWDER);
        for (var holder : recipes) {
            ItemStack result = holder.value().result();
            var ingredients = holder.value().ingredients();
            boolean uses = ingredients.stream().anyMatch(ingredient -> ingredient.ingredient().test(processor));
            boolean usesTitanium = ingredients.stream().anyMatch(ingredient -> ingredient.ingredient().test(titanium));
            boolean usesActuator = ingredients.stream().anyMatch(ingredient -> ingredient.ingredient().test(actuator));
            if (result.is(WeaponRegistry.COMBAT_SHOTGUN.get())) {
                helper.assertFalse(uses || usesTitanium, "The shotgun is the early weapon");
            } else if (result.getItem() instanceof WeaponItem) {
                helper.assertTrue(uses && usesTitanium && usesActuator, "A rifle needs titanium, processors and actuators: " + result);
            } else {
                helper.assertTrue(ingredients.stream().anyMatch(ingredient -> ingredient.ingredient().test(gunpowder)), "Rounds need gunpowder: " + result);
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void roundsStayInTheMagazineWhenDroppedAndSaved(GameTestHelper helper) {
        ItemStack stack = new ItemStack(WeaponRegistry.ASSAULT_RIFLE.get());
        Weapons.setAmmo(stack, 17);
        ItemEntity dropped = new ItemEntity(helper.getLevel(), 0, 0, 0, stack.copy());
        helper.assertTrue(Weapons.ammoIn(dropped.getItem()) == 17, "Dropped");
        DynamicOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag saved = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        helper.assertTrue(Weapons.ammoIn(loaded) == 17, "Saved and loaded, as in a chest or after a restart");
        helper.assertTrue(Weapons.ammoIn(new ItemStack(WeaponRegistry.ASSAULT_RIFLE.get())) == 0, "A new weapon is empty");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void everyWeaponHasDataNumbersItsOwnAmmoAndARecipe(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        List<Supplier<WeaponItem>> weapons = List.of(WeaponRegistry.ASSAULT_RIFLE, WeaponRegistry.BATTLE_RIFLE, WeaponRegistry.COMBAT_SHOTGUN);
        var recipes = TechRecipes.recipesFor(helper.getLevel(), Station.WEAPON_WORKSHOP);
        java.util.Set<Item> ammo = new java.util.HashSet<>();
        for (Supplier<WeaponItem> weapon : weapons) {
            WeaponDefinition definition = Weapons.definition(access, weapon.get()).orElse(null);
            helper.assertTrue(definition != null, "No data for " + weapon.get());
            helper.assertTrue(RebirthConfig.weapon(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(weapon.get()).getPath()) != null, "No config for " + weapon.get());
            helper.assertTrue(ammo.add(definition.ammo().value()), "Each weapon has its own rounds");
            for (Item made : List.of(weapon.get(), definition.ammo().value())) {
                helper.assertTrue(recipes.stream().anyMatch(recipe -> recipe.value().result().is(made)), "No recipe for " + made);
            }
        }
        helper.succeed();
    }
}
