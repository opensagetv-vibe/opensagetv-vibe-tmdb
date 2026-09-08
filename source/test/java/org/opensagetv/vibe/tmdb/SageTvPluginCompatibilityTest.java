package org.opensagetv.vibe.tmdb;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.opensagetv.vibe.tmdb.plugin.OpenSageTVVibeTmdbFacade;
import org.opensagetv.vibe.tmdb.plugin.OpenSageTVVibeTmdbPlugin;
import sage.SageTVEventListener;
import sage.SageTVPlugin;
import sage.SageTVPluginRegistry;

final class SageTvPluginCompatibilityTest {
  static void run() throws Exception {
    Path directory = Files.createTempDirectory("tmdb-plugin-test");
    Path config = directory.resolve("tmdb_config.toml");
    Files.write(config, ("[tmdb]\napi_key=\"fixture-key\"\ncache_path=\"cache.sqlite3\"\n")
        .getBytes(StandardCharsets.UTF_8));

    OpenSageTVVibeTmdbPlugin plugin =
        new OpenSageTVVibeTmdbPlugin(new EmptyRegistry(), true);
    require(plugin instanceof SageTVPlugin, "stock SageTVPlugin interface");
    plugin.setConfigValue("Configuration file", config.toString());
    plugin.setConfigValue("Enabled", "true");
    require(OpenSageTVVibeTmdbFacade.isAvailable(), "enable setting starts shared service immediately");
    require("Running".equals(OpenSageTVVibeTmdbFacade.getStatus()), "redacted plugin status");
    plugin.setConfigValue("Enabled", "false");
    require(!OpenSageTVVibeTmdbFacade.isAvailable(), "disable setting stops shared service immediately");
    require("Disabled".equals(OpenSageTVVibeTmdbFacade.getStatus()), "disabled plugin status");
    plugin.setConfigValue("Enabled", "true");
    require(OpenSageTVVibeTmdbFacade.isAvailable(), "plugin can be re-enabled without a JVM restart");
    plugin.stop();
    require(!OpenSageTVVibeTmdbFacade.isAvailable(), "plugin stops shared service");
    plugin.destroy();
  }

  private static final class EmptyRegistry implements SageTVPluginRegistry {
    public void eventSubscribe(SageTVEventListener listener, String eventName) {}
    public void eventUnsubscribe(SageTVEventListener listener, String eventName) {}
    public void postEvent(String eventName, Map<?, ?> eventVars) {}
    public void postEvent(String eventName, Map<?, ?> eventVars, boolean waitUntilDone) {}
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
