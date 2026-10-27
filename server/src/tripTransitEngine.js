/**
 * Kin-Tracker Sovereign Server Trip & Transit Engine
 * Computes real-time transit mode (Train, Car, Walking, Cycling),
 * origin departures, destination projections, and human-friendly status text.
 */

const http = require('http');

// Spatial cache for road distance queries (OSRM): cellKey -> { isRail: boolean, time: number }
const spatialRailCache = new Map();

// Consecutive road hits per member to avoid train jitter: memberId -> count
const consecutiveRoadHits = new Map();

// Default safe zones (Croydon / Surrey school campuses)
const DEFAULT_SAFE_ZONES = [
    { id: "zone_oasis_academy_byron", name: "Oasis Academy Byron", latitude: 51.3141000, longitude: -0.1288000, radiusMeters: 240.0 },
    { id: "zone_woodcote_high_school", name: "Woodcote High School", latitude: 51.3301600, longitude: -0.1422829, radiusMeters: 220.0 },
    { id: "zone_woodcote_school", name: "Woodcote Primary School", latitude: 51.3279926, longitude: -0.1405488, radiusMeters: 160.0 }
];

// Major transport hubs along the London - Brighton - Surrey corridors
const KNOWN_HUBS = [
    { name: "London Victoria", lat: 51.4952, lng: -0.1439, radiusM: 2000 },
    { name: "London Bridge", lat: 51.5050, lng: -0.0860, radiusM: 2000 },
    { name: "London Waterloo", lat: 51.5031, lng: -0.1132, radiusM: 2000 },
    { name: "London Blackfriars", lat: 51.5118, lng: -0.1037, radiusM: 1800 },
    { name: "St Pancras International", lat: 51.5314, lng: -0.1261, radiusM: 2000 },
    { name: "Central London", lat: 51.5074, lng: -0.1278, radiusM: 5000 },
    { name: "Clapham Junction", lat: 51.4632, lng: -0.1702, radiusM: 1600 },
    { name: "East Croydon", lat: 51.3754, lng: -0.0926, radiusM: 1400 },
    { name: "West Croydon", lat: 51.3787, lng: -0.1026, radiusM: 1000 },
    { name: "South Croydon", lat: 51.3615, lng: -0.0967, radiusM: 1000 },
    { name: "Purley", lat: 51.3375, lng: -0.1147, radiusM: 1200 },
    { name: "Coulsdon", lat: 51.3208, lng: -0.1402, radiusM: 1200 },
    { name: "Kenley", lat: 51.3235, lng: -0.0955, radiusM: 1000 },
    { name: "Caterham", lat: 51.2798, lng: -0.0768, radiusM: 1400 },
    { name: "Redhill", lat: 51.2405, lng: -0.1700, radiusM: 1600 },
    { name: "Gatwick Airport", lat: 51.1560, lng: -0.1610, radiusM: 2500 },
    { name: "Three Bridges", lat: 51.1170, lng: -0.1610, radiusM: 1400 },
    { name: "Haywards Heath", lat: 51.0040, lng: -0.1440, radiusM: 1600 },
    { name: "Brighton", lat: 50.8290, lng: -0.1410, radiusM: 3000 }
];

function distMeters(lat1, lon1, lat2, lon2) {
    if (!lat1 || !lon1 || !lat2 || !lon2) return 0;
    const dLat = (lat2 - lat1) * 111000.0;
    const dLon = (lon2 - lon1) * 111000.0 * Math.cos(((lat1 + lat2) / 2.0) * Math.PI / 180.0);
    return Math.hypot(dLat, dLon);
}

