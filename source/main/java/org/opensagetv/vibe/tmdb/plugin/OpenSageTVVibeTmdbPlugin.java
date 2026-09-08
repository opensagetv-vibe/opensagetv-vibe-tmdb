package org.opensagetv.vibe.tmdb.plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.opensagetv.vibe.tmdb.TmdbServiceRegistry;
import sage.Sage;
import sage.SageTVPlugin;
import sage.SageTVPluginRegistry;

/** Stock SageTV 9 plugin lifecycle wrapper for the reusable TMDB service. */
public final class OpenSageTVVibeTmdbPlugin implements SageTVPlugin {
  private static final String PREFIX = "opensagetv_vibe/tmdb/";
  private static final String ENABLED = "Enabled";
  private static final String CONFIGURATION_FILE = "Configuration file";
  private static final String STATUS = "Status";
  private static final String[] SETTINGS = {ENABLED, CONFIGURATION_FILE, STATUS};
  private static volatile String status = "Stopped";

  @SuppressWarnings("unused")
  private final SageTVPluginRegistry registry;

  public OpenSageTVVibeTmdbPlugin(SageTVPluginRegistry registry) {
    this(registry, false);
  }

  public OpenSageTVVibeTmdbPlugin(SageTVPluginRegistry registry, boolean reset) {
    this.registry = registry;
    if (reset) resetConfig();
  }

  @Override
  public synchronized void start() {
    if (!Boolean.parseBoolean(Sage.get(PREFIX + "enabled", "false"))) {
      status = "Disabled";
      return;
    }
    try {
      Path config = Paths.get(Sage.get(PREFIX + "configuration_file", defaultConfigPath()))
          .toAbsolutePath().normalize();
      TmdbServiceRegistry.start(config);
      status = "Running";
    } catch (Exception error) {
      status = "ERROR: " + safeMessage(error);
    }
  }

  @Override
  public synchronized void stop() {
    try {
      TmdbServiceRegistry.stop();
      status = "Stopped";
    } catch (IOException error) {
      status = "ERROR: unable to close TMDB service";
    }
  }

  @Override
  public void destroy() { stop(); }

  @Override
  public String[] getConfigSettings() { return SETTINGS.clone(); }

  @Override
  public String getConfigValue(String setting) {
    if (ENABLED.equals(setting)) return Sage.get(PREFIX + "enabled", "false");
    if (CONFIGURATION_FILE.equals(setting)) {
      return Sage.get(PREFIX + "configuration_file", defaultConfigPath());
    }
    if (STATUS.equals(setting)) return status;
    return "";
  }

  @Override
  public String[] getConfigValues(String setting) { return null; }

  @Override
  public int getConfigType(String setting) {
    if (ENABLED.equals(setting)) return CONFIG_BOOL;
    if (CONFIGURATION_FILE.equals(setting)) return CONFIG_FILE;
    return CONFIG_TEXT;
  }

  @Override
  public synchronized void setConfigValue(String setting, String value) {
    if (ENABLED.equals(setting)) {
      boolean enabled = Boolean.parseBoolean(value);
      Sage.put(PREFIX + "enabled", Boolean.toString(enabled));
      if (enabled) {
        start();
      } else {
        stop();
        status = "Disabled";
      }
    } else if (CONFIGURATION_FILE.equals(setting) && value != null && !value.trim().isEmpty()) {
      Sage.put(PREFIX + "configuration_file", value.trim());
      if (Boolean.parseBoolean(Sage.get(PREFIX + "enabled", "false"))) {
        stop();
        start();
      }
    }
  }

  @Override
  public void setConfigValues(String setting, String[] values) {}

  @Override
  public String[] getConfigOptions(String setting) { return null; }

  @Override
  public String getConfigHelpText(String setting) {
    if (ENABLED.equals(setting)) return "Start the shared TMDB metadata and SQLite cache service.";
    if (CONFIGURATION_FILE.equals(setting)) {
      return "Private tmdb_config.toml path. Keep this file outside plugin and handoff packages.";
    }
    if (STATUS.equals(setting)) return "Current service state; credentials are never displayed.";
    return "";
  }

  @Override
  public String getConfigLabel(String setting) { return setting; }

  @Override
  public synchronized void resetConfig() {
    Sage.put(PREFIX + "enabled", "false");
    Sage.put(PREFIX + "configuration_file", defaultConfigPath());
    status = "Stopped";
  }

  @Override
  public void sageEvent(String eventName, Map<?, ?> eventVars) {}

  public static String getStatus() { return status; }

  private static String defaultConfigPath() {
    return Paths.get(System.getProperty("user.dir"), "plugins", "opensagetv-vibe-tmdb",
        "tmdb_config.toml").toString();
  }

  private static String safeMessage(Exception error) {
    String message = error.getMessage();
    if (message == null || message.trim().isEmpty()) return error.getClass().getSimpleName();
    return message.replaceAll("(?i)(api[_ -]?key|token|bearer)\\s*[=:]\\s*\\S+", "$1=[redacted]");
  }
}
