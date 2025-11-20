package net.pinkraven.vanillatools.spawnglider;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue;
import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKeys;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class SpawngliderManager {

    private final LiteralArgumentBuilder<ServerCommandSource> spawngliderCommand;

    private BlockPos spawnCenter = null;
    private float spawnRadius = 0;
    private boolean spawnActive = false;
    private final Map<UUID, ItemStack> originalHelmets = new HashMap<>();
    private final Map<UUID, ServerPlayerEntity> playersInArea = new HashMap<>();
    private static final String GLIDER_HELMET_TAG = "spawn_glider_helmet";

    public SpawngliderManager() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);

        this.spawngliderCommand = literal("spawnglider")
                // spawnglider setspawn <pos> <radius>
                .then(literal("setspawn")
                        .requires(source -> source.hasPermissionLevel(2)) // Nur Operatoren
                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                .then(argument("radius", FloatArgumentType.floatArg())
                                        .executes(context -> {
                                            BlockPos blockPos = BlockPosArgumentType.getBlockPos(context, "pos");
                                            final float posx = (float) blockPos.getX();
                                            final float posy = (float) blockPos.getY();
                                            final float posz = (float) blockPos.getZ();
                                            final float radius = FloatArgumentType.getFloat(context, "radius");
                                            try {
                                                setspawn(posx, posy, posz, radius);
                                                context.getSource().sendFeedback(() -> Text.literal(String.format("Created new Spawn Glider Area at %s %s %s with radius %s", posx, posy, posz, radius)), true);
                                                return 1;
                                            } catch (IllegalArgumentException e) {
                                                context.getSource().sendError(Text.literal(e.getMessage()));
                                                return 0;
                                            }
                                        })
                                )
                        ))
                .then(literal("removespawn")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> {
                            try {
                                removeSpawn();
                                context.getSource().sendFeedback(() -> Text.literal("Removed Spawn Glider Area"), true);
                                return 1;
                            } catch (IllegalArgumentException e) {
                                context.getSource().sendError(Text.literal(e.getMessage()));
                                return 0;
                            }
                        }));
    }

    private void setspawn(float posx, float posy, float posz, float radius) {
        this.spawnCenter = new BlockPos((int) posx, (int) posy, (int) posz);
        this.spawnRadius = radius;
        this.spawnActive = true;
    }

    private void removeSpawn() {
        this.spawnActive = false;
        this.spawnCenter = null;
        this.spawnRadius = 0;

        // Remove gliders from all tracked players
        // Note: We get the server from the tick method, so we need to store it or get it differently
        // For safety, we'll just clear the maps and let the next tick clean up
        for (Map.Entry<UUID, ItemStack> entry : new HashMap<>(originalHelmets).entrySet()) {
            originalHelmets.remove(entry.getKey());
            restoreOriginalHelmet(playersInArea.get(entry.getKey()));
        }

        playersInArea.clear();
    }

    public void tick(MinecraftServer server) {
        if (!spawnActive || spawnCenter == null) {
            // Clean up any remaining glider helmets when area is inactive
            if (!originalHelmets.isEmpty()) {
                for (UUID playerId : new HashSet<>(originalHelmets.keySet())) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                    if (player != null) {
                        restoreOriginalHelmet(player);
                    }
                }
            }
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean isInArea = isPlayerInArea(player);
            UUID playerId = player.getUuid();
            ItemStack currentHelmet = player.getInventory().getStack(39);

            if (isInArea) {
                // Player is in area
                if (!playersInArea.keySet().contains(playerId)) {
                    // Player just entered the area
                    playersInArea.put(playerId, player);
                    applyGliderHelmet(player, server);
                } else {
                    // Player is still in area - check if they removed the helmet
                    if (!isGliderHelmet(currentHelmet)) {
                        // They removed it or it got replaced - reapply it
                        applyGliderHelmet(player, server);
                    }
                }
            } else {
                // Player is outside area
                if (playersInArea.keySet().contains(playerId)) {
                    // Player just left the area
                    if (player.isOnGround()) {
                        // Remove glider only if on ground
                        playersInArea.remove(playerId);
                        restoreOriginalHelmet(player);
                    } else {
                        // Still in air - keep helmet on but check if they removed it
                        if (!isGliderHelmet(currentHelmet)) {
                            applyGliderHelmet(player, server);
                        }
                    }
                }
            }
        }
    }

    private boolean isPlayerInArea(ServerPlayerEntity player) {
        if (spawnCenter == null) {
            return false;
        }

        Vec3d playerPos = player.getEyePos();
        double dx = playerPos.x - spawnCenter.getX();
        double dy = playerPos.y - spawnCenter.getY();
        double dz = playerPos.z - spawnCenter.getZ();

        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= spawnRadius;
    }

    private void applyGliderHelmet(ServerPlayerEntity player, MinecraftServer server) {
        UUID playerId = player.getUuid();
        ItemStack currentHelmet = player.getInventory().getStack(39);

        // Store original helmet if not already stored and current helmet is not our glider helmet
        if (!originalHelmets.containsKey(playerId) && !isGliderHelmet(currentHelmet)) {
            originalHelmets.put(playerId, currentHelmet.copy());
        }

        ItemStack gliderHelmet;
        ItemStack original = originalHelmets.get(playerId);

        if (original == null || original.isEmpty()) {
            // Give gray leather helmet
            gliderHelmet = new ItemStack(Items.LEATHER_HELMET);
            gliderHelmet.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(0x808080));
        } else {
            // Use existing helmet
            gliderHelmet = original.copy();
        }

        RegistryWrapper.Impl<Enchantment> enchRegistry =
                server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);

        // Look up the RegistryEntry for the vanilla BINDING_CURSE key
        RegistryEntry<Enchantment> binding = enchRegistry.getOrThrow(Enchantments.BINDING_CURSE);

        // Finally apply it
        gliderHelmet.addEnchantment(binding, 1);
        // Mark this as our special glider helmet using custom name
        gliderHelmet.set(DataComponentTypes.GLIDER, Unit.INSTANCE);
        gliderHelmet.set(DataComponentTypes.CUSTOM_NAME, Text.literal(GLIDER_HELMET_TAG));

        player.getInventory().setStack(39, gliderHelmet);
    }

    private void restoreOriginalHelmet(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        ItemStack originalHelmet = originalHelmets.remove(playerId);

        if (originalHelmet != null) {
            player.getInventory().setStack(39, originalHelmet);
        } else {
            // No original helmet, remove the glider helmet
            player.getInventory().setStack(39, ItemStack.EMPTY);
        }
    }

    private boolean isGliderHelmet(ItemStack helmet) {
        if (helmet.isEmpty()) {
            return false;
        }

        // Check if this helmet has our special tag
        if (helmet.contains(DataComponentTypes.CUSTOM_NAME)) {
            String name = helmet.get(DataComponentTypes.CUSTOM_NAME).getString();
            return GLIDER_HELMET_TAG.equals(name);
        }

        return false;
    }

    public LiteralArgumentBuilder<ServerCommandSource> getSpawngliderCommand() {
        return spawngliderCommand;
    }
}
