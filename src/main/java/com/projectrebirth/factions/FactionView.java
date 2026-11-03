package com.projectrebirth.factions;

import java.util.List;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What clients know about a faction: enough for the faction window and the names above heads. */
public record FactionView(UUID id, String name, int colorId, UUID leader, boolean friendlyFire, List<Member> members) {
    public record Member(UUID id, String name) {
        public static final StreamCodec<ByteBuf, Member> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Member::id,
                ByteBufCodecs.STRING_UTF8, Member::name,
                Member::new);
    }

    public static final StreamCodec<ByteBuf, FactionView> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, FactionView::id,
            ByteBufCodecs.STRING_UTF8, FactionView::name,
            ByteBufCodecs.VAR_INT, FactionView::colorId,
            UUIDUtil.STREAM_CODEC, FactionView::leader,
            ByteBufCodecs.BOOL, FactionView::friendlyFire,
            Member.STREAM_CODEC.apply(ByteBufCodecs.list()), FactionView::members,
            FactionView::new);

    static FactionView of(Faction faction) {
        List<Member> members = faction.members().entrySet().stream()
                .map(entry -> new Member(entry.getKey(), entry.getValue()))
                .toList();
        return new FactionView(faction.id(), faction.name(), faction.color().getId(), faction.leader(),
                FactionService.friendlyFire(faction), members);
    }

    public ChatFormatting color() {
        ChatFormatting color = ChatFormatting.getById(colorId);
        return color != null ? color : ChatFormatting.WHITE;
    }

    public boolean isMember(UUID player) {
        return members.stream().anyMatch(member -> member.id().equals(player));
    }
}
