package de.raindancer.modules.mannequin.listener;

import de.raindancer.modules.mannequin.model.Mannequin;
import de.raindancer.modules.mannequin.service.StatueService;
import de.raindancer.modules.mannequin.store.MannequinRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MannequinStatueListenerTest {

    private final MannequinRegistry registry = new MannequinRegistry();
    private final StatueService statues = mock(StatueService.class);

    private EntityDamageEvent damageTo(String id) {
        UUID entityId = UUID.randomUUID();
        registry.bindEntity(id, entityId);
        org.bukkit.entity.Mannequin live = mock(org.bukkit.entity.Mannequin.class);
        when(live.getUniqueId()).thenReturn(entityId);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(live);
        return event;
    }

    @Test
    @DisplayName("nothing hurts a statue — not a creative player, not fire, not the void")
    void aStatueTakesNoDamage() {
        registry.put(Mannequin.statue("MQ1", UUID.randomUUID(), UUID.randomUUID(), "Rain", "world", 0, 64, 0, 0f));
        EntityDamageEvent event = damageTo("MQ1");

        new MannequinStatueListener(registry, statues).onDamage(event);

        verify(event).setCancelled(true);
    }

    @Test
    @DisplayName("a training dummy is still hurt as before")
    void aDummyStillTakesDamage() {
        registry.put(Mannequin.freshlyPlaced("MQ1", UUID.randomUUID(), "world", 0, 64, 0));
        EntityDamageEvent event = damageTo("MQ1");

        new MannequinStatueListener(registry, statues).onDamage(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    @DisplayName("somebody joining is handed on, so their statues wear today's skin and name")
    void aJoinIsHandedOn() {
        Player player = mock(Player.class);
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new MannequinStatueListener(registry, statues).onJoin(event);

        verify(statues).playerJoined(player);
    }
}
