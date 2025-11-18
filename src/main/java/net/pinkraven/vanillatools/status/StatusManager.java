package net.pinkraven.vanillatools.status;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.network.encryption.PublicPlayerSession;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Nullables;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class StatusManager {

    private Map<String, String> statuses;
    private final LiteralArgumentBuilder<ServerCommandSource> statusCommand;

    public StatusManager() {
        statuses = new HashMap<>();

        this.statusCommand = literal("status")
                // status <status>
                .then(argument("status", StringArgumentType.string())
                        .suggests(getStatusSuggestions())
                        .executes(context -> {
                            final String status = StringArgumentType.getString(context, "status");
                            try {
                                setStatus(status, context.getSource().getPlayer());
                                context.getSource().sendFeedback(() -> Text.literal("Status is now: " + status), false);
                                return 0;
                            } catch (IllegalArgumentException e) {
                                context.getSource().sendError(Text.literal(e.getMessage()));
                                return 0;
                            }
                        })
                )
                // status info (nur für Operatoren)
                .then(literal("info")
                        .requires(source -> source.hasPermissionLevel(2)) // Nur Operatoren
                        .executes(context -> {
                            try {
                                context.getSource().sendFeedback(() -> Text.literal(getStatusInfo()), false);
                                return 0;
                            } catch (IllegalArgumentException e) {
                                context.getSource().sendError(Text.literal(e.getMessage()));
                                return 0;
                            }
                        })
                )
                // status create <name> <color> (nur für Operatoren)
                .then(literal("create")
                        .requires(source -> source.hasPermissionLevel(2)) // Nur Operatoren
                        .then(argument("name", StringArgumentType.string())
                                .then(argument("color", StringArgumentType.string())
                                        .suggests(getColorSuggestions())
                                        .executes(context -> {
                                            final String name = StringArgumentType.getString(context, "name");
                                            final String color = StringArgumentType.getString(context, "color");
                                            try {
                                                createStatus(name, color);
                                                context.getSource().sendFeedback(() -> Text.literal("Created Status: " + name + " with Color " + color), true);
                                                return 0;
                                            } catch (IllegalArgumentException e) {
                                                context.getSource().sendError(Text.literal(e.getMessage()));
                                                return 0;
                                            }
                                        })
                                )
                        )
                )
                // status remove <name> (nur für Operatoren)
                .then(literal("remove")
                        .requires(source -> source.hasPermissionLevel(2)) // Nur Operatoren
                        .then(argument("name", StringArgumentType.string())
                                .suggests(getStatusSuggestions())
                                .executes(context -> {
                                    final String name = StringArgumentType.getString(context, "name");
                                    try {
                                        removeStatus(name);
                                        context.getSource().sendFeedback(() -> Text.literal("Removed Status: " + name), true);
                                        return 0;
                                    } catch (IllegalArgumentException e) {
                                        context.getSource().sendError(Text.literal(e.getMessage()));
                                        return 0;
                                    }
                                })
                        )
                );
    }

    public LiteralArgumentBuilder<ServerCommandSource> getStatusCommand() {
        return statusCommand;
    }

    private SuggestionProvider<ServerCommandSource> getStatusSuggestions() {
        return (context, builder) -> {
            if (statuses != null) {
                statuses.keySet().forEach(builder::suggest);
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<ServerCommandSource> getColorSuggestions() {
        return (context, builder) -> {
            Formatting.getNames(true, false).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private void createStatus(String name, String color) {
        if (!statuses.containsKey(name)) {
            statuses.put(name, color);
        } else {
            throw new IllegalArgumentException("Status " + name + " already exists.");
        }

        /*TODO:
        * - DO I NEED TO SAVE THIS FOR SERVER RESTART?
        * */

    }

    private void removeStatus(String name) {
        if (statuses.containsKey(name)) {
            statuses.remove(name);
        } else {
            throw new IllegalArgumentException("No status with name " + name);
        }

    }

    private void setStatus(String name, ServerPlayerEntity player) {
        String playerName = player.getName().getString();
        String color = statuses.get(name);

        Text newName = Text.literal("[")
                .append(Text.literal(name).formatted(Formatting.valueOf(color.toUpperCase())))
                .append("] ")
                .append(Text.literal(playerName));

        player.setCustomName(newName);
        player.setCustomNameVisible(true);

        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) {
            server.getPlayerManager().sendToAll(
                    new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, player)
            );
        }

        /*
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) {
            PlayerListS2CPacket.Entry entry = new PlayerListS2CPacket.Entry(
                    player.getUuid(),
                    player.getGameProfile(),
                    true,
                    player.networkHandler.getLatency(),
                    player.interactionManager.getGameMode(),
                    newName,
                    player.isModelPartVisible(PlayerModelPart.HAT),
                    player.getPlayerListOrder(),
                    (PublicPlayerSession.Serialized) Nullables.map(player.getSession(), PublicPlayerSession::toSerialized)
            );

            PlayerListS2CPacket packet = new PlayerListS2CPacket(
                    EnumSet.of(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME),
                    List.of(entry)
            );

            for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
                viewer.networkHandler.sendPacket(packet);
            }
        }
        */
    }

    private String getStatusInfo() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Existig Statuses:\n");
        statuses.forEach((name, color) -> {
            stringBuilder.append(name + ": ").append(color.formatted(Formatting.valueOf(color.toUpperCase()))).append("\n");
        });
        return stringBuilder.toString();
    }
}
