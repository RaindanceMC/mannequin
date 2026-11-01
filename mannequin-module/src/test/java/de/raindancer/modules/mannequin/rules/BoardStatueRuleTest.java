package de.raindancer.modules.mannequin.rules;

import de.raindancer.core.ui.board.Leaderboard.Standing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BoardStatueRuleTest {

    private final BoardStatueRule rule = new BoardStatueRule();
    private final UUID rain = UUID.randomUUID();

    @Test
    @DisplayName("a holder: their name, and the place, the board and the value they hold it with")
    void holder() {
        var caption = rule.caption(1, "Fishing level", "fishing:level", new Standing(rain, "Rain", "level 25"));

        assertThat(caption.name()).isEqualTo("Rain");
        assertThat(caption.line()).isEqualTo("#1 Fishing level · level 25");
    }

    @Test
    @DisplayName("nobody holds the place: a neutral name and just the place and board")
    void nobody() {
        var caption = rule.caption(3, "Fishing level", "fishing:level", null);

        assertThat(caption.name()).isEqualTo("Nobody yet");
        assertThat(caption.line()).isEqualTo("#3 Fishing level");
    }

    @Test
    @DisplayName("a board that is not there names its id instead of a title")
    void unknownBoard() {
        assertThat(rule.caption(2, null, "fishing:level", null).line()).isEqualTo("#2 fishing:level");
        assertThat(rule.caption(2, " ", "fishing:level", null).line()).isEqualTo("#2 fishing:level");
    }

    @Test
    @DisplayName("a standing without a value or a name does not print gaps")
    void gaps() {
        var caption = rule.caption(1, "Fishing level", "fishing:level", new Standing(rain, " ", null));

        assertThat(caption.line()).isEqualTo("#1 Fishing level");
        assertThat(caption.name()).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("a standing with no player counts as nobody")
    void noPlayer() {
        assertThat(rule.caption(1, "T", "a:b", new Standing(null, "Ghost", "9")).name()).isEqualTo("Nobody yet");
        assertThat(rule.holder(new Standing(null, "Ghost", "9"))).isNull();
    }

    @Test
    @DisplayName("the holder changed when somebody else, or nobody, or somebody after nobody holds it")
    void holderChanged() {
        UUID other = UUID.randomUUID();

        assertThat(rule.holderChanged(rain, new Standing(rain, "Rain", "1"))).isFalse();
        assertThat(rule.holderChanged(rain, new Standing(other, "Other", "1"))).isTrue();
        assertThat(rule.holderChanged(rain, null)).isTrue();
        assertThat(rule.holderChanged(null, null)).isFalse();
        assertThat(rule.holderChanged(null, new Standing(rain, "Rain", "1"))).isTrue();
    }

    @Test
    @DisplayName("the same holder under a new name is a rename, not a new holder")
    void renamed() {
        assertThat(rule.renamed("Old", new Standing(rain, "New", "1"))).isTrue();
        assertThat(rule.renamed("New", new Standing(rain, "New", "1"))).isFalse();
        assertThat(rule.renamed("New", null)).isFalse();
    }
}
