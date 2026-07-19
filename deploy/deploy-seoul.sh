#!/bin/bash
# 天天扑克锦标赛 — 首尔服务器部署脚本
# 用法: ./deploy-seoul.sh

set -e

SERVER="43.164.130.145"
USER="root"
PROJECT_DIR="/opt/poker-night"
REPO="git@github.com:zhulianxing/tiantian-poker-tournament.git"

echo "=== 天天扑克锦标赛 部署到首尔 ==="

# 1. 拉取最新代码
echo "[1/6] 拉取最新代码..."
ssh $USER@$SERVER "cd $PROJECT_DIR && git fetch origin && git reset --hard origin/main"

# 2. 安装依赖
echo "[2/6] 安装依赖..."
ssh $USER@$SERVER "cd $PROJECT_DIR && npm install --production"

# 3. 复制 Nginx 配置
echo "[3/6] 更新 Nginx 配置..."
scp deploy/nginx-poker.conf $USER@$SERVER:/etc/nginx/conf.d/poker.conf
ssh $USER@$SERVER "nginx -t && systemctl reload nginx"

# 4. 重启 PM2 服务
echo "[4/6] 重启 PM2 服务..."
ssh $USER@$SERVER "cd $PROJECT_DIR && pm2 restart ecosystem.config.js"

# 5. 检查服务状态
echo "[5/6] 检查服务状态..."
ssh $USER@$SERVER "pm2 status"

# 6. 健康检查
echo "[6/6] 健康检查..."
ssh $USER@$SERVER "curl -s http://127.0.0.1:3010/health && echo ''"
ssh $USER@$SERVER "curl -s http://127.0.0.1:3011/health && echo ''"
ssh $USER@$SERVER "curl -s http://127.0.0.1:3012/health && echo ''"
ssh $USER@$SERVER "curl -s http://127.0.0.1:3013/health && echo ''"

echo "=== 部署完成 ==="
echo "访问: https://poker.clawclaw.tech"
