/* Compile-only subset of the Apache-2.0 SageTV property API. Not packaged. */
package sage;

public final class Sage {
  private static final java.util.Map<String, String> VALUES =
      new java.util.concurrent.ConcurrentHashMap<String, String>();
  private Sage() {}
  public static String get(String name, String defaultValue) {
    String value = VALUES.get(name);
    return value == null ? defaultValue : value;
  }
  public static void put(String name, String value) {
    if (value == null) VALUES.remove(name); else VALUES.put(name, value);
  }
}
