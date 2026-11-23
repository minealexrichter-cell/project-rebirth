package com.projectrebirth.wanderers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.core.RebirthGameRules;
import com.projectrebirth.environment.EnvironmentRegistry;
import com.projectrebirth.environment.Wasteland;
import com.projectrebirth.hostile.RemainsEntity;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 1.9.0, block E: groups of wanderers come and are made here. Every [wasteland.wanderers] spawnInterval a try near each
 * player may bring a group (by the game rule rebirthWanderers, not too many near the player nor in the dimension) 48–80
 * blocks away on dry ground outside faction territory; its make-up is one of the data {@code projectrebirth/wanderer_groups}
 * fitting the stage of the Wasteland, its gear random from it, its offers from {@code projectrebirth/wanderer_trades}.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class WandererGroups {
    /** Further creatures wanderers fight (the robots of the machine mind of later blocks). */
    public static final TagKey<EntityType<?>> FOES = TagKey.create(Registries.ENTITY_TYPE, ProjectRebirth.id("wanderer_foes"));

    private WandererGroups() {
    }

    public static boolean enabled(ServerLevel level) {
        return WastelandConfig.WANDERERS_ENABLED.get() && RebirthGameRules.on(level.getServer(), RebirthGameRules.WANDERERS);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !com.projectrebirth.config.RebirthConfig.SERVER_SPEC.isLoaded()) {
            return;
        }
        if ((player.tickCount + player.getId()) % WastelandConfig.WANDERERS_INTERVAL.get() != 0 || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!enabled(level) || level.dimension() != net.minecraft.world.level.Level.OVERWORLD
                || level.random.nextDouble() >= WastelandConfig.WANDERERS_CHANCE.get()) {
            return;
        }
        trySpawn(level, player.blockPosition(), level.random);
    }

    /** Heads of groups within the radius. */
    public static int groupsNear(ServerLevel level, BlockPos pos, double radius) {
        return level.getEntitiesOfClass(WandererEntity.class, new AABB(pos).inflate(radius), WandererEntity::isLeader).size();
    }

    public static int groupsInWorld(ServerLevel level) {
        int[] count = {0};
        level.getEntities(EntityTypeTest.forClass(WandererEntity.class), w -> {
            if (w.isLeader()) {
                count[0]++;
            }
            return false;
        });
        return count[0];
    }

    /** One try near a place; the head of the new group, or null. Public for tests. */
    @Nullable
    public static WandererEntity trySpawn(ServerLevel level, BlockPos around, RandomSource random) {
        if (groupsNear(level, around, WastelandConfig.WANDERERS_NEAR_RADIUS.get()) >= WastelandConfig.WANDERERS_MAX_NEAR.get()
                || groupsInWorld(level) >= WastelandConfig.WANDERERS_WORLD_CAP.get()) {
            return null;
        }
        WandererData.GroupSpec spec = pick(level, random);
        if (spec == null) {
            return null;
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0, distance = 48.0 + random.nextDouble() * 32.0;
            BlockPos column = around.offset(Mth.floor(Math.cos(angle) * distance), 0, Mth.floor(Math.sin(angle) * distance));
            if (!level.hasChunkAt(column)) {
                continue;
            }
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (fits(level, ground)) {
                return spawnGroup(level, spec, ground, random);
            }
        }
        return null;
    }

    private static boolean fits(ServerLevel level, BlockPos ground) {
        return level.getFluidState(ground).isEmpty() && level.getBlockState(ground.below()).isFaceSturdy(level, ground.below(), Direction.UP)
                && level.getFluidState(ground.below()).isEmpty()
                && com.projectrebirth.factions.FactionService.territoryAt(level.getServer(), level, ground).isEmpty();
    }

    @Nullable
    static WandererData.GroupSpec pick(ServerLevel level, RandomSource random) {
        int stage = Wasteland.stage(level);
        List<WandererData.GroupSpec> specs = level.registryAccess().registry(WandererData.GROUPS)
                .map(registry -> registry.stream().filter(spec -> spec.minStage() <= stage && spec.weight() > 0).toList()).orElse(List.of());
        int total = specs.stream().mapToInt(WandererData.GroupSpec::weight).sum();
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (WandererData.GroupSpec spec : specs) {
            roll -= spec.weight();
            if (roll < 0) {
                return spec;
            }
        }
        return null;
    }

    /** Makes a whole group at a place; returns its head. Public for tests. */
    public static WandererEntity spawnGroup(ServerLevel level, WandererData.GroupSpec spec, BlockPos at, RandomSource random) {
        UUID group = UUID.randomUUID();
        int stage = Wasteland.stage(level);
        WandererEntity head = null;
        for (WandererData.MemberSpec member : spec.members()) {
            int count = Mth.randomBetweenInclusive(random, Math.max(0, member.count().min()), Math.max(member.count().min(), member.count().max()));
            for (int i = 0; i < count; i++) {
                EntityType<WandererEntity> type = WandererRegistry.type(WandererEntity.Kind.byId(member.kind()));
                BlockPos place = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2));
                WandererEntity wanderer = type.create(level, null, place, MobSpawnType.EVENT, false, false);
                if (wanderer == null) {
                    continue;
                }
                wanderer.setGroupId(group);
                equip(wanderer, member, random);
                wanderer.scaleByStage(stage);
                if (head == null && member.leader()) {
                    head = wanderer;
                    wanderer.setLeader(true);
                }
                level.addFreshEntity(wanderer);
                if (head == null) {
                    head = wanderer;
                    wanderer.setLeader(true);
                }
            }
        }
        if (head != null) {
            rollOffers(level, head);
        }
        return head;
    }

    private static String one(List<String> options, RandomSource random, String otherwise) {
        return options.isEmpty() ? otherwise : options.get(random.nextInt(options.size()));
    }

    @Nullable
    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }

    /** Its look and gear by the member spec. */
    static void equip(WandererEntity wanderer, WandererData.MemberSpec spec, RandomSource random) {
        WandererEntity.Kind kind = WandererEntity.Kind.byId(spec.kind());
        switch (kind) {
            case ARMORED -> wanderer.setVariant(one(spec.variants(), random, ArmorVariant.COMBAT.id()));
            case ANDROID -> wanderer.setVariant(one(spec.variants(), random, BodyClass.BASIC_ROBOT.id()));
            case SURVIVOR -> wanderer.setVariant(one(spec.variants(), random, "a"));
            default -> wanderer.setVariant("");
        }
        Item weapon = spec.weapons().isEmpty() ? null : item(one(spec.weapons(), random, ""));
        if (weapon != null) {
            wanderer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        }
        Item offhand = spec.offhand().isEmpty() || random.nextBoolean() ? null : item(one(spec.offhand(), random, ""));
        if (offhand != null) {
            wanderer.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(offhand));
        }
        if (kind == WandererEntity.Kind.SURVIVOR) {
            switch (one(spec.kits(), random, "respirator")) {
                case "hazmat" -> wear(wanderer, EnvironmentRegistry.HAZMAT_HELMET.get(), EnvironmentRegistry.HAZMAT_SUIT.get(),
                        EnvironmentRegistry.HAZMAT_TROUSERS.get(), EnvironmentRegistry.HAZMAT_BOOTS.get());
                case "heavy_suit" -> wear(wanderer, EnvironmentRegistry.HEAVY_SUIT_HELMET.get(), EnvironmentRegistry.HEAVY_SUIT.get(),
                        EnvironmentRegistry.HEAVY_SUIT_TROUSERS.get(), EnvironmentRegistry.HEAVY_SUIT_BOOTS.get());
                default -> {
                    // a respirator and whatever armor was found
                    Item[] chests = {net.minecraft.world.item.Items.LEATHER_CHESTPLATE, net.minecraft.world.item.Items.CHAINMAIL_CHESTPLATE,
                            net.minecraft.world.item.Items.IRON_CHESTPLATE};
                    Item[] legs = {net.minecraft.world.item.Items.LEATHER_LEGGINGS, net.minecraft.world.item.Items.CHAINMAIL_LEGGINGS};
                    wear(wanderer, EnvironmentRegistry.RESPIRATOR.get(), chests[random.nextInt(chests.length)], legs[random.nextInt(legs.length)],
                            net.minecraft.world.item.Items.LEATHER_BOOTS);
                }
            }
        }
        float drop = WastelandConfig.WANDERERS_DROP_CHANCE.get().floatValue();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            wanderer.setDropChance(slot, kind == WandererEntity.Kind.SURVIVOR || slot.getType() == EquipmentSlot.Type.HAND ? drop : 0.0f);
        }
    }

    private static void wear(WandererEntity wanderer, Item head, Item chest, Item legs, Item feet) {
        wanderer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(head));
        wanderer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));
        wanderer.setItemSlot(EquipmentSlot.LEGS, new ItemStack(legs));
        wanderer.setItemSlot(EquipmentSlot.FEET, new ItemStack(feet));
    }

    /** New offers of a group's head: [wasteland.wanderers] offersMin to offersMax by weight, no offer twice. */
    public static void rollOffers(ServerLevel level, WandererEntity head) {
        int stage = Wasteland.stage(level);
        List<WandererData.Trade> pool = new ArrayList<>(level.registryAccess().registry(WandererData.TRADES)
                .map(registry -> registry.stream().filter(trade -> trade.minStage() <= stage && trade.weight() > 0).toList()).orElse(List.of()));
        int min = WastelandConfig.WANDERERS_OFFERS_MIN.get(), max = Math.max(min, WastelandConfig.WANDERERS_OFFERS_MAX.get());
        int count = Math.min(pool.size(), Mth.randomBetweenInclusive(level.random, min, max));
        head.offers().clear();
        for (int i = 0; i < count; i++) {
            int total = pool.stream().mapToInt(WandererData.Trade::weight).sum();
            int roll = level.random.nextInt(total);
            for (int j = 0; j < pool.size(); j++) {
                roll -= pool.get(j).weight();
                if (roll < 0) {
                    head.offers().add(pool.remove(j));
                    break;
                }
            }
        }
    }

    /** Where the head goes next: 100–200 blocks off, on dry ground outside faction territory; null if nothing fits. */
    @Nullable
    public static BlockPos nextDestination(ServerLevel level, BlockPos from, RandomSource random) {
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0, distance = 100.0 + random.nextDouble() * 100.0;
            BlockPos column = from.offset(Mth.floor(Math.cos(angle) * distance), 0, Mth.floor(Math.sin(angle) * distance));
            if (!level.hasChunkAt(column)) {
                // an unloaded place: walk toward it anyway, the ground is checked when it comes into reach
                return new BlockPos(column.getX(), from.getY(), column.getZ());
            }
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (fits(level, ground)) {
                return ground;
            }
        }
        return null;
    }

    /** What a fallen wanderer leaves: an armor its wreck (the wrench takes it apart), an android or a robot a wreck. */
    static void leaveRemains(WandererEntity wanderer) {
        switch (wanderer.kind()) {
            case ARMORED -> {
                PowerArmorEntity armor = new PowerArmorEntity(PowerArmorRegistry.POWER_ARMOR.get(), wanderer.level());
                armor.setVariant(ArmorVariant.byId(wanderer.variant()));
                armor.moveTo(wanderer.getX(), wanderer.getY(), wanderer.getZ(), wanderer.yBodyRot, 0.0f);
                armor.makeWreck();
                wanderer.level().addFreshEntity(armor);
            }
            case ANDROID -> RemainsEntity.leave(wanderer, RemainsEntity.Kind.forBody(BodyClass.byId(wanderer.variant())));
            case ROBOT -> RemainsEntity.leave(wanderer, RemainsEntity.Kind.WANDERER_ROBOT);
            default -> {
            }
        }
    }
}
