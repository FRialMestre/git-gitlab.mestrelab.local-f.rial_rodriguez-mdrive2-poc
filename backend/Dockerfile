FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY backend/pom.xml pom.xml
RUN mvn -q -DskipTests dependency:go-offline
COPY backend/src src
COPY certs/MestrelabCA.crt /tmp/MestrelabCA.crt
RUN mvn -q -DskipTests package
RUN keytool -importkeystore -noprompt \
    -srckeystore "$JAVA_HOME/lib/security/cacerts" \
    -srcstorepass changeit \
    -destkeystore /tmp/mestrelab-truststore.p12 \
    -deststoretype PKCS12 \
    -deststorepass changeit \
    -srcstoretype JKS
RUN keytool -importcert -noprompt -trustcacerts \
    -alias mestrelab-ca \
    -file /tmp/MestrelabCA.crt \
    -keystore /tmp/mestrelab-truststore.p12 \
    -storetype PKCS12 \
    -storepass changeit

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/target/token-validation-api-0.1.0.jar app.jar
COPY --from=build /tmp/mestrelab-truststore.p12 /app/mestrelab-truststore.p12
ENV JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/app/mestrelab-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]