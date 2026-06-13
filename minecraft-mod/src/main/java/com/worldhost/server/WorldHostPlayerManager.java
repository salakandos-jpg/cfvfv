package com.worldhost.server;

import com.worldhost.network.WorldHostNetwork;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WorldHostPlayerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("worldhost");

    public static boolean isOperator(MinecraftServer server, ServerPlayerEntity player) {
        return server.getPlayerManager().isOperator(player.getGameProfile());
    }

    public static void kickPlayer(MinecraftServer server, ServerPlayerEntity op, String targetName, String reason) {
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetName);
        if (target == null) {
            op.sendMessage(Text.literal("[WorldHost] Игрок " + targetName + " не найден.").formatted(Formatting.RED), false);
            return;
        }
        if (target == op) {
            op.sendMessage(Text.literal("[WorldHost] Нельзя кикнуть себя.").formatted(Formatting.RED), false);
            return;
        }
        target.networkHandler.disconnect(Text.literal(reason.isEmpty() ? "Вас кикнули с сервера" : reason));
        broadcastToOps(server, Text.literal("[WorldHost] Игрок " + targetName + " был кикнут. Причина: " + (reason.isEmpty() ? "не указана" : reason)).formatted(Formatting.YELLOW));
        LOGGER.info("[WorldHost] {} кикнул {}, причина: {}", op.getName().getString(), targetName, reason);
    }

    public static void banPlayer(MinecraftServer server, ServerPlayerEntity op, String targetName, String reason) {
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetName);
        String banReason = reason.isEmpty() ? "Вы заблокированы на этом сервере" : reason;

        server.getPlayerManager().getUserBanList().add(new net.minecraft.server.BannedPlayerEntry(
                target != null ? target.getGameProfile() :
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), targetName),
                null, op.getName().getString(), null, banReason
        ));

        if (target != null) {
            target.networkHandler.disconnect(Text.literal("Вы заблокированы: " + banReason));
        }
        broadcastToOps(server, Text.literal("[WorldHost] Игрок " + targetName + " заблокирован. Причина: " + banReason).formatted(Formatting.RED));
        LOGGER.info("[WorldHost] {} забанил {}, причина: {}", op.getName().getString(), targetName, banReason);
    }

    public static void unbanPlayer(MinecraftServer server, ServerPlayerEntity op, String targetName) {
        server.getPlayerManager().getUserBanList().remove(
                server.getPlayerManager().getUserBanList().get(targetName)
        );
        broadcastToOps(server, Text.literal("[WorldHost] Игрок " + targetName + " разблокирован.").formatted(Formatting.GREEN));
        op.sendMessage(Text.literal("[WorldHost] " + targetName + " разблокирован.").formatted(Formatting.GREEN), false);
    }

    public static void changeGameMode(MinecraftServer server, ServerPlayerEntity op, String targetName, String gamemodeName) {
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetName);
        if (target == null) {
            op.sendMessage(Text.literal("[WorldHost] Игрок " + targetName + " не найден.").formatted(Formatting.RED), false);
            return;
        }
        GameMode mode = parseGameMode(gamemodeName);
        if (mode == null) {
            op.sendMessage(Text.literal("[WorldHost] Неверный режим игры: " + gamemodeName).formatted(Formatting.RED), false);
            return;
        }
        target.changeGameMode(mode);
        target.sendMessage(Text.literal("[WorldHost] Ваш режим игры изменён на: " + getGameModeName(mode)).formatted(Formatting.AQUA), false);
        broadcastToOps(server, Text.literal("[WorldHost] " + op.getName().getString() + " изменил режим " + targetName + " на " + getGameModeName(mode)).formatted(Formatting.AQUA));
    }

    public static void giveOp(MinecraftServer server, ServerPlayerEntity op, String targetName) {
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetName);
        if (target == null) {
            op.sendMessage(Text.literal("[WorldHost] Игрок " + targetName + " не найден.").formatted(Formatting.RED), false);
            return;
        }
        server.getPlayerManager().addToOperators(target.getGameProfile());
        target.sendMessage(Text.literal("[WorldHost] Вам выдан оператор!").formatted(Formatting.GOLD), false);
        broadcastToOps(server, Text.literal("[WorldHost] " + targetName + " получил права оператора.").formatted(Formatting.GOLD));
        sendPlayerListToClient(server, op);
    }

    public static void removeOp(MinecraftServer server, ServerPlayerEntity op, String targetName) {
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetName);
        if (target == null) {
            op.sendMessage(Text.literal("[WorldHost] Игрок " + targetName + " не найден.").formatted(Formatting.RED), false);
            return;
        }
        if (target == op) {
            op.sendMessage(Text.literal("[WorldHost] Нельзя снять оператора с себя.").formatted(Formatting.RED), false);
            return;
        }
        server.getPlayerManager().removeFromOperators(target.getGameProfile());
        target.sendMessage(Text.literal("[WorldHost] Права оператора сняты.").formatted(Formatting.RED), false);
        broadcastToOps(server, Text.literal("[WorldHost] У " + targetName + " сняты права оператора.").formatted(Formatting.YELLOW));
        sendPlayerListToClient(server, op);
    }

    public static void openToLan(MinecraftServer server, ServerPlayerEntity op, boolean allowCheats, String gamemodeName, String motd, int maxPlayers) {
        if (!(server instanceof IntegratedServer intServer)) {
            op.sendMessage(Text.literal("[WorldHost] Открытие LAN доступно только в одиночной игре!").formatted(Formatting.RED), false);
            return;
        }
        GameMode mode = parseGameMode(gamemodeName);
        if (mode == null) mode = GameMode.SURVIVAL;

        String port = intServer.openToLan(mode, allowCheats, 0);
        if (port != null) {
            op.sendMessage(Text.literal("[WorldHost] ✓ Мир открыт! Порт: " + port).formatted(Formatting.GREEN), false);
            broadcastToAll(server, Text.literal("[WorldHost] Мир открыт для других игроков!").formatted(Formatting.GREEN));
            sendStatusSync(server, op, true, port, motd, maxPlayers);
        } else {
            op.sendMessage(Text.literal("[WorldHost] Мир уже открыт или произошла ошибка.").formatted(Formatting.YELLOW), false);
            sendStatusSync(server, op, true, "?", motd, maxPlayers);
        }
        LOGGER.info("[WorldHost] {} открыл мир. Читы: {}, Режим: {}", op.getName().getString(), allowCheats, gamemodeName);
    }

    public static void closeLan(MinecraftServer server, ServerPlayerEntity op) {
        broadcastToAll(server, Text.literal("[WorldHost] Хост закрыл доступ к миру.").formatted(Formatting.RED));
        op.sendMessage(Text.literal("[WorldHost] Мир закрыт. Все игроки будут отключены.").formatted(Formatting.YELLOW), false);

        List<ServerPlayerEntity> toKick = new ArrayList<>(server.getPlayerManager().getPlayerList());
        for (ServerPlayerEntity p : toKick) {
            if (p != op) {
                p.networkHandler.disconnect(Text.literal("Хост закрыл мир."));
            }
        }
        sendStatusSync(server, op, false, "", "", 0);
    }

    public static void sendPlayerListToClient(MinecraftServer server, ServerPlayerEntity requester) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(players.size());
        for (ServerPlayerEntity p : players) {
            buf.writeString(p.getName().getString());
            buf.writeString(p.getUuidAsString());
            buf.writeString(p.interactionManager.getGameMode().getName());
            buf.writeBoolean(server.getPlayerManager().isOperator(p.getGameProfile()));
            buf.writeInt(p.networkHandler.getConnectionInfo() != null ? p.networkHandler.getConnectionInfo().latency() : 0);
        }
        ServerPlayNetworking.send(requester, WorldHostNetwork.PLAYER_LIST_SYNC, buf);
    }

    public static void sendPrivateMessage(MinecraftServer server, ServerPlayerEntity op, String targetName, String message) {
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetName);
        if (target == null) {
            op.sendMessage(Text.literal("[WorldHost] Игрок " + targetName + " не найден.").formatted(Formatting.RED), false);
            return;
        }
        target.sendMessage(Text.literal("[ЛС от " + op.getName().getString() + "]: " + message).formatted(Formatting.LIGHT_PURPLE), false);
        op.sendMessage(Text.literal("[ЛС → " + targetName + "]: " + message).formatted(Formatting.LIGHT_PURPLE), false);
    }

    public static void teleportToHost(MinecraftServer server, ServerPlayerEntity op, String targetName) {
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetName);
        if (target == null) {
            op.sendMessage(Text.literal("[WorldHost] Игрок " + targetName + " не найден.").formatted(Formatting.RED), false);
            return;
        }
        target.teleport(op.getServerWorld(), op.getX(), op.getY(), op.getZ(), op.getYaw(), op.getPitch());
        target.sendMessage(Text.literal("[WorldHost] Вас телепортировали к хосту.").formatted(Formatting.AQUA), false);
        op.sendMessage(Text.literal("[WorldHost] " + targetName + " телепортирован к вам.").formatted(Formatting.AQUA), false);
    }

    private static void sendStatusSync(MinecraftServer server, ServerPlayerEntity player, boolean open, String port, String motd, int maxPlayers) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(open);
        buf.writeString(port);
        buf.writeString(motd);
        buf.writeInt(maxPlayers);
        ServerPlayNetworking.send(player, WorldHostNetwork.STATUS_SYNC, buf);
    }

    private static void broadcastToOps(MinecraftServer server, Text message) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (server.getPlayerManager().isOperator(p.getGameProfile())) {
                p.sendMessage(message, false);
            }
        }
    }

    private static void broadcastToAll(MinecraftServer server, Text message) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(message, false);
        }
    }

    private static GameMode parseGameMode(String name) {
        return switch (name.toUpperCase()) {
            case "SURVIVAL", "S", "0" -> GameMode.SURVIVAL;
            case "CREATIVE", "C", "1" -> GameMode.CREATIVE;
            case "ADVENTURE", "A", "2" -> GameMode.ADVENTURE;
            case "SPECTATOR", "SP", "3" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private static String getGameModeName(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> "Выживание";
            case CREATIVE -> "Творческий";
            case ADVENTURE -> "Приключение";
            case SPECTATOR -> "Наблюдатель";
        };
    }
}
