package org.opendatamesh.platform.pp.devops.client.executor;

import org.opendatamesh.platform.pp.devops.executor.ExecutorSecretsStore;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
import org.opendatamesh.platform.pp.devops.utils.client.RestUtilsFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorClientConfig {

    @Bean
    ExecutorClientFactory executorClientFactory(RestTemplateBuilder restTemplateBuilder,
                                                ExecutorServicesProperties executorServicesProperties,
                                                ExecutorSecretsStore executorSecretsStore) {
        return new ExecutorClientFactoryImpl(
                executorServicesProperties,
                executorSecretsStore,
                RestUtilsFactory.getRestUtils(restTemplateBuilder.build())
        );
    }
}
