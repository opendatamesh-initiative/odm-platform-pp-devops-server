package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityFactory;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClientFactory;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
import org.opendatamesh.platform.pp.devops.executor.RunFollowRegistry;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskMapper;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CancelActivityFactory {

    @Autowired
    private ActivityService activityService;
    @Autowired
    private TransactionalOutboundPort transactionalOutboundPort;
    @Autowired
    private AdvanceActivityFactory advanceActivityFactory;
    @Autowired
    private ExecutorClientFactory executorClientFactory;
    @Autowired
    private ExecutorServicesProperties executorServicesProperties;
    @Autowired
    private RunFollowRegistry runFollowRegistry;
    @Autowired
    private EntityInitAndDetachService entityInitAndDetachService;
    @Autowired
    private TaskMapper taskMapper;

    public UseCase buildCancelActivity(CancelActivityCommand command, CancelActivityPresenter presenter) {
        return new CancelActivity(
                command,
                presenter,
                new CancelActivityPersistenceOutboundPortImpl(activityService, entityInitAndDetachService),
                new CancelActivityAdvanceActivityOutboundPortImpl(advanceActivityFactory),
                new CancelActivityExecutorOutboundPortImpl(executorClientFactory, executorServicesProperties, taskMapper, runFollowRegistry),
                new CancelActivityPollingOutboundPortImpl(executorServicesProperties),
                transactionalOutboundPort
        );
    }
}
