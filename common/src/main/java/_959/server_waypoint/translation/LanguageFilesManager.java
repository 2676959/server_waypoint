package _959.server_waypoint.translation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static _959.server_waypoint.core.WaypointServerCore.LOGGER;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.walk;

public class LanguageFilesManager {
    private final Path EXTERNAL_LANG_PATH;
    private static final String FALL_BACK_LANGUAGE = "en_us";
    private static final String ASSETS_PATH = "assets/server_waypoint/lang/";
    private static final Map<String, Map<String, String>> translations = new HashMap<>();

    public LanguageFilesManager(Path configDir) throws IOException {
        EXTERNAL_LANG_PATH = configDir.resolve("lang");
        initExternalLangDirectory();
        loadAllInternalLanguageFiles();
    }

    private Map<String, String> convertJsonToHashMap(JsonObject jsonObject) {
        Map<String, String> languageMap = new HashMap<>();
        for (String key : jsonObject.keySet()) {
            String value = jsonObject.get(key).getAsString();
            languageMap.put(key, value);
        }
        return languageMap;
    }

    private void initExternalLangDirectory() throws IOException {
        if (Files.exists(EXTERNAL_LANG_PATH) && Files.isDirectory(EXTERNAL_LANG_PATH)) {
            return;
        }
        try {
            Files.createDirectory(EXTERNAL_LANG_PATH);
        } catch (IOException e) {
            LOGGER.error("Could not create language file directory {}", EXTERNAL_LANG_PATH);
            throw e;
        }
    }

    private void loadAllInternalLanguageFiles() {
        List<Path> langFiles = getInternalLanguageFiles();
        for (Path langFile : langFiles) {
            loadInternalLanguageFile(langFile.getFileName().toString());
        }
    }

    public void loadAllExternalLanguageFiles() {
        List<Path> langFiles = getExternalLanguageFiles();
        for (Path langFile : langFiles) {
            loadExternalLanguageFile(langFile);
        }
    }

    private void loadInternalLanguageFile(String fileName) {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(ASSETS_PATH + fileName);
        if (inputStream == null) {
            LOGGER.error("internal language file not found: {}", fileName);
            return;
        }
        JsonObject jsonObject = JsonParser.parseReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .getAsJsonObject();
        Map<String, String> languageMap = convertJsonToHashMap(jsonObject);
        translations.put(fileName.split("\\.")[0], languageMap);
    }

    private void loadExternalLanguageFile(Path fullPath) {
        if (!Files.exists(fullPath)) {
            LOGGER.error("external language file not found: {}", fullPath);
            return;
        }
        try {
            JsonObject jsonObject = JsonParser.parseReader(Files.newBufferedReader(fullPath, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, String> languageMap = convertJsonToHashMap(jsonObject);
            translations.put(fullPath.getFileName().toString().split("\\.")[0], languageMap);
        } catch (Exception e) {
            LOGGER.error("Error parsing language file {}: {}", fullPath, e.getMessage());
        }
    }

    public static Set<String> getLoadedLanguages() {
        return translations.keySet();
    }

    @Nullable
    public static String getTranslation(String languageCode, String key) {
        Map<String, String> languageMap = translations.get(languageCode);
        if (languageMap == null) {
            return null;
        }
        return languageMap.get(key);
    }

    @Nullable
    public static String getTranslation(Locale locale, String key) {
        String fullCode = locale.toString().toLowerCase();
        Map<String, String> languageMap = translations.get(fullCode);
        if (languageMap == null) {
            // try without region code
            String language = locale.getLanguage().toLowerCase();
            Set<String> allLanguageCodes = translations.keySet();
            for (String languageCode : allLanguageCodes) {
                if (languageCode.toLowerCase().contains(language)) {
                    return translations.get(languageCode).get(key);
                }
            }
            return translations.get(FALL_BACK_LANGUAGE).get(key);
        }
        return languageMap.get(key);
    }

    private List<Path> getInternalLanguageFiles() {
        String jarPath;
        try {
            // get path of the mod jar itself
            jarPath = getClass().getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
        } catch (URISyntaxException e) {
            LOGGER.error("Failed to get path of the mod jar itself: {}", e.getMessage());
            return new ArrayList<>();
        }
        // file walks JAR
        URI uri = URI.create("jar:file:" + jarPath);
        try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
            try (Stream<Path> paths = walk(fileSystem.getPath(ASSETS_PATH), 1)) {
                return paths.filter((file) -> isRegularFile(file) && file.getFileName().toString().endsWith(".json"))
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            LOGGER.error("Error loading internal language file: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Path> getExternalLanguageFiles() {
        try (Stream<Path> paths = walk(EXTERNAL_LANG_PATH, 1)) {
            return paths.filter((file) -> isRegularFile(file) && file.getFileName().toString().endsWith(".json"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.error("External language files not found: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

}