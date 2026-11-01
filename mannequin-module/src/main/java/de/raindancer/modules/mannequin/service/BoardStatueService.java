package de.raindancer.modules.mannequin.service;

import de.raindancer.core.ui.board.Leaderboard.Standing;
import de.raindancer.modules.mannequin.MannequinSettings;
import de.raindancer.modules.mannequin.model.Mannequin;
import de.raindancer.modules.mannequin.rules.BoardStatueRule;
import de.raindancer.modules.mannequin.store.MannequinRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Keeps statues that stand for a place on a leaderboard showing whoever holds that place now.
 *
 * <p>The board is asked on an async thread, never the server's: a plugin's {@code top} supplier may do
 * as much work as it likes. What comes back is applied in two halves — the stored record (holder,
 * skin source, name) through the registry and the store, which are safe from any thread, and
 * everything on the live entity (looked up on the global thread, since the server refuses that
 * anywhere else; skin, name and description line on the thread owning the entity). A
 * statue whose chunk is not loaded is still updated in the record, so it spawns already right.
 *
 * <p>A statue whose holder did not change is left alone except for its caption, which carries the
 * value ({@code level 25}) and so changes more often than the holder does.
 */
public final class BoardStatueService implements IMannequinService {

    private final MannequinRegistry registry;
    private final BoardStatueRule rule;
    private final Function<String, Optional<String>> title;
    private final BiFunction<String, Integer, List<Standing>> top;
    private final Consumer<Mannequin> save;
    private final Function<String, Optional<LivingEntity>> live;
    private final BiConsumer<org.bukkit.entity.Mannequin, UUID> wearSkinOf;
    private final Function<Mannequin, Component> nameOf;
    private final BiConsumer<Entity, Runnable> onEntityThread;
    private final Consumer<Runnable> async;
    private final Consumer<Runnable> global;

    /** What a pass found for one statue, waiting to be drawn on its live entity. */
    private record Shown(Mannequin statue, boolean holderChanged, Component line) {
    }

