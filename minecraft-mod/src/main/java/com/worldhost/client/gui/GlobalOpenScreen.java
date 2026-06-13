package com.worldhost.client.gui;

import com.worldhost.WorldHostMod;
import com.worldhost.client.GlobalHostManager;
import com.worldhost.client.WorldDiscoveryClient;
import com.worldhost.client.WorldHostClient;
import com.worldhost.network.WorldHostNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

@SuppressWarnings("ConstantConditions")
public class GlobalOpenScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 400;
    private static final int BG_COLOR   = 0xDD1A1A2E;
    private static final int ACCENT_CLR = 0xFF0F3460;

    private final Screen parent;
    private final WorldDiscoveryClient discovery;

    private TextFieldWidget motdField;
    private TextFieldWidget maxPlayersField;

    private String selectedGameMode = "SURVIVAL";
    private boolean allowCheats = false;
    private static final String[] GAMEMODES   = {"SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"};
    private static final String[] GAMEMODE_RU = {"Выживание", "Творческий", "Приключение", "Наблюдатель"};
    private int gameModeIndex = 0;

    private enum State { IDLE, DETECTING_IP, OPENING_LAN, REGISTERING, SUCCESS, ERROR }
    private State state = State.IDLE;

    private String detectedIp = null;
    private String sessionCode = null;
    private String errorMsg = "";

    private ButtonWidget openBtn;

    public GlobalOpenScreen(Screen parent, WorldDiscoveryClient discovery) {
        super(Text.literal("Открыть глобально"));
        this.parent = parent;
        this.discovery = discovery;
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

        motdField = new TextFieldWidget(textRenderer, lx, panelY + 90, fw, 20, Text.literal("MOTD"));
        motdField.setMaxLength(64);
        motdField.setText(WorldHostMod.config.motd);
        motdField.setPlaceholder(Text.literal("Название сервера..."));
        addDrawableChild(motdField);

        maxPlayersField = new TextFieldWidget(textRenderer, lx, panelY + 148, fw, 20, Text.literal("Макс. игроков"));
        maxPlayersField.setMaxLength(4);
        maxPlayersField.setText(String.valueOf(WorldHostMod.config.maxPlayers));
        addDrawableChild(maxPlayersField);

        addDrawableChild(ButtonWidget.builder(
                Text.literal("◀ " + GAMEMODE_RU[gameModeIndex] + " ▶"),
                btn -> {
                    gameModeIndex = (gameModeIndex + 1) % GAMEMODES.length;
                    selectedGameMode = GAMEMODES[gameModeIndex];
                    btn.setMessage(Text.literal("◀ " + GAMEMODE_RU[gameModeIndex] + " ▶"));
                }
        ).dimensions(lx, panelY + 200, fw, 22).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal(allowCheats ? "§aЧиты: ВКЛЮЧЕНЫ" : "§cЧиты: ВЫКЛЮЧЕНЫ"),
                btn -> {
                    allowCheats = !allowCheats;
                    btn.setMessage(Text.literal(allowCheats ? "§aЧиты: ВКЛЮЧЕНЫ" : "§cЧиты: ВЫКЛЮЧЕНЫ"));
                }
        ).dimensions(lx, panelY + 232, fw, 22).build());

        openBtn = ButtonWidget.builder(
                Text.literal("§a🌐 Открыть для всего мира!"),
                btn -> startOpen()
        ).dimensions(lx, panelY + 276, fw, 28).build();
        addDrawableChild(openBtn);

        addDrawableChild(ButtonWidget.builder(
                Text.literal("§7← Назад"),
                btn -> MinecraftClient.getInstance().setScreen(parent)
        ).dimensions(lx, panelY + PANEL_H - 38, fw, 24).build());
    }

    private void startOpen() {
        if (state != State.IDLE && state != State.ERROR) return;
        state = State.DETECTING_IP;
        openBtn.active = false;

        // Step 1: detect external IP
        discovery.detectExternalIp().thenAccept(ip -> {
            MinecraftClient.getInstance().execute(() -> {
                if (ip == null) {
                    state = State.ERROR;
                    errorMsg = "Не удалось определить внешний IP. Проверь интернет-соединение.";
                    openBtn.active = true;
                    return;
                }
                detectedIp = ip;
                state = State.OPENING_LAN;

                // Step 2: open to LAN (get port)
                MinecraftClient mc = MinecraftClient.getInstance();
                if (!(mc.getServer() instanceof IntegratedServer intServer)) {
                    state = State.ERROR;
                    errorMsg = "Доступно только в одиночном мире!";
                    openBtn.active = true;
                    return;
                }

                int maxPl = safeInt(maxPlayersField.getText(), 20, 1, 100);
                String motd = motdField.getText().trim().isEmpty()
                        ? "Добро пожаловать!" : motdField.getText().trim();

                // Open LAN via packet
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBoolean(allowCheats);
                buf.writeString(selectedGameMode);
                buf.writeString(motd);
                buf.writeInt(maxPl);
                ClientPlayNetworking.send(WorldHostNetwork.OPEN_TO_LAN, buf);

                // Wait briefly for port to be assigned, then register
                mc.execute(() -> {
                    int lanPort = intServer.getPort();
                    if (lanPort <= 0) lanPort = intServer.getPort();

                    WorldHostMod.config.motd = motd;
                    WorldHostMod.config.maxPlayers = maxPl;
                    WorldHostMod.config.allowCheats = allowCheats;
                    WorldHostMod.config.defaultGameMode = selectedGameMode;
                    WorldHostMod.config.save();

                    state = State.REGISTERING;
                    int finalPort = lanPort > 0 ? lanPort : 25565;
                    int finalMaxPl = maxPl;
                    String finalMotd = motd;
                    String playerName = mc.player != null ? mc.player.getName().getString() : "Host";

                    GlobalHostManager.open(
                            discovery, playerName, finalMotd, detectedIp, finalPort,
                            finalMaxPl, selectedGameMode, allowCheats,
                            () -> {
                                state = State.SUCCESS;
                                sessionCode = GlobalHostManager.getSessionCode();
                                WorldHostClient.lanOpen = true;
                                WorldHostClient.lanPort = String.valueOf(finalPort);
                                openBtn.active = false;
                            },
                            () -> {
                                state = State.ERROR;
                                errorMsg = "Не удалось зарегистрировать мир. Сервер недоступен?";
                                openBtn.active = true;
                            }
                    );
                });
            });
        });
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelX = cx - PANEL_W / 2;
        int panelY = cy - PANEL_H / 2;
        int lx = panelX + 20;

        ctx.fill(panelX + 4, panelY + 4, panelX + PANEL_W + 4, panelY + PANEL_H + 4, 0x88000000);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG_COLOR);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 2, 0xFF0F3460);
        ctx.fill(panelX, panelY + PANEL_H - 2, panelX + PANEL_W, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX, panelY, panelX + 2, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX + PANEL_W - 2, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 48, ACCENT_CLR);

        ctx.drawCenteredTextWithShadow(textRenderer, "§b🌐 Открыть для всего мира", cx, panelY + 8, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "§7Через интернет — без LAN", cx, panelY + 22, 0xAAAAAA);

        // State indicator
        String stateStr = switch (state) {
            case IDLE      -> "§7Готов к открытию";
            case DETECTING_IP -> "§e⟳ Определение внешнего IP...";
            case OPENING_LAN  -> "§e⟳ Открытие LAN порта...";
            case REGISTERING  -> "§e⟳ Регистрация в реестре миров...";
            case SUCCESS   -> "§a✔ Мир открыт! Код: §f" + sessionCode;
            case ERROR     -> "§c✘ Ошибка";
        };
        ctx.drawCenteredTextWithShadow(textRenderer, stateStr, cx, panelY + 36, 0xFFFFFF);

        if (state == State.SUCCESS && detectedIp != null) {
            String addr = detectedIp + ":" + GlobalHostManager.getPort();
            ctx.drawCenteredTextWithShadow(textRenderer, "§7Адрес: §f" + addr, cx, panelY + 262, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§8(нужен проброс порта или playit.gg)", cx, panelY + 274, 0x888888);
        }

        if (state == State.ERROR) {
            ctx.drawCenteredTextWithShadow(textRenderer, "§c" + errorMsg, cx, panelY + 262, 0xFF4444);
        }

        ctx.drawTextWithShadow(textRenderer, "§7Название сервера (MOTD):", lx, panelY + 78, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§7Макс. игроков:", lx, panelY + 136, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§7Режим игры:", lx, panelY + 188, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§7Читы:", lx, panelY + 220, 0xFFFFFF);

        // Port-forward info box
        ctx.fill(lx, panelY + 310, lx + PANEL_W - 40, panelY + 360, 0x33FFAA00);
        ctx.drawTextWithShadow(textRenderer, "§e⚠ Требования для глобального доступа:", lx + 4, panelY + 314, 0xFFCC00);
        ctx.drawTextWithShadow(textRenderer, "§71. Проброс порта на роутере (LAN-порт)", lx + 4, panelY + 326, 0xCCCCCC);
        ctx.drawTextWithShadow(textRenderer, "§7   ИЛИ использование playit.gg (бесплатно)", lx + 4, panelY + 336, 0xCCCCCC);
        ctx.drawTextWithShadow(textRenderer, "§72. Другие игроки устанавливают этот мод", lx + 4, panelY + 346, 0xCCCCCC);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private int safeInt(String s, int def, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(s.trim()))); }
        catch (NumberFormatException e) { return def; }
    }

    @Override
    public boolean shouldPause() { return false; }
}
