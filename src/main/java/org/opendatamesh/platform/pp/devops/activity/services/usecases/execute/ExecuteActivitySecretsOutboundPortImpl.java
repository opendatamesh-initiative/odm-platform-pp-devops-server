package org.opendatamesh.platform.pp.devops.activity.services.usecases.execute;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.executor.ExecutorSecretsStore;
import org.springframework.http.HttpHeaders;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class ExecuteActivitySecretsOutboundPortImpl implements ExecuteActivitySecretsOutboundPort {

    private final ExecutorSecretsStore executorSecretsStore;
    private final HttpHeaders headers;

    ExecuteActivitySecretsOutboundPortImpl(ExecutorSecretsStore executorSecretsStore, HttpHeaders headers) {
        this.executorSecretsStore = executorSecretsStore;
        this.headers = headers == null ? new HttpHeaders() : headers;
    }

    @Override
    public void storeExecutorSecrets(Activity activity) {
        for (String executorName : distinctExecutorNames(activity)) {
            executorSecretsStore.store(executorName, activity.getUuid(), rewriteSecretHeaders(executorName));
        }
    }

    private Set<String> distinctExecutorNames(Activity activity) {
        Set<String> names = new LinkedHashSet<>();
        if (activity.getTasks() == null) {
            return names;
        }
        for (Task task : activity.getTasks()) {
            if (task.getExecutorName() != null) {
                names.add(task.getExecutorName());
            }
        }
        return names;
    }

    private Map<String, String> rewriteSecretHeaders(String executorName) {
        String prefix = ("x-odm-" + executorName + "-executor-secret-").toLowerCase(Locale.ROOT);
        Map<String, String> rewritten = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            if (header.getKey() == null) {
                continue;
            }
            String lowerName = header.getKey().toLowerCase(Locale.ROOT);
            if (!lowerName.startsWith(prefix) || lowerName.length() == prefix.length()) {
                continue;
            }
            String suffix = lowerName.substring(prefix.length());
            List<String> values = header.getValue();
            if (values == null || values.isEmpty() || values.get(0) == null) {
                continue;
            }
            rewritten.put("x-odm-" + suffix, values.get(0));
        }
        return rewritten;
    }
}
