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
    private final java.util.Map<CommandNode<CommandSourceStack>, Integer> providers = new java.util.IdentityHashMap<>();
    private final JsonArray providerCallbacks = new JsonArray();
    private long droppedProviderCallbacks;
    private long droppedCallbacks;
    private org.bukkit.plugin.Plugin plugin;
    private java.util.function.BiConsumer<String, JsonObject> observer;
    private long commandObservationId;
    private final java.util.Map<java.util.UUID, JsonObject> pending = new java.util.HashMap<>();

    void install(org.bukkit.plugin.Plugin owner, java.util.function.BiConsumer<String, JsonObject> observer) {
        plugin = owner;
        this.observer = observer;
        Bukkit.getMessenger().registerOutgoingPluginChannel(owner, "swperf:command_ack");
        Bukkit.getMessenger().registerOutgoingPluginChannel(owner, "swperf:command_ready");
        Bukkit.getMessenger().registerIncomingPluginChannel(owner, "swperf:command_request", (channel, player, bytes) -> {
            owner();
            if (bytes.length > 4096 || pending.size() >= 256 || pending.containsKey(player.getUniqueId())) {
                throw new IllegalStateException("Invalid or overlapping native command acknowledgement marker");
            }
            JsonObject request = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            String id = request.get("actionId").getAsString();
            String command = request.get("command").getAsString();
            if (id.isEmpty() || id.length() > 128 || command.length() > 256 || command.contains("\n")) {
                throw new IllegalArgumentException("Invalid native command acknowledgement identity");
            }
            request.addProperty("expiresNs", System.nanoTime() + 10_000_000_000L);
            pending.put(player.getUniqueId(), request);
            JsonObject ready = request.deepCopy();
            ready.remove("expiresNs");
            ready.addProperty("stage", "marker-registered");
            ready.addProperty("serverObserved", true);
            player.sendPluginMessage(owner, "swperf:command_ready", ready.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });
        Bukkit.getScheduler().runTaskTimer(owner, () -> pending.entrySet().removeIf(entry ->
                Bukkit.getPlayer(entry.getKey()) == null || System.nanoTime() > entry.getValue().get("expiresNs").getAsLong()), 20, 20);
    }

    private void acknowledge(CommandSender actor, JsonObject event) {
        if (!(actor instanceof org.bukkit.entity.Player player)) {
            return;
        }
        JsonObject request = pending.get(player.getUniqueId());
        if (request == null || !request.get("command").getAsString().equals(event.get("input").getAsString())) {
            return;
        }
        pending.remove(player.getUniqueId());
        JsonObject reply = event.deepCopy();
        reply.addProperty("stage", "command-completed");
        reply.addProperty("actionId", request.get("actionId").getAsString());
        reply.addProperty("command", event.get("input").getAsString());
        player.sendPluginMessage(plugin, "swperf:command_ack", reply.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

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
            JsonObject event = new JsonObject();
            List<String> path = new ArrayList<>();
            for (var parsed : context.getNodes()) {
                var part = parsed.getNode();
                path.add(part instanceof ArgumentCommandNode<?, ?> ? "<" + part.getName() + ">" : part.getName());
            }
            CommandSender actor = context.getSource().getBukkitSender();
            event.addProperty("path", String.join(" ", path));
            event.addProperty("input", context.getInput());
            event.addProperty("actor", actor.getName());
            boolean console = actor instanceof org.bukkit.command.ConsoleCommandSender;
            if (console) {
                event.addProperty("commandObservationId", ++commandObservationId);
                JsonObject begin = event.deepCopy();
                // PaperMessageSender enqueues console delivery here too. These
                // markers surround delivery without replacing the real sender.
                Bukkit.getGlobalRegionScheduler().execute(plugin, () -> observer.accept("command.begin", begin));
            }
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
                event.addProperty("returned", result);
                event.addProperty("durationNs", elapsed);
                event.addProperty("allocatedBytes", before < 0 || after < before ? -1 : after - before);
                event.addProperty("clock", "server-monotonic");
                event.addProperty("allocationScope", "server-thread");
                event.addProperty("serverObserved", true);
                if (error != null) {
                    event.addProperty("exception", error);
                }
                acknowledge(context.getSource().getBukkitSender(), event);
                if (callbacks.size() < 1000) {
                    callbacks.add(event);
                } else {
                    droppedCallbacks++;
                }
                if (console) {
                    JsonObject completed = event.deepCopy();
                    Bukkit.getGlobalRegionScheduler().execute(plugin, () -> observer.accept("command.end", completed));
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

    private void instrumentSuggestions(CommandNode<CommandSourceStack> node) {
        if (!(node instanceof ArgumentCommandNode<CommandSourceStack, ?> argument)
                || argument.getCustomSuggestions() == null || providers.containsKey(node)) {
            return;
        }
        int id = providers.size() + 1;
        var original = argument.getCustomSuggestions();
        com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> observed = (context, builder) -> {
            long started = System.nanoTime();
            try {
                return original.getSuggestions(context, builder).whenComplete((suggestions, error) ->
                        recordProvider(id, builder.getInput(), started, suggestions, error));
            } catch (RuntimeException | com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                recordProvider(id, builder.getInput(), started, null, exception);
                throw exception;
            }
        };
        try {
            // Brigadier's merge API changes only commands. Preserve the native
            // node/argument/requirement graph and wrap its actual provider field.
            var field = ArgumentCommandNode.class.getDeclaredField("customSuggestions");
            field.setAccessible(true);
            field.set(argument, observed);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Native Brigadier suggestion observation unavailable", exception);
        }
        providers.put(node, id);
    }

    private void recordProvider(int id, String input, long started,
            com.mojang.brigadier.suggestion.Suggestions suggestions, Throwable error) {
        JsonObject event = new JsonObject();
        event.addProperty("providerId", id);
        event.addProperty("input", input);
        event.addProperty("serverObserved", true);
        event.addProperty("durationNs", System.nanoTime() - started);
        event.addProperty("clock", "server-monotonic");
        event.addProperty("scope", "native-provider-completion");
        JsonArray values = new JsonArray();
        if (suggestions != null) {
            for (var suggestion : suggestions.getList()) {
                values.add(suggestion.getText());
            }
        }
        event.add("suggestions", values);
        if (error != null) {
            event.addProperty("exception", error.getClass().getName());
        }
        synchronized (providerCallbacks) {
            if (providerCallbacks.size() < 1000) {
                providerCallbacks.add(event);
            } else {
                droppedProviderCallbacks++;
            }
        }
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
        instrumentSuggestions(node);
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
        if (suggestion) {
            value.addProperty("providerId", providers.get(node));
        }
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

    JsonObject restoreTokens() throws ReflectiveOperationException {
        owner();
        var plugin = Bukkit.getPluginManager().getPlugin("ServerWaypoint");
        if (plugin == null) {
            throw new IllegalStateException("Published plugin absent");
        }
        var commandField = plugin.getClass().getDeclaredField("waypointCommand");
        commandField.setAccessible(true);
        Object command = commandField.get(plugin);
        var registryField = command.getClass().getSuperclass().getDeclaredField("restoreRegistry");
        registryField.setAccessible(true);
        Object registry = registryField.get(command);
        JsonArray tokens = new JsonArray();
        synchronized (registry) {
            var entriesField = registry.getClass().getDeclaredField("entries");
            entriesField.setAccessible(true);
            for (Object token : ((java.util.Map<?, ?>) entriesField.get(registry)).keySet()) {
                tokens.add(token.toString());
            }
        }
        JsonObject result = new JsonObject();
        result.add("tokens", tokens);
        return result;
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
        synchronized (providerCallbacks) {
            result.add("providerCallbacks", providerCallbacks.deepCopy());
            result.addProperty("droppedProviderCallbacks", droppedProviderCallbacks);
            providerCallbacks.asList().clear();
            droppedProviderCallbacks = 0;
        }
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
