package org.opendatamesh.platform.pp.devops.executor;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The full-control tasks whose run this DevOps instance is following right now.
 * Cancel uses it to tell a run that will be recorded when it ends from a run nobody follows any more
 * (after a restart, or after the follower stopped on an error), which cancel must then close itself.
 */
@Component
public class RunFollowRegistry {

    private final Set<String> followedTaskUuids = ConcurrentHashMap.newKeySet();

    public void followStarted(String taskUuid) {
        followedTaskUuids.add(taskUuid);
    }

    public void followEnded(String taskUuid) {
        followedTaskUuids.remove(taskUuid);
    }

    public boolean isFollowed(String taskUuid) {
        return followedTaskUuids.contains(taskUuid);
    }
}
