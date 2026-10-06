# Service guides

This service's domain is the Activity aggregate: a named activity of a data product version, owning tasks and their logs and results.

Full control is the way an activity runs. The user interface asks DevOps to execute a named activity. DevOps creates that activity and its tasks as pending, then asks for the activity to be approved and, after that, for each task to be approved. Tasks run one after another, in the order they were given, on the executor named for each task. DevOps follows each run until it ends and keeps its log. When every task succeeds, the activity succeeds. The first task that fails fails the activity, and every task that has not started is canceled.

A user can cancel an open activity. Tasks that have not started are canceled. A task on the executor is asked to stop, and the call finishes when the activity itself has succeeded, failed, or canceled. A pipeline that already finished keeps that result. A refusal from Policy fails the activity and is not a user cancel.

While the Policy service is inactive, each approval request is approved automatically. See [Policy service](policy-service.md). The messages that move the activity along are described in [Events](events.md).
