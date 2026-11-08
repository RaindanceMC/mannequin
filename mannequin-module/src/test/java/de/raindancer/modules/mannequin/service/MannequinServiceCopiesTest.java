package de.raindancer.modules.mannequin.service;

import de.raindancer.modules.mannequin.MannequinSettings;
import de.raindancer.modules.mannequin.model.Mannequin;
import de.raindancer.modules.mannequin.rules.DurabilityRule;
import de.raindancer.modules.mannequin.store.MannequinRegistry;
import de.raindancer.modules.mannequin.store.MannequinStore;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MannequinServiceCopiesTest {

    @TempDir
    Path folder;

    private static final NamespacedKey KEY = new NamespacedKey("test", "mannequin-id");

    private static LivingEntity tagged(String id) {
        LivingEntity entity = mock(LivingEntity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.get(KEY, PersistentDataType.STRING)).thenReturn(id);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);
        return entity;
    }

    @Test
    @DisplayName("a leftover copy is found in the anchor's chunks without scanning every entity of the world")
    void scansChunksNotTheWholeWorld() {
        MannequinSettings settings = MannequinSettings.DEFAULTS;
        MannequinService service = new MannequinService(null, null, new MannequinRegistry(),
                new MannequinStore(folder), new MannequinEquipService(new DurabilityRule(), settings),
                (delay, task) -> { }, settings);
        Mannequin mannequin = Mannequin.freshlyPlaced("MQ1", UUID.randomUUID(), "world", 20, 64, 20);

        LivingEntity mine = tagged("MQ1");
        LivingEntity other = tagged("MQ2");
        Chunk chunk = mock(Chunk.class);
        when(chunk.getEntities()).thenReturn(new org.bukkit.entity.Entity[]{mine, other});
        World world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        when(world.isChunkLoaded(1, 1)).thenReturn(true);
        when(world.getChunkAt(1, 1)).thenReturn(chunk);

        service.removeCopiesAround(world, mannequin, KEY);

        verify(mine).remove();
        verify(other, never()).remove();
        verify(world, never()).getEntities();
        verify(world, never()).getChunkAt(0, 0);
    }
}
