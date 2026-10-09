package org.opendatamesh.platform.pp.devops.activity.services.usecases.requesttaskexecution;

import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityMapper;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskMapper;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RequestTaskExecutionFactory {

    @Autowired
    private ActivityService activityService;
    @Autowired
    private ActivityMapper activityMapper;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private NotificationClient notificationClient;
    @Autowired
    private ExecutorServicesProperties executorServicesProperties;
    @Autowired
    private TransactionalOutboundPort transactionalOutboundPort;
    @Autowired
    private EntityInitAndDetachService entityInitAndDetachService;

    public UseCase buildRequestTaskExecution(RequestTaskExecutionCommand command, RequestTaskExecutionPresenter presenter) {
        return new RequestTaskExecution(
                command,
                presenter,
                new RequestTaskExecutionPersistenceOutboundPortImpl(activityService, entityInitAndDetachService),
                new RequestTaskExecutionExecutorOutboundPortImpl(executorServicesProperties),
                new RequestTaskExecutionNotificationOutboundPortImpl(notificationClient, activityMapper, taskMapper),
                transactionalOutboundPort
        );
    }
}
