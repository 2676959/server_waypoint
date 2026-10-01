package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/** The upload panel and the outcome of an upload (spec 11). */
public final class UploadScreens {
    /** Whoever uploads is a player whose client has the mod. */
    private static final Viewer UPLOADER = new Viewer(Set.of(), true, false, null, null, 0F);

    /** What an upload changed, and the commands that follow up on it. */
    public record Outcome(UploadTarget source, int added, int replaced, int deleted, int unchanged, int conflicts,
                          int skipped, int staleDimensions, boolean saveFailed, boolean stoppedEarly,
                          @Nullable String preferMine, String retry) {
    }

    private UploadScreens() {
    }

    /** /wp upload: every map mod with Merge, Prefer mine and (with the delete permission) Mirror. */
    public static Component panel(Viewer viewer) {
        ChatLines lines = new ChatLines().add(translatable("wp.upload.title", GOLD));
        for (UploadTarget target : UploadTarget.values()) {
            String upload = "/wp upload " + target.name().toLowerCase(Locale.ROOT);
            lines.line(Chat.colored(sourceName(target), WHITE), text("  "), Chat.join(
                    Chat.link(viewer, translatable("wp.upload.merge"), AQUA, Click.run(upload),
                            Tooltip.of("wp.upload.merge.tooltip").line("wp.upload.merge.detail")),
                    Chat.link(viewer, translatable("wp.upload.prefer_mine"), YELLOW, Click.suggest(upload + " force local"),
                            Tooltip.of("wp.upload.prefer_mine.tooltip").line("wp.upload.prefer_mine.detail")
                                    .hint("wp.hint.confirm")),
                    viewer.can(Viewer.Permission.UPLOAD_DELETE)
                            ? Chat.link(viewer, translatable("wp.upload.mirror"), RED,
                            Click.suggest(upload + " force local delete"),
                            Tooltip.of("wp.upload.mirror.tooltip").line("wp.upload.mirror.detail").hint("wp.hint.confirm"))
                            : null));
        }
        return lines.add(translatable("wp.upload.scope_hint", GRAY)).build();
    }

    /** Asking your map mod for its waypoints… and, when mirroring, what will be removed. */
    public static Component requested(boolean mirror) {
        return new ChatLines()
                .add(Chat.colored(Chat.concat(translatable("wp.upload.requested"), text(Chat.ELLIPSIS)), GRAY))
                .add(mirror ? translatable("wp.upload.requested.mirror", GRAY) : null)
                .build();
    }

    /** ✔ Uploaded from Xaero's Minimap, the non-zero counts, then what to do about conflicts and stale dimensions. */
    public static Component result(Outcome outcome) {
        Component source = sourceName(outcome.source());
        ChatLines lines = new ChatLines().add(outcome.stoppedEarly()
                ? Chat.error(translatable("wp.upload.partial", source))
                : Chat.ok(translatable("wp.upload.done", source)));
        List<Component> counts = new ArrayList<>();
        count(counts, outcome.added(), translatable("wp.upload.count.added", text(outcome.added())), "added");
        count(counts, outcome.replaced(), translatable("wp.upload.count.replaced", text(outcome.replaced())), "replaced");
        count(counts, outcome.deleted(), translatable("wp.upload.count.deleted", text(outcome.deleted())), "deleted");
        count(counts, outcome.unchanged(), translatable("wp.upload.count.unchanged", text(outcome.unchanged())), "unchanged");
        count(counts, outcome.conflicts(), Chat.count("wp.count.conflict", outcome.conflicts()), "conflicts");
        count(counts, outcome.skipped(), translatable("wp.upload.count.skipped", text(outcome.skipped())), "skipped");
        lines.add(counts.isEmpty() ? translatable("wp.upload.nothing", GRAY) : Chat.colored(Chat.join(counts), GRAY));
        if (outcome.conflicts() > 0 && outcome.preferMine() != null) {
            lines.line(translatable("wp.upload.conflicts_kept", GRAY, Chat.count("wp.count.conflict", outcome.conflicts())),
                    text("  "), Chat.link(UPLOADER, translatable("wp.upload.prefer_mine"), YELLOW,
                            Click.suggest(outcome.preferMine()),
                            Tooltip.of("wp.upload.prefer_mine.tooltip").line("wp.upload.prefer_mine.detail")
                                    .hint("wp.hint.confirm")));
        }
        if (outcome.staleDimensions() > 0) {
            lines.line(translatable("wp.upload.stale", GRAY, Chat.count("wp.count.dimension", outcome.staleDimensions())),
                    text(" "), Chat.link(UPLOADER, translatable("wp.upload.try_again"), AQUA, Click.suggest(outcome.retry()),
                            Tooltip.of("wp.upload.try_again.tooltip").hint("wp.hint.confirm")));
        }
        if (outcome.saveFailed()) {
            lines.add(Errors.of("wp.error.upload.save"));
        }
        return lines.build();
    }

    public static Component sourceName(UploadTarget target) {
        return translatable("wp.upload.source." + target.name().toLowerCase(Locale.ROOT));
    }

    private static void count(List<Component> counts, int count, Component label, String kind) {
        if (count > 0) {
            counts.add(Chat.hover(UPLOADER, label, Tooltip.of("wp.upload.count." + kind + ".tooltip")));
        }
    }
}
