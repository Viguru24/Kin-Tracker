const http = require('http');
const fs = require('fs');
const path = require('path');
const Busboy = require('busboy');
const { WebSocketServer } = require('ws');
const { enrichMemberState, processTripLifecycle, loadCompletedTrips } = require('./tripTransitEngine');

const PORT = 3000;
const DATA_DIR = path.join(__dirname, 'kv-data');       // Group config + legacy blobs
const MEMBER_DIR = path.join(__dirname, 'member-data'); // Per-device member files
const UPDATE_DIR = path.join(__dirname, 'updates');     // OTA APK builds & metadata
const ADMIN_KEY = process.env.ADMIN_KEY || "test_secret_4666";

if (!fs.existsSync(DATA_DIR))   fs.mkdirSync(DATA_DIR,   { recursive: true });
if (!fs.existsSync(MEMBER_DIR)) fs.mkdirSync(MEMBER_DIR, { recursive: true });
if (!fs.existsSync(UPDATE_DIR)) fs.mkdirSync(UPDATE_DIR, { recursive: true });

// Known family names that are NEVER auto-deleted regardless of inactivity
const KNOWN_FAMILY = ['isabel', 'annette', 'dad', 'louis', 'wife', 'daughter', 'mama', 'mom', 'mother'];
const STALE_UNKNOWN_MS = 7 * 24 * 3600 * 1000; // 7 days for unknown/unnamed devices

function isKnownFamily(name) {
    const n = (name || '').toLowerCase().trim();
    return KNOWN_FAMILY.some(k => n.includes(k));
}

function safeKey(k) {
    return k.replace(/[^a-zA-Z0-9_\-]/g, '_');
}

// Helper: get home coords from group config
function getHomeCoords(token) {
    try {
        const file = path.join(DATA_DIR, safeKey(token) + '.json');
        if (!fs.existsSync(file)) return null;
        const d = JSON.parse(fs.readFileSync(file, 'utf8'));
        if (d.homeLat && d.homeLng && d.isHomeCalibrated) {
            return { lat: d.homeLat, lng: d.homeLng, radius: d.homeRadiusMeters || 150 };
        }
    } catch (e) {}
    return null;
}

// Distance in km
function distKm(lat1, lng1, lat2, lng2) {
    const dLat = (lat2 - lat1) * 111.0;
    const dLng = (lng2 - lng1) * 111.0 * Math.cos(lat1 * Math.PI / 180);
    return Math.sqrt(dLat * dLat + dLng * dLng);
}

// Helper: get safe zones from group config
function getSafeZones(token) {
    try {
        const file = path.join(DATA_DIR, safeKey(token) + '.json');
        if (!fs.existsSync(file)) return [];
        const d = JSON.parse(fs.readFileSync(file, 'utf8'));
        return d.safeZones || [];
    } catch (e) {}
    return [];
}

// Active WebSocket connections & recent alert events cache
const circleSockets = new Map(); // token -> Set of ws clients
const recentAlertsMap = new Map(); // token -> array of alert events

function broadcastMemberUpdate(token, member) {
    const sockets = circleSockets.get(token);
    if (!sockets || sockets.size === 0) return;
    const msg = JSON.stringify({ type: 'MEMBER_UPDATE', token, member });
    for (const ws of sockets) {
        if (ws.readyState === 1) ws.send(msg);
    }
}

function broadcastAlertEvent(token, alert) {
    if (!recentAlertsMap.has(token)) recentAlertsMap.set(token, []);
    const list = recentAlertsMap.get(token);
    list.push(alert);
    if (list.length > 20) list.shift();

    const sockets = circleSockets.get(token);
    if (!sockets || sockets.size === 0) return;
    const msg = JSON.stringify({ type: 'ALERT', token, alert });
    for (const ws of sockets) {
        if (ws.readyState === 1) ws.send(msg);
    }
}

