const relayBadge = document.getElementById('relayBadge');
const localDeviceIdEl = document.getElementById('localDeviceId');
const localPinEl = document.getElementById('localPin');
const serverUrlInput = document.getElementById('serverUrlInput');
const targetIdInput = document.getElementById('targetIdInput');
const targetPinInput = document.getElementById('targetPinInput');
const sessionLabel = document.getElementById('sessionLabel');
const remoteVideo = document.getElementById('remoteVideo');
const idleOverlay = document.getElementById('idleOverlay');
const logBox = document.getElementById('logBox');
const authDialog = document.getElementById('authDialog');
const authDesc = document.getElementById('authDesc');

let config = null;
let ws = null;
let pc = null;
let inputChannel = null;
let localMediaStream = null;
let activePeerId = '';
let isHostSharing = false;
let pendingCall = null;
let iceServers = [
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
let remoteDescSet = false;
let pendingCandidates = [];
let statsTimer = null;

function log(msg) {
  logBox.textContent = `[${new Date().toLocaleTimeString()}] ${msg}\n` + logBox.textContent.slice(0, 800);
}

function formatId(id) {
  return String(id || '').replace(/(\d{3})(?=\d)/g, '$1 ');
}

function sendSignal(payload) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(payload));
  }
}

function sendInput(packet) {
  if (inputChannel && inputChannel.readyState === 'open') {
    inputChannel.send(JSON.stringify(packet));
  }
}

async function init() {
  config = await window.airlinkNative.getConfig();
  localDeviceIdEl.textContent = formatId(config.deviceId);
  localPinEl.textContent = config.securityPin;
  serverUrlInput.value = config.signalingUrl;
  connectSignaling(config.signalingUrl);

  window.airlinkNative.onEmergencyDisconnect(() => {
    log('Emergency disconnect triggered (Ctrl+Alt+End)');
    disconnectSession();
  });
}

function connectSignaling(url) {
  if (ws) ws.close();
  relayBadge.textContent = 'Connecting…';
  relayBadge.className = 'pill';

  ws = new WebSocket(url);
  ws.onopen = () => {
    relayBadge.textContent = 'Relay Online';
    relayBadge.className = 'pill online';
    sendSignal({
      type: 'register',
      deviceId: config.deviceId,
      deviceName: config.deviceName,
      platform: 'windows',
      pin: config.securityPin
    });
  };

  ws.onmessage = async (evt) => {
    const msg = JSON.parse(evt.data);
    switch (msg.type) {
      case 'registered':
        if (Array.isArray(msg.iceServers) && msg.iceServers.length > 0) {
          iceServers = msg.iceServers;
        }
        if (msg.deviceId && msg.deviceId !== config.deviceId) {
          config = await window.airlinkNative.saveConfig({ deviceId: msg.deviceId });
          localDeviceIdEl.textContent = formatId(config.deviceId);
        }
        log(`Registered as ${config.deviceId}`);
        break;

      case 'call_request':
        if (config.requirePinApproval && msg.pin !== config.securityPin) {
          sendSignal({ type: 'call_reject', targetId: msg.senderId, reason: 'Invalid Security PIN' });
          log(`Blocked ${msg.callerName} (wrong PIN)`);
          return;
        }
        pendingCall = msg;
        authDesc.textContent = `${msg.callerName} (${msg.senderId}) wants to view and control your Windows desktop.`;
        authDialog.showModal();
        break;

      case 'call_accept':
        activePeerId = msg.senderId;
        isHostSharing = false;
        sessionLabel.textContent = `Controlling ${msg.accepterName}`;
        await startControllerSession(msg.senderId);
        break;

      case 'call_reject':
        sessionLabel.textContent = `Rejected: ${msg.reason}`;
        log(`Call rejected: ${msg.reason}`);
        break;

      case 'offer':
        await handleRemoteOffer(msg.senderId, msg.offer);
        break;

      case 'answer':
        if (pc) {
          await pc.setRemoteDescription(new RTCSessionDescription(msg.answer));
          remoteDescSet = true;
          flushCandidates();
        }
        break;

      case 'candidate':
        if (pc && remoteDescSet) {
          await pc.addIceCandidate(new RTCIceCandidate(msg.candidate));
        } else {
          pendingCandidates.push(msg.candidate);
        }
        break;

      case 'disconnect':
        log(msg.reason || 'Remote peer disconnected');
        teardownPeer();
        break;

      case 'error':
        log(`Server error: ${msg.message}`);
        sessionLabel.textContent = msg.message;
        break;
    }
  };

  ws.onclose = () => {
    relayBadge.textContent = 'Relay Offline';
    relayBadge.className = 'pill';
  };
}

