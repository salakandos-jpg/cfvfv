package com.worldhost.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;

public class WorldHostConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("worldhost");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("worldhost.json");

    // ── Server identity ──────────────────────────────────────────────────────
    public String motd = "Добро пожаловать в мой мир!";
    public int    maxPlayers = 20;
    public String defaultGameMode = "SURVIVAL";
    public boolean allowCheats = false;

    // ── Global relay (world discovery server) ────────────────────────────────
    /** URL of the World Host relay / discovery API. Change to your self-hosted instance if needed. */
    public String relayUrl = "https://YOUR_REPLIT_APP.replit.app";

    // ── Game settings ────────────────────────────────────────────────────────
    public boolean pvpEnabled = true;
    public boolean showPlayerList = true;
    public boolean notifyOnJoin = true;
    public boolean notifyOnLeave = true;

    // ── Auto-kick AFK ────────────────────────────────────────────────────────
    public boolean autoKickOnIdle = false;
    public int     idleTimeoutMinutes = 30;

    // ── Whitelist / ban ──────────────────────────────────────────────────────
    public boolean enableWhitelist = false;
    public boolean banChatMessages = true;

    // ── UI ───────────────────────────────────────────────────────────────────
    public boolean onlineMode = false;
    public String  language = "ru_ru";

    // ────────────────────────────────────────────────────────────────────────

    public static WorldHostConfig load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                WorldHostConfig cfg = GSON.fromJson(reader, WorldHostConfig.class);
                if (cfg != null) { LOGGER.info("[WorldHost] Конфиг загружен."); return cfg; }
            } catch (IOException e) {
                LOGGER.error("[WorldHost] Ошибка загрузки конфига: ", e);
            }
        }
        WorldHostConfig def = new WorldHostConfig();
        def.save();
        return def;
    }

    public void save() {
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            LOGGER.error("[WorldHost] Ошибка сохранения конфига: ", e);
        }
    }
}
