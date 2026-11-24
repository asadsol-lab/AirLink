const { app, BrowserWindow, ipcMain, desktopCapturer, globalShortcut } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');

let mainWindow = null;
let win32Input = null;

try {
  win32Input = require('./build/Release/airlink_win32_input.node');
  console.log('[AirLink] Loaded native Win32 SendInput addon.');
} catch (err) {
  console.warn('[AirLink] Native addon not yet built. Run "npm run build-native" on Windows:', err.message);
}

const configPath = path.join(app.getPath('userData'), 'airlink-windows-config.json');

function loadOrCreateConfig() {
  try {
    if (fs.existsSync(configPath)) {
      return JSON.parse(fs.readFileSync(configPath, 'utf8'));
    }
  } catch (_) {}
  const initial = {
    deviceId: Math.floor(100000000 + Math.random() * 900000000).toString(),
    securityPin: Math.floor(1000 + Math.random() * 9000).toString(),
    signalingUrl: 'ws://127.0.0.1:8080',
    deviceName: os.hostname() || 'Windows PC',
    requirePinApproval: true
  };
  fs.writeFileSync(configPath, JSON.stringify(initial, null, 2));
  return initial;
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1120,
    height: 760,
    minWidth: 880,
    minHeight: 600,
    backgroundColor: '#070B12',
    title: 'AirLink Desktop',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'index.html'));
}

app.whenReady().then(() => {
  createWindow();

  // PRD Section 7: Emergency Disconnect global hotkey Ctrl + Alt + End
  globalShortcut.register('CommandOrControl+Alt+End', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('emergency-disconnect');
    }
  });

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('will-quit', () => {
  globalShortcut.unregisterAll();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

ipcMain.handle('get-config', () => loadOrCreateConfig());

ipcMain.handle('save-config', (_event, updated) => {
  const current = loadOrCreateConfig();
  const merged = { ...current, ...updated };
  fs.writeFileSync(configPath, JSON.stringify(merged, null, 2));
  return merged;
});

ipcMain.handle('get-screen-sources', async () => {
  const sources = await desktopCapturer.getSources({ types: ['screen'] });
  return sources.map((s) => ({ id: s.id, name: s.name }));
});

ipcMain.on('inject-input', (_event, packet) => {
  if (!win32Input || !packet) return;
  try {
    switch (packet.type) {
      case 'mouse_move':
        win32Input.injectMouseMove(Number(packet.x), Number(packet.y));
        break;
      case 'mouse_click':
        if (typeof packet.x === 'number' && typeof packet.y === 'number' && packet.x >= 0 && packet.y >= 0) {
          win32Input.injectMouseMove(packet.x, packet.y);
        }
        win32Input.injectMouseClick(String(packet.button || 'left'), String(packet.action || 'click'));
        break;
      case 'mouse_scroll':
        if (typeof packet.x === 'number' && typeof packet.y === 'number') {
          win32Input.injectMouseMove(packet.x, packet.y);
        }
        win32Input.injectMouseScroll(Number(packet.deltaX || 0), Number(packet.deltaY || 0));
        break;
      case 'key_press':
        if (
          Number(packet.keyCode) === 35 &&
          Array.isArray(packet.modifiers) &&
          packet.modifiers.includes('ctrl') &&
          packet.modifiers.includes('alt')
        ) {
          if (mainWindow && !mainWindow.isDestroyed()) {
            mainWindow.webContents.send('emergency-disconnect');
          }
          return;
        }
        win32Input.injectKeyPress(
          Number(packet.keyCode || 0),
          Array.isArray(packet.modifiers) ? packet.modifiers.map(String) : [],
          String(packet.action || 'press')
        );
        break;
      case 'text_input':
        win32Input.injectTextInput(String(packet.text || ''));
        break;
    }
  } catch (err) {
    console.error('[AirLink] Win32 SendInput error:', err);
  }
});
