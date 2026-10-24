package _959.server_waypoint.perf;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.function.Consumer;

public final class ProbeWriter {
    private final Gson gson = new Gson();
    private final String runId;
    private final String attemptId;
    private final Consumer<String> output;
    private long sequence;

    public ProbeWriter(String runId, String attemptId, Consumer<String> output) {
        this.runId = runId;
        this.attemptId = attemptId;
        this.output = output;
    }

    public synchronized void write(String type, JsonObject data) {
        output.accept("SW_PERF " + gson.toJson(new ProbeEvent(1, runId, attemptId, ++sequence, type, data)));
    }

    public boolean matches(ProbeCommand command) {
        return runId.equals(command.runId()) && attemptId.equals(command.attemptId());
    }
}
