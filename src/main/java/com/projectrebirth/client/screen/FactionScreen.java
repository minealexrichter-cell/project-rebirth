package com.projectrebirth.client.screen;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.projectrebirth.factions.ClientFactionCache;
import com.projectrebirth.factions.FactionRole;
import com.projectrebirth.factions.FactionSync;
import com.projectrebirth.factions.FactionView;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Faction window (key G). Without a faction: create one (name and colour) and answer invitations.
 * In a faction there are four tabs: members (roles, invite, kick), territory (cores), diplomacy (allies and
 * enemies) and settings (colour, friendly fire, leave). Buttons only appear for what the player's role allows;
 * everything is still only a request that the server checks and answers with a message shown at the bottom.
 */
public class FactionScreen extends Screen {
    private static final int WIDTH = 240;
    private static final int HEIGHT = 220;
    private static final int MAX_ROWS = 7;
    private static final int SWATCH = 12;
    private static final List<ChatFormatting> COLORS = Arrays.stream(ChatFormatting.values()).filter(ChatFormatting::isColor).toList();
    /** Roles a member can be switched between with the "Role" button, in order. */
    private static final List<FactionRole> ASSIGNABLE = List.of(FactionRole.OFFICER, FactionRole.MEMBER, FactionRole.RECRUIT);

    private enum Tab {
        MEMBERS, TERRITORY, DIPLOMACY, SETTINGS
    }

    /** Remembered between openings of the window. */
    private static Tab tab = Tab.MEMBERS;

    private int left;
    private int top;
    private int seenVersion = -1;
    private EditBox nameBox;
    private String typedName = "";
    private ChatFormatting newColor = ChatFormatting.AQUA;
    private int paletteY = -1;
    /** Whether the window was last built for a faction member; the text field means something else in each case. */
    private boolean builtAsMember;

