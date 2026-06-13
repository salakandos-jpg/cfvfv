package com.worldhost.client.gui;

import com.worldhost.network.WorldHostNetwork;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Environment(EnvType.CLIENT)
public class BanListScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 340;
    private static final int BG_COLOR   = 0xDD1A1A2E;
    private static final int ACCENT_CLR = 0xFF0F3460;
    private static final int ROW_EVEN   = 0x22FFFFFF;
    private static final int ROW_ODD    = 0x11FFFFFF;
    private static final int ROW_SEL    = 0x44903020;

    private final Screen parent;
    private int selectedIndex = -1;

    private List<String> bannedNames = new ArrayList<>();
    private TextFieldWidget unbanField;

    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 8;
    private static final int ROW_H = 20;

    public BanListScreen(Screen parent) {
        super(Text.literal("Список забаненных"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelX = cx - PANEL_W / 2;
        int panelY = cy - PANEL_H / 2;
        int lx = panelX + 15;
        int fw = PANEL_W - 30;

        // Load ban list from local server (only works in single-player / LAN)
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getServer() != null) {
            BannedPlayerList list = mc.getServer().getPlayerManager().getUserBanList();
            bannedNames = new ArrayList<>();
            for (var entry : list.values()) {
                bannedNames.add(entry.getProfile().getName());
            }
        }

        // Scroll
        addDrawableChild(ButtonWidget.builder(Text.literal("▲"), btn -> { if (scrollOffset > 0) scrollOffset--; })
                .dimensions(lx + fw - 20, panelY + 55, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▼"), btn -> {
            if (scrollOffset < Math.max(0, bannedNames.size() - VISIBLE_ROWS)) scrollOffset++;
        }).dimensions(lx + fw - 20, panelY + 55 + ROW_H * VISIBLE_ROWS - 20, 20, 20).build());

        // Manual unban field
        unbanField = new TextFieldWidget(textRenderer, lx, panelY + PANEL_H - 76, fw - 100, 18, Text.literal("Ник"));
        unbanField.setMaxLength(32);
        unbanField.setPlaceholder(Text.literal("Ник игрока..."));
        addDrawableChild(unbanField);

        addDrawableChild(ButtonWidget.builder(Text.literal("§a✔ Разбанить"), btn -> {
            String name = selectedIndex >= 0 && selectedIndex < bannedNames.size()
                    ? bannedNames.get(selectedIndex)
                    : unbanField.getText().trim();
            if (!name.isEmpty()) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeString(name);
                ClientPlayNetworking.send(WorldHostNetwork.UNBAN_PLAYER, buf);
                bannedNames.remove(name);
                selectedIndex = -1;
                unbanField.setText("");
            }
        }).dimensions(lx + fw - 96, panelY + PANEL_H - 76, 96, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("§7← Назад"),
                btn -> MinecraftClient.getInstance().setScreen(parent))
                .dimensions(lx + fw / 2 - 45, panelY + PANEL_H - 40, 90, 24).build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelX = cx - PANEL_W / 2;
        int panelY = cy - PANEL_H / 2;
        int lx = panelX + 15;
        int fw = PANEL_W - 30;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= bannedNames.size()) break;
            int rowY = panelY + 55 + i * ROW_H;
            if (mouseX >= lx && mouseX <= lx + fw - 22 && mouseY >= rowY && mouseY < rowY + ROW_H) {
                selectedIndex = idx;
                unbanField.setText(bannedNames.get(idx));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount < 0) {
            if (scrollOffset < Math.max(0, bannedNames.size() - VISIBLE_ROWS)) scrollOffset++;
        } else {
            if (scrollOffset > 0) scrollOffset--;
        }
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelX = cx - PANEL_W / 2;
        int panelY = cy - PANEL_H / 2;
        int lx = panelX + 15;
        int fw = PANEL_W - 30;

        ctx.fill(panelX + 4, panelY + 4, panelX + PANEL_W + 4, panelY + PANEL_H + 4, 0x88000000);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG_COLOR);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 2, 0xFF0F3460);
        ctx.fill(panelX, panelY + PANEL_H - 2, panelX + PANEL_W, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX, panelY, panelX + 2, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX + PANEL_W - 2, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 48, ACCENT_CLR);

        ctx.drawCenteredTextWithShadow(textRenderer, "§d☰ Список забаненных", cx, panelY + 8, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "§7Управление заблокированными игроками", cx, panelY + 22, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "§7Всего забанено: §f" + bannedNames.size(), panelX + 15, panelY + 36, 0xFFFFFF);

        // Header
        ctx.fill(lx, panelY + 52, lx + fw - 22, panelY + 55, 0xFF0D2137);
        ctx.drawTextWithShadow(textRenderer, "§f  Ник игрока", lx + 4, panelY + 38, 0xFFFFFF);

        // Rows
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            int rowY = panelY + 55 + i * ROW_H;
            if (idx >= bannedNames.size()) {
                ctx.fill(lx, rowY, lx + fw - 22, rowY + ROW_H, i % 2 == 0 ? ROW_EVEN : ROW_ODD);
                continue;
            }
            String name = bannedNames.get(idx);
            int bg = (idx == selectedIndex) ? ROW_SEL : (i % 2 == 0 ? ROW_EVEN : ROW_ODD);
            ctx.fill(lx, rowY, lx + fw - 22, rowY + ROW_H, bg);
            ctx.drawTextWithShadow(textRenderer, "§c✖ §f" + name, lx + 4, rowY + 6, 0xFFFFFF);
        }

        if (bannedNames.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "§7Список заблокированных пуст", cx, panelY + 115, 0x888888);
        }

        ctx.drawTextWithShadow(textRenderer, "§7Разбанить вручную:", lx, panelY + PANEL_H - 92, 0xFFFFFF);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
