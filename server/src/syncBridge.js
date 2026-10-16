const https = require('https');
const db = require('./db');

const SYNC_URL = 'https://api.cosmowhisper.com/sync/81e5632c_pin_group';
const activeRingTimers = new Map();

function distanceMeters(lat1, lon1, lat2, lon2) {
    if (!lat1 || !lon1 || !lat2 || !lon2) return Infinity;
    const R = 6371000;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
              Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

/**
 * Fetch live data from production VPS into local database
 */
async function syncLiveProductionFamily() {
    return new Promise((resolve) => {
        https.get(SYNC_URL, (res) => {
            let raw = '';
            res.on('data', chunk => raw += chunk);
            res.on('end', () => {
                try {
                    const json = JSON.parse(raw);
                    if (json && json.members) {
                        const circle = db.getCircleById('circle_default_sovereign') || db.getCircleByInviteCode('4666');
                        if (circle) {
                            if (json.homeLat) circle.homeLat = json.homeLat;
                            if (json.homeLng) circle.homeLng = json.homeLng;
                            if (json.workLat) circle.workLat = json.workLat;
                            if (json.workLng) circle.workLng = json.workLng;
                            if (json.homeRadiusMeters) circle.homeRadiusMeters = json.homeRadiusMeters;
                            if (json.workRadiusMeters) circle.workRadiusMeters = json.workRadiusMeters;
                            circle.lastUpdated = json.lastUpdated || Date.now();
                            db.saveCircle(circle);

                            const safeZones = db.getCircleSafeZones ? db.getCircleSafeZones(circle.id) : [];

                            Object.values(json.members).forEach(m => {
                                if (m && m.id) {
                                    const normalized = { ...m, circleId: circle.id };
                                    const lat = Number(normalized.y || 0);
                                    const lng = Number(normalized.x || 0);
                                    const speed = Number(normalized.speedMph || 0);
                                    const isAlarmOrPaused = normalized.statusText === '🚨 ALARM' ||
                                        normalized.isLocationPaused ||
                                        String(normalized.statusText || '').toLowerCase().includes('paused');

                                    if (!isAlarmOrPaused && lat !== 0 && lng !== 0 && speed < 3.5) {
                                        const homeDist = (circle.homeLat && circle.homeLng)
                                            ? distanceMeters(lat, lng, Number(circle.homeLat), Number(circle.homeLng))
                                            : Infinity;
                                        if (homeDist <= Math.max(Number(circle.homeRadiusMeters || 140), 150)) {
                                            normalized.x = Number(circle.homeLng);
                                            normalized.y = Number(circle.homeLat);
                                            normalized.speedMph = 0;
                                            normalized.statusText = 'At Home (Live GPS)';
                                        } else {
                                            const matchedZone = safeZones.find(z => {
                                                const isSchool = (z.iconName || '').toLowerCase() === 'school' ||
                                                    (z.name || '').toLowerCase().includes('school') ||
                                                    (z.name || '').toLowerCase().includes('byron') ||
                                                    (z.name || '').toLowerCase().includes('academy');
                                                const buffer = (speed < 1.5 && isSchool) ? 150 : 30;
                                                const effRadius = Math.max(Number(z.radiusMeters || 180), 160) + buffer;
                                                return distanceMeters(lat, lng, Number(z.latitude), Number(z.longitude)) <= effRadius;
                                            });
                                            if (matchedZone) {
                                                normalized.x = Number(matchedZone.longitude);
                                                normalized.y = Number(matchedZone.latitude);
                                                normalized.speedMph = 0;
                                                normalized.statusText = `At ${matchedZone.name}`;
                                            }
                                        }
                                    }
                                    db.saveMember(normalized);
                                }
                            });
                        }
                    }
                    resolve(true);
                } catch (_) {
                    resolve(false);
                }
            });
        }).on('error', () => resolve(false));
    });
}

/**
 * Push current local circle + members snapshot to production VPS
 */
async function pushLiveProductionFamily(customMembersMap = null) {
    return new Promise((resolve) => {
        try {
            const circle = db.getCircleById('circle_default_sovereign') || db.getCircleByInviteCode('4666') || Object.values(db.getAllCircles())[0];
            if (!circle) return resolve(false);

            const membersMap = customMembersMap || {};
            if (!customMembersMap) {
                const members = db.getCircleMembers(circle.id);
                members.forEach(m => {
                    membersMap[m.id] = m;
                });
            }

            const payload = {
                homeLat: circle.homeLat,
                homeLng: circle.homeLng,
                isHomeCalibrated: Boolean(circle.isHomeCalibrated),
                workLat: circle.workLat,
                workLng: circle.workLng,
                isWorkCalibrated: Boolean(circle.isWorkCalibrated),
                homeRadiusMeters: circle.homeRadiusMeters,
                workRadiusMeters: circle.workRadiusMeters,
                lastUpdated: Date.now(),
                members: membersMap
            };

            const data = JSON.stringify(payload);
            const url = new URL(SYNC_URL);

            const req = https.request({
                hostname: url.hostname,
                path: url.pathname,
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Content-Length': Buffer.byteLength(data)
                }
            }, (res) => {
                resolve(res.statusCode >= 200 && res.statusCode < 300);
            });

            req.on('error', () => resolve(false));
            req.write(data);
            req.end();
        } catch (_) {
            resolve(false);
        }
    });
}

