package com.projectrebirth.client.screen;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.projectrebirth.factions.ClientFactionCache;
import com.projectrebirth.factions.FactionSync;
import com.projectrebirth.factions.FactionView;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Faction window (key G). Without a faction: create one (name and colour) and answer invitations.
 * In a faction: members, and for the leader invitations, kicking, handing over leadership, colour and friendly fire.
 * Everything is only a request: the server checks it and answers with a message shown at the bottom.
 */
public class FactionScreen extends Screen {
    private static final int WIDTH = 240;
    private static final int HEIGHT = 214;
    private static final int MAX_MEMBER_ROWS = 5;
    private static final int SWATCH = 12;
    private static final List<ChatFormatting> COLORS = Arrays.stream(ChatFormatting.values())
            .filter(ChatFormatting::isColor).toList();

    private int left;
    private int top;
    private int seenVersion = -1;
    private EditBox nameBox;
    private String typedName = "";
    private ChatFormatting newColor = ChatFormatting.AQUA;
    private int paletteY;
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
        Optional<FactionView> own = ownFaction();
        if (own.isPresent() != builtAsMember) {
            typedName = "";
            builtAsMember = own.isPresent();
        }
        if (own.isPresent()) {
            initMember(own.get());
        } else {
            initNoFaction();
        }
    }

    private Optional<FactionView> ownFaction() {
        return minecraft != null && minecraft.player != null ? ClientFactionCache.of(minecraft.player.getUUID()) : Optional.empty();
    }

    private boolean isLeader(FactionView faction) {
        return minecraft != null && minecraft.player != null && faction.leader().equals(minecraft.player.getUUID());
    }

    // ---------- no faction ----------

    private void initNoFaction() {
        int y = top + 34;
        nameBox = new EditBox(font, left + 10, y, 150, 20, Component.translatable("screen.projectrebirth.faction.name"));
        nameBox.setMaxLength(16);
        nameBox.setValue(typedName);
        nameBox.setHint(Component.translatable("screen.projectrebirth.faction.name").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.faction.create"),
                b -> send(FactionSync.Action.CREATE, nameBox.getValue(), newColor.getId())).bounds(left + 166, y, 64, 20).build());
        paletteY = y + 26;

        int row = top + 100;
        for (UUID factionId : ClientFactionCache.invites()) {
            if (row > top + HEIGHT - 40) {
                break;
            }
            Optional<FactionView> faction = ClientFactionCache.byId(factionId);
            if (faction.isEmpty()) {
                continue;
            }
            addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.faction.accept"),
                    b -> send(FactionSync.Action.ACCEPT, factionId.toString(), 0)).bounds(left + 120, row, 54, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.faction.decline"),
                    b -> send(FactionSync.Action.DECLINE, factionId.toString(), 0)).bounds(left + 176, row, 54, 18).build());
            row += 22;
        }
    }

    // ---------- member ----------

    private void initMember(FactionView faction) {
        boolean leader = isLeader(faction);
        int row = top + 46;
        int shown = 0;
        for (FactionView.Member member : faction.members()) {
            if (shown++ >= MAX_MEMBER_ROWS) {
                break;
            }
            if (leader && !member.id().equals(faction.leader())) {
                String id = member.id().toString();
                addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.faction.promote"),
                        b -> send(FactionSync.Action.PROMOTE, id, 0)).bounds(left + 130, row - 2, 48, 16).build());
                addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.faction.kick"),
                        b -> send(FactionSync.Action.KICK, id, 0)).bounds(left + 180, row - 2, 50, 16).build());
            }
            row += 18;
        }
        int controls = top + 46 + MAX_MEMBER_ROWS * 18 + 6;
        if (leader) {
            nameBox = new EditBox(font, left + 10, controls, 150, 18, Component.translatable("screen.projectrebirth.faction.player"));
            nameBox.setMaxLength(16);
            nameBox.setValue(typedName);
            nameBox.setHint(Component.translatable("screen.projectrebirth.faction.player").withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(nameBox);
            addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.faction.invite"),
                    b -> send(FactionSync.Action.INVITE, nameBox.getValue(), 0)).bounds(left + 166, controls, 64, 18).build());
            paletteY = controls + 24;
            Button fire = Button.builder(Component.translatable(faction.friendlyFire()
                                    ? "screen.projectrebirth.faction.friendly_fire_on" : "screen.projectrebirth.faction.friendly_fire_off"),
                            b -> send(FactionSync.Action.SET_FRIENDLY_FIRE, Boolean.toString(!faction.friendlyFire()), 0))
                    .bounds(left + 10, controls + 42, 150, 18).build();
            if (ClientFactionCache.friendlyFireLocked()) {
                fire.active = false;
                fire.setTooltip(Tooltip.create(Component.translatable("screen.projectrebirth.faction.friendly_fire_locked")));
            }
            addRenderableWidget(fire);
        } else {
            nameBox = null;
            paletteY = -1;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.faction.leave"),
                b -> send(FactionSync.Action.LEAVE, "", 0)).bounds(left + 166, controls + 42, 64, 18).build());
    }

    // ---------- shared ----------

    private void send(FactionSync.Action action, String arg, int color) {
        PacketDistributor.sendToServer(new FactionSync.ActionPayload(action, arg, color));
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
        Optional<FactionView> own = ownFaction();
        if (own.isPresent()) {
            renderMember(graphics, own.get());
        } else {
            renderNoFaction(graphics);
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
            if (row > top + HEIGHT - 40) {
                break;
            }
            Optional<FactionView> faction = ClientFactionCache.byId(factionId);
            if (faction.isPresent()) {
                graphics.drawString(font, Component.literal(faction.get().name()).withStyle(faction.get().color()), left + 10, row + 5, 0xFFFFFF);
                row += 22;
                any = true;
            }
        }
        if (!any) {
            graphics.drawString(font, Component.translatable("screen.projectrebirth.faction.no_invites"), left + 10, row + 5, 0x808080);
        }
    }

    private void renderMember(GuiGraphics graphics, FactionView faction) {
        graphics.drawCenteredString(font, Component.literal(faction.name()).withStyle(faction.color(), ChatFormatting.BOLD),
                left + WIDTH / 2, top + 20, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.projectrebirth.faction.summary", faction.members().size(),
                        Component.translatable(faction.friendlyFire() ? "screen.projectrebirth.faction.on" : "screen.projectrebirth.faction.off")),
                left + WIDTH / 2, top + 32, 0xA0A0A0);
        int row = top + 46;
        int shown = 0;
        for (FactionView.Member member : faction.members()) {
            if (shown >= MAX_MEMBER_ROWS) {
                graphics.drawString(font, Component.translatable("screen.projectrebirth.faction.more",
                        faction.members().size() - MAX_MEMBER_ROWS), left + 10, row, 0x808080);
                break;
            }
            boolean online = minecraft != null && minecraft.getConnection() != null && minecraft.getConnection().getPlayerInfo(member.id()) != null;
            MutableComponent line = Component.literal(member.id().equals(faction.leader()) ? "★ " : "  ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(member.name()).withStyle(online ? ChatFormatting.WHITE : ChatFormatting.GRAY));
            graphics.drawString(font, line, left + 10, row + 2, 0xFFFFFF);
            row += 18;
            shown++;
        }
    }

    private void renderPalette(GuiGraphics graphics, int mouseX, int mouseY, ChatFormatting selected) {
        for (int i = 0; i < COLORS.size(); i++) {
            int x = left + 10 + i * (SWATCH + 2);
            int color = 0xFF000000 | COLORS.get(i).getColor();
            graphics.fill(x, paletteY, x + SWATCH, paletteY + SWATCH, color);
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
                    if (ownFaction().isPresent()) {
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
