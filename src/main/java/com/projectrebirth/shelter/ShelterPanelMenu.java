package com.projectrebirth.shelter;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Window of the shelter control panel: numbers only, and the "Find the leak" button (button id 0). */
public class ShelterPanelMenu extends AbstractContainerMenu {
    public static final int BUTTON_FIND_LEAK = 0;

    private final BlockPos pos;
    private final ContainerData data;

    /** Client side: the numbers come from the server through data syncing. */
    public ShelterPanelMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, buf.readBlockPos(), new SimpleContainerData(ShelterControlPanelBlockEntity.DATA_COUNT));
    }

    ShelterPanelMenu(int containerId, BlockPos pos, ContainerData data) {
        super(ShelterRegistry.SHELTER_PANEL_MENU.get(), containerId);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);
    }

    public int get(int index) {
        return data.get(index);
    }

    /** A number of a room shown in the window. */
    public int room(int room, int field) {
        return data.get(ShelterControlPanelBlockEntity.DATA_ROOM_START + room * ShelterControlPanelBlockEntity.FIELDS_PER_ROOM + field);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_FIND_LEAK && player instanceof ServerPlayer serverPlayer
                && player.level().getBlockEntity(pos) instanceof ShelterControlPanelBlockEntity panel) {
            panel.findLeak(serverPlayer);
            serverPlayer.closeContainer();
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof ShelterControlPanelBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
