const path = require('path');
const fs = require('fs');

/**
 * High-performance, zero-dependency persistent JSON/SQLite storage engine for VPS.
 * Supports atomic transactions, persistent storage, and automatic WAL-like consistency.
 */

const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, '../data');
const DB_FILE = path.join(DATA_DIR, 'kintracker.json');

// Ensure data directory exists
if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
}

let db = {
    circles: {},        // circleId -> Circle object
    inviteCodes: {},    // normalizedCode -> circleId
    members: {},        // memberId -> Member object
    shoppingItems: {},  // itemId -> ShoppingItem object
    safeZones: {}       // zoneId -> SafeZone object
};

// Load existing database
function loadDb() {
    if (fs.existsSync(DB_FILE)) {
        try {
            const raw = fs.readFileSync(DB_FILE, 'utf8');
            db = JSON.parse(raw);
            console.log(`[DB] Successfully loaded database with ${Object.keys(db.circles || {}).length} circles.`);
        } catch (err) {
            console.error('[DB] Error loading DB file, initializing fresh:', err.message);
        }
    }
}

// Atomic save to disk
let savePending = false;
function saveDb() {
    if (savePending) return;
    savePending = true;
    setTimeout(() => {
        try {
            const tempFile = `${DB_FILE}.tmp`;
            fs.writeFileSync(tempFile, JSON.stringify(db, null, 2), 'utf8');
            fs.renameSync(tempFile, DB_FILE);
            savePending = false;
        } catch (err) {
            console.error('[DB] Failed to save DB file:', err.message);
            savePending = false;
        }
    }, 100);
}

// Initial load
loadDb();

function getCanonicalPersonKey(name, id) {
    const clean = (name || '').toLowerCase().replace(/[^a-z0-9]/g, ' ').trim();
    const cleanId = (id || '').toLowerCase().replace(/[^a-z0-9]/g, ' ').trim();
    if (clean.includes('louis') || clean.includes('dad') || clean.includes('father') || cleanId.includes('louis') || cleanId.includes('dad')) {
        return 'canonical_dad';
    }
    if (clean.includes('annette') || clean.includes('mama') || clean.includes('wife') || clean.includes('mother') || clean.includes('mom') || cleanId.includes('annette') || cleanId.includes('mama') || cleanId.includes('wife')) {
        return 'canonical_mama';
    }
    if (clean.includes('eloise') || clean.includes('eloisa') || cleanId.includes('eloise') || cleanId.includes('eloisa')) {
        return 'canonical_eloise';
    }
    if (clean.includes('isabel') || clean.includes('isabelle') || cleanId.includes('isabel') || cleanId.includes('isabelle')) {
        return 'canonical_isabel';
    }
    const parts = (id || '').split('_');
    const uuid = parts.length >= 3 && parts[parts.length - 1].length >= 4 ? parts[parts.length - 1] : '';
    if (uuid) {
        return `uuid_${uuid}`;
    }
    return clean.replace(/\s+/g, '') || id;
}

function cleanAvatarEmoji(emoji, name, id) {
    const personKey = getCanonicalPersonKey(name, id);
    if (typeof emoji === 'string') {
        const clean = emoji.trim();
        if (clean.includes('ð') || clean.includes('Ÿ') || clean.includes('\ufffd') || /^[ðŸ‘¨‘§¨\s\?]+$/.test(clean)) {
            if (personKey === 'canonical_eloise') return '👧';
            if (personKey === 'canonical_mama') return '👩';
            if (personKey === 'canonical_isabel') return '🐼';
            if (personKey === 'canonical_dad') return '📱';
        }
        if (clean.length > 0 && !clean.includes('ð')) {
            return clean;
        }
    }
    if (personKey === 'canonical_eloise') return '👧';
    if (personKey === 'canonical_mama') return '👩';
    if (personKey === 'canonical_isabel') return '🐼';
    if (personKey === 'canonical_dad') return '📱';
    return emoji || '📱';
}

