#!/bin/bash
# 天天扑克锦标赛 — 首尔后端部署脚本
# 用法: ./deploy-seoul.sh
# 架构：首尔跑 4 个 PM2 后端（3000-3003），公网入口在美国边缘节点
#   （nginx + SSH 隧道，见 nginx-poker-edge.conf，本脚本不涉及）

set -e

SERVER="43.164.130.145"
USER="root"
PROJECT_DIR="/opt/poker-night"

echo "=== 天天扑克锦标赛 部署到首尔 ==="

# 1. 同步代码（本地 → 服务器，rsync 直传）
echo "[1/5] 同步代码..."
rsync -avz --delete \
  --exclude node_modules --exclude .git --exclude .env \
  --exclude build --exclude '*.apk' \
  -e ssh \
  "$(dirname "$0")/.." $USER@$SERVER:$PROJECT_DIR

# 2. 安装依赖
echo "[2/5] 安装依赖..."
ssh $USER@$SERVER "export PATH=/root/.nvm/versions/node/v20.20.2/bin:\$PATH && cd $PROJECT_DIR && npm install --production"

# 3. 数据库迁移（幂等）
echo "[3/5] 数据库迁移..."
ssh $USER@$SERVER "export PGPASSWORD=poker123 && psql -U poker -d poker_night -h 127.0.0.1 -f $PROJECT_DIR/server/migrations/001_init.sql && psql -U poker -d poker_night -h 127.0.0.1 -f $PROJECT_DIR/server/migrations/002_usdt.sql"

# 4. 重启 PM2 服务
echo "[4/5] 重启 PM2 服务..."
ssh $USER@$SERVER "export PATH=/root/.nvm/versions/node/v20.20.2/bin:\$PATH && cd $PROJECT_DIR && pm2 restart ecosystem.config.js --update-env && pm2 status"

# 5. 健康检查
echo "[5/5] 健康检查..."
ssh $USER@$SERVER "curl -s http://127.0.0.1:3000/health && echo ''"
ssh $USER@$SERVER "curl -s http://127.0.0.1:3002/health && echo ''"
ssh $USER@$SERVER "curl -s http://127.0.0.1:3003/health && echo ''"

echo "=== 部署完成 ==="
echo "公网入口: https://poker.clawclaw.tech（美国边缘节点）"
