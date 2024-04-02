package fr.maxlego08.essentials.api;

import fr.maxlego08.essentials.api.commands.CommandCooldown;

import java.util.List;
import java.util.Optional;

public interface Configuration extends ConfigurationFile {

    boolean isEnableDebug();
    boolean isEnableCooldownBypass();

    List<CommandCooldown> getCommandCooldown();

    Optional<Integer> getCooldown(String command);

}