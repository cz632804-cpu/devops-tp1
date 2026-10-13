# PostgreSQL version required by the TP
FROM postgres:17.2-alpine

# Initialize the database on its first startup
# Scripts are executed in alphabetical order
COPY 01-CreateScheme.sql /docker-entrypoint-initdb.d/
COPY 02-InsertData.sql /docker-entrypoint-initdb.d/