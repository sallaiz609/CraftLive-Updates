const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("craftlive", {
  getState: () => ipcRenderer.invoke("craftlive:get-state"),
  connect: (username) => ipcRenderer.invoke("craftlive:connect", username),
  disconnect: () => ipcRenderer.invoke("craftlive:disconnect"),
  saveSettings: (settings) => ipcRenderer.invoke("craftlive:save-settings", settings),
  saveSlot: (slot) => ipcRenderer.invoke("craftlive:save-slot", slot),
  duplicateSlot: (slotId) => ipcRenderer.invoke("craftlive:duplicate-slot", slotId),
  resetSlot: (slotId) => ipcRenderer.invoke("craftlive:reset-slot", slotId),
  testSlot: (slotId) => ipcRenderer.invoke("craftlive:test-slot", slotId),
  clearLog: () => ipcRenderer.invoke("craftlive:clear-log"),
  exportConfig: () => ipcRenderer.invoke("craftlive:export-config"),
  importConfig: () => ipcRenderer.invoke("craftlive:import-config"),
  checkUpdate: (manifestUrl) => ipcRenderer.invoke("craftlive:check-update", manifestUrl),
  installUpdate: (featureSelections) => ipcRenderer.invoke("craftlive:install-update", featureSelections),
  openExternal: (url) => ipcRenderer.invoke("craftlive:open-external", url),
  onState: (callback) => {
    const listener = (_event, state) => callback(state);
    ipcRenderer.on("craftlive:state", listener);
    return () => ipcRenderer.removeListener("craftlive:state", listener);
  }
});