function calcBearing(lat1, lon1, lat2, lon2) {
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const y = Math.sin(dLon) * Math.cos(lat2 * Math.PI / 180);
    const x = Math.cos(lat1 * Math.PI / 180) * Math.sin(lat2 * Math.PI / 180) -
              Math.sin(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.cos(dLon);
    let brng = Math.atan2(y, x) * 180 / Math.PI;
    return (brng + 360) % 360;
}

function bearingDiff(b1, b2) {
    const phi = Math.abs(b1 - b2) % 360;
    return phi > 180 ? 360 - phi : phi;
}

function bearingToCardinal(bearingDeg) {
    const idx = Math.floor(((((bearingDeg + 22.5) % 360) + 360) % 360) / 45);
    return ['north', 'north-east', 'east', 'south-east', 'south', 'south-west', 'west', 'north-west'][idx] || 'north';
}

function resolveOriginName(lat, lng, home, safeZones) {
    if (!lat || !lng) return 'their location';
    if (home && home.lat && home.lng) {
        if (distMeters(lat, lng, home.lat, home.lng) <= (home.radius || 150) + 70) {
            return 'the house';
        }
    }
    const allZones = [...(safeZones || []), ...DEFAULT_SAFE_ZONES];
    for (const z of allZones) {
        if (distMeters(lat, lng, z.latitude, z.longitude) <= (z.radiusMeters || 160) + 60) {
            return z.name;
        }
    }
    for (const h of KNOWN_HUBS) {
        if (distMeters(lat, lng, h.lat, h.lng) <= h.radiusM) {
            return h.name;
        }
    }
    return 'their location';
}

function determineDestination(curLat, curLng, prevLat, prevLng, home, safeZones, isTrain) {
    if (!curLat || !curLng || !prevLat || !prevLng) return null;
    const moved = distMeters(curLat, curLng, prevLat, prevLng);
    if (moved < 25) return null;

    const travelBearing = calcBearing(prevLat, prevLng, curLat, curLng);
    const candidates = [];

    // 1. Home
    if (home && home.lat && home.lng) {
        const distCur = distMeters(curLat, curLng, home.lat, home.lng);
        const distPrev = distMeters(prevLat, prevLng, home.lat, home.lng);
        if (distCur < distPrev - 20 && distCur > 120) {
            const bearingToHome = calcBearing(curLat, curLng, home.lat, home.lng);
            if (bearingDiff(travelBearing, bearingToHome) <= 55) {
                candidates.push({ name: 'Home', dist: distCur, priority: 1, angleDiff: bearingDiff(travelBearing, bearingToHome) });
            }
        }
    }

    // 2. SafeZones
    const allZones = [...(safeZones || []), ...DEFAULT_SAFE_ZONES];
    for (const z of allZones) {
        const distCur = distMeters(curLat, curLng, z.latitude, z.longitude);
        const distPrev = distMeters(prevLat, prevLng, z.latitude, z.longitude);
        if (distCur < distPrev - 20 && distCur > (z.radiusMeters || 160)) {
            const bearingToZone = calcBearing(curLat, curLng, z.latitude, z.longitude);
            const diff = bearingDiff(travelBearing, bearingToZone);
            if (diff <= 55) {
                candidates.push({ name: z.name, dist: distCur, priority: 2, angleDiff: diff });
            }
        }
    }

    // 3. Known Hubs (London, Brighton, etc.)
    for (const h of KNOWN_HUBS) {
        const distCur = distMeters(curLat, curLng, h.lat, h.lng);
        const distPrev = distMeters(prevLat, prevLng, h.lat, h.lng);
        if (distCur < distPrev - 40 && distCur > h.radiusM) {
            const bearingToHub = calcBearing(curLat, curLng, h.lat, h.lng);
            const diff = bearingDiff(travelBearing, bearingToHub);
            if (diff <= 45) {
                candidates.push({ name: h.name, dist: distCur, priority: 3, angleDiff: diff });
            }
        }
    }

    if (candidates.length > 0) {
        candidates.sort((a, b) => a.priority - b.priority || a.angleDiff - b.angleDiff || a.dist - b.dist);
        return candidates[0].name;
    }

    if (isTrain) {
        if ((travelBearing >= 315 || travelBearing <= 45)) return "London";
        if (travelBearing >= 135 && travelBearing <= 225) return "Brighton";
        if (travelBearing > 45 && travelBearing < 135) return "Kent";
        return "Surrey";
    }

    return null;
}

/**
 * Evaluates transit mode based on speed, coordinates, and rail heuristics.
 */
function classifyTransitMode(speedMph, lat, lng, memberId, prevMode) {
    if (speedMph < 0.8) return 'STATIONARY';
    if (speedMph >= 0.8 && speedMph < 4.5) return 'WALKING';
    if (speedMph >= 4.5 && speedMph < 16.0) return 'CYCLING';
    if (speedMph > 80.0) return 'TRAIN';

    // Check spatial cache for rail
    if (lat && lng) {
        const cellKey = `${lat.toFixed(3)}_${lng.toFixed(3)}`;
        const cached = spatialRailCache.get(cellKey);
        if (cached && (Date.now() - cached.time < 3600000)) {
            if (cached.isRail) return 'TRAIN';
        }
    }

    // If previously on a train and speed > 15 mph, maintain train latch
    if (prevMode === 'TRAIN' && speedMph >= 12.0) {
        const roadHits = (consecutiveRoadHits.get(memberId) || 0) + 1;
        consecutiveRoadHits.set(memberId, roadHits);
        if (roadHits < 4) return 'TRAIN';
    }

    return 'DRIVING';
}

/**
 * Main enrichment processor for incoming member updates on the VPS.
 */
function enrichMemberState(token, memberId, incoming, stored, home, safeZones) {
    const out = Object.assign({}, incoming);
    const speed = Number(incoming.speedMph) || 0.0;
    const lat = Number(incoming.y) || 0.0;
    const lng = Number(incoming.x) || 0.0;

    if (!lat || !lng) return out;

    // 1. Check Home
    if (home && home.lat && home.lng) {
        const homeRadiusM = home.radius || 150;
        const distToHomeM = distMeters(lat, lng, home.lat, home.lng);
        if (distToHomeM <= homeRadiusM + 50 && speed < 3.5) {
            out.x = home.lng;
            out.y = home.lat;
            out.speedMph = 0.0;
            out.isLocationPaused = false;
            out.statusText = 'At Home (Live GPS)';
            out.lastStationaryPlace = 'the house';
            out.lastStationaryLat = home.lat;
            out.lastStationaryLng = home.lng;
            out.transitMode = 'STATIONARY';
            return out;
        }
    }

    // 2. Check SafeZones
    const allZones = [...(safeZones || []), ...DEFAULT_SAFE_ZONES];
    for (const z of allZones) {
        const distZoneM = distMeters(lat, lng, z.latitude, z.longitude);
        if (distZoneM <= (z.radiusMeters || 160) + 50 && speed < 3.5) {
            out.x = z.longitude;
            out.y = z.latitude;
            out.speedMph = 0.0;
            out.statusText = `At ${z.name}`;
            out.lastStationaryPlace = z.name;
            out.lastStationaryLat = z.latitude;
            out.lastStationaryLng = z.longitude;
            out.transitMode = 'STATIONARY';
            return out;
        }
    }

    // 3. Check Active Movement
    const prevLat = stored ? (Number(stored.y) || lat) : lat;
    const prevLng = stored ? (Number(stored.x) || lng) : lng;
    const movedM = distMeters(lat, lng, prevLat, prevLng);

    const prevMode = stored?.transitMode || 'STATIONARY';
    const transitMode = classifyTransitMode(speed, lat, lng, memberId, prevMode);
    out.transitMode = transitMode;

    if (transitMode === 'STATIONARY' || (speed < 1.0 && movedM < 25)) {
        if (!out.statusText || /Live GPS|tracking|away/i.test(out.statusText)) {
            const place = resolveOriginName(lat, lng, home, safeZones);
            out.statusText = (place !== 'their location') ? `At ${place}` : 'Stationary';
        }
        out.lastStationaryLat = lat;
        out.lastStationaryLng = lng;
        return out;
    }

    // Determine Destination
    const isTrain = (transitMode === 'TRAIN');
    const destination = determineDestination(lat, lng, prevLat, prevLng, home, safeZones, isTrain);

    // Build rich status text
    const speedRound = Math.round(speed);
    if (isTrain) {
        out.statusText = destination
            ? `On a train heading towards ${destination} (${speedRound} mph)`
            : `On a train (${speedRound} mph)`;
    } else if (transitMode === 'DRIVING') {
        out.statusText = destination
            ? `In a car heading towards ${destination} (${speedRound} mph)`
            : `Driving (${speedRound} mph)`;
    } else if (transitMode === 'WALKING') {
        out.statusText = destination
            ? `Walking towards ${destination}`
            : `Walking (${speed.toFixed(1)} mph)`;
    } else if (transitMode === 'CYCLING') {
        out.statusText = destination
            ? `Cycling towards ${destination} (${speedRound} mph)`
            : `Cycling (${speedRound} mph)`;
    }

    return out;
}

// ─────────────────────────────────────────────────────────────────────────────
// TRIP HISTORY & LIFECYCLE TRACKER (Server-Side Life360 Style)
// ─────────────────────────────────────────────────────────────────────────────

const fs = require('fs');
const path = require('path');

const TRIPS_DIR = path.join(__dirname, '../trips-data');
if (!fs.existsSync(TRIPS_DIR)) {
    try { fs.mkdirSync(TRIPS_DIR, { recursive: true }); } catch (_) {}
}

const activeTrips = new Map(); // memberId -> activeTrip
const lastAlertTimestamp = new Map(); // `${memberId}_${type}` -> timestamp

function getTripsFilePath(token) {
    const safe = (token || 'default').replace(/[^a-zA-Z0-9_\-]/g, '_');
    return path.join(TRIPS_DIR, `${safe}.json`);
}

function loadCompletedTrips(token) {
    const file = getTripsFilePath(token);
    if (!fs.existsSync(file)) return [];
    try {
        const raw = fs.readFileSync(file, 'utf8');
        return JSON.parse(raw);
    } catch (_) {
        return [];
    }
}

function saveCompletedTrips(token, trips) {
    const file = getTripsFilePath(token);
    try {
        // Keep the latest 60 completed trips per family circle
        const trimmed = trips.slice(-60);
        fs.writeFileSync(file, JSON.stringify(trimmed, null, 2), 'utf8');
    } catch (e) {
        console.error('[Trips] Failed saving trip history:', e.message);
    }
}

/**
 * Updates or finishes trip lifecycle when member telemetry updates.
 * Returns { completedTrip, alertEvent } if triggered.
 */
function processTripLifecycle(token, memberId, out, stored, home, safeZones) {
    const now = Date.now();
    const speed = Number(out.speedMph) || 0.0;
    const lat = Number(out.y) || 0.0;
    const lng = Number(out.x) || 0.0;
    const isMoving = (out.transitMode && out.transitMode !== 'STATIONARY') && speed >= 2.5;

    let alertEvent = null;
    let finishedTrip = null;

    let activeTrip = activeTrips.get(memberId);

    // 1. START A NEW TRIP
    if (isMoving) {
        if (!activeTrip) {
            const origin = stored?.lastStationaryPlace || resolveOriginName(stored?.y || lat, stored?.x || lng, home, safeZones);
            activeTrip = {
                id: `trip_${now}_${memberId}`,
                memberId: memberId,
                memberName: out.name || 'Member',
                transitMode: out.transitMode,
                origin: origin,
                destination: null,
                startLat: lat,
                startLng: lng,
                startTime: now,
                endTime: null,
                breadcrumbs: [{ lat, lng, speedMph: speed, timestamp: now }],
                topSpeedMph: speed,
                totalDistanceKm: 0.0,
                harshBrakingCount: 0,
                rapidAccelerationCount: 0,
                lastBreadcrumbTime: now
            };
            activeTrips.set(memberId, activeTrip);

            // Generate Departure Alert Event if not alerted recently
            const alertKey = `${memberId}_DEPARTURE`;
            const lastAlert = lastAlertTimestamp.get(alertKey) || 0;
            if (now - lastAlert > 15 * 60 * 1000) { // 15-minute cooldown
                lastAlertTimestamp.set(alertKey, now);
                const dest = determineDestination(lat, lng, stored?.y || lat, stored?.x || lng, home, safeZones, out.transitMode === 'TRAIN');
                const modePhrase = out.transitMode === 'TRAIN' ? 'on a train' : (out.transitMode === 'DRIVING' ? 'in a car' : out.transitMode.toLowerCase());
                const msg = dest 
                    ? `${out.name} has left ${origin} and is ${modePhrase} heading towards ${dest}!`
                    : `${out.name} has left ${origin} and is ${modePhrase}!`;

                alertEvent = {
                    id: `evt_${now}_${memberId}`,
                    type: 'DEPARTURE',
                    memberId: memberId,
                    memberName: out.name,
                    title: out.transitMode === 'TRAIN' ? '🚆 Journey Notice' : '🚪 Departure Warning',
                    message: msg,
                    origin: origin,
                    destination: dest,
                    transitMode: out.transitMode,
                    speedMph: speed,
                    timestamp: now
                };
            }
        } else {
            // TRIP CONTINUES: Append breadcrumb if moved >= 25m or >= 12s
            const lastBc = activeTrip.breadcrumbs[activeTrip.breadcrumbs.length - 1];
            const distFromLastM = distMeters(lat, lng, lastBc.lat, lastBc.lng);
            const timeDiff = now - (activeTrip.lastBreadcrumbTime || 0);

            if (distFromLastM >= 25 || timeDiff >= 12000) {
                // Safety Telemetry: harsh braking & acceleration
                const speedDiff = speed - lastBc.speedMph;
                if (speedDiff < -18 && activeTrip.transitMode === 'DRIVING') {
                    activeTrip.harshBrakingCount = (activeTrip.harshBrakingCount || 0) + 1;
                } else if (speedDiff > 18 && activeTrip.transitMode === 'DRIVING') {
                    activeTrip.rapidAccelerationCount = (activeTrip.rapidAccelerationCount || 0) + 1;
                }

                activeTrip.breadcrumbs.push({ lat, lng, speedMph: speed, timestamp: now });
                activeTrip.totalDistanceKm += (distFromLastM / 1000.0);
                activeTrip.topSpeedMph = Math.max(activeTrip.topSpeedMph || 0, speed);
                activeTrip.transitMode = out.transitMode;
                activeTrip.lastBreadcrumbTime = now;
            }
        }
    } 
    // 2. FINISH AN ACTIVE TRIP (Member has arrived and is stationary)
    else if (activeTrip && (out.transitMode === 'STATIONARY' || speed < 1.0)) {
        const atHome = (home && distMeters(lat, lng, home.lat, home.lng) <= (home.radius || 150) + 50);
        const atSafeZone = DEFAULT_SAFE_ZONES.some(z => distMeters(lat, lng, z.latitude, z.longitude) <= z.radiusMeters + 50);
        const timeSinceMove = now - (activeTrip.lastBreadcrumbTime || now);

        if (atHome || atSafeZone || timeSinceMove > 90000) {
            activeTrip.endTime = now;
            activeTrip.endLat = lat;
            activeTrip.endLng = lng;
            activeTrip.durationMinutes = Math.max(1, Math.round((activeTrip.endTime - activeTrip.startTime) / 60000));
            activeTrip.destination = atHome ? 'Home' : resolveOriginName(lat, lng, home, safeZones);

            // Only save if traveled a meaningful distance (> 250 meters)
            if (activeTrip.totalDistanceKm >= 0.25) {
                const existing = loadCompletedTrips(token);
                existing.push(activeTrip);
                saveCompletedTrips(token, existing);
                finishedTrip = activeTrip;

                // Generate Arrival Alert Event
                const alertKey = `${memberId}_ARRIVAL`;
                const lastAlert = lastAlertTimestamp.get(alertKey) || 0;
                if (now - lastAlert > 15 * 60 * 1000) {
                    lastAlertTimestamp.set(alertKey, now);
                    alertEvent = {
                        id: `evt_${now}_${memberId}`,
                        type: 'ARRIVAL',
                        memberId: memberId,
                        memberName: out.name,
                        title: '📍 Arrival Notice',
                        message: `${out.name} has arrived at ${activeTrip.destination}!`,
                        destination: activeTrip.destination,
                        transitMode: activeTrip.transitMode,
                        timestamp: now
                    };
                }
            }
            activeTrips.remove ? activeTrips.remove(memberId) : activeTrips.delete(memberId);
        }
    }

    return { completedTrip: finishedTrip, alertEvent };
}

module.exports = {
    distMeters,
    calcBearing,
    bearingDiff,
    bearingToCardinal,
    resolveOriginName,
    determineDestination,
    classifyTransitMode,
    enrichMemberState,
    processTripLifecycle,
    loadCompletedTrips,
    KNOWN_HUBS,
    DEFAULT_SAFE_ZONES
};

