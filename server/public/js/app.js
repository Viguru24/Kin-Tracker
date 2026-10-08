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
        placeLayers: {},
        placeMarkers: {},
        mapClickMode: null, // 'home', 'work', or 'place'
        pollTimer: null,
        audioWs: null,
        audioCtx: null,
        isPlayingAudio: false,
        ringingTimers: {} // memberId -> remaining seconds
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
        cfgCircleEmoji: document.getElementById('cfgCircleEmoji'),
        cfgInviteCode: document.getElementById('cfgInviteCode'),
        cfgHomeName: document.getElementById('cfgHomeName'),
        cfgHomeEmoji: document.getElementById('cfgHomeEmoji'),
        cfgHomeLat: document.getElementById('cfgHomeLat'),
        cfgHomeLng: document.getElementById('cfgHomeLng'),
        cfgHomeRadius: document.getElementById('cfgHomeRadius'),
        cfgHomeCalibrated: document.getElementById('cfgHomeCalibrated'),
        cfgWorkName: document.getElementById('cfgWorkName'),
        cfgWorkEmoji: document.getElementById('cfgWorkEmoji'),
        cfgWorkLat: document.getElementById('cfgWorkLat'),
        cfgWorkLng: document.getElementById('cfgWorkLng'),
        cfgWorkRadius: document.getElementById('cfgWorkRadius'),
        cfgWorkCalibrated: document.getElementById('cfgWorkCalibrated'),
        cfgSetHomeCurrentGPS: document.getElementById('cfgSetHomeCurrentGPS'),
        cfgSetWorkCurrentGPS: document.getElementById('cfgSetWorkCurrentGPS'),
        cfgPickHomeOnMapBtn: document.getElementById('cfgPickHomeOnMapBtn'),
        cfgPickWorkOnMapBtn: document.getElementById('cfgPickWorkOnMapBtn'),
        customSafeZonesList: document.getElementById('customSafeZonesList'),
        addNewPlaceBtn: document.getElementById('addNewPlaceBtn'),
        customPlaceModal: document.getElementById('customPlaceModal'),
        customPlaceForm: document.getElementById('customPlaceForm'),
        customPlaceModalTitle: document.getElementById('customPlaceModalTitle'),
        placeId: document.getElementById('placeId'),
        placeNameInput: document.getElementById('placeNameInput'),
        placeEmojiInput: document.getElementById('placeEmojiInput'),
        placeLatInput: document.getElementById('placeLatInput'),
        placeLngInput: document.getElementById('placeLngInput'),
        placeRadiusInput: document.getElementById('placeRadiusInput'),
        placeColorInput: document.getElementById('placeColorInput'),
        placeSetCurrentGPS: document.getElementById('placeSetCurrentGPS'),
        placePickOnMapBtn: document.getElementById('placePickOnMapBtn'),
        memberControlModal: document.getElementById('memberControlModal'),
        memberControlForm: document.getElementById('memberControlForm'),
        ctrlHeaderAvatar: document.getElementById('ctrlHeaderAvatar'),
        memberModalTitle: document.getElementById('memberModalTitle'),
        memberModalSubtitle: document.getElementById('memberModalSubtitle'),
        ringPulseIcon: document.getElementById('ringPulseIcon'),
        ringActionTitle: document.getElementById('ringActionTitle'),
        ringActionSubtitle: document.getElementById('ringActionSubtitle'),
        ctrlPrimaryRingBtn: document.getElementById('ctrlPrimaryRingBtn'),
        ringBtnIcon: document.getElementById('ringBtnIcon'),
        ringBtnText: document.getElementById('ringBtnText'),
        ctrlLocateOnMapBtn: document.getElementById('ctrlLocateOnMapBtn'),
        ctrlCallPhoneLink: document.getElementById('ctrlCallPhoneLink'),
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
            attributionControl: false,
            maxZoom: 21,
            minZoom: 2
        }).setView([51.329480, -0.119095], 14);

        // Native Dark Radar Map via Esri World Dark Gray Base (No API key, 100% reliable)
        // Set maxNativeZoom: 16 and maxZoom: 21 so Leaflet seamlessly overzooms tiles up to level 21
        L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}', {
            maxNativeZoom: 16,
            maxZoom: 21,
            attribution: 'Tiles &copy; Esri &mdash; Esri, DeLorme, NAVTEQ'
        }).addTo(state.map);

        // Dark Gray Reference Overlay (Labels, Streets, Borders)
        L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}', {
            maxNativeZoom: 16,
            maxZoom: 21,
            opacity: 0.9
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
            } else if (state.mapClickMode === 'place') {
                el.placeLatInput.value = lat.toFixed(6);
                el.placeLngInput.value = lng.toFixed(6);
                el.customPlaceModal.classList.remove('hidden');
                showToast(`📍 Place coordinates set to ${lat.toFixed(6)}, ${lng.toFixed(6)}`, 'info');
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
        const label = mode === 'home' ? '🏠 Home' : mode === 'work' ? '🏢 Work' : '📍 Custom Place';
        el.mapCalibrateText.innerText = `Click on the map to set ${label} coordinates`;
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
        updateMapMarkers(state.members, circle);

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

        const homeName = circle.homeName || 'HOME';
        const homeEmoji = circle.homeEmoji || '🏠';
        const workName = circle.workName || 'WORK';
        const workEmoji = circle.workEmoji || '🏢';

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

            const homeIcon = L.divIcon({
                className: 'custom-radar-pin',
                html: `<div class="radar-pin-container"><div class="radar-pin-avatar" style="border-color:#00ff88">${homeEmoji}</div><div class="radar-pin-label">${homeName.toUpperCase()}</div></div>`,
                iconSize: [40, 50],
                iconAnchor: [20, 45]
            });

            if (!state.homeCenterMarker) {
                state.homeCenterMarker = L.marker(homeLatLng, { icon: homeIcon }).addTo(state.map);
                state.homeCenterMarker.on('click', openCircleConfigModal);
            } else {
                state.homeCenterMarker.setLatLng(homeLatLng);
                state.homeCenterMarker.setIcon(homeIcon);
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

            const workIcon = L.divIcon({
                className: 'custom-radar-pin',
                html: `<div class="radar-pin-container"><div class="radar-pin-avatar" style="border-color:#00f0ff">${workEmoji}</div><div class="radar-pin-label">${workName.toUpperCase()}</div></div>`,
                iconSize: [40, 50],
                iconAnchor: [20, 45]
            });

            if (!state.workCenterMarker) {
                state.workCenterMarker = L.marker(workLatLng, { icon: workIcon }).addTo(state.map);
                state.workCenterMarker.on('click', openCircleConfigModal);
            } else {
                state.workCenterMarker.setLatLng(workLatLng);
                state.workCenterMarker.setIcon(workIcon);
            }
        }

        // ADDITIONAL CUSTOM PLACES / SAFE ZONES (Eloisa's School, Isabel's Work, etc.)
        const safeZones = circle.safeZones || [];
        const currentZoneIds = new Set(safeZones.map(z => z.id));

        // Remove obsolete zone layers
        Object.keys(state.placeLayers).forEach(id => {
            if (!currentZoneIds.has(id)) {
                state.map.removeLayer(state.placeLayers[id]);
                delete state.placeLayers[id];
            }
        });
        Object.keys(state.placeMarkers).forEach(id => {
            if (!currentZoneIds.has(id)) {
                state.map.removeLayer(state.placeMarkers[id]);
                delete state.placeMarkers[id];
            }
        });

        // Add or update custom places
        safeZones.forEach(zone => {
            const lat = Number(zone.latitude);
            const lng = Number(zone.longitude);
            if (!lat || !lng) return;

            const color = zone.colorHex || '#A855F7';
            const emoji = zone.iconName || '🏫';
            const radius = Number(zone.radiusMeters) || 100;
            const zoneLatLng = [lat, lng];

            // Perimeter circle
            if (state.placeLayers[zone.id]) {
                state.placeLayers[zone.id].setLatLng(zoneLatLng);
                state.placeLayers[zone.id].setRadius(radius);
                state.placeLayers[zone.id].setStyle({ color, fillColor: color });
            } else {
                state.placeLayers[zone.id] = L.circle(zoneLatLng, {
                    radius,
                    color,
                    fillColor: color,
                    fillOpacity: 0.15,
                    weight: 2,
                    dashArray: '3, 6'
                }).addTo(state.map);
            }

            // Marker Pin
            const placeIcon = L.divIcon({
                className: 'custom-radar-pin',
                html: `<div class="radar-pin-container"><div class="radar-pin-avatar" style="border-color:${color}; box-shadow: 0 0 14px ${color}88;">${emoji}</div><div class="radar-pin-label">${zone.name.toUpperCase()}</div></div>`,
                iconSize: [40, 50],
                iconAnchor: [20, 45]
            });

            if (state.placeMarkers[zone.id]) {
                state.placeMarkers[zone.id].setLatLng(zoneLatLng);
                state.placeMarkers[zone.id].setIcon(placeIcon);
            } else {
                const marker = L.marker(zoneLatLng, { icon: placeIcon }).addTo(state.map);
                marker.on('click', () => openCustomPlaceModal(zone));
                state.placeMarkers[zone.id] = marker;
            }
        });
    }

    function distanceMeters(lat1, lon1, lat2, lon2) {
        const R = 6371000;
        const dLat = (lat2 - lat1) * Math.PI / 180;
        const dLon = (lon2 - lon1) * Math.PI / 180;
        const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                  Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
                  Math.sin(dLon / 2) * Math.sin(dLon / 2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    function computeClusterLayout(members, circle) {
        const homeLat = circle ? Number(circle.homeLat || 0) : 0;
        const homeLng = circle ? Number(circle.homeLng || 0) : 0;
        const homeRadius = circle ? Number(circle.homeRadiusMeters || 140) : 140;
        const workLat = circle ? Number(circle.workLat || 0) : 0;
        const workLng = circle ? Number(circle.workLng || 0) : 0;
        const workRadius = circle ? Number(circle.workRadiusMeters || 75) : 75;
        const isWorkCalibrated = Boolean(circle && circle.isWorkCalibrated);

        const adjustedCoordinates = {};
        const activeMembers = (members || []).filter(m => Number(m.x) !== 0 && Number(m.y) !== 0);

        const homeClusterMembers = [];
        const workClusterMembers = [];
        const unassignedMembers = [];

        function isMemberMoving(m) {
            if (m.isComingHome) return true;
            if (m.speedMph && Number(m.speedMph) >= 1.5) return true;
            const s = (m.statusText || '').toLowerCase();
            return s.includes('walk') || s.includes('moving') || s.includes('driving') ||
                   s.includes('bike') || s.includes('transit') || s.includes('commute');
        }

        activeMembers.forEach(m => {
            const lat = Number(m.y);
            const lng = Number(m.x);
            const atHomeStatus = (m.statusText || '').toLowerCase().includes('home');
            const atWorkStatus = (m.statusText || '').toLowerCase().includes('work');

            const distToHome = (homeLat && homeLng) ? distanceMeters(lat, lng, homeLat, homeLng) : Infinity;
            const isHome = (homeLat && homeLng) && (distToHome <= homeRadius || atHomeStatus);

            const distToWork = (isWorkCalibrated && workLat && workLng) ? distanceMeters(lat, lng, workLat, workLng) : Infinity;
            const isWork = (isWorkCalibrated && workLat && workLng) && (distToWork <= workRadius || atWorkStatus);

            if (isMemberMoving(m)) {
                adjustedCoordinates[m.id] = [lat, lng];
                return;
            }

            if (isHome) {
                homeClusterMembers.push(m);
            } else if (isWork) {
                workClusterMembers.push(m);
            } else {
                unassignedMembers.push(m);
            }
        });

        function layoutCluster(clusterList, anchorLat, anchorLng, isHomeOrWork = false) {
            if (clusterList.length === 0) return;
            if (clusterList.length === 1) {
                if (isHomeOrWork) {
                    const spreadRadiusMeters = 14.0;
                    const angle = -Math.PI / 2.0; // Place above center pin so both Home pin and avatar are visible
                    const cosLat = Math.cos(anchorLat * Math.PI / 180.0);
                    const deltaLat = (spreadRadiusMeters * Math.sin(angle)) / 111139.0;
                    const deltaLng = (spreadRadiusMeters * Math.cos(angle)) / (111139.0 * cosLat);
                    adjustedCoordinates[clusterList[0].id] = [anchorLat + deltaLat, anchorLng + deltaLng];
                } else {
                    adjustedCoordinates[clusterList[0].id] = [Number(clusterList[0].y), Number(clusterList[0].x)];
                }
                return;
            }

            const count = clusterList.length;
            const spreadRadiusMeters = count === 2 ? 18.0 : count === 3 ? 22.0 : count === 4 ? 26.0 : Math.max(28.0, count * 7.5);
            const startAngle = count === 2 ? -Math.PI / 2.0 : count === 4 ? -Math.PI / 4.0 : -Math.PI / 2.0;
            const angleStep = (2.0 * Math.PI) / count;
            const cosLat = Math.cos(anchorLat * Math.PI / 180.0);

            clusterList.sort((a, b) => String(a.id).localeCompare(String(b.id)));

            for (let idx = 0; idx < count; idx++) {
                const m = clusterList[idx];
                const angle = startAngle + (idx * angleStep);
                const deltaLat = (spreadRadiusMeters * Math.sin(angle)) / 111139.0;
                const deltaLng = (spreadRadiusMeters * Math.cos(angle)) / (111139.0 * cosLat);
                adjustedCoordinates[m.id] = [anchorLat + deltaLat, anchorLng + deltaLng];
            }
        }

        // Layout Home Cluster
        if (homeLat && homeLng && homeClusterMembers.length > 0) {
            layoutCluster(homeClusterMembers, homeLat, homeLng, true);
        } else {
            homeClusterMembers.forEach(m => { adjustedCoordinates[m.id] = [Number(m.y), Number(m.x)]; });
        }

        // Layout Work Cluster
        if (isWorkCalibrated && workLat && workLng && workClusterMembers.length > 0) {
            layoutCluster(workClusterMembers, workLat, workLng, true);
        } else {
            workClusterMembers.forEach(m => { adjustedCoordinates[m.id] = [Number(m.y), Number(m.x)]; });
        }

        // Ad-hoc clusters for unassigned members within 40m
        const visited = new Set();
        for (let i = 0; i < unassignedMembers.length; i++) {
            const m1 = unassignedMembers[i];
            if (visited.has(m1.id)) continue;

            const cluster = [m1];
            visited.add(m1.id);

            for (let j = i + 1; j < unassignedMembers.length; j++) {
                const m2 = unassignedMembers[j];
                if (visited.has(m2.id)) continue;
                const distM = distanceMeters(Number(m1.y), Number(m1.x), Number(m2.y), Number(m2.x));
                if (distM < 40.0) {
                    cluster.push(m2);
                    visited.add(m2.id);
                }
            }

            if (cluster.length > 1) {
                let sumLat = 0, sumLng = 0;
                cluster.forEach(m => { sumLat += Number(m.y); sumLng += Number(m.x); });
                layoutCluster(cluster, sumLat / cluster.length, sumLng / cluster.length, false);
            } else {
                adjustedCoordinates[m1.id] = [Number(m1.y), Number(m1.x)];
            }
        }

        return adjustedCoordinates;
    }

    function getSafeEmoji(m) {
        if (!m) return '📱';
        const raw = (m.avatarEmoji || '').trim();
        if (raw && !raw.includes('ð') && !raw.includes('Ÿ') && !raw.includes('\ufffd') && !/^[ðŸ‘¨‘§¨\s\?]+$/.test(raw)) {
            return raw;
        }
        const clean = ((m.name || '') + ' ' + (m.id || '')).toLowerCase();
        if (clean.includes('eloise') || clean.includes('eloisa')) return '👧';
        if (clean.includes('annette') || clean.includes('mama') || clean.includes('wife')) return '👩';
        if (clean.includes('isabel')) return '🐼';
        if (clean.includes('louis') || clean.includes('dad') || clean.includes('father')) return '📱';
        return '📱';
    }

    // Audio Chime Synthesizer for Browser
    function playDashboardRingChime() {
        try {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            if (!AudioContext) return;
            const ctx = new AudioContext();
            const now = ctx.currentTime;

            // Two-tone cyber sonar chime
            const osc1 = ctx.createOscillator();
            const gain1 = ctx.createGain();
            osc1.type = 'sine';
            osc1.frequency.setValueAtTime(880, now);
            osc1.frequency.exponentialRampToValueAtTime(1760, now + 0.15);
            gain1.gain.setValueAtTime(0.35, now);
            gain1.gain.exponentialRampToValueAtTime(0.01, now + 0.3);
            osc1.connect(gain1);
            gain1.connect(ctx.destination);
            osc1.start(now);
            osc1.stop(now + 0.3);

            const osc2 = ctx.createOscillator();
            const gain2 = ctx.createGain();
            osc2.type = 'triangle';
            osc2.frequency.setValueAtTime(1320, now + 0.18);
            osc2.frequency.exponentialRampToValueAtTime(2640, now + 0.35);
            gain2.gain.setValueAtTime(0.35, now + 0.18);
            gain2.gain.exponentialRampToValueAtTime(0.01, now + 0.5);
            osc2.connect(gain2);
            gain2.connect(ctx.destination);
            osc2.start(now + 0.18);
            osc2.stop(now + 0.5);
        } catch (_) {}
    }

    const KNOWN_PHONES = {
        'dad': '+447802436159',
        'louis': '+447802436159',
        'annette': '+447803171262',
        'mama': '+447803171262',
        'isabel': '+447760477416',
        'eloise': ''
    };

    function resolvePhoneNumber(member) {
        if (!member) return '';
        if (member.phone && member.phone.trim()) return member.phone.trim();
        const clean = ((member.name || '') + ' ' + (member.id || '')).toLowerCase();
        for (const [k, p] of Object.entries(KNOWN_PHONES)) {
            if (clean.includes(k) && p) return p;
        }
        return '';
    }

    async function toggleRingPhone(memberId) {
        const member = state.members.find(m => m.id === memberId) || state.members.find(m => m.name.toLowerCase() === memberId.toLowerCase());
        const isAlarm = member && member.statusText === '🚨 ALARM';
        const targetName = member ? member.name : memberId;

        try {
            if (isAlarm) {
                await apiRequest(`/api/admin/members/${encodeURIComponent(memberId)}/stop-ring`, { method: 'POST' });
                showToast(`🔕 Stopped ringing ${targetName}'s phone`, 'info');
                if (state.ringingTimers[memberId]) {
                    clearInterval(state.ringingTimers[memberId]);
                    delete state.ringingTimers[memberId];
                }
            } else {
                playDashboardRingChime();
                await apiRequest(`/api/admin/members/${encodeURIComponent(memberId)}/ring`, {
                    method: 'POST',
                    body: JSON.stringify({ durationMs: 20000 })
                });
                showToast(`🚨 Ringing ${targetName}'s phone at MAX volume!`, 'error');
                startRingCountdown(memberId, 20);
            }
            await loadCircles(false);
            if (member && !el.memberControlModal.classList.contains('hidden') && el.ctrlMemberId.value === member.id) {
                const refreshed = state.members.find(m => m.id === member.id);
                if (refreshed) openMemberControlModal(refreshed);
            }
        } catch (err) {
            showToast(`Failed to ring phone: ${err.message}`, 'error');
        }
    }

    function startRingCountdown(memberId, seconds) {
        if (state.ringingTimers[memberId]) {
            clearInterval(state.ringingTimers[memberId]);
        }
        let remaining = seconds;
        state.ringingTimers[memberId] = setInterval(() => {
            remaining--;
            if (remaining <= 0) {
                clearInterval(state.ringingTimers[memberId]);
                delete state.ringingTimers[memberId];
                loadCircles(false);
            } else {
                renderCurrentCircle();
            }
        }, 1000);
    }

    function updateMapMarkers(members, circle) {
        if (!state.map) return;

        const currentCircle = circle || getCurrentCircle();
        const coordsMap = computeClusterLayout(members, currentCircle);
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
            const pos = coordsMap[m.id];
            if (!pos) return;
            const [lat, lng] = pos;

            const isAlarm = m.statusText === '🚨 ALARM';
            const color = m.avatarColorHex || '#00ff88';
            const emoji = getSafeEmoji(m);

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

            const popupContent = `
                <div class="map-popup-card">
                    <div class="map-popup-header">
                        <span class="map-popup-avatar">${emoji}</span>
                        <div>
                            <div class="map-popup-name">${m.name}</div>
                            <div class="text-xs text-dim">${m.statusText || 'Active'}</div>
                        </div>
                    </div>
                    <div class="map-popup-stats">
                        <span>🔋 ${m.batteryPercentage}% ${m.isCharging ? '⚡' : ''}</span>
                        <span>${m.speedMph > 2 ? m.speedMph + ' mph' : 'Stationary'}</span>
                    </div>
                    <div class="map-popup-actions">
                        <button class="btn btn-xs ${isAlarm ? 'btn-danger' : 'btn-cyan'}" onclick="window.__kinTrackerRing('${m.id}')">
                            ${isAlarm ? '🔕 Silence' : '🔔 Ring Phone'}
                        </button>
                        <button class="btn btn-xs btn-outline" onclick="window.__kinTrackerOpenMember('${m.id}')">
                            ⚙️ Details
                        </button>
                    </div>
                </div>
            `;

            if (state.markers[m.id]) {
                state.markers[m.id].setLatLng([lat, lng]);
                state.markers[m.id].setIcon(icon);
                if (state.markers[m.id].getPopup()) {
                    state.markers[m.id].getPopup().setContent(popupContent);
                }
            } else {
                const marker = L.marker([lat, lng], { icon }).addTo(state.map);
                marker.bindPopup(popupContent, { offset: [0, -35] });
                state.markers[m.id] = marker;
            }
        });
    }

    // Expose global bridge helpers for Leaflet inline popups
    window.__kinTrackerRing = function(memberId) {
        toggleRingPhone(memberId);
    };

    window.__kinTrackerOpenMember = function(memberId) {
        const m = state.members.find(item => item.id === memberId);
        if (m) openMemberControlModal(m);
    };

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
            state.map.fitBounds(bounds, { padding: [50, 50], maxZoom: 19 });
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
            const isRinging = isAlarm;

            card.innerHTML = `
                <div class="member-card-content">
                    <div class="member-main">
                        <div class="member-avatar" style="border-color: ${isAlarm ? '#ff3366' : (m.avatarColorHex || '#00ff88')}">
                            ${getSafeEmoji(m)}
                        </div>
                        <div class="member-name-group">
                            <div class="member-name">${m.name}</div>
                            <div class="member-status-line">
                                <span class="member-badge ${badgeClass}">${m.statusText || 'Active'}</span>
                                ${m.speedMph > 2 ? `<span>${m.speedMph} mph</span>` : ''}
                            </div>
                        </div>
                    </div>
                    <div class="member-actions-col">
                        <button class="btn-ring-phone ${isRinging ? 'ringing' : ''}" title="Ring ${m.name}'s phone loudly">
                            <span>${isRinging ? '🚨' : '🔔'}</span>
                            <span>${isRinging ? 'Ringing...' : 'Ring Phone'}</span>
                        </button>
                        <div class="member-telemetry-col">
                            <div class="battery-pill ${m.isCharging ? 'charging' : (m.batteryPercentage < 20 ? 'low' : '')}">
                                ${m.isCharging ? '⚡' : '🔋'} ${m.batteryPercentage}%
                            </div>
                            <div class="member-last-active">${relativeTime}</div>
                        </div>
                    </div>
                </div>
            `;

            // Ring Button Click
            const ringBtn = card.querySelector('.btn-ring-phone');
            if (ringBtn) {
                ringBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    toggleRingPhone(m.id);
                });
            }

            // Entire Card Click -> Opens Modal
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

    // 7. GEOFENCE CONFIGURATION & PLACES MANAGER
    function openCircleConfigModal() {
        const circle = getCurrentCircle();
        if (!circle) return;

        el.cfgCircleName.value = circle.name || 'Family Circle';
        if (el.cfgCircleEmoji) el.cfgCircleEmoji.value = circle.iconEmoji || '👑';
        el.cfgInviteCode.value = circle.inviteCode || circle.legacyPin || '';

        // Home Custom Names & Icons
        if (el.cfgHomeName) el.cfgHomeName.value = circle.homeName || 'Home';
        if (el.cfgHomeEmoji) el.cfgHomeEmoji.value = circle.homeEmoji || '🏠';
        el.cfgHomeLat.value = circle.homeLat || '';
        el.cfgHomeLng.value = circle.homeLng || '';
        el.cfgHomeRadius.value = circle.homeRadiusMeters || 140;
        el.cfgHomeCalibrated.checked = Boolean(circle.isHomeCalibrated);

        // Work Custom Names & Icons (e.g. Annette's Work)
        if (el.cfgWorkName) el.cfgWorkName.value = circle.workName || 'Work';
        if (el.cfgWorkEmoji) el.cfgWorkEmoji.value = circle.workEmoji || '🏢';
        el.cfgWorkLat.value = circle.workLat || '';
        el.cfgWorkLng.value = circle.workLng || '';
        el.cfgWorkRadius.value = circle.workRadiusMeters || 75;
        el.cfgWorkCalibrated.checked = Boolean(circle.isWorkCalibrated);

        // Render additional custom places (Eloisa's School, Isabel's Work, etc.)
        renderCustomPlacesList(circle.safeZones || [], circle.id);

        el.circleConfigModal.classList.remove('hidden');
    }

    function renderCustomPlacesList(safeZones, circleId) {
        if (!el.customSafeZonesList) return;
        el.customSafeZonesList.innerHTML = '';

        if (!safeZones || safeZones.length === 0) {
            el.customSafeZonesList.innerHTML = '<div class="text-dim text-xs">No additional places configured. Click "+ Add Place" above to add schools, workplaces, or family spots.</div>';
            return;
        }

        safeZones.forEach(zone => {
            const card = document.createElement('div');
            card.className = 'place-item-card';
            card.innerHTML = `
                <div class="place-item-left">
                    <div class="place-icon-badge" style="border-color: ${zone.colorHex || '#A855F7'}">${zone.iconName || '🏫'}</div>
                    <div>
                        <div class="place-name">${zone.name}</div>
                        <div class="place-meta">${Number(zone.latitude).toFixed(5)}, ${Number(zone.longitude).toFixed(5)} &bull; ${zone.radiusMeters || 100}m radius</div>
                    </div>
                </div>
                <div class="place-actions">
                    <button type="button" class="btn btn-xs btn-outline btn-locate-place" title="Locate on map">🗺️</button>
                    <button type="button" class="btn btn-xs btn-secondary btn-edit-place">✏️ Edit</button>
                    <button type="button" class="btn btn-xs btn-outline-danger btn-delete-place">🗑️</button>
                </div>
            `;

            card.querySelector('.btn-locate-place').addEventListener('click', (e) => {
                e.stopPropagation();
                el.circleConfigModal.classList.add('hidden');
                if (state.map) {
                    state.map.flyTo([Number(zone.latitude), Number(zone.longitude)], 16);
                }
            });

            card.querySelector('.btn-edit-place').addEventListener('click', (e) => {
                e.stopPropagation();
                openCustomPlaceModal(zone);
            });

            card.querySelector('.btn-delete-place').addEventListener('click', async (e) => {
                e.stopPropagation();
                if (!confirm(`Delete place "${zone.name}"?`)) return;
                try {
                    await apiRequest(`/api/admin/circles/${circleId}/safe-zones/${zone.id}`, { method: 'DELETE' });
                    showToast(`Deleted place "${zone.name}"`, 'success');
                    await loadCircles(false);
                    const updated = getCurrentCircle();
                    if (updated) renderCustomPlacesList(updated.safeZones || [], circleId);
                } catch (err) {
                    showToast(`Failed to delete place: ${err.message}`, 'error');
                }
            });

            el.customSafeZonesList.appendChild(card);
        });
    }

    function openCustomPlaceModal(place = null) {
        if (!el.customPlaceModal) return;
        if (place) {
            el.customPlaceModalTitle.innerText = `✏️ Edit Place: ${place.name}`;
            el.placeId.value = place.id;
            el.placeNameInput.value = place.name;
            el.placeEmojiInput.value = place.iconName || '🏫';
            el.placeLatInput.value = place.latitude;
            el.placeLngInput.value = place.longitude;
            el.placeRadiusInput.value = place.radiusMeters || 100;
            el.placeColorInput.value = place.colorHex || '#A855F7';
        } else {
            el.customPlaceModalTitle.innerText = `📍 Add Family Place / School / Work`;
            el.placeId.value = '';
            el.placeNameInput.value = '';
            el.placeEmojiInput.value = '🏫';
            const currentCircle = getCurrentCircle();
            el.placeLatInput.value = currentCircle?.homeLat || '';
            el.placeLngInput.value = currentCircle?.homeLng || '';
            el.placeRadiusInput.value = 100;
            el.placeColorInput.value = '#A855F7';
        }
        el.customPlaceModal.classList.remove('hidden');
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
        el.ctrlMemberEmoji.value = getSafeEmoji(member);
        el.ctrlMemberColor.value = member.avatarColorHex || '#00ff88';
        el.ctrlMemberLat.value = member.y || '';
        el.ctrlMemberLng.value = member.x || '';
        el.ctrlMemberBattery.value = member.batteryPercentage || 100;
        el.ctrlMemberSpeed.value = member.speedMph || 0;
        el.ctrlMemberStatus.value = member.statusText || 'Active';
        el.ctrlMemberCharging.checked = Boolean(member.isCharging);
        el.ctrlMemberPaused.checked = Boolean(member.isLocationPaused);

        // Header Avatar & Info
        if (el.ctrlHeaderAvatar) {
            el.ctrlHeaderAvatar.innerText = getSafeEmoji(member);
            el.ctrlHeaderAvatar.style.borderColor = member.avatarColorHex || '#00ff88';
        }
        if (el.memberModalTitle) {
            el.memberModalTitle.innerText = `${member.name}'s Device`;
        }
        if (el.memberModalSubtitle) {
            const rel = getRelativeTime(member.lastActive);
            el.memberModalSubtitle.innerText = `Battery: ${member.batteryPercentage}% ${member.isCharging ? '⚡' : ''} • Last active: ${rel}`;
        }

        // Phone call link
        const phone = resolvePhoneNumber(member);
        if (el.ctrlCallPhoneLink) {
            if (phone) {
                el.ctrlCallPhoneLink.href = `tel:${phone}`;
                el.ctrlCallPhoneLink.innerText = `📞 Call ${phone}`;
                el.ctrlCallPhoneLink.classList.remove('hidden');
            } else {
                el.ctrlCallPhoneLink.classList.add('hidden');
            }
        }

        // Ringing Hub State
        const isAlarm = member.statusText === '🚨 ALARM';
        const ringBanner = document.querySelector('.ring-action-banner');
        if (ringBanner) {
            if (isAlarm) {
                ringBanner.classList.add('ringing-active');
            } else {
                ringBanner.classList.remove('ringing-active');
            }
        }

        if (el.ringPulseIcon) el.ringPulseIcon.innerText = isAlarm ? '🚨' : '🔔';
        if (el.ringActionTitle) el.ringActionTitle.innerText = isAlarm ? `🚨 Ringing ${member.name}'s Phone...` : `Ring ${member.name}'s Phone Loudly`;
        if (el.ringActionSubtitle) el.ringActionSubtitle.innerText = isAlarm ? 'Phone is sounding maximum volume sovereign siren' : 'Sounds a high-decibel siren on their phone (even if on silent)';
        if (el.ringBtnIcon) el.ringBtnIcon.innerText = isAlarm ? '🔕' : '🔔';
        if (el.ringBtnText) el.ringBtnText.innerText = isAlarm ? 'Silence Siren' : 'Ring Phone Now';
        if (el.ctrlPrimaryRingBtn) {
            el.ctrlPrimaryRingBtn.className = isAlarm ? 'btn btn-danger btn-glow btn-ring-main' : 'btn btn-primary btn-glow btn-ring-main';
        }

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
                state.map.flyTo([circle.homeLat, circle.homeLng], 18);
            }
        });
        el.focusWorkBtn.addEventListener('click', () => {
            const circle = getCurrentCircle();
            if (circle && circle.workLat && circle.workLng && state.map) {
                state.map.flyTo([circle.workLat, circle.workLng], 18);
            }
        });

        // Map Click Calibrate Modes
        el.mapClickCalibrateHomeBtn.addEventListener('click', () => enterMapCalibrateMode('home'));
        el.mapClickCalibrateWorkBtn.addEventListener('click', () => enterMapCalibrateMode('work'));
        el.cancelMapCalibrateBtn.addEventListener('click', exitMapCalibrateMode);

        // Circle Config Modal
        // Circle Config Modal Form Submission
        el.openCircleConfigBtn.addEventListener('click', openCircleConfigModal);
        el.circleConfigForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const circle = getCurrentCircle();
            if (!circle) return;

            const updates = {
                name: el.cfgCircleName.value.trim(),
                iconEmoji: el.cfgCircleEmoji ? el.cfgCircleEmoji.value.trim() : '👑',
                homeName: el.cfgHomeName ? el.cfgHomeName.value.trim() : 'Home',
                homeEmoji: el.cfgHomeEmoji ? el.cfgHomeEmoji.value.trim() : '🏠',
                homeLat: Number(el.cfgHomeLat.value),
                homeLng: Number(el.cfgHomeLng.value),
                homeRadiusMeters: Number(el.cfgHomeRadius.value),
                isHomeCalibrated: el.cfgHomeCalibrated.checked,
                workName: el.cfgWorkName ? el.cfgWorkName.value.trim() : 'Work',
                workEmoji: el.cfgWorkEmoji ? el.cfgWorkEmoji.value.trim() : '🏢',
                workLat: Number(el.cfgWorkLat.value),
                workLng: Number(el.cfgWorkLng.value),
                workRadiusMeters: Number(el.cfgWorkRadius.value),
                isWorkCalibrated: el.cfgWorkCalibrated.checked
            };

            await saveGeofenceLocation(circle.id, updates);
            el.circleConfigModal.classList.add('hidden');
            showToast('Places & calibration settings updated successfully', 'success');
        });

        // Browser GPS & Map Picking for Home & Work in Config Modal
        if (el.cfgPickHomeOnMapBtn) {
            el.cfgPickHomeOnMapBtn.addEventListener('click', () => {
                el.circleConfigModal.classList.add('hidden');
                enterMapCalibrateMode('home');
            });
        }

        if (el.cfgPickWorkOnMapBtn) {
            el.cfgPickWorkOnMapBtn.addEventListener('click', () => {
                el.circleConfigModal.classList.add('hidden');
                enterMapCalibrateMode('work');
            });
        }

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

        // Custom Places (Schools, Workplaces, Family Spots)
        if (el.addNewPlaceBtn) {
            el.addNewPlaceBtn.addEventListener('click', () => openCustomPlaceModal(null));
        }

        if (el.placeSetCurrentGPS) {
            el.placeSetCurrentGPS.addEventListener('click', () => {
                if (!navigator.geolocation) return showToast('Geolocation not supported', 'error');
                navigator.geolocation.getCurrentPosition((pos) => {
                    el.placeLatInput.value = pos.coords.latitude.toFixed(6);
                    el.placeLngInput.value = pos.coords.longitude.toFixed(6);
                    showToast('Place set to browser GPS', 'info');
                }, (err) => showToast(err.message, 'error'));
            });
        }

        if (el.placePickOnMapBtn) {
            el.placePickOnMapBtn.addEventListener('click', () => {
                el.customPlaceModal.classList.add('hidden');
                enterMapCalibrateMode('place');
            });
        }

        if (el.customPlaceForm) {
            el.customPlaceForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                const circle = getCurrentCircle();
                if (!circle) return;

                const placeData = {
                    id: el.placeId.value.trim() || undefined,
                    name: el.placeNameInput.value.trim(),
                    iconName: el.placeEmojiInput.value.trim() || '🏫',
                    latitude: Number(el.placeLatInput.value),
                    longitude: Number(el.placeLngInput.value),
                    radiusMeters: Number(el.placeRadiusInput.value) || 100,
                    colorHex: el.placeColorInput.value || '#A855F7'
                };

                try {
                    await apiRequest(`/api/admin/circles/${circle.id}/safe-zones`, {
                        method: 'POST',
                        body: JSON.stringify(placeData)
                    });
                    el.customPlaceModal.classList.add('hidden');
                    showToast(`Saved place "${placeData.name}"`, 'success');
                    await loadCircles(false);
                    const updated = getCurrentCircle();
                    if (updated && !el.circleConfigModal.classList.contains('hidden')) {
                        renderCustomPlacesList(updated.safeZones || [], updated.id);
                    }
                } catch (err) {
                    showToast(`Failed to save place: ${err.message}`, 'error');
                }
            });
        }

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

        // Primary Modal Ring Button
        if (el.ctrlPrimaryRingBtn) {
            el.ctrlPrimaryRingBtn.addEventListener('click', async () => {
                const memberId = el.ctrlMemberId.value;
                if (!memberId) return;
                await toggleRingPhone(memberId);
            });
        }

        // Locate & Focus on Map
        if (el.ctrlLocateOnMapBtn) {
            el.ctrlLocateOnMapBtn.addEventListener('click', () => {
                const lat = Number(el.ctrlMemberLat.value);
                const lng = Number(el.ctrlMemberLng.value);
                if (lat && lng && state.map) {
                    el.memberControlModal.classList.add('hidden');
                    state.map.flyTo([lat, lng], 19, { animate: true, duration: 1.2 });
                    const memberId = el.ctrlMemberId.value;
                    if (state.markers[memberId]) {
                        state.markers[memberId].openPopup();
                    }
                }
            });
        }

        // Trigger / Silence Alarm
        el.ctrlTriggerAlarmBtn.addEventListener('click', async () => {
            const memberId = el.ctrlMemberId.value;
            try {
                const res = await apiRequest(`/api/admin/members/${encodeURIComponent(memberId)}/toggle-alarm`, { method: 'POST' });
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
