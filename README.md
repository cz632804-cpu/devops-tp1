
# DevOps TP01 - Docker

## Project overview

This project implements a three-tier application using Docker:

- Apache HTTP Server 2.4 as a reverse proxy
- Spring Boot 3.4.5 with Java 21 as the backend
- PostgreSQL 17.2 as the database

The application is deployed using Docker Compose and the images are published to Docker Hub.

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

Build:

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

## 7. Conclusion

This TP demonstrates Docker image creation, PostgreSQL initialization, database persistence, multistage Java builds, backend database connectivity, Apache reverse proxying, Docker Compose orchestration and Docker Hub publication.

The application can be built and started with:

```bash
docker compose up -d --build
```
