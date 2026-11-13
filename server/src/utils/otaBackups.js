const fs = require('fs');
const path = require('path');

const DEFAULT_MAX_BACKUPS = 5;

function readJsonSafe(filePath) {
    try {
        if (!fs.existsSync(filePath)) return null;
        return JSON.parse(fs.readFileSync(filePath, 'utf8'));
    } catch (_) {
        return null;
    }
}

function resolveActiveArtifacts(appDir, updateDir, appId) {
    let versionPath = path.join(appDir, 'version.json');
    let apkPath = path.join(appDir, 'app-latest.apk');
    if (appId === 'kin-tracker') {
        if (!fs.existsSync(versionPath) && fs.existsSync(path.join(updateDir, 'version.json'))) {
            versionPath = path.join(updateDir, 'version.json');
        }
        if (!fs.existsSync(apkPath) && fs.existsSync(path.join(updateDir, 'app-latest.apk'))) {
            apkPath = path.join(updateDir, 'app-latest.apk');
        }
    }
    return { versionPath, apkPath };
}

/**
 * Reads all valid archived OTA backups for an app, ordered newest first.
 */
function listBackups(appDir) {
    const backupsDir = path.join(appDir, 'backups');
    if (!fs.existsSync(backupsDir)) return [];

    const results = [];
    for (const entry of fs.readdirSync(backupsDir, { withFileTypes: true })) {
        if (!entry.isDirectory()) continue;
        const dirPath = path.join(backupsDir, entry.name);
        const meta = readJsonSafe(path.join(dirPath, 'backup.json')) || readJsonSafe(path.join(dirPath, 'version.json'));
        const apkPath = path.join(dirPath, 'app.apk');
        if (!meta || !fs.existsSync(apkPath)) continue;

        const stat = fs.statSync(apkPath);
        if (stat.size <= 0) continue;

        results.push({
            backupId: entry.name,
            appId: meta.appId,
            versionCode: Number(meta.versionCode) || 0,
            versionName: String(meta.versionName || ''),
            releaseNotes: String(meta.releaseNotes || ''),
            apkUrl: meta.apkUrl,
            publishedAt: Number(meta.publishedAt) || 0,
            archivedAt: Number(meta.archivedAt) || stat.mtimeMs,
            apkSize: stat.size
        });
    }

    return results.sort((a, b) => (b.archivedAt - a.archivedAt) || (b.versionCode - a.versionCode));
}

function pruneBackups(backupsDir, maxBackups = DEFAULT_MAX_BACKUPS) {
    const entries = fs.readdirSync(backupsDir, { withFileTypes: true })
        .filter(e => e.isDirectory())
        .map(e => {
            const fullPath = path.join(backupsDir, e.name);
            const meta = readJsonSafe(path.join(fullPath, 'backup.json'));
            const stat = fs.statSync(fullPath);
            return {
                name: e.name,
                fullPath,
                archivedAt: (meta && Number(meta.archivedAt)) || stat.mtimeMs
            };
        })
        .sort((a, b) => b.archivedAt - a.archivedAt);

    for (const stale of entries.slice(maxBackups)) {
        fs.rmSync(stale.fullPath, { recursive: true, force: true });
    }
}

/**
 * Snapshots the currently active build (version.json + app-latest.apk) into
 * backups/<backupId> before an OTA upload replaces it.
 */
