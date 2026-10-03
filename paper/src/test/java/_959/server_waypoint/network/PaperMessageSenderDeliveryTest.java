package _959.server_waypoint.network;

import _959.server_waypoint.network.PaperMessageSender.Delivery;
import _959.server_waypoint.translation.AdventureTranslator;
import _959.server_waypoint.translation.LanguageFilesManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Who /wp feedback goes to on Paper, and in whose view, with no server running. */
class PaperMessageSenderDeliveryTest {
    private static final Locale ENGLISH = Locale.US;
    private static final Locale SPANISH = Locale.forLanguageTag("es-ES");
    private static final Component FIRST_PAGE = Component.translatable("wp.page.first");

    @TempDir
    static Path directory;

    @BeforeAll
    static void registerTranslations() {
        new LanguageFilesManager(directory);
        GlobalTranslator.translator().addSource(new AdventureTranslator());
    }

    @Test
    void aPlayerRunningTheirOwnCommandGetsOneMessageWithTheTrailingNewline() {
        Player alex = fake(Player.class, "Alex", ENGLISH);
        CommandSourceStack source = source(alex, alex);

        List<Delivery> deliveries = PaperMessageSender.deliveries(source, FIRST_PAGE);

        assertEquals(1, deliveries.size());
        assertSame(alex, deliveries.get(0).recipient());
        assertEquals("First page\n", read(deliveries.get(0), ENGLISH));
        assertSame(alex, PaperMessageSender.viewingPlayer(source));
    }

    @Test
    void theConsoleRunningAsAPlayerGetsThePlayersViewUnderAViewedAsLine() {
        ConsoleCommandSender console = fake(ConsoleCommandSender.class, "CONSOLE", ENGLISH);
        Player alex = fake(Player.class, "Alex", SPANISH);
        CommandSourceStack source = source(console, alex);

        List<Delivery> deliveries = PaperMessageSender.deliveries(source, FIRST_PAGE);

        assertEquals(2, deliveries.size());
        assertSame(alex, deliveries.get(0).recipient());
        assertEquals("Primera página\n", read(deliveries.get(0), SPANISH));
        assertSame(console, deliveries.get(1).recipient());
        assertEquals("Viewed as Alex\nPrimera página", read(deliveries.get(1), ENGLISH),
                "the line is the console's, the view Alex's, and the console reads no trailing newline");
        assertSame(alex, PaperMessageSender.viewingPlayer(source));
    }

    @Test
    void aPlayerRunningAsAnotherPlayerGetsTheirViewUnderAViewedAsLineInTheirOwnLanguage() {
        Player alex = fake(Player.class, "Alex", ENGLISH);
        Player bea = fake(Player.class, "Bea", SPANISH);
        CommandSourceStack source = source(alex, bea);

        List<Delivery> deliveries = PaperMessageSender.deliveries(source, FIRST_PAGE);

        assertEquals(2, deliveries.size());
        assertSame(bea, deliveries.get(0).recipient());
        assertEquals("Primera página\n", read(deliveries.get(0), SPANISH));
        assertSame(alex, deliveries.get(1).recipient());
        assertEquals("Viewed as Bea\nPrimera página\n", read(deliveries.get(1), ENGLISH));
        assertEquals("Visto como Bea\nPrimera página\n", read(deliveries.get(1), SPANISH),
                "Paper renders the line for the receiver; the view is already Bea's");
        assertSame(bea, PaperMessageSender.viewingPlayer(source));
    }

    @Test
    void aCommandRunningAsSomethingThatIsNotAPlayerStaysWithTheSender() {
        Player alex = fake(Player.class, "Alex", ENGLISH);
        ArmorStand stand = fake(ArmorStand.class, "Stand", ENGLISH);
        CommandSourceStack source = source(alex, stand);

        List<Delivery> deliveries = PaperMessageSender.deliveries(source, FIRST_PAGE);

        assertEquals(1, deliveries.size());
        assertSame(alex, deliveries.get(0).recipient());
        assertEquals("First page\n", read(deliveries.get(0), ENGLISH));
        assertSame(alex, PaperMessageSender.viewingPlayer(source));
    }

    @Test
    void theConsoleGetsPlainTextWhenNoPlayerReadsIt() {
        ConsoleCommandSender console = fake(ConsoleCommandSender.class, "CONSOLE", ENGLISH);
        ArmorStand stand = fake(ArmorStand.class, "Stand", ENGLISH);

        for (CommandSourceStack source : List.of(source(console, null), source(console, stand))) {
            List<Delivery> deliveries = PaperMessageSender.deliveries(source, FIRST_PAGE);

            assertEquals(1, deliveries.size());
            assertSame(console, deliveries.get(0).recipient());
            assertEquals("First page", read(deliveries.get(0), ENGLISH));
            assertNull(PaperMessageSender.viewingPlayer(source));
        }
    }

    /** What the recipient reads once Paper has rendered the message for their language. */
    private static String read(Delivery delivery, Locale locale) {
        return PlainTextComponentSerializer.plainText().serialize(GlobalTranslator.render(delivery.message(), locale));
    }

    private static CommandSourceStack source(CommandSender sender, Entity executor) {
        return stub(CommandSourceStack.class, (method, arguments) -> switch (method.getName()) {
            case "getSender" -> sender;
            case "getExecutor" -> executor;
            default -> null;
        });
    }

    private static <T> T fake(Class<T> type, String name, Locale locale) {
        return stub(type, (method, arguments) -> switch (method.getName()) {
            case "getName", "toString" -> name;
            case "locale" -> locale;
            default -> null;
        });
    }

    private interface Answer {
        Object answer(java.lang.reflect.Method method, Object[] arguments);
    }

    /** A bare implementation of a Bukkit interface: identity equality, the given answers, defaults otherwise. */
    private static <T> T stub(Class<T> type, Answer answers) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            switch (method.getName()) {
                case "equals":
                    return proxy == arguments[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                default:
                    Object answer = answers.answer(method, arguments);
                    return answer != null ? answer : defaultValue(method.getReturnType());
            }
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        } else if (type == char.class) {
            return '\0';
        } else if (type == byte.class) {
            return (byte) 0;
        } else if (type == short.class) {
            return (short) 0;
        } else if (type == int.class) {
            return 0;
        } else if (type == long.class) {
            return 0L;
        } else if (type == float.class) {
            return 0F;
        } else if (type == double.class) {
            return 0D;
        }
        return null;
    }
}
