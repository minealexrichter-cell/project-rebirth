package com.projectrebirth.machinemind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.core.ModSounds;
import com.projectrebirth.core.RebirthGameRules;
import com.projectrebirth.environment.Wasteland;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 1.9.0, block F: the central computer of a node of the machine mind. It wakes when its chunk is first ticked (if the world has room
 * for one more node), grows through the stages, has its drones build the base of each stage by the plan of the data and put back
 * what is broken, and has the factory make its machines. While its chunk is not loaded nothing runs; the time is made up for when
 * it is loaded again. Two scales of strength, as the machines have: first the structure, then the core.
 */
public class MindComputerBlockEntity extends MindModuleBlockEntity {
    /** A gap in the game time longer than this is made up for. */
    private static final long GAP = 200L;
    /** Ticks a block of the plan takes while nobody sees it built. */
    private static final long CATCH_UP_BLOCK = 60L;
    private static final long DAY = 24000L;
    /** Ticks after damage in which the node does not grow. */
    private static final long ALARM = 1200L;
    private static final int BASE_REACH = 20;
    private static final int UNIT_RANGE = 96;

    private UUID nodeId = UUID.randomUUID();
    private boolean awake;
    private boolean asked;
    private boolean forced;
    private int stage = 1;
    private long grown;
    private long delay;
    private int resources = -1;
    private float structure = -1.0f;
    private float core = -1.0f;
    private long lastTime = -1L;
    private final Map<EntityType<?>, Long> madeAt = new HashMap<>();

    private List<MindBase.Cell> plan = List.of();
    private int plannedStage;
    private Direction plannedFacing;
    private int cursor;
    private final Map<BlockPos, MindBase.Cell> missing = new LinkedHashMap<>();
    /** Places a drone is on its way to, with the time the claim ends. */
    private final Map<BlockPos, Long> claims = new HashMap<>();
    private final ServerBossEvent structureBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
    private final ServerBossEvent coreBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private int shownStage = 1;

    public MindComputerBlockEntity(BlockPos pos, BlockState state) {
        super(MindRegistry.COMPUTER_ENTITY.get(), pos, state);
    }

    public static boolean enabled(ServerLevel level) {
        return WastelandConfig.MIND_ENABLED.get() && RebirthGameRules.on(level.getServer(), RebirthGameRules.MACHINE_MIND);
    }

    // ------------------------------------------------------------------ state

    public UUID nodeId() {
        return nodeId;
    }

    public int stage() {
        return stage;
    }

    public int resources() {
        return resources < 0 ? WastelandConfig.MIND_START_RESOURCES.get() : resources;
    }

    public void addResources(int amount) {
        resources = Mth.clamp(resources() + amount, 0, WastelandConfig.MIND_MAX_RESOURCES.get());
        setChanged();
    }

    @Override
    public int tier() {
        return MindModule.tier(level != null && level.isClientSide ? shownStage : stage);
    }

    /** Awake, its systems on in this world. */
    @Override
    public boolean active() {
        return awake && level instanceof ServerLevel server && enabled(server);
    }

    @Override
    public MindComputerBlockEntity computer() {
        return this;
    }

    public float maxStructure() {
        return (float) (WastelandConfig.MIND_STRUCTURE.get() * (1.0 + 0.5 * (stage - 1)));
    }

    public float maxCore() {
        return (float) (WastelandConfig.MIND_CORE.get() * (1.0 + 0.5 * (stage - 1)));
    }

    public float structure() {
        return structure < 0.0f ? maxStructure() : structure;
    }

    public float core() {
        return core < 0.0f ? maxCore() : core;
    }

    public List<MindBase.Cell> plan(ServerLevel server) {
        Direction facing = getBlockState().getValue(MindModuleBlock.FACING);
        if (plannedStage != stage || plannedFacing != facing) {
            plan = MindBase.plan(server, worldPosition, facing, stage);
            plannedStage = stage;
            plannedFacing = facing;
            cursor = 0;
            missing.clear();
        }
        return plan;
    }

    /** Wakes it whatever the limit of nodes is (the command, tests). */
    public void forceAwake() {
        forced = true;
        asked = false;
        setChanged();
    }

    public long grown() {
        return grown;
    }

