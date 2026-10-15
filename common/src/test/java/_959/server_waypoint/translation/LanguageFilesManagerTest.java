package _959.server_waypoint.translation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageFilesManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void leavesTheLoaderOwnedJarFileSystemOpenAndReadable() throws Exception {
        Path jar = createLanguageJar();
        try (var loaderFileSystem = FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), Map.of())) {
            loadTranslations(jar.toUri().toURL());
            assertTrue(loaderFileSystem.isOpen(), "Language discovery must not close the loader's filesystem");
            assertEquals("{\"test.key\":\"English\"}",
                    Files.readString(loaderFileSystem.getPath("lang/en_us.json")));
        }
    }

    @Test
    void loadsLanguagesFromAJarWithoutAnExistingFileSystem() throws Exception {
        Path jar = createLanguageJar();
        loadTranslations(jar.toUri().toURL());
        loadTranslations(jar.toUri().toURL());
    }

    private Path createLanguageJar() throws Exception {
        Path jar = temporaryDirectory.resolve("languages.jar");
        String classResource = LanguageFilesManager.class.getName().replace('.', '/') + ".class";
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(classResource));
            try (var input = LanguageFilesManager.class.getClassLoader().getResourceAsStream(classResource)) {
                input.transferTo(output);
            }
            output.closeEntry();
            for (String language : new String[]{"en_us", "es_es"}) {
                output.putNextEntry(new JarEntry("lang/" + language + ".json"));
                String value = language.equals("en_us") ? "English" : "Spanish";
                output.write(("{\"test.key\":\"" + value + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return jar;
    }

    private void loadTranslations(URL... urls) throws Exception {
        String className = LanguageFilesManager.class.getName();
        try (URLClassLoader loader = new URLClassLoader(urls, getClass().getClassLoader()) {
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

        loadTranslations(classes.toUri().toURL(), resources.toUri().toURL());
    }
}