    public FactionScreen() {
        super(Component.translatable("screen.projectrebirth.faction.title"));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        seenVersion = ClientFactionCache.version();
        paletteY = -1;
        nameBox = null;
        Optional<FactionView> own = ClientFactionCache.local();
        if (own.isPresent() != builtAsMember) {
            typedName = "";
            builtAsMember = own.isPresent();
        }
        if (own.isEmpty()) {
            initNoFaction();
            return;
        }
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab target = tabs[i];
            Button button = Button.builder(Component.translatable("screen.projectrebirth.faction.tab." + target.name().toLowerCase(java.util.Locale.ROOT)),
                    b -> {
                        tab = target;
                        typedName = "";
                        rebuildWidgets();
                    }).bounds(left + 8 + i * 57, top + 20, 54, 16).build();
            button.active = target != tab;
            addRenderableWidget(button);
        }
        FactionView faction = own.get();
        switch (tab) {
            case MEMBERS -> initMembers(faction);
            case DIPLOMACY -> initDiplomacy(faction);
            case SETTINGS -> initSettings(faction);
            case TERRITORY -> {
            }
        }
    }

    private FactionRole myRole(FactionView faction) {
        return minecraft != null && minecraft.player != null ? faction.role(minecraft.player.getUUID()) : FactionRole.RECRUIT;
    }

    private UUID me() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : new UUID(0, 0);
    }

    // ---------- no faction ----------

    private void initNoFaction() {
        int y = top + 34;
        nameBox = textBox(left + 10, y, 150, 20, "screen.projectrebirth.faction.name");
        addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.faction.create"),
                b -> send(FactionSync.Action.CREATE, nameBox.getValue(), newColor.getId())).bounds(left + 166, y, 64, 20).build());
        paletteY = y + 26;
        int row = top + 100;
        for (UUID factionId : ClientFactionCache.invites()) {
            if (row > top + HEIGHT - 40 || ClientFactionCache.byId(factionId).isEmpty()) {
                continue;
            }
            addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.faction.accept"),
                    b -> send(FactionSync.Action.ACCEPT, factionId.toString(), 0)).bounds(left + 120, row, 54, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.faction.decline"),
                    b -> send(FactionSync.Action.DECLINE, factionId.toString(), 0)).bounds(left + 176, row, 54, 18).build());
            row += 22;
        }
    }

    // ---------- members ----------

    private void initMembers(FactionView faction) {
        FactionRole mine = myRole(faction);
        int row = top + 54;
        int shown = 0;
        for (FactionView.Member member : faction.members()) {
            if (shown++ >= MAX_ROWS) {
                break;
            }
            if (!member.id().equals(me())) {
                String id = member.id().toString();
                FactionRole next = nextRole(mine, member.role());
                if (next != null) {
                    Button role = Button.builder(Component.translatable("screen.projectrebirth.faction.role"),
                            b -> send(FactionSync.Action.SET_ROLE, id, next.ordinal())).bounds(left + 128, row - 3, 36, 14).build();
                    role.setTooltip(Tooltip.create(Component.translatable("screen.projectrebirth.faction.role_to",
                            Component.translatable(next.translationKey()))));
                    addRenderableWidget(role);
                }
                if (mine == FactionRole.LEADER) {
                    Button lead = Button.builder(Component.translatable("screen.projectrebirth.faction.promote"),
                            b -> send(FactionSync.Action.PROMOTE, id, 0)).bounds(left + 166, row - 3, 36, 14).build();
                    lead.setTooltip(Tooltip.create(Component.translatable("screen.projectrebirth.faction.promote_hint")));
                    addRenderableWidget(lead);
                }
                if (mine.canKick(member.role())) {
                    Button kick = Button.builder(Component.literal("✕"),
                            b -> send(FactionSync.Action.KICK, id, 0)).bounds(left + 204, row - 3, 26, 14).build();
                    kick.setTooltip(Tooltip.create(Component.translatable("screen.projectrebirth.faction.kick")));
                    addRenderableWidget(kick);
                }
            }
            row += 18;
        }
        if (mine.canInvite()) {
            int y = top + 184;
            nameBox = textBox(left + 10, y, 150, 16, "screen.projectrebirth.faction.player");
            addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.faction.invite"),
                    b -> send(FactionSync.Action.INVITE, nameBox.getValue(), 0)).bounds(left + 166, y, 64, 16).build());
        }
    }

    /** The next role (in the officer, member, recruit cycle) this player may give, or null if none. */
    private static FactionRole nextRole(FactionRole mine, FactionRole current) {
        if (current == FactionRole.LEADER) {
            return null;
        }
        int start = ASSIGNABLE.indexOf(current);
        for (int step = 1; step < ASSIGNABLE.size(); step++) {
            FactionRole candidate = ASSIGNABLE.get((start + step) % ASSIGNABLE.size());
            if (mine.canAssign(current, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    // ---------- diplomacy ----------

    private void initDiplomacy(FactionView faction) {
        if (myRole(faction) != FactionRole.LEADER) {
            return;
        }
        int row = top + 54;
        int shown = 0;
        for (FactionView other : ClientFactionCache.all()) {
            if (other.id().equals(faction.id()) || shown++ >= MAX_ROWS) {
                continue;
            }
            String id = other.id().toString();
            if (faction.isEnemyOf(other)) {
                // at war: one wide peace button instead of the alliance and war buttons
                boolean offeredToUs = faction.peaceOffers().contains(other.id());
                boolean offeredByUs = other.peaceOffers().contains(faction.id());
                Button peace = Button.builder(Component.translatable(offeredToUs ? "screen.projectrebirth.faction.accept_peace"
                                        : offeredByUs ? "screen.projectrebirth.faction.peace_offered" : "screen.projectrebirth.faction.offer_peace"),
                                b -> send(FactionSync.Action.OFFER_PEACE, id, 0))
                        .bounds(left + 142, row - 3, 88, 14).build();
                peace.active = !offeredByUs;
                addRenderableWidget(peace);
                row += 18;
                continue;
            }
            Button alliance;
            if (faction.allies().contains(other.id())) {
                alliance = Button.builder(Component.translatable("screen.projectrebirth.faction.break_alliance"),
                        b -> send(FactionSync.Action.BREAK_ALLIANCE, id, 0)).bounds(left + 138, row - 3, 50, 14).build();
            } else if (faction.allyRequests().contains(other.id())) {
                alliance = Button.builder(Component.translatable("screen.projectrebirth.faction.accept_alliance"),
                        b -> send(FactionSync.Action.ACCEPT_ALLIANCE, id, 0)).bounds(left + 138, row - 3, 50, 14).build();
            } else {
                alliance = Button.builder(Component.translatable("screen.projectrebirth.faction.propose_alliance"),
                        b -> send(FactionSync.Action.PROPOSE_ALLIANCE, id, 0)).bounds(left + 138, row - 3, 50, 14).build();
                // already proposed and waiting for an answer
                alliance.active = !other.allyRequests().contains(faction.id());
            }
            addRenderableWidget(alliance);
            addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.faction.war"),
                    b -> send(FactionSync.Action.DECLARE_WAR, id, 0)).bounds(left + 190, row - 3, 40, 14).build());
            row += 18;
        }
    }

    // ---------- settings ----------

    private void initSettings(FactionView faction) {
        if (myRole(faction) == FactionRole.LEADER) {
            paletteY = top + 64;
            Button fire = Button.builder(Component.translatable(faction.friendlyFire()
                                    ? "screen.projectrebirth.faction.friendly_fire_on" : "screen.projectrebirth.faction.friendly_fire_off"),
                            b -> send(FactionSync.Action.SET_FRIENDLY_FIRE, Boolean.toString(!faction.friendlyFire()), 0))
                    .bounds(left + 10, top + 100, 220, 18).build();
            if (ClientFactionCache.friendlyFireLocked()) {
                fire.active = false;
                fire.setTooltip(Tooltip.create(Component.translatable("screen.projectrebirth.faction.friendly_fire_locked")));
            }
            addRenderableWidget(fire);
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.faction.leave"),
                b -> send(FactionSync.Action.LEAVE, "", 0)).bounds(left + 10, top + 180, 220, 18).build());
    }

    // ---------- shared ----------

    private EditBox textBox(int x, int y, int w, int h, String hintKey) {
        EditBox box = new EditBox(font, x, y, w, h, Component.translatable(hintKey));
        box.setMaxLength(16);
        box.setValue(typedName);
        box.setHint(Component.translatable(hintKey).withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(box);
        return box;
    }

    private void send(FactionSync.Action action, String arg, int value) {
        PacketDistributor.sendToServer(new FactionSync.ActionPayload(action, arg, value));
    }

    @Override
    public void tick() {
        super.tick();
        if (seenVersion != ClientFactionCache.version()) {
            // keep what the player was typing across the rebuild
            if (nameBox != null) {
                typedName = nameBox.getValue();
            }
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left + WIDTH / 2, top + 8, 0xFFFFFF);
        Optional<FactionView> own = ClientFactionCache.local();
        if (own.isEmpty()) {
            renderNoFaction(graphics);
        } else {
            switch (tab) {
                case MEMBERS -> renderMembers(graphics, own.get());
                case TERRITORY -> renderTerritory(graphics, own.get());
                case DIPLOMACY -> renderDiplomacy(graphics, own.get());
                case SETTINGS -> renderSettings(graphics, own.get());
            }
        }
        if (paletteY >= 0) {
            renderPalette(graphics, mouseX, mouseY, own.map(FactionView::color).orElse(newColor));
        }
        Component message = ClientFactionCache.lastMessage();
        if (message != null) {
            int color = ClientFactionCache.lastMessageSuccess() ? 0x80FF80 : 0xFF8080;
            graphics.drawCenteredString(font, message, left + WIDTH / 2, top + HEIGHT - 12, color);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xC0101418);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, 0xFF5A6470);
    }

    private void renderNoFaction(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("screen.projectrebirth.faction.create_header"), left + 10, top + 22, 0xC0C0C0);
        graphics.drawString(font, Component.translatable("screen.projectrebirth.faction.invites"), left + 10, top + 88, 0xC0C0C0);
        int row = top + 100;
        boolean any = false;
        for (UUID factionId : ClientFactionCache.invites()) {
            Optional<FactionView> faction = ClientFactionCache.byId(factionId);
            if (row > top + HEIGHT - 40 || faction.isEmpty()) {
                continue;
            }
            graphics.drawString(font, Component.literal(faction.get().name()).withStyle(faction.get().color()), left + 10, row + 5, 0xFFFFFF);
            row += 22;
            any = true;
        }
        if (!any) {
            graphics.drawString(font, Component.translatable("screen.projectrebirth.faction.no_invites"), left + 10, row + 5, 0x808080);
        }
    }

    private void header(GuiGraphics graphics, FactionView faction, Component second) {
        graphics.drawString(font, Component.literal(faction.name()).withStyle(faction.color(), ChatFormatting.BOLD), left + 10, top + 40, 0xFFFFFF);
        graphics.drawString(font, second, left + WIDTH - 10 - font.width(second), top + 40, 0xA0A0A0);
    }

    private void renderMembers(GuiGraphics graphics, FactionView faction) {
        header(graphics, faction, Component.translatable("screen.projectrebirth.faction.member_count", faction.members().size()));
        int row = top + 54;
        int shown = 0;
        for (FactionView.Member member : faction.members()) {
            if (shown >= MAX_ROWS) {
                graphics.drawString(font, Component.translatable("screen.projectrebirth.faction.more",
                        faction.members().size() - MAX_ROWS), left + 10, row, 0x808080);
                break;
            }
            boolean online = minecraft != null && minecraft.getConnection() != null && minecraft.getConnection().getPlayerInfo(member.id()) != null;
            MutableComponent name = Component.literal(member.name()).withStyle(online ? ChatFormatting.WHITE : ChatFormatting.GRAY);
            graphics.drawString(font, name, left + 10, row, 0xFFFFFF);
            graphics.drawString(font, Component.translatable(member.role().translationKey()).withStyle(member.role().color()), left + 82, row, 0xFFFFFF);
            row += 18;
            shown++;
        }
    }

    private void renderTerritory(GuiGraphics graphics, FactionView faction) {
        header(graphics, faction, Component.translatable("screen.projectrebirth.faction.radius", ClientFactionCache.coreRadius()));
        int y = top + 56;
        if (faction.cores().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.projectrebirth.faction.no_cores"), left + 10, y, 0x808080);
            y += 12;
        }
        for (GlobalPos core : faction.cores()) {
            graphics.drawString(font, Component.literal("◆ " + core.dimension().location().getPath() + "  "
                    + core.pos().getX() + ", " + core.pos().getY() + ", " + core.pos().getZ()), left + 10, y, 0xD0D0D0);
            y += 12;
        }
        if (minecraft != null && minecraft.player != null) {
            Component here = ClientFactionCache.territoryAt(minecraft.player.level().dimension(), minecraft.player.blockPosition())
                    .map(owner -> (Component) Component.translatable("screen.projectrebirth.faction.here",
                            Component.literal(owner.name()).withStyle(owner.color())))
                    .orElse(Component.translatable("screen.projectrebirth.faction.here_free"));
            graphics.drawString(font, here, left + 10, top + 112, 0xFFFFFF);
        }
        graphics.drawWordWrap(font, Component.translatable("screen.projectrebirth.faction.territory_hint"), left + 10, top + 128, 220, 0x909090);
    }

    private void renderDiplomacy(GuiGraphics graphics, FactionView faction) {
        header(graphics, faction, Component.translatable(myRole(faction) == FactionRole.LEADER
                ? "screen.projectrebirth.faction.diplomacy_leader" : "screen.projectrebirth.faction.diplomacy_view"));
        int row = top + 54;
        int shown = 0;
        for (FactionView other : ClientFactionCache.all()) {
            if (other.id().equals(faction.id()) || shown++ >= MAX_ROWS) {
                continue;
            }
            graphics.drawString(font, Component.literal(other.name()).withStyle(other.color()), left + 10, row, 0xFFFFFF);
            Component relation;
            if (faction.allies().contains(other.id())) {
                relation = Component.translatable("screen.projectrebirth.faction.relation.ally").withStyle(ChatFormatting.GREEN);
            } else if (faction.isEnemyOf(other)) {
                relation = warRelation(faction, other);
            } else if (faction.allyRequests().contains(other.id())) {
                relation = Component.translatable("screen.projectrebirth.faction.relation.offers").withStyle(ChatFormatting.YELLOW);
            } else {
                relation = Component.translatable("screen.projectrebirth.faction.relation.neutral").withStyle(ChatFormatting.GRAY);
            }
            graphics.drawString(font, relation, left + 76, row, 0xFFFFFF);
            row += 18;
        }
        if (shown == 0) {
            graphics.drawString(font, Component.translatable("screen.projectrebirth.faction.no_other_factions"), left + 10, row, 0x808080);
        }
    }

    /**
     * State of a war, by the same rules as the server: preparation countdown, then war; an active war is shown as
     * "protected" while nobody of the enemy faction is online (that is how the server treats their territory).
     */
    private Component warRelation(FactionView faction, FactionView enemy) {
        Long start = faction.warStart(enemy.id());
        long remaining = start == null ? 0 : start + ClientFactionCache.warPreparation() - ClientFactionCache.serverNow();
        if (remaining > 0) {
            return Component.translatable("screen.projectrebirth.faction.relation.preparing", (remaining + 59_999) / 60_000)
                    .withStyle(ChatFormatting.GOLD);
        }
        boolean enemyOnline = minecraft != null && minecraft.getConnection() != null
                && enemy.members().stream().anyMatch(member -> minecraft.getConnection().getPlayerInfo(member.id()) != null);
        return enemyOnline
                ? Component.translatable("screen.projectrebirth.faction.relation.enemy").withStyle(ChatFormatting.RED)
                : Component.translatable("screen.projectrebirth.faction.relation.protected").withStyle(ChatFormatting.DARK_AQUA);
    }

    private void renderSettings(GuiGraphics graphics, FactionView faction) {
        header(graphics, faction, Component.translatable("screen.projectrebirth.faction.your_role",
                Component.translatable(myRole(faction).translationKey()).withStyle(myRole(faction).color())));
        if (myRole(faction) == FactionRole.LEADER) {
            graphics.drawString(font, Component.translatable("screen.projectrebirth.faction.color"), left + 10, top + 54, 0xC0C0C0);
        } else {
            graphics.drawString(font, Component.translatable("screen.projectrebirth.faction.summary_ff",
                    Component.translatable(faction.friendlyFire() ? "screen.projectrebirth.faction.on" : "screen.projectrebirth.faction.off")),
                    left + 10, top + 56, 0xA0A0A0);
        }
    }

    private void renderPalette(GuiGraphics graphics, int mouseX, int mouseY, ChatFormatting selected) {
        for (int i = 0; i < COLORS.size(); i++) {
            int x = left + 10 + i * (SWATCH + 2);
            graphics.fill(x, paletteY, x + SWATCH, paletteY + SWATCH, 0xFF000000 | COLORS.get(i).getColor());
            boolean hovered = mouseX >= x && mouseX < x + SWATCH && mouseY >= paletteY && mouseY < paletteY + SWATCH;
            if (COLORS.get(i) == selected || hovered) {
                graphics.renderOutline(x - 1, paletteY - 1, SWATCH + 2, SWATCH + 2, COLORS.get(i) == selected ? 0xFFFFFFFF : 0xFFA0A0A0);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (paletteY >= 0 && mouseY >= paletteY && mouseY < paletteY + SWATCH) {
            for (int i = 0; i < COLORS.size(); i++) {
                int x = left + 10 + i * (SWATCH + 2);
                if (mouseX >= x && mouseX < x + SWATCH) {
                    if (ClientFactionCache.local().isPresent()) {
                        send(FactionSync.Action.SET_COLOR, "", COLORS.get(i).getId());
                    } else {
                        newColor = COLORS.get(i);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
