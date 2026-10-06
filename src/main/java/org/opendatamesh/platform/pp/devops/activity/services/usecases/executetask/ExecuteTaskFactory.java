package org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask;

import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityFactory;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClientFactory;
import org.opendatamesh.platform.pp.devops.executor.ExecutorPollingProperties;
import org.opendatamesh.platform.pp.devops.executor.ExecutorSecretsProperties;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.TaskMapper;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExecuteTaskFactory {

    @Autowired
    private ActivityService activityService;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private ExecutorServicesProperties executorServicesProperties;
    @Autowired
    private ExecutorClientFactory executorClientFactory;
    @Autowired
    private ExecutorPollingProperties executorPollingProperties;
    @Autowired
    private ExecutorSecretsProperties executorSecretsProperties;
    @Autowired
    private TransactionalOutboundPort transactionalOutboundPort;
    @Autowired
    private EntityInitAndDetachService entityInitAndDetachService;
    @Autowired
    private AdvanceActivityFactory advanceActivityFactory;

    public UseCase buildExecuteTask(ExecuteTaskCommand command, ExecuteTaskPresenter presenter) {
        return new ExecuteTask(
                command,
                presenter,
                new ExecuteTaskPersistenceOutboundPortImpl(activityService, entityInitAndDetachService),
                new ExecuteTaskPipelineParametersOutboundPortImpl(activityService, transactionalOutboundPort),
                new ExecuteTaskExecutorOutboundPortImpl(
                        executorServicesProperties,
                        executorClientFactory,
                        executorPollingProperties,
                        executorSecretsProperties,
                        taskMapper
                ),
                new ExecuteTaskAdvanceActivityOutboundPortImpl(advanceActivityFactory),
                transactionalOutboundPort
        );
    }
}
