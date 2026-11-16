package com.projectrebirth.garrison;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** The console's window (1.7.0, block D): no slots; what it shows comes as {@link ConsoleView}, what is pressed goes back as actions. */
public class ConsoleMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    /** On the client: the last view the server sent. */
    @Nullable
    private ConsoleView view;

    public ConsoleMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos());
    }

    public ConsoleMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(GarrisonRegistry.CONSOLE_MENU.get(), containerId);
        this.pos = pos;
    }

    public BlockPos pos() {
        return pos;
    }

    @Nullable
    public ConsoleView view() {
        return view;
    }

    public void setView(ConsoleView view) {
        this.view = view;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof ConsoleBlockEntity console && console.mayCommand(player)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
