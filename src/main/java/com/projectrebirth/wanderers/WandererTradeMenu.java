package com.projectrebirth.wanderers;

import java.util.ArrayList;
import java.util.List;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 1.9.0, block E: the trade window of a group of wanderers. The offers come with the window (they do not change while it is
 * open); a button trades one of them: the cost is taken from the player's inventory, the goods given (dropped at his feet if
 * there is no room). The window is made so that later versions can add other things to do with a group (errands, hire, news):
 * the trade is one "action" of the menu ({@link #clickMenuButton}).
 */
public class WandererTradeMenu extends AbstractContainerMenu {
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 150;
    /** Buttons from 0: trade the offer of this index. */
    public static final int ACTION_TRADE = 0;

    private final int wandererId;
    private final List<WandererData.Trade> offers;

    public WandererTradeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readVarInt(), read(buf));
    }

    public WandererTradeMenu(int id, Inventory inventory, int wandererId, List<WandererData.Trade> offers) {
        super(WandererRegistry.TRADE_MENU.get(), id);
        this.wandererId = wandererId;
        this.offers = offers;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    private static List<WandererData.Trade> read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<WandererData.Trade> offers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int costs = buf.readVarInt();
            List<ItemStack> cost = new ArrayList<>();
            for (int j = 0; j < costs; j++) {
                cost.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            }
            offers.add(new WandererData.Trade(cost, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), 1, 1));
        }
        return offers;
    }

    private static void write(RegistryFriendlyByteBuf buf, int id, List<WandererData.Trade> offers) {
        buf.writeVarInt(id);
        buf.writeVarInt(offers.size());
        for (WandererData.Trade offer : offers) {
            buf.writeVarInt(offer.cost().size());
            for (ItemStack stack : offer.cost()) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
            }
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, offer.result());
        }
    }

    /** Opens the group's trade for a player. */
    public static void open(ServerPlayer player, WandererEntity head) {
        List<WandererData.Trade> offers = List.copyOf(head.offers());
        player.openMenu(new SimpleMenuProvider((id, inventory, who) -> new WandererTradeMenu(id, inventory, head.getId(), offers),
                Component.translatable("screen.projectrebirth.wanderer_trade.title")), buf -> write(buf, head.getId(), offers));
    }

    public List<WandererData.Trade> offers() {
        return offers;
    }

    /** Whether the player has all of an offer's cost. */
    public static boolean canAfford(Player player, WandererData.Trade offer) {
        for (ItemStack cost : offer.cost()) {
            if (count(player, cost) < cost.getCount()) {
                return false;
            }
        }
        return true;
    }

    private static int count(Player player, ItemStack wanted) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItemSameComponents(stack, wanted)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** One trade of an offer. Public for tests; true if it went through. */
    public static boolean trade(Player player, WandererData.Trade offer) {
        if (!canAfford(player, offer)) {
            return false;
        }
        for (ItemStack cost : offer.cost()) {
            int left = cost.getCount();
            for (ItemStack stack : player.getInventory().items) {
                if (left > 0 && ItemStack.isSameItemSameComponents(stack, cost)) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
        ItemStack goods = offer.result().copy();
        if (!player.getInventory().add(goods)) {
            player.drop(goods, false);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.6f, 0.8f);
        return true;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        int index = id - ACTION_TRADE;
        if (index < 0 || index >= offers.size()) {
            return false;
        }
        boolean done = trade(player, offers.get(index));
        if (!done) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.wanderers.cannot_afford"), true);
        }
        return done;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        var entity = player.level().getEntity(wandererId);
        return entity instanceof WandererEntity wanderer && wanderer.isAlive() && wanderer.distanceToSqr(player) < 64.0 && !wanderer.hostileTo(player);
    }

    static String key(String path) {
        return ProjectRebirth.MOD_ID + "." + path;
    }
}
