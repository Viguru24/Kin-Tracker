const http = require('http');
const assert = require('assert');

// Test runner for VPS Dashboard & Admin API
async function runTests() {
    console.log('🧪 Starting Kin-Tracker VPS Dashboard & Admin API Tests...');

    // Require index to boot Express server
    process.env.PORT = 4666;
    process.env.ADMIN_KEY = 'test_secret_4666';
    const serverModule = require('../src/index');

    // Wait a brief moment for server to bind
    await new Promise(r => setTimeout(r, 600));

    function makeRequest(path, options = {}) {
        return new Promise((resolve, reject) => {
            const reqOptions = {
                hostname: '127.0.0.1',
                port: 4666,
                path,
                method: options.method || 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    ...(options.headers || {})
                }
            };

            const req = http.request(reqOptions, (res) => {
                let body = '';
                res.on('data', chunk => body += chunk);
                res.on('end', () => {
                    resolve({
                        statusCode: res.statusCode,
                        headers: res.headers,
                        body: body ? (res.headers['content-type']?.includes('application/json') ? JSON.parse(body) : body) : null
                    });
                });
            });

            req.on('error', reject);
            if (options.body) {
                req.write(typeof options.body === 'string' ? options.body : JSON.stringify(options.body));
            }
            req.end();
        });
    }

    try {
        // 1. Health check
        console.log('1️⃣ Testing /health endpoint...');
        const healthRes = await makeRequest('/health');
        assert.strictEqual(healthRes.statusCode, 200);
        assert.strictEqual(healthRes.body.status, 'online');
        console.log('   ✅ Health endpoint working.');

        // 2. Dashboard Static HTML
        console.log('2️⃣ Testing /dashboard static page serving...');
        const dashRes = await makeRequest('/dashboard');
        assert.strictEqual(dashRes.statusCode, 200);
        assert(dashRes.body.includes('Kin-Tracker Sovereign VPS Radar & Command Center'));
        console.log('   ✅ /dashboard static HTML served.');

        // 3. Admin Auth - Reject without token
        console.log('3️⃣ Testing Admin Auth Rejection without token...');
        const unauthRes = await makeRequest('/api/admin/circles');
        assert.strictEqual(unauthRes.statusCode, 401);
        console.log('   ✅ 401 Unauthorized returned properly.');

        // 4. Admin Login
        console.log('4️⃣ Testing Admin Login endpoint...');
        const loginRes = await makeRequest('/api/admin/login', {
            method: 'POST',
            body: { key: 'test_secret_4666' }
        });
        assert.strictEqual(loginRes.statusCode, 200);
        assert.strictEqual(loginRes.body.token, 'test_secret_4666');
        console.log('   ✅ Admin login successful.');

        const authHeaders = { 'x-admin-key': 'test_secret_4666' };

        // 5. Admin Stats
        console.log('5️⃣ Testing Admin Stats endpoint...');
        const statsRes = await makeRequest('/api/admin/stats', { headers: authHeaders });
        assert.strictEqual(statsRes.statusCode, 200);
        assert(statsRes.body.system.uptimeSeconds >= 0);
        console.log(`   ✅ Admin stats returned (RAM: ${statsRes.body.system.processRssMB}MB).`);

        // 6. Admin Circles list & Geofence update
        console.log('6️⃣ Testing Admin Circles & Geofence Calibration...');
        const circlesRes = await makeRequest('/api/admin/circles', { headers: authHeaders });
        assert.strictEqual(circlesRes.statusCode, 200);
        assert(circlesRes.body.circles.length > 0);
        const testCircle = circlesRes.body.circles[0];

        const updateRes = await makeRequest(`/api/admin/circles/${testCircle.id}`, {
            method: 'PUT',
            headers: authHeaders,
            body: {
                homeLat: 51.330000,
                homeLng: -0.118000,
                homeRadiusMeters: 160,
                isHomeCalibrated: true
            }
        });
        assert.strictEqual(updateRes.statusCode, 200);
        assert.strictEqual(updateRes.body.circle.homeRadiusMeters, 160);
        console.log('   ✅ Geofence calibrated successfully.');

        // 7. Member location & SOS Alarm toggle
        console.log('7️⃣ Testing Member Location Update & SOS Alarm Toggle...');
        // First push a member location via circle API
        await makeRequest(`/api/circles/${testCircle.id}/location`, {
            method: 'POST',
            body: {
                memberId: 'device_test_1',
                name: 'Test Explorer',
                lat: 51.331200,
                lng: -0.117500,
                batteryPct: 88,
                speedMph: 12.5
            }
        });

        const alarmRes = await makeRequest('/api/admin/members/device_test_1/toggle-alarm', {
            method: 'POST',
            headers: authHeaders
        });
        assert.strictEqual(alarmRes.statusCode, 200);
        assert.strictEqual(alarmRes.body.isAlarm, true);
        console.log('   ✅ SOS Alarm triggered and logged in event stream.');

        // 8. Event Stream Log check
        console.log('8️⃣ Testing Event Stream Log API...');
        const eventRes = await makeRequest('/api/admin/events', { headers: authHeaders });
        assert.strictEqual(eventRes.statusCode, 200);
        assert(eventRes.body.events.length > 0);
        console.log(`   ✅ ${eventRes.body.events.length} event(s) in VPS event buffer.`);

        // 9. Backup Export check
        console.log('9️⃣ Testing DB Backup Export...');
        const backupRes = await makeRequest('/api/admin/backup/export', { headers: authHeaders });
        assert.strictEqual(backupRes.statusCode, 200);
        assert(backupRes.body.circles);
        console.log('   ✅ Database snapshot export generated.');

        console.log('\n🎉 ALL VPS DASHBOARD & ADMIN API TESTS PASSED PERFECTLY!\n');
        process.exit(0);

    } catch (err) {
        console.error('❌ Test failed:', err);
        process.exit(1);
    }
}

runTests();
