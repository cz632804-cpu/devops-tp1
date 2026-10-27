# DevOps TP01 + TP02 + TP03 — Docker, CI/CD and Ansible

## Project overview

This project implements a three-tier application using Docker, with a Vue.js frontend:

- Vue.js frontend (from the teacher's [`devops-front`](https://github.com/takima-training/devops-front) repository), served by Apache HTTP Server 2.4
- Spring Boot 3.4.5 with Java 21 as the backend
- PostgreSQL 17.2 as the database

TP01 deploys the application with Docker Compose and publishes images to Docker Hub. TP02 adds automated testing, SonarQube Cloud quality analysis, and GitHub Actions CI/CD in **this same repository**. TP03 introduces Ansible deployment to a remote server. The teacher-provided Vue.js frontend has also been integrated and tested locally; deployment of this new frontend to the remote server is not yet verified.

Repository: https://github.com/cz632804-cpu/devops-tp1

## 1. Database

The database is initialized using two SQL files:

- `database/01-CreateScheme.sql`
- `database/02-InsertData.sql`

The database contains three departments (IRC, ETI, CGP) and four students (Eli, Emma, Jack, Aude).

### 1-1. Why use environment variables instead of storing passwords in the Dockerfile?

Environment variables allow configuration to be provided when starting a container without embedding credentials in the Docker image.

This makes the image reusable across development, testing and production environments.

However, environment variables are not fully secure because they may appear in container inspection information. Production systems should use dedicated secret management.

### 1-2. Why attach a volume to PostgreSQL?

A Docker volume stores database data independently from the container lifecycle.

Without persistent storage, removing a PostgreSQL container can delete its data. With a named volume, the container can be recreated while preserving the database.

We tested persistence by recreating the database container and verifying that the four student records remained available.

### 1-3. Document the database Dockerfile and commands

Dockerfile:

```dockerfile
FROM postgres:17.2-alpine

COPY 01-CreateScheme.sql /docker-entrypoint-initdb.d/
COPY 02-InsertData.sql /docker-entrypoint-initdb.d/
```

Build (standalone TP01 database exercise):

```bash
docker build -t my-database ./database
```

Create the network and volume:

```bash
docker network create app-network
docker volume create devops-postgres-data
```

Run PostgreSQL:

```bash
docker run -d \
  --name my-database-container \
  --network app-network \
  -e POSTGRES_DB=db \
  -e POSTGRES_USER=usr \
  -e POSTGRES_PASSWORD=pwd \
  -v devops-postgres-data:/var/lib/postgresql/data \
  my-database
```

Check the data:

```bash
docker exec my-database-container \
  psql -U usr -d db -c "SELECT * FROM students;"
```

These credentials are examples for local coursework only.

## 2. Backend API

### 1-4. Why use a multistage Docker build? Explain the Dockerfile

A multistage build separates the compilation environment from the runtime environment.

It reduces the runtime image size, avoids including Maven and the JDK in the final image, and limits unnecessary build tools in production.

Our backend Dockerfile contains two stages.

**Build stage**

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS myapp-build
```

Uses Java 21 JDK to compile the Spring Boot application.

```dockerfile
ENV MYAPP_HOME=/opt/myapp
WORKDIR $MYAPP_HOME
```

Defines the working directory.

```dockerfile
RUN apk add --no-cache maven
```

Installs Maven.

```dockerfile
COPY pom.xml .
RUN mvn dependency:go-offline -B
```

Copies Maven configuration and downloads dependencies into a cacheable Docker layer.

```dockerfile
COPY src ./src
RUN mvn package -DskipTests
```

Copies application source code and packages the executable JAR. Tests should be run separately before publishing the image.

**Runtime stage**

```dockerfile
FROM eclipse-temurin:21-jre-alpine
```

Uses the smaller Java Runtime Environment.

```dockerfile
ENV MYAPP_HOME=/opt/myapp
WORKDIR $MYAPP_HOME
```

Defines the runtime working directory.

```dockerfile
COPY --from=myapp-build \
  $MYAPP_HOME/target/*.jar \
  $MYAPP_HOME/myapp.jar
```

Copies the compiled application from the build stage.

```dockerfile
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "myapp.jar"]
```

Documents the application port and starts Spring Boot.

### Backend endpoints

The initial Greeting API is available at:

```http
GET /
```

Expected response:

```json
{"id":1,"content":"Hello, World!"}
```

The database-backed API provides the following Spring Boot routes (the reverse proxy exposes them with the `/api` prefix):

```http
GET /departments
GET /departments/{name}
GET /departments/{name}/students
GET /students/{id}
POST /students
```

The `GET /departments/{name}` and `POST /students` endpoints were added to support the teacher's Vue frontend. The local department and student listing was verified in the browser; the Add Student action should be checked separately after rebuilding the backend.

For example, the IRC endpoint returns student information with the associated department.

The backend connects to PostgreSQL through Spring JDBC using Docker service discovery.

## 3. Apache HTTP Server

### 1-5. Why do we need a reverse proxy?

A reverse proxy provides a single public entry point for incoming HTTP requests.

It forwards requests to internal backend services, hides internal ports, centralizes routing and can provide HTTPS termination or load balancing.

In this project, Apache listens on port 80 inside its container and forwards API requests to the Spring Boot service on port 8080.

The Docker Compose configuration publishes Apache on host port 8081.

The Vue.js frontend is available at:

```text
http://localhost:8081/
```

The API is accessible through Apache under `/api/`, for example:

```text
http://localhost:8081/api/departments
http://localhost:8081/api/departments/IRC/students
```

Apache removes the `/api/` prefix when forwarding to `http://backend:8080/`. A fallback rewrite to `/index.html` allows Vue Router routes such as `/departments/IRC` to work on refresh.

## 4. Docker Compose

### 1-6. Why is Docker Compose important?

Docker Compose allows multiple related containers to be configured and managed as one application.

Instead of manually creating networks, volumes and containers, we define the services in a single YAML file.

It improves reproducibility, simplifies startup and shutdown, and supports service dependencies, environment variables, health checks and persistent storage.

### 1-7. Important Docker Compose commands

Validate the configuration:

```bash
docker compose config
```

Build and start the application:

```bash
docker compose up -d --build
```

List services:

```bash
docker compose ps
```

View logs:

```bash
docker compose logs --tail=30
```

Execute a command inside a service:

```bash
docker compose exec database psql -U usr -d db
```

Restart services:

```bash
docker compose restart
```

Stop services:

```bash
docker compose stop
```

Remove containers and the Compose network:

```bash
docker compose down
```

Warning: `docker compose down -v` also removes the Compose-managed named volumes.

### 1-8. Document the Docker Compose file

The file `docker-compose.yml` defines three services.

**Local environment configuration**

Copy `.env.example` to `.env` before starting the application, and set `POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD`. Docker Compose interpolates these values into the PostgreSQL and Spring Boot environment configuration. The `.env` file must be ignored by Git and must not be committed. `.env.example` is a shareable template, not a production secret store.

```bash
cp .env.example .env
# Edit .env to set values appropriate for your local environment.
docker compose config --quiet
docker compose up -d --build
```

The original demo values (`db`, `usr`, `pwd`) are intended only for a local teaching environment. Environment variables can still be exposed through container inspection, so production deployments should use managed secrets.

**database**

- Builds a PostgreSQL 17.2 image.
- Initializes database tables and sample records.
- Uses a named volume for persistent storage.
- Uses `pg_isready` as a health check.
- Connects to the private Compose network.

**backend**

- Builds the Spring Boot application using Java 21.
- Connects to PostgreSQL using `database:5432`.
- Receives JDBC connection settings through environment variables.
- Waits for the database health check.
- Exposes port 8080 only inside Docker networking.

**http-server**

- Builds the teacher-provided Vue.js frontend in a Node.js build stage.
- Copies the compiled `dist/` files into an Apache HTTP Server runtime image.
- Serves the Vue.js application and forwards `/api/` requests to `backend:8080`.
- Maps host port 8081 to container port 80.
- Depends on the backend service.

**networks**

All three services communicate through an isolated Docker bridge network.

**volumes**

A named PostgreSQL volume preserves the database across container recreation.

Architecture:

```text
Browser (Vue.js frontend)
  |
  | localhost:8081
  v
Apache HTTP Server (Vue static files + /api reverse proxy)
  |
  | /api/* -> backend:8080/*
  v
Spring Boot API
  |
  | database:5432
  v
PostgreSQL
  |
  v
Persistent Docker Volume
```

## 5. Docker Hub

### 1-9. Document publication commands and images

Docker Hub username: `nifdgg`

Published images:

- https://hub.docker.com/r/nifdgg/tp01-database
- https://hub.docker.com/r/nifdgg/tp01-backend
- https://hub.docker.com/r/nifdgg/tp01-http-server

Tag the local images:

```bash
docker tag tp01-database:latest nifdgg/tp01-database:1.0
docker tag tp01-backend:latest nifdgg/tp01-backend:1.0
docker tag tp01-http-server:latest nifdgg/tp01-http-server:1.0
```

Publish the images:

```bash
docker login

docker push nifdgg/tp01-database:1.0
docker push nifdgg/tp01-backend:1.0
docker push nifdgg/tp01-http-server:1.0
```

Download the images:

```bash
docker pull nifdgg/tp01-database:1.0
docker pull nifdgg/tp01-backend:1.0
docker pull nifdgg/tp01-http-server:1.0
```

Version tags such as `1.0` identify a specific image release more clearly than `latest`.

### 1-10. Why publish images to an online repository?

Publishing Docker images to an online registry makes them available to other developers, servers and deployment environments.

It allows the same tested image to be downloaded and deployed without rebuilding it on every machine.

Online registries also help with version management, collaboration, CI/CD automation, and reproducible deployments.

Docker Hub is a public image registry, but organizations may also use private registries to control access.

## 6. Testing and verification

Run the application:

```bash
docker compose up -d --build
```

Verify the services:

```bash
docker compose ps
```

Test the backend through Apache:

```bash
curl http://localhost:8081/api/departments
curl http://localhost:8081/api/departments/IRC
curl http://localhost:8081/api/departments/IRC/students
```

Open the Vue.js frontend and select a department:

```text
http://localhost:8081/
http://localhost:8081/departments/IRC
```

The frontend's department list and student list were verified locally. Also verify student creation through the **Add** button after rebuilding the Spring Boot image; it is not claimed as verified here.

Verify PostgreSQL:

```bash
docker compose exec database \
  psql -U usr -d db -c "SELECT * FROM students;"
```

The setup provides container networking, persistent storage, health checks, Spring Boot API endpoints and an Apache reverse proxy.

## 7. TP02 — GitHub Actions CI/CD

The GitHub Actions workflows were added to the **original TP01 repository**, rather than relying on the separate fork used for the introductory Git/GitHub TD.

### CI workflow — `.github/workflows/ci.yml`

The workflow is named **CI DevOps** and is triggered by pushes and pull requests targeting `main` and `develop`.

Its `test-backend` job runs on `ubuntu-24.04` and performs the following steps:

1. Check out the repository with complete Git history (`fetch-depth: 0`) for SonarQube analysis.
2. Install Temurin Java 21 and cache Maven dependencies using `backend/simple-api/pom.xml`.
3. Run `mvn -B clean verify` from `backend/simple-api`, executing the available Java tests, including the PostgreSQL Testcontainers integration test.
4. For **pushes to `main` only**, run SonarQube Cloud analysis and wait for the Quality Gate result (`sonar.qualitygate.wait=true`).

The SonarQube analysis is limited to pushes on `main` because the previous free-plan setup did not permit analysis of non-main branches. `develop` still receives Maven build and test checks; pull requests run those checks without the SonarQube Maven analysis step.

### Testcontainers integration test

The Spring Boot backend has test dependencies for `org.testcontainers:junit-jupiter` and `org.testcontainers:postgresql`.

The integration test is in:

`backend/simple-api/src/test/java/fr/takima/training/simpleapi/PostgresIntegrationTests.java`

The test starts an isolated `postgres:17.2-alpine` container, connects the Spring application to that container using `@DynamicPropertySource`, and executes `SELECT 1` through `JdbcTemplate` to verify actual database connectivity. This database is independent of the PostgreSQL service started by the TP01 Docker Compose file.

```bash
cd backend/simple-api
mvn clean verify
```

The successful local run reported one Testcontainers integration test with no failures or errors. The CI workflow also completed successfully after that test was committed. **The integration test checks database connectivity; it does not yet exhaustively test every student/department API endpoint.**

Testcontainers requires an available Docker Engine. For compatibility with the Docker Desktop environment used during this TP, the test resources include `docker-java.properties` with `api.version=1.44`. This is a compatibility workaround, not a requirement for all environments.

### SonarQube Cloud analysis and Quality Gate

SonarQube Cloud project:

https://sonarcloud.io/dashboard?id=cz632804-cpu_devops-tp1&branch=main

Configuration:

- Project key: `cz632804-cpu_devops-tp1`
- Organization key: `cz632804-cpu`
- Maven module: `backend/simple-api`
- Host: `https://sonarcloud.io`
- Analysis method: GitHub Actions (SonarQube's automatic analysis is disabled)

The CI invokes the SonarQube Maven scanner after `mvn clean verify` and waits for the Quality Gate. A non-passing Quality Gate causes the CI job to fail and thereby prevents the CD publishing job from running.

The scanner authenticates using the repository secret `SONAR_TOKEN`. Neither the token nor a production password is stored in the workflow YAML or committed to the repository.

### CD workflow — `.github/workflows/cd.yml`

The workflow is named **CD DevOps**. It is triggered by a `workflow_run` event when **CI DevOps** completes for the `main` branch. The publishing job has additional conditions requiring a successful CI run that was initiated by a push to `main`.

CD checks out the **validated commit SHA** from the successful CI, logs into Docker Hub using repository secrets, and builds/pushes three images with the `latest` tag:

| Docker image | Docker build context |
| --- | --- |
| `nifdgg/tp01-backend:latest` | `./backend/simple-api` |
| `nifdgg/tp01-database:latest` | `./database` |
| `nifdgg/tp01-http-server:latest` | repository root (`.`), Dockerfile `./http-server/Dockerfile` |

Publishing uses `docker/build-push-action@v6`. The images can be pulled on a different machine without rebuilding their Dockerfiles.

This repository's GitHub Actions secrets are:

| Secret | Purpose |
| --- | --- |
| `DOCKERHUB_USERNAME` | Docker Hub account used to publish images |
| `DOCKERHUB_TOKEN` | Docker Hub authentication token with push access |
| `SONAR_TOKEN` | SonarQube Cloud analysis authentication token |

Set these in **GitHub repository → Settings → Secrets and variables → Actions**. Secrets belong to this repository and are not automatically copied from a different fork.

### Branch behavior and validation

| Event | Maven tests | SonarQube Maven analysis | Docker image publishing |
| --- | --- | --- | --- |
| Push to `main` | Yes | Yes; waits for Quality Gate | Only if CI succeeds |
| Push to `develop` | Yes | No | No |
| Pull request to `main` or `develop` | Yes | No | No |

Observed successful GitHub Actions executions for this repository:

- Initial CI/CD integration: CI #1 and CD #1 — success.
- PostgreSQL Testcontainers integration: CI #2 and CD #2 — success.
- SonarQube Cloud and Quality Gate integration: CI #3 and CD #3 — success (CI commit `aef5fad`).

The **`develop` branch behavior is defined in the workflows but has not yet been separately verified in this repository**. This is distinct from the earlier fork, where `develop` CI was tested.

Actions history: https://github.com/cz632804-cpu/devops-tp1/actions

## 8. TP02 — Questions and answers

### 2-1. What are Testcontainers?

Testcontainers is a testing library that starts temporary Docker containers during automated tests. In this project, a PostgreSQL container provides a real database for the integration test, reducing dependence on manually maintained test databases and improving reproducibility in local development and CI.

### 2-2. Why do we need secure variables?

Credentials such as Docker Hub access tokens and SonarQube tokens must not be stored in source code or workflow files. GitHub Actions secrets allow workflows to use these values without committing them to the repository. Secret access should be limited, and logs must not intentionally print their values.

### 2-3. Why put `needs: build-and-test-backend` on a CD job?

In a **single GitHub Actions workflow**, `needs` creates a dependency between jobs, so a publishing job runs only after the required build-and-test job has succeeded. This prevents an untested build from being published.

**In our final split-workflow implementation**, CI and CD live in two different YAML files, so we do **not** use `needs` to connect them. Instead, `workflow_run` triggers the CD workflow on CI completion, and the CD job checks that the prior CI conclusion was `success` and the event was a push to `main`.

### 2-4. Why push Docker images to a registry?

A registry such as Docker Hub distributes versioned images to other developers and deployment servers. Pushing images from CI/CD enables a build that passed tests and the quality check to be retrieved and deployed elsewhere without repeating the local build steps.

## 9. TP03 — Ansible deployment and Vue.js frontend

### Ansible deployment

The `ansible/` directory contains the deployment playbook and the Ansible roles used to provision and deploy the Dockerized application to the school server. The existing TP03 deployment was configured before the Vue.js frontend was integrated. The frontend changes are currently on the `feature/teacher-frontend` branch; the remote deployment of these changes has not yet been verified.

The deployment workflow remains: CI validates the backend on `main`, CD builds and pushes Docker images to Docker Hub, and Ansible deploys the images to the target host.

### Teacher-provided Vue.js frontend

Frontend source: https://github.com/takima-training/devops-front

Files relevant to the frontend integration:

- `devops-front/src/components/DepartmentList.vue` — fetches `/api/departments`.
- `devops-front/src/components/Department.vue` — fetches department details and students and submits new students using `/api` routes.
- `http-server/Dockerfile` — builds Vue.js using Node.js and serves the generated files with Apache.
- `http-server/proxy.conf` — routes `/api/` to Spring Boot and falls back to the Vue entry page for client-side navigation.
- `docker-compose.yml` — builds Apache from the repository root so Docker can access `devops-front/`.
- `.github/workflows/cd.yml` — uses the repository-root build context for the Apache image.
- `backend/simple-api/src/main/java/fr/takima/training/simpleapi/controller/StudentController.java` — provides student and department API endpoints.

The Vue application no longer calls `localhost:8080` directly. Instead, it makes same-origin requests to `/api/`, which Apache proxies internally to Spring Boot. This avoids exposing the backend port to the browser and avoids cross-origin requests for this frontend.

**Local verification:** the Vue homepage, department navigation, and student listing have been displayed successfully at `http://localhost:8081/`. Student creation and the new frontend's remote deployment must be validated separately.

## 10. TP03 — Questions and answers (Ansible)

> These are explanations of the Ansible concepts used in this project. They are not presented as verbatim questions from the teacher's TP03 handout, which was not provided with this README.

### 3-1. What is Ansible and why use it?

Ansible is an agentless configuration-management and automation tool. It connects to the target Linux host over SSH and applies declarative tasks. In this project, it automates provisioning and deployment so we do not have to repeat Docker installation and container commands manually on the school server.

### 3-2. What is an inventory?

An inventory identifies the machines Ansible manages and their SSH connection settings. For this TP, it targets the school server (`zihao.cheng.takima.school`), while the playbook defines the tasks to execute there. The server host and SSH user must match the project's actual inventory; credentials and private keys must not be committed.

### 3-3. What is an Ansible playbook?

A playbook is a YAML file containing one or more plays. Each play specifies target hosts and tasks or roles to apply. In this project, `ansible/playbook.yml` orchestrates the setup and deployment of the Docker services.

### 3-4. What are Ansible roles, and why use them?

Roles organize related tasks into reusable, maintainable components. Our project separates responsibilities into `docker`, `network`, `database`, `backend`, and `proxy` roles. This is easier to read and update than maintaining all deployment commands in one large playbook. Role directory scaffolding does not by itself mean that every optional handler, template, or test has been implemented.

### 3-5. What does idempotency mean?

An operation is idempotent when repeating it has the same intended result without introducing unnecessary changes. Ansible modules should describe the desired state, such as an installed package or a running container. We should verify idempotency by rerunning the playbook and inspecting the `changed` count; we should not claim every task is fully idempotent without this check.

### 3-6. How does Ansible deploy the Docker application?

The workflow builds Docker images in GitHub Actions, publishes them to Docker Hub, and runs the Ansible deployment after successful CI on `main`. The server pulls the published images and runs the PostgreSQL, Spring Boot and Apache containers using the configuration in the Ansible roles. In the updated application, the Apache image also contains the compiled Vue frontend.

### 3-7. Why use a reverse proxy on the server?

Apache serves the Vue static assets and provides one public HTTP entry point. Requests to `/api/` are sent to `backend:8080/` on the internal Docker network, and other frontend routes are handled by Vue. PostgreSQL is accessed by the backend on `database:5432`; its port does not need to be exposed publicly.

### 3-8. What is the difference between CI and CD in this project?

CI checks the application with Maven/Testcontainers and, on a qualifying push to `main`, SonarQube Cloud. Once CI succeeds, CD builds and pushes the Docker images and uses Ansible to deploy them to the school server. A push to `feature/teacher-frontend` alone does not deploy the new frontend to the school server.

### 3-9. How are sensitive variables handled?

GitHub Actions secrets store automation credentials such as Docker Hub and SonarQube tokens. Local `.env` configuration must not be committed. Ansible Vault can encrypt sensitive Ansible variables, but it is not claimed as implemented in this project. Secrets must not be placed in a public README or committed inventory.

### 3-10. How do we verify the deployment?

Check that the Ansible playbook finishes successfully, inspect the containers on the server, and test the public site in a browser. Confirm that the Vue homepage loads, the IRC department and student list are displayed, and API requests reach Spring Boot through `/api/`. Student creation should be demonstrated only after confirming that `POST /students` works. The new Vue version has been verified locally; its remote deployment is still pending verification.

## 11. How to demonstrate TP01, TP02 and TP03

**TP01 — Docker:**

```bash
# From the repository root, after preparing .env:
docker compose up -d --build
docker compose ps
```

Open `http://localhost:8081/`, select a department such as IRC, and display its students. Show that the frontend uses `/api/departments` and `/api/departments/IRC/students`, Apache proxies these requests to Spring Boot, and PostgreSQL stores the data. Demonstrate the Add button only after validating student creation.

**TP02 — Automation:**

Open the GitHub Actions page and show successful CI and CD runs for the previously deployed version, including Maven/Testcontainers and SonarQube in CI and Docker build-and-push steps in CD. The new frontend build must be validated on `main` before claiming that this version was deployed.

**TP03 — Deployment and frontend integration:**

Show `ansible/playbook.yml` and the Ansible roles, then show `http-server/Dockerfile`, `http-server/proxy.conf`, and the modified Vue `fetch` calls. Demonstrate the locally running Vue frontend. Show the remote site only after the new Docker images have been built, pushed, and deployed successfully.

## 12. Conclusion

TP01 demonstrates Docker image creation, PostgreSQL initialization, database persistence, multistage Java builds, backend database connectivity, Apache reverse proxying, Docker Compose orchestration, and Docker Hub publication. TP02 extends the application with automated Maven/Testcontainers tests, SonarQube quality checks, and GitHub Actions-based Docker image delivery. TP03 adds Ansible-based deployment. The teacher-provided Vue frontend is integrated and works locally; remote deployment of this updated frontend remains to be verified.

The application can be built and started with:

```bash
docker compose up -d --build
```
