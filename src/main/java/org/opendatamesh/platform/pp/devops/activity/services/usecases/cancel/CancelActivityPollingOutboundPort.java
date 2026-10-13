package org.opendatamesh.platform.pp.devops.activity.services.usecases.cancel;

interface CancelActivityPollingOutboundPort {
    int maxStatusReads();

    void waitOnePollInterval();

    void waitOneSecond();
}