function broadcastCompletedTrip(token, trip) {
    const sockets = circleSockets.get(token);
    if (!sockets || sockets.size === 0) return;
    const msg = JSON.stringify({ type: 'COMPLETED_TRIP', token, trip });
    for (const ws of sockets) {
        if (ws.readyState === 1) ws.send(msg);
    }
}

// Save member - only if newer, enriched with real-time transit & destination intelligence
function saveMember(token, memberId, data) {
    const file = path.join(MEMBER_DIR, safeKey(token) + '__' + safeKey(memberId) + '.json');
    let stored = null;
    try {
        if (fs.existsSync(file)) {
            stored = JSON.parse(fs.readFileSync(file, 'utf8'));
            if ((stored.lastActive || 0) >= (data.lastActive || 0)) return stored;
        }
    } catch (e) {}

    const home = getHomeCoords(token);
    const safeZones = getSafeZones(token);
    const out = enrichMemberState(token, memberId, data, stored, home, safeZones);

    const { completedTrip, alertEvent } = processTripLifecycle(token, memberId, out, stored, home, safeZones);

    fs.writeFileSync(file, JSON.stringify(out), 'utf8');

    broadcastMemberUpdate(token, out);
    if (alertEvent) broadcastAlertEvent(token, alertEvent);
    if (completedTrip) broadcastCompletedTrip(token, completedTrip);

    return out;
}

// Load all members for a token (respecting stale rules)
function loadAllMembers(token) {
    const prefix = safeKey(token) + '__';
    const now = Date.now();
    const members = {};
    if (!fs.existsSync(MEMBER_DIR)) return members;
    for (const file of fs.readdirSync(MEMBER_DIR)) {
        if (!file.startsWith(prefix) || !file.endsWith('.json')) continue;
        try {
            const m = JSON.parse(fs.readFileSync(path.join(MEMBER_DIR, file), 'utf8'));
            const age = now - (m.lastActive || 0);
            if (isKnownFamily(m.name) || age < STALE_UNKNOWN_MS) {
                members[m.id] = m;
            } else {
                // Clean up truly stale unknown device files
                fs.unlink(path.join(MEMBER_DIR, file), () => {});
            }
        } catch (e) {}
    }
    return members;
}

// Load group config (everything except members)
function loadGroupConfig(token) {
    const file = path.join(DATA_DIR, `${safeKey(token)}.json`);
    if (!fs.existsSync(file)) return null;
    try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch (e) { return null; }
}

// Save group config (strip members ? they live in member-data)
function saveGroupConfig(token, payload) {
    const config = { ...payload };
    delete config.members; // stored separately
    const file = path.join(DATA_DIR, `${safeKey(token)}.json`);
    fs.writeFileSync(file, JSON.stringify(config), 'utf8');
}

// Smart PUT: update caller's member, merge with all known members, persist
function handleSmartPut(token, incoming, res, req) {
    const incomingMembers = incoming.members || {};
    for (const [id, member] of Object.entries(incomingMembers)) {
        saveMember(token, id, member);
    }

    saveGroupConfig(token, incoming);

    const allMembers = loadAllMembers(token);

    const merged = { ...incoming, members: allMembers, lastUpdated: Date.now() };
    const file = path.join(DATA_DIR, `${safeKey(token)}.json`);
    fs.writeFileSync(file, JSON.stringify(merged), 'utf8');

    const details = Object.values(incomingMembers).map(m => `${m.id}(${m.name}, bat=${m.batteryPercentage}%, act=${m.lastActive})`).join('; ');
    console.log(`[${new Date().toISOString()}] PUT ${token} from ${(req.headers['x-forwarded-for'] || req.socket.remoteAddress)} | incoming details: [${details}]`);

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: true }));
}

// GET: serve merged payload (always fresh with trips and alerts)
function handleGet(token, res) {
    const config = loadGroupConfig(token);
    if (!config) {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Not found' }));
        return;
    }
    const allMembers = loadAllMembers(token);
    const completedTrips = loadCompletedTrips(token);
    const alerts = recentAlertsMap.get(token) || [];
    const payload = {
        ...config,
        members: allMembers,
        completedTrips: completedTrips.slice(-30),
        recentAlerts: alerts.slice(-10)
    };
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(payload));
}

