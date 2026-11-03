package com.projectrebirth.factions;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Faction network messages. Server to client: the full faction list (small) after every change and on login,
 * and the result of the player's last request. Client to server: requests from the faction window, which the
 * server checks in {@link FactionService}.
 */
public final class FactionSync {
    private FactionSync() {
    }

    /** Full state for one player: all factions plus the invitations addressed to this player. */
    public record StatePayload(List<FactionView> factions, List<UUID> invites, boolean friendlyFireLocked, int coreRadius,
                               long serverTime, long warPreparation)
            implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE = new Type<>(ProjectRebirth.id("faction_state"));
        public static final StreamCodec<ByteBuf, StatePayload> STREAM_CODEC = StreamCodec.composite(
                FactionView.STREAM_CODEC.apply(ByteBufCodecs.list()), StatePayload::factions,
                UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), StatePayload::invites,
                ByteBufCodecs.BOOL, StatePayload::friendlyFireLocked,
                ByteBufCodecs.VAR_INT, StatePayload::coreRadius,
                ByteBufCodecs.VAR_LONG, StatePayload::serverTime,
                ByteBufCodecs.VAR_LONG, StatePayload::warPreparation,
                StatePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ResultPayload(Component message, boolean success) implements CustomPacketPayload {
        public static final Type<ResultPayload> TYPE = new Type<>(ProjectRebirth.id("faction_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResultPayload> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC, ResultPayload::message,
                ByteBufCodecs.BOOL, ResultPayload::success,
                ResultPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * What the window asks for. {@code arg} is a name, a player or faction UUID or "true"/"false";
     * {@code color} carries a colour id (SET_COLOR, CREATE) or a role id (SET_ROLE).
     */
    public enum Action {
        CREATE, INVITE, ACCEPT, DECLINE, LEAVE, KICK, PROMOTE, SET_COLOR, SET_FRIENDLY_FIRE, SET_ROLE,
        PROPOSE_ALLIANCE, ACCEPT_ALLIANCE, BREAK_ALLIANCE, DECLARE_WAR, OFFER_PEACE
    }

    public record ActionPayload(Action action, String arg, int color) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(ProjectRebirth.id("faction_action"));
        public static final StreamCodec<ByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), ActionPayload::action,
                ByteBufCodecs.stringUtf8(64), ActionPayload::arg,
                ByteBufCodecs.VAR_INT, ActionPayload::color,
                ActionPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(FactionSync::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(StatePayload.TYPE, StatePayload.STREAM_CODEC, FactionSync::onState);
        registrar.playToClient(ResultPayload.TYPE, ResultPayload.STREAM_CODEC,
                (payload, context) -> ClientFactionCache.setMessage(payload.message(), payload.success()));
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, FactionSync::onAction);
    }

    private static void onState(StatePayload payload, IPayloadContext context) {
        ClientFactionCache.update(payload.factions(), payload.invites(), payload.friendlyFireLocked(), payload.coreRadius(),
                context.player().getUUID(), payload.serverTime(), payload.warPreparation());
        // names above heads and in chat include the faction tag
        context.player().level().players().forEach(Player::refreshDisplayName);
    }

    private static void onAction(ActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        FactionService.Result result = switch (payload.action()) {
            case CREATE -> FactionService.create(player, payload.arg(), color(payload.color()));
            case INVITE -> FactionService.invite(player, payload.arg());
            case ACCEPT -> withUuid(payload.arg(), id -> FactionService.accept(player, id));
            case DECLINE -> withUuid(payload.arg(), id -> FactionService.decline(player, id));
            case LEAVE -> FactionService.leave(player);
            case KICK -> withUuid(payload.arg(), id -> FactionService.kick(player, id));
            case PROMOTE -> withUuid(payload.arg(), id -> FactionService.promote(player, id));
            case SET_COLOR -> FactionService.setColor(player, color(payload.color()));
            case SET_FRIENDLY_FIRE -> FactionService.setFriendlyFire(player, Boolean.parseBoolean(payload.arg()));
            case SET_ROLE -> withUuid(payload.arg(), id -> FactionService.setRole(player, id, FactionRole.byId(payload.color())));
            case PROPOSE_ALLIANCE -> withUuid(payload.arg(), id -> FactionService.proposeAlliance(player, id));
            case ACCEPT_ALLIANCE -> withUuid(payload.arg(), id -> FactionService.acceptAlliance(player, id));
            case BREAK_ALLIANCE -> withUuid(payload.arg(), id -> FactionService.breakAlliance(player, id));
            case DECLARE_WAR -> withUuid(payload.arg(), id -> FactionService.declareEnemy(player, id));
            case OFFER_PEACE -> withUuid(payload.arg(), id -> FactionService.offerPeace(player, id));
        };
        PacketDistributor.sendToPlayer(player, new ResultPayload(result.message(), result.success()));
        player.sendSystemMessage(result.message());
    }

    private static ChatFormatting color(int id) {
        ChatFormatting color = ChatFormatting.getById(id);
        return color != null ? color : ChatFormatting.WHITE;
    }

    private static FactionService.Result withUuid(String arg, Function<UUID, FactionService.Result> action) {
        try {
            return action.apply(UUID.fromString(arg));
        } catch (IllegalArgumentException e) {
            return new FactionService.Result(false, Component.translatable("message.projectrebirth.faction.not_in_faction"));
        }
    }

    public static void sendTo(ServerPlayer player) {
        // test players have no real connection and cannot receive mod messages
        if (player.connection == null || !player.connection.hasChannel(StatePayload.TYPE)) {
            return;
        }
        FactionData data = FactionData.get(player.server);
        List<FactionView> views = data.all().stream().map(FactionView::of).toList();
        List<UUID> invites = data.all().stream()
                .filter(faction -> faction.invited().contains(player.getUUID()))
                .map(Faction::id)
                .toList();
        PacketDistributor.sendToPlayer(player,
                new StatePayload(views, invites, !RebirthConfig.FACTION_LEADER_CAN_TOGGLE_FRIENDLY_FIRE.get(),
                        FactionService.coreRadius(), System.currentTimeMillis(), FactionService.preparationMillis()));
    }

    public static void sendToAll(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(FactionSync::sendTo);
    }
}
