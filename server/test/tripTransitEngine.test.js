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
});
