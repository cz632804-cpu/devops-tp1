# Stage 1: Compile Java with JDK 21
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /usr/src

COPY Main.java .

RUN javac Main.java

# Stage 2: Run Java with JRE 21
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Only copy the compiled bytecode from the build stage
COPY --from=build /usr/src/Main.class .

CMD ["java", "Main"]