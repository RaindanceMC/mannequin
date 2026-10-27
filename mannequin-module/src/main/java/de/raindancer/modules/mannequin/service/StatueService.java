package de.raindancer.modules.mannequin.service;

import de.raindancer.modules.mannequin.MannequinSettings;
import de.raindancer.modules.mannequin.model.Mannequin;
import de.raindancer.modules.mannequin.store.MannequinRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Statues: mannequins that stand for a player and keep looking like them.
 *
 * <p>The name above it is Core's own nametag for that player ({@code Identities#nametag}) — rank
 * prefix, colour, gradient, nickname — redrawn on {@link #refreshNames}'s timer, so a restyle or an
 * animated gradient shows on the statue as it does above the player. Their skin is fetched fresh on
 * every spawn and taken from their session the moment they join ({@link #playerJoined}), which is
 * the earliest the server can know they changed it.
 */
public final class StatueService implements IMannequinService {

    private final MannequinRegistry registry;
    private final Function<String, Optional<LivingEntity>> live;
    private final Consumer<Mannequin> save;
    private final BiConsumer<org.bukkit.entity.Mannequin, Player> wearSkinOf;
    private final BiFunction<UUID, String, Component> nametag;
    private final Function<UUID, Optional<Component>> subtitle;
    private final BiConsumer<Entity, Runnable> onEntityThread;

    /**
     * @param wearSkinOf     puts an online player's own session profile on a live mannequin
     * @param onEntityThread runs a task on the thread owning that entity (Folia's region thread)
     */
    public StatueService(MannequinRegistry registry, Function<String, Optional<LivingEntity>> live,
                         Consumer<Mannequin> save, BiConsumer<org.bukkit.entity.Mannequin, Player> wearSkinOf,
                         BiFunction<UUID, String, Component> nametag,
                         Function<UUID, Optional<Component>> subtitle,
                         BiConsumer<Entity, Runnable> onEntityThread) {
        this.registry = registry;
        this.live = live;
        this.save = save;
        this.wearSkinOf = wearSkinOf;
        this.nametag = nametag;
        this.subtitle = subtitle;
        this.onEntityThread = onEntityThread;
    }

    @Override
    public void settings(MannequinSettings settings) {
        // Nothing configurable yet.
    }

    /** Everything that makes a freshly spawned mannequin a statue, on top of what every mannequin gets. */
    public void dress(LivingEntity entity, Mannequin statue) {
        entity.customName(nameOf(statue));
        entity.setCustomNameVisible(true);
        entity.setInvulnerable(true);
        entity.setCanPickupItems(false);
        if (entity instanceof org.bukkit.entity.Mannequin mannequin) {
            // null hides the description line; vanilla's default would read "NPC".
            mannequin.setDescription(subtitle.apply(statue.statueOf()).orElse(null));
        }
    }

    /** Draws every live statue's name again where it no longer matches the player's style. */
    public void refreshNames() {
        for (Mannequin statue : registry.all()) {
            if (!statue.isStatue()) {
                continue;
            }
            live.apply(statue.id()).ifPresent(entity -> onEntityThread.accept(entity, () -> rename(entity, statue)));
        }
    }

    /** Their statues take on the skin they joined with, and the name they have now. */
    public void playerJoined(Player player) {
        UUID who = player.getUniqueId();
        for (Mannequin statue : registry.all()) {
            if (!who.equals(statue.statueOf())) {
                continue;
            }
            Mannequin current = statue;
            String name = player.getName();
            if (name != null && !name.isBlank() && !name.equals(statue.displayName())) {
                current = statue.withDisplayName(name);
                registry.put(current);
                save.accept(current);
            }
            Mannequin shown = current;
            live.apply(statue.id()).ifPresent(entity -> onEntityThread.accept(entity, () -> {
                if (entity instanceof org.bukkit.entity.Mannequin mannequin) {
                    wearSkinOf.accept(mannequin, player);
                }
                rename(entity, shown);
            }));
        }
    }

    private void rename(LivingEntity entity, Mannequin statue) {
        Component name = nameOf(statue);
        if (!Objects.equals(entity.customName(), name)) {
            entity.customName(name);
        }
    }

    private Component nameOf(Mannequin statue) {
        return nametag.apply(statue.statueOf(), statue.displayName());
    }
}
