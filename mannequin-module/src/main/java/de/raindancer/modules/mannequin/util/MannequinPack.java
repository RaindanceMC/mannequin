package de.raindancer.modules.mannequin.util;

import de.raindancer.core.RainsCore;
import de.raindancer.core.content.pack.JarPack;
import de.raindancer.core.platform.log.Log;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * A statue's fishing rod, always cast. Vanilla only draws the cast rod for a player with a bobber
 * out, which a mannequin never is, so the rod gets an item model of its own that is nothing but
 * vanilla's {@code item/fishing_rod_cast}. Somebody not wearing the server pack sees a plain rod.
 */
public final class MannequinPack {

    public static final NamespacedKey CAST_ROD = new NamespacedKey("rainsmannequins", "fishing_rod_cast");
    public static final String ROOT = "/de/raindancer/modules/mannequin/pack/";
    public static final List<String> FILES = List.of(
            "pack.mcmeta",
            "assets/rainsmannequins/items/fishing_rod_cast.json");
    private static final String OWNER = "Mannequins";
    private static final String NAME = "items";

    private MannequinPack() {
    }

    /** A fishing rod with no model of its own comes back drawn cast; anything else as it was. */
    public static ItemStack castIfRod(ItemStack stack) {
        if (stack == null || stack.getType() != Material.FISHING_ROD) {
            return stack;
        }
        ItemStack cast = stack.clone();
        cast.editMeta(meta -> {
            if (!meta.hasItemModel()) {
                meta.setItemModel(CAST_ROD);
            }
        });
        return cast;
    }

    public static void contribute(Plugin plugin, RainsCore core) {
        try {
            JarPack.offer(plugin, core.resourcePacks(), MannequinPack.class, ROOT, FILES, OWNER, NAME,
                    "A statue's fishing rod, cast");
        } catch (IOException failure) {
            Log.of("mannequin").warn("The cast fishing rod could not be written ({}); statues hold a plain rod.",
                    failure.getMessage());
        }
    }

    public static void withdraw(RainsCore core) {
        core.resourcePacks().withdraw(OWNER.toLowerCase(Locale.ROOT) + ":" + NAME);
    }
}
