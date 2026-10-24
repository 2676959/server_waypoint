package _959.server_waypoint.perf;

import com.google.gson.JsonObject;

public record ProbeEvent(int schemaVersion, String runId, String attemptId, long sequence, String type, JsonObject data) {}
