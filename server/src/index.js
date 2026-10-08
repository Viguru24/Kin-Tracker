require('dotenv').config();
const http = require('http');
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const circleRoutes = require('./routes/circles');
const db = require('./db');
const { setupAudioRelay, getAudioRelayStats } = require('./audioRelay');

const app = express();
const PORT = process.env.PORT || 3000;

// Security & Middlewares
app.use(helmet({ contentSecurityPolicy: false }));
app.use(cors({ origin: '*' }));
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));
app.use(morgan('combined'));

// Initialize default sovereign family circle if database is fresh
(function initDefaultCircle() {
    // Default circle for existing Kin-Tracker installations (legacy PIN 4666 / token 81e5632c_pin_group)
    const existing = db.getCircleByInviteCode('4666') || db.getCircleByInviteCode('RADAR-1');
    if (!existing) {
        const defaultCircle = {
            id: 'circle_default_sovereign',
            name: 'Family Circle',
            inviteCode: 'KT-4666',
            legacyPin: '4666',
            creatorId: 'admin_device',
            createdAt: Date.now(),
            lastUpdated: Date.now(),
            homeLat: 51.329480,
            homeLng: -0.119095,
            isHomeCalibrated: true,
            workLat: 51.375800,
            workLng: -0.098000,
            isWorkCalibrated: false,
            homeRadiusMeters: 140.0,
            workRadiusMeters: 75.0,
            memberIds: []
        };
        db.saveCircle(defaultCircle);
        // Also map legacy token "81e5632c_pin_group"
        db.inviteCodes['81E5632C_PIN_GROUP'] = defaultCircle.id;
        db.inviteCodes['4666'] = defaultCircle.id;
        console.log('[Init] Seeded default Family Circle with Invite Code "KT-4666" and PIN "4666"');
    }
})();

// Health & Status
app.get('/health', (req, res) => {
    res.json({
        status: 'online',
        server: 'Kin-Tracker Sovereign GPS VPS Server',
        version: '2.0.0',
        timestamp: Date.now(),
        stats: db.getStats()
    });
});

app.get('/api/info', (req, res) => {
    res.json({
        app: 'Kin-Tracker Family Safety Radar',
        version: '2.0.0',
        supportedInviteFormats: ['XXX-XXX', '6-character alphanumeric', '4-digit PIN (legacy)'],
        stats: db.getStats()
    });
});

// Main API Routes
app.use('/api/circles', circleRoutes);

// Legacy Backward-Compatibility Sync Endpoints (for old client versions if any)
app.get('/sync/:token', (req, res) => {
    const { token } = req.params;
    const cleanToken = token.replace(/[\s\-_]/g, '').toUpperCase();
    const circle = db.getCircleByInviteCode(cleanToken) || db.getCircleById(token);
    
    if (!circle) {
        return res.json({});
    }

    const membersMap = {};
    db.getCircleMembers(circle.id).forEach(m => {
        membersMap[m.id] = m;
    });

    return res.json({
        homeLat: circle.homeLat,
        homeLng: circle.homeLng,
        isHomeCalibrated: circle.isHomeCalibrated,
        workLat: circle.workLat,
        workLng: circle.workLng,
        isWorkCalibrated: circle.isWorkCalibrated,
        homeRadiusMeters: circle.homeRadiusMeters,
        workRadiusMeters: circle.workRadiusMeters,
        lastUpdated: circle.lastUpdated,
        members: membersMap,
        creatorId: circle.creatorId,
        pinCode: circle.inviteCode || circle.legacyPin || '4666',
        shoppingItems: db.getCircleShoppingItems(circle.id)
    });
});

