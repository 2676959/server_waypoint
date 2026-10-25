package _959.server_waypoint.perf.paper;

import _959.server_waypoint.perf.ProbeCommand;
import _959.server_waypoint.perf.ProbeWriter;
import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.List;

public final class PaperPerformanceProbe extends JavaPlugin {
    private PaperFixtureAdapter fixture;
    private final BotAssertions bots = new BotAssertions();
    private final CommandAssertions commands = new CommandAssertions();
    private ProbeWriter writer;
    private boolean sampling;
    private String phase = "WARMUP";
    private long phaseStarted;
    private final InventoryWorkload workload = new InventoryWorkload();

    @Override public void onEnable() {
        fixture = new PaperFixtureAdapter();
        commands.install(this, this::emit);
        Bukkit.getPluginManager().registerEvents(bots, this);
        Bukkit.getPluginManager().registerEvents(new PaperTickProbe(this, fixture), this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (sampling) {
                workload.tick();
            }
        }, 1, 1);
    }

    boolean sampling() {
        return sampling;
    }

    String phase() {
        return phase;
    }

    void emit(String type, JsonObject value) {
        if (writer != null) {
            writer.write(type, value);
        }
    }

    void addWorkloadSample(JsonObject sample) {
        workload.addSample(sample);
        try {
            sample.addProperty("navigationSessions", InventoryWorkload.navigationSessions());
        } catch (ReflectiveOperationException exception) {
            sample.addProperty("navigationSessions", -1);
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            return true;
        }
        try {
            ProbeCommand request = ProbeCommand.parse(args);
            if (writer == null) {
                if (!request.operation().equals("hello")) {
                    throw new IllegalArgumentException("hello required first");
                }
                writer = new ProbeWriter(request.runId(), request.attemptId(), line -> getLogger().info(line));
            }
            if (!writer.matches(request)) {
                throw new IllegalArgumentException("Run/attempt mismatch");
            }
            JsonObject data = request.data();
            switch (request.operation()) {
                case "hello" -> hello(data);
                case "fixture.place" -> {
                    fixture.place(Path.of(data.get("directory").getAsString()));
                    emit("fixture.placed", fixture.verify());
                }
                case "fixture.verify" -> {
                    fixture.load(Path.of(data.get("directory").getAsString()));
                    emit("fixture.verified", fixture.verify());
                }
                case "fixture.activate" -> {
                    fixture.activate();
                    emit("fixture.activated", fixture.snapshot());
                }
                case "fixture.quiesce" -> {
                    fixture.quiesce();
                    Bukkit.getWorlds().get(0).save();
                    emit("fixture.quiesced", fixture.snapshot());
                }
                case "scenario.start" -> {
                    workload.configure(data);
                    phase = data.has("phase") ? data.get("phase").getAsString() : "WARMUP";
                    phaseStarted = System.nanoTime();
                    sampling = true;
                    emit("scenario.started", fixture.snapshot());
                }
                case "scenario.stop" -> {
                    sampling = false;
                    JsonObject stopped = fixture.snapshot();
                    stopped.addProperty("phaseElapsedSeconds", (System.nanoTime() - phaseStarted) / 1_000_000_000.0);
                    emit("scenario.stopped", stopped);
                }
                case "storage.snapshot" -> emit("storage.snapshot", StorageAssertions.snapshot());
                case "commands.feedback-barrier" -> Bukkit.getGlobalRegionScheduler().execute(this, () -> emit("commands.feedback.completed", new JsonObject()));
                case "commands.restore-tokens" -> emit("commands.restore-tokens", commands.restoreTokens());
                case "commands.tree" -> emit("commands.tree", commands.tree());
                case "commands.execute" -> {
                    JsonObject acknowledgement = commands.execute(data);
                    Bukkit.getScheduler().runTask(this, () -> emit("commands.executed", commands.completed(acknowledgement)));
                }
                case "commands.suggest" -> emit("commands.suggested", commands.suggest(data));
                case "players.malformed" -> { bots.sendMalformed(); emit("players.malformed", new JsonObject()); }
                case "players.snapshot" -> emit("players.snapshot", bots.snapshot(false));
                case "players.reset" -> emit("players.reset", bots.snapshot(true));
                case "snapshot" -> emit("snapshot", fixture.snapshot());
                case "shutdown" -> Bukkit.shutdown();
                default -> throw new IllegalArgumentException("Unsupported probe operation");
            }
        } catch (Exception exception) {
            JsonObject error = new JsonObject();
            error.addProperty("reason", exception.toString());
            emit("error", error);
            getLogger().warning(exception.toString());
        }
        return true;
    }

    private void hello(JsonObject request) throws Exception {
        boolean control = request.has("control") && request.get("control").getAsBoolean();
        Plugin audited = Bukkit.getPluginManager().getPlugin("ServerWaypoint");
        if ((!control && (audited == null || !audited.isEnabled())) || (control && audited != null)) {
            throw new IllegalStateException("Declared control/candidate plugin presence mismatch");
        }
        JsonObject result = new JsonObject();
        result.addProperty("runtime", Bukkit.getVersion());
        result.addProperty("java", System.getProperty("java.runtime.version"));
        result.addProperty("fixtureAdapter", "paper-1.21.11-native-structure");
        result.addProperty("threadModel", "paper-server-thread");
        result.addProperty("tickMetric", "native-processing-duration");
        result.add("capabilities", new Gson().toJsonTree(List.of("fixture", "tick-processing", "zero-player-ticking")));
        result.add("operations", new Gson().toJsonTree(List.of("fixture.place", "fixture.verify", "fixture.activate", "fixture.quiesce", "scenario.start", "scenario.stop", "snapshot", "shutdown")));
        result.addProperty("probeJar", PaperFixtureAdapter.hash(getFile().toPath()));
        if (audited != null) {
            result.addProperty("auditedJar", PaperFixtureAdapter.hash(Path.of(request.get("auditedPath").getAsString())));
            result.addProperty("loadedClassSource", audited.getClass().getProtectionDomain().getCodeSource().getLocation().toString());
            try (var stream = audited.getClass().getResourceAsStream("/" + audited.getClass().getName().replace('.', '/') + ".class")) {
                result.addProperty("loadedMainClassSha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
            }
        } else {
            result.add("auditedJar", JsonNull.INSTANCE);
        }
        emit("hello", result);
    }
}
