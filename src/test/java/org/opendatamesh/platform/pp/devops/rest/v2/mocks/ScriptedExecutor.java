package org.opendatamesh.platform.pp.devops.rest.v2.mocks;

import org.opendatamesh.platform.pp.devops.client.executor.ExecutorClient;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskLogsRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskRunCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartCommandRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStartResultRes;
import org.opendatamesh.platform.pp.devops.client.executor.resources.ExecutorTaskStatusRes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One scripted executor client. Tests set the start run id, the status sequence, the logs,
 * and whether cancel fails. The factory records which executor names were opened.
 */
public class ScriptedExecutor implements ExecutorClient {

    private final List<String> openedExecutors = Collections.synchronizedList(new ArrayList<>());
    private final List<ExecutorTaskStartCommandRes> starts = Collections.synchronizedList(new ArrayList<>());
    private final List<String> canceledRunIds = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger statusReads = new AtomicInteger();
    private final List<ExecutorTaskStatusRes> statuses = new ArrayList<>();

    private RuntimeException startFailure;
    private String fixedRunId;
    private int statusCursor;
    private ExecutorTaskLogsRes logsResponse;
    private boolean logsConfigured;
    private RuntimeException logsFailure;
    private RuntimeException cancelFailure;
    private CountDownLatch holdEntered;
    private CountDownLatch holdRelease;

    public synchronized void reset() {
        openedExecutors.clear();
        starts.clear();
        canceledRunIds.clear();
        statusReads.set(0);
        statuses.clear();
        statusCursor = 0;
        startFailure = null;
        fixedRunId = null;
        logsResponse = null;
        logsConfigured = false;
        logsFailure = null;
        cancelFailure = null;
        holdEntered = null;
        holdRelease = null;
    }

    public ScriptedExecutor statuses(ExecutorTaskStatusRes... sequence) {
        synchronized (this) {
            statuses.clear();
            statuses.addAll(List.of(sequence));
            statusCursor = 0;
        }
        return this;
    }

    public ScriptedExecutor logs(String content) {
        ExecutorTaskLogsRes logs = new ExecutorTaskLogsRes();
        logs.setContent(content);
        logs.setGeneratedAt(new Date(1_700_000_000_000L));
        this.logsResponse = logs;
        this.logsConfigured = true;
        return this;
    }

    public ScriptedExecutor startRunId(String runId) {
        this.fixedRunId = runId;
        return this;
    }

    public ScriptedExecutor startThrows(RuntimeException failure) {
        this.startFailure = failure;
        return this;
    }

    public ScriptedExecutor logsThrow(RuntimeException failure) {
        this.logsFailure = failure;
        return this;
    }

    public ScriptedExecutor cancelThrows(RuntimeException failure) {
        this.cancelFailure = failure;
        return this;
    }

    public ScriptedExecutor holdStatus(CountDownLatch entered, CountDownLatch release) {
        this.holdEntered = entered;
        this.holdRelease = release;
        return this;
    }

    public void opened(String executorName) {
        openedExecutors.add(executorName);
    }

    public List<String> openedExecutors() {
        synchronized (openedExecutors) {
            return List.copyOf(openedExecutors);
        }
    }

    public List<ExecutorTaskStartCommandRes> starts() {
        synchronized (starts) {
            return List.copyOf(starts);
        }
    }

    public int statusReads() {
        return statusReads.get();
    }

    public List<String> canceledRunIds() {
        synchronized (canceledRunIds) {
            return List.copyOf(canceledRunIds);
        }
    }

    public boolean cancelCalled() {
        return !canceledRunIds().isEmpty();
    }

    @Override
    public ExecutorTaskStartResultRes startTask(ExecutorTaskStartCommandRes command) {
        starts.add(command);
        if (startFailure != null) {
            throw startFailure;
        }
        ExecutorTaskStartResultRes result = new ExecutorTaskStartResultRes();
        result.setProviderRunId(fixedRunId != null ? fixedRunId : "run-" + UUID.randomUUID());
        return result;
    }

    @Override
    public void cancelTask(ExecutorTaskRunCommandRes command) {
        canceledRunIds.add(command.getProviderRunId());
        if (cancelFailure != null) {
            throw cancelFailure;
        }
    }

    @Override
    public ExecutorTaskStatusRes getTaskStatus(ExecutorTaskRunCommandRes command) {
        statusReads.incrementAndGet();
        CountDownLatch entered;
        CountDownLatch release;
        synchronized (this) {
            entered = holdEntered;
            release = holdRelease;
            holdEntered = null;
            holdRelease = null;
        }
        if (entered != null && release != null) {
            entered.countDown();
            try {
                if (!release.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("status read was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while holding the status read", interrupted);
            }
            ExecutorTaskStatusRes held = new ExecutorTaskStatusRes();
            held.setStatus("SUCCEEDED");
            return held;
        }
        synchronized (this) {
            if (statuses.isEmpty()) {
                ExecutorTaskStatusRes succeeded = new ExecutorTaskStatusRes();
                succeeded.setStatus("SUCCEEDED");
                return succeeded;
            }
            int index = Math.min(statusCursor, statuses.size() - 1);
            statusCursor++;
            return statuses.get(index);
        }
    }

    @Override
    public ExecutorTaskLogsRes getTaskLogs(ExecutorTaskRunCommandRes command) {
        if (logsFailure != null) {
            throw logsFailure;
        }
        if (!logsConfigured) {
            return null;
        }
        return logsResponse;
    }
}