    /** How long a stage takes now, with what the damage added. */
    public long needed() {
        return Math.round(WastelandConfig.MIND_STAGE_DAYS.get() * DAY) + delay;
    }

    public static int cap(ServerLevel server) {
        int wasteland = Wasteland.stage(server) + WastelandConfig.MIND_AHEAD.get();
        return Math.min(wasteland, RebirthGameRules.machineMindMaxStage(server.getServer()));
    }

    public void delay(long ticks) {
        delay = Math.min(delay + ticks, 20L * DAY);
        setChanged();
    }

    public void noteDamage(ServerLevel server) {
        lastDamage = server.getGameTime();
    }

    // ------------------------------------------------------------------ the second

    @Override
    public void serverTick(ServerLevel server, BlockPos pos) {
        if (Math.floorMod(server.getGameTime() + pos.asLong(), 20L) != 0L) {
            return;
        }
        if (!asked) {
            asked = true;
            awake = MindNodes.get(server.getServer()).wake(nodeId, server, pos, stage, WastelandConfig.MIND_MAX_NODES.get(), forced);
            setChanged();
        }
        if (awake && MindNodes.get(server.getServer()).byId(nodeId) == null) {
            // the list was not saved with the chunk (a crash): it enters itself again
            MindNodes.get(server.getServer()).wake(nodeId, server, pos, stage, 0, true);
        }
        long now = server.getGameTime();
        if (active()) {
            if (lastTime >= 0L && now - lastTime > GAP) {
                // the land of the whole base must be there before the time is made up for
                if (!server.hasChunksAt(pos.offset(-BASE_REACH, 0, -BASE_REACH), pos.offset(BASE_REACH, 0, BASE_REACH))) {
                    return;
                }
                catchUp(server, now - lastTime);
            }
            lastTime = now;
            second(server, now);
        } else {
            lastTime = now;
        }
        bars(server);
        show(server);
    }

    private void second(ServerLevel server, long now) {
        fillBox(server);
        look(server, 48);
        claims.values().removeIf(until -> until < now);
        boolean calm = now - lastDamage > CALM;
        if (calm && (structure() < maxStructure() || core() < maxCore())) {
            structure = Math.min(maxStructure(), structure() + WastelandConfig.MIND_REPAIR.get().floatValue());
            core = Math.min(maxCore(), core() + WastelandConfig.MIND_REPAIR.get().floatValue() * 0.25f);
            setChanged();
        }
        if (now - lastDamage > ALARM && stage < cap(server)) {
            // it grows with most of the base of this stage standing
            if (missing.size() <= plan(server).size() / 10) {
                grown += 20L;
                setChanged();
            }
            if (grown >= needed()) {
                setStage(server, stage + 1);
            }
        }
        int income = WastelandConfig.MIND_INCOME.get();
        if (income > 0 && now / 20L % Math.max(1, 60 / income) == 0L) {
            addResources(Math.max(1, income / 60));
        }
        make(server, now);
        if (now % 100L < 20L) {
            server.playSound(null, worldPosition, ModSounds.MIND_HUM.get(), SoundSource.HOSTILE, 0.6f, 1.0f);
        }
    }

    /** Looks over a part of the plan: what is missing, whose modules are whose. */
    private void look(ServerLevel server, int cells) {
        List<MindBase.Cell> all = plan(server);
        for (int i = 0; i < Math.min(cells, all.size()); i++) {
            cursor = (cursor + 1) % all.size();
            MindBase.Cell cell = all.get(cursor);
            if (!server.isLoaded(cell.pos())) {
                continue;
            }
            if (MindBase.built(server, cell)) {
                missing.remove(cell.pos());
                if (server.getBlockEntity(cell.pos()) instanceof MindModuleBlockEntity module) {
                    module.setComputer(worldPosition);
                }
            } else if (MindBase.canBuild(server, cell)) {
                missing.put(cell.pos(), cell);
            } else {
                missing.remove(cell.pos());
            }
        }
    }

    /** Looks the whole plan over at once (tests, the making up of time). */
    public int lookAll(ServerLevel server) {
        look(server, plan(server).size());
        return missing.size();
    }

