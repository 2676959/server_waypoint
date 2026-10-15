package _959.server_waypoint.text.chat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Paper ships a newer Adventure than the one common compiles against, and there the constants of
 * ClickEvent.Action are no longer the enum fields compiled code refers to. Production code builds
 * clicks through the ClickEvent factories, which both versions keep.
 */
class AdventureCompatibilityTest {
    private static final String CLICK_ACTION = "net/kyori/adventure/text/event/ClickEvent$Action";

    @Test
    void productionClassesNeverReferToClickEventActions() throws IOException, URISyntaxException {
        Path classes = Path.of(Click.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (Stream<Path> files = Files.walk(classes)) {
            List<String> offenders = files.filter(file -> file.toString().endsWith(".class"))
                    .filter(file -> contains(file, CLICK_ACTION))
                    .map(file -> classes.relativize(file).toString())
                    .sorted()
                    .toList();
            assertEquals(List.of(), offenders);
        }
    }

    private static boolean contains(Path file, String text) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).contains(text);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
