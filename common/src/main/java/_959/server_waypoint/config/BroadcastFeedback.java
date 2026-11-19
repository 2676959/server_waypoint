package _959.server_waypoint.config;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

@JsonAdapter(value = BroadcastFeedback.Adapter.class, nullSafe = false)
public enum BroadcastFeedback {
    ALL("all"),
    OPS("ops"),
    OFF("off");

    private final String id;

    BroadcastFeedback(String id) {
        this.id = id;
    }

    static final class Adapter extends TypeAdapter<BroadcastFeedback> {
        @Override
        public void write(JsonWriter out, BroadcastFeedback value) throws IOException {
            out.value(value.id);
        }

        @Override
        public BroadcastFeedback read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.STRING) {
                String id = in.nextString();
                for (BroadcastFeedback mode : values()) {
                    if (mode.id.equals(id)) {
                        return mode;
                    }
                }
            }
            throw new JsonParseException("Features.broadcastFeedback must be one of all, ops or off");
        }
    }
}
