package org.opendatamesh.platform.pp.devops.executor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Validated
@ConfigurationProperties(prefix = "odm.utility-plane")
public class ExecutorServicesProperties {

    @Valid
    private Map<String, ExecutorServiceProperties> executorServices = new LinkedHashMap<>();

    public Map<String, ExecutorServiceProperties> getExecutorServices() {
        return executorServices;
    }

    public void setExecutorServices(Map<String, ExecutorServiceProperties> executorServices) {
        this.executorServices = executorServices == null ? new LinkedHashMap<>() : executorServices;
    }

    public Optional<ExecutorInfo> findExecutor(String name) {
        ExecutorServiceProperties properties = propertiesOf(name);
        if (properties == null) {
            return Optional.empty();
        }
        return Optional.of(new ExecutorInfo(name, properties.getExecutionMode()));
    }

    public Optional<String> findAddress(String name) {
        ExecutorServiceProperties properties = propertiesOf(name);
        if (properties == null || properties.getAddress() == null) {
            return Optional.empty();
        }
        return Optional.of(properties.getAddress());
    }

    private ExecutorServiceProperties propertiesOf(String name) {
        if (name == null || executorServices == null) {
            return null;
        }
        return executorServices.get(name);
    }

    public static class ExecutorServiceProperties {

        @NotBlank
        private String address;

        @NotNull
        private ExecutionMode executionMode;

        public ExecutorServiceProperties() {
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public ExecutionMode getExecutionMode() {
            return executionMode;
        }

        public void setExecutionMode(ExecutionMode executionMode) {
            this.executionMode = executionMode;
        }
    }
}
