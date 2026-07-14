package com.hlysine.create_power_loader.client.screen;

import com.hlysine.create_power_loader.network.C2SAddCoOwnerPacket;
import com.hlysine.create_power_loader.network.C2SRemoveCoOwnerPacket;
import com.hlysine.create_power_loader.network.C2SToggleTickLoadingPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.hlysine.create_power_loader.network.S2COwnerScreenPacket;
import com.mojang.authlib.properties.PropertyMap;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owner management screen for chunk loaders.
 *
 * <p>Layout (all programmatic — no custom texture needed):
 * <pre>
 *  ┌──────────────────────────────────────────────────┐
 *  │  Chunk Loader Ownership            [X]           │
 *  │  ⚠ Suppressed (inactive owner)  [badge]         │
 *  │                                                  │
 *  │  Owner                                           │
 *  │  ┌──────┐                                        │
 *  │  │ Head │  PlayerName  (Last seen: X hours ago)  │
 *  │  └──────┘                                        │
 *  │                                                  │
 *  │  Co-owners  (3 / 7)                              │
 *  │  ┌────┐ ┌────┐ ┌────┐ ┌────┐                    │
 *  │  │Head│ │Head│ │Head│ │ +  │ ...                 │
 *  │  └────┘ └────┘ └────┘ └────┘                    │
 *  │                                                  │
 *  │  Add player: [_______________] [Add]             │
 *  │  [  Tick Loading: ON/OFF  ]                      │
 *  └──────────────────────────────────────────────────┘
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class ChunkLoaderOwnerScreen extends Screen {

    // --- Layout constants ---
    private static final int PANEL_W = 270;
    private static final int PANEL_H = 240;
    private static final int SLOT_W = 48;
    private static final int SLOT_H = 56;
    private static final int SLOT_GAP = 6;
    private static final int SLOTS_PER_ROW = 4;

    // --- Colors ---
    private static final int COL_PANEL_BG     = 0xFF1A1A2E;
    private static final int COL_PANEL_BORDER  = 0xFF4A4A8A;
    private static final int COL_SECTION_LABEL = 0xFFAAA0C8;
    private static final int COL_SLOT_BG       = 0xFF252540;
    private static final int COL_SLOT_OWNER    = 0xFF1E2E4E;
    private static final int COL_SLOT_HOVER    = 0xFF353560;
    private static final int COL_SLOT_BORDER   = 0xFF5A5A9A;
    private static final int COL_SLOT_REMOVE_BG = 0xCC993333;
    private static final int COL_SUPPRESSED_BG  = 0xCC5C1E1E;
    private static final int COL_TEXT_PRIMARY   = 0xFFEEEEFF;
    private static final int COL_TEXT_DIM       = 0xFF9090B0;
    private static final int COL_TEXT_RED       = 0xFFFF6060;
    private static final int COL_TEXT_GREEN     = 0xFF60FF80;

    // --- State ---
    private BlockPos blockPos;
    @Nullable private UUID ownerUUID;
    private String ownerName;
    private long ownerLastSeenEpoch;
    private List<S2COwnerScreenPacket.OwnerEntry> coOwners;
    private boolean tickLoadingEnabled;
    private boolean viewerIsOwner;
    private boolean isSuppressed;

    // --- Widgets ---
    private EditBox usernameField;
    private Button addButton;
    private Button tickLoadingButton;

    // --- Interaction state ---
    private int hoveredSlot = -1;   // -1 = none, 0 = owner, 1-7 = co-owners
    private int removeConfirmSlot = -1; // slot index awaiting removal confirmation

    // Computed layout (set in init)
    private int panelX, panelY;
    private int ownerSlotX, ownerSlotY;
    private int coOwnerRowX, coOwnerRow1Y, coOwnerRow2Y;

    public ChunkLoaderOwnerScreen(S2COwnerScreenPacket packet) {
        super(Component.literal("Chunk Loader Ownership"));
        applyPacket(packet);
    }

    /** Refreshes state without closing/reopening the screen. */
    public void refreshData(S2COwnerScreenPacket packet) {
        applyPacket(packet);
        if (tickLoadingButton != null) updateTickLoadingButton();
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    private void applyPacket(S2COwnerScreenPacket packet) {
        this.blockPos           = packet.pos();
        this.ownerUUID          = packet.ownerUUID();
        this.ownerName          = packet.ownerName();
        this.ownerLastSeenEpoch = packet.ownerLastSeenEpoch();
        this.coOwners           = new ArrayList<>(packet.coOwners());
        this.tickLoadingEnabled = packet.tickLoadingEnabled();
        this.viewerIsOwner      = packet.viewerIsOwner();
        this.isSuppressed       = packet.isSuppressed();
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;

        // Owner slot position
        ownerSlotX = panelX + 10;
        ownerSlotY = panelY + 50;

        // Co-owner slots start (4 per row)
        coOwnerRowX  = panelX + 10;
        coOwnerRow1Y = panelY + 130;
        coOwnerRow2Y = coOwnerRow1Y + SLOT_H + SLOT_GAP;

        // --- Username field ---
        int fieldY = panelY + PANEL_H - 58;
        usernameField = new EditBox(font, panelX + 80, fieldY, 110, 18,
                Component.literal("Player name"));
        usernameField.setMaxLength(64);
        usernameField.setHint(Component.literal("username...").withStyle(ChatFormatting.DARK_GRAY));
        addWidget(usernameField);

        // --- Add button ---
        addButton = Button.builder(Component.literal("Add"), b -> sendAddCoOwner())
                .pos(panelX + 196, fieldY - 1)
                .size(44, 20)
                .build();
        addRenderableWidget(addButton);
        addButton.active = viewerIsOwner;

        // --- Tick Loading toggle ---
        tickLoadingButton = Button.builder(tickLoadingLabel(), b -> sendToggleTickLoading())
                .pos(panelX + 10, panelY + PANEL_H - 34)
                .size(PANEL_W - 20, 20)
                .build();
        addRenderableWidget(tickLoadingButton);

        // --- Close button (ESC also works) ---
        Button closeBtn = Button.builder(Component.literal("✕"), b -> onClose())
                .pos(panelX + PANEL_W - 24, panelY + 4)
                .size(20, 14)
                .build();
        addRenderableWidget(closeBtn);
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dim background
        renderBackground(g, mouseX, mouseY, partialTick);

        // Panel
        drawPanel(g);

        // Title
        g.drawString(font, "Chunk Loader Ownership", panelX + 10, panelY + 8, COL_TEXT_PRIMARY, false);
        g.drawString(font, blockPos.toShortString(), panelX + 10, panelY + 18, COL_TEXT_DIM, false);

        // Suppression banner
        if (isSuppressed) {
            g.fill(panelX + 10, panelY + 30, panelX + PANEL_W - 10, panelY + 43, COL_SUPPRESSED_BG);
            g.drawCenteredString(font, "⚠  Loaders suppressed — owner inactive",
                    panelX + PANEL_W / 2, panelY + 33, COL_TEXT_RED);
        }

        // Owner section
        g.drawString(font, "Owner", panelX + 10, ownerSlotY - 12, COL_SECTION_LABEL, false);
        if (ownerUUID != null) {
            renderOwnerSlot(g, mouseX, mouseY, ownerSlotX, ownerSlotY,
                    ownerUUID, ownerName, ownerLastSeenEpoch, true);
        } else {
            // Unclaimed — draw an empty slot with a "Claim" label
            drawSlotBox(g, ownerSlotX, ownerSlotY, COL_SLOT_BG, COL_SLOT_BORDER);
            g.drawCenteredString(font, "Unclaimed", ownerSlotX + SLOT_W / 2, ownerSlotY + SLOT_H / 2 - 4, COL_TEXT_DIM);
        }

        // Co-owners section
        int coOwnerCount = coOwners.size();
        g.drawString(font, "Co-owners  (" + coOwnerCount + " / 7)", panelX + 10, coOwnerRow1Y - 14, COL_SECTION_LABEL, false);

        for (int i = 0; i < 7; i++) {
            int row = i / SLOTS_PER_ROW;
            int col = i % SLOTS_PER_ROW;
            int sx = coOwnerRowX + col * (SLOT_W + SLOT_GAP);
            int sy = (row == 0 ? coOwnerRow1Y : coOwnerRow2Y);
            int logicalSlot = i + 1; // slot 0 = owner, 1-7 = co-owners

            if (i < coOwners.size()) {
                S2COwnerScreenPacket.OwnerEntry entry = coOwners.get(i);
                renderOwnerSlot(g, mouseX, mouseY, sx, sy, entry.uuid(), entry.name(), entry.lastSeenEpoch(), false);
                // Remove X button (owner-only)
                if (viewerIsOwner) {
                    drawRemoveButton(g, sx + SLOT_W - 12, sy + 1, logicalSlot == removeConfirmSlot);
                }
            } else {
                // Empty slot
                boolean hov = isInSlot(mouseX, mouseY, sx, sy);
                drawSlotBox(g, sx, sy, hov ? COL_SLOT_HOVER : COL_SLOT_BG, COL_SLOT_BORDER);
                g.drawCenteredString(font, "+", sx + SLOT_W / 2, sy + SLOT_H / 2 - 4, COL_TEXT_DIM);
            }
        }

        // Add player label
        g.drawString(font, "Add player:", panelX + 10, panelY + PANEL_H - 53, COL_TEXT_DIM, false);

        // Username field
        usernameField.render(g, mouseX, mouseY, partialTick);

        // Buttons
        super.render(g, mouseX, mouseY, partialTick);

        // Tooltips
        renderTooltips(g, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphics g) {
        // Background
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, COL_PANEL_BG);
        // Border (1px solid)
        g.fill(panelX,               panelY,               panelX + PANEL_W, panelY + 1,               COL_PANEL_BORDER);
        g.fill(panelX,               panelY + PANEL_H - 1, panelX + PANEL_W, panelY + PANEL_H,          COL_PANEL_BORDER);
        g.fill(panelX,               panelY,               panelX + 1,        panelY + PANEL_H,          COL_PANEL_BORDER);
        g.fill(panelX + PANEL_W - 1, panelY,               panelX + PANEL_W,  panelY + PANEL_H,          COL_PANEL_BORDER);
    }

    private void drawSlotBox(GuiGraphics g, int x, int y, int bg, int border) {
        g.fill(x, y, x + SLOT_W, y + SLOT_H, bg);
        g.fill(x,            y,            x + SLOT_W, y + 1,        border);
        g.fill(x,            y + SLOT_H-1, x + SLOT_W, y + SLOT_H,   border);
        g.fill(x,            y,            x + 1,       y + SLOT_H,   border);
        g.fill(x + SLOT_W-1, y,            x + SLOT_W,  y + SLOT_H,  border);
    }

    private void renderOwnerSlot(GuiGraphics g, int mx, int my,
                                  int x, int y,
                                  UUID uuid, String name, long lastSeenEpoch,
                                  boolean isOwner) {
        boolean hovered = isInSlot(mx, my, x, y);
        int bg = isOwner ? COL_SLOT_OWNER : (hovered ? COL_SLOT_HOVER : COL_SLOT_BG);
        drawSlotBox(g, x, y, bg, COL_SLOT_BORDER);

        // Player head item
        ItemStack headItem = makeHeadItem(uuid, name);
        g.renderItem(headItem, x + 8, y + 6);

        // Name (truncated)
        String displayName = name.length() > 7 ? name.substring(0, 6) + "…" : name;
        g.drawCenteredString(font, displayName, x + SLOT_W / 2, y + SLOT_H - 14, COL_TEXT_PRIMARY);

        // Owner crown badge
        if (isOwner) {
            g.drawString(font, "♛", x + 2, y + 2, 0xFFFFD700, false);
        }
    }

    private void drawRemoveButton(GuiGraphics g, int x, int y, boolean confirming) {
        int color = confirming ? 0xFFFF4444 : COL_SLOT_REMOVE_BG;
        g.fill(x, y, x + 11, y + 11, color);
        g.drawCenteredString(font, "✕", x + 5, y + 1, 0xFFFFFFFF);
    }

    private void renderTooltips(GuiGraphics g, int mx, int my) {
        // Owner slot tooltip
        if (ownerUUID != null && isInSlot(mx, my, ownerSlotX, ownerSlotY)) {
            String timeStr = formatTimeSince(ownerLastSeenEpoch);
            g.renderTooltip(font, toSeqs(List.of(
                    Component.literal(ownerName).withStyle(ChatFormatting.GOLD),
                    Component.literal("Owner").withStyle(ChatFormatting.YELLOW),
                    Component.literal("Last seen: " + timeStr).withStyle(ChatFormatting.GRAY)
            )), mx, my);
            return;
        }

        // Co-owner slot tooltips
        for (int i = 0; i < coOwners.size(); i++) {
            int row = i / SLOTS_PER_ROW;
            int col = i % SLOTS_PER_ROW;
            int sx = coOwnerRowX + col * (SLOT_W + SLOT_GAP);
            int sy = row == 0 ? coOwnerRow1Y : coOwnerRow2Y;
            if (isInSlot(mx, my, sx, sy)) {
                S2COwnerScreenPacket.OwnerEntry entry = coOwners.get(i);
                String timeStr = formatTimeSince(entry.lastSeenEpoch());
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(entry.name()).withStyle(ChatFormatting.GREEN));
                lines.add(Component.literal("Co-owner").withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal("Last seen: " + timeStr).withStyle(ChatFormatting.GRAY));
                if (viewerIsOwner) lines.add(Component.literal("Click ✕ to remove").withStyle(ChatFormatting.RED));
                g.renderTooltip(font, toSeqs(lines), mx, my);
                return;
            }
        }
    }

    /** Converts a list of Components to FormattedCharSequence for use with GuiGraphics.renderTooltip. */
    private static List<net.minecraft.util.FormattedCharSequence> toSeqs(List<Component> components) {
        return components.stream()
                .map(Component::getVisualOrderText)
                .collect(java.util.stream.Collectors.toList());
    }

    // =========================================================================
    // Mouse handling
    // =========================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int imx = (int) mx, imy = (int) my;

        // Check remove-X buttons on co-owner slots
        if (viewerIsOwner && button == 0) {
            for (int i = 0; i < coOwners.size(); i++) {
                int row = i / SLOTS_PER_ROW;
                int col = i % SLOTS_PER_ROW;
                int sx = coOwnerRowX + col * (SLOT_W + SLOT_GAP);
                int sy = row == 0 ? coOwnerRow1Y : coOwnerRow2Y;
                int rx = sx + SLOT_W - 12, ry = sy + 1;
                if (imx >= rx && imx <= rx + 11 && imy >= ry && imy <= ry + 11) {
                    int logicalSlot = i + 1;
                    if (removeConfirmSlot == logicalSlot) {
                        // Confirmed — send remove packet
                        PacketDistributor.sendToServer(
                                new C2SRemoveCoOwnerPacket(blockPos, coOwners.get(i).uuid()));
                        removeConfirmSlot = -1;
                    } else {
                        removeConfirmSlot = logicalSlot;
                    }
                    return true;
                }
            }
        }

        // Reset confirm if clicking elsewhere
        removeConfirmSlot = -1;

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (usernameField.isFocused() && keyCode == 257 /* ENTER */) {
            sendAddCoOwner();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // =========================================================================
    // Network helpers
    // =========================================================================

    private void sendAddCoOwner() {
        String name = usernameField.getValue().trim();
        if (name.isEmpty()) return;
        PacketDistributor.sendToServer(new C2SAddCoOwnerPacket(blockPos, name));
        usernameField.setValue("");
    }

    private void sendToggleTickLoading() {
        PacketDistributor.sendToServer(new C2SToggleTickLoadingPacket(blockPos));
    }

    private void updateTickLoadingButton() {
        if (tickLoadingButton != null) {
            tickLoadingButton.setMessage(tickLoadingLabel());
        }
    }

    private Component tickLoadingLabel() {
        return tickLoadingEnabled
                ? Component.literal("⬜ Tick Loading: ON").withStyle(ChatFormatting.GREEN)
                : Component.literal("⬜ Tick Loading: OFF").withStyle(ChatFormatting.RED);
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private boolean isInSlot(int mx, int my, int sx, int sy) {
        return mx >= sx && mx <= sx + SLOT_W && my >= sy && my <= sy + SLOT_H;
    }

    private static ItemStack makeHeadItem(UUID uuid, String name) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        try {
            stack.set(DataComponents.PROFILE,
                    new ResolvableProfile(Optional.of(name), Optional.of(uuid), new PropertyMap()));
        } catch (Exception ignored) {
            // Fall back to plain skull
        }
        return stack;
    }

    private static String formatTimeSince(long epochSeconds) {
        if (epochSeconds <= 0) return "never";
        long seconds = Instant.now().getEpochSecond() - epochSeconds;
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        return days + "d ago";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
