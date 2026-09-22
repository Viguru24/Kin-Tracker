#!/usr/bin/env bash
# ==============================================================================
# Kin-Tracker Sovereign GPS Server VPS Automated Setup Script
# ==============================================================================
set -e

echo "🚀 Installing Kin-Tracker Sovereign GPS Server on VPS..."

# 1. Update system packages
if command -v apt-get &> /dev/null; then
    sudo apt-get update -y && sudo apt-get upgrade -y
    sudo apt-get install -y curl git ufw nginx
fi

# 2. Install Node.js (v20) if not present
if ! command -v node &> /dev/null; then
    echo "📦 Installing Node.js v20..."
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt-get install -y nodejs
fi

# 3. Install PM2 process manager
if ! command -v pm2 &> /dev/null; then
    echo "📦 Installing PM2 globally..."
    sudo npm install -g pm2
fi

# 4. Install server dependencies
echo "📦 Installing dependencies..."
npm install --production

# 5. Start / Reload with PM2
echo "🚀 Starting server with PM2..."
pm2 start ecosystem.config.js || pm2 restart ecosystem.config.js
pm2 save
pm2 startup || true

echo ""
echo "============================================================"
echo "✅ Kin-Tracker Server is running on port 3000!"
echo "📡 Test with: curl http://localhost:3000/health"
echo "============================================================"
