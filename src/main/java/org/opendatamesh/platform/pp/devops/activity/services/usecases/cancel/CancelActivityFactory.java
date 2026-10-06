package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.activity.services.usecases.advance.AdvanceActivityFactory;
import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClientFactory;
import org.opendatamesh.platform.pp.devops.executor.ExecutorPollingProperties;
import org.opendatamesh.platform.pp.devops.executor.ExecutorSecretsProperties;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

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
    private ExecutorPollingProperties executorPollingProperties;
    @Autowired
    private ExecutorSecretsProperties executorSecretsProperties;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityInitAndDetachService entityInitAndDetachService;

    public UseCase buildCancelActivity(CancelActivityCommand command, CancelActivityPresenter presenter) {
        return new CancelActivity(
                command,
                presenter,
                new CancelActivityPersistenceOutboundPortImpl(activityService, transactionManager, entityInitAndDetachService),
                new CancelActivityAdvanceActivityOutboundPortImpl(advanceActivityFactory),
                new CancelActivityExecutorOutboundPortImpl(executorClientFactory),
                new CancelActivityPollingOutboundPortImpl(executorPollingProperties, executorSecretsProperties),
                transactionalOutboundPort
        );
    }
}
