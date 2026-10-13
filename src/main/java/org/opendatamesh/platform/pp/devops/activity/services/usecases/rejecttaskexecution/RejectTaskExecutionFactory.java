package org.opendatamesh.platform.pp.devops.activity.services.usecases.rejecttaskexecution;

import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityFactory;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RejectTaskExecutionFactory {

    @Autowired
    private ActivityService activityService;
    @Autowired
    private TransactionalOutboundPort transactionalOutboundPort;
    @Autowired
    private EntityInitAndDetachService entityInitAndDetachService;
    @Autowired
    private AdvanceActivityFactory advanceActivityFactory;

    public UseCase buildRejectTaskExecution(RejectTaskExecutionCommand command, RejectTaskExecutionPresenter presenter) {
        return new RejectTaskExecution(
                command,
                presenter,
                new RejectTaskExecutionPersistenceOutboundPortImpl(activityService, entityInitAndDetachService),
                new RejectTaskExecutionAdvanceActivityOutboundPortImpl(advanceActivityFactory),
                transactionalOutboundPort
        );
    }
}