function flushCandidates() {
  while (pendingCandidates.length > 0) {
    const c = pendingCandidates.shift();
    pc.addIceCandidate(new RTCIceCandidate(c)).catch(() => {});
  }
}

function createPeer(targetId) {
  teardownPeer();
  activePeerId = targetId;
  remoteDescSet = false;
  pendingCandidates = [];

  pc = new RTCPeerConnection({ iceServers });

  pc.onicecandidate = (e) => {
    if (e.candidate) {
      sendSignal({
        type: 'candidate',
        targetId,
        candidate: {
          candidate: e.candidate.candidate,
          sdpMid: e.candidate.sdpMid,
          sdpMLineIndex: e.candidate.sdpMLineIndex
        }
      });
    }
  };

  pc.ontrack = (e) => {
    if (e.track.kind === 'video') {
      remoteVideo.srcObject = e.streams[0];
      idleOverlay.style.display = 'none';
    }
  };

  pc.ondatachannel = (e) => {
    attachDataChannel(e.channel);
  };

  pc.onconnectionstatechange = () => {
    if (pc && (pc.connectionState === 'failed' || pc.connectionState === 'closed')) {
      teardownPeer();
    }
  };

  statsTimer = setInterval(pollStats, 1500);
}

function attachDataChannel(channel) {
  inputChannel = channel;
  channel.onopen = () => log('DataChannel inputs_channel OPEN');
  channel.onmessage = async (e) => {
    try {
      const pkt = JSON.parse(e.data);
      if (pkt.type === 'quality_change') {
        await applyHostQuality(pkt.preset);
      } else {
        window.airlinkNative.injectInput(pkt);
      }
    } catch (_) {}
  };
}

async function applyHostQuality(preset) {
  if (!localMediaStream) return;
  const track = localMediaStream.getVideoTracks()[0];
  if (!track) return;
  const presets = {
    HD_1080_60: { width: 1920, height: 1080, frameRate: 60 },
    HD_720_60: { width: 1280, height: 720, frameRate: 60 },
    SD_480_30: { width: 854, height: 480, frameRate: 30 }
  };
  const cfg = presets[preset] || presets.HD_720_60;
  try {
    await track.applyConstraints(cfg);
    log(`Adjusted desktop stream to ${preset}`);
  } catch (err) {
    log(`Quality constraint update failed: ${err.message}`);
  }
}

async function startControllerSession(targetId) {
  createPeer(targetId);
  pc.addTransceiver('video', { direction: 'recvonly' });
  const dc = pc.createDataChannel('inputs_channel', { ordered: true });
  attachDataChannel(dc);

  const offer = await pc.createOffer({ offerToReceiveVideo: true, offerToReceiveAudio: false });
  await pc.setLocalDescription(offer);
  sendSignal({ type: 'offer', targetId, offer: { type: offer.type, sdp: offer.sdp } });
}

