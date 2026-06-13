package com.worldhost.client.gui;

import com.worldhost.WorldHostMod;
import com.worldhost.client.GlobalHostManager;
import com.worldhost.client.WorldDiscoveryClient;
import com.worldhost.client.WorldHostClient;
import com.worldhost.network.WorldHostNetwork;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class WorldHostScreen extends Screen {

    private static final int PANEL_W = 290;
    private static final int PANEL_H = 340;
    private static final int BG_COLOR   = 0xDD1A1A2E;
    private static final int ACCENT_CLR = 0xFF0F3460;

    public WorldHostScreen() {
        super(Text.literal("World Host"));
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelX = cx - PANEL_W / 2;
        int panelY = cy - PANEL_H / 2;
        int btnX = panelX + 20;
        int btnW = PANEL_W - 40;
        int y = panelY + 60;

        WorldDiscoveryClient discovery = WorldHostClient.getDiscovery();
        boolean inSinglePlayer = MinecraftClient.getInstance().getServer() != null;
        boolean globalOpen = GlobalHostManager.isOpen();

        // ── Открыть / закрыть глобально (только в одиночном мире) ──────────
        if (inSinglePlayer) {
            if (!globalOpen) {
                addDrawableChild(makeBtn(btnX, y, btnW, 24,
                        "§a🌐 Открыть для всего мира",
                        btn -> {
                            this.close();
                            MinecraftClient.getInstance().setScreen(new GlobalOpenScreen(this, discovery));
                        }));
            } else {
                addDrawableChild(makeBtn(btnX, y, btnW, 24,
                        "§c■ Закрыть глобальный доступ",
                        btn -> {
                            GlobalHostManager.close(discovery);
                            WorldHostClient.lanOpen = false;
                            ClientPlayNetworking.send(WorldHostNetwork.CLOSE_LAN, PacketByteBufs.empty());
                            this.close();
                        }));
            }
            y += 30;

            // LAN (только локальная сеть) — дополнительный вариант
            if (!WorldHostClient.lanOpen) {
                addDrawableChild(makeBtn(btnX, y, btnW, 22,
                        "§7▶ Открыть только в LAN",
                        btn -> {
                            this.close();
                            MinecraftClient.getInstance().setScreen(new OpenWorldScreen(this));
                        }));
            } else {
                addDrawableChild(makeBtn(btnX, y, btnW, 22,
                        "§8■ Закрыть LAN",
                        btn -> {
                            ClientPlayNetworking.send(WorldHostNetwork.CLOSE_LAN, PacketByteBufs.empty());
                            WorldHostClient.lanOpen = false;
                            this.close();
                        }));
            }
            y += 28;
        }

        // ── Войти в мир другого игрока ──────────────────────────────────────
        addDrawableChild(makeBtn(btnX, y, btnW, 24,
                "§b🌐 Войти в мир (по интернету)",
                btn -> {
                    this.close();
                    MinecraftClient.getInstance().setScreen(new JoinWorldScreen(this, discovery));
                }));
        y += 30;

        // ── Панель управления ────────────────────────────────────────────────
        addDrawableChild(makeBtn(btnX, y, btnW, 22,
                "§e⚙ Панель управления",
                btn -> {
                    this.close();
                    MinecraftClient.getInstance().setScreen(new HostControlPanelScreen(this));
                }));
        y += 28;

        // ── Настройки ────────────────────────────────────────────────────────
        addDrawableChild(makeBtn(btnX, y, btnW, 22,
                "§d✦ Настройки",
                btn -> {
                    this.close();
                    MinecraftClient.getInstance().setScreen(new SettingsScreen(this));
                }));
        y += 28;

        // ── Список забаненных ────────────────────────────────────────────────
        addDrawableChild(makeBtn(btnX, y, btnW, 22,
                "§5☰ Забаненные игроки",
                btn -> {
                    this.close();
                    MinecraftClient.getInstance().setScreen(new BanListScreen(this));
                }));
        y += 28;

        // ── Закрыть ──────────────────────────────────────────────────────────
        addDrawableChild(makeBtn(btnX, y, btnW, 20,
                "§7✖ Закрыть [Delete]",
                btn -> this.close()));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelX = cx - PANEL_W / 2;
        int panelY = cy - PANEL_H / 2;

        // shadow
        ctx.fill(panelX + 4, panelY + 4, panelX + PANEL_W + 4, panelY + PANEL_H + 4, 0x88000000);
        // bg + borders
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG_COLOR);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 2, 0xFF0F3460);
        ctx.fill(panelX, panelY + PANEL_H - 2, panelX + PANEL_W, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX, panelY, panelX + 2, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX + PANEL_W - 2, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF0F3460);
        // header
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 52, ACCENT_CLR);

        ctx.drawCenteredTextWithShadow(textRenderer, "§b⊕ World Host", cx, panelY + 8, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "§7Хостинг мира через интернет", cx, panelY + 20, 0xAAAAAA);

        // status line
        String status;
        if (GlobalHostManager.isOpen()) {
            status = "§a● ГЛОБАЛЬНО  §8код: §f" + GlobalHostManager.getSessionCode()
                   + "  §8" + GlobalHostManager.getExternalIp() + ":" + GlobalHostManager.getPort();
        } else if (WorldHostClient.lanOpen) {
            status = "§e● LAN  порт: §f" + WorldHostClient.lanPort;
        } else {
            status = "§c● ЗАКРЫТ";
        }
        ctx.drawCenteredTextWithShadow(textRenderer, status, cx, panelY + 34, 0xFFFFFF);

        // player count
        int cnt = 1;
        if (MinecraftClient.getInstance().getNetworkHandler() != null)
            cnt = MinecraftClient.getInstance().getNetworkHandler().getPlayerList().size();
        ctx.drawTextWithShadow(textRenderer, "§7Игроков: §f" + cnt, panelX + 10, panelY + PANEL_H - 14, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§8v1.0.0", panelX + PANEL_W - 36, panelY + PANEL_H - 14, 0x666666);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 261 /* Delete */) { this.close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private ButtonWidget makeBtn(int x, int y, int w, int h, String label, ButtonWidget.PressAction a) {
        return ButtonWidget.builder(Text.literal(label), a).dimensions(x, y, w, h).build();
    }
}
