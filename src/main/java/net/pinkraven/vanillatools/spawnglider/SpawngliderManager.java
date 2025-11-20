package net.pinkraven.vanillatools.spawnglider;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class SpawngliderManager {

    private final LiteralArgumentBuilder<ServerCommandSource> spawngliderCommand;

    public SpawngliderManager() {
        this.spawngliderCommand = literal("spawnglider")
        // spawnglider setspawn <posx> <posy> <posy> <radius>
        .then(literal("setspawn")
        .requires(source -> source.hasPermissionLevel(2)) // Nur Operatoren
        .then(argument("posx", FloatArgumentType.floatArg())
            .then(argument("posy", FloatArgumentType.floatArg())
                .then(argument("posz", FloatArgumentType.floatArg())
                    .then(argument("radius", FloatArgumentType.floatArg())
                        .executes(context -> {
                            final float posx = FloatArgumentType.getFloat(context, "posx");
                            final float posy = FloatArgumentType.getFloat(context, "posy");
                            final float posz = FloatArgumentType.getFloat(context, "posz");
                            final float radius = FloatArgumentType.getFloat(context, "radius");
                            try {
                                setspawn(posx, posy, posz, radius);
                                context.getSource().sendFeedback(() -> Text.literal(String.format("Created new Spawn Glider Area at %s %s %s with radius %s", posx, posy, posz, radius )), true);
                                return 0;
                            } catch (IllegalArgumentException e) {
                                context.getSource().sendError(Text.literal(e.getMessage()));
                                return 0;
                            }
                        })
                    )
                )
            )
        ).executes(context -> {
                final float posx = FloatArgumentType.getFloat(context, "posx");
                final float posy = FloatArgumentType.getFloat(context, "posy");
                final float posz = FloatArgumentType.getFloat(context, "posz");
                final float radius = FloatArgumentType.getFloat(context, "radius");
                try {
                    removeSpawn();
                    context.getSource().sendFeedback(() -> Text.literal("Removed Spawn Glider Area"), true);
                    return 0;
                } catch (IllegalArgumentException e) {
                    context.getSource().sendError(Text.literal(e.getMessage()));
                    return 0;
                }
            })
        );
    }

    private void setspawn(float posx, float posy, float posz, float radius) {

    }

    private void removeSpawn() {

    }

    public LiteralArgumentBuilder<ServerCommandSource> getSpawngliderCommand() {
        return spawngliderCommand;
    }
}
