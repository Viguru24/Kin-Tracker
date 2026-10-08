const express = require('express');
const router = express.Router();
const os = require('os');
const db = require('../db');
const { getAudioRelayStats } = require('../audioRelay');
const { generateInviteCode } = require('../utils/codeGenerator');
const { ringMemberPhone, stopRingMemberPhone } = require('../syncBridge');

// Configurable Admin Key (default to 4666 or process.env.ADMIN_KEY)
const ADMIN_KEY = process.env.ADMIN_KEY || '4666';

// Admin Authentication Middleware
function requireAdmin(req, res, next) {
    const key = req.headers['x-admin-key'] || req.query.adminKey || req.body?.adminKey;
    if (!key || key !== ADMIN_KEY) {
        return res.status(401).json({ success: false, error: 'Unauthorized: Invalid Admin Key' });
    }
    next();
}

// 1. LOGIN / AUTH CHECK
router.post('/login', (req, res) => {
    const { key } = req.body;
    if (key === ADMIN_KEY) {
        db.logEvent('ADMIN', 'Admin logged in to VPS Dashboard');
        return res.json({ success: true, token: key, message: 'Authentication successful' });
    }
    return res.status(401).json({ success: false, error: 'Invalid admin passcode' });
});

router.get('/auth/check', requireAdmin, (req, res) => {
    res.json({ success: true, authorized: true });
});

