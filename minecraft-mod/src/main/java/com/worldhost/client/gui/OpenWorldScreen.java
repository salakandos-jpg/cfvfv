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
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class OpenWorldScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 370;
    private static final int BG_COLOR   = 0xDD1A1A2E;
    private static final int ACCENT_CLR = 0xFF0F3460;

    private final Screen parent;

    private TextFieldWidget motdField;
    private TextFieldWidget maxPlayersField;

    private String selectedGameMode = "SURVIVAL";
    private boolean allowCheats = false;

    private static final String[] GAMEMODES    = {"SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"};
    private static final String[] GAMEMODE_RU  = {"Выживание", "Творческий", "Приключение", "Наблюдатель"};
    private int gameModeIndex = 0;

    public OpenWorldScreen(Screen parent) {
        super(Text.literal("Открыть мир"));
        this.parent = parent;
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

        motdField = new TextFieldWidget(textRenderer, btnX, panelY + 90, btnW, 20,
                Text.literal("MOTD сервера"));
        motdField.setMaxLength(64);
        motdField.setText(WorldHostMod.config.motd);
        motdField.setPlaceholder(Text.literal("Добро пожаловать в мой мир!"));
        addDrawableChild(motdField);

        maxPlayersField = new TextFieldWidget(textRenderer, btnX, panelY + 145, btnW, 20,
                Text.literal("Макс. игроков"));
        maxPlayersField.setMaxLength(4);
        maxPlayersField.setText(String.valueOf(WorldHostMod.config.maxPlayers));
        addDrawableChild(maxPlayersField);

        // Game mode cycle button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("◀ " + GAMEMODE_RU[gameModeIndex] + " ▶"),
                btn -> {
                    gameModeIndex = (gameModeIndex + 1) % GAMEMODES.length;
                    selectedGameMode = GAMEMODES[gameModeIndex];
                    btn.setMessage(Text.literal("◀ " + GAMEMODE_RU[gameModeIndex] + " ▶"));
                }
        ).dimensions(btnX, panelY + 200, btnW, 22).build());

        // Allow cheats toggle
        addDrawableChild(ButtonWidget.builder(
                Text.literal(allowCheats ? "§aЧиты: ВКЛЮЧЕНЫ" : "§cЧиты: ВЫКЛЮЧЕНЫ"),
                btn -> {
                    allowCheats = !allowCheats;
                    btn.setMessage(Text.literal(allowCheats ? "§aЧиты: ВКЛЮЧЕНЫ" : "§cЧиты: ВЫКЛЮЧЕНЫ"));
                }
        ).dimensions(btnX, panelY + 232, btnW, 22).build());

        // Open button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("§a▶ Открыть мир!"),
                btn -> openWorld()
        ).dimensions(btnX, panelY + 274, btnW, 26).build());

        // Back button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("§7← Назад"),
                btn -> MinecraftClient.getInstance().setScreen(parent)
        ).dimensions(btnX, panelY + 310, btnW, 22).build());
    }

    private void openWorld() {
        String motd = motdField.getText().trim();
        if (motd.isEmpty()) motd = "Добро пожаловать в мой мир!";

        int maxPlayers = 20;
        try {
            maxPlayers = Math.max(1, Math.min(100, Integer.parseInt(maxPlayersField.getText().trim())));
        } catch (NumberFormatException ignored) {}

        WorldHostMod.config.motd = motd;
        WorldHostMod.config.maxPlayers = maxPlayers;
        WorldHostMod.config.allowCheats = allowCheats;
        WorldHostMod.config.defaultGameMode = selectedGameMode;
        WorldHostMod.config.save();

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(allowCheats);
        buf.writeString(selectedGameMode);
        buf.writeString(motd);
        buf.writeInt(maxPlayers);
        ClientPlayNetworking.send(WorldHostNetwork.OPEN_TO_LAN, buf);
        MinecraftClient.getInstance().setScreen(null);
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

        ctx.drawCenteredTextWithShadow(textRenderer, "§bОткрыть мир", cx, panelY + 8, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "§7Настройки публичного доступа", cx, panelY + 22, 0xAAAAAA);
        ctx.drawCenteredTextWithShadow(textRenderer, "§e⚠ Только в одиночном мире!", cx, panelY + 36, 0xFFCC00);

        ctx.drawTextWithShadow(textRenderer, "§7Название сервера (MOTD):", panelX + 20, panelY + 78, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§7Максимум игроков (1-100):", panelX + 20, panelY + 133, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§7Режим игры для новых игроков:", panelX + 20, panelY + 188, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§7Разрешить читы:", panelX + 20, panelY + 220, 0xFFFFFF);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
