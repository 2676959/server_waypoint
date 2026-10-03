package _959.server_waypoint.core.network;

import _959.server_waypoint.text.chat.Chat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static net.kyori.adventure.text.Component.text;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformMessageSenderViewTest {
    @Test
    void aPlayerViewEndsWithOneNewline() {
        assertEquals(List.of("Saved", ""), lines(PlatformMessageSender.forPlayer(text("Saved"))));
    }

    @Test
    void theCommandersCopyIsTheViewedAsLineThenTheView() {
        Component view = PlatformMessageSender.forPlayer(text("Saved"));

        Component copy = PlatformMessageSender.forCommander(Chat.viewedAs("Alex"), view);

        assertEquals(List.of("Viewed as Alex", "Saved", ""), lines(copy));
        assertEquals(NamedTextColor.GRAY, colorOf(copy, "Viewed as"));
    }

    @Test
    void thePlainTextCopyHasNoTrailingNewline() {
        Component copy = PlatformMessageSender.forCommander(Chat.viewedAs("Alex"), text("Saved"));

        assertEquals(List.of("Viewed as Alex", "Saved"), lines(copy));
    }
}