// Migrate existing kv-data blobs
function migrateExistingData() {
    if (!fs.existsSync(DATA_DIR)) return;
    for (const file of fs.readdirSync(DATA_DIR)) {
        if (!file.endsWith('.json')) continue;
        try {
            const raw = fs.readFileSync(path.join(DATA_DIR, file), 'utf8');
            const data = JSON.parse(raw);
            if (!data.members || typeof data.members !== 'object') continue;
            const token = file.replace('.json', '');
            let migrated = 0;
            for (const [id, member] of Object.entries(data.members)) {
                const memberFile = path.join(MEMBER_DIR, `${safeKey(token)}__${safeKey(id)}.json`);
                if (!fs.existsSync(memberFile)) {
                    fs.writeFileSync(memberFile, JSON.stringify(member), 'utf8');
                    migrated++;
                }
            }
            if (migrated > 0) console.log(`[MIGRATE] ${file}: extracted ${migrated} member(s) to member-data/`);
        } catch (e) {}
    }
}

// --- MULTI-APP OTA UPDATE HANDLERS ---
const APP_ALIASES = {
    // Kin-Tracker (Default)
    'kin-tracker': 'kin-tracker',
    'kintracker': 'kin-tracker',
    'familytracker': 'kin-tracker',
    'com.aistudio.familytracker.lnvwe': 'kin-tracker',
    'default': 'kin-tracker',

    // Cosmo Symphony Companion
    'cosmo-companion': 'cosmo-companion',
    'companion': 'cosmo-companion',
    'cosmo-symphony-companion': 'cosmo-companion',
    'com.cosmo.symphony.companion': 'cosmo-companion',

    // Jinshin Jyutsu Android
    'jinshin-jyutsu': 'jinshin-jyutsu',
    'jinshin': 'jinshin-jyutsu',
    'jin-shin': 'jinshin-jyutsu',
    'jinshinflow': 'jinshin-jyutsu',
    'com.aistudio.jinshinflow.kyztmp': 'jinshin-jyutsu',

    // Cosmo Flow
    'cosmo-flow': 'cosmo-flow',
    'cosmoflow': 'cosmo-flow',
    'flow': 'cosmo-flow',
    'com.aistudio.cosmoflow.vxbk': 'cosmo-flow',

    // Cooking Application (Cosmo Compendium / Heirloom Recipes)
    'cosmo-compendium': 'cosmo-compendium',
    'compendium': 'cosmo-compendium',
    'cookbook': 'cosmo-compendium',
    'recipes': 'cosmo-compendium',
    'cooking': 'cosmo-compendium',
    'heirloom': 'cosmo-compendium',
    'com.aistudio.heirloomrecipes.xkvp': 'cosmo-compendium'
};

function resolveAppId(rawId) {
    if (!rawId) return 'kin-tracker';
    const clean = String(rawId).toLowerCase().trim().replace(/[\s_]/g, '-');
    return APP_ALIASES[clean] || APP_ALIASES[rawId.toLowerCase()] || clean;
}

function getAppUpdateDir(appId) {
    const resolved = resolveAppId(appId);
    const dir = path.join(UPDATE_DIR, resolved);
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }
    // Backward-compatibility: if kin-tracker dir is missing files but root UPDATE_DIR has them
    if (resolved === 'kin-tracker') {
        const rootVersion = path.join(UPDATE_DIR, 'version.json');
        const rootApk = path.join(UPDATE_DIR, 'app-latest.apk');
        const appVersion = path.join(dir, 'version.json');
        const appApk = path.join(dir, 'app-latest.apk');
        if (!fs.existsSync(appVersion) && fs.existsSync(rootVersion)) {
            try { fs.copyFileSync(rootVersion, appVersion); } catch (_) {}
        }
        if (!fs.existsSync(appApk) && fs.existsSync(rootApk)) {
            try { fs.copyFileSync(rootApk, appApk); } catch (_) {}
        }
    }
    return { resolved, dir };
}

