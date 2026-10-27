package de.raindancer.modules.mannequin.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatuePoseTest {

    @Test
    void namesAreReadWhicheverWayTheyAreTyped() {
        assertThat(StatuePose.byName("Sleeping")).contains(StatuePose.SLEEPING);
        assertThat(StatuePose.byName("gliding")).contains(StatuePose.GLIDING);
        assertThat(StatuePose.byName("crouching")).contains(StatuePose.SNEAKING);
        assertThat(StatuePose.byName("SWIMMING")).contains(StatuePose.SWIMMING);
    }

    @Test
    void anUnknownPoseIsNothing() {
        assertThat(StatuePose.byName("dabbing")).isEmpty();
        assertThat(StatuePose.byName(null)).isEmpty();
    }

    @Test
    void everyPoseIsOneTheMannequinEntityKnows() {
        assertThat(StatuePose.GLIDING.bukkit()).isEqualTo(org.bukkit.entity.Pose.FALL_FLYING);
        assertThat(StatuePose.SNEAKING.bukkit()).isEqualTo(org.bukkit.entity.Pose.SNEAKING);
        assertThat(StatuePose.STANDING.bukkit()).isEqualTo(org.bukkit.entity.Pose.STANDING);
    }
}
