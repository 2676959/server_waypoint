package _959.server_waypoint.translation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.walk;

public class LanguageFilesManager {
    private final Path EXTERNAL_LANG_PATH;
    private static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_translator");
    private static final String FALL_BACK_LANGUAGE = "en_us";
    private static final String ASSETS_PATH = "lang/";
    private static volatile Map<String, Map<String, String>> internalTranslations = Map.of();
    private static volatile Map<String, Map<String, String>> externalTranslations = Map.of();

    public LanguageFilesManager(@NotNull Path configDir) {
        EXTERNAL_LANG_PATH = configDir.resolve("lang");
        loadAllInternalLanguageFiles();
        String languageCodes = String.join(", ", internalTranslations.keySet());
        LOGGER.info("Internal translations: {}", languageCodes);
    }

    public void initLanguageManager() throws IOException {
        initExternalLangDirectory();
        loadAllExternalLanguageFiles();
    }

    private Map<String, String> convertJsonToHashMap(JsonObject jsonObject) {
        Map<String, String> languageMap = new HashMap<>();
        for (String key : jsonObject.keySet()) {
            String value = jsonObject.get(key).getAsString();
            languageMap.put(key, value);
        }
        return Map.copyOf(languageMap);
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
        Map<String, Map<String, String>> loadedTranslations = new HashMap<>();
        for (Path langFile : langFiles) {
            loadInternalLanguageFile(langFile.getFileName().toString(), loadedTranslations);
        }
        internalTranslations = Map.copyOf(loadedTranslations);
    }

    public void loadAllExternalLanguageFiles() {
        List<Path> langFiles = getExternalLanguageFiles();
        Map<String, Map<String, String>> loadedTranslations = new HashMap<>();
        for (Path langFile : langFiles) {
            loadExternalLanguageFile(langFile, loadedTranslations);
        }
        externalTranslations = Map.copyOf(loadedTranslations);
    }

    private void loadInternalLanguageFile(String fileName, Map<String, Map<String, String>> loadedTranslations) {
        InputStream resourceStream = LanguageFilesManager.class.getClassLoader()
                .getResourceAsStream(ASSETS_PATH + fileName);
        if (resourceStream == null) {
            LOGGER.error("internal language file not found: {}", fileName);
            return;
        }
        try (InputStream inputStream = resourceStream;
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, String> languageMap = convertJsonToHashMap(jsonObject);
            String key = fileName.split("\\.")[0].toLowerCase();
            loadedTranslations.put(key, languageMap);
        } catch (IOException e) {
            LOGGER.error("Error loading internal language file {}: {}", fileName, e.getMessage());
        }
    }

    private void loadExternalLanguageFile(Path fullPath, Map<String, Map<String, String>> loadedTranslations) {
        if (!Files.exists(fullPath)) {
            LOGGER.error("external language file not found: {}", fullPath);
            return;
        }
        try {
            String key = fullPath.getFileName().toString().split("\\.")[0].toLowerCase();
            try (BufferedReader reader = Files.newBufferedReader(fullPath, StandardCharsets.UTF_8)) {
                JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, String> languageMap = convertJsonToHashMap(jsonObject);
                loadedTranslations.put(key, languageMap);
            }
        } catch (Exception e) {
            LOGGER.error("Error parsing language file {}: {}", fullPath, e.getMessage());
        }
    }

    public static @Unmodifiable @NotNull List<String> getExternalLoadedLanguages() {
        Map<String, Map<String, String>> snapshot = externalTranslations;
        List<String> languageCodes = new ArrayList<>(snapshot.keySet());
        Collections.sort(languageCodes);
        return Collections.unmodifiableList(languageCodes);
    }

    @Nullable
    public static String getTranslation(String languageCode, String key) {
        Map<String, Map<String, String>> externalSnapshot = externalTranslations;
        Map<String, Map<String, String>> internalSnapshot = internalTranslations;
        return getTranslation(languageCode, key, externalSnapshot, internalSnapshot);
    }

    @Nullable
    public static String getTranslation(Locale locale, String key) {
        Map<String, Map<String, String>> externalSnapshot = externalTranslations;
        Map<String, Map<String, String>> internalSnapshot = internalTranslations;
        String fullCode = locale.toString().toLowerCase();
        String translation = getTranslation(fullCode, key, externalSnapshot, internalSnapshot);
        if (translation == null) {
            // try without region code
            String language = locale.getLanguage().toLowerCase();
            Set<String> allLoadedLanguageCodes = getAllLoadedLanguageCodes(internalSnapshot, externalSnapshot);
            List<String> matchingLanguages = new ArrayList<>(allLoadedLanguageCodes.size());
            for (String value : allLoadedLanguageCodes) {
                if (value.toLowerCase().startsWith(language)) {
                    matchingLanguages.add(value);
                }
            }
            Collections.sort(matchingLanguages);
            for (String languageCode : matchingLanguages) {
                if ((translation = getTranslation(languageCode, key, externalSnapshot, internalSnapshot)) != null) {
                    break;
                }
            }
        }
        return translation == null
                ? getTranslation(FALL_BACK_LANGUAGE, key, externalSnapshot, internalSnapshot)
                : translation;
    }

