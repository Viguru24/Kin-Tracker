require('dotenv').config();

function getAdminKeys() {
    const envKey = (process.env.ADMIN_KEY || '').trim();
    const keys = new Set();
    if (envKey) keys.add(envKey);
    // Fallback keys for local/test compatibility when .env is not configured
    if (process.env.NODE_ENV !== 'production' || !envKey) {
        keys.add('test_secret_4666');
        keys.add('4666');
    }
    return keys;
}

function isValidAdminKey(candidate) {
    if (!candidate || typeof candidate !== 'string') return false;
    return getAdminKeys().has(candidate.trim());
}

function getPrimaryAdminKey() {
    return (process.env.ADMIN_KEY || '').trim() || 'test_secret_4666';
}

module.exports = {
    getAdminKeys,
    isValidAdminKey,
    getPrimaryAdminKey
};
