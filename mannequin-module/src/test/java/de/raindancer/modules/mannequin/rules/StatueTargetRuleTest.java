package de.raindancer.modules.mannequin.rules;

import de.raindancer.modules.mannequin.model.Mannequin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StatueTargetRuleTest {

    private final StatueTargetRule rule = new StatueTargetRule();

    private Mannequin statue(String id, String world, int x, int y, int z) {
        return Mannequin.statue(id, UUID.randomUUID(), UUID.randomUUID(), "Rain", world, x, y, z, 0f);
    }

    @Test
    void theClosestStatueInReachIsMeant() {
        List<Mannequin> all = List.of(statue("A", "world", 3, 64, 0), statue("B", "world", 1, 64, 0));

        assertThat(rule.nearest(all, "world", 0.5, 64, 0.5, 4)).get().extracting(Mannequin::id).isEqualTo("B");
    }

    @Test
    void outOfReachOrAnotherWorldIsNothing() {
        List<Mannequin> all = List.of(statue("A", "world", 10, 64, 0), statue("B", "nether", 0, 64, 0));

        assertThat(rule.nearest(all, "world", 0.5, 64, 0.5, 4)).isEmpty();
    }

    @Test
    void aTrainingDummyIsNeverMeant() {
        List<Mannequin> all = List.of(Mannequin.freshlyPlaced("D", UUID.randomUUID(), "world", 0, 64, 0));

        assertThat(rule.nearest(all, "world", 0.5, 64, 0.5, 4)).isEmpty();
    }
}
