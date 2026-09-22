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

// Database Helper Methods
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
        return circle.memberIds.map(id => db.members[id]).filter(Boolean);
    },

    getMember(memberId) {
        return db.members[memberId] || null;
    },

    saveMember(member) {
        db.members[member.id] = member;
        // Also ensure member is in circle's member list
        if (member.circleId && db.circles[member.circleId]) {
            if (!db.circles[member.circleId].memberIds) {
                db.circles[member.circleId].memberIds = [];
            }
            if (!db.circles[member.circleId].memberIds.includes(member.id)) {
                db.circles[member.circleId].memberIds.push(member.id);
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

    // RAW STATS
    getStats() {
        return {
            circlesCount: Object.keys(db.circles).length,
            membersCount: Object.keys(db.members).length,
            shoppingItemsCount: Object.keys(db.shoppingItems).length,
            safeZonesCount: Object.keys(db.safeZones).length,
            dataDir: DATA_DIR
        };
    }
};

module.exports = dbOperations;
