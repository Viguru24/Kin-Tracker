const express = require('express');
const router = express.Router();
const db = require('../db');
const { generateInviteCode, normalizeCode } = require('../utils/codeGenerator');

/**
 * 1. CREATE A NEW CIRCLE (Life360 Style)
 * Generates an intuitive 6-character code (e.g. "K9F-2Q8").
 */
router.post('/create', (req, res) => {
    try {
        const {
            name,
            creatorId,
            creatorName,
            avatarColorHex,
            avatarEmoji,
            homeLat,
            homeLng,
            isHomeCalibrated,
            workLat,
            workLng,
            isWorkCalibrated,
            homeRadiusMeters,
            workRadiusMeters
        } = req.body;

        const circleId = `circle_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
        let inviteCode = generateInviteCode();

        // Ensure invite code uniqueness
        let attempts = 0;
        while (db.getCircleByInviteCode(inviteCode) && attempts < 10) {
            inviteCode = generateInviteCode();
            attempts++;
        }

        const circle = {
            id: circleId,
            name: name && name.trim() ? name.trim() : 'Family Circle',
            inviteCode: inviteCode,
            creatorId: creatorId || 'admin',
            createdAt: Date.now(),
            lastUpdated: Date.now(),
            homeLat: Number(homeLat) || 51.329480,
            homeLng: Number(homeLng) || -0.119095,
            isHomeCalibrated: Boolean(isHomeCalibrated),
            workLat: Number(workLat) || 0.0,
            workLng: Number(workLng) || 0.0,
            isWorkCalibrated: Boolean(isWorkCalibrated),
            homeRadiusMeters: Number(homeRadiusMeters) || 140.0,
            workRadiusMeters: Number(workRadiusMeters) || 75.0,
            memberIds: []
        };

        db.saveCircle(circle);

        // If creator info is provided, add them as first member
        if (creatorId) {
            const creatorMember = {
                id: creatorId,
                circleId: circleId,
                name: creatorName || 'Owner',
                avatarColorHex: avatarColorHex || '#00FF88',
                avatarEmoji: avatarEmoji || '👑',
                x: circle.homeLng,
                y: circle.homeLat,
                batteryPercentage: 100,
                isCharging: false,
                speedMph: 0.0,
                statusText: 'Circle Creator',
                isComingHome: false,
                etaMinutes: 0,
                lastActive: Date.now(),
                isLocationPaused: false
            };
            db.saveMember(creatorMember);
        }

        console.log(`[Circle] Created circle "${circle.name}" with Invite Code ${circle.inviteCode} (ID: ${circle.id})`);

        return res.status(201).json({
            success: true,
            circle: {
                ...circle,
                members: db.getCircleMembers(circleId),
                shoppingItems: db.getCircleShoppingItems(circleId),
                safeZones: db.getCircleSafeZones(circleId)
            }
        });
    } catch (err) {
        console.error('[Circle Create Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

/**
 * 2. JOIN AN EXISTING CIRCLE BY 6-CHARACTER CODE (or legacy 4-digit PIN)
 */
router.post('/join', (req, res) => {
    try {
        const {
            inviteCode,
            memberId,
            name,
            avatarColorHex,
            avatarEmoji,
            phone
        } = req.body;

        if (!inviteCode) {
            return res.status(400).json({ success: false, error: 'Invite code is required.' });
        }

        const circle = db.getCircleByInviteCode(inviteCode);
        if (!circle) {
            return res.status(404).json({
                success: false,
                error: `Circle with code "${inviteCode}" was not found. Please check the code and try again.`
            });
        }

        // Register / update joining member
        if (memberId) {
            const existingMember = db.getMember(memberId);
            const member = {
                id: memberId,
                circleId: circle.id,
                name: name || existingMember?.name || 'Family Member',
                avatarColorHex: avatarColorHex || existingMember?.avatarColorHex || '#00F0FF',
                avatarEmoji: avatarEmoji || existingMember?.avatarEmoji || '📱',
                phone: phone || existingMember?.phone || '',
                x: existingMember?.x || circle.homeLng,
                y: existingMember?.y || circle.homeLat,
                batteryPercentage: existingMember?.batteryPercentage || 100,
                isCharging: existingMember?.isCharging || false,
                speedMph: existingMember?.speedMph || 0.0,
                statusText: 'Just Joined',
                isComingHome: false,
                etaMinutes: 0,
                lastActive: Date.now(),
                isLocationPaused: false
            };
            db.saveMember(member);
        }

        console.log(`[Circle] Member ${name || memberId} successfully joined circle "${circle.name}" (${circle.inviteCode})`);

        return res.json({
            success: true,
            circle: {
                ...circle,
                members: db.getCircleMembers(circle.id),
                shoppingItems: db.getCircleShoppingItems(circle.id),
                safeZones: db.getCircleSafeZones(circle.id)
            }
        });
    } catch (err) {
        console.error('[Circle Join Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

/**
 * 3. REAL-TIME CIRCLE SYNC
 * Returns active members, locations, shopping items, and safe zones.
 */
router.get('/:circleId/sync', (req, res) => {
    try {
        const { circleId } = req.params;
        let circle = db.getCircleById(circleId);

        // Fallback: If passed an invite code instead of circleId
        if (!circle) {
            circle = db.getCircleByInviteCode(circleId);
        }

        if (!circle) {
            return res.status(404).json({ success: false, error: 'Circle not found' });
        }

        const members = db.getCircleMembers(circle.id);
        const shoppingItems = db.getCircleShoppingItems(circle.id);
        const safeZones = db.getCircleSafeZones(circle.id);

        return res.json({
            success: true,
            circleId: circle.id,
            name: circle.name,
            inviteCode: circle.inviteCode,
            creatorId: circle.creatorId,
            homeLat: circle.homeLat,
            homeLng: circle.homeLng,
            isHomeCalibrated: circle.isHomeCalibrated,
            workLat: circle.workLat,
            workLng: circle.workLng,
            isWorkCalibrated: circle.isWorkCalibrated,
            homeRadiusMeters: circle.homeRadiusMeters,
            workRadiusMeters: circle.workRadiusMeters,
            lastUpdated: circle.lastUpdated,
            members: members,
            shoppingItems: shoppingItems,
            safeZones: safeZones
        });
    } catch (err) {
        console.error('[Circle Sync Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

/**
 * 4. ATOMIC LOCATION UPDATE (One device updates only its own location)
 * Prevents race conditions and location loss.
 */
router.post('/:circleId/location', (req, res) => {
    try {
        const { circleId } = req.params;
        const {
            memberId,
            name,
            avatarColorHex,
            avatarEmoji,
            lat,
            lng,
            batteryPct,
            isCharging,
            speedMph,
            statusText,
            isComingHome,
            etaMinutes,
            isLocationPaused
        } = req.body;

        if (!memberId) {
            return res.status(400).json({ success: false, error: 'memberId is required.' });
        }

        let circle = db.getCircleById(circleId);
        if (!circle) {
            circle = db.getCircleByInviteCode(circleId);
        }
        if (!circle) {
            return res.status(404).json({ success: false, error: 'Circle not found.' });
        }

        const existingMember = db.getMember(memberId);
        const updatedMember = {
            id: memberId,
            circleId: circle.id,
            name: name || existingMember?.name || 'Device',
            avatarColorHex: avatarColorHex || existingMember?.avatarColorHex || '#00FF88',
            avatarEmoji: avatarEmoji || existingMember?.avatarEmoji || '📱',
            x: Number(lng) || existingMember?.x || 0.0,
            y: Number(lat) || existingMember?.y || 0.0,
            batteryPercentage: batteryPct !== undefined ? Number(batteryPct) : (existingMember?.batteryPercentage || 100),
            isCharging: isCharging !== undefined ? Boolean(isCharging) : (existingMember?.isCharging || false),
            speedMph: speedMph !== undefined ? Number(speedMph) : (existingMember?.speedMph || 0.0),
            statusText: statusText || existingMember?.statusText || 'Active',
            isComingHome: isComingHome !== undefined ? Boolean(isComingHome) : false,
            etaMinutes: etaMinutes !== undefined ? Number(etaMinutes) : 0,
            lastActive: Date.now(),
            isLocationPaused: isLocationPaused !== undefined ? Boolean(isLocationPaused) : false
        };

        db.saveMember(updatedMember);

        // Update circle timestamp
        circle.lastUpdated = Date.now();
        db.saveCircle(circle);

        return res.json({
            success: true,
            member: updatedMember
        });
    } catch (err) {
        console.error('[Location Update Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

/**
 * 5. REGENERATE INVITE CODE (Life360 feature)
 */
router.post('/:circleId/regenerate-code', (req, res) => {
    try {
        const { circleId } = req.params;
        const circle = db.getCircleById(circleId);
        if (!circle) {
            return res.status(404).json({ success: false, error: 'Circle not found' });
        }

        const newCode = generateInviteCode();
        circle.inviteCode = newCode;
        circle.lastUpdated = Date.now();
        db.saveCircle(circle);

        console.log(`[Circle] Regenerated code for "${circle.name}": ${newCode}`);

        return res.json({
            success: true,
            inviteCode: newCode
        });
    } catch (err) {
        console.error('[Regenerate Code Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

/**
 * 6. SHOPPING LIST SYNC
 */
router.post('/:circleId/shopping', (req, res) => {
    try {
        const { circleId } = req.params;
        const { action, item, itemId } = req.body; // action: 'add', 'toggle', 'delete'

        if (action === 'add' && item) {
            const newItem = {
                id: item.id || `item_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
                circleId: circleId,
                name: item.name,
                isChecked: Boolean(item.isChecked),
                addedByMemberId: item.addedByMemberId || '',
                addedByMemberName: item.addedByMemberName || '',
                timestamp: Date.now()
            };
            db.saveShoppingItem(newItem);
        } else if (action === 'toggle' && item) {
            const existing = db.shoppingItems[item.id];
            if (existing) {
                existing.isChecked = Boolean(item.isChecked);
                db.saveShoppingItem(existing);
            }
        } else if (action === 'delete' && (itemId || item?.id)) {
            db.deleteShoppingItem(itemId || item.id);
        }

        const items = db.getCircleShoppingItems(circleId);
        return res.json({ success: true, shoppingItems: items });
    } catch (err) {
        console.error('[Shopping Sync Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

/**
 * 7. LEAVE / KICK MEMBER
 */
router.post('/:circleId/leave', (req, res) => {
    try {
        const { circleId } = req.params;
        const { memberId } = req.body;
        if (!memberId) return res.status(400).json({ success: false, error: 'memberId required' });

        db.removeMemberFromCircle(circleId, memberId);
        return res.json({ success: true, message: 'Member removed from circle' });
    } catch (err) {
        console.error('[Leave Circle Error]', err);
        return res.status(500).json({ success: false, error: err.message });
    }
});

module.exports = router;
