package com.worldhost.client.gui;

import com.worldhost.client.WorldDiscoveryClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class JoinWorldScreen extends Screen {

    private static final int PANEL_W = 440;
    private static final int PANEL_H = 380;
    private static final int BG_COLOR   = 0xDD1A1A2E;
    private static final int ACCENT_CLR = 0xFF0F3460;
    private static final int ROW_EVEN   = 0x22FFFFFF;
    private static final int ROW_ODD    = 0x11FFFFFF;
    private static final int ROW_SEL    = 0x44309050;

    private final Screen parent;
    private final WorldDiscoveryClient discovery;

    private List<WorldDiscoveryClient.WorldEntry> worlds = new ArrayList<>();
    private int selectedIndex = -1;
    private boolean loading = false;
    private String statusMsg = "§7Нажмите «Обновить» для поиска миров";

    private TextFieldWidget directAddressField;
    private TextFieldWidget codeField;

    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 6;
    private static final int ROW_H = 28;

    public JoinWorldScreen(Screen parent, WorldDiscoveryClient discovery) {
        super(Text.literal("Войти в мир"));
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
        int lx = panelX + 10;
        int fw = PANEL_W - 20;

        // Refresh / scroll
        addDrawableChild(ButtonWidget.builder(Text.literal("§f↻ Обновить"), btn -> refreshList())
                .dimensions(lx, panelY + 52, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▲"), btn -> { if (scrollOffset > 0) scrollOffset--; })
                .dimensions(lx + fw - 22, panelY + 52, 22, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▼"), btn -> {
            if (scrollOffset < Math.max(0, worlds.size() - VISIBLE_ROWS)) scrollOffset++;
        }).dimensions(lx + fw - 22, panelY + 52 + ROW_H * VISIBLE_ROWS - 22, 22, 22).build());

        // Join selected
        addDrawableChild(ButtonWidget.builder(Text.literal("§a▶ Войти (выбранный)"), btn -> joinSelected())
                .dimensions(lx, panelY + 52 + ROW_H * VISIBLE_ROWS + 8, (fw - 8) / 2, 22).build());

        // Code field
        codeField = new TextFieldWidget(textRenderer,
                lx + (fw - 8) / 2 + 8, panelY + 52 + ROW_H * VISIBLE_ROWS + 8,
                (fw - 8) / 2, 22, Text.literal("Код"));
        codeField.setMaxLength(8);
        codeField.setPlaceholder(Text.literal("Код (ABC123)..."));
        addDrawableChild(codeField);

        // Direct address
        int directY = panelY + 52 + ROW_H * VISIBLE_ROWS + 40;
        directAddressField = new TextFieldWidget(textRenderer, lx, directY, fw - 110, 20, Text.literal("IP:порт"));
        directAddressField.setMaxLength(64);
        directAddressField.setPlaceholder(Text.literal("IP:порт  (например 1.2.3.4:25565)"));
        addDrawableChild(directAddressField);

        addDrawableChild(ButtonWidget.builder(Text.literal("§b→ Прямое подключение"), btn -> joinDirect())
                .dimensions(lx + fw - 106, directY, 106, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("§7← Назад"),
                btn -> MinecraftClient.getInstance().setScreen(parent))
                .dimensions(lx + fw / 2 - 45, panelY + PANEL_H - 34, 90, 24).build());

        refreshList();
    }

    private void refreshList() {
        loading = true;
        statusMsg = "§e⟳ Загрузка списка миров...";
        worlds = new ArrayList<>();
        selectedIndex = -1;

        discovery.listWorlds().thenAccept(list -> {
            MinecraftClient.getInstance().execute(() -> {
                worlds = new ArrayList<>(list);
                loading = false;
                statusMsg = worlds.isEmpty()
                        ? "§7Нет доступных миров. Попробуй позже."
                        : "§a" + worlds.size() + " мир(ов) найдено";
            });
        });
    }

    private void joinSelected() {
        if (selectedIndex >= 0 && selectedIndex < worlds.size()) {
            WorldDiscoveryClient.WorldEntry w = worlds.get(selectedIndex);
            connect(w.address(), w.port(), w.ownerName() + " — " + w.motd());
        } else if (!codeField.getText().trim().isEmpty()) {
            joinByCode(codeField.getText().trim().toUpperCase());
        }
    }

    private void joinByCode(String code) {
        statusMsg = "§e⟳ Поиск мира по коду " + code + "...";
        discovery.listWorlds().thenAccept(list -> {
            MinecraftClient.getInstance().execute(() -> {
                list.stream()
                    .filter(w -> w.code().equalsIgnoreCase(code))
                    .findFirst()
                    .ifPresentOrElse(
                        w -> connect(w.address(), w.port(), w.ownerName() + " — " + w.motd()),
                        () -> statusMsg = "§cМир с кодом «" + code + "» не найден."
                    );
            });
        });
    }

    private void joinDirect() {
        String raw = directAddressField.getText().trim();
        if (raw.isEmpty()) return;
        String host = raw.contains(":") ? raw.substring(0, raw.lastIndexOf(':')) : raw;
        int port = 25565;
        if (raw.contains(":")) {
            try { port = Integer.parseInt(raw.substring(raw.lastIndexOf(':') + 1)); }
            catch (NumberFormatException ignored) {}
        }
        connect(host, port, raw);
    }

    private void connect(String host, int port, String label) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo info = new ServerInfo(label, host + ":" + port, false);
        ConnectScreen.connect(this, mc, ServerAddress.parse(host + ":" + port), info, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelX = cx - PANEL_W / 2;
        int panelY = cy - PANEL_H / 2;
        int lx = panelX + 10;
        int fw = PANEL_W - 20;
        int listY = panelY + 52;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= worlds.size()) break;
            int rowY = listY + i * ROW_H;
            if (mouseX >= lx && mouseX < lx + fw - 24 && mouseY >= rowY && mouseY < rowY + ROW_H) {
                if (selectedIndex == idx) {
                    // double-click: join immediately
                    joinSelected();
                } else {
                    selectedIndex = idx;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount < 0) {
            if (scrollOffset < Math.max(0, worlds.size() - VISIBLE_ROWS)) scrollOffset++;
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
        int lx = panelX + 10;
        int fw = PANEL_W - 20;
        int listY = panelY + 52;

        ctx.fill(panelX + 4, panelY + 4, panelX + PANEL_W + 4, panelY + PANEL_H + 4, 0x88000000);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG_COLOR);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 2, 0xFF0F3460);
        ctx.fill(panelX, panelY + PANEL_H - 2, panelX + PANEL_W, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX, panelY, panelX + 2, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX + PANEL_W - 2, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF0F3460);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 48, ACCENT_CLR);

        ctx.drawCenteredTextWithShadow(textRenderer, "§b🌐 Войти в мир", cx, panelY + 8, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "§7Миры других игроков с этим модом", cx, panelY + 22, 0xAAAAAA);
        ctx.drawCenteredTextWithShadow(textRenderer, statusMsg, cx, panelY + 36, 0xFFFFFF);

        // Column headers
        ctx.fill(lx, listY - 14, lx + fw - 24, listY, 0xFF0D2137);
        ctx.drawTextWithShadow(textRenderer, "§fХозяин", lx + 4, listY - 11, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§fMOTD", lx + 90, listY - 11, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§fРежим", lx + 240, listY - 11, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§fИгрок.", lx + 310, listY - 11, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§fКод", lx + 360, listY - 11, 0xFFFFFF);

        // Rows
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            int rowY = listY + i * ROW_H;
            if (idx >= worlds.size()) {
                ctx.fill(lx, rowY, lx + fw - 24, rowY + ROW_H, i % 2 == 0 ? ROW_EVEN : ROW_ODD);
                continue;
            }
            WorldDiscoveryClient.WorldEntry w = worlds.get(idx);
            int bg = (idx == selectedIndex) ? ROW_SEL : (i % 2 == 0 ? ROW_EVEN : ROW_ODD);
            ctx.fill(lx, rowY, lx + fw - 24, rowY + ROW_H, bg);

            ctx.drawTextWithShadow(textRenderer, "§f" + w.ownerName(), lx + 4, rowY + 4, 0xFFFFFF);
            String motdShort = w.motd().length() > 22 ? w.motd().substring(0, 20) + "…" : w.motd();
            ctx.drawTextWithShadow(textRenderer, "§7" + motdShort, lx + 90, rowY + 4, 0xCCCCCC);
            ctx.drawTextWithShadow(textRenderer, "§b" + w.address() + ":" + w.port(), lx + 4, rowY + 16, 0xAAAAFF);

            String gm = switch (w.gameMode().toUpperCase()) {
                case "SURVIVAL"  -> "§aВыж";
                case "CREATIVE"  -> "§bТво";
                case "ADVENTURE" -> "§eПри";
                case "SPECTATOR" -> "§7Наб";
                default          -> "§f?";
            };
            ctx.drawTextWithShadow(textRenderer, gm, lx + 240, rowY + 10, 0xFFFFFF);
            ctx.drawTextWithShadow(textRenderer,
                    "§f" + w.onlinePlayers() + "§7/§f" + w.maxPlayers(),
                    lx + 310, rowY + 10, 0xFFFFFF);
            ctx.drawTextWithShadow(textRenderer, "§e" + w.code(), lx + 360, rowY + 10, 0xFFEE55);
        }

        if (!loading && worlds.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7Нет доступных миров",
                    cx, listY + ROW_H * VISIBLE_ROWS / 2 - 4, 0x888888);
        }

        // Labels
        ctx.drawTextWithShadow(textRenderer, "§7Или введи код мира:", lx + (fw - 8) / 2 + 8, panelY + 42 + ROW_H * VISIBLE_ROWS, 0x888888);
        ctx.drawTextWithShadow(textRenderer, "§7— Прямое подключение:", lx, panelY + 52 + ROW_H * VISIBLE_ROWS + 30, 0x888888);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
