package de.raindancer.modules.mannequin.command;

import de.raindancer.core.platform.command.MistypedCommand;
import de.raindancer.modules.mannequin.MannequinServices;
import de.raindancer.modules.mannequin.MannequinSettings;
import de.raindancer.modules.mannequin.model.Mannequin;
import de.raindancer.modules.mannequin.model.MannequinKind;
import de.raindancer.modules.mannequin.model.ItemSpec;
import de.raindancer.modules.mannequin.model.StatuePose;
import de.raindancer.modules.mannequin.rules.CreateMannequinRule;
import de.raindancer.modules.mannequin.rules.StatueTargetRule;
import de.raindancer.modules.mannequin.util.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import de.raindancer.core.platform.util.Scheduling;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@code /mannequin create|statue|remove|loadout|skin|stats|list} — everything typing is faster for than
 * clicking through a menu, or that takes an argument a menu cannot ask for (an id).
 *
 * <h2>{@code remove} confirms the same way {@code farmworld-module}'s {@code delete} does</h2>
 * {@code /mannequin remove <id> confirm} — a bare {@code /mannequin remove <id>} says what would
 * happen and stops there. Tab completion deliberately never offers the word {@code confirm}
 * itself, so it has to be typed on purpose rather than tabbed past. The GUI's own remove button
 * already confirms through a page; a command with the same effect and no confirmation at all would
 * be a second, weaker entrance to the same irreversible action.
 */
public final class MannequinCommand implements IMannequinCommand {

    private static final List<String> SUBCOMMANDS =
            List.of("create", "statue", "remove", "loadout", "skin", "stats", "list");

    private final Supplier<MannequinServices> services;
    private final CreateMannequinRule createRule = new CreateMannequinRule();
    private final StatueTargetRule targetRule = new StatueTargetRule();
    private static final double STATUE_REACH = 4;
    private static final String POSE_NAMES = java.util.Arrays.stream(StatuePose.values())
            .map(StatuePose::typed).collect(java.util.stream.Collectors.joining(", "));

    public MannequinCommand(Supplier<MannequinServices> services) {
        this.services = services;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, String @NotNull [] args) {
        MannequinServices live = services.get();
        CommandSender sender = source.getSender();

        if (args.length == 0) {
            // The same door /claim and /home open bare: whichever answer is right for everybody,
            // rather than a guess about which mannequin somebody meant.
            if (sender instanceof Player player) {
                live.screens().list(player);
                return;
            }
            live.messages().send(sender, "mannequin.usage");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> create(live, sender, args);
            case "statue" -> statue(live, sender, args);
            // remove has its own, wider check (mayManage OR the server-wide REMOVE_ANY permission),
            // so it is looked up with withAnyMannequin rather than gated a second, narrower time.
            case "remove" -> withAnyMannequin(live, sender, args, (l, p, m) -> remove(l, p, m, args));
            case "loadout" -> withMannequin(live, sender, args, (l, p, m) -> l.screens().loadout(p, m));
            case "skin" -> withTrainingDummy(live, sender, args, (l, p, m) -> l.screens().skin(p, m));
            case "stats" -> withTrainingDummy(live, sender, args, (l, p, m) -> l.screens().stats(p, m));
            case "list" -> list(live, sender);
            default -> {
                if (!MistypedCommand.subcommand(sender, "mannequin", args, 0, suggest(source, new String[]{""}))) {
                    live.messages().send(sender, "mannequin.usage");
                }
            }
        }
    }

