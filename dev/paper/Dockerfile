FROM eclipse-temurin:26-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp verify

FROM itzg/minecraft-server:java25
COPY --from=build /opt/java/openjdk /opt/java26
ENV JAVA_HOME=/opt/java26
ENV PATH="/opt/java26/bin:${PATH}"
COPY --from=build /build/target/storefront-2.0-SNAPSHOT.jar /plugins/Storefront.jar
COPY dev/paper/config.yml /plugins/Storefront/config.yml
