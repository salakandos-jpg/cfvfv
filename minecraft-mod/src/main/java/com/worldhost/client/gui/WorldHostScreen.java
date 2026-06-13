package com.worldhost.client.gui;

import com.worldhost.WorldHostMod;
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
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public class WorldHostScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 300;
    private static final int BG_COLOR   = 0xDD1A1A2E;
    private static final int BORDER_CLR = 0xFF16213E;
    private static final int ACCENT_CLR = 0xFF0F3460;
    private static final int BTN_GREEN  = 0xFF27AE60;
    private static final int BTN_RED    = 0xFFC0392B;
    private static final int BTN_BLUE   = 0xFF2980B9;
    private static final int BTN_GOLD   = 0xFFF39C12;

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
        int startY = panelY + 60;

        if (!WorldHostClient.lanOpen) {
            addDrawableChild(makeButton(btnX, startY, btnW, 24,
                    "§a▶ Открыть мир для игроков",
                    btn -> {
                        this.close();
                        MinecraftClient.getInstance().setScreen(new OpenWorldScreen(this));
                    }
            ));
        } else {
            addDrawableChild(makeButton(btnX, startY, btnW, 24,
                    "§c■ Закрыть мир",
                    btn -> {
                        ClientPlayNetworking.send(WorldHostNetwork.CLOSE_LAN, PacketByteBufs.empty());
                        this.close();
                    }
            ));
        }

        addDrawableChild(makeButton(btnX, startY + 34, btnW, 24,
                "§b⚙ Панель управления",
                btn -> {
                    this.close();
                    MinecraftClient.getInstance().setScreen(new HostControlPanelScreen(this));
                }
        ));

        addDrawableChild(makeButton(btnX, startY + 68, btnW, 24,
                "§e✦ Настройки World Host",
                btn -> {
                    this.close();
                    MinecraftClient.getInstance().setScreen(new SettingsScreen(this));
                }
        ));

        addDrawableChild(makeButton(btnX, startY + 102, btnW, 24,
                "§d☰ Список забаненных",
                btn -> {
                    this.close();
                    MinecraftClient.getInstance().setScreen(new BanListScreen(this));
                }
        ));

        addDrawableChild(makeButton(btnX, startY + 136, btnW, 24,
                "§7✖ Закрыть",
                btn -> this.close()
        ));
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
        // background
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG_COLOR);
        // border
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 2, 0xFF0F3460);
        ctx.fill(panelX, panelY + PANEL_H - 2, panelX + PANEL_W, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX, panelY, panelX + 2, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX + PANEL_W - 2, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF0F3460);
        // header bar
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 48, ACCENT_CLR);

        // title
        ctx.drawCenteredTextWithShadow(textRenderer, "§bWorld Host", cx, panelY + 8, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "§7Управление миром", cx, panelY + 22, 0xAAAAAA);

        // status badge
        String status = WorldHostClient.lanOpen
                ? "§a● ОТКРЫТ  Порт: " + WorldHostClient.lanPort
                : "§c● ЗАКРЫТ";
        ctx.drawCenteredTextWithShadow(textRenderer, status, cx, panelY + 36, 0xFFFFFF);

        // player count
        int playerCount = MinecraftClient.getInstance().world != null
                ? MinecraftClient.getInstance().getNetworkHandler() != null
                  ? MinecraftClient.getInstance().getNetworkHandler().getPlayerList().size() : 1
                : 1;
        ctx.drawTextWithShadow(textRenderer, "§7Игроков онлайн: §f" + playerCount, panelX + 10, panelY + PANEL_H - 18, 0xFFFFFF);

        String ver = "§8v" + WorldHostMod.class.getPackage().getImplementationVersion();
        ctx.drawTextWithShadow(textRenderer, ver != null ? ver : "§8v1.0.0", panelX + PANEL_W - 40, panelY + PANEL_H - 18, 0xFFFFFF);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private ButtonWidget makeButton(int x, int y, int width, int height, String label, ButtonWidget.PressAction action) {
        return ButtonWidget.builder(Text.literal(label), action)
                .dimensions(x, y, width, height)
                .build();
    }
}