    /**
     * {@code /mannequin create [kind]} — {@code kind} is optional and case-insensitive, defaulting
     * to {@code player} when omitted. An unrecognised kind is reported rather than silently
     * defaulted, the same "a refusal says something" rule every screen in this module already
     * follows for a button — a typo here should not quietly hand somebody a different mob than the
     * one they typed.
     */
    private void create(MannequinServices live, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            live.messages().send(sender, "mannequin.only-a-player");
            return;
        }
        MannequinSettings settings = live.config();
        if (!createRule.mayCreate(settings.openCreation(), player)) {
            live.messages().send(sender, "mannequin.create.no-permission");
            return;
        }
        MannequinKind kind = MannequinKind.PLAYER;
        if (args.length >= 2) {
            Optional<MannequinKind> requested = MannequinKind.byName(args[1]);
            if (requested.isEmpty()) {
                live.messages().send(sender, "mannequin.create.unknown-kind", "kind", args[1]);
                return;
            }
            kind = requested.get();
        }
        // The full location, not block-snapped: MannequinService#create reads its yaw before
        // deriving the block coordinates, so the dummy faces the way the player was looking.
        Optional<Mannequin> created = live.purchases().buy(player, kind);
        if (created.isEmpty()) {
            return;
        }
        live.messages().send(sender, "mannequin.create.done", "id", created.get().id());
    }

    /**
     * {@code /mannequin statue <player>} — somebody this server knows, or any Minecraft name, which
     * is then asked of Mojang. Free and outside the per-player limits: it is an admin's decoration.
     */
    private void statue(MannequinServices live, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            live.messages().send(sender, "mannequin.only-a-player");
            return;
        }
        if (!player.hasPermission(PermissionNodes.STATUE)) {
            live.messages().send(sender, "mannequin.statue.no-permission");
            return;
        }
        if (args.length < 2 || args[1].isBlank()) {
            live.messages().send(sender, "mannequin.statue.usage");
            return;
        }
        // These words win over a player of the same name; that player's statue is placed by
        // somebody else typing it, or by any other spelling of the command there is none of.
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "pose" -> {
                statuePose(live, player, args);
                return;
            }
            case "hold" -> {
                withStatue(live, player, args, 2, statue -> statueHold(live, player, statue));
                return;
            }
            case "remove" -> {
                withStatue(live, player, args, 2, statue -> {
                    live.mannequins().remove(statue.id());
                    live.messages().send(player, "mannequin.statue.removed", "name", statue.displayName(),
                            "id", statue.id());
                });
                return;
            }
            default -> {
            }
        }
        String typed = args[1].trim();
        // Captured now: where they stood when they typed it, not wherever they walked during a lookup.
        org.bukkit.Location anchor = player.getLocation();
        org.bukkit.OfflinePlayer known = Bukkit.getPlayerExact(typed);
        if (known == null) {
            known = Bukkit.getOfflinePlayerIfCached(typed);
        }
        if (known != null && known.getName() != null) {
            placeStatue(live, player, known.getUniqueId(), known.getName(), anchor);
            return;
        }
        live.messages().send(sender, "mannequin.statue.looking-up", "name", typed);
        Bukkit.createProfile(typed).update().whenCompleteAsync((found, error) ->
                Scheduling.entity(live.plugin(), player, () -> {
                    if (error != null || found == null || found.getId() == null || found.getName() == null) {
                        live.messages().send(player, "mannequin.statue.not-found", "name", typed);
                        return;
                    }
                    placeStatue(live, player, found.getId(), found.getName(), anchor);
                }));
    }

    /** {@code /mannequin statue pose <pose> [id]} */
    private void statuePose(MannequinServices live, Player player, String[] args) {
        Optional<StatuePose> pose = args.length >= 3 ? StatuePose.byName(args[2]) : Optional.empty();
        if (pose.isEmpty()) {
            live.messages().send(player, "mannequin.statue.pose.usage", "poses", POSE_NAMES);
            return;
        }
        withStatue(live, player, args, 3, statue -> {
            Mannequin posed = statue.withPose(pose.get());
            live.mannequins().save(posed);
            live.mannequins().spawn(posed);
            live.messages().send(player, "mannequin.statue.pose.done", "name", statue.displayName(),
                    "pose", pose.get().typed());
        });
    }

    /** {@code /mannequin statue hold [id]}: what is in your hands, exactly, an empty hand emptying its hand. */
    private void statueHold(MannequinServices live, Player player, Mannequin statue) {
        org.bukkit.inventory.PlayerInventory hands = player.getInventory();
        Mannequin holding = statue
                .withSlot(org.bukkit.inventory.EquipmentSlot.HAND, held(hands.getItemInMainHand()))
                .withSlot(org.bukkit.inventory.EquipmentSlot.OFF_HAND, held(hands.getItemInOffHand()));
        live.mannequins().save(holding);
        live.mannequins().spawn(holding);
        live.messages().send(player, "mannequin.statue.hold.done", "name", statue.displayName());
    }

    private static ItemSpec held(org.bukkit.inventory.ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        org.bukkit.inventory.ItemStack one = stack.clone();
        one.setAmount(1);
        return ItemSpec.exactly(one);
    }

    /**
     * The statue named by {@code args[idAt]}, or — none named — the one looked at, or the closest
     * within {@link #STATUE_REACH} blocks.
     */
    private void withStatue(MannequinServices live, Player player, String[] args, int idAt,
                            java.util.function.Consumer<Mannequin> action) {
        Optional<Mannequin> statue;
        if (args.length > idAt) {
            statue = live.registry().get(args[idAt]);
            if (statue.isEmpty()) {
                live.messages().send(player, "mannequin.unknown-id", "id", args[idAt]);
                return;
            }
            if (!statue.get().isStatue()) {
                live.messages().send(player, "mannequin.statue.not-a-statue", "id", args[idAt]);
                return;
            }
        } else {
            org.bukkit.entity.Entity looked = player.getTargetEntity(8, false);
            statue = Optional.ofNullable(looked)
                    .flatMap(entity -> live.registry().idFor(entity.getUniqueId()))
                    .flatMap(live.registry()::get)
                    .filter(Mannequin::isStatue);
            if (statue.isEmpty()) {
                org.bukkit.Location at = player.getLocation();
                statue = targetRule.nearest(live.registry().all(), at.getWorld().getName(),
                        at.getX(), at.getY(), at.getZ(), STATUE_REACH);
            }
            if (statue.isEmpty()) {
                live.messages().send(player, "mannequin.statue.none-here");
                return;
            }
        }
        action.accept(statue.get());
    }

    private void placeStatue(MannequinServices live, Player player, UUID depicted, String name,
                             org.bukkit.Location anchor) {
        Mannequin statue = live.mannequins().createStatue(player.getUniqueId(), depicted, name, anchor);
        live.messages().send(player, "mannequin.statue.done", "name", name, "id", statue.id());
    }

    private void remove(MannequinServices live, Player player, Mannequin mannequin, String[] args) {
        // Trusted, not only owned: sharing a mannequin means being able to actually retire it, the
        // same as a claim's trusted members can give up the claim they were let into. REMOVE_ANY is
        // the server-wide escape hatch on top of that, for an admin cleaning up after somebody who
        // has left.
        boolean allowed = mannequin.mayManage(player.getUniqueId());
        if (!allowed && !player.hasPermission(PermissionNodes.REMOVE_ANY)) {
            live.messages().send(player, "mannequin.remove.no-permission");
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            live.messages().send(player, "mannequin.remove.are-you-sure", "id", mannequin.id());
            return;
        }
        live.purchases().retire(mannequin);
        live.messages().send(player, "mannequin.remove.done", "id", mannequin.id());
    }

    private void list(MannequinServices live, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            live.messages().send(sender, "mannequin.only-a-player");
            return;
        }
        List<Mannequin> owned = live.registry().accessibleBy(player.getUniqueId());
        if (owned.isEmpty()) {
            live.messages().send(sender, "mannequin.list.empty");
            return;
        }
        for (Mannequin mannequin : owned) {
            live.messages().send(sender, "mannequin.list.entry", "id", mannequin.id(),
                    "name", mannequin.displayName(), "world", mannequin.world());
        }
    }

    private interface WithMannequin {
        void run(MannequinServices services, Player player, Mannequin mannequin);
    }

    /** Loadout, skin, stats — reachable by whoever owns the mannequin or has been trusted with it. */
    private void withMannequin(MannequinServices live, CommandSender sender, String[] args,
                               WithMannequin action) {
        withAnyMannequin(live, sender, args, (l, p, m) -> {
            if (!m.mayManage(p.getUniqueId())) {
                l.messages().send(p, "mannequin.no-permission");
                return;
            }
            action.run(l, p, m);
        });
    }

    /** Skin and stats: a statue has neither a skin of its own nor a training tally. */
    private void withTrainingDummy(MannequinServices live, CommandSender sender, String[] args,
                                   WithMannequin action) {
        withMannequin(live, sender, args, (l, p, m) -> {
            if (m.isStatue()) {
                l.messages().send(p, "mannequin.statue.not-for-statues");
                return;
            }
            action.run(l, p, m);
        });
    }

    /** Looks the id up without a permission gate — {@code remove} has its own, wider one. */
    private void withAnyMannequin(MannequinServices live, CommandSender sender, String[] args,
                                  WithMannequin action) {
        if (!(sender instanceof Player player)) {
            live.messages().send(sender, "mannequin.only-a-player");
            return;
        }
        if (args.length < 2) {
            live.messages().send(sender, "mannequin.missing-id");
            return;
        }
        Optional<Mannequin> found = live.registry().get(args[1]);
        if (found.isEmpty()) {
            live.messages().send(sender, "mannequin.unknown-id", "id", args[1]);
            return;
        }
        action.run(live, player, found.get());
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source,
                                               String @NotNull [] args) {
        if (args.length <= 1) {
            String typed = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            return SUBCOMMANDS.stream().filter(name -> name.startsWith(typed)).toList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sub.equals("create")) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            return java.util.Arrays.stream(de.raindancer.modules.mannequin.model.MannequinKind.values())
                    .map(kind -> kind.name().toLowerCase(Locale.ROOT))
                    .filter(name -> name.startsWith(typed))
                    .toList();
        }
        if (args.length == 2 && sub.equals("statue")) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            return java.util.stream.Stream.concat(java.util.stream.Stream.of("pose", "hold", "remove"),
                            Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(typed))
                    .toList();
        }
        if (args.length == 3 && sub.equals("statue") && args[1].equalsIgnoreCase("pose")) {
            String typed = args[2].toLowerCase(Locale.ROOT);
            return java.util.Arrays.stream(StatuePose.values()).map(StatuePose::typed)
                    .filter(name -> name.startsWith(typed)).toList();
        }
        boolean statueId = sub.equals("statue") && ((args.length == 3 && List.of("hold", "remove")
                .contains(args[1].toLowerCase(Locale.ROOT))) || (args.length == 4 && args[1].equalsIgnoreCase("pose")));
        if (statueId) {
            String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
            return services.get().registry().all().stream().filter(Mannequin::isStatue).map(Mannequin::id)
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
        }
        if (args.length == 2 && List.of("remove", "loadout", "skin", "stats").contains(sub)
                && source.getSender() instanceof Player player) {
            MannequinServices live = services.get();
            return live.registry().accessibleBy(player.getUniqueId()).stream()
                    .map(Mannequin::id)
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    @Override
    public @NotNull String permission() {
        return PermissionNodes.USE;
    }

    @Override
    public String describe() {
        return "create, remove, dress, skin and inspect training dummies, and place statues";
    }
}
