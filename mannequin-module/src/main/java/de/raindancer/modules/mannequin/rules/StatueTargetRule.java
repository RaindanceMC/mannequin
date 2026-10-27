package de.raindancer.modules.mannequin.rules;

import de.raindancer.modules.mannequin.model.Mannequin;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Which statue somebody means when they name none: the closest one within reach of where they stand. */
public final class StatueTargetRule implements IMannequinRule {

    public Optional<Mannequin> nearest(List<Mannequin> all, String world, double x, double y, double z, double reach) {
        return all.stream()
                .filter(Mannequin::isStatue)
                .filter(statue -> statue.world().equals(world))
                .filter(statue -> distanceSquared(statue, x, y, z) <= reach * reach)
                .min(Comparator.comparingDouble(statue -> distanceSquared(statue, x, y, z)));
    }

    private static double distanceSquared(Mannequin statue, double x, double y, double z) {
        double dx = statue.x() + 0.5 - x;
        double dy = statue.y() - y;
        double dz = statue.z() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public String describe() {
        return "which statue is meant when none is named: the closest within reach";
    }
}
