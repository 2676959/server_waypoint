package _959.server_waypoint.translation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageFilesManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversLanguagesWhenResourcesAreSeparateFromTheCodeSource() throws Exception {
        Path classes = Files.createDirectories(temporaryDirectory.resolve("mod/build/classes/java/main"));
        String className = LanguageFilesManager.class.getName();
        String classResource = className.replace('.', '/') + ".class";
        Path classFile = classes.resolve(classResource);
        Files.createDirectories(classFile.getParent());
        try (var input = LanguageFilesManager.class.getClassLoader().getResourceAsStream(classResource)) {
            Files.copy(input, classFile);
        }

        Path resources = Files.createDirectories(temporaryDirectory.resolve("common/build/resources/main"));
        Path languages = Files.createDirectories(resources.resolve("lang"));
        Files.writeString(languages.resolve("en_us.json"), "{\"test.key\":\"English\"}");
        Files.writeString(languages.resolve("es_es.json"), "{\"test.key\":\"Spanish\"}");

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{classes.toUri().toURL(), resources.toUri().toURL()}, getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(className)) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }

            @Override
            public URL getResource(String name) {
                return name.startsWith("lang/") ? findResource(name) : super.getResource(name);
            }
        }) {
            Class<?> manager = loader.loadClass(className);
            manager.getConstructor(Path.class).newInstance(temporaryDirectory);
            var getTranslation = manager.getMethod("getTranslation", String.class, String.class);
            assertEquals("English", getTranslation.invoke(null, "en_us", "test.key"));
            assertEquals("Spanish", getTranslation.invoke(null, "es_es", "test.key"));
        }
    }
}
