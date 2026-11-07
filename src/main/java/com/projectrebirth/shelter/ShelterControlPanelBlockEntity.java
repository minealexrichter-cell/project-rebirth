package com.projectrebirth.shelter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.energy.BackupBatteryBlockEntity;
import com.projectrebirth.technology.energy.Cables;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * The shelter control panel. It watches the rooms of the air filtration units on the cable next to it (and the room it
 * stands in) and the backup batteries on that cable: whether they are sealed, their air, cartridges, energy, reserve. It
 * puts out a redstone signal of the danger, so players can build their own sirens, and shows where air gets out.
 */
public class ShelterControlPanelBlockEntity extends BlockEntity implements MenuProvider {
    public static final int ROOMS_SHOWN = 4;
    public static final int FIELDS_PER_ROOM = 6;
    public static final int DATA_DANGER = 0;
    public static final int DATA_ROOMS = 1;
    /** Charge of the backup batteries, per mille; -1 without any. */
    public static final int DATA_BACKUP = 2;
    public static final int DATA_RESERVE = 3;
    public static final int DATA_ROOM_START = 4;
    public static final int DATA_COUNT = DATA_ROOM_START + ROOMS_SHOWN * FIELDS_PER_ROOM;

    /** Per room: 1 sealed, 2 leak to the sky, 3 leak to an unloaded area, 4 too large. */
    public static final int ROOM_STATE = 0;
    public static final int ROOM_VOLUME = 1;
    /** Pollution times 100. */
    public static final int ROOM_POLLUTION = 2;
    public static final int ROOM_UNITS = 3;
    /** The emptiest cartridge in use, per mille. */
    public static final int ROOM_CARTRIDGE = 4;
    /** Bit 1: a unit lacks energy; bit 2: a unit lacks a cartridge. */
    public static final int ROOM_FLAGS = 5;
    public static final int FLAG_NO_ENERGY = 1;
    public static final int FLAG_NO_CARTRIDGE = 2;

    public static final int DANGER_LEAK = 15;
    public static final int DANGER_NO_SUPPLY = 12;
    public static final int DANGER_RESERVE = 8;
    public static final int DANGER_POLLUTED = 6;
    public static final int DANGER_CARTRIDGE_LOW = 4;

