package de.raindancer.modules.mannequin.listener;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.mannequin.service.MannequinService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.UUID;

/**
 * Re-spawning a world's mannequins when it loads, and letting go of the live entities — never the
 * stored records — when it unloads; the same for each chunk, since the server drops the (unsaved)
 * entities with their chunk.
 */
public final class MannequinWorldListener implements IMannequinListener {

    private final MannequinService mannequins;
    private final org.bukkit.plugin.Plugin plugin;

    public MannequinWorldListener(MannequinService mannequins, org.bukkit.plugin.Plugin plugin) {
        this.mannequins = mannequins;
        this.plugin = plugin;
    }

    @EventHandler
    public void onLoad(WorldLoadEvent event) {
        mannequins.spawnAllIn(event.getWorld());
    }

    @EventHandler
    public void onUnload(WorldUnloadEvent event) {
        mannequins.despawnAllIn(event.getWorld().getName());
    }

    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        org.bukkit.Chunk chunk = event.getChunk();
        // A tick later: spawning into a chunk while it is still being loaded is not something to rely on.
        org.bukkit.Location at = new org.bukkit.Location(chunk.getWorld(), (chunk.getX() << 4) + 8, 64,
                (chunk.getZ() << 4) + 8);
        Scheduling.regionLater(plugin, at, 1L, () -> {
            if (chunk.isLoaded()) {
                mannequins.spawnMissingIn(chunk);
            }
        });
    }

    @EventHandler
    public void onChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
        mannequins.letGoIn(event.getChunk());
    }

    @Override
    public void forget(UUID player) {
        // Nothing per-player here: this listener only ever reacts to a world, never a player.
    }

    @Override
    public String describe() {
        return "spawning a world's mannequins when it loads, despawning them when it unloads — and so per chunk";
    }
}
