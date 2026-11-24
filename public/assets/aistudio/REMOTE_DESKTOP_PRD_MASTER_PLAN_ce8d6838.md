# 📱💻 Master PRD & Implementation Guide: Bidirectional Remote Control System
**Project Codename:** `AirLink / OSLink Clone`  
**Target Platforms:** Android (Mobile APK) & Windows Desktop (`.exe`)  
**Core Framework:** Flutter (Dart) + WebRTC (P2P Ultra-Low Latency) + Node.js (Signaling)

---

## 🎯 1. Overview & Objective
Is document ka maqsad ek mukammal **Bidirectional Remote Desktop & Screen Mirroring Suite** tayyar karna hai (bilkul **OSLink**, **AnyDesk**, ya **RustDesk** ki tarah):
1. **Phone Controls PC:** Mobile user apne phone par PC ki live screen (60 FPS) dekhega aur phone ke touch/gestures se PC ka mouse aur keyboard control karega.
2. **PC Controls Phone:** PC user apne computer par phone ki live screen dekhega aur computer ke mouse click/scroll se phone ko complete control karega (bina root ke, using Android Accessibility Service).
3. **P2P Direct Connection:** WebRTC protocol par low latency (<50ms delay) real-time video aur data transfer.
4. **Device Management:** 9-digit Device ID aur Security PIN system jisse koi bhi do devices connect ho sakein.

---

## 🤖 2. Instructions for Implementing AI Agent
> **Notice for the AI Agent reading this document:**  
> Aap is PRD ke zariye user ke liye ye mukammal software build karenge. Neeche diye gaye steps ko sequentially follow karein:
> - User se unnecessary sawaalat na poochein; project structure aur code directly create karein.
> - Code modular, clean, aur production-grade hona chahiye with null-safety.
> - WebRTC implementation ke liye `flutter_webrtc` use karein.
> - Input handling ke liye Windows par Win32 `SendInput` aur Android par `AccessibilityService` implement karein.

---

## 👤 3. What the Human User Must Do (User Checklist)
Ye wo cheezein hain jo user (Asad) ko apne computer aur phone par karni hongi:

- [ ] **Step 1: Windows Defender Exclusion**
  - Windows Security ➔ Virus & threat protection ➔ Manage settings ➔ Exclusions ➔ Add Exclusion ➔ Folder:
    - `H:\flutter`
    - `H:\AndroidSDK`
  - *Kyun zaroori hai?* Taake Windows Defender `dart.exe` ko virus samajh kar block na kare.
- [ ] **Step 2: Windows C++ Build Tools**
  - Flutter se Windows Desktop `.exe` banane ke liye Visual Studio 2022 ka "Desktop development with C++" workload install hona chahiye.
- [ ] **Step 3: Android Phone Developer Options**
  - Phone mein "Developer Options" on karein aur "USB Debugging" enable karein.
  - App install hone ke baad Phone ki Settings mein ja kar **Accessibility Service** ko "ON" karna hoga taake PC se touch control chal sake.
- [ ] **Step 4: Signaling Server Run Karna**
  - Local testing ke liye PC ke terminal mein ek simple Node.js command chalani hogi: `node server.js`.

---

## 🏗️ 4. Technology Stack & Architecture

```
┌────────────────────────────────────────────────────────┐
│               Node.js Signaling Server                │
│             (WebSockets / SDP / ICE Exchange)         │
└───────────────▲────────────────────────▲───────────────┘
                │                        │
         Handshake (JSON)         Handshake (JSON)
                │                        │
┌───────────────▼────────┐      ┌────────▼───────────────┐
│     Android App        │◄────►│  Windows Desktop App   │
│  (Flutter Client)      │ WebRTC P2P Data & Video Stream│
│ - MediaProjection (Cap)│      │ - DesktopDuplication   │
│ - Accessibility (Touch)│      │ - Win32 SendInput      │
└────────────────────────┘      └────────────────────────┘
```

| Component | Technology | Role |
| :--- | :--- | :--- |
| **Cross-Platform UI** | Flutter 3.x (Dart) | Single codebase for Android & Windows GUI |
| **Realtime Stream** | WebRTC (`flutter_webrtc`) | H.264 Video stream + WebRTC DataChannel (Inputs) |
| **Signaling Server** | Node.js (`ws` library) | Device ID allocation & P2P connection exchange |
| **STUN Server** | Google STUN (`stun.l.google.com:19302`) | NAT Traversal / Direct P2P routing |
| **Windows Input Engine** | C++ Win32 API (`SendInput`) | Mouse click, move, wheel, keyboard injection |
| **Android Input Engine**| Kotlin `AccessibilityService` | Dispatch gestures, swipes, back/home navigation |

