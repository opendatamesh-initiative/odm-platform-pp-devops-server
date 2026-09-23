create table if not exists activities (
    uuid                       varchar(36) primary key,
    data_product_version_uuid  varchar(36) not null,
    data_product_fqn           varchar(255),
    data_product_version_tag   varchar(255),
    name                       varchar(255) not null,
    sort_order                 integer,
    status                     varchar(255) not null,
    started_at                 timestamp,
    finished_at                timestamp,
    created_at                 timestamp,
    updated_at                 timestamp
);

create table if not exists activities_tasks (
    uuid              varchar(36) primary key,
    activity_uuid     varchar(36) not null references activities(uuid) on delete cascade,
    name              varchar(255),
    description       text,
    sort_order        integer,
    status            varchar(255) not null,
    provider_run_id   varchar(255),
    started_at        timestamp,
    finished_at       timestamp,
    created_at        timestamp,
    updated_at        timestamp
);

create table if not exists activities_task_logs (
    uuid          varchar(36) primary key,
    task_uuid     varchar(36) not null references activities_tasks(uuid) on delete cascade,
    content       text not null,
    generated_at  timestamp,
    created_at    timestamp,
    updated_at    timestamp
);

create table if not exists activities_task_results (
    uuid          varchar(36) primary key,
    task_uuid     varchar(36) not null references activities_tasks(uuid) on delete cascade,
    content       text not null,
    generated_at  timestamp,
    created_at    timestamp,
    updated_at    timestamp
);

create index if not exists activities_data_product_version_uuid_name_status_idx
    on activities (data_product_version_uuid, name, status);

create index if not exists activities_tasks_activity_uuid_status_idx
    on activities_tasks (activity_uuid, status);

create index if not exists activities_tasks_provider_run_id_idx
    on activities_tasks (provider_run_id);
