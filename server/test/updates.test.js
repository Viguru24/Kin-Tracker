const fs = require('fs');
const os = require('os');
const path = require('path');
const http = require('http');
const assert = require('assert');
const express = require('express');
const { archiveCurrentBuild, listBackups, restoreBackup } = require('../src/utils/otaBackups');

async function runTests() {
    console.log('🧪 Starting OTA Backup & Rollback Tests...');

    // 1. Unit test otaBackups archive/list/prune/restore in isolated temp dir
    const tmpRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'kt-ota-test-'));
    try {
        const updateDir = path.join(tmpRoot, 'updates');
        const appId = 'kin-tracker';
        const appDir = path.join(updateDir, appId);
        fs.mkdirSync(appDir, { recursive: true });

        const apkV49 = Buffer.from('FAKE-APK-BINARY-V49');
        const metaV49 = {
            appId,
            versionCode: 49,
            versionName: '2.8.3',
            apkUrl: 'https://api.cosmowhisper.com/updates/app-latest.apk',
            releaseNotes: 'Verified stable v49',
            publishedAt: 1000001
        };
        fs.writeFileSync(path.join(appDir, 'app-latest.apk'), apkV49);
        fs.writeFileSync(path.join(appDir, 'version.json'), JSON.stringify(metaV49, null, 2));

        // Archive v49 before deploying v50
        const archived49 = archiveCurrentBuild(appDir, updateDir, appId, 2);
        assert(archived49 && archived49.versionCode === 49, 'Should archive build 49');

        // Overwrite live build with bad v50
        const apkV50 = Buffer.from('BROKEN-APK-BINARY-V50');
        const metaV50 = {
            appId,
            versionCode: 50,
            versionName: '2.8.4',
            apkUrl: 'https://api.cosmowhisper.com/updates/app-latest.apk',
            releaseNotes: 'Bad v50',
            publishedAt: 1000002
        };
        fs.writeFileSync(path.join(appDir, 'app-latest.apk'), apkV50);
        fs.writeFileSync(path.join(appDir, 'version.json'), JSON.stringify(metaV50, null, 2));

        // Backups list must contain v49
        const backups = listBackups(appDir);
        assert.strictEqual(backups.length, 1);
        assert.strictEqual(backups[0].versionCode, 49);

        // Rollback without explicit selector picks v49 (different from current 50)
        const rollback = restoreBackup(appDir, updateDir, appId);
        assert.strictEqual(rollback.success, true);
        assert.strictEqual(rollback.metadata.versionCode, 49);
        assert.strictEqual(rollback.metadata.versionName, '2.8.3');

        const restoredApk = fs.readFileSync(path.join(appDir, 'app-latest.apk'));
        assert(restoredApk.equals(apkV49), 'app-latest.apk bytes must match v49 after rollback');
        const rootVersion = JSON.parse(fs.readFileSync(path.join(updateDir, 'version.json'), 'utf8'));
        assert.strictEqual(rootVersion.versionCode, 49, 'Root version.json must sync on kin-tracker rollback');
        console.log('   ✅ otaBackups archive + list + restore verified.');

        // 2. HTTP route test against Express router
        const router = require('../src/routes/updates');
        const app = express();
        app.use(express.json());
        app.use('/updates', router);

        const server = http.createServer(app);
        await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
        const port = server.address().port;

        try {
            const unauthRollback = await requestJson(port, 'POST', '/updates/rollback', {}, { 'x-admin-key': 'wrong' });
            assert.strictEqual(unauthRollback.statusCode, 403);

            const backupsRes = await requestJson(port, 'GET', '/updates/backups?token=81e5632c_pin_group');
            assert.strictEqual(backupsRes.statusCode, 200);
            assert(Array.isArray(backupsRes.body.backups));
            console.log('   ✅ /updates/backups and /updates/rollback HTTP auth routes verified.');
        } finally {
            await new Promise(resolve => server.close(resolve));
        }

        console.log('🎉 All OTA backup & rollback tests passed!');
    } finally {
        fs.rmSync(tmpRoot, { recursive: true, force: true });
    }
}

function requestJson(port, method, urlPath, bodyObj = null, headers = {}) {
    return new Promise((resolve, reject) => {
        const req = http.request({
            hostname: '127.0.0.1',
            port,
            path: urlPath,
            method,
            headers: {
                'Content-Type': 'application/json',
                ...headers
            }
        }, res => {
            let raw = '';
            res.on('data', chunk => raw += chunk);
            res.on('end', () => {
                resolve({
                    statusCode: res.statusCode,
                    body: raw ? JSON.parse(raw) : null
                });
            });
        });
        req.on('error', reject);
        if (bodyObj) req.write(JSON.stringify(bodyObj));
        req.end();
    });
}

runTests().catch(err => {
    console.error('❌ OTA backup tests failed:', err);
    process.exit(1);
});