---

## 📦 5. Step-by-Step Implementation Roadmap

### Phase 1: Project Initialization
1. Naya Flutter project create karein:
   ```bash
   flutter create airlink_remote --platforms=windows,android
   ```
2. `pubspec.yaml` mein zaroori dependencies add karein:
   ```yaml
   dependencies:
     flutter:
       sdk: flutter
     flutter_webrtc: ^0.10.5
     web_socket_channel: ^2.4.1
     uuid: ^4.3.3
     shared_preferences: ^2.2.2
     flutter_screenutil: ^5.9.0
     provider: ^6.1.2
   ```

### Phase 2: Signaling Server (Node.js)
1. Server directory: `server/server.js`.
2. Har connecting device ko unique **9-digit Device ID** allocate karta hai.
3. WebRTC Offer, Answer, aur ICE Candidates relay karta hai.

### Phase 3: Modern UI/UX (OSLink Dashboard)
1. **Device Card:** Apna Device ID aur 4-digit Security PIN display karega.
2. **Connect Box:** Target Device ID aur PIN enter karke "Connect" button dabane ka option.
3. **Devices List:** Previously connected devices ki history.
4. **Remote Player Screen:**
   - Full-screen interactive video player.
   - Top floating toolbar (Quality, Fullscreen, Keyboard, Disconnect).

### Phase 4: Video Streaming Engine
1. **Windows -> Android:** Windows par Screen Capture start ho kar WebRTC Video Track mein encode hoti hai aur phone par display hoti hai.
2. **Android -> Windows:** Android par `MediaProjectionManager` permission prompt aati hai aur phone ki screen capture ho kar PC par aati hai.

### Phase 5: Input Transmission & Injection (Zero-Lag)
WebRTC DataChannel (`inputs_channel`) ke zariye real-time JSON packets send hote hain:

#### Mouse / Touch Event Packet Schema:
```json
{
  "type": "mouse_move",
  "x": 0.542,
  "y": 0.321
}
```
```json
{
  "type": "mouse_click",
  "button": "left",
  "action": "down"
}
```
```json
{
  "type": "key_press",
  "keyCode": 65,
  "modifiers": ["ctrl"]
}
```

---

## 💻 6. Production Ready Code Templates

### 6.1 Signaling Server (`server/server.js`)
```javascript
const WebSocket = require('ws');
const server = new WebSocket.Server({ port: 8080 });

const clients = new Map(); // deviceId -> socket

function generateDeviceId() {
  return Math.floor(100000000 + Math.random() * 900000000).toString();
}

server.on('connection', (ws) => {
  let currentDeviceId = generateDeviceId();
  clients.set(currentDeviceId, ws);
  
  ws.send(JSON.stringify({ type: 'registered', deviceId: currentDeviceId }));
  console.log(`Device connected: ${currentDeviceId}`);

  ws.on('message', (message) => {
    try {
      const data = JSON.parse(message);
      const targetWs = clients.get(data.targetId);

      if (targetWs && targetWs.readyState === WebSocket.OPEN) {
        data.senderId = currentDeviceId;
        targetWs.send(JSON.stringify(data));
      } else if (data.type === 'call_request') {
        ws.send(JSON.stringify({ type: 'error', message: 'Target device offline' }));
      }
    } catch (e) {
      console.error('Error handling message:', e);
    }
  });

  ws.on('close', () => {
    clients.delete(currentDeviceId);
    console.log(`Device disconnected: ${currentDeviceId}`);
  });
});

console.log('Signaling Server running on ws://0.0.0.0:8080');
```

---

### 6.2 Windows Native Mouse/Keyboard Injector (`windows/runner/input_injector.cpp`)
```cpp
#include <windows.h>
#include <flutter/method_channel.h>

void InjectMouseMove(double normX, double normY) {
    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);

    INPUT input = { 0 };
    input.type = INPUT_MOUSE;
    input.mi.dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE;
    input.mi.dx = (LONG)(normX * 65535.0f);
    input.mi.dy = (LONG)(normY * 65535.0f);
    SendInput(1, &input, sizeof(INPUT));
}

void InjectMouseClick(bool isDown, bool isLeft) {
    INPUT input = { 0 };
    input.type = INPUT_MOUSE;
    input.mi.dwFlags = isLeft ? 
        (isDown ? MOUSEEVENTF_LEFTDOWN : MOUSEEVENTF_LEFTUP) :
        (isDown ? MOUSEEVENTF_RIGHTDOWN : MOUSEEVENTF_RIGHTUP);
    SendInput(1, &input, sizeof(INPUT));
}
```