function archiveCurrentBuild(appDir, updateDir, appId, maxBackups = DEFAULT_MAX_BACKUPS) {
    const { versionPath, apkPath } = resolveActiveArtifacts(appDir, updateDir, appId);
    const meta = readJsonSafe(versionPath);
    if (!meta || !fs.existsSync(apkPath)) return null;

    const apkStat = fs.statSync(apkPath);
    if (apkStat.size <= 0) return null;

    const existing = listBackups(appDir);
    const matchingExisting = existing.find(b =>
        b.versionCode === Number(meta.versionCode) &&
        b.apkSize === apkStat.size &&
        b.publishedAt === Number(meta.publishedAt || 0)
    );
    if (matchingExisting) {
        return matchingExisting;
    }

    const backupsDir = path.join(appDir, 'backups');
    fs.mkdirSync(backupsDir, { recursive: true });

    const now = Date.now();
    const vCode = Number(meta.versionCode) || 0;
    let backupId = `v${vCode}-${now}`;
    let counter = 1;
    while (fs.existsSync(path.join(backupsDir, backupId))) {
        backupId = `v${vCode}-${now}-${counter++}`;
    }

    const targetDir = path.join(backupsDir, backupId);
    fs.mkdirSync(targetDir, { recursive: true });
    fs.copyFileSync(apkPath, path.join(targetDir, 'app.apk'));

    const backupMeta = {
        ...meta,
        appId: meta.appId || appId,
        backupId,
        apkSize: apkStat.size,
        archivedAt: now
    };
    fs.writeFileSync(path.join(targetDir, 'backup.json'), JSON.stringify(backupMeta, null, 2));

    pruneBackups(backupsDir, maxBackups);
    return backupMeta;
}

/**
 * Restores a backed-up build to the active app-latest.apk + version.json.
 * Selects by backupId, versionCode, or defaults to the newest prior version.
 */
function restoreBackup(appDir, updateDir, appId, selector = {}) {
    const backups = listBackups(appDir);
    if (backups.length === 0) {
        return { error: 'No backed-up builds available for rollback', statusCode: 404 };
    }

    const currentMeta = readJsonSafe(path.join(appDir, 'version.json')) ||
        (appId === 'kin-tracker' ? readJsonSafe(path.join(updateDir, 'version.json')) : null);

    let chosen = null;
    if (selector.backupId) {
        chosen = backups.find(b => b.backupId === String(selector.backupId));
    } else if (selector.versionCode !== undefined && selector.versionCode !== null && selector.versionCode !== '') {
        const targetCode = Number(selector.versionCode);
        chosen = backups.find(b => b.versionCode === targetCode);
    } else if (currentMeta) {
        chosen = backups.find(b => b.versionCode !== Number(currentMeta.versionCode)) || backups[0];
    } else {
        chosen = backups[0];
    }

    if (!chosen) {
        return { error: 'Requested backup build not found', statusCode: 404 };
    }

    const backupDir = path.join(appDir, 'backups', chosen.backupId);
    const backupApk = path.join(backupDir, 'app.apk');
    if (!fs.existsSync(backupApk)) {
        return { error: `Backup APK missing for ${chosen.backupId}`, statusCode: 404 };
    }

    const finalApkPath = path.join(appDir, 'app-latest.apk');
    const tmpRestorePath = path.join(appDir, `app-restore-${Date.now()}.tmp`);
    fs.copyFileSync(backupApk, tmpRestorePath);
    fs.renameSync(tmpRestorePath, finalApkPath);

    const restoredMeta = {
        appId,
        versionCode: chosen.versionCode,
        versionName: chosen.versionName,
        apkUrl: chosen.apkUrl || (appId === 'kin-tracker'
            ? 'https://api.cosmowhisper.com/updates/app-latest.apk'
            : `https://api.cosmowhisper.com/updates/${appId}/app-latest.apk`),
        releaseNotes: chosen.releaseNotes,
        publishedAt: chosen.publishedAt,
        restoredFromBackupId: chosen.backupId,
        restoredAt: Date.now()
    };

    const metaJson = JSON.stringify(restoredMeta, null, 2);
    fs.writeFileSync(path.join(appDir, 'version.json'), metaJson);

    if (appId === 'kin-tracker') {
        fs.writeFileSync(path.join(updateDir, 'version.json'), metaJson);
        const rootApkPath = path.join(updateDir, 'app-latest.apk');
        if (rootApkPath !== finalApkPath) {
            fs.copyFileSync(finalApkPath, rootApkPath);
        }
    }

    return {
        success: true,
        restoredFrom: chosen,
        metadata: restoredMeta
    };
}

module.exports = {
    archiveCurrentBuild,
    listBackups,
    restoreBackup
};
