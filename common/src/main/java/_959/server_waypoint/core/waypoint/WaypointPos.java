package _959.server_waypoint.core.waypoint;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.Expose;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public record WaypointPos(@Expose int x, @Expose int y, @Expose int z) {
   public String toShortString() { return String.format("%d, %d, %d", x, y, z); }
   public float X() {
      return x + 0.5F;
   }

   public float Z() {
      return z + 0.5F;
   }

   public static class WaypointPosAdapter extends TypeAdapter<WaypointPos> {
       @Override
       public void write(JsonWriter jsonWriter, WaypointPos waypointPos) throws IOException {
           if (waypointPos == null) {
               jsonWriter.nullValue();
               return;
           }

           jsonWriter.jsonValue("[%d, %d, %d]".formatted(waypointPos.x, waypointPos.y, waypointPos.z));
       }

       @Override
       public WaypointPos read(JsonReader jsonReader) throws IOException {
           if (jsonReader.peek() == JsonToken.NULL) {
               jsonReader.nextNull();
               return null;
           }

           int x;
           int y;
           int z;

           jsonReader.beginArray();

           if (jsonReader.hasNext()) {
               x = jsonReader.nextInt();
           } else {
               throw new IOException("Missing x coordinate in WaypointPos array.");
           }

           if (jsonReader.hasNext()) {
               y = jsonReader.nextInt();
           } else {
               throw new IOException("Missing y coordinate in WaypointPos array.");
           }

           if (jsonReader.hasNext()) {
               z = jsonReader.nextInt();
           } else {
               throw new IOException("Missing z coordinate in WaypointPos array.");
           }

           jsonReader.endArray();
           return new WaypointPos(x, y, z);
       }
   }
}
