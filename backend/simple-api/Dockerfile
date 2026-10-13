# Stage 1: Build Spring Boot application with Java 21
FROM eclipse-temurin:21-jdk-alpine AS myapp-build

# Application directory
ENV MYAPP_HOME=/opt/myapp
WORKDIR $MYAPP_HOME

# Install Maven
RUN apk add --no-cache maven

# Copy Maven configuration and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the executable JAR
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Run with Java 21 JRE
FROM eclipse-temurin:21-jre-alpine

ENV MYAPP_HOME=/opt/myapp
WORKDIR $MYAPP_HOME

# Copy only the executable JAR from the build stage
COPY --from=myapp-build $MYAPP_HOME/target/*.jar $MYAPP_HOME/myapp.jar

# Spring Boot default HTTP port
EXPOSE 8080

# Start the Spring Boot application
ENTRYPOINT ["java", "-jar", "myapp.jar"]