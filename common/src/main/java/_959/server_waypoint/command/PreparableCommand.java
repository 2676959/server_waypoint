package _959.server_waypoint.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/** Resolves and validates native argument values before a scope schedules work or opens storage. */
@FunctionalInterface
interface PreparableCommand<S> extends Command<S> {
    Command<S> prepare(CommandContext<S> context) throws CommandSyntaxException;

    @Override
    default int run(CommandContext<S> context) throws CommandSyntaxException {
        return prepare(context).run(context);
    }

    static <S> Command<S> prepare(Command<S> command, CommandContext<S> context) throws CommandSyntaxException {
        return command instanceof PreparableCommand<S> preparable ? preparable.prepare(context) : command;
    }
}
