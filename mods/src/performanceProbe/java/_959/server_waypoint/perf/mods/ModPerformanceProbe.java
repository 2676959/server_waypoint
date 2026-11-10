package _959.server_waypoint.perf.mods;

import _959.server_waypoint.perf.ProbeCommand;
import _959.server_waypoint.perf.ProbeWriter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public final class ModPerformanceProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("swperf");
    private final String loader;
    private final Class<?> owner;
    private final ModFixtureAdapter fixture = new ModFixtureAdapter();
    private final ModTickProbe ticks = new ModTickProbe();
    private ProbeWriter writer;
    private boolean sampling;
    private String phase = "WARMUP";
    private long phaseStarted;

    public ModPerformanceProbe(String loader, Class<?> owner) {
        this.loader = loader;
        this.owner = owner;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("swperf")
                .requires(source -> source.getEntity() == null)
                .then(Commands.argument("run", StringArgumentType.word())
                        .then(Commands.argument("attempt", StringArgumentType.word())
                                .then(Commands.argument("operation", StringArgumentType.word())
                                        .then(Commands.argument("data", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    handle(context.getSource().getServer(), new String[]{
                                                            StringArgumentType.getString(context, "run"),
                                                            StringArgumentType.getString(context, "attempt"),
                                                            StringArgumentType.getString(context, "operation"),
                                                            StringArgumentType.getString(context, "data")
                                                    });
                                                    return 1;
                                                }))))));
    }

    public void tickStart(MinecraftServer server) {
        ticks.start(server);
    }

    public void tickEnd(MinecraftServer server) {
        JsonObject sample = ticks.end(server, fixture, phase);
        if (sample != null && sampling) {
            emit("sample", sample);
        }
    }

    private void handle(MinecraftServer server, String[] args) {
        try {
            ProbeCommand request = ProbeCommand.parse(args);
            if (writer == null) {
                if (!request.operation().equals("hello")) {
                    throw new IllegalArgumentException("hello required first");
                }
                writer = new ProbeWriter(request.runId(), request.attemptId(), LOGGER::info);
            }
            if (!writer.matches(request)) {
                throw new IllegalArgumentException("Run/attempt mismatch");
            }
            JsonObject data = request.data();
            switch (request.operation()) {
                case "hello" -> hello(server, data);
                case "fixture.place" -> {
                    fixture.place(server, Path.of(data.get("directory").getAsString()));
                    emit("fixture.placed", fixture.snapshot(server));
                }
                case "fixture.verify" -> {
                    fixture.load(Path.of(data.get("directory").getAsString()));
                    emit("fixture.verified", fixture.snapshot(server));
                }
                case "fixture.activate" -> {
                    fixture.activate();
                    emit("fixture.activated", fixture.snapshot(server));
                }
                case "fixture.quiesce" -> {
                    server.saveEverything(false, true, true);
                    emit("fixture.quiesced", fixture.snapshot(server));
                }
                case "scenario.start" -> {
                    phase = data.has("phase") ? data.get("phase").getAsString() : "WARMUP";
                    phaseStarted = System.nanoTime();
                    sampling = true;
                    emit("scenario.started", fixture.snapshot(server));
                }
                case "scenario.stop" -> {
                    sampling = false;
                    fixture.restore(server);
                    JsonObject stopped = fixture.snapshot(server);
                    stopped.addProperty("phaseElapsedSeconds", (System.nanoTime() - phaseStarted) / 1_000_000_000.0);
                    emit("scenario.stopped", stopped);
                }
                case "snapshot" -> emit("snapshot", fixture.snapshot(server));
                case "shutdown" -> server.halt(false);
                default -> throw new IllegalArgumentException("Unsupported probe operation");
            }
        } catch (Exception exception) {
            JsonObject error = new JsonObject();
            error.addProperty("reason", exception.toString());
            emit("error", error);
            LOGGER.warn(exception.toString());
        }
    }

    private void hello(MinecraftServer server, JsonObject request) throws Exception {
        boolean control = request.has("control") && request.get("control").getAsBoolean();
        JsonObject channel = ProductionChannel.inspect(loader);
        boolean present = channel.get("present").getAsBoolean();
        if (control == present) {
            throw new IllegalStateException("Declared control/candidate mod presence mismatch");
        }
        JsonObject result = new JsonObject();
        result.addProperty("runtime", SharedRuntime.describe(server));
        result.addProperty("java", System.getProperty("java.runtime.version"));
        result.addProperty("fixtureAdapter", loader + "-1.21.11-native-component");
        result.addProperty("threadModel", "server-thread");
        result.addProperty("tickMetric", "loader-pre-post");
        result.add("channel", channel);
        List<String> capabilities = new java.util.ArrayList<>(List.of("fixture", "tick-processing", "zero-player-ticking"));
        if (channel.get("channelRegistered").getAsBoolean() && !"fabric".equals(loader)) {
            capabilities.add("channel-envelope");
        }
        result.add("capabilities", new Gson().toJsonTree(capabilities));
        result.add("operations", new Gson().toJsonTree(List.of(
                "fixture.place", "fixture.verify", "fixture.activate", "fixture.quiesce",
                "scenario.start", "scenario.stop", "snapshot", "shutdown")));
        result.addProperty("probeJar", ModFixtureAdapter.hash(codeSource()));
        if (present) {
            result.addProperty("auditedJar", ModFixtureAdapter.hash(Path.of(request.get("auditedPath").getAsString())));
            result.addProperty("loadedClassSource", channel.get("loadedClassSource").getAsString());
        } else {
            result.add("auditedJar", com.google.gson.JsonNull.INSTANCE);
        }
        emit("hello", result);
    }

    private Path codeSource() throws Exception {
        return Path.of(owner.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private void emit(String type, JsonObject value) {
        if (writer != null) {
            writer.write(type, value);
        }
    }
}
