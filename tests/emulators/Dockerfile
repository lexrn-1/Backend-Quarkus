FROM eclipse-temurin:21-jre-jammy AS java
FROM node:22-bookworm-slim
COPY --from=java /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="/opt/java/openjdk/bin:${PATH}"
RUN npm install --global firebase-tools@15.35.0 && mkdir /workspace && chown node:node /workspace
WORKDIR /workspace
COPY firebase.json firestore.rules firestore.indexes.json ./
USER node
EXPOSE 8085 9099
CMD ["firebase", "emulators:start", "--project", "demo-app-meet", "--only", "auth,firestore"]
