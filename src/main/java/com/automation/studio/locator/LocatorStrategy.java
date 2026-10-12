package com.automation.studio.locator;

import java.util.Map;
import java.util.Optional;

/** One way of building a locator from a DOM description. Strategies are tried in priority order. */
@FunctionalInterface
public interface LocatorStrategy {
    Optional<LocatorInfo> tryBuild(Map<String, String> dom);
}
