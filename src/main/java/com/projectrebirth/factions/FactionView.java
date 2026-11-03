package com.projectrebirth.factions;

import java.util.List;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What clients know about a faction: enough for the faction window (members and roles, relations, cores) and for
 * the names above heads.
 */
public record FactionView(UUID id, String name, int colorId, UUID leader, boolean friendlyFire, List<Member> members,
                          List<UUID> allies, List<UUID> enemies, List<UUID> allyRequests, List<GlobalPos> cores) {
    public record Member(UUID id, String name, FactionRole role) {
        public static final StreamCodec<ByteBuf, Member> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Member::id,
                ByteBufCodecs.STRING_UTF8, Member::name,
                ByteBufCodecs.idMapper(FactionRole::byId, FactionRole::ordinal), Member::role,
                Member::new);
    }

    private static final StreamCodec<ByteBuf, List<UUID>> UUIDS = UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, FactionView> STREAM_CODEC = StreamCodec.of(
            (buf, view) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, view.id());
                ByteBufCodecs.STRING_UTF8.encode(buf, view.name());
                ByteBufCodecs.VAR_INT.encode(buf, view.colorId());
                UUIDUtil.STREAM_CODEC.encode(buf, view.leader());
                ByteBufCodecs.BOOL.encode(buf, view.friendlyFire());
                Member.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, view.members());
                UUIDS.encode(buf, view.allies());
                UUIDS.encode(buf, view.enemies());
                UUIDS.encode(buf, view.allyRequests());
                GlobalPos.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, view.cores());
            },
            buf -> new FactionView(
                    UUIDUtil.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    UUIDUtil.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    Member.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    UUIDS.decode(buf),
                    UUIDS.decode(buf),
                    UUIDS.decode(buf),
                    GlobalPos.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)));

    static FactionView of(Faction faction) {
        List<Member> members = faction.members().entrySet().stream()
                .map(entry -> new Member(entry.getKey(), entry.getValue(), faction.role(entry.getKey())))
                .toList();
        return new FactionView(faction.id(), faction.name(), faction.color().getId(), faction.leader(),
                FactionService.friendlyFire(faction), members, List.copyOf(faction.allies()), List.copyOf(faction.enemies()),
                List.copyOf(faction.allyRequests()), List.copyOf(faction.cores()));
    }

    public ChatFormatting color() {
        ChatFormatting color = ChatFormatting.getById(colorId);
        return color != null ? color : ChatFormatting.WHITE;
    }

    public boolean isMember(UUID player) {
        return members.stream().anyMatch(member -> member.id().equals(player));
    }

    public FactionRole role(UUID player) {
        return members.stream().filter(member -> member.id().equals(player)).map(Member::role).findFirst()
                .orElse(FactionRole.RECRUIT);
    }

    /** Enemies if either side has declared war. */
    public boolean isEnemyOf(FactionView other) {
        return enemies.contains(other.id()) || other.enemies().contains(id);
    }
}
