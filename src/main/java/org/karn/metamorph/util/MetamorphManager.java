package org.karn.metamorph.util;

import eu.pb4.playerdata.api.PlayerDataApi;
import eu.pb4.playerdata.api.storage.JsonDataStorage;
import eu.pb4.playerdata.api.storage.PlayerDataStorage;
import me.drex.vanish.api.VanishAPI;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class MetamorphManager {
    public static final PlayerDataStorage<MetamorphData> METAMORPH_DATA_STORAGE = new JsonDataStorage<>("metamorph", MetamorphData.class);

    public static boolean isMetamorph(MinecraftServer server, UUID uuid) {
        MetamorphData data = PlayerDataApi.getCustomDataFor(server, uuid, METAMORPH_DATA_STORAGE);
        return data != null && data.isMetamorph;
    }

    public static boolean setMetamorph(ServerPlayerEntity actor, boolean bool) {
        if (isMetamorph(actor.server, actor.getUuid()) == bool) return false;
        if (bool) metamorph(actor);
        MetamorphData data = PlayerDataApi.getCustomDataFor(actor, METAMORPH_DATA_STORAGE);
        if (data == null) data = new MetamorphData();
        data.isMetamorph = bool;
        PlayerDataApi.setCustomDataFor(actor, METAMORPH_DATA_STORAGE, data);
        if (!bool) unmetamorph(actor);
        return true;
    }

    private static void unmetamorph(ServerPlayerEntity actor) {
        VanishAPI.setVanish(actor,false);
    }

    private static void metamorph(ServerPlayerEntity actor) {
        VanishAPI.setVanish(actor,true);
    }
}
