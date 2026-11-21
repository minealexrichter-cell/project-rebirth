package com.projectrebirth.client.screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiButton;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiMap;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiTable;
import com.projectrebirth.client.ui.UiTabs;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.client.ui.UiTooltip;
import com.projectrebirth.factions.ClientFactionCache;
import com.projectrebirth.factions.FactionRole;
import com.projectrebirth.factions.FactionSync;
import com.projectrebirth.factions.FactionView;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Faction window (key G) in the style "Industrial terminal" (1.8.0 block D). Without a faction: create one (name and
 * colour) and answer invitations. In a faction there are five tabs: members (roles, invite, kick), territory (the map of
 * the land around with the cores), alliances and wars (with the preparation timer of a war), the rights of the roles (a
 * table) and settings (colour, friendly fire, leave). Buttons only appear for what the player's role allows; everything is
 * still only a request that the server checks and answers with a message shown at the bottom.
 */
public class FactionScreen extends Screen {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 230;
    private static final int ROW = 17;
    private static final int MAX_ROWS = 8;
    private static final int SWATCH = 12;
    private static final int LIST_Y = 60;
    private static final List<ChatFormatting> COLORS = Arrays.stream(ChatFormatting.values()).filter(ChatFormatting::isColor).toList();
    /** Roles a member can be switched between with the "Role" button, in order. */
    private static final List<FactionRole> ASSIGNABLE = List.of(FactionRole.OFFICER, FactionRole.MEMBER, FactionRole.RECRUIT);

    private enum Tab {
        MEMBERS, TERRITORY, DIPLOMACY, RIGHTS, SETTINGS
    }

    /** Remembered between openings of the window. */
    private static Tab tab = Tab.MEMBERS;

    private final UiText.Hints hints = new UiText.Hints();
    private int left;
    private int top;
    private int seenVersion = -1;
    private EditBox nameBox;
    private String typedName = "";
    private ChatFormatting newColor = ChatFormatting.AQUA;
    private int paletteY = -1;
    /** Whether the window was last built for a faction member; the text field means something else in each case. */
    private boolean builtAsMember;
    private UiMap map;
    private TerritorySource territory;

    public FactionScreen() {
        super(Component.translatable("screen.projectrebirth.faction.title"));
    }

    /** The window open on a tab (by its number); for the layout check of development. */
    public static FactionScreen onTab(int index) {
        tab = Tab.values()[Math.floorMod(index, Tab.values().length)];
        return new FactionScreen();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.faction." + key, args);
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        seenVersion = ClientFactionCache.version();
        paletteY = -1;
        nameBox = null;
        closeMap();
        Optional<FactionView> own = ClientFactionCache.local();
        if (own.isPresent() != builtAsMember) {
            typedName = "";
            builtAsMember = own.isPresent();
        }
        if (own.isEmpty()) {
            initNoFaction();
            return;
        }
        List<UiTabs.Tab> labels = new ArrayList<>();
        for (Tab target : Tab.values()) {
            labels.add(UiTabs.Tab.of(tr("tab." + target.name().toLowerCase(Locale.ROOT))));
        }
        addRenderableWidget(new UiTabs(left + 7, top + 24, WIDTH - 14, labels, () -> tab.ordinal(), index -> {
            tab = Tab.values()[index];
            typedName = "";
            rebuildWidgets();
        }));
        FactionView faction = own.get();
        switch (tab) {
            case MEMBERS -> initMembers(faction);
            case TERRITORY -> initTerritory();
            case DIPLOMACY -> initDiplomacy(faction);
            case SETTINGS -> initSettings(faction);
            case RIGHTS -> {
            }
        }
    }

    private FactionRole myRole(FactionView faction) {
        return minecraft != null && minecraft.player != null ? faction.role(minecraft.player.getUUID()) : FactionRole.RECRUIT;
    }

