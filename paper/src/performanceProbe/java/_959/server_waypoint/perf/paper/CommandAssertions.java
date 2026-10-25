package _959.server_waypoint.perf.paper;

import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/** Native registered command tree and owner-thread dispatcher/suggestion observations. */
final class CommandAssertions {
    private CommandDispatcher<CommandSourceStack> dispatcher;
    private final java.util.Set<CommandNode<CommandSourceStack>> instrumented = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final JsonArray callbacks = new JsonArray();
    private long droppedCallbacks;

    private long allocated() {
        var bean = ManagementFactory.getThreadMXBean();
        return bean instanceof com.sun.management.ThreadMXBean supported && supported.isThreadAllocatedMemorySupported() && supported.isThreadAllocatedMemoryEnabled()
                ? supported.getThreadAllocatedBytes(Thread.currentThread().getId()) : -1;
    }

    private void instrument(CommandNode<CommandSourceStack> parent, CommandNode<CommandSourceStack> node) {
        if (node.getCommand() == null || !instrumented.add(node)) {
            return;
        }
        Command<CommandSourceStack> original = node.getCommand();
        Command<CommandSourceStack> observed = context -> {
            long before = allocated();
            long started = System.nanoTime();
            int result = 0;
            String error = null;
            try {
                result = original.run(context);
                return result;
            } catch (RuntimeException | com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                error = exception.getClass().getName();
                throw exception;
            } finally {
                long elapsed = System.nanoTime() - started;
                long after = allocated();
                JsonObject event = new JsonObject();
                List<String> path = new ArrayList<>();
                for (var parsed : context.getNodes()) {
                    var part = parsed.getNode();
                    path.add(part instanceof ArgumentCommandNode<?, ?> ? "<" + part.getName() + ">" : part.getName());
                }
                event.addProperty("path", String.join(" ", path));
                event.addProperty("input", context.getInput());
                event.addProperty("actor", context.getSource().getBukkitSender().getName());
                event.addProperty("returned", result);
                event.addProperty("durationNs", elapsed);
                event.addProperty("allocatedBytes", before < 0 || after < before ? -1 : after - before);
                event.addProperty("clock", "server-monotonic");
                event.addProperty("allocationScope", "server-thread");
                event.addProperty("serverObserved", true);
                if (error != null) {
                    event.addProperty("exception", error);
                }
                if (callbacks.size() < 1000) {
                    callbacks.add(event);
                } else {
                    droppedCallbacks++;
                }
            }
        };
        CommandNode<CommandSourceStack> replacement;
        if (node instanceof LiteralCommandNode<CommandSourceStack> literal) {
            replacement = new LiteralCommandNode<>(literal.getLiteral(), observed, node.getRequirement(), node.getRedirect(), node.getRedirectModifier(), node.isFork());
        } else if (node instanceof ArgumentCommandNode<CommandSourceStack, ?> argument) {
            replacement = argumentCopy(argument, observed);
        } else {
            throw new IllegalStateException("Unsupported registered command node: " + node.getClass());
        }
        // Brigadier's documented merge replaces only the existing command callback;
        // requirements, redirects, argument types, suggestions and children stay intact.
        parent.addChild(replacement);
    }

    private <T> CommandNode<CommandSourceStack> argumentCopy(ArgumentCommandNode<CommandSourceStack, T> argument, Command<CommandSourceStack> observed) {
        return new ArgumentCommandNode<>(argument.getName(), argument.getType(), observed, argument.getRequirement(), argument.getRedirect(), argument.getRedirectModifier(), argument.isFork(), argument.getCustomSuggestions());
    }

    JsonObject tree() {
        owner();
        // Paper's public lifecycle dispatcher maps/copies nodes; instrument the
        // native dispatcher actually used by CraftServer.dispatchCommand.
        dispatcher = ((CraftServer) Bukkit.getServer()).getServer().getCommands().getDispatcher();
        JsonArray nodes = new JsonArray();
        for (String name : List.of("wp", "sw-cross-server-keygen")) {
            CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild(name);
            if (root == null) {
                throw new IllegalStateException("Missing published command root: " + name);
            }
            walk(dispatcher.getRoot(), root, new ArrayList<>(), nodes, new ArrayList<>());
        }
        JsonObject result = new JsonObject();
        result.add("nodes", nodes);
        result.addProperty("origin", "native-minecraft-command-dispatcher");
        return result;
    }

