const express = require('express');
const fs = require('fs');
const path = require('path');
const multer = require('multer');

const router = express.Router();
const UPDATE_DIR = path.join(__dirname, '../../updates');
const ADMIN_KEY = process.env.ADMIN_KEY || "test_secret_4666";

// Ensure update directory exists
if (!fs.existsSync(UPDATE_DIR)) {
    fs.mkdirSync(UPDATE_DIR, { recursive: true });
}

// Setup multer storage for incoming APKs
const storage = multer.diskStorage({
    destination: (req, file, cb) => cb(null, UPDATE_DIR),
    filename: (req, file, cb) => cb(null, 'app-latest.apk')
});
const upload = multer({ 
    storage: storage,
    limits: { fileSize: 150 * 1024 * 1024 } // 150MB max APK size
});

// Helper to validate family group token or admin key
const validateGroupOrAdminToken = (req, res, next) => {
    const groupToken = req.headers['x-group-token'] || req.query.token;
    const adminKey = req.headers['x-admin-key'];
    
    // Normalize token
    const cleanGroupToken = String(groupToken || '').toLowerCase().replace(/[\s\-_]/g, '');
    const isKnownFamilyGroup = cleanGroupToken.includes('81e5632c') || 
                               cleanGroupToken === '4666' || 
                               cleanGroupToken.includes('pingroup');

    if (isKnownFamilyGroup || (adminKey && adminKey === ADMIN_KEY)) {
        return next();
    }
    return res.status(401).json({ error: 'Unauthorized access to update channel' });
};

// 1. Get current version metadata (App polls this)
router.get('/version.json', validateGroupOrAdminToken, (req, res) => {
    const jsonPath = path.join(UPDATE_DIR, 'version.json');
    if (!fs.existsSync(jsonPath)) {
        return res.status(404).json({ error: 'No update metadata found.' });
    }
    try {
        const metadata = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));
        res.json(metadata);
    } catch (e) {
        res.status(500).json({ error: 'Invalid update metadata format' });
    }
});

// 2. Download the latest APK (App downloads this)
router.get('/app-latest.apk', validateGroupOrAdminToken, (req, res) => {
    const apkPath = path.join(UPDATE_DIR, 'app-latest.apk');
    if (!fs.existsSync(apkPath)) {
        return res.status(404).send('APK file not found.');
    }
    res.download(apkPath, 'kin-tracker-update.apk');
});

// 3. Upload a new build (Your PowerShell script hits this)
router.post('/upload', upload.single('apk'), (req, res) => {
    const adminKey = req.headers['x-admin-key'];
    if (adminKey !== ADMIN_KEY) {
        return res.status(403).json({ error: 'Forbidden: Invalid Admin Key' });
    }

    const { versionCode, versionName, releaseNotes } = req.body;
    if (!versionCode || !versionName) {
        return res.status(400).json({ error: 'Missing version metadata' });
    }

    const host = req.get('host') || 'api.cosmowhisper.com';
    const protocol = req.protocol === 'https' || req.get('x-forwarded-proto') === 'https' ? 'https' : 'http';
    const baseUrl = `${protocol}://${host}`;

    // Save metadata file
    const metadata = {
        versionCode: parseInt(versionCode, 10),
        versionName: String(versionName).trim(),
        apkUrl: `${baseUrl}/updates/app-latest.apk`,
        releaseNotes: releaseNotes || "Bug fixes and stability updates.",
        publishedAt: Date.now()
    };

    fs.writeFileSync(
        path.join(UPDATE_DIR, 'version.json'), 
        JSON.stringify(metadata, null, 2)
    );

    res.json({ 
        success: true, 
        message: `Successfully deployed version ${versionName} (${versionCode})`,
        metadata 
    });
});

module.exports = router;
