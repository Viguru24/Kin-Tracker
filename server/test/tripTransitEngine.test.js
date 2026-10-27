const assert = require('assert');
const { describe, it } = require('node:test');
const {
    distMeters,
    calcBearing,
    resolveOriginName,
    determineDestination,
    classifyTransitMode,
    enrichMemberState
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
});
