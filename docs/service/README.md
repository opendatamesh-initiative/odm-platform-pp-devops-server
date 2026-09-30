# Service guides

This service's domain is the Activity aggregate: a named activity of a data product version, owning tasks and their logs and results.

Full control is the way an activity runs. The user interface asks DevOps to execute a named activity. DevOps creates that activity and its tasks as pending, then asks for the activity to be approved and, after that, for each task to be approved. Tasks run one after another, in the order they were given, on the executor named for each task. DevOps follows each run until it ends and keeps its log. When every task succeeds, the activity succeeds. The first task that fails fails the activity, and every task that has not started is canceled.

While the Policy service is inactive, each approval request is approved automatically. See [Policy service](policy-service.md). The messages that move the activity along are described in [Events](events.md).

Add a guide here when behaviour beyond this process lands.
