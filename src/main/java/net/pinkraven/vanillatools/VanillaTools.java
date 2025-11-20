package net.pinkraven.vanillatools;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.pinkraven.vanillatools.spawnglider.SpawngliderManager;
import net.pinkraven.vanillatools.status.StatusManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VanillaTools implements ModInitializer {
	public static final String MOD_ID = "vanilla-tools";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final StatusManager statusManager = new StatusManager();
	public static final SpawngliderManager spawngliderManager = new SpawngliderManager();

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(statusManager.getStatusCommand());
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(spawngliderManager.getSpawngliderCommand());
		});
	}
}
