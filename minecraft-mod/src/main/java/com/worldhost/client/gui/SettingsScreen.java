package com.worldhost.client.gui;

import com.worldhost.WorldHostMod;
import com.worldhost.config.WorldHostConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class SettingsScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 420;
    private static final int BG_COLOR   = 0xDD1A1A2E;
    private static final int ACCENT_CLR = 0xFF0F3460;

    private final Screen parent;

    private TextFieldWidget motdField;
    private TextFieldWidget maxPlayersField;
    private TextFieldWidget idleTimeoutField;

    private boolean pvpEnabled;
    private boolean notifyOnJoin;
    private boolean notifyOnLeave;
    private boolean autoKickOnIdle;
    private boolean enableWhitelist;
    private boolean banChatMessages;
    private boolean showPlayerList;

    private static final String[] GAMEMODES    = {"SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"};
    private static final String[] GAMEMODE_RU  = {"Выживание", "Творческий", "Приключение", "Наблюдатель"};
    private int gameModeIndex = 0;
    private ButtonWidget gmButton;

    public SettingsScreen(Screen parent) {
        super(Text.literal("Настройки World Host"));
        this.parent = parent;
        WorldHostConfig cfg = WorldHostMod.config;
        pvpEnabled      = cfg.pvpEnabled;
        notifyOnJoin    = cfg.notifyOnJoin;
        notifyOnLeave   = cfg.notifyOnLeave;
        autoKickOnIdle  = cfg.autoKickOnIdle;
        enableWhitelist = cfg.enableWhitelist;
        banChatMessages = cfg.banChatMessages;
        showPlayerList  = cfg.showPlayerList;
        for (int i = 0; i < GAMEMODES.length; i++) {
            if (GAMEMODES[i].equals(cfg.defaultGameMode)) { gameModeIndex = i; break; }
        }
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelX = cx - PANEL_W / 2;
        int panelY = cy - PANEL_H / 2;
        int lx = panelX + 20;
        int fw = PANEL_W - 40;
        int bw = (fw - 8) / 2;
        int y = panelY + 60;

        motdField = new TextFieldWidget(textRenderer, lx, y + 14, fw, 18, Text.literal("MOTD"));
        motdField.setMaxLength(64);
        motdField.setText(WorldHostMod.config.motd);
        addDrawableChild(motdField);
        y += 40;

        maxPlayersField = new TextFieldWidget(textRenderer, lx, y + 14, fw, 18, Text.literal("Макс. игроков"));
        maxPlayersField.setMaxLength(4);
        maxPlayersField.setText(String.valueOf(WorldHostMod.config.maxPlayers));
        addDrawableChild(maxPlayersField);
        y += 40;

        gmButton = ButtonWidget.builder(
                Text.literal("Режим по умолч.: " + GAMEMODE_RU[gameModeIndex]),
                btn -> {
                    gameModeIndex = (gameModeIndex + 1) % GAMEMODES.length;
                    btn.setMessage(Text.literal("Режим по умолч.: " + GAMEMODE_RU[gameModeIndex]));
                }
        ).dimensions(lx, y, fw, 20).build();
        addDrawableChild(gmButton);
        y += 28;

        // Toggle buttons (two per row)
        ButtonWidget pvpBtn = buildToggle(lx, y, bw, "PvP", pvpEnabled, b -> pvpEnabled = b);
        ButtonWidget joinBtn = buildToggle(lx + bw + 8, y, bw, "Уведомл. входа", notifyOnJoin, b -> notifyOnJoin = b);
        addDrawableChild(pvpBtn);
        addDrawableChild(joinBtn);
        y += 28;

        ButtonWidget leaveBtn = buildToggle(lx, y, bw, "Уведомл. выхода", notifyOnLeave, b -> notifyOnLeave = b);
        ButtonWidget wlBtn = buildToggle(lx + bw + 8, y, bw, "Белый список", enableWhitelist, b -> enableWhitelist = b);
        addDrawableChild(leaveBtn);
        addDrawableChild(wlBtn);
        y += 28;

        ButtonWidget banMsgBtn = buildToggle(lx, y, bw, "Сообщ. о бане", banChatMessages, b -> banChatMessages = b);
        ButtonWidget plBtn = buildToggle(lx + bw + 8, y, bw, "Список игроков", showPlayerList, b -> showPlayerList = b);
        addDrawableChild(banMsgBtn);
        addDrawableChild(plBtn);
        y += 28;

        ButtonWidget idleBtn = buildToggle(lx, y, bw, "Авто-кик AFK", autoKickOnIdle, b -> autoKickOnIdle = b);
        addDrawableChild(idleBtn);

        idleTimeoutField = new TextFieldWidget(textRenderer, lx + bw + 8, y, bw, 20, Text.literal("Таймаут AFK"));
        idleTimeoutField.setMaxLength(4);
        idleTimeoutField.setText(String.valueOf(WorldHostMod.config.idleTimeoutMinutes));
        idleTimeoutField.setPlaceholder(Text.literal("мин."));
        addDrawableChild(idleTimeoutField);
        y += 36;

        addDrawableChild(ButtonWidget.builder(Text.literal("§a✔ Сохранить"), btn -> save())
                .dimensions(lx, panelY + PANEL_H - 38, bw, 24).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("§7← Назад"), btn -> MinecraftClient.getInstance().setScreen(parent))
                .dimensions(lx + bw + 8, panelY + PANEL_H - 38, bw, 24).build());
    }

    private ButtonWidget buildToggle(int x, int y, int w, String label, boolean initial, java.util.function.Consumer<Boolean> onChange) {
        boolean[] val = {initial};
        return ButtonWidget.builder(
                Text.literal((val[0] ? "§a✔ " : "§c✘ ") + label),
                btn -> {
                    val[0] = !val[0];
                    onChange.accept(val[0]);
                    btn.setMessage(Text.literal((val[0] ? "§a✔ " : "§c✘ ") + label));
                }
        ).dimensions(x, y, w, 20).build();
    }

    private void save() {
        WorldHostConfig cfg = WorldHostMod.config;
        cfg.motd            = motdField.getText().trim().isEmpty() ? "Добро пожаловать!" : motdField.getText().trim();
        cfg.maxPlayers      = safeInt(maxPlayersField.getText(), 20, 1, 100);
        cfg.defaultGameMode = GAMEMODES[gameModeIndex];
        cfg.pvpEnabled      = pvpEnabled;
        cfg.notifyOnJoin    = notifyOnJoin;
        cfg.notifyOnLeave   = notifyOnLeave;
        cfg.autoKickOnIdle  = autoKickOnIdle;
        cfg.idleTimeoutMinutes = safeInt(idleTimeoutField.getText(), 30, 1, 1440);
        cfg.enableWhitelist = enableWhitelist;
        cfg.banChatMessages = banChatMessages;
        cfg.showPlayerList  = showPlayerList;
        cfg.save();
        MinecraftClient.getInstance().setScreen(parent);
    }

    private int safeInt(String s, int def, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(s.trim()))); }
        catch (NumberFormatException e) { return def; }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelX = cx - PANEL_W / 2;
        int panelY = cy - PANEL_H / 2;

        ctx.fill(panelX + 4, panelY + 4, panelX + PANEL_W + 4, panelY + PANEL_H + 4, 0x88000000);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG_COLOR);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 2, 0xFF0F3460);
        ctx.fill(panelX, panelY + PANEL_H - 2, panelX + PANEL_W, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX, panelY, panelX + 2, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX + PANEL_W - 2, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 48, ACCENT_CLR);

        ctx.drawCenteredTextWithShadow(textRenderer, "§e✦ Настройки World Host", cx, panelY + 8, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "§7Параметры сохраняются в файл конфигурации", cx, panelY + 22, 0xAAAAAA);
        ctx.drawCenteredTextWithShadow(textRenderer, "§8worldhost.json", cx, panelY + 34, 0x888888);

        int lx = panelX + 20;
        int y = panelY + 52;
        ctx.drawTextWithShadow(textRenderer, "§7Название сервера (MOTD):", lx, y, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§7Максимум игроков (1-100):", lx, y + 40, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§7AFK таймаут (мин):", lx + (PANEL_W - 40) / 2 + 8, panelY + 248, 0xAAAAAA);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
