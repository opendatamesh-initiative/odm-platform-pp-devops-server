# Service guides

This service's domain is the **Activity** root aggregate: a named activity of a data product version
(`dataProductVersionUuid`, plus `dataProductFqn` and `dataProductVersionTag`), owning **tasks** and their
**logs** and **results**.

Anemic CRUD is this increment: one `/api/v2/pp/devops/activities` collection; tasks change only through the
activity POST and PUT; process, execute, polling, and notifications are later.

Add a guide here when behaviour beyond CRUD lands.