function validateGroupOrAdmin(req) {
    const parsedUrl = new URL(req.url, 'http://localhost');
    const queryToken = parsedUrl.searchParams.get('token');
    const headerToken = req.headers['x-group-token'];
    const adminKey = req.headers['x-admin-key'];
    const token = (queryToken || headerToken || '').toLowerCase().replace(/[\s\-_]/g, '');
    const isFamily = token.includes('81e5632c') || token === '4666' || token.includes('pingroup') ||
                     token.includes('companion') || token.includes('jinshin') || token.includes('flow') ||
                     token.includes('cookbook') || token.includes('heirloom') || token.length >= 4;
    const isAdmin = adminKey && adminKey === ADMIN_KEY;
    return isFamily || isAdmin;
}

function handleUpdates(req, res) {
    const parsedUrl = new URL(req.url, 'http://localhost');
    const pathname = parsedUrl.pathname;
    const parts = pathname.replace(/^\/updates\/?/, '').split('/').filter(Boolean);

    let reqAppId = parsedUrl.searchParams.get('app') || req.headers['x-app-id'] || 'kin-tracker';
    let action = '';

    if (parts.length === 0) {
        action = 'status';
    } else if (parts.length === 1) {
        if (parts[0] === 'status' || parts[0] === 'list') {
            action = 'status';
        } else if (parts[0] === 'version.json') {
            action = 'version';
        } else if (parts[0] === 'app-latest.apk') {
            action = 'apk';
        } else if (parts[0] === 'upload') {
            action = 'upload';
        } else {
            reqAppId = parts[0];
            action = 'status_single';
        }
    } else if (parts.length >= 2) {
        reqAppId = parts[0];
        if (parts[1] === 'version.json') action = 'version';
        else if (parts[1] === 'app-latest.apk') action = 'apk';
        else if (parts[1] === 'upload') action = 'upload';
        else action = parts[1];
    }

    // 0. GET /updates/status or /updates/list
    if (req.method === 'GET' && action === 'status') {
        const apps = {};
        const knownDirs = ['kin-tracker', 'cosmo-companion', 'jinshin-jyutsu', 'cosmo-flow', 'cosmo-compendium'];
        
        if (fs.existsSync(UPDATE_DIR)) {
            const entries = fs.readdirSync(UPDATE_DIR, { withFileTypes: true });
            entries.forEach(e => {
                if (e.isDirectory() && !knownDirs.includes(e.name)) {
                    knownDirs.push(e.name);
                }
            });
        }

        knownDirs.forEach(slug => {
            const dir = path.join(UPDATE_DIR, slug);
            const vFile = path.join(dir, 'version.json');
            const apkFile = path.join(dir, 'app-latest.apk');
            if (fs.existsSync(vFile)) {
                try {
                    const meta = JSON.parse(fs.readFileSync(vFile, 'utf8'));
                    const apkStat = fs.existsSync(apkFile) ? fs.statSync(apkFile) : null;
                    apps[slug] = {
                        ...meta,
                        apkSize: apkStat ? apkStat.size : 0,
                        apkExists: Boolean(apkStat)
                    };
                } catch (_) {}
            }
        });

        // Also check root if kin-tracker not found in subfolder
        if (!apps['kin-tracker']) {
            const rootV = path.join(UPDATE_DIR, 'version.json');
            const rootApk = path.join(UPDATE_DIR, 'app-latest.apk');
            if (fs.existsSync(rootV)) {
                try {
                    const meta = JSON.parse(fs.readFileSync(rootV, 'utf8'));
                    const apkStat = fs.existsSync(rootApk) ? fs.statSync(rootApk) : null;
                    apps['kin-tracker'] = {
                        ...meta,
                        apkSize: apkStat ? apkStat.size : 0,
                        apkExists: Boolean(apkStat)
                    };
                } catch (_) {}
            }
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            server: 'Sovereign VPS Multi-App OTA Hub',
            status: 'online',
            totalApps: Object.keys(apps).length,
            apps
        }, null, 2));
        return;
    }

    const { resolved: appId, dir: appDir } = getAppUpdateDir(reqAppId);

    // 1. GET version.json
    if (req.method === 'GET' && action === 'version') {
        if (!validateGroupOrAdmin(req)) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Unauthorized access to update channel' }));
            return;
        }

        let vFile = path.join(appDir, 'version.json');
        if (!fs.existsSync(vFile) && appId === 'kin-tracker') {
            vFile = path.join(UPDATE_DIR, 'version.json');
        }

        if (!fs.existsSync(vFile)) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: `No update metadata found for app '${appId}'` }));
            return;
        }

        const data = fs.readFileSync(vFile, 'utf8');
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(data);
        return;
    }

    // 2. GET app-latest.apk
    if (req.method === 'GET' && action === 'apk') {
        if (!validateGroupOrAdmin(req)) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Unauthorized access to update channel' }));
            return;
        }

        let apkFile = path.join(appDir, 'app-latest.apk');
        if (!fs.existsSync(apkFile) && appId === 'kin-tracker') {
            apkFile = path.join(UPDATE_DIR, 'app-latest.apk');
        }

        if (!fs.existsSync(apkFile)) {
            res.writeHead(404, { 'Content-Type': 'text/plain' });
            res.end(`APK file not found for app '${appId}'`);
            return;
        }

        const stat = fs.statSync(apkFile);
        res.writeHead(200, {
            'Content-Type': 'application/vnd.android.package-archive',
            'Content-Disposition': `attachment; filename="${appId}-update.apk"`,
            'Content-Length': stat.size
        });
        fs.createReadStream(apkFile).pipe(res);
        return;
    }

    // 3. POST upload
    if (req.method === 'POST' && action === 'upload') {
        const adminKey = req.headers['x-admin-key'];
        if (adminKey !== ADMIN_KEY) {
            res.writeHead(403, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Forbidden: Invalid Admin Key' }));
            return;
        }

        const bb = Busboy({ headers: req.headers, limits: { fileSize: 150 * 1024 * 1024 } });
        const fields = {};
        let targetAppId = reqAppId;
        let fileWriteFinished = Promise.resolve();
        let targetDir = appDir;

        bb.on('field', (name, val) => {
            fields[name] = val;
            if (name === 'appId' || name === 'app') {
                targetAppId = resolveAppId(val);
                targetDir = getAppUpdateDir(targetAppId).dir;
            }
        });

        bb.on('file', (name, file, info) => {
            if (name === 'apk') {
                const saveTo = path.join(targetDir, 'app-latest.apk');
                const writeStream = fs.createWriteStream(saveTo);
                fileWriteFinished = new Promise((resolve, reject) => {
                    writeStream.on('finish', resolve);
                    writeStream.on('error', reject);
                });
                file.pipe(writeStream);
            } else {
                file.resume();
            }
        });

        bb.on('finish', async () => {
            try {
                await fileWriteFinished;
                const finalAppId = resolveAppId(fields.appId || fields.app || targetAppId);
                const finalDir = getAppUpdateDir(finalAppId).dir;

                const savedFile = path.join(targetDir, 'app-latest.apk');
                const finalFile = path.join(finalDir, 'app-latest.apk');
                if (savedFile !== finalFile && fs.existsSync(savedFile)) {
                    fs.copyFileSync(savedFile, finalFile);
                }

                const vCode = parseInt(fields.versionCode, 10);
                const vName = String(fields.versionName || '1.0').trim();
                const notes = fields.releaseNotes || 'Bug fixes and stability updates.';
                
                const apkUrl = (finalAppId === 'kin-tracker') 
                    ? 'https://api.cosmowhisper.com/updates/app-latest.apk'
                    : `https://api.cosmowhisper.com/updates/${finalAppId}/app-latest.apk`;

                const metadata = {
                    appId: finalAppId,
                    versionCode: vCode,
                    versionName: vName,
                    apkUrl: apkUrl,
                    releaseNotes: notes,
                    publishedAt: Date.now()
                };

                const metaJson = JSON.stringify(metadata, null, 2);
                fs.writeFileSync(path.join(finalDir, 'version.json'), metaJson, 'utf8');

                // If kin-tracker, keep root synced too
                if (finalAppId === 'kin-tracker') {
                    fs.writeFileSync(path.join(UPDATE_DIR, 'version.json'), metaJson, 'utf8');
                    const rootApkPath = path.join(UPDATE_DIR, 'app-latest.apk');
                    if (finalFile !== rootApkPath) {
                        try { fs.copyFileSync(finalFile, rootApkPath); } catch (_) {}
                    }
                }

                console.log(`[OTA] Deployed build for [${finalAppId}]: v${vName} (code: ${vCode})`);
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({
                    success: true,
                    message: `Successfully deployed [${finalAppId}] version ${vName} (${vCode})`,
                    metadata
                }));
            } catch (err) {
                res.writeHead(500, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Failed saving update: ' + err.message }));
            }
        });

        req.pipe(bb);
        return;
    }

    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: `Update route not found for path: ${pathname}` }));
}

