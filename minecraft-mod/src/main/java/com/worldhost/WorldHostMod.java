package com.worldhost;

import com.worldhost.config.WorldHostConfig;
import com.worldhost.network.WorldHostNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldHostMod implements ModInitializer {

    public static final String MOD_ID = "worldhost";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static WorldHostConfig config;

    @Override
    public void onInitialize() {
        LOGGER.info("[WorldHost] Инициализация мода World Host...");

        config = WorldHostConfig.load();

        WorldHostNetwork.registerServerPackets();

        LOGGER.info("[WorldHost] Мод World Host успешно загружен!");
    }
}
