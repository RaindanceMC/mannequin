package de.raindancer.modules.mannequin.rules;

import de.raindancer.core.ui.board.Leaderboard.Standing;
import de.raindancer.modules.mannequin.model.Mannequin;

import java.util.Objects;
import java.util.UUID;

/**
 * What a statue standing for a place on a leaderboard says, and when it has to change.
 *
 * <p>Plain words, not markup: the caption is drawn as text (a name is whatever the board's plugin
 * handed over, and must never be parsed), and the holder's own styled nametag is Core's job once there
 * is a holder at all.
 */
public final class BoardStatueRule implements IMannequinRule {

    /** How far down a board a statue may stand for; a podium is three, and ten is a page. */
    public static final int MOST_PLACES = 10;

    /** Whether {@code place} is one a statue can stand for. */
    public static boolean validPlace(int place) {
        return place >= 1 && place <= MOST_PLACES;
    }

    /** What is drawn on a statue: the name above it and the line under the name. */
    public record Caption(String name, String line) {
    }

    /**
     * @param place      1 is the top
     * @param boardTitle the board's title, or {@code null} when the board is unknown — its plugin is not
     *                   installed — in which case the id is named instead
     * @param boardId    only used to name an unknown board
     * @param standing   who holds the place, or {@code null} for nobody
     */
    public Caption caption(int place, String boardTitle, String boardId, Standing standing) {
        String title = boardTitle == null || boardTitle.isBlank() ? boardId : boardTitle;
        String line = "#" + place + " " + title;
        if (standing == null || standing.player() == null) {
            return new Caption(Mannequin.NOBODY, line);
        }
        String value = standing.value();
        if (value != null && !value.isBlank()) {
            line += " · " + value;
        }
        String name = standing.name() == null || standing.name().isBlank() ? "Unknown" : standing.name();
        return new Caption(name, line);
    }

    /** The player a standing is of, {@code null} for nobody. */
    public UUID holder(Standing standing) {
        return standing == null ? null : standing.player();
    }

    /** Whether the statue has to become somebody else (or nobody): the holder is not who it shows. */
    public boolean holderChanged(UUID shown, Standing now) {
        return !Objects.equals(shown, holder(now));
    }

    /** Whether the name drawn on a statue of the same holder is out of date (they renamed). */
    public boolean renamed(String shownName, Standing now) {
        return now != null && now.player() != null && now.name() != null && !now.name().isBlank()
                && !now.name().equals(shownName);
    }

    @Override
    public String describe() {
        return "what a leaderboard-bound statue says, and whether its place has a new holder";
    }
}