---

### 6.3 Android Accessibility Gesture Dispatcher (`android/app/src/main/kotlin/.../RemoteAccessibilityService.kt`)
```kotlin
package com.example.airlink_remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class RemoteAccessibilityService : AccessibilityService() {
    companion object {
        var instance: RemoteAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun performClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
```

---

### 6.4 WebRTC Manager (Dart / Flutter)
```dart
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'dart:convert';

class RemoteSessionManager {
  RTCPeerConnection? peerConnection;
  RTCDataChannel? inputChannel;
  final RTCVideoRenderer remoteRenderer = RTCVideoRenderer();
  late WebSocketChannel socket;

  final Map<String, dynamic> rtcConfig = {
    'iceServers': [
      {'urls': 'stun:stun.l.google.com:19302'},
    ]
  };

  Future<void> initialize(String serverUrl) async {
    await remoteRenderer.initialize();
    socket = WebSocketChannel.connect(Uri.parse(serverUrl));
    socket.stream.listen(_handleSignalingMessage);
  }

  void _handleSignalingMessage(dynamic event) async {
    final msg = jsonDecode(event);
    switch (msg['type']) {
      case 'offer':
        await _handleOffer(msg['offer'], msg['senderId']);
        break;
      case 'answer':
        await peerConnection?.setRemoteDescription(
          RTCSessionDescription(msg['answer']['sdp'], msg['answer']['type'])
        );
        break;
      case 'candidate':
        await peerConnection?.addCandidate(
          RTCIceCandidate(
            msg['candidate']['candidate'],
            msg['candidate']['sdpMid'],
            msg['candidate']['sdpMLineIndex'],
          )
        );
        break;
    }
  }

  Future<void> _handleOffer(Map<String, dynamic> offer, String senderId) async {
    peerConnection = await createPeerConnection(rtcConfig);
    peerConnection?.onTrack = (event) {
      if (event.track.kind == 'video') {
        remoteRenderer.srcObject = event.streams[0];
      }
    };
    
    peerConnection?.onDataChannel = (channel) {
      inputChannel = channel;
      inputChannel?.onMessage = (message) {
        // Parse touch / mouse click packet and inject native input
      };
    };

    await peerConnection?.setRemoteDescription(
      RTCSessionDescription(offer['sdp'], offer['type'])
    );
    final answer = await peerConnection!.createAnswer();
    await peerConnection!.setLocalDescription(answer);

    socket.sink.add(jsonEncode({
      'type': 'answer',
      'targetId': senderId,
      'answer': {'sdp': answer.sdp, 'type': answer.type}
    }));
  }

  void sendInput(Map<String, dynamic> inputData) {
    if (inputChannel != null && inputChannel!.state == RTCDataChannelState.RTCDataChannelOpen) {
      inputChannel!.send(RTCDataChannelMessage(jsonEncode(inputData)));
    }
  }
}
```

---

## 🔒 7. Security & Privacy Architecture
1. **End-to-End Encryption (DTLS-SRTP):** WebRTC media aur data streams military-grade AES-128/256 se hardware level par encrypted hoti hain.
2. **Access Authorization Dialog:** Jab bhi koi remote device connect hone ki koshish karegi, host screen par prompt aayega: *"Redmi Note 9 Pro wants to control your screen. Allow / Deny"*.
3. **Emergency Disconnect:** Host keyboard par `Ctrl + Alt + End` ya floating bubble par click karke remote session instantly terminate kiya ja sakega.

---

## 🧪 8. Verification & QA Checklist
Implementation mukammal hone ke baad following tests pass hone chahiye:

- [ ] **Test 1:** Signaling server run ho kar dono devices ko valid 9-digit Device ID assign kare.
- [ ] **Test 2:** PC se phone par call request bhejne par phone par accept dialog aana chahiye.
- [ ] **Test 3:** Video stream bina stutter ke 60 FPS aur low latency par render honi chahiye.
- [ ] **Test 4:** Mobile screen par tap karne par PC par mouse click aur move sahi coordinates par register ho.
- [ ] **Test 5:** PC mouse se mobile screen scroll aur click ho.
