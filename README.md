# DevOps TP01 + TP02 — Docker and CI/CD

## Project overview

This project implements a three-tier application using Docker:

- Apache HTTP Server 2.4 as a reverse proxy
- Spring Boot 3.4.5 with Java 21 as the backend
- PostgreSQL 17.2 as the database

TP01 deploys the application with Docker Compose and publishes images to Docker Hub. TP02 adds automated testing, SonarQube Cloud quality analysis, and GitHub Actions CI/CD in **this same repository**.

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

The database-backed API provides:

```http
GET /departments
GET /departments/IRC/students
GET /students/1
```

For example, the IRC endpoint returns student information with the associated department.

The backend connects to PostgreSQL through Spring JDBC using Docker service discovery.

## 3. Apache HTTP Server

### 1-5. Why do we need a reverse proxy?

A reverse proxy provides a single public entry point for incoming HTTP requests.

It forwards requests to internal backend services, hides internal ports, centralizes routing and can provide HTTPS termination or load balancing.

In this project, Apache listens on port 80 inside its container and forwards API requests to the Spring Boot service on port 8080.

The Docker Compose configuration publishes Apache on host port 8081.

The Apache landing page is available at:

```text
http://localhost:8081/index.html
```

The API is available through the proxy at:

```text
http://localhost:8081/departments/IRC/students
```

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

- Builds an Apache HTTP Server image.
- Serves a simple HTML landing page.
- Forwards API requests to `backend:8080`.
- Maps host port 8081 to container port 80.
- Depends on the backend service.

**networks**

All three services communicate through an isolated Docker bridge network.

**volumes**

A named PostgreSQL volume preserves the database across container recreation.

Architecture:

```text
Client
  |
  | localhost:8081
  v
Apache HTTP Server
  |
  | backend:8080
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

Test the greeting API:

```bash
curl http://localhost:8081/
```

Test the database-backed API:

```bash
curl http://localhost:8081/departments/IRC/students
```

Test the Apache landing page:

```text
http://localhost:8081/index.html
```

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
| `nifdgg/tp01-http-server:latest` | `./http-server` |

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

## 9. TP03 — Ansible Deployment

TP03 extends the existing Docker and CI/CD project by automating the installation of Docker and the deployment of the application on a remote Linux server using Ansible.

The deployed application contains the same three services used in TP01:

- PostgreSQL database
- Spring Boot backend
- Apache HTTP Server reverse proxy

The remote server used for this TP is:

`zihao.cheng.takima.school`

Ansible connects to the server using SSH with the `admin` account.

### 3-1. Ansible inventory and commands

**What is an Ansible inventory?**

An Ansible inventory defines the hosts that Ansible manages. It can organize hosts into groups and specify connection settings, such as SSH users and authentication keys.

Our inventory is located at:

`ansible/inventories/setup.yml`

It defines the production server and uses the SSH user `admin`.

```yaml
all:
  vars:
    ansible_user: admin
    ansible_ssh_private_key_file: "{{ lookup('env', 'HOME') }}/.ssh/id_rsa"
  children:
    prod:
      hosts:
        zihao.cheng.takima.school:
```

The SSH private key is stored outside the Git repository and must never be committed.

**How can we test the Ansible connection?**

From the `ansible` directory:

```bash
ansible all -i inventories/setup.yml -m ping
```

A successful connection returns `SUCCESS` and `pong`.

**How can we retrieve information about the remote server?**

```bash
ansible all -i inventories/setup.yml -m setup
```

The `setup` module collects system information, known as Ansible facts.

**What does `--become` do?**

The `--become` option allows Ansible to execute commands with elevated privileges, usually through `sudo`.

For example:

```bash
ansible all -i inventories/setup.yml -m command -a "docker ps" --become
```

### 3-2. Ansible playbook and roles

**What is an Ansible playbook?**

An Ansible playbook is a YAML file describing the configuration and tasks that should be executed on managed servers.

Our main playbook is:

`ansible/playbook.yml`

It contains two plays.

The first play installs and configures Docker:

```yaml
- name: Configure Docker server
  hosts: all
  gather_facts: true
  become: true
  roles:
    - docker
