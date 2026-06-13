package com.worldhost.client;

import com.worldhost.WorldHostMod;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the lifecycle of a globally-registered world session.
 * Runs heartbeat every 30s while the world is open.
 */
public class GlobalHostManager {

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "worldhost-heartbeat");
                t.setDaemon(true);
                return t;
            });

    private static ScheduledFuture<?> heartbeatTask = null;
    private static final AtomicReference<String> sessionCode = new AtomicReference<>(null);
    private static final AtomicReference<String> externalIp  = new AtomicReference<>(null);
    private static int registeredPort = 0;

    public static String getSessionCode()  { return sessionCode.get(); }
    public static String getExternalIp()   { return externalIp.get(); }
    public static int    getPort()         { return registeredPort; }
    public static boolean isOpen()         { return sessionCode.get() != null; }

    public static void open(WorldDiscoveryClient discovery, String ownerName, String motd,
                            String ip, int port, int maxPlayers,
                            String gameMode, boolean allowCheats,
                            Runnable onSuccess, Runnable onFailure) {

        externalIp.set(ip);
        registeredPort = port;

        discovery.registerWorld(ownerName, motd, ip, port, maxPlayers, 1, gameMode, allowCheats)
                .thenAccept(code -> {
                    if (code == null) {
                        MinecraftClient.getInstance().execute(onFailure);
                        return;
                    }
                    sessionCode.set(code);
                    WorldHostMod.LOGGER.info("[WorldHost] Мир зарегистрирован глобально. Код: {}", code);

                    heartbeatTask = SCHEDULER.scheduleAtFixedRate(() -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        int online = 1;
                        if (mc.getServer() != null) {
                            online = mc.getServer().getCurrentPlayerCount();
                        }
                        discovery.sendHeartbeat(code, online);
                    }, 30, 30, TimeUnit.SECONDS);

                    MinecraftClient.getInstance().execute(onSuccess);
                })
                .exceptionally(e -> {
                    WorldHostMod.LOGGER.error("[WorldHost] Ошибка регистрации: ", e);
                    MinecraftClient.getInstance().execute(onFailure);
                    return null;
                });
    }

    public static void close(WorldDiscoveryClient discovery) {
        String code = sessionCode.getAndSet(null);
        if (heartbeatTask != null) { heartbeatTask.cancel(false); heartbeatTask = null; }
        if (code != null) {
            discovery.closeWorld(code);
            WorldHostMod.LOGGER.info("[WorldHost] Сессия {} закрыта.", code);
        }
        externalIp.set(null);
        registeredPort = 0;
    }
}
