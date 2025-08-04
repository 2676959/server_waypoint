package _959.server_waypoint.translation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static _959.server_waypoint.core.WaypointServerCore.LOGGER;

public class LanguageFilesManager {
    private final Path EXTERNAL_LANG_PATH;
    private static final String ASSETS_PATH = "assets/server_waypoint/lang/";
    private static String currentLanguage = "en_us";
    private static final Map<String, Map<String, String>> translations = new HashMap<>();

    public LanguageFilesManager(Path configDir) throws IOException, URISyntaxException {
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
        Files.createDirectory(EXTERNAL_LANG_PATH);
    }

    private void loadAllInternalLanguageFiles() throws URISyntaxException, IOException {
        List<Path> langFiles = getInternalLanguageFiles();
        for (Path langFile : langFiles) {
            loadInternalLanguageFile(langFile.getFileName().toString());
        }
    }

    public void loadAllExternalLanguageFiles() throws IOException {
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
        } catch (IOException e) {
            LOGGER.error("Error loading language file {}: {}", fullPath, e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error parsing language file {}: {}", fullPath, e.getMessage());
        }
    }

    public static String getTranslation(String key) {
        return getTranslation(currentLanguage, key);
    }

    public static String getTranslation(String languageCode, String key) {
        Map<String, String> languageMap = translations.get(languageCode);
        if (languageMap == null) {
            return key;
        }
        
        String translation = languageMap.get(key);
        if (translation == null) {
            return key;
        }
        return translation;
    }

    public static void setCurrentLanguage(String languageCode) {
        currentLanguage = languageCode;
    }

    private List<Path> getInternalLanguageFiles() throws URISyntaxException, IOException {
        List<Path> result;
        // get path of the current running JAR
        String jarPath = getClass().getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .getPath();
        // file walks JAR
        URI uri = URI.create("jar:file:" + jarPath);
        try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
            result = Files.walk(fs.getPath(LanguageFilesManager.ASSETS_PATH))
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        }
        return result;
    }

    private List<Path> getExternalLanguageFiles() throws IOException {
        return Files.walk(EXTERNAL_LANG_PATH)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());
    }

}