```

The second play deploys the application:

```yaml
- name: Configure application network
  hosts: all
  gather_facts: false
  become: true
  vars:
    ansible_python_interpreter: /opt/docker_venv/bin/python
    db_password: "{{ lookup('env', 'DB_PASSWORD') }}"
  roles:
    - network
    - database
    - backend
    - proxy
```

We use two plays because Docker installation requires the system Python environment, while Docker container management uses a dedicated Python virtual environment.

The database password is retrieved from the `DB_PASSWORD` environment variable rather than being hardcoded in the playbook.

**Why use Ansible roles?**

Roles organize related tasks into reusable components. This makes the deployment easier to understand, maintain and modify.

Our project uses five roles:

| Role | Purpose |
| --- | --- |
| `docker` | Install and configure Docker Engine |
| `network` | Create the Docker application network |
| `database` | Deploy PostgreSQL and its persistent volume |
| `backend` | Deploy the Spring Boot application |
| `proxy` | Deploy Apache HTTP Server |

The relevant directory structure is:

```text
ansible/
├── inventories/
│   └── setup.yml
├── playbook.yml
└── roles/
    ├── docker/
    │   └── tasks/
    │       └── main.yml
    ├── network/
    │   └── tasks/
    │       └── main.yml
    ├── database/
    │   └── tasks/
    │       └── main.yml
    ├── backend/
    │   └── tasks/
    │       └── main.yml
    └── proxy/
        └── tasks/
            └── main.yml
```

**What is idempotence in Ansible?**

Idempotence means that running the same configuration multiple times should result in the same desired system state.

For example, when Docker is already installed and correctly configured, Ansible should not reinstall it unnecessarily.

### Docker role

File:

`ansible/roles/docker/tasks/main.yml`

This role:

- Installs the required system packages.
- Configures the official Docker repository.
- Installs Docker Engine and Docker Compose.
- Creates the Python virtual environment `/opt/docker_venv`.
- Installs the required Docker Python libraries.
- Starts and enables the Docker service.

The virtual environment separates Docker Python dependencies from the operating system's Python packages.

### Network role

File:

`ansible/roles/network/tasks/main.yml`

The network role uses `community.docker.docker_network` to create:

`app-network`

This network allows the containers to communicate using their Docker names.

For example, the backend reaches PostgreSQL at `database:5432`.

### Database role

File:

`ansible/roles/database/tasks/main.yml`

The database role uses the image:

`nifdgg/tp01-database:latest`

It creates:

- A PostgreSQL container named `database`.
- A persistent volume named `postgres-data`.

The volume is mounted at:

`/var/lib/postgresql/data`

This preserves database data when the container is recreated.

### Backend role

File:

`ansible/roles/backend/tasks/main.yml`

The backend role deploys the Spring Boot image:

`nifdgg/tp01-backend:latest`

The container is named `backend` and uses port `8080` internally.

Its PostgreSQL connection is configured using:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://database:5432/db
SPRING_DATASOURCE_USERNAME=usr
```

The database password is provided by the Ansible variable `db_password`.

### Proxy role

File:

`ansible/roles/proxy/tasks/main.yml`

The proxy role deploys:

`nifdgg/tp01-http-server:latest`

The container is named `http-server`.

Apache publishes port `80` on the remote server and forwards API requests to Spring Boot.

The `/index.html` landing page is served by Apache.

### 3-3. Deploying Docker containers with Ansible

**How does Ansible manage Docker containers?**

The `community.docker.docker_container` module creates and manages Docker containers.

It supports configuration options including:

- Docker image and container name
- Container state and restart policy
- Environment variables
- Docker networks
- Persistent volumes
- Published ports

