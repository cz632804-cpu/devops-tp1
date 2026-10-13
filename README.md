# DevOps TP1 — Docker

## 1. Database

### 1-1. Why is it better to use environment variables instead of putting passwords in the Dockerfile?

Using environment variables at runtime avoids embedding passwords directly in the Docker image. This makes the image more reusable across different environments and reduces the risk of exposing credentials through the Dockerfile or image metadata.

However, passing passwords directly on the command line can still expose them through command history or inspection tools. In production, a secrets management solution should be used.

### 1-2. Why do we need a volume attached to the PostgreSQL container?

A Docker volume allows database data to persist independently of the container's lifecycle.

Without persistent storage, deleting the container can cause the database data to be lost. With a volume, we can recreate the container while preserving the existing database.

We verified this by deleting and recreating our PostgreSQL container with the same volume. The four student records were still available.

### 1-3. Database Dockerfile and essential commands

**Dockerfile**

```dockerfile
FROM postgres:17.2-alpine

COPY 01-CreateScheme.sql /docker-entrypoint-initdb.d/
COPY 02-InsertData.sql /docker-entrypoint-initdb.d/
```

**Build the database image**

```bash
docker build -t my-database ./database
```

**Create the Docker network and volume**

```bash
docker network create app-network
docker volume create devops-postgres-data
```

**Run the PostgreSQL container**

```bash
docker run -d --name my-database-container \
  --network app-network \
  -e POSTGRES_DB=db \
  -e POSTGRES_USER=usr \
  -e POSTGRES_PASSWORD=pwd \
  -v devops-postgres-data:/var/lib/postgresql/data \
  my-database
```

**Check running containers and logs**

```bash
docker ps
docker logs my-database-container
```

**Verify the database data**

```bash
docker exec my-database-container psql -U usr -d db -c "SELECT * FROM students;"
```

**Persistence test**

We stopped and removed the PostgreSQL container, recreated it using the same Docker volume, and verified that all four student records were preserved.