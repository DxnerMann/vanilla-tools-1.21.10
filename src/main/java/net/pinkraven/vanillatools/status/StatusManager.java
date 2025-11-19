package net.pinkraven.vanillatools.status;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class StatusManager {

    private Map<String, String> statuses;
    private Map<Team, List<ServerPlayerEntity>> teams;
    private final LiteralArgumentBuilder<ServerCommandSource> statusCommand;

    public StatusManager() {
		statuses = new HashMap<>();
        teams = new HashMap<>();

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
                                                createStatus(name, color, context.getSource().getPlayer());
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
                                        deleteStatus(name, context.getSource().getPlayer());
                                        context.getSource().sendFeedback(() -> Text.literal("Removed Status: " + name), true);
                                        return 0;
                                    } catch (IllegalArgumentException e) {
                                        context.getSource().sendError(Text.literal(e.getMessage()));
                                        return 0;
                                    }
                                })
                        )
                ).executes(context -> {
                    try {
                        removeStatus(context.getSource().getPlayer());
                        context.getSource().sendFeedback(() -> Text.literal("Status is now removed"), false);
                        return 0;
                    } catch (IllegalArgumentException e) {
                        context.getSource().sendError(Text.literal(e.getMessage()));
                        return 0;
                    }
                });
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

    private void createStatus(String name, String color, ServerPlayerEntity player) {
        if (!statuses.containsKey(name)) {
            statuses.put(name, color);

			Team newTeam = player.getEntityWorld().getScoreboard().addTeam(name);
			newTeam.setDisplayName(Text.literal(name));

			Text prefix = Text.literal("[").formatted(Formatting.GRAY)
					.append(Text.literal(name).formatted(Formatting.valueOf(color.toUpperCase())))
					.append(Text.literal("]")).formatted(Formatting.GRAY)
					.append(" ");
			newTeam.setPrefix(prefix);

            teams.put(newTeam, List.of());

        } else {
            throw new IllegalArgumentException("Status " + name + " already exists.");
        }
    }

    private void deleteStatus(String name, ServerPlayerEntity player) {
        if (statuses.containsKey(name)) {
            statuses.remove(name);

			player.getEntityWorld().getServer().getScoreboard().removeTeam(player.getEntityWorld().getScoreboard().getTeam(name));
            teams.remove(player.getEntityWorld().getScoreboard().getTeam(name));
        } else {
            throw new IllegalArgumentException("No status with name " + name);
        }

    }

    private void setStatus(String name, ServerPlayerEntity player) {
        Scoreboard scoreboard = player.getEntityWorld().getServer().getScoreboard();
        Team team = scoreboard.getTeam(name);

        if (team == null) {
            throw new IllegalArgumentException("No Status with name " + name);
        } else {
            String playerName = player.getName().getString();
            scoreboard.addScoreHolderToTeam(playerName, team);

            teams.computeIfAbsent(team, k -> new ArrayList<>()).add(player);
        }
    }

    private void removeStatus(ServerPlayerEntity player) {
        Scoreboard scoreboard = player.getEntityWorld().getScoreboard();
        scoreboard.removeScoreHolderFromTeam(player.getName().getString(), getTeamFromPlayer(player));
    }

    private String getStatusInfo() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Existig Statuses:\n");
        statuses.forEach((name, color) -> {
            stringBuilder.append(name + ": ").append(color.formatted(Formatting.valueOf(color.toUpperCase()))).append("\n");
        });
        return stringBuilder.toString();
    }

    private Team getTeamFromPlayer(ServerPlayerEntity player) {
        return teams.entrySet().stream()
                .filter(entry -> entry.getValue().contains(player))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
