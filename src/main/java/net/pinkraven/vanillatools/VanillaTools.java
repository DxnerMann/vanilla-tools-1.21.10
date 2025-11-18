package net.pinkraven.vanillatools;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
import net.pinkraven.vanillatools.status.StatusManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class VanillaTools implements ModInitializer {
	public static final String MOD_ID = "vanilla-tools";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final StatusManager statusManager = new StatusManager();

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(statusManager.getStatusCommand());
		});
	}
}
