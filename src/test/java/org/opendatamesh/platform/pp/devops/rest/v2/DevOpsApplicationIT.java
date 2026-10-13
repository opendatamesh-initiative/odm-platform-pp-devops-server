package org.opendatamesh.platform.pp.devops.rest.v2;

import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.opendatamesh.platform.pp.devops.DevOpsApplication;
import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.rest.v2.mocks.ExecutorClientFactoryMock;
import org.opendatamesh.platform.pp.devops.rest.v2.mocks.ScriptedExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.util.List;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {
        DevOpsApplication.class,
        TestContainerConfig.class,
        TestConfig.class
})
public abstract class DevOpsApplicationIT {

    @LocalServerPort
    protected String port;

    protected TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.jpa.properties.hibernate.default_schema}")
    private String schema;

    @Autowired
    protected NotificationClient notificationClient;

    @Autowired
    private ExecutorClientFactoryMock executorClientFactory;

    @BeforeEach
    void resetIntegrationState() {
        jdbcTemplate.execute("TRUNCATE TABLE "
                + schema + ".activities_tasks_logs, "
                + schema + ".activities_tasks_results, "
                + schema + ".activities_tasks, "
                + schema + ".activities");
        reset(notificationClient);
        executorClientFactory.reset();
    }

    protected ScriptedExecutor executor() {
        return executorClientFactory.executor();
    }

    protected void clearNotifications() {
        clearInvocations(notificationClient);
    }

    protected List<Object> notifiedEvents() {
        int calls = (int) mockingDetails(notificationClient).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("notifyEvent"))
                .count();
        if (calls == 0) {
            return List.of();
        }
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(notificationClient, times(calls)).notifyEvent(events.capture());
        return List.copyOf(events.getAllValues());
    }

    protected void assertProcessingSucceeded(long notificationId) {
        verify(notificationClient, atLeastOnce()).processingSuccess(notificationId);
    }


    @PostConstruct
    public final void init() {
        rest = new TestRestTemplate();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setConnectionRequestTimeout(java.time.Duration.ofSeconds(30));
        requestFactory.setConnectTimeout(java.time.Duration.ofSeconds(30));
        rest.getRestTemplate().setRequestFactory(requestFactory);
        // add uri template handler because '+' of iso date would not be encoded
        DefaultUriBuilderFactory defaultUriBuilderFactory = new DefaultUriBuilderFactory();
        defaultUriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES);
        rest.setUriTemplateHandler(defaultUriBuilderFactory);
    }

    protected String apiUrl(RoutesV2 route) {
        return apiUrl(route, "");
    }

    protected String apiUrl(RoutesV2 route, String extension) {
        return apiUrlFromString(route.getPath() + extension);
    }

    protected String apiUrlFromString(String routeUrlString) {
        return "http://localhost:" + port + routeUrlString;
    }

    protected String apiUrlOfItem(RoutesV2 route) {
        return apiUrl(route, "/{uuid}");
    }
}