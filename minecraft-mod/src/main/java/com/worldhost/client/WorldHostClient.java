package com.worldhost.client;

import com.worldhost.WorldHostMod;
import com.worldhost.client.event.KeyInputHandler;
import com.worldhost.client.gui.WorldHostScreen;
import com.worldhost.network.WorldHostNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class WorldHostClient implements ClientModInitializer {

    public static KeyBinding openMenuKey;

    public static boolean lanOpen = false;
    public static String lanPort = "";
    public static String currentMotd = "";
    public static int currentMaxPlayers = 20;

    @Override
    public void onInitializeClient() {
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.worldhost.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_DELETE,
                "category.worldhost.main"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openMenuKey.wasPressed() && client.world != null) {
                client.setScreen(new WorldHostScreen());
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(WorldHostNetwork.PLAYER_LIST_SYNC, (client, handler, buf, sender) -> {
            int count = buf.readInt();
            PlayerInfo[] players = new PlayerInfo[count];
            for (int i = 0; i < count; i++) {
                players[i] = new PlayerInfo(
                        buf.readString(),
                        buf.readString(),
                        buf.readString(),
                        buf.readBoolean(),
                        buf.readInt()
                );
            }
            client.execute(() -> {
                if (client.currentScreen instanceof com.worldhost.client.gui.HostControlPanelScreen panel) {
                    panel.updatePlayerList(players);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(WorldHostNetwork.STATUS_SYNC, (client, handler, buf, sender) -> {
            boolean open = buf.readBoolean();
            String port = buf.readString();
            String motd = buf.readString();
            int maxPlayers = buf.readInt();
            client.execute(() -> {
                lanOpen = open;
                lanPort = port;
                currentMotd = motd;
                currentMaxPlayers = maxPlayers;
            });
        });

        WorldHostMod.LOGGER.info("[WorldHost] Клиент инициализирован. Delete — открыть меню.");
    }

    public record PlayerInfo(String name, String uuid, String gameMode, boolean isOp, int ping) {}
}
