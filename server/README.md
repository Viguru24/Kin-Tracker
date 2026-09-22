# 🛰️ Kin-Tracker Sovereign VPS Backend Server

High-performance, sovereign REST backend for Kin-Tracker family safety radar with **Life360-style 6-character Circle Invite Codes** (`ABC-123`) and zero-race-condition atomic GPS sync.

---

## 🚀 1-Minute VPS Quick Start

### Method A: Using PM2 (Recommended)

```bash
# 1. Clone repository to your VPS
git clone https://github.com/Viguru24/kin-tracker.git
cd kin-tracker/server

# 2. Run automated setup script
chmod +x deploy-vps.sh
./deploy-vps.sh
```

### Method B: Using Docker Compose

```bash
cd kin-tracker/server
docker compose up -d --build
```

---

## 📡 API Overview

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/circles/create` | Creates a new circle & generates a 6-character code (`K9F-2Q8`) |
| `POST` | `/api/circles/join` | Joins an existing circle using the 6-character invite code |
| `GET` | `/api/circles/:circleId/sync` | Fetches active members, coordinates, battery, and shopping items |
| `POST` | `/api/circles/:circleId/location` | Atomically updates a single device's GPS and telemetry |
| `POST` | `/api/circles/:circleId/shopping` | Syncs shared family shopping list items |
| `POST` | `/api/circles/:circleId/regenerate-code` | Generates a fresh 6-character invite code |
| `POST` | `/api/circles/:circleId/leave` | Removes a member from the circle |
| `GET` | `/health` | Server status and diagnostics |

---

## 🔒 Nginx Reverse Proxy with HTTPS (SSL)

To expose your server securely over HTTPS on your domain (e.g., `api.cosmowhisper.com`):

```nginx
server {
    server_name api.cosmowhisper.com;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Enable SSL for free via Certbot:
```bash
sudo certbot --nginx -d api.cosmowhisper.com
```
