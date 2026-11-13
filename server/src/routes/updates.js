const express = require('express');
const fs = require('fs');
const path = require('path');
const multer = require('multer');
const { isValidAdminKey } = require('../config');
const { archiveCurrentBuild, listBackups, restoreBackup } = require('../utils/otaBackups');

const router = express.Router();
const UPDATE_DIR = path.join(__dirname, '../../updates');
const STAGING_DIR = path.join(UPDATE_DIR, '.staging');

// Ensure base update and staging directories exist
if (!fs.existsSync(UPDATE_DIR)) {
    fs.mkdirSync(UPDATE_DIR, { recursive: true });
}
if (!fs.existsSync(STAGING_DIR)) {
    fs.mkdirSync(STAGING_DIR, { recursive: true });
}

// Canonical App Slugs and Aliases
const APP_ALIASES = {
    'kin-tracker': 'kin-tracker',
    'kintracker': 'kin-tracker',
    'familytracker': 'kin-tracker',
    'com.aistudio.familytracker.lnvwe': 'kin-tracker',
    'default': 'kin-tracker',

    'cosmo-companion': 'cosmo-companion',
    'companion': 'cosmo-companion',
    'cosmo-symphony-companion': 'cosmo-companion',
    'com.cosmo.symphony.companion': 'cosmo-companion',

    'jinshin-jyutsu': 'jinshin-jyutsu',
    'jinshin': 'jinshin-jyutsu',
    'jin-shin': 'jinshin-jyutsu',
    'jinshinflow': 'jinshin-jyutsu',
    'com.aistudio.jinshinflow.kyztmp': 'jinshin-jyutsu',

    'cosmo-flow': 'cosmo-flow',
    'cosmoflow': 'cosmo-flow',
    'flow': 'cosmo-flow',
    'com.aistudio.cosmoflow.vxbk': 'cosmo-flow',

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

// Stage incoming APKs in .staging/ so active app-latest.apk stays intact until archived
const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        if (!fs.existsSync(STAGING_DIR)) fs.mkdirSync(STAGING_DIR, { recursive: true });
        cb(null, STAGING_DIR);
    },
    filename: (req, file, cb) => cb(null, `app-upload-${Date.now()}-${Math.round(Math.random() * 1e6)}.tmp`)
});
const upload = multer({ 
    storage: storage,
    limits: { fileSize: 150 * 1024 * 1024 } // 150MB max APK size
});

// Helper to validate token
const validateGroupOrAdminToken = (req, res, next) => {
    const groupToken = req.headers['x-group-token'] || req.query.token;
    const adminKey = req.headers['x-admin-key'];
    
    if (adminKey && isValidAdminKey(adminKey)) return next();

    const clean = String(groupToken || '').toLowerCase().replace(/[\s\-_]/g, '');
    const isKnown = clean.includes('81e5632c') || 
                    clean === '4666' || 
                    clean.includes('pingroup') ||
                    clean.includes('companion') ||
                    clean.includes('jinshin') ||
                    clean.includes('flow') ||
                    clean.includes('cookbook') ||
                    clean.includes('heirloom') ||
                    clean.length >= 4;

    if (isKnown) return next();
    return res.status(401).json({ error: 'Unauthorized access to update channel' });
};

