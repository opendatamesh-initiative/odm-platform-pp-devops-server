package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejectexecution;

import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.client.notification.NotificationClient;
import org.opendatamesh.platform.pp.devops.executor.ExecutorSecretsStore;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityMapper;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RejectActivityExecutionFactory {

    @Autowired
    private ActivityService activityService;
    @Autowired
    private ActivityMapper activityMapper;
    @Autowired
    private NotificationClient notificationClient;
    @Autowired
    private ExecutorSecretsStore executorSecretsStore;
    @Autowired
    private TransactionalOutboundPort transactionalOutboundPort;
    @Autowired
    private EntityInitAndDetachService entityInitAndDetachService;

    public UseCase buildRejectActivityExecution(RejectActivityExecutionCommand command,
                                               RejectActivityExecutionPresenter presenter) {
        return new RejectActivityExecution(
                command,
                presenter,
                new RejectActivityExecutionPersistenceOutboundPortImpl(activityService, entityInitAndDetachService),
                new RejectActivityExecutionNotificationOutboundPortImpl(notificationClient, activityMapper),
                new RejectActivityExecutionSecretsOutboundPortImpl(executorSecretsStore),
                transactionalOutboundPort
        );
    }
}