/**
 * Trigger remote phone ringing for a family member
 */
async function ringMemberPhone(memberId, durationMs = 20000) {
    let member = db.getMember(memberId);
    if (!member) {
        const allCircles = db.getAllCircles();
        for (const c of allCircles) {
            const match = (c.members || []).find(m => m.id === memberId || m.name.toLowerCase() === memberId.toLowerCase());
            if (match) {
                member = match;
                break;
            }
        }
    }

    if (!member) return { success: false, error: 'Member not found' };

    const targetName = member.name;
    const personKey = db.getCanonicalPersonKey ? db.getCanonicalPersonKey(member.name, member.id) : member.id;

    // Set statusText to '🚨 ALARM' for this member and any matching devices
    const affectedMembers = [];
    const allCircles = db.getAllCircles();
    allCircles.forEach(circle => {
        (circle.members || []).forEach(m => {
            const mKey = db.getCanonicalPersonKey ? db.getCanonicalPersonKey(m.name, m.id) : m.id;
            if (m.id === member.id || mKey === personKey || m.name.toLowerCase() === targetName.toLowerCase()) {
                m.statusText = '🚨 ALARM';
                m.lastActive = Date.now();
                db.saveMember(m);
                affectedMembers.push(m);
            }
        });
    });

    db.logEvent('ALARM', `🚨 Ringing ${targetName}'s phone at maximum volume...`, { memberId: member.id, name: targetName });

    // Push immediately to production cloud
    await pushLiveProductionFamily();

    // Clear existing timer if any
    if (activeRingTimers.has(personKey)) {
        clearTimeout(activeRingTimers.get(personKey));
    }

    // Schedule auto-stop after duration
    const timer = setTimeout(async () => {
        await stopRingMemberPhone(memberId);
    }, durationMs);

    activeRingTimers.set(personKey, timer);

    return { success: true, isRinging: true, member, durationMs };
}

/**
 * Stop remote phone ringing for a family member
 */
async function stopRingMemberPhone(memberId) {
    let member = db.getMember(memberId);
    if (!member) {
        const allCircles = db.getAllCircles();
        for (const c of allCircles) {
            const match = (c.members || []).find(m => m.id === memberId || m.name.toLowerCase() === memberId.toLowerCase());
            if (match) {
                member = match;
                break;
            }
        }
    }

    if (!member) return { success: false, error: 'Member not found' };

    const targetName = member.name;
    const personKey = db.getCanonicalPersonKey ? db.getCanonicalPersonKey(member.name, member.id) : member.id;

    if (activeRingTimers.has(personKey)) {
        clearTimeout(activeRingTimers.get(personKey));
        activeRingTimers.delete(personKey);
    }

    // Reset statusText to Active / Stationary
    const allCircles = db.getAllCircles();
    allCircles.forEach(circle => {
        (circle.members || []).forEach(m => {
            const mKey = db.getCanonicalPersonKey ? db.getCanonicalPersonKey(m.name, m.id) : m.id;
            if (m.id === member.id || mKey === personKey || m.name.toLowerCase() === targetName.toLowerCase()) {
                if (m.statusText === '🚨 ALARM') {
                    m.statusText = 'Active';
                    m.lastActive = Date.now();
                    db.saveMember(m);
                }
            }
        });
    });

    db.logEvent('ALARM', `🔕 Stopped ringing ${targetName}'s phone.`, { memberId: member.id, name: targetName });

    // Push immediately to production cloud
    await pushLiveProductionFamily();

    return { success: true, isRinging: false, member };
}

module.exports = {
    syncLiveProductionFamily,
    pushLiveProductionFamily,
    ringMemberPhone,
    stopRingMemberPhone
};
