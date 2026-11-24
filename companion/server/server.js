const WebSocket = require('ws');

const PORT = process.env.PORT || 8080;
const server = new WebSocket.Server({ port: PORT });

// deviceId -> { ws, deviceName, platform, pin, activePeerId, failedPinAttempts, blockedUntil }
const clients = new Map();

const ICE_SERVERS = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' },
  { urls: 'stun:stun2.l.google.com:19302' },
  {
    urls: [
      'turn:openrelay.metered.ca:80',
      'turn:openrelay.metered.ca:443',
      'turn:openrelay.metered.ca:443?transport=tcp'
    ],
    username: 'openrelay',
    credential: 'openrelay'
  }
];

function generateUniqueDeviceId() {
  let id;
  do {
    id = Math.floor(100000000 + Math.random() * 900000000).toString();
  } while (clients.has(id));
  return id;
}

function safeSend(ws, payload) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(payload));
  }
}

function clearPeerLink(deviceId, reason) {
  const entry = clients.get(deviceId);
  if (!entry || !entry.activePeerId) return;
  const peerId = entry.activePeerId;
  entry.activePeerId = null;

  const peerEntry = clients.get(peerId);
  if (peerEntry && peerEntry.activePeerId === deviceId) {
    peerEntry.activePeerId = null;
    safeSend(peerEntry.ws, {
      type: 'disconnect',
      senderId: deviceId,
      targetId: peerId,
      reason: reason || 'Remote peer disconnected'
    });
  }
}

server.on('connection', (ws) => {
  let currentDeviceId = generateUniqueDeviceId();
  clients.set(currentDeviceId, {
    ws,
    deviceName: 'Unknown',
    platform: 'unknown',
    pin: '',
    activePeerId: null,
    failedPinAttempts: 0,
    blockedUntil: 0
  });
  ws.isAlive = true;

  safeSend(ws, { type: 'registered', deviceId: currentDeviceId, iceServers: ICE_SERVERS });
  console.log(`[AirLink] Connected & assigned temporary ID: ${currentDeviceId}`);

  ws.on('pong', () => {
    ws.isAlive = true;
  });

  ws.on('message', (rawMessage) => {
    try {
      const data = JSON.parse(rawMessage.toString());
      const callerEntry = clients.get(currentDeviceId);

      if (data.type === 'register') {
        const requestedId = String(data.deviceId || '').trim();
        if (/^\d{9}$/.test(requestedId) && (!clients.has(requestedId) || clients.get(requestedId).ws === ws)) {
          const prev = clients.get(currentDeviceId);
          clients.delete(currentDeviceId);
          currentDeviceId = requestedId;
          clients.set(currentDeviceId, {
            ws,
            deviceName: data.deviceName || 'AirLink Device',
            platform: data.platform || 'unknown',
            pin: String(data.pin || ''),
            activePeerId: prev ? prev.activePeerId : null,
            failedPinAttempts: 0,
            blockedUntil: 0
          });
        } else if (callerEntry) {
          callerEntry.deviceName = data.deviceName || 'AirLink Device';
          callerEntry.platform = data.platform || 'unknown';
          callerEntry.pin = String(data.pin || '');
        }
        safeSend(ws, { type: 'registered', deviceId: currentDeviceId, iceServers: ICE_SERVERS });
        console.log(`[AirLink] Registered ${data.deviceName} (${data.platform}) -> ${currentDeviceId}`);
        return;
      }

      if (data.type === 'check_status') {
        const queryId = String(data.targetId || '');
        const entry = clients.get(queryId);
        safeSend(ws, {
          type: 'device_status',
          targetId: queryId,
          online: Boolean(entry && entry.ws.readyState === WebSocket.OPEN),
          deviceName: entry ? entry.deviceName : '',
          platform: entry ? entry.platform : ''
        });
        return;
      }

      const targetId = String(data.targetId || '');
      const targetEntry = clients.get(targetId);

      if (data.type === 'call_request' && callerEntry) {
        if (Date.now() < callerEntry.blockedUntil) {
          safeSend(ws, {
            type: 'call_reject',
            senderId: targetId,
            targetId: currentDeviceId,
            reason: 'Too many failed PIN attempts. Wait 30 seconds.'
          });
          return;
        }
      }

      if (!targetEntry || targetEntry.ws.readyState !== WebSocket.OPEN) {
        if (data.type === 'call_request' || data.type === 'offer') {
          safeSend(ws, {
            type: 'error',
            message: `Target device ${targetId} is offline or unreachable`
          });
        }
        return;
      }

      if (data.type === 'call_accept' && callerEntry) {
        callerEntry.activePeerId = targetId;
        targetEntry.activePeerId = currentDeviceId;
        targetEntry.failedPinAttempts = 0;
      } else if (data.type === 'call_reject') {
        if (String(data.reason || '').toLowerCase().includes('pin')) {
          targetEntry.failedPinAttempts = (targetEntry.failedPinAttempts || 0) + 1;
          if (targetEntry.failedPinAttempts >= 5) {
            targetEntry.blockedUntil = Date.now() + 30000;
            targetEntry.failedPinAttempts = 0;
          }
        }
      } else if (data.type === 'disconnect') {
        if (callerEntry) callerEntry.activePeerId = null;
        if (targetEntry.activePeerId === currentDeviceId) targetEntry.activePeerId = null;
      }

      data.senderId = currentDeviceId;
      safeSend(targetEntry.ws, data);
    } catch (err) {
      console.error('[AirLink] Message parse error:', err.message);
    }
  });

  ws.on('close', () => {
    const entry = clients.get(currentDeviceId);
    if (entry && entry.ws === ws) {
      clearPeerLink(currentDeviceId, 'Remote peer went offline');
      clients.delete(currentDeviceId);
      console.log(`[AirLink] Disconnected: ${currentDeviceId}`);
    }
  });
});

// Keepalive heartbeat to prune stale sockets and notify active peers
const heartbeat = setInterval(() => {
  server.clients.forEach((ws) => {
    if (ws.isAlive === false) return ws.terminate();
    ws.isAlive = false;
    ws.ping();
  });
}, 30000);

server.on('close', () => clearInterval(heartbeat));

console.log(`[AirLink] Signaling Server listening on ws://0.0.0.0:${PORT}`);
