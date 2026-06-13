package com.worldhost.network;

import com.worldhost.WorldHostMod;
import com.worldhost.server.WorldHostPlayerManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class WorldHostNetwork {

    public static final Identifier KICK_PLAYER     = new Identifier("worldhost", "kick_player");
    public static final Identifier BAN_PLAYER      = new Identifier("worldhost", "ban_player");
    public static final Identifier UNBAN_PLAYER    = new Identifier("worldhost", "unban_player");
    public static final Identifier CHANGE_GAMEMODE = new Identifier("worldhost", "change_gamemode");
    public static final Identifier GIVE_OP         = new Identifier("worldhost", "give_op");
    public static final Identifier REMOVE_OP       = new Identifier("worldhost", "remove_op");
    public static final Identifier OPEN_TO_LAN     = new Identifier("worldhost", "open_to_lan");
    public static final Identifier CLOSE_LAN       = new Identifier("worldhost", "close_lan");
    public static final Identifier GET_PLAYER_LIST = new Identifier("worldhost", "get_player_list");
    public static final Identifier PLAYER_LIST_SYNC= new Identifier("worldhost", "player_list_sync");
    public static final Identifier STATUS_SYNC     = new Identifier("worldhost", "status_sync");
    public static final Identifier SEND_MESSAGE    = new Identifier("worldhost", "send_message");
    public static final Identifier TELEPORT_PLAYER = new Identifier("worldhost", "teleport_player");

    public static void registerServerPackets() {

        ServerPlayNetworking.registerGlobalReceiver(KICK_PLAYER, (server, player, handler, buf, sender) -> {
            if (!WorldHostPlayerManager.isOperator(server, player)) return;
            String targetName = buf.readString();
            String reason = buf.readString();
            server.execute(() -> WorldHostPlayerManager.kickPlayer(server, player, targetName, reason));
        });

        ServerPlayNetworking.registerGlobalReceiver(BAN_PLAYER, (server, player, handler, buf, sender) -> {
            if (!WorldHostPlayerManager.isOperator(server, player)) return;
            String targetName = buf.readString();
            String reason = buf.readString();
            server.execute(() -> WorldHostPlayerManager.banPlayer(server, player, targetName, reason));
        });

        ServerPlayNetworking.registerGlobalReceiver(UNBAN_PLAYER, (server, player, handler, buf, sender) -> {
            if (!WorldHostPlayerManager.isOperator(server, player)) return;
            String targetName = buf.readString();
            server.execute(() -> WorldHostPlayerManager.unbanPlayer(server, player, targetName));
        });

        ServerPlayNetworking.registerGlobalReceiver(CHANGE_GAMEMODE, (server, player, handler, buf, sender) -> {
            if (!WorldHostPlayerManager.isOperator(server, player)) return;
            String targetName = buf.readString();
            String gamemode = buf.readString();
            server.execute(() -> WorldHostPlayerManager.changeGameMode(server, player, targetName, gamemode));
        });

        ServerPlayNetworking.registerGlobalReceiver(GIVE_OP, (server, player, handler, buf, sender) -> {
            if (!WorldHostPlayerManager.isOperator(server, player)) return;
            String targetName = buf.readString();
            server.execute(() -> WorldHostPlayerManager.giveOp(server, player, targetName));
        });

        ServerPlayNetworking.registerGlobalReceiver(REMOVE_OP, (server, player, handler, buf, sender) -> {
            if (!WorldHostPlayerManager.isOperator(server, player)) return;
            String targetName = buf.readString();
            server.execute(() -> WorldHostPlayerManager.removeOp(server, player, targetName));
        });

        ServerPlayNetworking.registerGlobalReceiver(OPEN_TO_LAN, (server, player, handler, buf, sender) -> {
            if (!WorldHostPlayerManager.isOperator(server, player)) return;
            boolean allowCheats = buf.readBoolean();
            String gamemode = buf.readString();
            String motd = buf.readString();
            int maxPlayers = buf.readInt();
            server.execute(() -> WorldHostPlayerManager.openToLan(server, player, allowCheats, gamemode, motd, maxPlayers));
        });

        ServerPlayNetworking.registerGlobalReceiver(CLOSE_LAN, (server, player, handler, buf, sender) -> {
            if (!WorldHostPlayerManager.isOperator(server, player)) return;
            server.execute(() -> WorldHostPlayerManager.closeLan(server, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(GET_PLAYER_LIST, (server, player, handler, buf, sender) -> {
            server.execute(() -> WorldHostPlayerManager.sendPlayerListToClient(server, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(SEND_MESSAGE, (server, player, handler, buf, sender) -> {
            if (!WorldHostPlayerManager.isOperator(server, player)) return;
            String targetName = buf.readString();
            String message = buf.readString();
            server.execute(() -> WorldHostPlayerManager.sendPrivateMessage(server, player, targetName, message));
        });

        ServerPlayNetworking.registerGlobalReceiver(TELEPORT_PLAYER, (server, player, handler, buf, sender) -> {
            if (!WorldHostPlayerManager.isOperator(server, player)) return;
            String targetName = buf.readString();
            server.execute(() -> WorldHostPlayerManager.teleportToHost(server, player, targetName));
        });
    }
}