async function startHostScreenShare(targetId) {
  createPeer(targetId);
  const sources = await window.airlinkNative.getScreenSources();
  if (!sources.length) {
    sendSignal({ type: 'call_reject', targetId, reason: 'No Windows display source available' });
    return;
  }

  localMediaStream = await navigator.mediaDevices.getUserMedia({
    audio: false,
    video: {
      mandatory: {
        chromeMediaSource: 'desktop',
        chromeMediaSourceId: sources[0].id,
        maxWidth: 1920,
        maxHeight: 1080,
        maxFrameRate: 60
      }
    }
  });

  localMediaStream.getVideoTracks().forEach((track) => pc.addTrack(track, localMediaStream));
  sendSignal({
    type: 'call_accept',
    targetId,
    accepterName: config.deviceName,
    accepterPlatform: 'windows'
  });
  sessionLabel.textContent = `Sharing Windows screen to ${targetId}`;
  idleOverlay.style.display = 'none';
}

async function handleRemoteOffer(senderId, offer) {
  if (!pc) createPeer(senderId);
  await pc.setRemoteDescription(new RTCSessionDescription(offer));
  remoteDescSet = true;
  flushCandidates();
  const answer = await pc.createAnswer();
  await pc.setLocalDescription(answer);
  sendSignal({ type: 'answer', targetId: senderId, answer: { type: answer.type, sdp: answer.sdp } });
}

async function pollStats() {
  if (!pc) return;
  const report = await pc.getStats();
  let fps = 0;
  let rtt = 0;
  report.forEach((stat) => {
    if ((stat.type === 'inbound-rtp' || stat.type === 'outbound-rtp') && stat.kind === 'video') {
      fps = Math.round(stat.framesPerSecond || fps);
    }
    if (stat.type === 'candidate-pair' && stat.state === 'succeeded' && stat.currentRoundTripTime) {
      rtt = Math.round(stat.currentRoundTripTime * 1000);
    }
  });
  if (activePeerId) {
    sessionLabel.textContent = `${isHostSharing ? 'Hosting' : 'Controlling'} ${activePeerId} • ${fps} FPS • ${rtt} ms`;
  }
}

function disconnectSession() {
  if (activePeerId) {
    sendSignal({ type: 'disconnect', targetId: activePeerId, reason: 'Session ended by Windows user' });
  }
  teardownPeer();
}

function teardownPeer() {
  if (statsTimer) clearInterval(statsTimer);
  statsTimer = null;
  if (localMediaStream) {
    localMediaStream.getTracks().forEach((t) => t.stop());
    localMediaStream = null;
  }
  if (inputChannel) inputChannel.close();
  inputChannel = null;
  if (pc) pc.close();
  pc = null;
  remoteVideo.srcObject = null;
  idleOverlay.style.display = 'flex';
  sessionLabel.textContent = 'Idle';
  activePeerId = '';
  isHostSharing = false;
}

// Compute active rendered video rect inside <video style="object-fit: contain">
function getNormalizedVideoCoords(clientX, clientY) {
  const rect = remoteVideo.getBoundingClientRect();
  const vw = remoteVideo.videoWidth || 1080;
  const vh = remoteVideo.videoHeight || 1920;
  const videoAspect = vw / vh;
  const elemAspect = rect.width / Math.max(1, rect.height);

  let drawW = rect.width;
  let drawH = rect.height;
  let offsetX = 0;
  let offsetY = 0;

  if (elemAspect > videoAspect) {
    drawW = rect.height * videoAspect;
    offsetX = (rect.width - drawW) / 2;
  } else {
    drawH = rect.width / videoAspect;
    offsetY = (rect.height - drawH) / 2;
  }

  const nx = Math.min(1, Math.max(0, (clientX - rect.left - offsetX) / Math.max(1, drawW)));
  const ny = Math.min(1, Math.max(0, (clientY - rect.top - offsetY) / Math.max(1, drawH)));
  return { x: nx, y: ny };
}

function buttonName(btn) {
  if (btn === 2) return 'right';
  if (btn === 1) return 'middle';
  return 'left';
}

remoteVideo.addEventListener('contextmenu', (e) => e.preventDefault());

remoteVideo.addEventListener('mousemove', (e) => {
  if (isHostSharing || !inputChannel || inputChannel.readyState !== 'open') return;
  const { x, y } = getNormalizedVideoCoords(e.clientX, e.clientY);
  sendInput({ type: 'mouse_move', x, y });
});

