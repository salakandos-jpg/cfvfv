package com.worldhost.client.gui;

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
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class HostControlPanelScreen extends Screen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 400;
    private static final int BG_COLOR   = 0xDD1A1A2E;
    private static final int ACCENT_CLR = 0xFF0F3460;
    private static final int ROW_EVEN   = 0x22FFFFFF;
    private static final int ROW_ODD    = 0x11FFFFFF;
    private static final int ROW_SEL    = 0x44306090;

    private final Screen parent;
    private List<WorldHostClient.PlayerInfo> players = new ArrayList<>();
    private int selectedIndex = -1;

    private TextFieldWidget kickReasonField;
    private TextFieldWidget banReasonField;
    private TextFieldWidget messageField;

    private String selectedGamemode = "SURVIVAL";
    private static final String[] GAMEMODES   = {"SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"};
    private static final String[] GAMEMODE_RU = {"Выживание", "Творческий", "Приключение", "Наблюдатель"};
    private int gameModeIndex = 0;
    private ButtonWidget gmButton;

    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 6;
    private static final int ROW_H = 22;

    public HostControlPanelScreen(Screen parent) {
        super(Text.literal("Панель управления"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelX = cx - PANEL_W / 2;
        int panelY = cy - PANEL_H / 2;

        int listX = panelX + 10;
        int listY = panelY + 55;
        int listW = PANEL_W - 200;

        int btnX = panelX + listW + 20;
        int btnW = 170;

        kickReasonField = new TextFieldWidget(textRenderer, btnX, listY + 30, btnW, 18, Text.literal("Причина кика"));
        kickReasonField.setMaxLength(64);
        kickReasonField.setPlaceholder(Text.literal("Причина..."));
        addDrawableChild(kickReasonField);

        addDrawableChild(ButtonWidget.builder(Text.literal("§e⚡ Кик"), btn -> doKick())
                .dimensions(btnX, listY + 50, btnW, 20).build());

        banReasonField = new TextFieldWidget(textRenderer, btnX, listY + 80, btnW, 18, Text.literal("Причина бана"));
        banReasonField.setMaxLength(64);
        banReasonField.setPlaceholder(Text.literal("Причина..."));
        addDrawableChild(banReasonField);

        addDrawableChild(ButtonWidget.builder(Text.literal("§c✖ Бан"), btn -> doBan())
                .dimensions(btnX, listY + 100, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("§a✔ Разбан"), btn -> doUnban())
                .dimensions(btnX, listY + 122, btnW, 20).build());

        gmButton = ButtonWidget.builder(
                Text.literal("◀ " + GAMEMODE_RU[gameModeIndex] + " ▶"),
                btn -> {
                    gameModeIndex = (gameModeIndex + 1) % GAMEMODES.length;
                    selectedGamemode = GAMEMODES[gameModeIndex];
                    btn.setMessage(Text.literal("◀ " + GAMEMODE_RU[gameModeIndex] + " ▶"));
                }
        ).dimensions(btnX, listY + 152, btnW, 20).build();
        addDrawableChild(gmButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("§b✦ Сменить режим"), btn -> doChangeGameMode())
                .dimensions(btnX, listY + 174, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("§6★ Выдать OP"), btn -> doGiveOp())
                .dimensions(btnX, listY + 204, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("§7☆ Снять OP"), btn -> doRemoveOp())
                .dimensions(btnX, listY + 224, btnW, 20).build());

        messageField = new TextFieldWidget(textRenderer, btnX, listY + 254, btnW, 18, Text.literal("Сообщение"));
        messageField.setMaxLength(128);
        messageField.setPlaceholder(Text.literal("Личное сообщение..."));
        addDrawableChild(messageField);

        addDrawableChild(ButtonWidget.builder(Text.literal("§d✉ Отправить"), btn -> doSendMessage())
                .dimensions(btnX, listY + 274, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("§3⊕ Телепорт к себе"), btn -> doTeleport())
                .dimensions(btnX, listY + 296, btnW, 20).build());

        // Refresh
        addDrawableChild(ButtonWidget.builder(Text.literal("§f↻ Обновить список"), btn -> requestPlayerList())
                .dimensions(listX, panelY + PANEL_H - 36, 140, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("§7← Назад"), btn -> MinecraftClient.getInstance().setScreen(parent))
                .dimensions(panelX + PANEL_W - 100, panelY + PANEL_H - 36, 90, 20).build());

        // Scroll buttons
        addDrawableChild(ButtonWidget.builder(Text.literal("▲"), btn -> { if (scrollOffset > 0) scrollOffset--; })
                .dimensions(listX + listW - 20, listY, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▼"), btn -> {
            if (scrollOffset < Math.max(0, players.size() - VISIBLE_ROWS)) scrollOffset++;
        }).dimensions(listX + listW - 20, listY + ROW_H * VISIBLE_ROWS - 20, 20, 20).build());

        requestPlayerList();
    }

    private void requestPlayerList() {
        ClientPlayNetworking.send(WorldHostNetwork.GET_PLAYER_LIST, PacketByteBufs.empty());
    }

    public void updatePlayerList(WorldHostClient.PlayerInfo[] newPlayers) {
        this.players = new ArrayList<>(List.of(newPlayers));
        if (selectedIndex >= players.size()) selectedIndex = -1;
    }

    private WorldHostClient.PlayerInfo getSelected() {
        if (selectedIndex < 0 || selectedIndex >= players.size()) return null;
        return players.get(selectedIndex);
    }

    private String getSelectedName() {
        WorldHostClient.PlayerInfo p = getSelected();
        return p != null ? p.name() : null;
    }

    private void doKick() {
        String name = getSelectedName();
        if (name == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(name);
        buf.writeString(kickReasonField.getText());
        ClientPlayNetworking.send(WorldHostNetwork.KICK_PLAYER, buf);
        kickReasonField.setText("");
        requestPlayerList();
    }

    private void doBan() {
        String name = getSelectedName();
        if (name == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(name);
        buf.writeString(banReasonField.getText());
        ClientPlayNetworking.send(WorldHostNetwork.BAN_PLAYER, buf);
        banReasonField.setText("");
        requestPlayerList();
    }

    private void doUnban() {
        String name = getSelectedName();
        if (name == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(name);
        ClientPlayNetworking.send(WorldHostNetwork.UNBAN_PLAYER, buf);
    }

    private void doChangeGameMode() {
        String name = getSelectedName();
        if (name == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(name);
        buf.writeString(selectedGamemode);
        ClientPlayNetworking.send(WorldHostNetwork.CHANGE_GAMEMODE, buf);
    }

    private void doGiveOp() {
        String name = getSelectedName();
        if (name == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(name);
        ClientPlayNetworking.send(WorldHostNetwork.GIVE_OP, buf);
        requestPlayerList();
    }

    private void doRemoveOp() {
        String name = getSelectedName();
        if (name == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(name);
        ClientPlayNetworking.send(WorldHostNetwork.REMOVE_OP, buf);
        requestPlayerList();
    }

    private void doSendMessage() {
        String name = getSelectedName();
        String msg = messageField.getText().trim();
        if (name == null || msg.isEmpty()) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(name);
        buf.writeString(msg);
        ClientPlayNetworking.send(WorldHostNetwork.SEND_MESSAGE, buf);
        messageField.setText("");
    }

    private void doTeleport() {
        String name = getSelectedName();
        if (name == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(name);
        ClientPlayNetworking.send(WorldHostNetwork.TELEPORT_PLAYER, buf);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelX = cx - PANEL_W / 2;
        int panelY = cy - PANEL_H / 2;
        int listX = panelX + 10;
        int listY = panelY + 55;
        int listW = PANEL_W - 200;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= players.size()) break;
            int rowY = listY + i * ROW_H;
            if (mouseX >= listX && mouseX <= listX + listW - 22 && mouseY >= rowY && mouseY < rowY + ROW_H) {
                selectedIndex = idx;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount < 0) {
            if (scrollOffset < Math.max(0, players.size() - VISIBLE_ROWS)) scrollOffset++;
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
        int listX = panelX + 10;
        int listY = panelY + 55;
        int listW = PANEL_W - 200;
        int btnX = panelX + listW + 20;

        ctx.fill(panelX + 4, panelY + 4, panelX + PANEL_W + 4, panelY + PANEL_H + 4, 0x88000000);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG_COLOR);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 2, 0xFF0F3460);
        ctx.fill(panelX, panelY + PANEL_H - 2, panelX + PANEL_W, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX, panelY, panelX + 2, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX + PANEL_W - 2, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 48, ACCENT_CLR);

        ctx.drawCenteredTextWithShadow(textRenderer, "§b⚙ Панель управления", cx, panelY + 8, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "§7Управление игроками в реальном времени", cx, panelY + 22, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "§7Онлайн: §f" + players.size(), panelX + 10, panelY + 36, 0xFFFFFF);

        // Left: player list header
        ctx.fill(listX, listY - 16, listX + listW - 22, listY, 0xFF0D2137);
        ctx.drawTextWithShadow(textRenderer, "§f  Игрок", listX + 2, listY - 12, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§fРежим", listX + 100, listY - 12, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§fOp", listX + 160, listY - 12, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§fПинг", listX + 180, listY - 12, 0xFFFFFF);

        // Player rows
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= players.size()) break;
            WorldHostClient.PlayerInfo p = players.get(idx);
            int rowY = listY + i * ROW_H;
            int bg = (idx == selectedIndex) ? ROW_SEL : (i % 2 == 0 ? ROW_EVEN : ROW_ODD);
            ctx.fill(listX, rowY, listX + listW - 22, rowY + ROW_H, bg);
            String nameStr = p.isOp() ? "§6★ " + p.name() : "§f  " + p.name();
            ctx.drawTextWithShadow(textRenderer, nameStr, listX + 2, rowY + 7, 0xFFFFFF);
            String gm = switch (p.gameMode()) {
                case "survival"   -> "§aВыж";
                case "creative"   -> "§bТво";
                case "adventure"  -> "§eПри";
                case "spectator"  -> "§7Наб";
                default           -> "§f?";
            };
            ctx.drawTextWithShadow(textRenderer, gm, listX + 100, rowY + 7, 0xFFFFFF);
            ctx.drawTextWithShadow(textRenderer, p.isOp() ? "§6★" : "§7-", listX + 160, rowY + 7, 0xFFFFFF);
            int ping = p.ping();
            String pingColor = ping < 80 ? "§a" : ping < 200 ? "§e" : "§c";
            ctx.drawTextWithShadow(textRenderer, pingColor + ping + "ms", listX + 180, rowY + 7, 0xFFFFFF);
        }

        // Right panel header
        String selName = selectedIndex >= 0 && selectedIndex < players.size()
                ? "§7Выбран: §f" + players.get(selectedIndex).name()
                : "§7(выберите игрока)";
        ctx.drawTextWithShadow(textRenderer, selName, btnX, listY + 10, 0xFFFFFF);

        ctx.drawTextWithShadow(textRenderer, "§7Причина кика:", btnX, listY + 20, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "§7Причина бана:", btnX, listY + 70, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "§7Режим игры:", btnX, listY + 142, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "§7OP-права:", btnX, listY + 194, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "§7Личное сообщение:", btnX, listY + 244, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "§7Телепортация:", btnX, listY + 286, 0xAAAAAA);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
