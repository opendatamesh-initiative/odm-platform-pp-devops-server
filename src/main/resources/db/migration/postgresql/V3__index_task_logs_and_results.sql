-- Loading a task's logs and results filters on task_uuid. Without these indexes PostgreSQL scans the whole
-- table, and under SERIALIZABLE a full scan conflicts with every concurrent write to it, even for another activity.
create index if not exists activities_tasks_logs_task_uuid_idx
    on activities_tasks_logs (task_uuid);

create index if not exists activities_tasks_results_task_uuid_idx
    on activities_tasks_results (task_uuid);
