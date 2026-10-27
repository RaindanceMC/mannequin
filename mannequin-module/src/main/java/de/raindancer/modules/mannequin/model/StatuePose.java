package de.raindancer.modules.mannequin.model;

import org.bukkit.entity.Pose;

import java.util.Locale;
import java.util.Optional;

/** The poses a mannequin entity can hold — vanilla allows these five and nothing else on it. */
public enum StatuePose {
    STANDING(Pose.STANDING),
    SNEAKING(Pose.SNEAKING),
    SWIMMING(Pose.SWIMMING),
    GLIDING(Pose.FALL_FLYING),
    SLEEPING(Pose.SLEEPING);

    private final Pose bukkit;

    StatuePose(Pose bukkit) {
        this.bukkit = bukkit;
    }

    public Pose bukkit() {
        return bukkit;
    }

    public String typed() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** As typed, case-insensitive; {@code crouching} is sneaking. */
    public static Optional<StatuePose> byName(String typed) {
        if (typed == null) {
            return Optional.empty();
        }
        String name = typed.trim().toUpperCase(Locale.ROOT);
        if (name.equals("CROUCHING")) {
            return Optional.of(SNEAKING);
        }
        for (StatuePose pose : values()) {
            if (pose.name().equals(name)) {
                return Optional.of(pose);
            }
        }
        return Optional.empty();
    }
}