The Docker image definitions use `pull: true` so the server checks for updated images in Docker Hub during deployment.

**Why use Docker networks?**

A Docker network allows containers to communicate through service names instead of relying on fixed IP addresses.

**Why use a PostgreSQL volume?**

The database volume preserves data independently of the PostgreSQL container lifecycle.

**How can we deploy the application manually?**

First, install the Docker Ansible collection if it is not already available:

```bash
ansible-galaxy collection install community.docker
```

Then navigate to the Ansible directory:

```bash
cd ansible
```

Check connectivity:

```bash
ansible all -i inventories/setup.yml -m ping
```

Check the playbook syntax:

```bash
ansible-playbook -i inventories/setup.yml playbook.yml --syntax-check
```

For a manual deployment, securely provide the database password:

```bash
read -rs -p "Database password: " DB_PASSWORD
echo
export DB_PASSWORD
```

Run the playbook:

```bash
ansible-playbook -i inventories/setup.yml playbook.yml
```

Remove the password variable from the current shell environment afterward:

```bash
unset DB_PASSWORD
```

The database password must match the password used by the existing PostgreSQL instance.

**How can we verify the deployed containers?**

```bash
ansible all -i inventories/setup.yml -m command -a "docker ps" --become
```

The expected containers are:

- `database`
- `backend`
- `http-server`

Check the Docker network:

```bash
ansible all -i inventories/setup.yml -m command -a "docker network ls" --become
```

Check PostgreSQL availability:

```bash
ansible all -i inventories/setup.yml -m command -a "docker exec database pg_isready -U usr -d db" --become
```

### TP03 checkpoints

**Checkpoint 1 — Docker installed**

Ansible successfully installed and configured Docker on the remote server.

We verified the Docker service using:

```bash
ansible all -i inventories/setup.yml -m command -a "docker --version"
```

```bash
ansible all -i inventories/setup.yml -m command -a "systemctl is-active docker"
```

Docker was installed successfully and the service was active.

**Checkpoint 2 — API accessible**

Ansible successfully deployed the three application containers.

The public API is accessible at:

http://zihao.cheng.takima.school/

A successful response is a JSON object containing an ID and the message `Hello, World!`.

The backend is accessed through the Apache reverse proxy.

**Checkpoint 3 — Continuous Deployment**

The GitHub Actions CD workflow automatically:

1. Checks out the commit validated by CI.
2. Logs in to Docker Hub.
3. Builds and publishes the backend image.
4. Builds and publishes the database image.
5. Builds and publishes the Apache image.
6. Installs Ansible on the GitHub Actions runner.
7. Executes the Ansible playbook on the remote server.

The deployment is executed after a successful CI workflow for a push to `main`.

The workflow uses the following repository secrets:

| Secret | Purpose |
| --- | --- |
| `DOCKERHUB_USERNAME` | Docker Hub username |
| `DOCKERHUB_TOKEN` | Docker Hub access token |
| `ANSIBLE_SSH_PRIVATE_KEY` | SSH authentication |
| `ANSIBLE_SSH_KNOWN_HOSTS` | Remote SSH host verification |
| `DB_PASSWORD` | Database password |

The GitHub Actions workflow **CD DevOps #6** completed successfully for commit `ea6d6b1`.

The `Deploy application with Ansible` step also completed successfully.

This verifies that image publication and remote deployment were automated.

**Checkpoint 4 — Front working**

The Apache landing page is accessible at:

http://zihao.cheng.takima.school/index.html

It provides access to the application endpoints:

- Greeting API: `/`
- Departments: `/departments`
- IRC Students: `/departments/IRC/students`
- Student Details: `/students/1`

The frontend and backend are accessible through the same Apache reverse proxy.

### TP03 — Security considerations

**Why should we avoid deploying unverified Docker images?**

An unverified image may contain defects, vulnerabilities or unexpected code.

Our workflow requires the CI checks to succeed before Docker images are published and deployed.

