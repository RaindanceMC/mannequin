package de.raindancer.modules.mannequin.util;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MannequinPackTest {

    @Test
    void everyFileTheContributionNamesIsInTheJar() throws Exception {
        for (String file : MannequinPack.FILES) {
            try (InputStream in = MannequinPack.class.getResourceAsStream(MannequinPack.ROOT + file)) {
                assertThat(in).as(file).isNotNull();
            }
        }
    }

    @Test
    void theCastRodIsVanillasOwnCastModel() throws Exception {
        String path = "assets/" + MannequinPack.CAST_ROD.getNamespace() + "/items/" + MannequinPack.CAST_ROD.getKey() + ".json";
        assertThat(MannequinPack.FILES).contains(path);
        try (InputStream in = MannequinPack.class.getResourceAsStream(MannequinPack.ROOT + path)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).contains("\"minecraft:item/fishing_rod_cast\"");
        }
    }
}
