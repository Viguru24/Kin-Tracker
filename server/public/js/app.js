/**
 * KIN-TRACKER SOVEREIGN VPS RADAR & COMMAND CENTER
 * Frontend Application Logic (Vanilla JS + Leaflet)
 */

(function () {
    'use strict';

    // Application State
    const state = {
        adminKey: localStorage.getItem('kt_admin_key') || '',
        circles: [],
        currentCircleId: null,
        members: [],
        stats: {},
        audioStats: {},
        events: [],
        map: null,
        markers: {},
        homeCircleLayer: null,
        workCircleLayer: null,
        homeCenterMarker: null,
        workCenterMarker: null,
        mapClickMode: null, // 'home' or 'work'
        pollTimer: null,
        audioWs: null,
        audioCtx: null,
        isPlayingAudio: false
    };

    // DOM Element References
    const el = {
        authModal: document.getElementById('authModal'),
        loginForm: document.getElementById('loginForm'),
        adminKeyInput: document.getElementById('adminKeyInput'),
        loginBtn: document.getElementById('loginBtn'),
        loginError: document.getElementById('loginError'),
        dashboardApp: document.getElementById('dashboardApp'),
        circleSelect: document.getElementById('circleSelect'),
        globalSosBanner: document.getElementById('globalSosBanner'),
        sosBannerText: document.getElementById('sosBannerText'),
        resolveAllSosBtn: document.getElementById('resolveAllSosBtn'),
        serverUptime: document.getElementById('serverUptime'),
        serverRam: document.getElementById('serverRam'),
        refreshBtn: document.getElementById('refreshBtn'),
        logoutBtn: document.getElementById('logoutBtn'),
        broadcastBtn: document.getElementById('broadcastBtn'),
        backupModalBtn: document.getElementById('backupModalBtn'),
        statActiveCount: document.getElementById('statActiveCount'),
        statTotalCount: document.getElementById('statTotalCount'),
        statAvgBattery: document.getElementById('statAvgBattery'),
        statChargingCount: document.getElementById('statChargingCount'),
        statHomeStatus: document.getElementById('statHomeStatus'),
        statWorkStatus: document.getElementById('statWorkStatus'),
        statAudioStatus: document.getElementById('statAudioStatus'),
        statListenersCount: document.getElementById('statListenersCount'),
        mapCircleName: document.getElementById('mapCircleName'),
        radarMap: document.getElementById('radarMap'),
        mapCalibrateBanner: document.getElementById('mapCalibrateBanner'),
        mapCalibrateText: document.getElementById('mapCalibrateText'),
        cancelMapCalibrateBtn: document.getElementById('cancelMapCalibrateBtn'),
        fitMapBtn: document.getElementById('fitMapBtn'),
        focusHomeBtn: document.getElementById('focusHomeBtn'),
        focusWorkBtn: document.getElementById('focusWorkBtn'),
        mapClickCalibrateHomeBtn: document.getElementById('mapClickCalibrateHomeBtn'),
        mapClickCalibrateWorkBtn: document.getElementById('mapClickCalibrateWorkBtn'),
        inviteCodeDisplay: document.getElementById('inviteCodeDisplay'),
        copyInviteBtn: document.getElementById('copyInviteBtn'),
        regenInviteBtn: document.getElementById('regenInviteBtn'),
        membersList: document.getElementById('membersList'),
        openCircleConfigBtn: document.getElementById('openCircleConfigBtn'),
        circleConfigModal: document.getElementById('circleConfigModal'),
        circleConfigForm: document.getElementById('circleConfigForm'),
        cfgCircleName: document.getElementById('cfgCircleName'),
        cfgInviteCode: document.getElementById('cfgInviteCode'),
        cfgHomeLat: document.getElementById('cfgHomeLat'),
        cfgHomeLng: document.getElementById('cfgHomeLng'),
        cfgHomeRadius: document.getElementById('cfgHomeRadius'),
        cfgHomeCalibrated: document.getElementById('cfgHomeCalibrated'),
        cfgWorkLat: document.getElementById('cfgWorkLat'),
        cfgWorkLng: document.getElementById('cfgWorkLng'),
        cfgWorkRadius: document.getElementById('cfgWorkRadius'),
        cfgWorkCalibrated: document.getElementById('cfgWorkCalibrated'),
        cfgSetHomeCurrentGPS: document.getElementById('cfgSetHomeCurrentGPS'),
        cfgSetWorkCurrentGPS: document.getElementById('cfgSetWorkCurrentGPS'),
        memberControlModal: document.getElementById('memberControlModal'),
        memberControlForm: document.getElementById('memberControlForm'),
        ctrlMemberId: document.getElementById('ctrlMemberId'),
        ctrlMemberName: document.getElementById('ctrlMemberName'),
        ctrlMemberEmoji: document.getElementById('ctrlMemberEmoji'),
        ctrlMemberColor: document.getElementById('ctrlMemberColor'),
        ctrlMemberLat: document.getElementById('ctrlMemberLat'),
        ctrlMemberLng: document.getElementById('ctrlMemberLng'),
        ctrlMemberBattery: document.getElementById('ctrlMemberBattery'),
        ctrlMemberSpeed: document.getElementById('ctrlMemberSpeed'),
        ctrlMemberStatus: document.getElementById('ctrlMemberStatus'),
        ctrlMemberCharging: document.getElementById('ctrlMemberCharging'),
        ctrlMemberPaused: document.getElementById('ctrlMemberPaused'),
        ctrlTriggerAlarmBtn: document.getElementById('ctrlTriggerAlarmBtn'),
        ctrlKickMemberBtn: document.getElementById('ctrlKickMemberBtn'),
        broadcastModal: document.getElementById('broadcastModal'),
        broadcastForm: document.getElementById('broadcastForm'),
        broadcastMessageInput: document.getElementById('broadcastMessageInput'),
        backupModal: document.getElementById('backupModal'),
        downloadBackupBtn: document.getElementById('downloadBackupBtn'),
        restoreFileInput: document.getElementById('restoreFileInput'),
        executeRestoreBtn: document.getElementById('executeRestoreBtn'),
        toastContainer: document.getElementById('toastContainer'),
        audioWave: document.getElementById('audioWave'),
        audioBroadcasterName: document.getElementById('audioBroadcasterName'),
        audioBroadcastStatus: document.getElementById('audioBroadcastStatus'),
        startListenAudioBtn: document.getElementById('startListenAudioBtn'),
        stopListenAudioBtn: document.getElementById('stopListenAudioBtn'),
        browserAudioStatus: document.getElementById('browserAudioStatus'),
        eventStreamContainer: document.getElementById('eventStreamContainer'),
        clearLogsBtn: document.getElementById('clearLogsBtn')
    };

    // Helper: API Client
    async function apiRequest(endpoint, options = {}) {
        const headers = {
            'Content-Type': 'application/json',
            'x-admin-key': state.adminKey,
            ...(options.headers || {})
        };
        try {
            const res = await fetch(endpoint, { ...options, headers });
            const data = await res.json();
            if (!res.ok) {
                if (res.status === 401) {
                    handleLogout();
                }
                throw new Error(data.error || `HTTP ${res.status}`);
            }
            return data;
        } catch (err) {
            console.error(`[API Error] ${endpoint}:`, err);
            throw err;
        }
    }

    // Toast Notifications
    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        const icons = { success: '✅', error: '❌', info: 'ℹ️' };
        toast.innerHTML = `<span>${icons[type] || 'ℹ️'}</span><span>${message}</span>`;
        el.toastContainer.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(100%)';
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }

    // 1. AUTHENTICATION
    async function checkAuth() {
        if (!state.adminKey) {
            showLogin();
            return;
        }
        try {
            await apiRequest('/api/admin/auth/check');
            showDashboard();
        } catch (_) {
            showLogin();
        }
    }

    function showLogin() {
        el.authModal.classList.remove('hidden');
        el.dashboardApp.classList.add('hidden');
        if (state.pollTimer) clearInterval(state.pollTimer);
    }

    function showDashboard() {
        el.authModal.classList.add('hidden');
        el.dashboardApp.classList.remove('hidden');
        initMap();
        fetchInitialData();
        startPolling();
    }

    function handleLogout() {
        state.adminKey = '';
        localStorage.removeItem('kt_admin_key');
        if (state.pollTimer) clearInterval(state.pollTimer);
        if (state.audioWs) state.audioWs.close();
        showLogin();
    }

    // 2. INITIALIZE RADAR MAP
    function initMap() {
        if (state.map) return;

        state.map = L.map('radarMap', {
            zoomControl: true,
            attributionControl: false
        }).setView([51.329480, -0.119095], 14);

        // Dark tile layer (CartoDB Dark Matter)
        L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
            subdomains: 'abcd',
            maxZoom: 20
        }).addTo(state.map);

        // Map Click Calibration Handler
        state.map.on('click', (e) => {
            if (!state.mapClickMode) return;
            const { lat, lng } = e.latlng;
            const currentCircle = getCurrentCircle();
            if (!currentCircle) return;

            if (state.mapClickMode === 'home') {
                saveGeofenceLocation(currentCircle.id, {
                    homeLat: lat,
                    homeLng: lng,
                    isHomeCalibrated: true
                });
                showToast(`🏠 Home calibrated to ${lat.toFixed(6)}, ${lng.toFixed(6)}`, 'success');
            } else if (state.mapClickMode === 'work') {
                saveGeofenceLocation(currentCircle.id, {
                    workLat: lat,
                    workLng: lng,
                    isWorkCalibrated: true
                });
                showToast(`🏢 Work calibrated to ${lat.toFixed(6)}, ${lng.toFixed(6)}`, 'success');
            }
            exitMapCalibrateMode();
        });
    }

    function exitMapCalibrateMode() {
        state.mapClickMode = null;
        el.mapCalibrateBanner.classList.add('hidden');
        el.radarMap.style.cursor = '';
    }

    function enterMapCalibrateMode(mode) {
        state.mapClickMode = mode;
        el.mapCalibrateBanner.classList.remove('hidden');
        el.mapCalibrateText.innerText = `Click on the map to set ${mode === 'home' ? '🏠 Home' : '🏢 Work'} coordinates`;
        el.radarMap.style.cursor = 'crosshair';
    }

    // 3. DATA FETCHING & POLLING
    async function fetchInitialData() {
        await Promise.all([
            loadCircles(),
            loadStats(),
            loadEvents()
        ]);
    }

    function startPolling() {
        if (state.pollTimer) clearInterval(state.pollTimer);
        state.pollTimer = setInterval(async () => {
            await Promise.all([
                loadCircles(false),
                loadStats(false),
                loadEvents(false)
            ]);
        }, 3000);
    }

    async function loadCircles(updatePicker = true) {
        try {
            const data = await apiRequest('/api/admin/circles');
            state.circles = data.circles || [];

            if (state.circles.length === 0) {
                el.membersList.innerHTML = '<div class="empty-state">No family circles found.</div>';
                return;
            }

            if (!state.currentCircleId || !state.circles.find(c => c.id === state.currentCircleId)) {
                state.currentCircleId = state.circles[0].id;
            }

            if (updatePicker) {
                renderCirclePicker();
            }

            renderCurrentCircle();
        } catch (err) {
            console.error('Failed to load circles:', err);
        }
    }

    function renderCirclePicker() {
        el.circleSelect.innerHTML = '';
        state.circles.forEach(circle => {
            const opt = document.createElement('option');
            opt.value = circle.id;
            opt.innerText = `${circle.name} (${circle.inviteCode || circle.legacyPin || 'NO PIN'})`;
            if (circle.id === state.currentCircleId) opt.selected = true;
            el.circleSelect.appendChild(opt);
        });
    }

    function getCurrentCircle() {
        return state.circles.find(c => c.id === state.currentCircleId) || null;
    }

    function renderCurrentCircle() {
        const circle = getCurrentCircle();
        if (!circle) return;

        el.mapCircleName.innerText = circle.name;
        el.inviteCodeDisplay.innerText = circle.inviteCode || circle.legacyPin || 'KT-4666';
        state.members = circle.members || [];

        // Render Stats
        updateStatsView(circle);

        // Render Map Geofences & Members
        updateMapGeofences(circle);
        updateMapMarkers(state.members);

        // Render Roster List
        renderMembersList(state.members);
    }

    // 4. STATS & TELEMETRY VIEW
    async function loadStats() {
        try {
            const data = await apiRequest('/api/admin/stats');
            state.stats = data.system || {};
            state.audioStats = data.audio || {};

            // Update Header Telemetry
            if (state.stats.uptimeSeconds) {
                const hours = Math.floor(state.stats.uptimeSeconds / 3600);
                const mins = Math.floor((state.stats.uptimeSeconds % 3600) / 60);
                el.serverUptime.innerText = `${hours}h ${mins}m`;
            }
            if (state.stats.processRssMB) {
                el.serverRam.innerText = `${state.stats.processRssMB} MB`;
            }

            // Global SOS Check
            if (data.activeAlarms && data.activeAlarms.length > 0) {
                el.globalSosBanner.classList.remove('hidden');
                const alarmNames = data.activeAlarms.map(a => a.member.name).join(', ');
                el.sosBannerText.innerText = `🚨 ACTIVE SOS DISTRESS: ${alarmNames.toUpperCase()}`;
            } else {
                el.globalSosBanner.classList.add('hidden');
            }

            // Audio Relay Stats
            updateAudioRelayUI();
        } catch (err) {
            console.error('Failed to load stats:', err);
        }
    }

    function updateStatsView(circle) {
        const members = circle.members || [];
        const activeCount = members.filter(m => (Date.now() - Number(m.lastActive || 0)) < 15 * 60 * 1000).length;
        el.statActiveCount.innerText = activeCount;
        el.statTotalCount.innerText = `/ ${members.length} registered`;

        // Battery calculation
        if (members.length > 0) {
            const totalBat = members.reduce((sum, m) => sum + (Number(m.batteryPercentage) || 0), 0);
            const avgBat = Math.round(totalBat / members.length);
            const chargingCount = members.filter(m => m.isCharging).length;
            el.statAvgBattery.innerText = `${avgBat}%`;
            el.statChargingCount.innerText = `⚡ ${chargingCount} charging`;
        } else {
            el.statAvgBattery.innerText = '--%';
            el.statChargingCount.innerText = '0 charging';
        }

        // Geofence status tags
        el.statHomeStatus.innerText = `Home ${circle.homeRadiusMeters || 140}m`;
        el.statHomeStatus.className = `stat-tag ${circle.isHomeCalibrated ? 'success' : 'info'}`;
        el.statWorkStatus.innerText = `Work ${circle.workRadiusMeters || 75}m`;
        el.statWorkStatus.className = `stat-tag ${circle.isWorkCalibrated ? 'success' : 'info'}`;
    }

    // 5. MAP RENDERING
    function updateMapGeofences(circle) {
        if (!state.map) return;

        // HOME GEOFENCE
        if (circle.homeLat && circle.homeLng) {
            const homeLatLng = [circle.homeLat, circle.homeLng];
            if (!state.homeCircleLayer) {
                state.homeCircleLayer = L.circle(homeLatLng, {
                    radius: circle.homeRadiusMeters || 140,
                    color: '#00ff88',
                    fillColor: '#00ff88',
                    fillOpacity: 0.15,
                    weight: 2,
                    dashArray: '4, 8'
                }).addTo(state.map);
            } else {
                state.homeCircleLayer.setLatLng(homeLatLng);
                state.homeCircleLayer.setRadius(circle.homeRadiusMeters || 140);
            }

            if (!state.homeCenterMarker) {
                const homeIcon = L.divIcon({
                    className: 'custom-radar-pin',
                    html: '<div class="radar-pin-container"><div class="radar-pin-avatar" style="border-color:#00ff88">🏠</div><div class="radar-pin-label">HOME</div></div>',
                    iconSize: [40, 50],
                    iconAnchor: [20, 45]
                });
                state.homeCenterMarker = L.marker(homeLatLng, { icon: homeIcon }).addTo(state.map);
            } else {
                state.homeCenterMarker.setLatLng(homeLatLng);
            }
        }

        // WORK GEOFENCE
        if (circle.workLat && circle.workLng && (circle.workLat !== 0 || circle.workLng !== 0)) {
            const workLatLng = [circle.workLat, circle.workLng];
            if (!state.workCircleLayer) {
                state.workCircleLayer = L.circle(workLatLng, {
                    radius: circle.workRadiusMeters || 75,
                    color: '#00f0ff',
                    fillColor: '#00f0ff',
                    fillOpacity: 0.15,
                    weight: 2,
                    dashArray: '4, 8'
                }).addTo(state.map);
            } else {
                state.workCircleLayer.setLatLng(workLatLng);
                state.workCircleLayer.setRadius(circle.workRadiusMeters || 75);
            }

            if (!state.workCenterMarker) {
                const workIcon = L.divIcon({
                    className: 'custom-radar-pin',
                    html: '<div class="radar-pin-container"><div class="radar-pin-avatar" style="border-color:#00f0ff">🏢</div><div class="radar-pin-label">WORK</div></div>',
                    iconSize: [40, 50],
                    iconAnchor: [20, 45]
                });
                state.workCenterMarker = L.marker(workLatLng, { icon: workIcon }).addTo(state.map);
            } else {
                state.workCenterMarker.setLatLng(workLatLng);
            }
        }
    }

    function updateMapMarkers(members) {
        if (!state.map) return;

        const currentMemberIds = new Set(members.map(m => m.id));

        // Remove old markers
        Object.keys(state.markers).forEach(id => {
            if (!currentMemberIds.has(id)) {
                state.map.removeLayer(state.markers[id]);
                delete state.markers[id];
            }
        });

        // Add or update markers
        members.forEach(m => {
            const lat = Number(m.y);
            const lng = Number(m.x);
            if (!lat || !lng) return;

            const isAlarm = m.statusText === '🚨 ALARM';
            const color = m.avatarColorHex || '#00ff88';
            const emoji = m.avatarEmoji || '📱';

            const markerHtml = `
                <div class="radar-pin-container">
                    <div class="radar-pin-avatar ${isAlarm ? 'alarm' : ''}" style="border-color:${isAlarm ? '#ff3366' : color}">
                        ${emoji}
                    </div>
                    <div class="radar-pin-label">
                        ${m.name} (${m.batteryPercentage}%${m.isCharging ? '⚡' : ''})
                    </div>
                </div>
            `;

            const icon = L.divIcon({
                className: 'custom-radar-pin',
                html: markerHtml,
                iconSize: [40, 55],
                iconAnchor: [20, 48]
            });

            if (state.markers[m.id]) {
                state.markers[m.id].setLatLng([lat, lng]);
                state.markers[m.id].setIcon(icon);
            } else {
                const marker = L.marker([lat, lng], { icon }).addTo(state.map);
                marker.on('click', () => openMemberControlModal(m));
                state.markers[m.id] = marker;
            }
        });
    }

    function fitMapToAll() {
        const bounds = [];
        const circle = getCurrentCircle();
        if (circle && circle.homeLat && circle.homeLng) {
            bounds.push([circle.homeLat, circle.homeLng]);
        }
        state.members.forEach(m => {
            if (m.y && m.x) bounds.push([m.y, m.x]);
        });
        if (bounds.length > 0 && state.map) {
            state.map.fitBounds(bounds, { padding: [50, 50], maxZoom: 16 });
        }
    }

    // 6. ROSTER RENDERING
    function renderMembersList(members) {
        if (!members || members.length === 0) {
            el.membersList.innerHTML = '<div class="empty-state">No devices registered in this circle.</div>';
            return;
        }

        el.membersList.innerHTML = '';
        members.forEach(m => {
            const card = document.createElement('div');
            const isAlarm = m.statusText === '🚨 ALARM';
            card.className = `member-card ${isAlarm ? 'alarm-active' : ''}`;

            const badgeClass = isAlarm ? 'alarm' :
                m.isLocationPaused ? 'paused' :
                (m.speedMph > 5 ? 'moving' : 'home');

            const relativeTime = getRelativeTime(m.lastActive);

            card.innerHTML = `
                <div class="member-main">
                    <div class="member-avatar" style="border-color: ${isAlarm ? '#ff3366' : (m.avatarColorHex || '#00ff88')}">
                        ${m.avatarEmoji || '📱'}
                    </div>
                    <div class="member-name-group">
                        <div class="member-name">${m.name}</div>
                        <div class="member-status-line">
                            <span class="member-badge ${badgeClass}">${m.statusText || 'Active'}</span>
                            ${m.speedMph > 2 ? `<span>${m.speedMph} mph</span>` : ''}
                        </div>
                    </div>
                </div>
                <div class="member-telemetry-col">
                    <div class="battery-pill ${m.isCharging ? 'charging' : (m.batteryPercentage < 20 ? 'low' : '')}">
                        ${m.isCharging ? '⚡' : '🔋'} ${m.batteryPercentage}%
                    </div>
                    <div class="member-last-active">${relativeTime}</div>
                </div>
            `;

            card.addEventListener('click', () => openMemberControlModal(m));
            el.membersList.appendChild(card);
        });
    }

    function getRelativeTime(timestamp) {
        if (!timestamp) return 'Never';
        const diffSec = Math.floor((Date.now() - Number(timestamp)) / 1000);
        if (diffSec < 10) return 'Just now';
        if (diffSec < 60) return `${diffSec}s ago`;
        if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
        if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
        return `${Math.floor(diffSec / 86400)}d ago`;
    }

    // 7. GEOFENCE CONFIGURATION MODAL
    function openCircleConfigModal() {
        const circle = getCurrentCircle();
        if (!circle) return;

        el.cfgCircleName.value = circle.name || '';
        el.cfgInviteCode.value = circle.inviteCode || circle.legacyPin || '';
        el.cfgHomeLat.value = circle.homeLat || '';
        el.cfgHomeLng.value = circle.homeLng || '';
        el.cfgHomeRadius.value = circle.homeRadiusMeters || 140;
        el.cfgHomeCalibrated.checked = Boolean(circle.isHomeCalibrated);

        el.cfgWorkLat.value = circle.workLat || '';
        el.cfgWorkLng.value = circle.workLng || '';
        el.cfgWorkRadius.value = circle.workRadiusMeters || 75;
        el.cfgWorkCalibrated.checked = Boolean(circle.isWorkCalibrated);

        el.circleConfigModal.classList.remove('hidden');
    }

    async function saveGeofenceLocation(circleId, updates) {
        try {
            await apiRequest(`/api/admin/circles/${circleId}`, {
                method: 'PUT',
                body: JSON.stringify(updates)
            });
            await loadCircles(false);
        } catch (err) {
            showToast(`Failed to update geofence: ${err.message}`, 'error');
        }
    }

    // 8. MEMBER CONTROL MODAL
    function openMemberControlModal(member) {
        el.ctrlMemberId.value = member.id;
        el.ctrlMemberName.value = member.name || '';
        el.ctrlMemberEmoji.value = member.avatarEmoji || '📱';
        el.ctrlMemberColor.value = member.avatarColorHex || '#00ff88';
        el.ctrlMemberLat.value = member.y || '';
        el.ctrlMemberLng.value = member.x || '';
        el.ctrlMemberBattery.value = member.batteryPercentage || 100;
        el.ctrlMemberSpeed.value = member.speedMph || 0;
        el.ctrlMemberStatus.value = member.statusText || 'Active';
        el.ctrlMemberCharging.checked = Boolean(member.isCharging);
        el.ctrlMemberPaused.checked = Boolean(member.isLocationPaused);

        const isAlarm = member.statusText === '🚨 ALARM';
        el.ctrlTriggerAlarmBtn.innerText = isAlarm ? '✅ Silence SOS Alarm' : '🚨 Trigger SOS Alarm';
        el.ctrlTriggerAlarmBtn.className = isAlarm ? 'btn btn-sm btn-emerald' : 'btn btn-sm btn-danger';

        el.memberControlModal.classList.remove('hidden');
    }

    // 9. AUDIO RELAY PLAYBACK IN BROWSER
    function updateAudioRelayUI() {
        const circle = getCurrentCircle();
        if (!circle) return;

        const room = state.audioStats.rooms ? state.audioStats.rooms[circle.id] : null;
        if (room && room.hasBroadcaster) {
            el.statAudioStatus.innerText = 'LIVE BROADCAST';
            el.statAudioStatus.className = 'stat-tag live-audio';
            el.statListenersCount.innerText = `${room.listenerCount} listener(s)`;
            el.audioBroadcastStatus.innerText = 'STREAMING LIVE';
            el.audioBroadcastStatus.className = 'badge-pill secure-badge';
            el.audioBroadcasterName.innerText = `🎙️ ${room.broadcaster?.memberName || 'Broadcaster'} is live`;
            el.audioWave.classList.add('active');
            el.startListenAudioBtn.disabled = false;
        } else {
            el.statAudioStatus.innerText = 'STANDBY';
            el.statAudioStatus.className = 'stat-tag';
            el.statListenersCount.innerText = '0 listeners';
            el.audioBroadcastStatus.innerText = 'IDLE';
            el.audioBroadcastStatus.className = 'badge-pill';
            el.audioBroadcasterName.innerText = 'No active SOS voice stream';
            el.audioWave.classList.remove('active');
            if (!state.isPlayingAudio) {
                el.startListenAudioBtn.disabled = true;
            }
        }
    }

    function startListeningAudio() {
        const circle = getCurrentCircle();
        if (!circle) return;

        try {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            state.audioCtx = new AudioContext({ sampleRate: 16000 });

            const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${wsProtocol}//${window.location.host}/audio?role=listen&circleId=${circle.id}&memberId=admin_browser_dashboard&memberName=AdminDashboard`;

            state.audioWs = new WebSocket(wsUrl);
            state.audioWs.binaryType = 'arraybuffer';

            state.audioWs.onopen = () => {
                state.isPlayingAudio = true;
                el.startListenAudioBtn.classList.add('hidden');
                el.stopListenAudioBtn.classList.remove('hidden');
                el.browserAudioStatus.innerText = 'Connected & listening...';
                showToast('🎧 Connected to live voice stream', 'success');
            };

            let nextStartTime = 0;
            state.audioWs.onmessage = (event) => {
                if (typeof event.data === 'string') {
                    const msg = JSON.parse(event.data);
                    if (msg.status === 'idle') {
                        el.browserAudioStatus.innerText = 'Broadcaster stopped.';
                    }
                    return;
                }

                // Raw PCM 16-bit Mono 16kHz audio buffer
                const arrayBuffer = event.data;
                const int16Array = new Int16Array(arrayBuffer);
                const float32Array = new Float32Array(int16Array.length);

                for (let i = 0; i < int16Array.length; i++) {
                    float32Array[i] = int16Array[i] / 32768.0;
                }

                const audioBuffer = state.audioCtx.createBuffer(1, float32Array.length, 16000);
                audioBuffer.copyToChannel(float32Array, 0);

                const source = state.audioCtx.createBufferSource();
                source.buffer = audioBuffer;
                source.connect(state.audioCtx.destination);

                const currentTime = state.audioCtx.currentTime;
                if (nextStartTime < currentTime) {
                    nextStartTime = currentTime + 0.05;
                }
                source.start(nextStartTime);
                nextStartTime += audioBuffer.duration;
            };

            state.audioWs.onclose = () => {
                stopListeningAudio();
            };

            state.audioWs.onerror = (err) => {
                console.error('[Audio WS Error]', err);
                stopListeningAudio();
            };

        } catch (err) {
            showToast(`Audio Playback Error: ${err.message}`, 'error');
        }
    }

    function stopListeningAudio() {
        state.isPlayingAudio = false;
        if (state.audioWs) {
            state.audioWs.close();
            state.audioWs = null;
        }
        if (state.audioCtx) {
            state.audioCtx.close();
            state.audioCtx = null;
        }
        el.startListenAudioBtn.classList.remove('hidden');
        el.stopListenAudioBtn.classList.add('hidden');
        el.browserAudioStatus.innerText = 'Player idle';
    }

    // 10. ACTIVITY EVENT STREAM
    async function loadEvents() {
        try {
            const data = await apiRequest('/api/admin/events?limit=40');
            state.events = data.events || [];
            renderEventStream();
        } catch (_) {}
    }

    function renderEventStream() {
        el.eventStreamContainer.innerHTML = '';
        if (state.events.length === 0) {
            el.eventStreamContainer.innerHTML = '<div class="text-dim">No events recorded yet.</div>';
            return;
        }

        state.events.forEach(evt => {
            const row = document.createElement('div');
            row.className = 'event-row';
            const timeStr = new Date(evt.timestamp).toLocaleTimeString();
            row.innerHTML = `
                <span class="event-time">${timeStr}</span>
                <span class="event-tag ${evt.type}">${evt.type}</span>
                <span class="event-msg">${evt.message}</span>
            `;
            el.eventStreamContainer.appendChild(row);
        });
    }

    // 11. EVENT LISTENERS SETUP
    function setupEventListeners() {
        // Login Form
        el.loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const key = el.adminKeyInput.value.trim();
            try {
                const data = await apiRequest('/api/admin/login', {
                    method: 'POST',
                    body: JSON.stringify({ key })
                });
                state.adminKey = data.token;
                localStorage.setItem('kt_admin_key', state.adminKey);
                showDashboard();
                showToast('Welcome to Kin-Tracker Console', 'success');
            } catch (err) {
                el.loginError.innerText = err.message || 'Invalid passcode';
                el.loginError.classList.remove('hidden');
            }
        });

        // Logout
        el.logoutBtn.addEventListener('click', handleLogout);

        // Refresh
        el.refreshBtn.addEventListener('click', async () => {
            await fetchInitialData();
            showToast('Telemetry refreshed', 'info');
        });

        // Circle Select
        el.circleSelect.addEventListener('change', (e) => {
            state.currentCircleId = e.target.value;
            renderCurrentCircle();
            fitMapToAll();
        });

        // Copy Invite Code
        el.copyInviteBtn.addEventListener('click', () => {
            const code = el.inviteCodeDisplay.innerText;
            navigator.clipboard.writeText(code);
            showToast(`Copied code "${code}" to clipboard`, 'success');
        });

        // Regenerate Invite Code
        el.regenInviteBtn.addEventListener('click', async () => {
            const circle = getCurrentCircle();
            if (!circle || !confirm(`Regenerate invite code for "${circle.name}"?`)) return;
            try {
                const res = await apiRequest(`/api/admin/circles/${circle.id}/regenerate-code`, { method: 'POST' });
                showToast(`New Invite Code generated: ${res.inviteCode}`, 'success');
                await loadCircles(false);
            } catch (err) {
                showToast(`Failed to regenerate code: ${err.message}`, 'error');
            }
        });

        // Map Control Buttons
        el.fitMapBtn.addEventListener('click', fitMapToAll);
        el.focusHomeBtn.addEventListener('click', () => {
            const circle = getCurrentCircle();
            if (circle && circle.homeLat && circle.homeLng && state.map) {
                state.map.flyTo([circle.homeLat, circle.homeLng], 16);
            }
        });
        el.focusWorkBtn.addEventListener('click', () => {
            const circle = getCurrentCircle();
            if (circle && circle.workLat && circle.workLng && state.map) {
                state.map.flyTo([circle.workLat, circle.workLng], 16);
            }
        });

        // Map Click Calibrate Modes
        el.mapClickCalibrateHomeBtn.addEventListener('click', () => enterMapCalibrateMode('home'));
        el.mapClickCalibrateWorkBtn.addEventListener('click', () => enterMapCalibrateMode('work'));
        el.cancelMapCalibrateBtn.addEventListener('click', exitMapCalibrateMode);

        // Circle Config Modal
        el.openCircleConfigBtn.addEventListener('click', openCircleConfigModal);
        el.circleConfigForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const circle = getCurrentCircle();
            if (!circle) return;

            const updates = {
                name: el.cfgCircleName.value.trim(),
                homeLat: Number(el.cfgHomeLat.value),
                homeLng: Number(el.cfgHomeLng.value),
                homeRadiusMeters: Number(el.cfgHomeRadius.value),
                isHomeCalibrated: el.cfgHomeCalibrated.checked,
                workLat: Number(el.cfgWorkLat.value),
                workLng: Number(el.cfgWorkLng.value),
                workRadiusMeters: Number(el.cfgWorkRadius.value),
                isWorkCalibrated: el.cfgWorkCalibrated.checked
            };

            await saveGeofenceLocation(circle.id, updates);
            el.circleConfigModal.classList.add('hidden');
            showToast('Geofence settings updated successfully', 'success');
        });

        // Browser GPS for Home & Work in Config Modal
        el.cfgSetHomeCurrentGPS.addEventListener('click', () => {
            if (!navigator.geolocation) return showToast('Geolocation not supported in browser', 'error');
            navigator.geolocation.getCurrentPosition((pos) => {
                el.cfgHomeLat.value = pos.coords.latitude.toFixed(6);
                el.cfgHomeLng.value = pos.coords.longitude.toFixed(6);
                el.cfgHomeCalibrated.checked = true;
                showToast('Home set to current browser GPS position', 'info');
            }, (err) => showToast(err.message, 'error'));
        });

        el.cfgSetWorkCurrentGPS.addEventListener('click', () => {
            if (!navigator.geolocation) return showToast('Geolocation not supported in browser', 'error');
            navigator.geolocation.getCurrentPosition((pos) => {
                el.cfgWorkLat.value = pos.coords.latitude.toFixed(6);
                el.cfgWorkLng.value = pos.coords.longitude.toFixed(6);
                el.cfgWorkCalibrated.checked = true;
                showToast('Work set to current browser GPS position', 'info');
            }, (err) => showToast(err.message, 'error'));
        });

        // Member Control Form
        el.memberControlForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const memberId = el.ctrlMemberId.value;
            const updates = {
                name: el.ctrlMemberName.value.trim(),
                avatarEmoji: el.ctrlMemberEmoji.value.trim(),
                avatarColorHex: el.ctrlMemberColor.value,
                lat: Number(el.ctrlMemberLat.value),
                lng: Number(el.ctrlMemberLng.value),
                batteryPercentage: Number(el.ctrlMemberBattery.value),
                speedMph: Number(el.ctrlMemberSpeed.value),
                statusText: el.ctrlMemberStatus.value.trim(),
                isCharging: el.ctrlMemberCharging.checked,
                isLocationPaused: el.ctrlMemberPaused.checked
            };

            try {
                await apiRequest(`/api/admin/members/${memberId}/update`, {
                    method: 'POST',
                    body: JSON.stringify(updates)
                });
                el.memberControlModal.classList.add('hidden');
                showToast(`Device ${updates.name} updated`, 'success');
                await loadCircles(false);
            } catch (err) {
                showToast(`Failed to update device: ${err.message}`, 'error');
            }
        });

        // Trigger / Silence Alarm
        el.ctrlTriggerAlarmBtn.addEventListener('click', async () => {
            const memberId = el.ctrlMemberId.value;
            try {
                const res = await apiRequest(`/api/admin/members/${memberId}/toggle-alarm`, { method: 'POST' });
                el.memberControlModal.classList.add('hidden');
                showToast(res.isAlarm ? '🚨 SOS Distress Alarm Triggered' : '✅ SOS Alarm Resolved', res.isAlarm ? 'error' : 'success');
                await loadCircles(false);
            } catch (err) {
                showToast(`Alarm toggle failed: ${err.message}`, 'error');
            }
        });

        // Resolve All SOS from Banner
        el.resolveAllSosBtn.addEventListener('click', async () => {
            const circle = getCurrentCircle();
            if (!circle) return;
            try {
                for (const m of (circle.members || [])) {
                    if (m.statusText === '🚨 ALARM') {
                        await apiRequest(`/api/admin/members/${m.id}/toggle-alarm`, { method: 'POST' });
                    }
                }
                showToast('All active SOS distress alarms resolved', 'success');
                await loadCircles(false);
                await loadStats();
            } catch (err) {
                showToast(`Failed to resolve alarms: ${err.message}`, 'error');
            }
        });

        // Kick Member
        el.ctrlKickMemberBtn.addEventListener('click', async () => {
            const memberId = el.ctrlMemberId.value;
            const circle = getCurrentCircle();
            if (!circle || !confirm('Are you sure you want to remove this device from the circle?')) return;
            try {
                await apiRequest(`/api/admin/circles/${circle.id}/members/${memberId}`, { method: 'DELETE' });
                el.memberControlModal.classList.add('hidden');
                showToast('Device removed from circle', 'success');
                await loadCircles(false);
            } catch (err) {
                showToast(`Failed to remove device: ${err.message}`, 'error');
            }
        });

        // Broadcast Modal
        el.broadcastBtn.addEventListener('click', () => {
            el.broadcastMessageInput.value = '';
            el.broadcastModal.classList.remove('hidden');
        });
        el.broadcastForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const msg = el.broadcastMessageInput.value.trim();
            const circle = getCurrentCircle();
            try {
                await apiRequest('/api/admin/broadcast', {
                    method: 'POST',
                    body: JSON.stringify({ circleId: circle?.id, message: msg, statusText: msg })
                });
                el.broadcastModal.classList.add('hidden');
                showToast('Broadcast sent to all circle devices', 'success');
                await loadCircles(false);
            } catch (err) {
                showToast(`Broadcast failed: ${err.message}`, 'error');
            }
        });

        // Backup Modal & Snapshot Export / Import
        el.backupModalBtn.addEventListener('click', () => el.backupModal.classList.remove('hidden'));
        el.downloadBackupBtn.addEventListener('click', () => {
            window.location.href = `/api/admin/backup/export?adminKey=${encodeURIComponent(state.adminKey)}`;
        });
        el.restoreFileInput.addEventListener('change', (e) => {
            el.executeRestoreBtn.disabled = !e.target.files.length;
        });
        el.executeRestoreBtn.addEventListener('click', async () => {
            const file = el.restoreFileInput.files[0];
            if (!file || !confirm('WARNING: Restoring will overwrite the current database. Proceed?')) return;
            try {
                const text = await file.text();
                const json = JSON.parse(text);
                await apiRequest('/api/admin/backup/import', {
                    method: 'POST',
                    body: JSON.stringify(json)
                });
                el.backupModal.classList.add('hidden');
                showToast('Database snapshot restored successfully!', 'success');
                await loadCircles();
            } catch (err) {
                showToast(`Restore failed: ${err.message}`, 'error');
            }
        });

        // Audio Stream Controls
        el.startListenAudioBtn.addEventListener('click', startListeningAudio);
        el.stopListenAudioBtn.addEventListener('click', stopListeningAudio);

        // Clear Logs
        el.clearLogsBtn.addEventListener('click', () => {
            el.eventStreamContainer.innerHTML = '<div class="text-dim">Logs view cleared.</div>';
        });

        // Modal Close Buttons
        document.querySelectorAll('[data-close]').forEach(btn => {
            btn.addEventListener('click', () => {
                const modalId = btn.getAttribute('data-close');
                const target = document.getElementById(modalId);
                if (target) target.classList.add('hidden');
            });
        });
    }

    // App Initialization Entry
    document.addEventListener('DOMContentLoaded', () => {
        setupEventListeners();
        checkAuth();
    });

})();
