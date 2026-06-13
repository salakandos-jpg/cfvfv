package com.worldhost.client;

import com.google.gson.*;
import com.worldhost.WorldHostMod;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class WorldDiscoveryClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Gson GSON = new Gson();

    private final String baseUrl;

    public WorldDiscoveryClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
    }

    public record WorldEntry(
            String code,
            String ownerName,
            String motd,
            String address,
            int port,
            int maxPlayers,
            int onlinePlayers,
            String gameMode,
            boolean allowCheats
    ) {
        public String displayAddress() { return address + ":" + port; }
    }

    public CompletableFuture<String> registerWorld(
            String ownerName, String motd, String address, int port,
            int maxPlayers, int onlinePlayers, String gameMode, boolean allowCheats) {

        JsonObject body = new JsonObject();
        body.addProperty("ownerName", ownerName);
        body.addProperty("motd", motd);
        body.addProperty("address", address);
        body.addProperty("port", port);
        body.addProperty("maxPlayers", maxPlayers);
        body.addProperty("onlinePlayers", onlinePlayers);
        body.addProperty("gameMode", gameMode);
        body.addProperty("allowCheats", allowCheats);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/worldhost/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(8))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() == 200) {
                        JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                        return obj.get("code").getAsString();
                    }
                    throw new RuntimeException("Register failed: " + resp.statusCode());
                })
                .exceptionally(e -> {
                    WorldHostMod.LOGGER.warn("[WorldHost] Не удалось зарегистрировать мир: {}", e.getMessage());
                    return null;
                });
    }

    public CompletableFuture<Void> sendHeartbeat(String code, int onlinePlayers) {
        JsonObject body = new JsonObject();
        body.addProperty("onlinePlayers", onlinePlayers);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/worldhost/heartbeat/" + code))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(5))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                .thenApply(r -> (Void) null)
                .exceptionally(e -> null);
    }

    public CompletableFuture<Void> closeWorld(String code) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/worldhost/close/" + code))
                .DELETE()
                .timeout(Duration.ofSeconds(5))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                .thenApply(r -> (Void) null)
                .exceptionally(e -> null);
    }

    public CompletableFuture<List<WorldEntry>> listWorlds() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/worldhost/list"))
                .GET()
                .timeout(Duration.ofSeconds(8))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) return List.of();
                    JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                    JsonArray arr = obj.getAsJsonArray("worlds");
                    List<WorldEntry> result = new ArrayList<>();
                    for (JsonElement el : arr) {
                        JsonObject w = el.getAsJsonObject();
                        result.add(new WorldEntry(
                                w.get("code").getAsString(),
                                w.get("ownerName").getAsString(),
                                w.get("motd").getAsString(),
                                w.get("address").getAsString(),
                                w.get("port").getAsInt(),
                                w.get("maxPlayers").getAsInt(),
                                w.get("onlinePlayers").getAsInt(),
                                w.get("gameMode").getAsString(),
                                w.get("allowCheats").getAsBoolean()
                        ));
                    }
                    return result;
                })
                .exceptionally(e -> {
                    WorldHostMod.LOGGER.warn("[WorldHost] Ошибка получения списка миров: {}", e.getMessage());
                    return List.of();
                });
    }

    public CompletableFuture<String> detectExternalIp() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.ipify.org"))
                .GET()
                .timeout(Duration.ofSeconds(6))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .exceptionally(e -> {
                    WorldHostMod.LOGGER.warn("[WorldHost] Не удалось определить внешний IP: {}", e.getMessage());
                    return null;
                });
    }
}
