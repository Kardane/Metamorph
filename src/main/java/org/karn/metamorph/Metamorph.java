package org.karn.metamorph;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.karn.metamorph.command.MetamorphCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Metamorph implements DedicatedServerModInitializer {
    public static final String MOD_ID = "Metamorph";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeServer() {
        CommandRegistrationCallback.EVENT.register((dispatcher, commandRegistryAccess, ignored1) -> {
            MetamorphCommand.register(dispatcher);
        });
        LOGGER.info("Metamorph has Loaded!");
    }
}
