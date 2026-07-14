package _959.server_waypoint.common.client.gui.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * GSON-backed JSON persistence for widget themes.
 */
public final class WidgetThemeJson {
    public static final int FORMAT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FORMAT_VERSION_PROPERTY = "formatVersion";
    private static final String COLORS_PROPERTY = "colors";

    private WidgetThemeJson() {
    }

    public static String toJson(WidgetTheme theme) {
        Objects.requireNonNull(theme, "theme");
        JsonObject root = new JsonObject();
        root.addProperty(FORMAT_VERSION_PROPERTY, FORMAT_VERSION);

        JsonObject colors = new JsonObject();
        for (WidgetThemeVariable variable : WidgetThemeVariable.values()) {
            colors.addProperty(variable.getJsonName(), formatColor(theme.getColor(variable)));
        }
        root.add(COLORS_PROPERTY, colors);
        return GSON.toJson(root);
    }

    public static WidgetTheme fromJson(String json) {
        return fromJson(json, WidgetThemes.MODERN_DARK);
    }

    public static WidgetTheme fromJson(String json, WidgetTheme fallbackTheme) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(fallbackTheme, "fallbackTheme");
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
            throw new JsonParseException("Widget theme root must be a JSON object");
        }

        JsonObject root = parsed.getAsJsonObject();
        int formatVersion = readFormatVersion(root);
        if (formatVersion != FORMAT_VERSION) {
            throw new JsonParseException("Unsupported widget theme format version: " + formatVersion);
        }

        WidgetTheme.Builder builder = WidgetTheme.builder(fallbackTheme);
        JsonElement colorsElement = root.get(COLORS_PROPERTY);
        if (colorsElement == null) {
            return builder.build();
        }
        if (!colorsElement.isJsonObject()) {
            throw new JsonParseException("Widget theme colors must be a JSON object");
        }

        for (Map.Entry<String, JsonElement> entry : colorsElement.getAsJsonObject().entrySet()) {
            WidgetThemeVariable.fromJsonName(entry.getKey()).ifPresent(variable ->
                    builder.setColor(variable, parseColor(entry.getKey(), entry.getValue())));
        }
        return builder.build();
    }

    public static void save(Path path, WidgetTheme theme) throws IOException {
        Objects.requireNonNull(path, "path");
        Path absolutePath = path.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String fileName = absolutePath.getFileName().toString();
        String temporaryPrefix = fileName.length() >= 3 ? fileName : "theme-" + fileName;
        Path temporaryFile = Files.createTempFile(parent, temporaryPrefix, ".tmp");
        try {
            Files.writeString(temporaryFile, toJson(theme), StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, absolutePath,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, absolutePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    public static WidgetTheme load(Path path) throws IOException {
        return load(path, WidgetThemes.MODERN_DARK);
    }

    public static WidgetTheme load(Path path, WidgetTheme fallbackTheme) throws IOException {
        Objects.requireNonNull(path, "path");
        return fromJson(Files.readString(path, StandardCharsets.UTF_8), fallbackTheme);
    }

    public static void saveCurrent(Path path) throws IOException {
        save(path, WidgetThemeManager.getTheme());
    }

    public static WidgetTheme loadAndApply(Path path) throws IOException {
        WidgetTheme theme = load(path);
        WidgetThemeManager.setTheme(theme);
        return theme;
    }

    private static int readFormatVersion(JsonObject root) {
        JsonElement versionElement = root.get(FORMAT_VERSION_PROPERTY);
        if (versionElement == null || !versionElement.isJsonPrimitive()
                || !versionElement.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("Widget theme formatVersion must be a number");
        }
        try {
            return versionElement.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new JsonParseException("Widget theme formatVersion must be an integer", exception);
        }
    }

    private static int parseColor(String variableName, JsonElement colorElement) {
        if (!(colorElement instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new JsonParseException("Widget theme color '" + variableName + "' must be a hex string");
        }

        String value = primitive.getAsString();
        if (!value.startsWith("#") || value.length() != 7 && value.length() != 9) {
            throw new JsonParseException(
                    "Widget theme color '" + variableName + "' must use #RRGGBB or #AARRGGBB format");
        }
        try {
            long color = Long.parseUnsignedLong(value.substring(1), 16);
            return value.length() == 7 ? (int)(0xFF000000L | color) : (int)color;
        } catch (NumberFormatException exception) {
            throw new JsonParseException(
                    "Widget theme color '" + variableName + "' must use #RRGGBB or #AARRGGBB format",
                    exception);
        }
    }

    private static String formatColor(int color) {
        return String.format(Locale.ROOT, "#%08X", color);
    }
}