migrateExistingData();

// ---------------------------------------------------------------------------
// REMOTE CONFIG: server-controlled app settings so most fixes need no app update.
// GET  /config/<app>.json        -> merged config (defaults + config/<app>.json)
// PUT  /config/<app>.json        -> replace config (requires x-admin-key)
// ---------------------------------------------------------------------------
const CONFIG_DIR = path.join(__dirname, 'config');
const DEFAULT_APP_CONFIG = {
    version: 1,
    map: {
        // "esri_street" (default) or "osm" (classic OpenStreetMap fallback)
        provider: 'esri_street'
    },
    zones: {
        // Hide a saved Work zone that sits on top of a school zone
        hideWorkNearSchool: true,
        // Hide the Work zone entirely for everyone
        hideWorkZone: false
    },
    features: {}
};

function deepMerge(base, extra) {
    const out = Array.isArray(base) ? base.slice() : Object.assign({}, base);
    Object.keys(extra || {}).forEach(k => {
        if (extra[k] && typeof extra[k] === 'object' && !Array.isArray(extra[k]) && base && typeof base[k] === 'object') {
            out[k] = deepMerge(base[k], extra[k]);
        } else {
            out[k] = extra[k];
        }
    });
    return out;
}

function handleConfig(req, res) {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    const appId = (pathname.replace(/^\/config\/?/, '').replace(/\.json$/, '').split('/')[0] || 'kin-tracker')
        .replace(/[^a-zA-Z0-9_-]/g, '');
    const file = path.join(CONFIG_DIR, `${appId}.json`);

    if (req.method === 'GET') {
        let config = DEFAULT_APP_CONFIG;
        try {
            if (fs.existsSync(file)) config = deepMerge(DEFAULT_APP_CONFIG, JSON.parse(fs.readFileSync(file, 'utf8')));
        } catch (e) {
            console.error('[CONFIG] Bad config file, serving defaults:', e.message);
        }
        res.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-cache' });
        res.end(JSON.stringify(config));
        return;
    }

    if (req.method === 'PUT' || req.method === 'POST') {
        if (req.headers['x-admin-key'] !== ADMIN_KEY) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Invalid admin key' }));
            return;
        }
        let body = '';
        req.on('data', chunk => { body += chunk; });
        req.on('end', () => {
            try {
                const parsed = JSON.parse(body);
                if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('Config must be a JSON object');
                fs.mkdirSync(CONFIG_DIR, { recursive: true });
                fs.writeFileSync(file, JSON.stringify(parsed, null, 2), 'utf8');
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: true, config: deepMerge(DEFAULT_APP_CONFIG, parsed) }));
            } catch (e) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: e.message }));
            }
        });
        return;
    }

    res.writeHead(405, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Method not allowed' }));
}

