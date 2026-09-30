package org.opendatamesh.platform.pp.devops.activity.services.usecases.execute;

import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.executor.ExecutorSecretsStore;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityMapper;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class ExecuteActivityFactory {

    @Autowired
    private ActivityService activityService;
    @Autowired
    private ActivityMapper activityMapper;
    @Autowired
    private NotificationClient notificationClient;
    @Autowired
    private ExecutorServicesProperties executorServicesProperties;
    @Autowired
    private ExecutorSecretsStore executorSecretsStore;
    @Autowired
    private TransactionalOutboundPort transactionalOutboundPort;

    public UseCase buildExecuteActivity(ExecuteActivityCommand command, ExecuteActivityPresenter presenter, HttpHeaders headers) {
        return new ExecuteActivity(
                command,
                presenter,
                new ExecuteActivityPersistenceOutboundPortImpl(activityService),
                new ExecuteActivityExecutorOutboundPortImpl(executorServicesProperties),
                new ExecuteActivitySecretsOutboundPortImpl(executorSecretsStore, headers),
                new ExecuteActivityNotificationOutboundPortImpl(notificationClient, activityMapper),
                transactionalOutboundPort
        );
    }
}
