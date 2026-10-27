package de.raindancer.modules.mannequin.service;

import de.raindancer.modules.mannequin.model.Mannequin;
import de.raindancer.modules.mannequin.store.MannequinRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatueServiceTest {

    private final UUID depicted = UUID.randomUUID();
    private final MannequinRegistry registry = new MannequinRegistry();
    private final org.bukkit.entity.Mannequin live = mock(org.bukkit.entity.Mannequin.class);
    private final List<Mannequin> saved = new ArrayList<>();
    private final List<Player> skinsWorn = new ArrayList<>();
    private Component subtitle;

    private StatueService service() {
        return new StatueService(registry,
                id -> registry.liveEntity(id).map(ignored -> (LivingEntity) live),
                saved::add,
                (entity, player) -> skinsWorn.add(player),
                (player, name) -> Component.text(name, NamedTextColor.AQUA),
                player -> Optional.ofNullable(subtitle),
                (entity, task) -> task.run());
    }

    private Mannequin placed(String name) {
        Mannequin statue = Mannequin.statue("MQ1", UUID.randomUUID(), depicted, name, "world", 0, 64, 0, 0f);
        registry.put(statue);
        registry.bindEntity("MQ1", UUID.randomUUID());
        return statue;
    }

    @Test
    @DisplayName("a statue is dressed as the player: their styled name, never hurt, picking nothing up")
    void dressing() {
        subtitle = Component.text("Builder");
        service().dress(live, placed("Rain"));

        verify(live).customName(Component.text("Rain", NamedTextColor.AQUA));
        verify(live).setCustomNameVisible(true);
        verify(live).setInvulnerable(true);
        verify(live).setCanPickupItems(false);
        verify(live).setDescription(Component.text("Builder"));
    }

    @Test
    @DisplayName("no subtitle hides the grey NPC line instead of showing it")
    void noSubtitleHidesTheDescription() {
        service().dress(live, placed("Rain"));

        verify(live).setDescription(null);
    }

    @Test
    @DisplayName("a changed name style is drawn again; an unchanged one is left alone")
    void namesFollowTheirStyle() {
        placed("Rain");
        when(live.customName()).thenReturn(Component.text("Rain", NamedTextColor.RED));
        service().refreshNames();
        verify(live).customName(Component.text("Rain", NamedTextColor.AQUA));

        org.mockito.Mockito.clearInvocations(live);
        when(live.customName()).thenReturn(Component.text("Rain", NamedTextColor.AQUA));
        service().refreshNames();
        verify(live, never()).customName(any(Component.class));
    }

    @Test
    @DisplayName("the player joining puts their current skin and current name on their statue")
    void joiningRefreshesSkinAndName() {
        placed("OldName");
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(depicted);
        when(player.getName()).thenReturn("NewName");

        service().playerJoined(player);

        assertThat(skinsWorn).containsExactly(player);
        assertThat(registry.get("MQ1")).get().extracting(Mannequin::displayName).isEqualTo("NewName");
        assertThat(saved).extracting(Mannequin::displayName).containsExactly("NewName");
        verify(live).customName(Component.text("NewName", NamedTextColor.AQUA));
    }

    @Test
    @DisplayName("an unchanged name is not written again")
    void anUnchangedNameIsNotSaved() {
        placed("Rain");
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(depicted);
        when(player.getName()).thenReturn("Rain");

        service().playerJoined(player);

        assertThat(skinsWorn).containsExactly(player);
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("somebody else joining leaves the statue alone")
    void someoneElseJoining() {
        placed("Rain");
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        service().playerJoined(player);

        assertThat(skinsWorn).isEmpty();
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("a training dummy is never treated as a statue")
    void dummiesAreLeftAlone() {
        registry.put(Mannequin.freshlyPlaced("MQ2", UUID.randomUUID(), "world", 0, 64, 0).withSkinSource(depicted));
        registry.bindEntity("MQ2", UUID.randomUUID());
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(depicted);

        service().playerJoined(player);
        service().refreshNames();

        assertThat(skinsWorn).isEmpty();
        verify(live, never()).customName(any(Component.class));
    }
}
