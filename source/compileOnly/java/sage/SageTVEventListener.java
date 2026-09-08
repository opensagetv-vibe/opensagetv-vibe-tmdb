/* Compile-only subset of the Apache-2.0 SageTV plugin API. Not packaged. */
package sage;

import java.util.Map;

public interface SageTVEventListener {
  void sageEvent(String eventName, Map<?, ?> eventVars);
}