const server = http.createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, PUT, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, x-admin-key, x-group-token');

    if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }

    // Intercept Remote Config routes (server-controlled app settings)
    if (req.url.startsWith('/config/') || req.url === '/config') {
        handleConfig(req, res);
        return;
    }

    // Intercept OTA Update routes
    if (req.url.startsWith('/updates/') || req.url === '/updates') {
        handleUpdates(req, res);
        return;
    }

    // Intercept Trip History route
    if (req.url.startsWith('/trips/')) {
        const token = req.url.slice(7).split('?')[0];
        const trips = loadCompletedTrips(token);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, trips }));
        return;
    }

    const key = req.url.slice(1).split('?')[0];
    if (!key || key.length > 255) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Invalid key' }));
        return;
    }

    if (req.method === 'GET') {
        handleGet(key, res);
    } else if (req.method === 'PUT') {
        let body = '';
        req.on('data', chunk => { body += chunk; });
        req.on('end', () => {
            let parsed;
            try { parsed = JSON.parse(body); } catch (e) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Invalid JSON' }));
                return;
            }
            if (parsed && typeof parsed.members === 'object') {
                handleSmartPut(key, parsed, res, req);
            } else {
                const safeFile = path.join(DATA_DIR, `${safeKey(key)}.json`);
                fs.writeFile(safeFile, body, 'utf8', err => {
                    if (err) { res.writeHead(500); res.end(JSON.stringify({ error: 'Write error' })); return; }
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ success: true }));
                });
            }
        });
    } else {
        res.writeHead(405, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Method not allowed' }));
    }
});

