FROM eclipse-temurin:17-jre

WORKDIR /app

# Claude Code CLI needs node
RUN apt-get update && apt-get install -y nodejs npm && \
    npm install -g @anthropic-ai/claude-code && \
    rm -rf /var/lib/apt/lists/*

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
