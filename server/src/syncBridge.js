const https = require('https');
const db = require('./db');

const SYNC_URL = 'https://api.cosmowhisper.com/sync/81e5632c_pin_group';
const activeRingTimers = new Map();

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

                            Object.values(json.members).forEach(m => {
                                if (m && m.id) {
                                    db.saveMember({
                                        ...m,
                                        circleId: circle.id
                                    });
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