    @Nullable
    private static String getTranslation(
            String languageCode,
            String key,
            Map<String, Map<String, String>> externalSnapshot,
            Map<String, Map<String, String>> internalSnapshot
    ) {
        String translation = getTranslationFrom(externalSnapshot, languageCode, key);
        return translation == null ? getTranslationFrom(internalSnapshot, languageCode, key) : translation;
    }

    @Nullable
    private static String getTranslationFrom(@NotNull Map<String, Map<String, String>> source, String languageCode, String key) {
        if (languageCode == null || key == null) {
            return null;
        }
        Map<String, String> translations = source.get(languageCode);
        if (translations != null) {
            return translations.get(key);
        }
        return null;
    }

    private static @Unmodifiable @NotNull Set<String> getAllLoadedLanguageCodes(
            Map<String, Map<String, String>> internalSnapshot,
            Map<String, Map<String, String>> externalSnapshot
    ) {
        Set<String> languageCodes = new HashSet<>(internalSnapshot.keySet());
        languageCodes.addAll(externalSnapshot.keySet());
        return Collections.unmodifiableSet(languageCodes);
    }

    private List<Path> getInternalLanguageFiles() {
        Path codeSourcePath;
        try {
            URL location = getCodeSourceLocation();
            if (location == null) {
                LOGGER.error("Failed to get path of internal language files: code source location is unavailable");
                return List.of();
            }
            codeSourcePath = Path.of(location.toURI());
        } catch (URISyntaxException e) {
            LOGGER.error("Failed to get path of internal language files: {}", e.getMessage());
            return List.of();
        }

        if (Files.isDirectory(codeSourcePath)) {
            return getInternalLanguageFilesFromDirectoryCodeSource(codeSourcePath);
        }

        return getInternalLanguageFilesFromJar(codeSourcePath);
    }

    private List<Path> getInternalLanguageFilesFromDirectoryCodeSource(Path codeSourcePath) {
        for (Path langPath : getDirectoryCodeSourceLangPaths(codeSourcePath)) {
            List<Path> langFiles = getInternalLanguageFilesFromDirectory(langPath);
            if (!langFiles.isEmpty()) {
                return langFiles;
            }
        }

        LOGGER.error("Internal language files not found near code source: {}", codeSourcePath);
        return List.of();
    }

    private List<Path> getInternalLanguageFilesFromDirectory(Path langPath) {
        if (!Files.isDirectory(langPath)) {
            return List.of();
        }

        try (Stream<Path> paths = walk(langPath, 1)) {
            return paths.filter((file) -> isRegularFile(file) && file.getFileName().toString().endsWith(".json"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.error("Error loading internal language file: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Path> getDirectoryCodeSourceLangPaths(Path codeSourcePath) {
        List<Path> langPaths = new ArrayList<>(3);
        langPaths.add(codeSourcePath.resolve(ASSETS_PATH));

        Path buildDirectory = findParentDirectory(codeSourcePath, "build");
        if (buildDirectory != null) {
            langPaths.add(buildDirectory.resolve("devCommon").resolve(ASSETS_PATH));
            langPaths.add(buildDirectory.resolve("resources").resolve("main").resolve(ASSETS_PATH));
        }

        return langPaths;
    }

    @Nullable
    private Path findParentDirectory(Path path, String name) {
        Path current = path;
        while (current != null) {
            Path fileName = current.getFileName();
            if (fileName != null && fileName.toString().equals(name)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private List<Path> getInternalLanguageFilesFromJar(Path jarPath) {
        URI uri = URI.create("jar:" + jarPath.toUri());
        try (FileSystem fileSystem = getOrCreateFileSystem(uri)) {
            try (Stream<Path> paths = walk(fileSystem.getPath(ASSETS_PATH), 1)) {
                return paths.filter((file) -> isRegularFile(file) && file.getFileName().toString().endsWith(".json"))
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            LOGGER.error("Error loading internal language file: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Path> getExternalLanguageFiles() {
        try (Stream<Path> paths = walk(EXTERNAL_LANG_PATH, 1)) {
            return paths.filter((file) -> isRegularFile(file) && file.getFileName().toString().endsWith(".json"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.error("External language files not found: {}", e.getMessage());
            return List.of();
        }
    }

    public void unloadAllExternalLanguages() {
        externalTranslations = Map.of();
    }

    public void reloadExternalLanguages() {
        loadAllExternalLanguageFiles();
    }

    private FileSystem getOrCreateFileSystem(URI uri) throws IOException {
        try {
            return FileSystems.newFileSystem(uri, Collections.emptyMap());
        } catch (FileSystemAlreadyExistsException e) {
            return FileSystems.getFileSystem(uri);
        }
    }

    @Nullable
    private URL getCodeSourceLocation() {
        ProtectionDomain protectionDomain = getClass().getProtectionDomain();
        if (protectionDomain == null) {
            return null;
        }
        CodeSource codeSource = protectionDomain.getCodeSource();
        if (codeSource == null) {
            return null;
        }
        return codeSource.getLocation();
    }
}