// 2. OVERVIEW & SYSTEM STATS
router.get('/stats', requireAdmin, (req, res) => {
    try {
        const memUsage = process.memoryUsage();
        const systemStats = {
            serverTime: Date.now(),
            uptimeSeconds: Math.floor(process.uptime()),
            nodeVersion: process.version,
            platform: process.platform,
            cpuCount: os.cpus()?.length || 1,
            totalMemoryMB: Math.round(os.totalmem() / (1024 * 1024)),
            freeMemoryMB: Math.round(os.freemem() / (1024 * 1024)),
            processHeapUsedMB: Math.round(memUsage.heapUsed / (1024 * 1024)),
            processRssMB: Math.round(memUsage.rss / (1024 * 1024))
        };

        const dbStats = db.getStats();
        const audioRooms = getAudioRelayStats();

        // Check for any active SOS alarms
        const allCircles = db.getAllCircles();
        const activeAlarms = [];
        allCircles.forEach(circle => {
            (circle.members || []).forEach(m => {
                if (m.statusText === '🚨 ALARM') {
                    activeAlarms.push({
                        circleId: circle.id,
                        circleName: circle.name,
                        member: m
                    });
                }
            });
        });

        return res.json({
            success: true,
            system: systemStats,
            db: dbStats,
            audio: {
                totalRooms: Object.keys(audioRooms).length,
                rooms: audioRooms
            },
            activeAlarms
        });
    } catch (err) {
        console.error('[Admin Stats Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

// 3. GET ALL CIRCLES & MEMBERS
router.get('/circles', requireAdmin, (req, res) => {
    try {
        const circles = db.getAllCircles();
        return res.json({ success: true, circles });
    } catch (err) {
        console.error('[Admin Get Circles Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

// 4. CREATE CIRCLE
router.post('/circles/create', requireAdmin, (req, res) => {
    try {
        const {
            name,
            homeLat,
            homeLng,
            homeRadiusMeters,
            workLat,
            workLng,
            workRadiusMeters
        } = req.body;

        const circleId = `circle_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
        let inviteCode = generateInviteCode();
        let attempts = 0;
        while (db.getCircleByInviteCode(inviteCode) && attempts < 10) {
            inviteCode = generateInviteCode();
            attempts++;
        }

        const circle = {
            id: circleId,
            name: name && name.trim() ? name.trim() : 'Family Circle',
            inviteCode: inviteCode,
            creatorId: 'admin_dashboard',
            createdAt: Date.now(),
            lastUpdated: Date.now(),
            homeLat: Number(homeLat) || 51.329480,
            homeLng: Number(homeLng) || -0.119095,
            isHomeCalibrated: true,
            workLat: Number(workLat) || 0.0,
            workLng: Number(workLng) || 0.0,
            isWorkCalibrated: false,
            homeRadiusMeters: Number(homeRadiusMeters) || 140.0,
            workRadiusMeters: Number(workRadiusMeters) || 75.0,
            memberIds: []
        };

        db.saveCircle(circle);
        db.logEvent('CIRCLE', `Admin created new circle: "${circle.name}" (${circle.inviteCode})`, { circleId });

        return res.status(201).json({ success: true, circle });
    } catch (err) {
        console.error('[Admin Create Circle Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

// 5. UPDATE CIRCLE (Geofences, calibrations, names)
router.put('/circles/:circleId', requireAdmin, (req, res) => {
    try {
        const { circleId } = req.params;
        const updates = req.body;
        const existing = db.getCircleById(circleId);

        if (!existing) {
            return res.status(404).json({ success: false, error: 'Circle not found' });
        }

        const allowedFields = [
            'name', 'homeLat', 'homeLng', 'isHomeCalibrated', 'homeRadiusMeters',
            'workLat', 'workLng', 'isWorkCalibrated', 'workRadiusMeters', 'inviteCode', 'legacyPin'
        ];

        const sanitizedUpdates = {};
        allowedFields.forEach(field => {
            if (updates[field] !== undefined) {
                if (['homeLat', 'homeLng', 'workLat', 'workLng', 'homeRadiusMeters', 'workRadiusMeters'].includes(field)) {
                    sanitizedUpdates[field] = Number(updates[field]);
                } else if (['isHomeCalibrated', 'isWorkCalibrated'].includes(field)) {
                    sanitizedUpdates[field] = Boolean(updates[field]);
                } else {
                    sanitizedUpdates[field] = updates[field];
                }
            }
        });

        const updatedCircle = db.updateCircle(circleId, sanitizedUpdates);
        db.logEvent('CIRCLE', `Admin updated circle settings for "${updatedCircle.name}"`, { circleId });

        return res.json({ success: true, circle: updatedCircle });
    } catch (err) {
        console.error('[Admin Update Circle Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

// 6. DELETE CIRCLE
router.delete('/circles/:circleId', requireAdmin, (req, res) => {
    try {
        const { circleId } = req.params;
        const circle = db.getCircleById(circleId);
        if (!circle) {
            return res.status(404).json({ success: false, error: 'Circle not found' });
        }

        db.deleteCircle(circleId);
        db.logEvent('CIRCLE', `Admin deleted circle: "${circle.name}" (${circleId})`);
        return res.json({ success: true, message: 'Circle deleted' });
    } catch (err) {
        console.error('[Admin Delete Circle Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

// 7. REGENERATE INVITE CODE
router.post('/circles/:circleId/regenerate-code', requireAdmin, (req, res) => {
    try {
        const { circleId } = req.params;
        const circle = db.getCircleById(circleId);
        if (!circle) {
            return res.status(404).json({ success: false, error: 'Circle not found' });
        }

        const newCode = generateInviteCode();
        circle.inviteCode = newCode;
        circle.lastUpdated = Date.now();
        db.saveCircle(circle);
        db.logEvent('CIRCLE', `Regenerated invite code for "${circle.name}": ${newCode}`);

        return res.json({ success: true, inviteCode: newCode, circle });
    } catch (err) {
        console.error('[Admin Regen Code Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

// 8. UPDATE MEMBER (Teleport, rename, toggle pause, battery)
router.post('/members/:memberId/update', requireAdmin, (req, res) => {
    try {
        const { memberId } = req.params;
        const updates = req.body;
        const member = db.getMember(memberId);

        if (!member) {
            return res.status(404).json({ success: false, error: 'Member not found' });
        }

        if (updates.name !== undefined) member.name = String(updates.name).trim();
        if (updates.lat !== undefined) member.y = Number(updates.lat);
        if (updates.lng !== undefined) member.x = Number(updates.lng);
        if (updates.batteryPercentage !== undefined) member.batteryPercentage = Number(updates.batteryPercentage);
        if (updates.isCharging !== undefined) member.isCharging = Boolean(updates.isCharging);
        if (updates.statusText !== undefined) member.statusText = String(updates.statusText);
        if (updates.isLocationPaused !== undefined) member.isLocationPaused = Boolean(updates.isLocationPaused);
        if (updates.speedMph !== undefined) member.speedMph = Number(updates.speedMph);
        if (updates.avatarEmoji !== undefined) member.avatarEmoji = String(updates.avatarEmoji);
        if (updates.avatarColorHex !== undefined) member.avatarColorHex = String(updates.avatarColorHex);

        member.lastActive = Date.now();
        db.saveMember(member);

        db.logEvent('MEMBER', `Admin updated member: ${member.name} (${member.id})`, { memberId });
        return res.json({ success: true, member });
    } catch (err) {
        console.error('[Admin Update Member Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

// 9. TOGGLE ALARM / SOS FOR MEMBER
router.post('/members/:memberId/toggle-alarm', requireAdmin, async (req, res) => {
    try {
        const { memberId } = req.params;
        const member = db.getMember(memberId);
        if (!member) {
            return res.status(404).json({ success: false, error: 'Member not found' });
        }

        const isCurrentlyAlarm = member.statusText === '🚨 ALARM';
        if (isCurrentlyAlarm) {
            const result = await stopRingMemberPhone(memberId);
            return res.json({ success: true, isAlarm: false, isRinging: false, member: result.member || member });
        } else {
            const result = await ringMemberPhone(memberId, 20000);
            return res.json({ success: true, isAlarm: true, isRinging: true, member: result.member || member });
        }
    } catch (err) {
        console.error('[Admin Toggle Alarm Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

// 9b. EXPLICIT RING MEMBER PHONE (Over-the-Air High-Decibel Siren)
router.post('/members/:memberId/ring', requireAdmin, async (req, res) => {
    try {
        const { memberId } = req.params;
        const durationMs = parseInt(req.body?.durationMs, 10) || 20000;
        const result = await ringMemberPhone(memberId, durationMs);
        if (!result.success) {
            return res.status(404).json(result);
        }
        return res.json({ success: true, message: `Ringing ${result.member.name}'s phone`, ...result });
    } catch (err) {
        console.error('[Admin Ring Member Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

// 9c. EXPLICIT STOP RINGING MEMBER PHONE
router.post('/members/:memberId/stop-ring', requireAdmin, async (req, res) => {
    try {
        const { memberId } = req.params;
        const result = await stopRingMemberPhone(memberId);
        if (!result.success) {
            return res.status(404).json(result);
        }
        return res.json({ success: true, message: `Stopped ringing ${result.member.name}'s phone`, ...result });
    } catch (err) {
        console.error('[Admin Stop Ring Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

// 10. REMOVE MEMBER FROM CIRCLE
router.delete('/circles/:circleId/members/:memberId', requireAdmin, (req, res) => {
    try {
        const { circleId, memberId } = req.params;
        const member = db.getMember(memberId);
        const memberName = member?.name || memberId;

        db.removeMemberFromCircle(circleId, memberId);
        db.logEvent('MEMBER', `Admin removed member "${memberName}" from circle ${circleId}`, { circleId, memberId });

        return res.json({ success: true, message: `Member ${memberName} removed successfully` });
    } catch (err) {
        console.error('[Admin Remove Member Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

// 11. BROADCAST MESSAGE / NOTIFICATION
router.post('/broadcast', requireAdmin, (req, res) => {
    try {
        const { circleId, message, statusText } = req.body;
        if (!statusText && !message) {
            return res.status(400).json({ success: false, error: 'Message or statusText is required' });
        }

        const textToSet = statusText || message;
        let count = 0;

        if (circleId) {
            const members = db.getCircleMembers(circleId);
            members.forEach(m => {
                m.statusText = textToSet;
                m.lastActive = Date.now();
                db.saveMember(m);
                count++;
            });
        } else {
            const allCircles = db.getAllCircles();
            allCircles.forEach(c => {
                (c.members || []).forEach(m => {
                    m.statusText = textToSet;
                    m.lastActive = Date.now();
                    db.saveMember(m);
                    count++;
                });
            });
        }

        db.logEvent('BROADCAST', `Admin broadcast message: "${textToSet}" to ${count} members`);
        return res.json({ success: true, message: `Broadcast sent to ${count} member(s)` });
    } catch (err) {
        console.error('[Admin Broadcast Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

// 12. GET RECENT ACTIVITY LOGS
router.get('/events', requireAdmin, (req, res) => {
    try {
        const limit = parseInt(req.query.limit, 10) || 50;
        const events = db.getRecentEvents(limit);
        return res.json({ success: true, events });
    } catch (err) {
        console.error('[Admin Events Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

// 13. EXPORT DATABASE SNAPSHOT
router.get('/backup/export', requireAdmin, (req, res) => {
    try {
        const data = db.exportDatabase();
        res.setHeader('Content-Type', 'application/json');
        res.setHeader('Content-Disposition', `attachment; filename=kintracker_backup_${Date.now()}.json`);
        return res.send(JSON.stringify(data, null, 2));
    } catch (err) {
        console.error('[Admin Export Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

// 14. IMPORT DATABASE SNAPSHOT
router.post('/backup/import', requireAdmin, (req, res) => {
    try {
        const snapshot = req.body;
        if (!snapshot || typeof snapshot !== 'object') {
            return res.status(400).json({ success: false, error: 'Invalid JSON snapshot' });
        }
        const stats = db.importDatabase(snapshot);
        db.logEvent('ADMIN', 'Admin restored database from snapshot backup');
        return res.json({ success: true, message: 'Database restored successfully', stats });
    } catch (err) {
        console.error('[Admin Import Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

module.exports = router;
