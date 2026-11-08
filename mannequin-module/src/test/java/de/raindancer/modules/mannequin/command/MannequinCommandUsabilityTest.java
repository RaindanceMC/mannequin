package de.raindancer.modules.mannequin.command;

import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.mannequin.MannequinServices;
import de.raindancer.modules.mannequin.model.Mannequin;
import de.raindancer.modules.mannequin.store.MannequinRegistry;
import de.raindancer.modules.mannequin.util.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MannequinCommandUsabilityTest {

    private final Messages messages = mock(Messages.class);
    private final MannequinRegistry registry = new MannequinRegistry();
    private final MannequinServices services = mock(MannequinServices.class);
    private final Player player = mock(Player.class);
    private final CommandSourceStack source = mock(CommandSourceStack.class);
    private final MannequinCommand command = new MannequinCommand(() -> services);

    MannequinCommandUsabilityTest() {
        when(services.messages()).thenReturn(messages);
        when(services.registry()).thenReturn(registry);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(source.getSender()).thenReturn(player);
    }

    @Test
    @DisplayName("statue is only offered to somebody who may place one")
    void statueOnlyForThoseWhoMay() {
        assertThat(command.suggest(source, new String[]{""})).doesNotContain("statue");

        when(player.hasPermission(PermissionNodes.STATUE)).thenReturn(true);
        assertThat(command.suggest(source, new String[]{""})).contains("statue");
    }

    @Test
    @DisplayName("every place a board statue can take is offered, not only the first three")
    void allPlacesOffered() {
        assertThat(command.suggest(source, new String[]{"statue", "top", "fishing", ""}))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        assertThat(command.suggest(source, new String[]{"statue", "top", "fishing", "1"}))
                .containsExactly("1", "10");
    }

    @Test
    @DisplayName("a subcommand missing its id shows its own usage")
    void missingIdShowsUsage() {
        command.execute(source, new String[]{"loadout"});

        verify(messages).send(player, "mannequin.missing-id", "usage", "/mannequin loadout <id>");
    }

    @Test
    @DisplayName("the list says how many there are before the entries")
    void listHasAHeader() {
        UUID owner = player.getUniqueId();
        registry.put(Mannequin.freshlyPlaced("MQ1", owner, "world", 0, 64, 0));
        registry.put(Mannequin.freshlyPlaced("MQ2", owner, "world", 4, 64, 4));

        command.execute(source, new String[]{"list"});

        verify(messages).send(player, "mannequin.list.header", "count", 2);
    }
}
