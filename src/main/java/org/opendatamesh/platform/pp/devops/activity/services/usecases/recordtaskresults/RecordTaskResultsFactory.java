package org.opendatamesh.platform.pp.devops.activity.services.usecases.recordtaskresults;

import org.opendatamesh.platform.pp.devops.activity.services.core.ActivityService;
import org.opendatamesh.platform.pp.devops.utils.services.EntityInitAndDetachService;
import org.opendatamesh.platform.pp.devops.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.devops.utils.usecases.UseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RecordTaskResultsFactory {

    @Autowired
    private ActivityService activityService;
    @Autowired
    private TransactionalOutboundPort transactionalOutboundPort;
    @Autowired
    private EntityInitAndDetachService entityInitAndDetachService;

    public UseCase buildRecordTaskResults(RecordTaskResultsCommand command, RecordTaskResultsPresenter presenter) {
        return new RecordTaskResults(
                command,
                presenter,
                new RecordTaskResultsPersistenceOutboundPortImpl(activityService, entityInitAndDetachService),
                transactionalOutboundPort
        );
    }
}
