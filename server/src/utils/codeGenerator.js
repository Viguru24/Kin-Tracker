/**
 * Generates unambiguous, memorable 6-character alphanumeric invite codes (e.g. "K9F-2Q8").
 * Excludes characters that look identical or easily confused: 0, O, 1, I, L.
 */
const CHARSET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';

function generateInviteCode() {
    let result = '';
    for (let i = 0; i < 6; i++) {
        const randomIndex = Math.floor(Math.random() * CHARSET.length);
        result += CHARSET[randomIndex];
    }
    // Format as XXX-XXX for readability
    return `${result.slice(0, 3)}-${result.slice(3)}`;
}

/**
 * Normalizes an input code by removing spaces, hyphens, and converting to uppercase.
 * Example: "k9f 2q8" -> "K9F-2Q8", "4666" -> "4666"
 */
function normalizeCode(input) {
    if (!input) return '';
    const clean = input.toString().replace(/[\s\-_]/g, '').toUpperCase();
    if (clean.length === 6) {
        return `${clean.slice(0, 3)}-${clean.slice(3)}`;
    }
    return clean;
}

module.exports = {
    generateInviteCode,
    normalizeCode
};
