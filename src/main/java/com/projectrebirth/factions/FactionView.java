package com.projectrebirth.factions;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

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
                          List<UUID> allies, List<War> wars, List<UUID> allyRequests, List<UUID> peaceOffers,
                          List<GlobalPos> cores) {
    /** A war with another faction and the server time it was declared. */
    public record War(UUID enemy, long start) {
        public static final StreamCodec<ByteBuf, War> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, War::enemy,
                ByteBufCodecs.VAR_LONG, War::start,
                War::new);
    }

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
                War.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, view.wars());
                UUIDS.encode(buf, view.allyRequests());
                UUIDS.encode(buf, view.peaceOffers());
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
                    War.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    UUIDS.decode(buf),
                    UUIDS.decode(buf),
                    GlobalPos.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)));

    static FactionView of(Faction faction) {
        List<Member> members = faction.members().entrySet().stream()
                .map(entry -> new Member(entry.getKey(), entry.getValue(), faction.role(entry.getKey())))
                .toList();
        return new FactionView(faction.id(), faction.name(), faction.color().getId(), faction.leader(),
                FactionService.friendlyFire(faction), members, List.copyOf(faction.allies()),
                faction.warStarts().entrySet().stream().map(war -> new War(war.getKey(), war.getValue())).toList(),
                List.copyOf(faction.allyRequests()), List.copyOf(faction.peaceOffers()), List.copyOf(faction.cores()));
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

    public boolean isEnemyOf(FactionView other) {
        return warStart(other.id()) != null;
    }

    /** When the war with this faction was declared (server time), or null if there is no war. */
    @Nullable
    public Long warStart(UUID other) {
        return wars.stream().filter(war -> war.enemy().equals(other)).map(War::start).findFirst().orElse(null);
    }
}
