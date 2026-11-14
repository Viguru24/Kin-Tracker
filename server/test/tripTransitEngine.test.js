const assert = require('assert');
const { describe, it } = require('node:test');
const {
    distMeters,
    calcBearing,
    resolveOriginName,
    determineDestination,
    classifyTransitMode,
    enrichMemberState,
    processTripLifecycle,
    reconcileCoTravelingCompanions,
    loadCompletedTrips
} = require('../src/tripTransitEngine');

describe('Server TripTransitEngine', () => {
    const home = { lat: 51.329480, lng: -0.119095, radius: 140 };
    const woodcoteSchool = {
        id: "zone_woodcote_high_school",
        name: "Woodcote High School",
        latitude: 51.3301600,
        longitude: -0.1422829,
        radiusMeters: 220.0
    };

    it('should format clean departure message when Eloise leaves her school', () => {
        const memberId = 'device_eloise_test';
        const startState = {
            id: memberId,
            name: 'Eloise',
            x: -0.1410,
            y: 51.3290,
            speedMph: 3.0,
            transitMode: 'WALKING'
        };
        const prevAtSchool = {
            id: memberId,
            name: 'Eloise',
            x: woodcoteSchool.longitude,
            y: woodcoteSchool.latitude,
            lastStationaryPlace: 'Woodcote High School',
            speedMph: 0.0,
            transitMode: 'STATIONARY'
        };
        const step1 = processTripLifecycle('test_token_eloise', memberId, startState, prevAtSchool, home, [woodcoteSchool]);
        assert.ok(step1.alertEvent);
        assert.strictEqual(step1.alertEvent.message, 'Eloise has just left her school!');
    });

    it('should detect when member is At Home and snap coordinates', () => {
        const incoming = {
            id: 'device_isabel',
            name: 'Isabel',
            x: -0.119090,
            y: 51.329485,
            speedMph: 0.0,
            statusText: 'Live GPS'
        };
        const result = enrichMemberState('token1', 'device_isabel', incoming, null, home, [woodcoteSchool]);
        assert.strictEqual(result.statusText, 'At Home (Live GPS)');
        assert.strictEqual(result.x, home.lng);
        assert.strictEqual(result.y, home.lat);
        assert.strictEqual(result.transitMode, 'STATIONARY');
    });

    it('should detect when member is At Woodcote High School', () => {
        const incoming = {
            id: 'device_isabel',
            name: 'Isabel',
            x: -0.142280,
            y: 51.330165,
            speedMph: 0.0,
            statusText: 'Live GPS'
        };
        const result = enrichMemberState('token1', 'device_isabel', incoming, null, home, [woodcoteSchool]);
        assert.strictEqual(result.statusText, 'At Woodcote High School');
        assert.strictEqual(result.x, woodcoteSchool.longitude);
        assert.strictEqual(result.y, woodcoteSchool.latitude);
    });

    it('should detect driving in a car heading towards Home', () => {
        const stored = {
            id: 'device_isabel',
            name: 'Isabel',
            x: -0.1400,
            y: 51.3300,
            speedMph: 20.0,
            transitMode: 'DRIVING'
        };
        const incoming = {
            id: 'device_isabel',
            name: 'Isabel',
            x: -0.1300, // moving east towards home (-0.119)
            y: 51.3298,
            speedMph: 28.0,
            statusText: 'Live GPS'
        };
        const result = enrichMemberState('token1', 'device_isabel', incoming, stored, home, [woodcoteSchool]);
        assert.ok(result.statusText.includes('In a car heading towards Home'));
        assert.ok(result.statusText.includes('28 mph'));
        assert.strictEqual(result.transitMode, 'DRIVING');
    });

    it('should detect driving in a car heading towards School', () => {
        const stored = {
            id: 'device_isabel',
            name: 'Isabel',
            x: -0.1220,
            y: 51.3296,
            speedMph: 15.0,
            transitMode: 'DRIVING'
        };
        const incoming = {
            id: 'device_isabel',
            name: 'Isabel',
            x: -0.1320, // moving west towards school (-0.142)
            y: 51.3299,
            speedMph: 26.0,
            statusText: 'Live GPS'
        };
        const result = enrichMemberState('token1', 'device_isabel', incoming, stored, home, [woodcoteSchool]);
        assert.ok(result.statusText.includes('In a car heading towards Woodcote High School'));
        assert.ok(result.statusText.includes('26 mph'));
    });

    it('should detect train traveling northward towards London', () => {
        const stored = {
            id: 'device_isabel',
            name: 'Isabel',
            x: -0.1410,
            y: 50.8400,
            speedMph: 85.0
        };
        const incoming = {
            id: 'device_isabel',
            name: 'Isabel',
            x: -0.1410,
            y: 50.8900, // moving north from Brighton
            speedMph: 88.0,
            statusText: 'Live GPS'
        };
        const result = enrichMemberState('token1', 'device_isabel', incoming, stored, home, [woodcoteSchool]);
        assert.strictEqual(result.transitMode, 'TRAIN');
        assert.ok(result.statusText.includes('On a train heading towards London') || result.statusText.includes('On a train heading towards Home'));
        assert.ok(result.statusText.includes('88 mph'));
    });

    it('should create departure alert event and record completed trip upon arrival', () => {
        const memberId = 'device_test_trip_lifecycle';
        const startState = {
            id: memberId,
            name: 'Isabel',
            x: -0.1250,
            y: 51.3298,
            speedMph: 25.0,
            transitMode: 'DRIVING',
            statusText: 'In a car heading towards Home'
        };
        const prevAtSchool = {
            id: memberId,
            name: 'Isabel',
            x: woodcoteSchool.longitude,
            y: woodcoteSchool.latitude,
            lastStationaryPlace: 'Woodcote High School',
            speedMph: 0.0,
            transitMode: 'STATIONARY'
        };

        // 1. Departure evaluation
        const step1 = processTripLifecycle('test_token', memberId, startState, prevAtSchool, home, [woodcoteSchool]);
        assert.ok(step1.alertEvent, 'Departure alert event should be created on trip start');
        assert.strictEqual(step1.alertEvent.type, 'DEPARTURE');
        assert.ok(step1.alertEvent.message.includes('Isabel has left Woodcote High School'));

        // 2. Midway movement (append breadcrumbs)
        const midState = {
            ...startState,
            x: -0.1210,
            y: 51.3295,
            speedMph: 30.0
        };
        const step2 = processTripLifecycle('test_token', memberId, midState, startState, home, [woodcoteSchool]);
        assert.strictEqual(step2.alertEvent, null, 'Cooldown prevents duplicate departure alerts');

        // 3. Arrival at Home
        const arrivalState = {
            id: memberId,
            name: 'Isabel',
            x: home.lng,
            y: home.lat,
            speedMph: 0.0,
            transitMode: 'STATIONARY',
            statusText: 'At Home (Live GPS)'
        };
        const step3 = processTripLifecycle('test_token', memberId, arrivalState, midState, home, [woodcoteSchool]);
        assert.ok(step3.completedTrip, 'Completed trip should be saved upon arrival at Home');
        assert.strictEqual(step3.completedTrip.destination, 'Home');
        assert.ok(step3.completedTrip.totalDistanceKm > 0.2);
        assert.ok(step3.alertEvent, 'Arrival alert event should be emitted');
        assert.strictEqual(step3.alertEvent.type, 'ARRIVAL');
        assert.ok(step3.alertEvent.message.includes('arrived at Home'));
    });
    it('should reset locationSince when relocating from Home to School and not leak 18h dwell time', () => {
        const storedAtHome = {
            id: 'device_annette',
            name: 'Annette',
            x: home.lng,
            y: home.lat,
            speedMph: 0.0,
            transitMode: 'STATIONARY',
            lastStationaryPlace: 'the house',
            locationSince: Date.now() - (18 * 3600 * 1000) // 18 hours ago
        };
        const incomingAtSchool = {
            id: 'device_annette',
            name: 'Annette',
            x: woodcoteSchool.longitude,
            y: woodcoteSchool.latitude,
            speedMph: 0.0,
            statusText: 'Live GPS'
        };
        const result = enrichMemberState('token1', 'device_annette', incomingAtSchool, storedAtHome, home, [woodcoteSchool]);
        assert.strictEqual(result.statusText, 'At Woodcote High School');
        assert.ok(result.locationSince > Date.now() - 5000, 'locationSince must be fresh upon arrival, not 18 hours ago');
        assert.notStrictEqual(result.locationSince, storedAtHome.locationSince);
    });

    it('should clear locationSince to 0 while actively moving', () => {
        const stored = {
            id: 'device_annette',
            name: 'Annette',
            x: -0.1400,
            y: 51.3300,
            speedMph: 0.0,
            transitMode: 'STATIONARY',
            locationSince: Date.now() - 3600000
        };
        const incomingMoving = {
            id: 'device_annette',
            name: 'Annette',
            x: -0.1300,
            y: 51.3298,
            speedMph: 28.0,
            statusText: 'Driving'
        };
        const result = enrichMemberState('token1', 'device_annette', incomingMoving, stored, home, [woodcoteSchool]);
        assert.strictEqual(result.transitMode, 'DRIVING');
        assert.strictEqual(result.locationSince, 0, 'locationSince must be 0 while moving');
    });

    it('should preserve locationSince when stationary at the same place', () => {
        const arrivalTime = Date.now() - (2 * 3600 * 1000); // 2 hours ago
        const storedAtSchool = {
            id: 'device_annette',
            name: 'Annette',
            x: woodcoteSchool.longitude,
            y: woodcoteSchool.latitude,
            speedMph: 0.0,
            transitMode: 'STATIONARY',
            lastStationaryPlace: 'Woodcote High School',
            locationSince: arrivalTime
        };
        const incomingStillAtSchool = {
            id: 'device_annette',
            name: 'Annette',
            x: woodcoteSchool.longitude,
            y: woodcoteSchool.latitude,
            speedMph: 0.0,
            statusText: 'At Woodcote High School'
        };
        const result = enrichMemberState('token1', 'device_annette', incomingStillAtSchool, storedAtSchool, home, [woodcoteSchool]);
        assert.strictEqual(result.locationSince, arrivalTime, 'locationSince must be preserved while staying at the same place');
    });

    it('should suppress single-tick zero-speed cell tower bounce and couple co-traveling companions (Annette & Isabel)', () => {
        const now = Date.now();
        const storedAnnette = {
            id: 'device_annette_d97b0a',
            name: 'Annette',
            x: -0.1102770,
            y: 51.3260479,
            speedMph: 0.0,
            transitMode: 'STATIONARY',
            lastActive: now - 20000
        };
        // Sudden 370m zero-speed cell jump in 20s
        const incomingCellBounce = {
            id: 'device_annette_d97b0a',
            name: 'Annette',
            x: -0.1080836,
            y: 51.3281267,
            speedMph: 0.0,
            statusText: 'Stationary'
        };
        const held = enrichMemberState('81e5632c_pin_group', 'device_annette_d97b0a', incomingCellBounce, storedAnnette, home, [woodcoteSchool]);
        assert.strictEqual(held.y, storedAnnette.y, 'Single-tick zero-speed cell bounce must be held at previous coordinates');
        assert.strictEqual(held.x, storedAnnette.x);

        // Co-travel coupling: Annette has fresh GPS, Isabel's pocket phone lags 45s behind by 150m
        const members = {
            device_annette_d97b0a: {
                id: 'device_annette_d97b0a',
                name: 'Annette',
                y: 51.3260528,
                x: -0.1102785,
                speedMph: 1.4,
                transitMode: 'WALKING',
                statusText: 'Walking (1.4 mph)',
                lastActive: now
            },
            device_isabel_4ec9dd: {
                id: 'device_isabel_4ec9dd',
                name: 'Isabel',
                y: 51.3260034,
                x: -0.1101554,
                speedMph: 0.0,
                transitMode: 'STATIONARY',
                statusText: 'Stationary',
                lastActive: now - 5000
            }
        };
        // Step 1: Confirm pair when 7m apart
        reconcileCoTravelingCompanions('81e5632c_pin_group', members, home, [woodcoteSchool]);

        // Step 2: Isabel's pocket phone reports a lagging/drifted coordinate 140m behind 30s older than Annette
        members.device_isabel_4ec9dd.y = 51.3247862;
        members.device_isabel_4ec9dd.x = -0.1124929;
        members.device_isabel_4ec9dd.lastActive = now - 35000;
        reconcileCoTravelingCompanions('81e5632c_pin_group', members, home, [woodcoteSchool]);

        assert.strictEqual(members.device_isabel_4ec9dd.y, members.device_annette_d97b0a.y, 'Lagging pocket phone must stay coupled with co-traveling companion');
        assert.strictEqual(members.device_isabel_4ec9dd.x, members.device_annette_d97b0a.x);
    });

    it('should suppress long-range (>1km) single-tick cell-tower teleport spikes', () => {
        const now = Date.now();
        const storedAnnette = {
            id: 'device_annette_d97b0a',
            name: 'Annette',
            x: -0.1153264,
            y: 51.3306103,
            speedMph: 0.4,
            transitMode: 'STATIONARY',
            lastActive: now - 5000
        };
        // Sudden 1.8km jump in 5 seconds (cell tower / stale GPS spike)
        const spike = {
            id: 'device_annette_d97b0a',
            name: 'Annette',
            x: -0.0950000,
            y: 51.3410000,
            speedMph: 2.0,
            statusText: 'Live GPS'
        };
        const held = enrichMemberState('81e5632c_pin_group', 'device_annette_d97b0a', spike, storedAnnette, home, [woodcoteSchool]);
        assert.strictEqual(held.y, storedAnnette.y, 'Long-range 5s teleport spike must be held at previous coordinates');
        assert.strictEqual(held.x, storedAnnette.x);
    });
});