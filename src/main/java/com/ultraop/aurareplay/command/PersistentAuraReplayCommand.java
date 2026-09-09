package com.ultraop.aurareplay.command;

import com.ultraop.aurareplay.core.AuraEngine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/** Command facade that adds durable recording operations without changing the existing command tree. */
public final class PersistentAuraReplayCommand implements CommandExecutor, TabCompleter {
    private final AuraReplayCommand delegate;
    private final AuraEngine engine;

    public PersistentAuraReplayCommand(AuraEngine engine) {
        this.engine = engine;
        this.delegate = new AuraReplayCommand(engine);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (RecordingCommandHandler.handle(engine, sender, args)) return true;
        return delegate.onCommand(sender, command, label, args);
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> persistent = RecordingCommandHandler.complete(args);
        return persistent.isEmpty() ? delegate.onTabComplete(sender, command, alias, args) : persistent;
    }
}
