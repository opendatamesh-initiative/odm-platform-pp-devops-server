package org.opendatamesh.platform.pp.devops.executor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ExecutorSecretsStoreImpl implements ExecutorSecretsStore {

    private final Cache<String, Map<String, String>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    @Override
    public void store(String executorName, String activityUuid, Map<String, String> secretHeaders) {
        if (secretHeaders == null || secretHeaders.isEmpty()) {
            return;
        }
        cache.put(key(executorName, activityUuid), Collections.unmodifiableMap(new LinkedHashMap<>(secretHeaders)));
    }

    @Override
    public Map<String, String> find(String executorName, String activityUuid) {
        Map<String, String> stored = cache.getIfPresent(key(executorName, activityUuid));
        if (stored == null) {
            return Map.of();
        }
        return stored;
    }

    @Override
    public void removeAll(String activityUuid) {
        String suffix = "-ID-" + activityUuid;
        cache.asMap().keySet().removeIf(key -> key.endsWith(suffix));
    }

    private static String key(String executorName, String activityUuid) {
        return executorName + "-ID-" + activityUuid;
    }
}
