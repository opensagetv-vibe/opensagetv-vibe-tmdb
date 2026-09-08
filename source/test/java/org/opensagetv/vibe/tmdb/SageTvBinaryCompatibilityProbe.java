package org.opensagetv.vibe.tmdb;

import org.opensagetv.vibe.tmdb.plugin.OpenSageTVVibeTmdbPlugin;
import sage.SageTVPlugin;
import sage.SageTVPluginRegistry;

/** Runs without compile-only stubs when an actual Sage.jar is available. */
public final class SageTvBinaryCompatibilityProbe {
  private SageTvBinaryCompatibilityProbe() {}

  public static void main(String[] args) throws Exception {
    if (!SageTVPlugin.class.isAssignableFrom(OpenSageTVVibeTmdbPlugin.class)) {
      throw new AssertionError("Plugin does not implement the runtime SageTVPlugin API");
    }
    OpenSageTVVibeTmdbPlugin.class.getConstructor(SageTVPluginRegistry.class);
    OpenSageTVVibeTmdbPlugin.class.getConstructor(SageTVPluginRegistry.class, Boolean.TYPE);
    System.out.println("PASS: plugin links against the actual stock-compatible SageTV API");
  }
}
