package de.raindancer.modules.mannequin.service;

import de.raindancer.core.ui.board.Leaderboard.Standing;
import de.raindancer.modules.mannequin.model.Mannequin;
import de.raindancer.modules.mannequin.rules.BoardStatueRule;
import de.raindancer.modules.mannequin.store.MannequinRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
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

class BoardStatueServiceTest {

    private final MannequinRegistry registry = new MannequinRegistry();
    private final org.bukkit.entity.Mannequin live = mock(org.bukkit.entity.Mannequin.class);
    private final List<Mannequin> saved = new ArrayList<>();
    private final List<UUID> skins = new ArrayList<>();
    private final UUID rain = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private List<Standing> board = List.of();
    private Optional<String> title = Optional.of("Fishing level");

    private BoardStatueService service() {
        return new BoardStatueService(registry, new BoardStatueRule(),
                id -> title,
                (id, n) -> board.subList(0, Math.min(n, board.size())),
                saved::add,
                id -> registry.liveEntity(id).map(ignored -> (LivingEntity) live),
                (entity, player) -> skins.add(player),
                statue -> Component.text(statue.displayName(), NamedTextColor.AQUA),
                (entity, task) -> task.run(), Runnable::run, Runnable::run);
    }

    private Mannequin placed(int place) {
        Mannequin statue = Mannequin.boardStatue("MQ1", UUID.randomUUID(), "fishing:level", place, "world", 0, 64, 0, 0f);
        registry.put(statue);
        registry.bindEntity("MQ1", UUID.randomUUID());
        return statue;
    }

    @Test
    @DisplayName("a new holder: the statue is saved as theirs, wears their skin, is named and captioned for them")
    void newHolder() {
        placed(1);
        board = List.of(new Standing(rain, "Rain", "level 25"));

        service().refreshAll();

        Mannequin now = registry.get("MQ1").orElseThrow();
        assertThat(now.statueOf()).isEqualTo(rain);
        assertThat(now.skinSource()).isEqualTo(rain);
        assertThat(now.displayName()).isEqualTo("Rain");
        assertThat(saved).hasSize(1);
        assertThat(skins).containsExactly(rain);
        verify(live).customName(Component.text("Rain", NamedTextColor.AQUA));
        verify(live).setDescription(Component.text("#1 Fishing level · level 25"));
    }

    @Test
    @DisplayName("the same holder with a new value: only the caption changes, no skin swap, nothing saved")
    void sameHolderNewValue() {
        placed(1);
        board = List.of(new Standing(rain, "Rain", "level 25"));
        BoardStatueService service = service();
        service.refreshAll();
        saved.clear();
        skins.clear();
        when(live.getDescription()).thenReturn(Component.text("#1 Fishing level · level 25"));

        board = List.of(new Standing(rain, "Rain", "level 26"));
        service.refreshAll();

        assertThat(saved).isEmpty();
        assertThat(skins).isEmpty();
        verify(live).setDescription(Component.text("#1 Fishing level · level 26"));
    }

    @Test
    @DisplayName("somebody overtakes: the statue becomes theirs")
    void overtaken() {
        placed(1);
        BoardStatueService service = service();
        board = List.of(new Standing(rain, "Rain", "level 25"));
        service.refreshAll();

        board = List.of(new Standing(other, "Other", "level 26"), new Standing(rain, "Rain", "level 25"));
        service.refreshAll();

        assertThat(registry.get("MQ1").orElseThrow().statueOf()).isEqualTo(other);
        assertThat(skins).containsExactly(rain, other);
    }

    @Test
    @DisplayName("each statue reads its own place")
    void places() {
        placed(2);
        board = List.of(new Standing(rain, "Rain", "level 25"), new Standing(other, "Other", "level 20"));

        service().refreshAll();

        assertThat(registry.get("MQ1").orElseThrow().statueOf()).isEqualTo(other);
    }

    @Test
    @DisplayName("nobody on the place, or the board gone: the neutral look — default skin, Nobody yet, place and title")
    void nobody() {
        placed(1);
        BoardStatueService service = service();
        board = List.of(new Standing(rain, "Rain", "level 25"));
        service.refreshAll();

        board = List.of();
        title = Optional.empty();
        service.refreshAll();

        Mannequin now = registry.get("MQ1").orElseThrow();
        assertThat(now.statueOf()).isNull();
        assertThat(now.isStatue()).isTrue();
        assertThat(now.displayName()).isEqualTo("Nobody yet");
        assertThat(skins).containsExactly(rain, null);
        verify(live).setDescription(Component.text("#1 fishing:level"));
    }

    @Test
    @DisplayName("a statue that is not live is still updated in the record, so it spawns right")
    void notLive() {
        registry.put(Mannequin.boardStatue("MQ1", UUID.randomUUID(), "fishing:level", 1, "world", 0, 64, 0, 0f));
        board = List.of(new Standing(rain, "Rain", "level 25"));

        service().refreshAll();

        assertThat(registry.get("MQ1").orElseThrow().statueOf()).isEqualTo(rain);
        verify(live, never()).customName(any(Component.class));
    }

    @Test
    @DisplayName("a board that throws leaves the statue standing for nobody instead of breaking the pass")
    void failingBoard() {
        placed(1);
        BoardStatueService service = new BoardStatueService(registry, new BoardStatueRule(), id -> title,
                (id, n) -> {
                    throw new IllegalStateException("down");
                }, saved::add, id -> Optional.empty(), (entity, player) -> {
                }, statue -> Component.empty(), (entity, task) -> task.run(), Runnable::run, Runnable::run);

        service.refreshAll();

        assertThat(registry.get("MQ1").orElseThrow().statueOf()).isNull();
    }

    @Test
    @DisplayName("a statue of an ordinary player is never asked of a board")
    void ordinaryStatuesAreLeftAlone() {
        registry.put(Mannequin.statue("MQ2", UUID.randomUUID(), other, "Other", "world", 0, 64, 0, 0f));
        board = List.of(new Standing(rain, "Rain", "level 25"));

        service().refreshAll();

        assertThat(registry.get("MQ2").orElseThrow().statueOf()).isEqualTo(other);
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("before the board has answered a spawn shows just the place and the title")
    void captionBeforeTheAnswer() {
        Mannequin statue = placed(3);

        assertThat(service().descriptionOf(statue)).isEqualTo(Component.text("#3 Fishing level"));
    }

    @Test
    @DisplayName("a spawn asks the board only when it is registered — at a restart it may simply not be yet")
    void spawnWaitsForAKnownBoard() {
        Mannequin statue = placed(1);
        board = List.of(new Standing(rain, "Rain", "level 25"));
        title = Optional.empty();

        service().spawned(statue);

        assertThat(registry.get("MQ1").orElseThrow().statueOf()).isNull();

        title = Optional.of("Fishing level");
        service().spawned(statue);

        assertThat(registry.get("MQ1").orElseThrow().statueOf()).isEqualTo(rain);
    }
}
