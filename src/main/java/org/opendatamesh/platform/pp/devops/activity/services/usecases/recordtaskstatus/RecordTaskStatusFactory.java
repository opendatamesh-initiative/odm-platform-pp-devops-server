package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskstatus;

import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityFactory;
import org.opendatamesh.platform.pp.devops.executor.ExecutorServicesProperties;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RecordTaskStatusFactory {

    @Autowired
    private ActivityService activityService;
    @Autowired
    private ExecutorServicesProperties executorServicesProperties;
    @Autowired
    private TransactionalOutboundPort transactionalOutboundPort;
    @Autowired
    private EntityInitAndDetachService entityInitAndDetachService;
    @Autowired
    private AdvanceActivityFactory advanceActivityFactory;

    public UseCase buildRecordTaskStatus(RecordTaskStatusCommand command, RecordTaskStatusPresenter presenter) {
        return new RecordTaskStatus(
                command,
                presenter,
                new RecordTaskStatusPersistenceOutboundPortImpl(activityService, entityInitAndDetachService),
                new RecordTaskStatusExecutorOutboundPortImpl(executorServicesProperties),
                new RecordTaskStatusAdvanceActivityOutboundPortImpl(advanceActivityFactory),
                transactionalOutboundPort
        );
    }
}