    private UUID me() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : new UUID(0, 0);
    }

    private UiButton button(int x, int y, int w, int h, Component text, Runnable action) {
        return addRenderableWidget(new UiButton(x, y, w, h, text, null, 0, UiButton.Look.METAL, action));
    }

    // ---------- no faction ----------

    private void initNoFaction() {
        int y = top + 40;
        nameBox = textBox(left + 12, y, 220, 16, "screen.projectrebirth.faction.name");
        button(left + 238, y, WIDTH - 250, 16, tr("create"), () -> send(FactionSync.Action.CREATE, nameBox.getValue(), newColor.getId()));
        paletteY = y + 24;
        int row = top + 112;
        for (UUID factionId : ClientFactionCache.invites()) {
            if (row > top + HEIGHT - 40 || ClientFactionCache.byId(factionId).isEmpty()) {
                continue;
            }
            button(left + WIDTH - 140, row, 62, 14, tr("accept"), () -> send(FactionSync.Action.ACCEPT, factionId.toString(), 0));
            button(left + WIDTH - 75, row, 62, 14, tr("decline"), () -> send(FactionSync.Action.DECLINE, factionId.toString(), 0));
            row += ROW;
        }
    }

    // ---------- members ----------

    private void initMembers(FactionView faction) {
        FactionRole mine = myRole(faction);
        int row = top + LIST_Y + 1;
        int shown = 0;
        for (FactionView.Member member : faction.members()) {
            if (shown++ >= MAX_ROWS) {
                break;
            }
            if (!member.id().equals(me())) {
                String id = member.id().toString();
                FactionRole next = nextRole(mine, member.role());
                if (next != null) {
                    UiButton role = button(left + 196, row, 54, 14, tr("role"), () -> send(FactionSync.Action.SET_ROLE, id, next.ordinal()));
                    role.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("role_to", Component.translatable(next.translationKey()))));
                }
                if (mine == FactionRole.LEADER) {
                    UiButton lead = button(left + 252, row, 54, 14, tr("promote"), () -> send(FactionSync.Action.PROMOTE, id, 0));
                    lead.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("promote_hint")));
                }
                if (mine.canKick(member.role())) {
                    addRenderableWidget(UiButton.icon(left + 310, row, 14, UiSprites.ICON_CLOSE, 7, tr("kick"),
                            () -> send(FactionSync.Action.KICK, id, 0)));
                }
            }
            row += ROW;
        }
        if (mine.canInvite()) {
            int y = top + HEIGHT - 38;
            nameBox = textBox(left + 12, y, 220, 14, "screen.projectrebirth.faction.player");
            button(left + 238, y, WIDTH - 250, 14, tr("invite"), () -> send(FactionSync.Action.INVITE, nameBox.getValue(), 0));
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

    // ---------- territory ----------

    /** The land around the player: each chunk painted in the colour of the faction that owns it, by the server's rule. */
    private static final class TerritorySource implements UiMap.Source {
        private final int side;
        private final int minX;
        private final int minZ;

        TerritorySource(int centreChunkX, int centreChunkZ, int radius) {
            side = radius * 2 + 1;
            minX = centreChunkX - radius;
            minZ = centreChunkZ - radius;
        }

        @Override
        public int side() {
            return side;
        }

        @Override
        public int minChunkX() {
            return minX;
        }

        @Override
        public int minChunkZ() {
            return minZ;
        }

        private static Optional<FactionView> owner(int chunkX, int chunkZ) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) {
                return Optional.empty();
            }
            return ClientFactionCache.territoryAt(player.level().dimension(), new BlockPos(chunkX * 16 + 8, 64, chunkZ * 16 + 8));
        }

        @Override
        public int color(int layers, int chunkX, int chunkZ) {
            return owner(chunkX, chunkZ).map(faction -> {
                Integer rgb = faction.color().getColor();
                return UiColors.withAlpha(rgb == null ? 0xFFFFFF : rgb, 0x90);
            }).orElse(0);
        }

        @Override
        public Component describe(int layers, int chunkX, int chunkZ) {
            return owner(chunkX, chunkZ).map(faction -> (Component) tr("here", Component.literal(faction.name()).withStyle(faction.color())))
                    .orElse(tr("here_free"));
        }
    }

    private void initTerritory() {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        int radius = Math.max(6, Math.min(24, ClientFactionCache.coreRadius() / 16 * 2 + 3));
        BlockPos at = minecraft.player.blockPosition();
        territory = new TerritorySource(at.getX() >> 4, at.getZ() >> 4, radius);
        map = addRenderableWidget(new UiMap(left + 7, top + LIST_Y - 2, 190, HEIGHT - LIST_Y - 20, territory, () -> 1, this::territoryMarks));
        map.centreOn(at.getX(), at.getZ());
    }

    private List<UiMap.Mark> territoryMarks() {
        List<UiMap.Mark> marks = new ArrayList<>();
        if (minecraft == null || minecraft.player == null) {
            return marks;
        }
        for (FactionView faction : ClientFactionCache.all()) {
            for (GlobalPos core : faction.cores()) {
                if (core.dimension().equals(minecraft.player.level().dimension())) {
                    Integer rgb = faction.color().getColor();
                    marks.add(new UiMap.Mark(core.pos().getX() + 0.5, core.pos().getZ() + 0.5, 0xFF000000 | (rgb == null ? 0xFFFFFF : rgb), null,
                            tr("core_of", Component.literal(faction.name()).withStyle(faction.color())), 5));
                }
            }
        }
        marks.add(new UiMap.Mark(minecraft.player.getX(), minecraft.player.getZ(), 0xFFFFFFFF, null, tr("you"), 4));
        return marks;
    }

    private void closeMap() {
        if (map != null) {
            map.close();
            map = null;
        }
    }

    @Override
    public void removed() {
        closeMap();
        super.removed();
    }

    // ---------- diplomacy ----------

    private void initDiplomacy(FactionView faction) {
        if (myRole(faction) != FactionRole.LEADER) {
            return;
        }
        int row = top + LIST_Y + 1;
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
                UiButton peace = button(left + 228, row, 96, 14, tr(offeredToUs ? "accept_peace" : offeredByUs ? "peace_offered" : "offer_peace"),
                        () -> send(FactionSync.Action.OFFER_PEACE, id, 0));
                if (offeredByUs) {
                    peace.disable(tr("peace_offered"));
                }
                row += ROW;
                continue;
            }
            UiButton alliance;
            if (faction.allies().contains(other.id())) {
                alliance = button(left + 228, row, 56, 14, tr("break_alliance"), () -> send(FactionSync.Action.BREAK_ALLIANCE, id, 0));
            } else if (faction.allyRequests().contains(other.id())) {
                alliance = button(left + 228, row, 56, 14, tr("accept_alliance"), () -> send(FactionSync.Action.ACCEPT_ALLIANCE, id, 0));
            } else {
                alliance = button(left + 228, row, 56, 14, tr("propose_alliance"), () -> send(FactionSync.Action.PROPOSE_ALLIANCE, id, 0));
                // already proposed and waiting for an answer
                if (other.allyRequests().contains(faction.id())) {
                    alliance.disable(tr("relation.offers"));
                }
            }
            button(left + 286, row, 38, 14, tr("war"), () -> send(FactionSync.Action.DECLARE_WAR, id, 0));
            row += ROW;
        }
    }

    // ---------- settings ----------

    private void initSettings(FactionView faction) {
        if (myRole(faction) == FactionRole.LEADER) {
            paletteY = top + 74;
            UiButton fire = button(left + 12, top + 100, WIDTH - 24, 16, tr(faction.friendlyFire() ? "friendly_fire_on" : "friendly_fire_off"),
                    () -> send(FactionSync.Action.SET_FRIENDLY_FIRE, Boolean.toString(!faction.friendlyFire()), 0));
            if (ClientFactionCache.friendlyFireLocked()) {
                fire.disable(tr("friendly_fire_locked"));
            }
        }
        button(left + 12, top + HEIGHT - 40, WIDTH - 24, 16, tr("leave"), () -> send(FactionSync.Action.LEAVE, "", 0)).color(UiColors.ERROR);
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
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiPanels.window(graphics, left, top, WIDTH, HEIGHT);
        UiPanels.title(graphics, left, top, WIDTH, title, null, left + WIDTH - 8);
        Optional<FactionView> own = ClientFactionCache.local();
        if (own.isPresent() && tab != Tab.TERRITORY) {
            UiPanels.screen(graphics, left + 7, top + LIST_Y - 2, WIDTH - 14, HEIGHT - LIST_Y - 20);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hints.clear();
        super.render(graphics, mouseX, mouseY, partialTick);
        Optional<FactionView> own = ClientFactionCache.local();
        if (own.isEmpty()) {
            renderNoFaction(graphics);
        } else {
            switch (tab) {
                case MEMBERS -> renderMembers(graphics, own.get());
                case TERRITORY -> renderTerritory(graphics, own.get(), mouseX, mouseY);
                case DIPLOMACY -> renderDiplomacy(graphics, own.get());
                case RIGHTS -> renderRights(graphics, own.get());
                case SETTINGS -> renderSettings(graphics, own.get());
            }
        }
        if (paletteY >= 0) {
            renderPalette(graphics, mouseX, mouseY, own.map(FactionView::color).orElse(newColor));
        }
        Component message = ClientFactionCache.lastMessage();
        if (message != null) {
            boolean ok = ClientFactionCache.lastMessageSuccess();
            UiPanels.plate(graphics, ok ? UiSprites.Tone.OK : UiSprites.Tone.ERROR, message, left + 7, top + HEIGHT - 17, WIDTH - 14);
        }
        Component hint = hints.at(mouseX, mouseY);
        if (hint != null) {
            UiTooltip.of(hint).render(graphics, mouseX, mouseY);
        }
    }

    private void renderNoFaction(GuiGraphics graphics) {
        UiPanels.label(graphics, tr("create_header"), left + 12, top + 28, WIDTH - 24);
        UiPanels.label(graphics, tr("invites"), left + 12, top + 98, WIDTH - 24);
        UiPanels.screen(graphics, left + 7, top + 108, WIDTH - 14, HEIGHT - 108 - 20);
        int row = top + 112;
        boolean any = false;
        for (UUID factionId : ClientFactionCache.invites()) {
            Optional<FactionView> faction = ClientFactionCache.byId(factionId);
            if (row > top + HEIGHT - 40 || faction.isEmpty()) {
                continue;
            }
            hints.line(graphics, Component.literal(faction.get().name()).withStyle(faction.get().color()), left + 12, row + 3, WIDTH - 160, UiColors.TEXT);
            row += ROW;
            any = true;
        }
        if (!any) {
            UiText.line(graphics, tr("no_invites"), left + 12, row + 3, WIDTH - 24, UiColors.TEXT_DIM);
        }
    }

    /** The faction's name with its colour mark on the left, a second fact on the right. */
    private void header(GuiGraphics graphics, FactionView faction, Component second) {
        Integer rgb = faction.color().getColor();
        graphics.fill(left + 8, top + 43, left + 14, top + 49, 0xFF000000 | (rgb == null ? 0xFFFFFF : rgb));
        int secondWidth = Math.min(WIDTH / 2, font.width(second));
        hints.line(graphics, Component.literal(faction.name()).withStyle(ChatFormatting.BOLD), left + 18, top + 42, WIDTH - 40 - secondWidth, UiColors.TEXT);
        hints.line(graphics, second, left + WIDTH - 8 - secondWidth, top + 42, secondWidth, UiColors.TEXT_SECONDARY);
    }

    private boolean online(UUID id) {
        return minecraft != null && minecraft.getConnection() != null && minecraft.getConnection().getPlayerInfo(id) != null;
    }

    private static int roleColor(FactionRole role) {
        Integer rgb = role.color().getColor();
        return 0xFF000000 | (rgb == null ? 0xFFFFFF : rgb);
    }

    private void renderMembers(GuiGraphics graphics, FactionView faction) {
        header(graphics, faction, tr("member_count", faction.members().size()));
        int row = top + LIST_Y + 1;
        int shown = 0;
        for (FactionView.Member member : faction.members()) {
            if (shown >= MAX_ROWS) {
                UiText.line(graphics, tr("more", faction.members().size() - MAX_ROWS), left + 12, row + 3, 200, UiColors.TEXT_DIM);
                break;
            }
            boolean online = online(member.id());
            UiSprites.draw(graphics, online ? UiSprites.Tone.OK.dot : UiSprites.Tone.INFO.dot, left + 12, row + 4, 5, 5);
            hints.line(graphics, Component.literal(member.name()), left + 21, row + 3, 90, online ? UiColors.TEXT : UiColors.TEXT_DIM);
            hints.line(graphics, Component.translatable(member.role().translationKey()), left + 116, row + 3, 76, roleColor(member.role()));
            row += ROW;
            shown++;
        }
    }

    private void renderTerritory(GuiGraphics graphics, FactionView faction, int mouseX, int mouseY) {
        header(graphics, faction, tr("radius", ClientFactionCache.coreRadius()));
        int rx = left + 204;
        int rw = WIDTH - 7 - 204;
        UiPanels.label(graphics, tr("cores"), rx, top + LIST_Y - 1, rw);
        int y = top + LIST_Y + 10;
        if (faction.cores().isEmpty()) {
            UiText.wrapped(graphics, tr("no_cores"), rx, y, rw, 20, UiColors.TEXT_DIM);
            y += 22;
        }
        int shown = 0;
        for (GlobalPos core : faction.cores()) {
            if (shown++ >= 5) {
                break;
            }
            hints.line(graphics, Component.literal(core.pos().getX() + ", " + core.pos().getY() + ", " + core.pos().getZ()), rx, y, rw, UiColors.SCREEN_TEXT);
            y += 11;
        }
        if (minecraft != null && minecraft.player != null) {
            Optional<FactionView> owner = ClientFactionCache.territoryAt(minecraft.player.level().dimension(), minecraft.player.blockPosition());
            Component here = owner.map(o -> (Component) tr("here", Component.literal(o.name()).withStyle(o.color()))).orElse(tr("here_free"));
            UiPanels.plate(graphics, owner.isPresent() ? UiSprites.Tone.INFO : UiSprites.Tone.OK, here, rx, top + 128, rw);
        }
        UiText.wrapped(graphics, tr("territory_hint_short"), rx, top + 147, rw, HEIGHT - 147 - 22, UiColors.TEXT_DIM);
        if (map != null) {
            Component hovered = map.hovered(mouseX, mouseY);
            if (hovered != null) {
                UiTooltip.of(hovered).render(graphics, mouseX, mouseY);
            }
        }
    }

    private void renderDiplomacy(GuiGraphics graphics, FactionView faction) {
        header(graphics, faction, tr(myRole(faction) == FactionRole.LEADER ? "diplomacy_leader" : "diplomacy_view"));
        int row = top + LIST_Y + 1;
        int shown = 0;
        for (FactionView other : ClientFactionCache.all()) {
            if (other.id().equals(faction.id()) || shown++ >= MAX_ROWS) {
                continue;
            }
            Integer rgb = other.color().getColor();
            graphics.fill(left + 12, row + 4, left + 17, row + 9, 0xFF000000 | (rgb == null ? 0xFFFFFF : rgb));
            hints.line(graphics, Component.literal(other.name()), left + 21, row + 3, 84, UiColors.TEXT);
            if (faction.allies().contains(other.id())) {
                relation(graphics, row, UiSprites.Tone.OK, tr("relation.ally"));
            } else if (faction.isEnemyOf(other)) {
                warRelation(graphics, row, faction, other);
            } else if (faction.allyRequests().contains(other.id())) {
                relation(graphics, row, UiSprites.Tone.WARNING, tr("relation.offers"));
            } else {
                relation(graphics, row, UiSprites.Tone.INFO, tr("relation.neutral"));
            }
            row += ROW;
        }
        if (shown == 0) {
            UiText.line(graphics, tr("no_other_factions"), left + 12, row + 3, WIDTH - 24, UiColors.TEXT_DIM);
        }
    }

    private void relation(GuiGraphics graphics, int row, UiSprites.Tone tone, Component text) {
        UiSprites.draw(graphics, tone.dot, left + 110, row + 4, 5, 5);
        hints.line(graphics, text, left + 118, row + 3, 106, tone.text);
    }

    /**
     * State of a war, by the same rules as the server: preparation with its countdown and a bar, then war; an active war is
     * shown as "protected" while nobody of the enemy faction is online (that is how the server treats their territory).
     */
    private void warRelation(GuiGraphics graphics, int row, FactionView faction, FactionView enemy) {
        Long start = faction.warStart(enemy.id());
        long preparation = ClientFactionCache.warPreparation();
        long remaining = start == null ? 0 : start + preparation - ClientFactionCache.serverNow();
        if (remaining > 0) {
            relation(graphics, row, UiSprites.Tone.WARNING, tr("relation.preparing_clock", com.projectrebirth.client.hud.HudDraw.clock((int) ((remaining + 999) / 1000))));
            UiBars.horizontal(graphics, left + 118, row + 12, 104, 2, 1.0f - remaining / (float) Math.max(1L, preparation), UiColors.WARNING);
            return;
        }
        boolean enemyOnline = enemy.members().stream().anyMatch(member -> online(member.id()));
        relation(graphics, row, enemyOnline ? UiSprites.Tone.ERROR : UiSprites.Tone.INFO, tr(enemyOnline ? "relation.enemy" : "relation.protected"));
    }

    /** What each role may do: the same rules the server checks (FactionRole), the machines by the config. */
    private void renderRights(GuiGraphics graphics, FactionView faction) {
        header(graphics, faction, tr("your_role", Component.translatable(myRole(faction).translationKey()).withStyle(myRole(faction).color())));
        FactionRole[] roles = FactionRole.values();
        UiTable table = new UiTable().column(tr("rights.right"), 118);
        for (FactionRole role : roles) {
            table.column(Component.translatable(role.translationKey()), (WIDTH - 22 - 118) / roles.length);
        }
        String[] rights = {"invite", "kick", "roles", "core", "machines", "containers", "diplomacy", "settings"};
        for (String right : rights) {
            UiTable.Cell[] cells = new UiTable.Cell[roles.length + 1];
            cells[0] = UiTable.Cell.text(tr("rights." + right));
            for (int i = 0; i < roles.length; i++) {
                cells[i + 1] = allowed(roles[i], right) ? UiTable.Cell.yes(tr("rights.yes")) : UiTable.Cell.no(tr("rights.no"));
            }
            table.row(cells);
        }
        table.render(graphics, left + 11, top + LIST_Y, 16, hints);
    }

    private static boolean allowed(FactionRole role, String right) {
        return switch (right) {
            case "invite" -> role.canInvite();
            case "kick" -> role.canKick(FactionRole.RECRUIT);
            case "roles" -> role.canAssign(FactionRole.RECRUIT, FactionRole.MEMBER);
            case "core" -> role.canPlaceCore();
            case "machines" -> role.canManageMachines();
            case "containers" -> role.canUseContainers();
            default -> role == FactionRole.LEADER;
        };
    }

    private void renderSettings(GuiGraphics graphics, FactionView faction) {
        header(graphics, faction, tr("your_role", Component.translatable(myRole(faction).translationKey()).withStyle(myRole(faction).color())));
        if (myRole(faction) == FactionRole.LEADER) {
            UiPanels.label(graphics, tr("color"), left + 12, top + 63, WIDTH - 24);
        } else {
            UiText.line(graphics, tr("summary_ff", tr(faction.friendlyFire() ? "on" : "off")), left + 12, top + 64, WIDTH - 24, UiColors.SCREEN_TEXT);
        }
    }

    private void renderPalette(GuiGraphics graphics, int mouseX, int mouseY, ChatFormatting selected) {
        for (int i = 0; i < COLORS.size(); i++) {
            int x = left + 12 + i * (SWATCH + 2);
            graphics.fill(x - 1, paletteY - 1, x + SWATCH + 1, paletteY + SWATCH + 1, UiColors.OUTLINE);
            graphics.fill(x, paletteY, x + SWATCH, paletteY + SWATCH, 0xFF000000 | COLORS.get(i).getColor());
            boolean hovered = mouseX >= x && mouseX < x + SWATCH && mouseY >= paletteY && mouseY < paletteY + SWATCH;
            if (COLORS.get(i) == selected || hovered) {
                graphics.renderOutline(x - 2, paletteY - 2, SWATCH + 4, SWATCH + 4, COLORS.get(i) == selected ? UiColors.AMBER : UiColors.TEXT_SECONDARY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (paletteY >= 0 && mouseY >= paletteY && mouseY < paletteY + SWATCH) {
            for (int i = 0; i < COLORS.size(); i++) {
                int x = left + 12 + i * (SWATCH + 2);
                if (mouseX >= x && mouseX < x + SWATCH) {
                    if (ClientFactionCache.local().isPresent()) {
                        send(FactionSync.Action.SET_COLOR, "", COLORS.get(i).getId());
                    } else {
                        newColor = COLORS.get(i);
                    }
                    com.projectrebirth.client.ui.UiSounds.CLICK.play();
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
