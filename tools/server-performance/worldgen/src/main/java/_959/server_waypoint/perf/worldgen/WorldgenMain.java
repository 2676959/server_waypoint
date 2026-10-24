package _959.server_waypoint.perf.worldgen;

import java.nio.file.Path;

public final class WorldgenMain {
    private WorldgenMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("generate --recipe FILE --output NEW_DIR | verify --manifest FILE --input DIR");
        }
        if (args[0].equals("generate") && args[1].equals("--recipe") && args[3].equals("--output")) {
            StructureWriter.generate(Path.of(args[2]), Path.of(args[4]));
        } else if (args[0].equals("verify") && args[1].equals("--manifest") && args[3].equals("--input")) {
            FixtureManifest.verify(Path.of(args[2]), Path.of(args[4]));
        } else {
            throw new IllegalArgumentException("Unknown generator operation");
        }
    }
}