**Why use GitHub Actions Secrets?**

GitHub Actions Secrets protect sensitive information such as SSH private keys, Docker Hub tokens and database passwords from being stored directly in the repository.

**Why verify SSH host keys?**

SSH host key verification helps ensure that the deployment process connects to the expected server rather than an impersonating host.

**What could be improved for a production deployment?**

A production environment should use:

- HTTPS instead of plain HTTP.
- Strong database credentials.
- Immutable Docker image digests instead of mutable `latest` tags.
- Restricted SSH permissions and deployment credentials.
- Health checks, monitoring and backups.
- Protected branches and deployment approvals.
- A rollback procedure for unsuccessful deployments.

Ansible Vault could also be introduced to encrypt sensitive variables, although this project currently uses GitHub Actions Secrets and environment variables for deployment credentials.

---

## 10. How to demonstrate TP01, TP02 and TP03

### TP01 — Docker

From the repository root, prepare the local environment variables and start the application:

```bash
cp .env.example .env
# Configure the local environment variables in .env.
docker compose up -d --build
docker compose ps
```

Open the local application:

http://localhost:8081/index.html

Demonstrate the following endpoints:

- Greeting API: `http://localhost:8081/`
- Departments: `http://localhost:8081/departments`
- IRC Students: `http://localhost:8081/departments/IRC/students`
- Student Details: `http://localhost:8081/students/1`

Explain that Apache forwards requests to Spring Boot and that Spring Boot retrieves data from PostgreSQL.

Verify database persistence:

```bash
docker compose exec database psql -U usr -d db -c "SELECT * FROM students;"
```

### TP02 — CI/CD

Open the GitHub Actions page:

https://github.com/cz632804-cpu/devops-tp1/actions

Show the successful `CI DevOps` workflow.

Explain the main steps:

- Maven build and tests
- PostgreSQL integration testing with Testcontainers
- SonarQube Cloud analysis
- Quality Gate verification

Then open the `CD DevOps` workflow and show the three Docker image build-and-push steps.

Docker Hub images:

- https://hub.docker.com/r/nifdgg/tp01-database
- https://hub.docker.com/r/nifdgg/tp01-backend
- https://hub.docker.com/r/nifdgg/tp01-http-server

Explain that image publication only occurs after the required CI checks succeed.

### TP03 — Ansible

Open the Ansible inventory:

`ansible/inventories/setup.yml`

Show the target server and SSH connection settings.

Open:

`ansible/playbook.yml`

Explain the two plays and five roles.

Demonstrate an Ansible connectivity check:

```bash
cd ansible
ansible all -i inventories/setup.yml -m ping
```

Verify the remote Docker containers:

```bash
ansible all -i inventories/setup.yml -m command -a "docker ps" --become
```

Show the remote application:

- Frontend: http://zihao.cheng.takima.school/index.html
- Backend API: http://zihao.cheng.takima.school/

Finally, open GitHub Actions and select the successful **CD DevOps #6** workflow.

Show the green `Deploy application with Ansible` step.

Explain that the application is automatically deployed to the remote server after a successful CI run and Docker image publication.

---

## 11. Conclusion

TP01 demonstrates Docker image creation, PostgreSQL initialization, database persistence, multistage Java builds, backend database connectivity, Apache reverse proxying, Docker Compose orchestration, and Docker Hub publication.

TP02 extends the same application with automated Maven/Testcontainers tests, SonarQube Cloud quality checks, and GitHub Actions-based Docker image delivery.

TP03 adds infrastructure automation using Ansible. The project automatically installs Docker, creates the application network, and deploys PostgreSQL, Spring Boot and Apache HTTP Server on a remote Linux server.

GitHub Actions connects the three stages by running CI checks, publishing Docker images and deploying the application using Ansible.

The completed project demonstrates a DevOps workflow covering application containerization, automated testing, continuous delivery and remote deployment.