// Setup WebSocket Server for Sub-second Telemetry Streaming
const wss = new WebSocketServer({ noServer: true });

server.on('upgrade', (request, socket, head) => {
    try {
        const host = request.headers.host || 'localhost';
        const urlObj = new URL(request.url, `http://${host}`);
        let token = urlObj.searchParams.get('token');
        if (!token) {
            const parts = urlObj.pathname.split('/').filter(Boolean);
            token = parts.find(p => p !== 'sync' && p !== 'ws') || '81e5632c_pin_group';
        }

        wss.handleUpgrade(request, socket, head, (ws) => {
            wss.emit('connection', ws, request, token);
        });
    } catch (err) {
        socket.destroy();
    }
});

wss.on('connection', (ws, request, token) => {
    if (!circleSockets.has(token)) circleSockets.set(token, new Set());
    circleSockets.get(token).add(ws);

    // Send immediate snapshot on connect
    const config = loadGroupConfig(token) || {};
    const allMembers = loadAllMembers(token);
    const completedTrips = loadCompletedTrips(token);
    const alerts = recentAlertsMap.get(token) || [];

    try {
        ws.send(JSON.stringify({
            type: 'SNAPSHOT',
            token,
            config,
            members: allMembers,
            completedTrips: completedTrips.slice(-30),
            recentAlerts: alerts.slice(-10)
        }));
    } catch (_) {}

    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message.toString());
            if (data.type === 'LOCATION_UPDATE' && data.memberId && data.member) {
                saveMember(token, data.memberId, data.member);
            }
        } catch (_) {}
    });

    ws.on('close', () => {
        const set = circleSockets.get(token);
        if (set) {
            set.delete(ws);
            if (set.size === 0) circleSockets.delete(token);
        }
    });

    ws.on('error', () => {
        const set = circleSockets.get(token);
        if (set) set.delete(ws);
    });
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`[Ra] Smart KinTracker Sync Server running on port ${PORT}`);
    console.log(`[Ra] Group configs  -> ${DATA_DIR}`);
    console.log(`[Ra] Member records -> ${MEMBER_DIR}`);
    console.log(`[Ra] OTA Updates    -> ${UPDATE_DIR}`);
    console.log(`[Ra] Trips Storage  -> ${path.join(__dirname, '../trips-data')}`);
    console.log(`[Ra] Realtime WebSocket Telemetry Hub Active`);
});
