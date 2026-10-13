package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtasklogs;

import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RecordTaskLogsFactory {

    @Autowired
    private ActivityService activityService;
    @Autowired
    private ExecutorServicesProperties executorServicesProperties;
    @Autowired
    private TransactionalOutboundPort transactionalOutboundPort;
    @Autowired
    private EntityInitAndDetachService entityInitAndDetachService;

    public UseCase buildRecordTaskLogs(RecordTaskLogsCommand command, RecordTaskLogsPresenter presenter) {
        return new RecordTaskLogs(
                command,
                presenter,
                new RecordTaskLogsPersistenceOutboundPortImpl(activityService, entityInitAndDetachService),
                new RecordTaskLogsExecutorOutboundPortImpl(executorServicesProperties),
                transactionalOutboundPort
        );
    }
}