    private void walk(CommandNode<CommandSourceStack> parent, CommandNode<CommandSourceStack> node, List<String> prefix, JsonArray nodes, List<JsonObject> arguments) {
        if (prefix.size() > 64 || nodes.size() > 100000) {
            throw new IllegalStateException("Native command tree exceeds bounded inventory");
        }
        instrument(parent, node);
        List<String> path = new ArrayList<>(prefix);
        List<JsonObject> nextArguments = new ArrayList<>(arguments);
        boolean suggestion = false;
        if (node instanceof ArgumentCommandNode<?, ?> argument) {
            path.add("<" + node.getName() + ">");
            JsonObject descriptor = new JsonObject();
            descriptor.addProperty("name", node.getName());
            descriptor.addProperty("type", argument.getType().getClass().getName());
            descriptor.add("examples", new Gson().toJsonTree(argument.getType().getExamples()));
            nextArguments.add(descriptor);
            suggestion = argument.getCustomSuggestions() != null;
        } else {
            path.add(node.getName());
        }
        JsonObject value = new JsonObject();
        value.addProperty("path", String.join(" ", path));
        value.addProperty("executable", node.getCommand() != null);
        value.addProperty("suggestions", suggestion);
        value.add("arguments", new Gson().toJsonTree(nextArguments));
        nodes.add(value);
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            walk(node, child, path, nodes, nextArguments);
        }
    }

    JsonObject execute(JsonObject request) {
        owner();
        JsonArray commands = request.getAsJsonArray("commands");
        if (commands == null || commands.size() > 1000) {
            throw new IllegalArgumentException("Bounded native command batch required");
        }
        CommandSender actor = actor(request);
        JsonArray results = new JsonArray();
        var bean = ManagementFactory.getThreadMXBean();
        com.sun.management.ThreadMXBean allocations = bean instanceof com.sun.management.ThreadMXBean supported && supported.isThreadAllocatedMemorySupported() && supported.isThreadAllocatedMemoryEnabled() ? supported : null;
        for (var element : commands) {
            String command = element.getAsString();
            if (command.length() > 1024 || command.contains("\n")) {
                throw new IllegalArgumentException("Bounded single-line command required");
            }
            long before = allocations == null ? -1 : allocations.getThreadAllocatedBytes(Thread.currentThread().getId());
            long start = System.nanoTime();
            boolean registered = Bukkit.dispatchCommand(actor, command);
            long elapsed = System.nanoTime() - start;
            long after = allocations == null ? -1 : allocations.getThreadAllocatedBytes(Thread.currentThread().getId());
            JsonObject result = new JsonObject();
            result.addProperty("command", command);
            result.addProperty("actor", actor.getName());
            result.addProperty("registered", registered);
            result.addProperty("dispatchDurationNs", elapsed);
            result.addProperty("allocatedBytes", before < 0 || after < before ? -1 : after - before);
            result.addProperty("allocationScope", "server-thread");
            result.addProperty("clock", "server-monotonic");
            result.addProperty("trigger", "native-command-dispatch");
            results.add(result);
        }
        JsonObject result = new JsonObject();
        result.add("executions", results);
        result.addProperty("completion", "queued-native-dispatch");
        return result;
    }

    JsonObject completed(JsonObject acknowledgement) {
        owner();
        acknowledgement.add("callbacks", callbacks.deepCopy());
        acknowledgement.addProperty("droppedCallbacks", droppedCallbacks);
        acknowledgement.addProperty("completion", "next-owner-tick-after-native-command-queue");
        callbacks.asList().clear();
        droppedCallbacks = 0;
        return acknowledgement;
    }

    JsonObject suggest(JsonObject request) {
        owner();
        String input = request.get("input").getAsString();
        if (input.length() > 1024 || input.contains("\n")) {
            throw new IllegalArgumentException("Bounded suggestion input required");
        }
        long started = System.nanoTime();
        List<String> suggestions = Bukkit.getServer().getCommandMap().tabComplete(actor(request), input);
        JsonObject result = new JsonObject();
        result.addProperty("input", input);
        result.addProperty("durationNs", System.nanoTime() - started);
        result.addProperty("clock", "server-monotonic");
        result.add("suggestions", new Gson().toJsonTree(suggestions == null ? List.of() : suggestions));
        return result;
    }

    private CommandSender actor(JsonObject request) {
        String name = request.has("actor") ? request.get("actor").getAsString() : "console";
        CommandSender actor = name.equals("console") ? Bukkit.getConsoleSender() : Bukkit.getPlayerExact(name);
        if (actor == null) {
            throw new IllegalArgumentException("Native actor is not connected: " + name);
        }
        return actor;
    }

    private void owner() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Paper command observation requires server owner thread");
        }
    }
}