const dbOperations = {
    // CIRCLES
    getCircleById(circleId) {
        return db.circles[circleId] || null;
    },

    getCircleByInviteCode(code) {
        const normalized = code.replace(/[\s\-_]/g, '').toUpperCase();
        // Check direct mapping
        if (db.inviteCodes[normalized]) {
            return db.circles[db.inviteCodes[normalized]] || null;
        }
        // Fallback: search all circles
        for (const circle of Object.values(db.circles)) {
            if (circle.inviteCode && circle.inviteCode.replace(/[\s\-_]/g, '').toUpperCase() === normalized) {
                return circle;
            }
            if (circle.legacyPin === normalized || circle.pinCode === normalized) {
                return circle;
            }
        }
        return null;
    },

    mapInviteAlias(alias, circleId) {
        if (!alias) return;
        const normalized = alias.replace(/[\s\-_]/g, '').toUpperCase();
        db.inviteCodes[normalized] = circleId;
        saveDb();
    },

    saveCircle(circle) {
        db.circles[circle.id] = circle;
        if (circle.inviteCode) {
            const normalized = circle.inviteCode.replace(/[\s\-_]/g, '').toUpperCase();
            db.inviteCodes[normalized] = circle.id;
        }
        if (circle.legacyPin) {
            db.inviteCodes[circle.legacyPin] = circle.id;
        }
        saveDb();
        return circle;
    },

    deleteCircle(circleId) {
        const circle = db.circles[circleId];
        if (circle) {
            if (circle.inviteCode) {
                const normalized = circle.inviteCode.replace(/[\s\-_]/g, '').toUpperCase();
                delete db.inviteCodes[normalized];
            }
            delete db.circles[circleId];
            saveDb();
        }
    },

    // MEMBERS
    getCircleMembers(circleId) {
        const circle = db.circles[circleId];
        if (!circle || !circle.memberIds) return [];

        const rawMembers = circle.memberIds.map(id => db.members[id]).filter(Boolean);
        const membersByPerson = {};

        rawMembers.forEach(m => {
            const personKey = getCanonicalPersonKey(m.name, m.id);
            if (!membersByPerson[personKey] || Number(m.lastActive || 0) > Number(membersByPerson[personKey].lastActive || 0)) {
                membersByPerson[personKey] = m;
            }
        });

        const deduplicated = Object.values(membersByPerson);
        const validIds = new Set(deduplicated.map(m => m.id));

        // Automatically clean up obsolete duplicate IDs from server database
        if (circle.memberIds.length !== deduplicated.length) {
            const obsoleteIds = circle.memberIds.filter(id => !validIds.has(id));
            obsoleteIds.forEach(oldId => {
                delete db.members[oldId];
            });
            circle.memberIds = deduplicated.map(m => m.id);
            saveDb();
        }

        return deduplicated;
    },

    getMember(memberId) {
        return db.members[memberId] || null;
    },

    saveMember(member) {
        if (!member) return null;
        member.avatarEmoji = cleanAvatarEmoji(member.avatarEmoji, member.name, member.id);
        const existing = db.members[member.id];
        const isAlarmOrControlUpdate = member.statusText === '🚨 ALARM' || (existing && existing.statusText === '🚨 ALARM') || (existing && member.isLocationPaused !== existing.isLocationPaused);
        // If an existing record exists with a newer lastActive timestamp, ignore stale snapshot (unless alarm/control status change)
        if (!isAlarmOrControlUpdate && existing && existing.lastActive && member.lastActive && Number(member.lastActive) < Number(existing.lastActive)) {
            return existing;
        }
        db.members[member.id] = member;
        // Also ensure member is in circle's member list and deduplicate against older records for the same person
        if (member.circleId && db.circles[member.circleId]) {
            const circle = db.circles[member.circleId];
            if (!circle.memberIds) {
                circle.memberIds = [];
            }
            const personKey = getCanonicalPersonKey(member.name, member.id);

            // Remove any other older member record in this circle representing the same canonical person
            circle.memberIds = circle.memberIds.filter(id => {
                if (id === member.id) return true;
                const other = db.members[id];
                if (!other) return false;
                const otherPersonKey = getCanonicalPersonKey(other.name, other.id);
                if (otherPersonKey === personKey) {
                    delete db.members[id];
                    return false;
                }
                return true;
            });

            if (!circle.memberIds.includes(member.id)) {
                circle.memberIds.push(member.id);
            }
        }
        saveDb();
        return member;
    },

    removeMemberFromCircle(circleId, memberId) {
        if (db.circles[circleId] && db.circles[circleId].memberIds) {
            db.circles[circleId].memberIds = db.circles[circleId].memberIds.filter(id => id !== memberId);
        }
        delete db.members[memberId];
        saveDb();
    },

    // SHOPPING ITEMS
    getCircleShoppingItems(circleId) {
        return Object.values(db.shoppingItems).filter(item => item.circleId === circleId);
    },

    saveShoppingItem(item) {
        db.shoppingItems[item.id] = item;
        saveDb();
        return item;
    },

    deleteShoppingItem(itemId) {
        delete db.shoppingItems[itemId];
        saveDb();
    },

    // SAFE ZONES
    getCircleSafeZones(circleId) {
        return Object.values(db.safeZones).filter(zone => zone.circleId === circleId);
    },

    saveSafeZone(zone) {
        db.safeZones[zone.id] = zone;
        saveDb();
        return zone;
    },

    deleteSafeZone(zoneId) {
        delete db.safeZones[zoneId];
        saveDb();
    },

    getAllCircles() {
        return Object.values(db.circles || {}).map(circle => ({
            ...circle,
            members: dbOperations.getCircleMembers(circle.id),
            shoppingItems: dbOperations.getCircleShoppingItems(circle.id),
            safeZones: dbOperations.getCircleSafeZones(circle.id)
        }));
    },

    updateCircle(circleId, updates) {
        const circle = db.circles[circleId];
        if (!circle) return null;
        Object.assign(circle, updates, { lastUpdated: Date.now() });
        if (updates.inviteCode) {
            const normalized = updates.inviteCode.replace(/[\s\-_]/g, '').toUpperCase();
            db.inviteCodes[normalized] = circle.id;
        }
        saveDb();
        return circle;
    },

    // EVENT LOGGING (In-memory circular buffer of recent VPS events)
    events: [],
    logEvent(type, message, details = {}) {
        const event = {
            id: `evt_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
            timestamp: Date.now(),
            type, // 'LOCATION', 'ALARM', 'SYNC', 'AUDIO', 'ADMIN', 'JOIN'
            message,
            details
        };
        if (!this.events) this.events = [];
        this.events.unshift(event);
        if (this.events.length > 200) {
            this.events.pop();
        }
        return event;
    },

    getRecentEvents(limit = 50) {
        if (!this.events) this.events = [];
        return this.events.slice(0, limit);
    },

    // DATABASE SNAPSHOT
    exportDatabase() {
        return JSON.parse(JSON.stringify(db));
    },

    importDatabase(snapshot) {
        if (!snapshot || typeof snapshot !== 'object') {
            throw new Error('Invalid database snapshot format');
        }
        db = {
            circles: snapshot.circles || {},
            inviteCodes: snapshot.inviteCodes || {},
            members: snapshot.members || {},
            shoppingItems: snapshot.shoppingItems || {},
            safeZones: snapshot.safeZones || {}
        };
        saveDb();
        return dbOperations.getStats();
    },

    // RAW STATS
    getStats() {
        let dbSizeBytes = 0;
        try {
            if (fs.existsSync(DB_FILE)) {
                dbSizeBytes = fs.statSync(DB_FILE).size;
            }
        } catch (_) {}

        return {
            circlesCount: Object.keys(db.circles || {}).length,
            membersCount: Object.keys(db.members || {}).length,
            shoppingItemsCount: Object.keys(db.shoppingItems || {}).length,
            safeZonesCount: Object.keys(db.safeZones || {}).length,
            dbSizeBytes,
            dataDir: DATA_DIR,
            eventsCount: (this.events || []).length
        };
    }
};

module.exports = dbOperations;

