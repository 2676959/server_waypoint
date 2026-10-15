import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.game.HMCLGameLauncher;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameDirectoryID;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.i18n.LocalizedText;

/** Export offline launches using HMCL's installed-version resolution and native patcher. */
public class ExportClient {
    public static void main(String[] args) throws Exception {
        SettingsManager.init();
        Path store = Path.of(args[0]).toAbsolutePath();
        String profile = args[1];
        GameDirectory directory = new GameDirectory(
                GameDirectoryID.generate(),
                LocalizedText.plain("Live tests"), PortablePath.of(store.toString()),
                SettingsManager.getDefaultGameSettingsPresetOrCreate().idProperty().getValue());
        HMCLGameRepository repository = new HMCLGameRepository(directory);
        repository.refreshVersions();
        Version version = org.jackhuang.hmcl.download.MaintainTask.maintain(
                repository, repository.getResolvedVersion(profile));
        Path javaHome = Path.of(args[2]);
        JavaInfo info;
        try (BufferedReader reader = Files.newBufferedReader(javaHome.resolve("release"))) {
            info = JavaInfo.fromReleaseFile(reader);
        }
        JavaRuntime runtime = JavaRuntime.of(javaHome.resolve("bin/java"), info, false);
        List<String> runtimeArguments = new ArrayList<>();
        version = org.jackhuang.hmcl.util.NativePatcher.patchNative(repository, version,
                repository.getGameVersion(version).orElse(null), runtime,
                repository.getEffectiveGameSettings(profile), runtimeArguments);
        Path game = store.resolve("versions").resolve(profile);
        LaunchOptions.Builder builder = repository.getLaunchOptions(profile, runtime, game,
                Collections.emptyList(), runtimeArguments, true);
        builder.setMinMemory(128).setMaxMemory(1024).setQuickPlayOption(null);
        for (List<String> flags : List.of(builder.getJavaArguments(), builder.getOverrideJavaArguments())) {
            flags.removeIf(flag -> flag.startsWith("-Xmx") || flag.startsWith("-Xms"));
        }
        LaunchOptions options = builder.create();
        if (!options.getJavaAgents().isEmpty() || (options.getWrapper() != null && !options.getWrapper().isEmpty())
                || (options.getPreLaunchCommand() != null && !options.getPreLaunchCommand().isEmpty())) {
            throw new IllegalStateException("Unexpected launcher hooks");
        }
        AuthInfo auth = new AuthInfo("SwLiveTest", UUID.nameUUIDFromBytes(
                "OfflinePlayer:SwLiveTest".getBytes(StandardCharsets.UTF_8)), "0", "legacy", "{}");
        new HMCLGameLauncher(repository, version, auth, options).makeLaunchScript(Path.of(args[3]));
        System.exit(0);
    }
}