remoteVideo.addEventListener('mousedown', (e) => {
  if (isHostSharing) return;
  remoteVideo.focus();
  const { x, y } = getNormalizedVideoCoords(e.clientX, e.clientY);
  sendInput({ type: 'mouse_click', button: buttonName(e.button), action: 'down', x, y });
});

remoteVideo.addEventListener('mouseup', (e) => {
  if (isHostSharing) return;
  const { x, y } = getNormalizedVideoCoords(e.clientX, e.clientY);
  sendInput({ type: 'mouse_click', button: buttonName(e.button), action: 'up', x, y });
});

remoteVideo.addEventListener('wheel', (e) => {
  if (isHostSharing) return;
  e.preventDefault();
  const { x, y } = getNormalizedVideoCoords(e.clientX, e.clientY);
  // Normalize wheel delta: scrolling down on Windows gives positive e.deltaY -> swipe finger up (negative deltaY)
  const dy = Math.sign(-e.deltaY) * 1.2;
  const dx = Math.sign(-e.deltaX) * 1.2;
  sendInput({ type: 'mouse_scroll', deltaX: dx, deltaY: dy, x, y });
}, { passive: false });

remoteVideo.addEventListener('keydown', (e) => {
  if (isHostSharing) return;
  e.preventDefault();
  const mods = [];
  if (e.ctrlKey) mods.push('ctrl');
  if (e.altKey) mods.push('alt');
  if (e.shiftKey) mods.push('shift');

  if (e.key.length === 1 && !e.ctrlKey && !e.altKey) {
    sendInput({ type: 'text_input', text: e.key });
  } else {
    sendInput({ type: 'key_press', keyCode: e.keyCode, keyName: e.key, modifiers: mods, action: 'press' });
  }
});

document.getElementById('regenPinBtn').onclick = async () => {
  const newPin = Math.floor(1000 + Math.random() * 9000).toString();
  config = await window.airlinkNative.saveConfig({ securityPin: newPin });
  localPinEl.textContent = newPin;
  sendSignal({
    type: 'register',
    deviceId: config.deviceId,
    deviceName: config.deviceName,
    platform: 'windows',
    pin: config.securityPin
  });
};

document.getElementById('connectRelayBtn').onclick = async () => {
  const url = serverUrlInput.value.trim();
  config = await window.airlinkNative.saveConfig({ signalingUrl: url });
  connectSignaling(url);
};

document.getElementById('connectPeerBtn').onclick = () => {
  const targetId = targetIdInput.value.trim();
  const pin = targetPinInput.value.trim();
  if (targetId.length !== 9 || pin.length !== 4) {
    log('Enter valid 9-digit Android ID and 4-digit PIN');
    return;
  }
  sessionLabel.textContent = `Requesting access to ${targetId}…`;
  sendSignal({
    type: 'call_request',
    targetId,
    senderId: config.deviceId,
    callerName: config.deviceName,
    callerPlatform: 'windows',
    pin
  });
};

document.getElementById('allowBtn').onclick = async () => {
  authDialog.close();
  if (!pendingCall) return;
  const req = pendingCall;
  pendingCall = null;
  isHostSharing = true;
  await startHostScreenShare(req.senderId);
};

document.getElementById('denyBtn').onclick = () => {
  authDialog.close();
  if (!pendingCall) return;
  sendSignal({ type: 'call_reject', targetId: pendingCall.senderId, reason: 'Windows host declined request' });
  pendingCall = null;
};

document.getElementById('androidBackBtn').onclick = () => sendInput({ type: 'system_action', action: 'back' });
document.getElementById('androidHomeBtn').onclick = () => sendInput({ type: 'system_action', action: 'home' });
document.getElementById('androidRecentsBtn').onclick = () => sendInput({ type: 'system_action', action: 'recents' });
document.getElementById('disconnectBtn').onclick = disconnectSession;

init();