    public void setStage(ServerLevel server, int next) {
        int old = stage;
        float structureShare = structure() / maxStructure(), coreShare = core() / maxCore();
        stage = Mth.clamp(next, 1, 5);
        structure = maxStructure() * structureShare;
        core = maxCore() * coreShare;
        grown = 0L;
        delay = 0L;
        MindNodes.get(server.getServer()).update(nodeId, stage, true);
        lookAll(server);
        sync();
        if (stage > old) {
            server.playSound(null, worldPosition, ModSounds.MIND_STAGE.get(), SoundSource.HOSTILE, 4.0f, 1.0f);
            for (ServerPlayer player : server.players()) {
                if (player.blockPosition().closerThan(worldPosition, 160.0)) {
                    player.displayClientMessage(Component.translatable("message.projectrebirth.mind.stage", roman(stage), stageName(stage)), false);
                }
            }
        }
    }

    public static Component stageName(int stage) {
        return Component.translatable("mind.projectrebirth.stage." + Mth.clamp(stage, 1, 5));
    }

    public static String roman(int stage) {
        return switch (stage) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "I";
        };
    }

    // ------------------------------------------------------------------ time made up for

    /** What the node did while nobody was there: it grew, its drones built, the stores filled. Public for tests. */
    public void catchUp(ServerLevel server, long ticks) {
        long left = Math.min(ticks, 200L * DAY);
        addResources((int) Math.min(WastelandConfig.MIND_MAX_RESOURCES.get(), left / 100L));
        while (true) {
            lookAll(server);
            int blocks = (int) Math.min(missing.size(), left / CATCH_UP_BLOCK);
            List<MindBase.Cell> now = new ArrayList<>(missing.values());
            for (int i = 0; i < blocks; i++) {
                if (MindBase.build(server, now.get(i), worldPosition)) {
                    missing.remove(now.get(i).pos());
                }
            }
            left -= blocks * CATCH_UP_BLOCK;
            if (stage >= cap(server) || missing.size() > plan(server).size() / 10) {
                break;
            }
            long need = needed() - grown;
            if (left < need) {
                grown += left;
                break;
            }
            left -= need;
            setStage(server, stage + 1);
        }
        // its machines, within their limits
        for (MindStage.Production production : productions(server)) {
            int count = units(server, production.entity());
            int more = (int) Math.min(Math.max(0, production.cap() - count), ticks / (production.seconds() * 20L));
            for (int i = 0; i < more; i++) {
                if (hasModule(server, production.module()) == null) {
                    break;
                }
                spawn(server, production);
            }
        }
        setChanged();
    }

    // ------------------------------------------------------------------ building

    /** The next block for a drone to put, claimed for it; null: nothing to build or nothing to build it of. */
    @Nullable
    public MindBase.Cell takeJob(ServerLevel server, Vec3 from) {
        if (resources() < WastelandConfig.MIND_BLOCK_COST.get()) {
            return null;
        }
        MindBase.Cell best = null;
        int seen = 0;
        for (MindBase.Cell cell : missing.values()) {
            if (claims.containsKey(cell.pos())) {
                continue;
            }
            // low blocks first; among those the nearest of a few
            if (best != null && (cell.pos().getY() > best.pos().getY() || ++seen > 24)) {
                break;
            }
            if (best == null || cell.pos().distToCenterSqr(from) < best.pos().distToCenterSqr(from)) {
                best = cell;
            }
        }
        if (best != null) {
            claims.put(best.pos(), server.getGameTime() + 600L);
        }
        return best;
    }

    /** A drone is at the place: the block is put, for its price. */
    public boolean finishJob(ServerLevel server, MindBase.Cell cell) {
        claims.remove(cell.pos());
        if (resources() < WastelandConfig.MIND_BLOCK_COST.get() || !MindBase.build(server, cell, worldPosition)) {
            missing.remove(cell.pos());
            return false;
        }
        missing.remove(cell.pos());
        addResources(-WastelandConfig.MIND_BLOCK_COST.get());
        server.levelEvent(2001, cell.pos(), Block.getId(cell.state()));
        return true;
    }

    public int missing() {
        return missing.size();
    }

    // ------------------------------------------------------------------ making machines

    /** What it may make now: a later stage's entry of a kind takes the place of the earlier. */
    public List<MindStage.Production> productions(ServerLevel server) {
        Map<EntityType<?>, MindStage.Production> out = new LinkedHashMap<>();
        for (MindStage data : MindStage.upTo(server.registryAccess(), stage)) {
            for (MindStage.Production production : data.robots()) {
                out.put(production.entity(), production);
            }
        }
        return List.copyOf(out.values());
    }

    public int units(ServerLevel server, EntityType<?> type) {
        return server.getEntities((Entity) null, new AABB(worldPosition).inflate(UNIT_RANGE),
                e -> e.getType() == type && e.isAlive() && e instanceof MindUnit unit && nodeId.equals(unit.node())).size();
    }

    @Nullable
    private BlockPos hasModule(ServerLevel server, Block block) {
        for (MindBase.Cell cell : plan(server)) {
            if (cell.state().is(block) && server.isLoaded(cell.pos()) && server.getBlockState(cell.pos()).is(block)) {
                return cell.pos();
            }
        }
        return null;
    }

    private void make(ServerLevel server, long now) {
        if (now % 100L >= 20L) {
            return;
        }
        for (MindStage.Production production : productions(server)) {
            long last = madeAt.getOrDefault(production.entity(), 0L);
            if (now - last < production.seconds() * 20L || units(server, production.entity()) >= production.cap()) {
                continue;
            }
            BlockPos module = hasModule(server, production.module());
            // without its factory the computer itself still makes a drone now and then, to build the factory again
            boolean emergency = module == null && production.entity() == MindRegistry.DRONE.get() && now - last > production.seconds() * 100L;
            if (module == null && !emergency || resources() < production.cost() && units(server, production.entity()) > 0) {
                continue;
            }
            madeAt.put(production.entity(), now);
            addResources(-production.cost());
            if (module != null && server.getBlockEntity(module) instanceof MindModuleBlockEntity entity) {
                entity.work(server, 160);
            }
            spawn(server, production);
            break;
        }
    }

    @Nullable
    private Entity spawn(ServerLevel server, MindStage.Production production) {
        BlockPos module = hasModule(server, production.module());
        BlockPos from = module == null ? worldPosition : module;
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos at = from.offset(server.random.nextInt(7) - 3, 1 + server.random.nextInt(3), server.random.nextInt(7) - 3);
            AABB box = production.entity().getDimensions().makeBoundingBox(Vec3.atBottomCenterOf(at));
            if (!server.isLoaded(at) || !server.noCollision(box)) {
                continue;
            }
            Entity entity = production.entity().create(server, null, at, MobSpawnType.EVENT, false, false);
            if (entity == null) {
                return null;
            }
            if (entity instanceof MindUnit unit) {
                unit.setNode(nodeId);
            }
            if (entity instanceof MindDroneEntity drone) {
                drone.setComputer(worldPosition);
            }
            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
            }
            server.addFreshEntity(entity);
            return entity;
        }
        return null;
    }

    // ------------------------------------------------------------------ damage and the end

    @Override
    public void damage(ServerLevel server, float amount) {
        lastDamage = server.getGameTime();
        float over = Math.max(0.0f, amount - structure());
        structure = Math.max(0.0f, structure() - amount);
        if (over > 0.0f) {
            core = Math.max(0.0f, core() - over);
        }
        setChanged();
        hurtEffects(server);
        if (core() <= 0.0f) {
            destroy(server);
        }
    }

    /** The end of the node: its machines go dead, the computer becomes a wreck, the base a ruin to take apart. */
    public void destroy(ServerLevel server) {
        BlockPos pos = worldPosition;
        burst(server, pos, 2.5);
        server.playSound(null, pos, ModSounds.MIND_DEATH.get(), SoundSource.HOSTILE, 5.0f, 1.0f);
        dropLoot(server, pos, "mind_computer_" + stage);
        Direction facing = getBlockState().getValue(MindModuleBlock.FACING);
        end(server);
        server.setBlockAndUpdate(pos, MindRegistry.WRECK.get().defaultBlockState().setValue(MindModuleBlock.FACING, facing));
        for (ServerPlayer player : server.players()) {
            if (player.blockPosition().closerThan(pos, 160.0)) {
                player.displayClientMessage(Component.translatable("message.projectrebirth.mind.destroyed"), false);
            }
        }
    }

    private void end(ServerLevel server) {
        if (!awake) {
            return;
        }
        awake = false;
        MindNodes.get(server.getServer()).update(nodeId, stage, false);
        structureBar.removeAllPlayers();
        coreBar.removeAllPlayers();
        for (Entity entity : server.getEntities((Entity) null, new AABB(worldPosition).inflate(UNIT_RANGE), e -> e instanceof MindUnit unit && nodeId.equals(unit.node()))) {
            ((MindUnit) entity).shutDown();
        }
    }

    @Override
    public void removed(ServerLevel server) {
        end(server);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        structureBar.removeAllPlayers();
        coreBar.removeAllPlayers();
    }

    static void burst(ServerLevel server, BlockPos pos, double size) {
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, size, size, size, 0.05);
        server.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.8f);
    }

    /** Drops of a destroyed module: the loot table entities/&lt;name&gt;. */
    static void dropLoot(ServerLevel server, BlockPos pos, String name) {
        ResourceLocation id = ProjectRebirth.id("entities/" + name);
        var table = server.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
        LootParams params = new LootParams.Builder(server).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).create(LootContextParamSets.CHEST);
        for (ItemStack drop : table.getRandomItems(params)) {
            Block.popResource(server, pos.above(), drop);
        }
    }

    // ------------------------------------------------------------------ what players see

    /** The two bars of its strength, for players at the node. */
    private void bars(ServerLevel server) {
        Set<ServerPlayer> near = new HashSet<>();
        if (active()) {
            for (ServerPlayer player : server.players()) {
                if (player.blockPosition().closerThan(worldPosition, 32.0) && !player.isSpectator()) {
                    near.add(player);
                }
            }
        }
        structureBar.setName(Component.translatable("mind.projectrebirth.bar.structure", roman(stage), stageName(stage)));
        coreBar.setName(Component.translatable("mind.projectrebirth.bar.core"));
        structureBar.setProgress(Mth.clamp(structure() / maxStructure(), 0.0f, 1.0f));
        coreBar.setProgress(Mth.clamp(core() / maxCore(), 0.0f, 1.0f));
        for (ServerBossEvent bar : new ServerBossEvent[] {structureBar, coreBar}) {
            for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
                if (!near.contains(player)) {
                    bar.removePlayer(player);
                }
            }
            near.forEach(bar::addPlayer);
        }
    }

    @Override
    public void clientTick(Level level, BlockPos pos) {
        super.clientTick(level, pos);
        if (shownActive() && level.random.nextInt(10) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + level.random.nextDouble(), pos.getY() + level.random.nextDouble() * (1 + tier()),
                    pos.getZ() + level.random.nextDouble(), 0.0, 0.02, 0.0);
        }
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("node", nodeId);
        tag.putBoolean("awake", awake);
        tag.putBoolean("asked", asked);
        tag.putBoolean("forced", forced);
        tag.putInt("stage", stage);
        tag.putLong("grown", grown);
        tag.putLong("delay", delay);
        tag.putInt("resources", resources);
        tag.putFloat("structure", structure);
        tag.putFloat("core", core);
        tag.putLong("last_time", lastTime);
        CompoundTag made = new CompoundTag();
        madeAt.forEach((type, at) -> made.putLong(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), at));
        tag.put("made_at", made);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("shown_stage")) {
            shownStage = tag.getInt("shown_stage");
            return;
        }
        if (tag.hasUUID("node")) {
            nodeId = tag.getUUID("node");
            awake = tag.getBoolean("awake");
            asked = tag.getBoolean("asked");
            forced = tag.getBoolean("forced");
            stage = Mth.clamp(tag.getInt("stage"), 1, 5);
            grown = tag.getLong("grown");
            delay = tag.getLong("delay");
            resources = tag.getInt("resources");
            structure = tag.getFloat("structure");
            core = tag.getFloat("core");
            lastTime = tag.getLong("last_time");
            CompoundTag made = tag.getCompound("made_at");
            for (String key : made.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null) {
                    BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(type -> madeAt.put(type, made.getLong(key)));
                }
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("shown_stage", stage);
        return tag;
    }
}
