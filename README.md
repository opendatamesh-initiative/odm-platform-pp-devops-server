# ODM Platform DevOps Server

> The product-plane DevOps service for the [Open Data Mesh Platform](https://dpds.opendatamesh.org/) —
> orchestrate data-product activities and tasks across the lifecycle.

This repository is the **v2** DevOps server (Java 21 / Spring Boot 3.5). It ships shared infrastructure (generic CRUD, use-case ports, notification client, Flyway, CI). Anemic CRUD for the Activity root aggregate is the first domain increment. Flyway `V1` defines the Activity aggregate tables (`activities`, nested tasks, logs, and results).

<p align="center">
  <a href="https://github.com/opendatamesh-initiative/odm-platform-pp-devops-server/actions/workflows/ci.yml"><img src="https://github.com/opendatamesh-initiative/odm-platform-pp-devops-server/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=flat-square" alt="License: Apache 2.0"></a>
</p>

<p align="center">
  <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/Java-21-ED8B00.svg?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F.svg?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 3.5"></a>
</p>

<p align="center">
  <a href="#quick-start-local">Quick start</a> ·
  <a href="#run-with-docker">Docker</a> ·
  <a href="#documentation">Documentation</a> ·
  <a href="#contributing">Contributing</a>
</p>

---

## Quick start (local)

**Requirements:** Java **21** · Maven **3.6+** · Docker (for Testcontainers / `mvn verify`)

```bash
git clone https://github.com/opendatamesh-initiative/odm-platform-pp-devops-server.git
cd odm-platform-pp-devops-server

mvn clean install
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile uses an in-memory **H2** database (port **8002**). When the server is up:

| | Endpoint |
|:--|:---------|
| **Swagger UI** | [http://localhost:8002/swagger-ui.html](http://localhost:8002/swagger-ui.html) |
| **OpenAPI** | [http://localhost:8002/v3/api-docs](http://localhost:8002/v3/api-docs) |

API prefix: **`/api/v2/pp/devops/`**.

More detail: [Development](docs/setup/development.md)

---

## Run with Docker

```bash
mvn clean package
docker build -t odm-platform-pp-devops-server .

docker run -p 8080:8080 \
  -e DB_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/odm_devops \
  -e DB_USERNAME=your_username \
  -e DB_PASSWORD=your_password \
  -e PROFILES_ACTIVE=docker \
  odm-platform-pp-devops-server
```

| Guide | Link |
|:------|:-----|
| Step-by-step deploy | [Deployment](docs/setup/deployment.md) |
| Properties to manage | [Configuration](docs/setup/configuration.md) |

---

## Documentation

All guides live under [`docs/`](docs/README.md). Agent onboarding: [`AGENTS.md`](AGENTS.md).

<details open>
<summary><strong>Service</strong></summary>

<br>

| Guide | Description |
|:------|:------------|
| [Activity lifecycle](docs/service/activity-lifecycle.md) | Activities, tasks, and execute → approve → run |
| [Events](docs/service/events.md) | Notification-driven approval and execution |
| [Policy service](docs/service/policy-service.md) | Governance gate vs auto-approve |

</details>

<details open>
<summary><strong>Setup</strong></summary>

<br>

| Guide | Description |
|:------|:------------|
| [Development](docs/setup/development.md) | Build, run, profiles, testing |
| [Deployment](docs/setup/deployment.md) | Containers and external dependencies |
| [Configuration](docs/setup/configuration.md) | DB, Notification, Policy, executors |

</details>

---

## Contributing

Contributions are welcome.

1. Fork the repository and create a feature branch
2. Make your changes with clear commits
3. Open a pull request against the main branch

Bugs, questions, or proposals → [open an issue](https://github.com/opendatamesh-initiative/odm-platform-pp-devops-server/issues).

---

## License

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

Part of the [Open Data Mesh Initiative](https://github.com/opendatamesh-initiative).