app.put('/sync/:token', (req, res) => {
    const { token } = req.params;
    const body = req.body;
    const cleanToken = token.replace(/[\s\-_]/g, '').toUpperCase();
    let circle = db.getCircleByInviteCode(cleanToken) || db.getCircleById(token);

    if (!circle) {
        circle = {
            id: `circle_${token}`,
            name: 'Family Circle',
            inviteCode: cleanToken.length === 6 ? `${cleanToken.slice(0,3)}-${cleanToken.slice(3)}` : cleanToken,
            creatorId: body.creatorId || 'admin',
            createdAt: Date.now(),
            lastUpdated: Date.now(),
            homeLat: body.homeLat || 51.329480,
            homeLng: body.homeLng || -0.119095,
            isHomeCalibrated: Boolean(body.isHomeCalibrated),
            workLat: body.workLat || 0.0,
            workLng: body.workLng || 0.0,
            isWorkCalibrated: Boolean(body.isWorkCalibrated),
            homeRadiusMeters: body.homeRadiusMeters || 140.0,
            workRadiusMeters: body.workRadiusMeters || 75.0,
            memberIds: []
        };
        db.saveCircle(circle);
    } else {
        if (body.homeLat !== undefined) circle.homeLat = body.homeLat;
        if (body.homeLng !== undefined) circle.homeLng = body.homeLng;
        if (body.isHomeCalibrated !== undefined) circle.isHomeCalibrated = Boolean(body.isHomeCalibrated);
        if (body.workLat !== undefined) circle.workLat = body.workLat;
        if (body.workLng !== undefined) circle.workLng = body.workLng;
        if (body.isWorkCalibrated !== undefined) circle.isWorkCalibrated = Boolean(body.isWorkCalibrated);
        if (body.homeRadiusMeters !== undefined) circle.homeRadiusMeters = body.homeRadiusMeters;
        if (body.workRadiusMeters !== undefined) circle.workRadiusMeters = body.workRadiusMeters;
        circle.lastUpdated = Date.now();
        db.saveCircle(circle);
    }

    // If members map provided
    if (body.members && typeof body.members === 'object') {
        const incomingMembers = Object.values(body.members).filter(m => m && m.id);

        // Deduplicate devices by UUID suffix so renaming doesn't leave ghost members in server DB
        const incomingUuids = new Set();
        incomingMembers.forEach(m => {
            const parts = m.id.split('_');
            if (parts.length >= 3) {
                const uuid = parts[parts.length - 1];
                if (uuid.length >= 4) incomingUuids.add(uuid);
            }
        });

        // Clean up any existing circle member that has the same UUID suffix but a different ID,
        // or any member explicitly removed from the client members map
        if (circle.memberIds && circle.memberIds.length > 0) {
            const obsoleteIds = [];
            circle.memberIds.forEach(existingId => {
                const parts = existingId.split('_');
                if (parts.length >= 3) {
                    const uuid = parts[parts.length - 1];
                    if (incomingUuids.has(uuid) && !body.members[existingId]) {
                        obsoleteIds.push(existingId);
                        return;
                    }
                }
                if (!body.members[existingId]) {
                    obsoleteIds.push(existingId);
                }
            });
            obsoleteIds.forEach(oldId => {
                db.removeMemberFromCircle(circle.id, oldId);
            });
        }

        incomingMembers.forEach(m => {
            const existing = db.getMember(m.id);
            const isAlarmOrControlUpdate = m.statusText === '🚨 ALARM' || (existing && existing.statusText === '🚨 ALARM') || (existing && m.isLocationPaused !== existing.isLocationPaused);
            if (!isAlarmOrControlUpdate && existing && existing.lastActive && m.lastActive && Number(m.lastActive) < Number(existing.lastActive)) {
                return; // Do not overwrite newer member data with stale snapshot
            }
            db.saveMember({ ...m, circleId: circle.id });
        });
    }

    return res.json({ success: true, message: 'Synced successfully' });
});

// Audio Relay Stats & Diagnostics
app.get('/api/audio/stats', (req, res) => {
    res.json({
        status: 'online',
        rooms: getAudioRelayStats()
    });
});

// Start listening
const server = http.createServer(app);
setupAudioRelay(server);

server.listen(PORT, '0.0.0.0', () => {
    console.log(`====================================================`);
    console.log(`🚀 Kin-Tracker Sovereign GPS Server v2.0.0`);
    console.log(`📡 Listening on: http://0.0.0.0:${PORT}`);
    console.log(`🎙️ Cloud Audio Relay active at ws://0.0.0.0:${PORT}/audio`);
    console.log(`🛡️ Life360-Style Invite System Active`);
    console.log(`====================================================`);
});