    private static final int HIGHLIGHT_TICKS = 100;
    private static final DustParticleOptions LEAK_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.1f), 1.5f);

    private final int[] values = new int[DATA_COUNT];
    private int danger;
    private final List<BlockPos> leaks = new ArrayList<>();
    private UUID highlightFor;
    private long highlightUntil;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index >= 0 && index < values.length ? values[index] : 0;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ShelterControlPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ShelterRegistry.SHELTER_CONTROL_PANEL_ENTITY.get(), pos, state);
    }

    /** The redstone signal: 0 when all is well, 15 at a leak. */
    public int danger() {
        return danger;
    }

    public int value(int index) {
        return values[index];
    }

    /** The rooms watched now. Public for tests. */
    public List<Room> rooms() {
        if (!(level instanceof ServerLevel server)) {
            return List.of();
        }
        Shelters shelters = Shelters.find(server);
        if (shelters == null) {
            return List.of();
        }
        Set<BlockPos> units = new LinkedHashSet<>();
        List<BlockPos> near = Cables.machinesNear(server, worldPosition);
        for (BlockPos pos : near) {
            if (server.getBlockEntity(pos) instanceof AirFiltrationUnitBlockEntity) {
                units.add(pos);
            }
        }
        List<Room> rooms = new ArrayList<>(shelters.roomsOf(units));
        for (Direction side : Direction.values()) {
            Room around = shelters.roomAt(worldPosition.relative(side));
            if (around != null && rooms.stream().noneMatch(room -> room == around)) {
                rooms.add(around);
            }
        }
        return rooms;
    }

    private List<BackupBatteryBlockEntity> backups(ServerLevel server) {
        List<BackupBatteryBlockEntity> found = new ArrayList<>();
        for (BlockPos pos : Cables.machinesNear(server, worldPosition)) {
            if (server.getBlockEntity(pos) instanceof BackupBatteryBlockEntity battery) {
                found.add(battery);
            }
        }
        return found;
    }

    /** Looks at the rooms once a second; public for tests. */
    public void update() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        java.util.Arrays.fill(values, 0);
        leaks.clear();
        int worst = 0;
        List<Room> rooms = rooms();
        values[DATA_ROOMS] = rooms.size();
        for (int i = 0; i < rooms.size(); i++) {
            Room room = rooms.get(i);
            int state = switch (room.leak()) {
                case NONE -> 1;
                case SKY -> 2;
                case UNLOADED -> 3;
                case TOO_LARGE -> 4;
            };
            int flags = 0;
            float cartridge = 1.0f;
            boolean low = false;
            for (var it = room.units().iterator(); it.hasNext(); ) {
                BlockPos pos = BlockPos.of(it.nextLong());
                if (server.isLoaded(pos) && server.getBlockEntity(pos) instanceof AirFiltrationUnitBlockEntity unit) {
                    int unitState = unit.state();
                    if (unitState == AirFiltrationUnitBlockEntity.STATE_NO_ENERGY) {
                        flags |= FLAG_NO_ENERGY;
                    }
                    if (unitState == AirFiltrationUnitBlockEntity.STATE_NO_CARTRIDGE || unitState == AirFiltrationUnitBlockEntity.STATE_SPENT_FULL) {
                        flags |= FLAG_NO_CARTRIDGE;
                    }
                    cartridge = Math.min(cartridge, CartridgeItem.lifeShare(unit.items().getStackInSlot(AirFiltrationUnitBlockEntity.ACTIVE_SLOT)));
                    low |= unit.cartridgeLow();
                }
            }
            if (!room.isSealed()) {
                leaks.add(room.leakPos());
                worst = Math.max(worst, DANGER_LEAK);
            } else {
                if (flags != 0) {
                    worst = Math.max(worst, DANGER_NO_SUPPLY);
                }
                if (room.pollution() > RebirthConfig.HUD_DANGER_POLLUTION.get()) {
                    worst = Math.max(worst, DANGER_POLLUTED);
                }
                if (low) {
                    worst = Math.max(worst, DANGER_CARTRIDGE_LOW);
                }
            }
            if (i < ROOMS_SHOWN) {
                int base = DATA_ROOM_START + i * FIELDS_PER_ROOM;
                values[base + ROOM_STATE] = state;
                values[base + ROOM_VOLUME] = Math.min(Short.MAX_VALUE, room.volume()); // menu data is 16 bits
                values[base + ROOM_POLLUTION] = Math.min(Short.MAX_VALUE, Math.round(room.pollution() * 100.0f));
                values[base + ROOM_UNITS] = room.units().size();
                values[base + ROOM_CARTRIDGE] = Math.round(cartridge * 1000.0f);
                values[base + ROOM_FLAGS] = flags;
            }
        }
        List<BackupBatteryBlockEntity> backups = backups(server);
        if (backups.isEmpty()) {
            values[DATA_BACKUP] = -1;
        } else {
            long stored = 0;
            long capacity = 0;
            for (BackupBatteryBlockEntity battery : backups) {
                stored += battery.energy().getEnergyStored();
                capacity += battery.energy().getMaxEnergyStored();
                if (battery.isOnReserve()) {
                    values[DATA_RESERVE] = 1;
                    worst = Math.max(worst, DANGER_RESERVE);
                }
            }
            values[DATA_BACKUP] = capacity <= 0 ? 0 : (int) (stored * 1000L / capacity);
        }
        values[DATA_DANGER] = worst;
        if (worst != danger) {
            danger = worst;
            server.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            server.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (server.getGameTime() % 20 == 0) {
            update();
        }
        if (highlightFor != null && server.getGameTime() % 5 == 0) {
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(highlightFor);
            if (player == null || server.getGameTime() > highlightUntil) {
                highlightFor = null;
                return;
            }
            for (BlockPos leak : leaks) {
                server.sendParticles(player, LEAK_DUST, true, leak.getX() + 0.5, leak.getY() + 0.5, leak.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.0);
            }
        }
    }

    /** The "Find the leak" button: marks where air gets out, for this player, for a few seconds. */
    public void findLeak(ServerPlayer player) {
        update();
        if (leaks.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.panel.no_leak"), false);
            return;
        }
        highlightFor = player.getUUID();
        highlightUntil = level.getGameTime() + HIGHLIGHT_TICKS;
        for (BlockPos leak : leaks) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.panel.leak_at", leak.getX(), leak.getY(), leak.getZ()), false);
        }
    }

    public List<BlockPos> leaks() {
        return leaks;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        update();
        return new ShelterPanelMenu(containerId, worldPosition, data);
    }
}
