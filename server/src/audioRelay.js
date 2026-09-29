const { WebSocketServer, WebSocket } = require('ws');
const url = require('url');

/**
 * In-memory registry of active audio rooms by circleId:
 * circleId -> {
 *   broadcaster: WebSocket | null,
 *   broadcasterInfo: { memberId, memberName, connectedAt } | null,
 *   listeners: Map<WebSocket, { memberId, memberName, connectedAt }>
 * }
 */
const rooms = new Map();

function getOrCreateRoom(circleId) {
    if (!rooms.has(circleId)) {
        rooms.set(circleId, {
            broadcaster: null,
            broadcasterInfo: null,
            listeners: new Map()
        });
    }
    return rooms.get(circleId);
}

function cleanRoomIfEmpty(circleId) {
    const room = rooms.get(circleId);
    if (!room) return;
    if (!room.broadcaster && room.listeners.size === 0) {
        rooms.delete(circleId);
    }
}

function setupAudioRelay(server) {
    const wss = new WebSocketServer({
        noServer: true,
        perMessageDeflate: false // disable compression for raw PCM audio chunks for lowest latency
    });

    server.on('upgrade', (request, socket, head) => {
        const parsedUrl = url.parse(request.url, true);
        if (parsedUrl.pathname === '/audio') {
            wss.handleUpgrade(request, socket, head, (ws) => {
                wss.emit('connection', ws, request);
            });
        }
    });

    wss.on('connection', (ws, request) => {
        const parsedUrl = url.parse(request.url, true);
        const query = parsedUrl.query || {};
        const role = (query.role || '').toLowerCase(); // 'broadcast' or 'listen'
        const circleId = query.circleId || 'default';
        const memberId = query.memberId || 'anonymous';
        const memberName = query.memberName || memberId;

        ws.isAlive = true;
        ws.on('pong', () => { ws.isAlive = true; });

        const room = getOrCreateRoom(circleId);

        if (role === 'broadcast') {
            // Close any existing broadcaster for this circle (only 1 broadcaster at a time per circle)
            if (room.broadcaster && room.broadcaster !== ws) {
                try {
                    room.broadcaster.send(JSON.stringify({ type: 'replaced', message: 'New broadcaster connected for this circle' }));
                    room.broadcaster.close(1000, 'Replaced by new broadcaster');
                } catch (_) {}
            }

            room.broadcaster = ws;
            room.broadcasterInfo = { memberId, memberName, connectedAt: Date.now() };
            console.log(`[AudioRelay] 🎙️ Broadcaster connected for circle "${circleId}" (${memberName} / ${memberId}). Active listeners: ${room.listeners.size}`);

            // Notify all listeners that a broadcaster is live
            for (const [listenerWs] of room.listeners) {
                if (listenerWs.readyState === WebSocket.OPEN) {
                    try {
                        listenerWs.send(JSON.stringify({ type: 'status', status: 'live', broadcaster: memberName }));
                    } catch (_) {}
                }
            }

            ws.on('message', (data, isBinary) => {
                if (!isBinary) {
                    // Control message (JSON ping or status)
                    return;
                }
                // Relay raw PCM audio buffer to all connected listeners in this circle
                for (const [listenerWs] of room.listeners) {
                    if (listenerWs.readyState === WebSocket.OPEN) {
                        try {
                            listenerWs.send(data, { binary: true });
                        } catch (err) {
                            console.error(`[AudioRelay] Error sending audio chunk to listener:`, err.message);
                        }
                    }
                }
            });

            ws.on('close', (code, reason) => {
                console.log(`[AudioRelay] 🔕 Broadcaster disconnected for circle "${circleId}" (${code}: ${reason || 'normal'})`);
                if (room.broadcaster === ws) {
                    room.broadcaster = null;
                    room.broadcasterInfo = null;
                }
                // Notify listeners that broadcaster ended
                for (const [listenerWs] of room.listeners) {
                    if (listenerWs.readyState === WebSocket.OPEN) {
                        try {
                            listenerWs.send(JSON.stringify({ type: 'status', status: 'idle', message: 'Broadcaster disconnected' }));
                        } catch (_) {}
                    }
                }
                cleanRoomIfEmpty(circleId);
            });

            ws.on('error', (err) => {
                console.error(`[AudioRelay] Broadcaster error in circle "${circleId}":`, err.message);
            });

        } else if (role === 'listen') {
            room.listeners.set(ws, { memberId, memberName, connectedAt: Date.now() });
            console.log(`[AudioRelay] 🎧 Listener connected to circle "${circleId}" (${memberName}). Total listeners: ${room.listeners.size}`);

            // Immediately inform listener of current broadcaster status
            const isLive = Boolean(room.broadcaster && room.broadcaster.readyState === WebSocket.OPEN);
            ws.send(JSON.stringify({
                type: 'status',
                status: isLive ? 'live' : 'idle',
                broadcaster: room.broadcasterInfo ? room.broadcasterInfo.memberName : null
            }));

            ws.on('message', () => {
                // Listeners don't send audio upstream
            });

            ws.on('close', () => {
                room.listeners.delete(ws);
                console.log(`[AudioRelay] ⏹️ Listener disconnected from circle "${circleId}". Remaining: ${room.listeners.size}`);
                cleanRoomIfEmpty(circleId);
            });

            ws.on('error', (err) => {
                console.error(`[AudioRelay] Listener error in circle "${circleId}":`, err.message);
            });

        } else {
            console.warn(`[AudioRelay] Unknown role "${role}" requested by ${memberId}`);
            ws.close(1008, 'Invalid role: must be broadcast or listen');
        }
    });

    // Keepalive ping/pong loop (every 25s to keep cellular NAT sessions open)
    const interval = setInterval(() => {
        wss.clients.forEach((ws) => {
            if (!ws.isAlive) {
                return ws.terminate();
            }
            ws.isAlive = false;
            ws.ping();
        });
    }, 25000);

    wss.on('close', () => {
        clearInterval(interval);
    });

    return wss;
}

function getAudioRelayStats() {
    const stats = {};
    for (const [circleId, room] of rooms.entries()) {
        stats[circleId] = {
            hasBroadcaster: Boolean(room.broadcaster && room.broadcaster.readyState === WebSocket.OPEN),
            broadcaster: room.broadcasterInfo,
            listenerCount: room.listeners.size
        };
    }
    return stats;
}

module.exports = {
    setupAudioRelay,
    getAudioRelayStats
};
