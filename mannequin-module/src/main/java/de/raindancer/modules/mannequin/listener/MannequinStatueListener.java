package de.raindancer.modules.mannequin.listener;

import de.raindancer.modules.mannequin.model.Mannequin;
import de.raindancer.modules.mannequin.service.StatueService;
import de.raindancer.modules.mannequin.store.MannequinRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

/**
 * Statues are never hurt — {@code setInvulnerable} alone still lets a creative player through, so
 * every damage event is cancelled here as well. {@code /kill} goes past both, and is answered by
 * the ordinary respawn: only {@code /mannequin remove} takes a statue away for good.
 */
public final class MannequinStatueListener implements IMannequinListener {

    private final MannequinRegistry registry;
    private final StatueService statues;

    public MannequinStatueListener(MannequinRegistry registry, StatueService statues) {
        this.registry = registry;
        this.statues = statues;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        boolean statue = registry.idFor(event.getEntity().getUniqueId())
                .flatMap(registry::get)
                .map(Mannequin::isStatue)
                .orElse(false);
        if (statue) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        statues.playerJoined(event.getPlayer());
    }

    @Override
    public void forget(UUID player) {
        // Nothing per-player: a statue belongs to the mannequin, not to whoever looks at it.
    }

    @Override
    public String describe() {
        return "keeping statues unhurt, and in their player's current skin and name";
    }
}
