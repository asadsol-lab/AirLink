const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('airlinkNative', {
  getConfig: () => ipcRenderer.invoke('get-config'),
  saveConfig: (config) => ipcRenderer.invoke('save-config', config),
  getScreenSources: () => ipcRenderer.invoke('get-screen-sources'),
  injectInput: (packet) => ipcRenderer.send('inject-input', packet),
  onEmergencyDisconnect: (cb) => {
    const handler = () => cb();
    ipcRenderer.on('emergency-disconnect', handler);
    return () => ipcRenderer.removeListener('emergency-disconnect', handler);
  }
});
