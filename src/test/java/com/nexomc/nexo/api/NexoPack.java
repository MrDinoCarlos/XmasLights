package com.nexomc.nexo.api;

import es.astralicastudio.xmaslights.NexoHostFixture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Test-only shape of the documented optional API. Never packaged in the plugin JAR. */
public final class NexoPack {
    private NexoPack() { }
    public static void sendPack(Player player) {
        ((NexoHostFixture) Bukkit.getPluginManager().getPlugin("Nexo")).server.sendPack(player);
    }
}
