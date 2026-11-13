FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY middleware/pom.xml pom.xml
RUN mvn -q -DskipTests dependency:go-offline
COPY middleware/src src
COPY certs/root_ca_bruker.crt /tmp/root_ca_bruker.crt
RUN mvn -q -DskipTests package
RUN keytool -importkeystore -noprompt \
    -srckeystore "$JAVA_HOME/lib/security/cacerts" \
    -srcstorepass changeit \
    -destkeystore /tmp/bruker-truststore.p12 \
    -deststoretype PKCS12 \
    -deststorepass changeit \
    -srcstoretype JKS
RUN keytool -importcert -noprompt -trustcacerts \
    -alias bruker-ca \
    -file /tmp/root_ca_bruker.crt \
    -keystore /tmp/bruker-truststore.p12 \
    -storetype PKCS12 \
    -storepass changeit

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/target/middleware-0.1.0.jar app.jar
COPY --from=build /tmp/bruker-truststore.p12 /app/bruker-truststore.p12
ENV JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/app/bruker-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]