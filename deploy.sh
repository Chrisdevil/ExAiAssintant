#!/bin/bash
set -e

# ExAiAssistant 快速部署脚本
# 用法:
#   本地一键启动:  ./deploy.sh local
#   部署到云端:    ./deploy.sh prod user@your-server.com

deploy_local() {
    echo "=== 本地构建 & 启动 ==="
    ./mvnw package -DskipTests -q
    export DEEPSEEK_API_KEY="${DEEPSEEK_API_KEY:-your-key-here}"
    docker compose up -d --build
    echo "启动完成: http://localhost:8080"
}

deploy_prod() {
    local HOST=$1
    echo "=== 构建 jar ==="
    ./mvnw package -DskipTests -q

    echo "=== 上传到 $HOST ==="
    ssh "$HOST" "mkdir -p /opt/exai"
    scp target/*.jar Dockerfile docker-compose.yml "$HOST:/opt/exai/"
    scp -r src/main/resources/embedding "$HOST:/opt/exai/"

    echo "=== 远程启动 ==="
    ssh "$HOST" "cd /opt/exai && docker compose up -d --build"
    echo "部署完成"
}

case "${1:-local}" in
    local)  deploy_local ;;
    prod)   deploy_prod "$2" ;;
    *)      echo "用法: $0 {local|prod <host>}" ;;
esac
