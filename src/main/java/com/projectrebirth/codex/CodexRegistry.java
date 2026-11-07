package com.projectrebirth.codex;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Version 1.4.1 "Codex": the in-game guide book, given once to every player. */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class CodexRegistry {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ProjectRebirth.MOD_ID);

    public static final DeferredItem<CodexItem> REBIRTH_CODEX = ITEMS.register("rebirth_codex",
            () -> new CodexItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** Whether this player has been given the book; saved, kept on death, so a dead player is not given another. */
    public static final Supplier<AttachmentType<Boolean>> CODEX_GIVEN = ATTACHMENTS.register("codex_given",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL, given -> given).copyOnDeath().build());

    private CodexRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
    }

    /** Gives the book once: into the inventory, or at the feet if it is full. Public for tests. */
    public static boolean giveOnce(Player player) {
        if (player.getData(CODEX_GIVEN)) {
            return false;
        }
        player.setData(CODEX_GIVEN, true);
        ItemStack book = new ItemStack(REBIRTH_CODEX.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        return true;
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        giveOnce(event.getEntity());
    }
}