// 0. Overview of all apps
router.get(['/status', '/list'], (req, res) => {
    const apps = {};
    const knownDirs = ['kin-tracker', 'cosmo-companion', 'jinshin-jyutsu', 'cosmo-flow', 'cosmo-compendium'];
    
    if (fs.existsSync(UPDATE_DIR)) {
        const entries = fs.readdirSync(UPDATE_DIR, { withFileTypes: true });
        entries.forEach(e => {
            if (e.isDirectory() && !knownDirs.includes(e.name)) knownDirs.push(e.name);
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

    res.json({
        server: 'Sovereign VPS Multi-App OTA Hub',
        status: 'online',
        totalApps: Object.keys(apps).length,
        apps
    });
});

// 1. Get current version metadata (App polls this)
function handleVersionRequest(req, res) {
    const targetApp = req.params.appId || req.query.app || 'kin-tracker';
    const { resolved: appId, dir: appDir } = getAppUpdateDir(targetApp);

    let jsonPath = path.join(appDir, 'version.json');
    if (!fs.existsSync(jsonPath) && appId === 'kin-tracker') {
        jsonPath = path.join(UPDATE_DIR, 'version.json');
    }

    if (!fs.existsSync(jsonPath)) {
        return res.status(404).json({ error: `No update metadata found for app '${appId}'.` });
    }
    try {
        const metadata = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));
        res.json(metadata);
    } catch (e) {
        res.status(500).json({ error: 'Invalid update metadata format' });
    }
}

router.get('/version.json', validateGroupOrAdminToken, handleVersionRequest);
router.get('/:appId/version.json', validateGroupOrAdminToken, handleVersionRequest);

// 2. Download the latest APK (App downloads this)
function handleApkDownload(req, res) {
    const targetApp = req.params.appId || req.query.app || 'kin-tracker';
    const { resolved: appId, dir: appDir } = getAppUpdateDir(targetApp);

    let apkPath = path.join(appDir, 'app-latest.apk');
    if (!fs.existsSync(apkPath) && appId === 'kin-tracker') {
        apkPath = path.join(UPDATE_DIR, 'app-latest.apk');
    }

    if (!fs.existsSync(apkPath)) {
        return res.status(404).send(`APK file not found for app '${appId}'.`);
    }
    res.download(apkPath, `${appId}-update.apk`);
}

router.get('/app-latest.apk', validateGroupOrAdminToken, handleApkDownload);
router.get('/:appId/app-latest.apk', validateGroupOrAdminToken, handleApkDownload);

function cleanupStagedFile(filePath) {
    if (filePath && fs.existsSync(filePath)) {
        try { fs.unlinkSync(filePath); } catch (_) {}
    }
}

function moveStagedApk(stagedPath, targetPath) {
    try {
        fs.renameSync(stagedPath, targetPath);
    } catch (_) {
        fs.copyFileSync(stagedPath, targetPath);
        cleanupStagedFile(stagedPath);
    }
}

// 3. Upload a new build (archives prior working APK + metadata first)
function handleUpload(req, res) {
    const adminKey = req.headers['x-admin-key'];
    if (!isValidAdminKey(adminKey)) {
        cleanupStagedFile(req.file && req.file.path);
        return res.status(403).json({ error: 'Forbidden: Invalid Admin Key' });
    }

    const { versionCode, versionName, releaseNotes } = req.body;
    if (!versionCode || !versionName) {
        cleanupStagedFile(req.file && req.file.path);
        return res.status(400).json({ error: 'Missing version metadata' });
    }

    const targetApp = req.params.appId || req.body.appId || req.query.app || 'kin-tracker';
    const { resolved: appId, dir: appDir } = getAppUpdateDir(targetApp);

    // Preserve previous working build before overwriting app-latest.apk
    const archivedBackup = archiveCurrentBuild(appDir, UPDATE_DIR, appId);

    const currentApkPath = path.join(appDir, 'app-latest.apk');
    if (req.file && req.file.path) {
        moveStagedApk(req.file.path, currentApkPath);
    }

    const host = req.get('host') || 'api.cosmowhisper.com';
    const protocol = req.protocol === 'https' || req.get('x-forwarded-proto') === 'https' ? 'https' : 'http';
    const baseUrl = `${protocol}://${host}`;

    const apkUrl = (appId === 'kin-tracker')
        ? `${baseUrl}/updates/app-latest.apk`
        : `${baseUrl}/updates/${appId}/app-latest.apk`;

    const metadata = {
        appId: appId,
        versionCode: parseInt(versionCode, 10),
        versionName: String(versionName).trim(),
        apkUrl: apkUrl,
        releaseNotes: releaseNotes || "Bug fixes and stability updates.",
        publishedAt: Date.now()
    };

    const metaJson = JSON.stringify(metadata, null, 2);
    fs.writeFileSync(path.join(appDir, 'version.json'), metaJson);

    // Keep root synced for kin-tracker
    if (appId === 'kin-tracker') {
        fs.writeFileSync(path.join(UPDATE_DIR, 'version.json'), metaJson);
        const rootApkPath = path.join(UPDATE_DIR, 'app-latest.apk');
        if (rootApkPath !== currentApkPath && fs.existsSync(currentApkPath)) {
            try { fs.copyFileSync(currentApkPath, rootApkPath); } catch (_) {}
        }
    }

    res.json({
        success: true,
        message: `Successfully deployed [${appId}] version ${versionName} (${versionCode})`,
        metadata,
        archivedBackup
    });
}

router.post('/upload', upload.single('apk'), handleUpload);
router.post('/:appId/upload', upload.single('apk'), handleUpload);

// 4. List available rollback backups for an app
function handleListBackups(req, res) {
    const targetApp = req.params.appId || req.query.app || 'kin-tracker';
    const { resolved: appId, dir: appDir } = getAppUpdateDir(targetApp);
    const currentPath = path.join(appDir, 'version.json');
    let current = null;
    if (fs.existsSync(currentPath)) {
        try { current = JSON.parse(fs.readFileSync(currentPath, 'utf8')); } catch (_) {}
    }
    const backups = listBackups(appDir);
    res.json({
        appId,
        current,
        totalBackups: backups.length,
        backups
    });
}

router.get('/backups', validateGroupOrAdminToken, handleListBackups);
router.get('/:appId/backups', validateGroupOrAdminToken, handleListBackups);

// 5. Instant OTA rollback to a previous archived build
function handleRollback(req, res) {
    const adminKey = req.headers['x-admin-key'];
    if (!isValidAdminKey(adminKey)) {
        return res.status(403).json({ error: 'Forbidden: Invalid Admin Key' });
    }

    const targetApp = req.params.appId || (req.body && req.body.appId) || req.query.app || 'kin-tracker';
    const { resolved: appId, dir: appDir } = getAppUpdateDir(targetApp);
    const result = restoreBackup(appDir, UPDATE_DIR, appId, req.body || {});

    if (result.error) {
        return res.status(result.statusCode || 404).json({ error: result.error });
    }

    res.json({
        success: true,
        message: `Rolled back [${appId}] to version ${result.metadata.versionName} (${result.metadata.versionCode})`,
        restoredFrom: result.restoredFrom,
        metadata: result.metadata
    });
}

router.post('/rollback', handleRollback);
router.post('/:appId/rollback', handleRollback);

module.exports = router;
