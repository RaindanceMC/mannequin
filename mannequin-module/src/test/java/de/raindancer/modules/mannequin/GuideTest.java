package de.raindancer.modules.mannequin;

import de.raindancer.core.ui.help.HelpLint;
import de.raindancer.modules.api.ModuleCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GuideTest {

    @Test
    @DisplayName("the guide pages read, quote real settings and explain every command")
    void theGuideIsComplete() {
        assertThat(HelpLint.of(Path.of("src/main/resources/rains-help"))
                .module("mannequin")
                .settings(MannequinSettings.class, MannequinSettings.DEFAULTS)
                .otherTopics("basics", "guide")
                .commands(new MannequinModule().commands().stream().map(ModuleCommand::name).toList())
                .problems()).isEmpty();
    }
}