    /** The caption each statue shows now, by mannequin id; a spawn reads it instead of asking the board. */
    private final Map<String, Component> captions = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * @param title          a board's title, empty when no such board is registered
     * @param top            the best {@code n} standings of a board, best first; never throws, empty for an unknown board
     * @param wearSkinOf     puts a player's current skin on a live mannequin, or the default skin for {@code null}
     * @param nameOf         the name drawn above a statue as it is now
     * @param onEntityThread runs a task on the thread owning that entity (Folia's region thread)
     * @param async          runs a task off the server thread
     * @param global         runs a task on the global thread, where a live entity is looked up
     */
    public BoardStatueService(MannequinRegistry registry, BoardStatueRule rule,
                              Function<String, Optional<String>> title,
                              BiFunction<String, Integer, List<Standing>> top,
                              Consumer<Mannequin> save, Function<String, Optional<LivingEntity>> live,
                              BiConsumer<org.bukkit.entity.Mannequin, UUID> wearSkinOf,
                              Function<Mannequin, Component> nameOf,
                              BiConsumer<Entity, Runnable> onEntityThread, Consumer<Runnable> async,
                              Consumer<Runnable> global) {
        this.registry = registry;
        this.rule = rule;
        this.title = title;
        this.top = top;
        this.save = save;
        this.live = live;
        this.wearSkinOf = wearSkinOf;
        this.nameOf = nameOf;
        this.onEntityThread = onEntityThread;
        this.async = async;
        this.global = global;
    }

    @Override
    public void settings(MannequinSettings settings) {
        // Nothing configurable.
    }

    /** The line under the name: the last one drawn, or — before the board has answered — just place and title. */
    public Component descriptionOf(Mannequin statue) {
        Component known = captions.get(statue.id());
        if (known != null) {
            return known;
        }
        return Component.text(rule.caption(statue.boardPlace(), title.apply(statue.boardId()).orElse(null),
                statue.boardId(), null).line());
    }

    /** A board statue was just spawned: ask for its place, unless the board is not registered (yet). */
    public void spawned(Mannequin statue) {
        // Not-yet-registered is the usual state at a restart (the board's plugin may load after this
        // one); resolving it now would blank a statue that is about to be right again. The timer
        // decides that, if it is still unknown half a minute on.
        if (statue.isBoardBound() && title.apply(statue.boardId()).isPresent()) {
            async.accept(() -> resolve(List.of(statue.id())));
        }
    }

    /** Asks every board a statue stands for, once, off the server thread. */
    public void refreshAll() {
        List<String> ids = registry.all().stream().filter(Mannequin::isBoardBound).map(Mannequin::id).toList();
        if (!ids.isEmpty()) {
            async.accept(() -> resolve(ids));
        }
    }

    /** One pass; a pass still running when the next is asked for is not doubled up. */
    private void resolve(List<String> ids) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            List<Mannequin> statues = ids.stream().map(registry::get).flatMap(Optional::stream)
                    .filter(Mannequin::isBoardBound).toList();
            captions.keySet().removeIf(id -> registry.get(id).filter(Mannequin::isBoardBound).isEmpty());
            Map<String, Integer> deepest = new LinkedHashMap<>();
            for (Mannequin statue : statues) {
                deepest.merge(statue.boardId(), statue.boardPlace(), Math::max);
            }
            Map<String, List<Standing>> boards = new LinkedHashMap<>();
            deepest.forEach((board, place) -> boards.put(board, safeTop(board, place)));
            List<Shown> found = new java.util.ArrayList<>();
            for (Mannequin statue : statues) {
                List<Standing> standings = boards.get(statue.boardId());
                Standing holder = standings.size() >= statue.boardPlace() ? standings.get(statue.boardPlace() - 1) : null;
                update(statue.id(), holder).ifPresent(found::add);
            }
            if (!found.isEmpty()) {
                global.accept(() -> found.forEach(this::show));
            }
        } finally {
            running.set(false);
        }
    }

    private List<Standing> safeTop(String board, int place) {
        try {
            List<Standing> standings = top.apply(board, place);
            return standings == null ? List.of() : standings;
        } catch (RuntimeException failed) {
            return List.of();
        }
    }

    /** The record half: safe off the server thread. */
    private Optional<Shown> update(String id, Standing holder) {
        Mannequin current = registry.get(id).filter(Mannequin::isBoardBound).orElse(null);
        if (current == null) {
            return Optional.empty();
        }
        BoardStatueRule.Caption caption = rule.caption(current.boardPlace(),
                title.apply(current.boardId()).orElse(null), current.boardId(), holder);
        boolean changed = rule.holderChanged(current.statueOf(), holder);
        Mannequin shown = current;
        if (changed || rule.renamed(current.displayName(), holder)) {
            shown = current.withHolder(rule.holder(holder), caption.name());
            registry.put(shown);
            save.accept(shown);
        }
        Component line = Component.text(caption.line());
        captions.put(id, line);
        return Optional.of(new Shown(shown, changed, line));
    }

    /** The entity half: on the global thread, then on the entity's own. */
    private void show(Shown now) {
        Mannequin statue = now.statue();
        Component line = now.line();
        live.apply(statue.id()).ifPresent(entity -> onEntityThread.accept(entity, () -> {
            if (now.holderChanged() && entity instanceof org.bukkit.entity.Mannequin mannequin) {
                wearSkinOf.accept(mannequin, statue.statueOf());
            }
            Component name = nameOf.apply(statue);
            if (!Objects.equals(entity.customName(), name)) {
                entity.customName(name);
            }
            if (entity instanceof org.bukkit.entity.Mannequin mannequin
                    && !Objects.equals(mannequin.getDescription(), line)) {
                mannequin.setDescription(line);
            }
        }));
    }
}
