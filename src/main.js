import { app, BrowserWindow, clipboard, dialog, ipcMain, net, shell } from "electron";
import electronUpdater from "electron-updater";
import { spawn } from "node:child_process";
import { randomBytes } from "node:crypto";
import { existsSync } from "node:fs";
import { readFile, rename, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { MOB_CATALOGS } from "./mob-catalog.js";
import { DEFAULT_UPDATE_MANIFEST_URL } from "./update-config.js";
import {
  ControlEvent,
  TikTokLiveConnection,
  WebcastEvent
} from "tiktok-live-connector";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SLOT_COUNT = 20;
const CONFIG_VERSION = 3;
const LOG_LIMIT = 250;
const QUEUE_LIMIT = 1000;
const GLOBAL_INTERACTION_INTERVAL_MS = 5000;
const UI_BROADCAST_INTERVAL_MS = 150;
const LIVE_RETRY_INTERVAL_MS = 15000;
const LIVE_RECONNECT_DELAY_MS = 5000;
const DEFAULT_MINECRAFT_VERSION = "1.20.1";
const MAX_MANIFEST_BYTES = 64 * 1024;
const UPDATE_CHECK_INTERVAL_MS = 10 * 60 * 1000;
const SUPPORTER_TIKTOK_ACCOUNT = "venom_hun_";
const { autoUpdater } = electronUpdater;

let mainWindow = null;
let liveConnection = null;
let liveRetryTimer = null;
let liveMonitorActive = false;
let liveAttemptInProgress = false;
let waitingNoticeShown = false;
let configPath = "";
let config = null;
let commandQueue = [];
let activeInteractionId = "";
let lastInteractionFinishedAt = 0;
let queueFullLogAt = 0;
let stateBroadcastTimer = null;
let lastStateBroadcastAt = 0;
let logs = [];
let recentGifts = [];
let cooldowns = new Map();
let likeCounters = new Map();
let eventLogTimes = new Map();
let minecraftWindowDetected = false;
let keyboardBusy = false;
let minecraftStatusKnown = false;
let tiktokStatus = "offline";
let tiktokRoomId = null;
let lastError = "";
let appLanguage = "hu";
let availableUpdate = null;
let installerUpdaterConfigured = false;
let installerUpdateRequested = false;
let updateCheckPromise = null;
let lastAnnouncedUpdateVersion = "";
let updateState = {
  status: "idle",
  availableVersion: "",
  notes: "",
  features: [],
  progress: 0,
  error: ""
};
let supporterSubscriptionCount = 0;
let lastSupporterSubscriber = null;

function mt(hungarian, english) {
  return appLanguage === "en" ? english : hungarian;
}

const triggerDefaults = {
  gift: { value: "", mode: "exact", minCount: 1 },
  like: { value: "", mode: "exact", minCount: 100 },
  follow: { value: "", mode: "exact", minCount: 1 },
  share: { value: "", mode: "exact", minCount: 1 },
  chat: { value: "!boom", mode: "exact", minCount: 1 },
  member: { value: "", mode: "exact", minCount: 1 },
  subscribe: { value: "", mode: "exact", minCount: 1 }
};

function blankSlot(index) {
  return {
    id: `slot-${index + 1}`,
    position: index,
    enabled: false,
    name: `${mt("Interakció", "Interaction")} ${index + 1}`,
    accent: ["lime", "cyan", "violet", "amber", "rose"][index % 5],
    trigger: { kind: "gift", ...triggerDefaults.gift },
    commands: "summon minecraft:zombie ~ ~1 ~",
    cooldownMs: 0,
    commandDelayMs: 250,
    chance: 100
  };
}

function makeDefaultConfig() {
  const slots = Array.from({ length: SLOT_COUNT }, (_, index) => blankSlot(index));
  const examples = [
    {
      name: mt("Rózsa → zombi", "Rose → zombie"),
      trigger: { kind: "gift", value: "Rose", mode: "exact", minCount: 1 },
      commands: `title @a actionbar {"text":"${mt("{user} zombit küldött!", "{user} sent a zombie!")}","color":"green"}\nsummon minecraft:zombie ~ ~1 ~`
    },
    {
      name: mt("100 like → villám", "100 likes → lightning"),
      trigger: { kind: "like", value: "", mode: "exact", minCount: 100 },
      commands: "summon minecraft:lightning_bolt ~ ~ ~"
    },
    {
      name: mt("Követés → gyógyítás", "Follow → healing"),
      trigger: { kind: "follow", value: "", mode: "exact", minCount: 1 },
      commands: "effect give @a minecraft:regeneration 8 1 true"
    }
  ];
  examples.forEach((example, index) => Object.assign(slots[index], example));
  return {
    version: CONFIG_VERSION,
    username: "",
    autoConnect: false,
    updateManifestUrl: DEFAULT_UPDATE_MANIFEST_URL,
    featureFlags: {},
    minecraftVersion: DEFAULT_MINECRAFT_VERSION,
    slots
  };
}

function normalizeSlot(raw, position) {
  const base = blankSlot(position);
  const allowedKinds = Object.keys(triggerDefaults);
  const kind = allowedKinds.includes(raw?.trigger?.kind) ? raw.trigger.kind : "gift";
  return {
    ...base,
    ...raw,
    id: base.id,
    position,
    enabled: Boolean(raw?.enabled),
    name: String(raw?.name || base.name).slice(0, 60),
    accent: ["lime", "cyan", "violet", "amber", "rose"].includes(raw?.accent)
      ? raw.accent
      : base.accent,
    trigger: {
      kind,
      value: String(raw?.trigger?.value || "").slice(0, 120),
      mode: raw?.trigger?.mode === "contains" ? "contains" : "exact",
      minCount: clampNumber(raw?.trigger?.minCount, 1, 100000, triggerDefaults[kind].minCount)
    },
    commands: String(raw?.commands || base.commands).slice(0, 20000),
    cooldownMs: clampNumber(raw?.cooldownMs, 0, 3600000, 0),
    commandDelayMs: clampNumber(raw?.commandDelayMs, 0, 60000, 250),
    chance: clampNumber(raw?.chance, 1, 100, 100)
  };
}

function normalizeConfig(raw) {
  const rawSlots = Array.isArray(raw?.slots) ? raw.slots : [];
  return {
    version: CONFIG_VERSION,
    username: String(raw?.username || "").replace(/^@/, "").trim().slice(0, 80),
    autoConnect: Boolean(raw?.autoConnect),
    updateManifestUrl: normalizeUpdateManifestUrl(raw?.updateManifestUrl || DEFAULT_UPDATE_MANIFEST_URL, false),
    featureFlags: normalizeFeatureFlags(raw?.featureFlags),
    minecraftVersion: normalizeMinecraftVersion(raw?.minecraftVersion),
    slots: Array.from({ length: SLOT_COUNT }, (_, index) => normalizeSlot(rawSlots[index], index))
  };
}

function clampNumber(value, min, max, fallback) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, Math.round(parsed)));
}

function normalizeMinecraftVersion(value) {
  const version = String(value || "").trim().slice(0, 20);
  if (version === "latest") return version;
  const classicMatch = version.match(/^1\.(\d+)(?:\.(\d+))?$/);
  if (classicMatch && Number(classicMatch[1]) >= 7) return version;
  if (/^(?:2[6-9]|[3-9]\d)(?:\.\d+){0,2}$/.test(version)) return version;
  return DEFAULT_MINECRAFT_VERSION;
}

function normalizeUpdateManifestUrl(value, required = true) {
  const candidate = String(value || "").trim().slice(0, 1000);
  if (!candidate && !required) return "";
  try {
    const parsed = new URL(candidate);
    if (parsed.protocol !== "https:" || parsed.username || parsed.password) throw new Error();
    return parsed.href;
  } catch {
    if (!candidate && required) throw new Error(mt("Add meg a frissítési leíró HTTPS-címét.", "Enter the HTTPS update manifest URL."));
    if (candidate) throw new Error(mt("A frissítési címnek érvényes HTTPS-címnek kell lennie.", "The update address must be a valid HTTPS URL."));
    return "";
  }
}

function normalizeFeatureFlags(raw) {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return {};
  return Object.fromEntries(
    Object.entries(raw)
      .filter(([id]) => /^[a-z0-9][a-z0-9._-]{0,79}$/i.test(id))
      .slice(0, 100)
      .map(([id, enabled]) => [id, Boolean(enabled)])
  );
}

async function loadConfig() {
  configPath = path.join(app.getPath("userData"), "config.json");
  try {
    const raw = JSON.parse(await readFile(configPath, "utf8"));
    config = normalizeConfig(raw);
  } catch {
    config = makeDefaultConfig();
    await saveConfig();
  }
}

async function saveConfig() {
  const tempPath = `${configPath}.tmp`;
  await writeFile(tempPath, `${JSON.stringify(config, null, 2)}\n`, "utf8");
  await rename(tempPath, configPath);
}

function publicState() {
  const mobCatalog = resolveMobCatalog(config.minecraftVersion);
  return {
    config,
    logs,
    recentGifts,
    language: appLanguage,
    mobs: mobCatalog.mobs,
    minecraftCatalogVersion: mobCatalog.version,
    update: {
      currentVersion: app.getVersion(),
      ...updateState
    },
    supporterSubscriptions: {
      enabled: Boolean(config.featureFlags["supporter-subscriptions"]),
      account: SUPPORTER_TIKTOK_ACCOUNT,
      count: supporterSubscriptionCount,
      lastSubscriber: lastSupporterSubscriber
    },
    status: {
      tiktok: tiktokStatus,
      roomId: tiktokRoomId,
      minecraft: minecraftWindowDetected ? "online" : "offline",
      minecraftStatusKnown,
      queuedCommands: commandQueue.length,
      queuedInteractions: new Set(commandQueue.map((item) => item.interactionId)).size,
      fixedInteractionIntervalMs: GLOBAL_INTERACTION_INTERVAL_MS,
      lastError
    }
  };
}

function versionParts(version) {
  return String(version).split(".").map((part) => Number(part) || 0);
}

function compareMinecraftVersions(left, right) {
  const a = versionParts(left);
  const b = versionParts(right);
  for (let index = 0; index < Math.max(a.length, b.length); index += 1) {
    if ((a[index] || 0) !== (b[index] || 0)) return (a[index] || 0) - (b[index] || 0);
  }
  return 0;
}

function resolveMobCatalog(requestedVersion) {
  const versions = Object.keys(MOB_CATALOGS).sort(compareMinecraftVersions);
  if (!versions.length) return { version: "", mobs: [] };
  if (requestedVersion === "latest") {
    const version = versions.at(-1);
    return { version, mobs: MOB_CATALOGS[version] };
  }
  const compatible = versions.filter((version) => compareMinecraftVersions(version, requestedVersion) <= 0);
  const version = compatible.at(-1) || versions[0];
  return { version, mobs: MOB_CATALOGS[version] };
}

function compareAppVersions(left, right) {
  const parse = (value) => String(value).split(".").map((part) => Number.parseInt(part, 10) || 0);
  const a = parse(left);
  const b = parse(right);
  for (let index = 0; index < Math.max(a.length, b.length); index += 1) {
    if ((a[index] || 0) !== (b[index] || 0)) return (a[index] || 0) - (b[index] || 0);
  }
  return 0;
}

function setUpdateState(next) {
  updateState = { ...updateState, ...next };
  broadcastState();
}

function validateUpdateManifest(raw, manifestUrl) {
  const version = String(raw?.version || "").trim();
  if (!/^\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?$/.test(version)) {
    throw new Error(mt("A frissítési leíróban hibás a verziószám.", "The update manifest contains an invalid version number."));
  }
  let downloadUrl;
  try {
    downloadUrl = new URL(String(raw?.downloadUrl || ""), manifestUrl);
  } catch {
    throw new Error(mt("A frissítési csomag címe hibás.", "The update package URL is invalid."));
  }
  if (downloadUrl.protocol !== "https:" || downloadUrl.username || downloadUrl.password) {
    throw new Error(mt("A frissítési csomagnak HTTPS-címen kell lennie.", "The update package must use an HTTPS URL."));
  }
  const sha256 = String(raw?.sha256 || "").trim().toLocaleLowerCase();
  if (!/^[a-f0-9]{64}$/.test(sha256)) {
    throw new Error(mt("A frissítési leíróból hiányzik az érvényes SHA-256 ellenőrzőösszeg.", "The update manifest is missing a valid SHA-256 checksum."));
  }
  const seenFeatures = new Set();
  const features = (Array.isArray(raw?.features) ? raw.features : []).slice(0, 20).flatMap((feature) => {
    const id = String(feature?.id || "").trim();
    if (!/^[a-z0-9][a-z0-9._-]{0,79}$/i.test(id) || seenFeatures.has(id)) return [];
    seenFeatures.add(id);
    const name = String(appLanguage === "hu" ? feature?.nameHu || feature?.name || id : feature?.nameEn || feature?.name || id).slice(0, 120);
    const description = String(appLanguage === "hu" ? feature?.descriptionHu || feature?.description || "" : feature?.descriptionEn || feature?.description || "").slice(0, 500);
    return [{ id, name, description, defaultEnabled: Boolean(feature?.defaultEnabled) }];
  });
  return {
    version,
    downloadUrl: downloadUrl.href,
    sha256,
    notes: String(appLanguage === "hu" ? raw?.notesHu || raw?.notes || "" : raw?.notesEn || raw?.notes || "").slice(0, 3000),
    features
  };
}

async function fetchUpdateManifest(urlInput = config.updateManifestUrl) {
  const manifestUrl = normalizeUpdateManifestUrl(urlInput);
  const response = await net.fetch(manifestUrl, { cache: "no-store" });
  if (!response.ok) throw new Error(`${mt("A frissítési kiszolgáló válasza", "Update server response")}: HTTP ${response.status}`);
  const announcedSize = Number(response.headers.get("content-length") || 0);
  if (announcedSize > MAX_MANIFEST_BYTES) throw new Error(mt("A frissítési leíró túl nagy.", "The update manifest is too large."));
  const body = (await response.text()).replace(/^\uFEFF/, "");
  if (Buffer.byteLength(body, "utf8") > MAX_MANIFEST_BYTES) throw new Error(mt("A frissítési leíró túl nagy.", "The update manifest is too large."));
  return validateUpdateManifest(JSON.parse(body), manifestUrl);
}

function installerUpdaterAvailable() {
  return process.platform === "win32" && app.isPackaged && existsSync(path.join(process.resourcesPath, "app-update.yml"));
}

function updateReleaseNotes(info) {
  if (typeof info?.releaseNotes === "string") return info.releaseNotes.slice(0, 3000);
  if (!Array.isArray(info?.releaseNotes)) return "";
  return info.releaseNotes
    .map((item) => typeof item === "string" ? item : item?.note || "")
    .filter(Boolean)
    .join("\n\n")
    .slice(0, 3000);
}

async function featureMetadataFor(version) {
  try {
    const manifest = await fetchUpdateManifest(config.updateManifestUrl || DEFAULT_UPDATE_MANIFEST_URL);
    return manifest.version === version ? manifest : null;
  } catch {
    return null;
  }
}

function configureInstallerUpdater() {
  if (installerUpdaterConfigured || !installerUpdaterAvailable()) return;
  installerUpdaterConfigured = true;
  autoUpdater.autoDownload = false;
  autoUpdater.autoInstallOnAppQuit = true;

  autoUpdater.on("download-progress", (progress) => {
    if (!installerUpdateRequested) return;
    setUpdateState({ status: "downloading", progress: Math.max(0, Math.min(99, Math.round(progress?.percent || 0))), error: "" });
  });

  autoUpdater.on("update-downloaded", () => {
    if (!installerUpdateRequested) return;
    setUpdateState({ status: "installing", progress: 100, error: "" });
    setTimeout(() => autoUpdater.quitAndInstall(true, true), 700);
  });

  autoUpdater.on("error", (error) => {
    if (!installerUpdateRequested) return;
    installerUpdateRequested = false;
    const message = error?.message || String(error);
    if (updateState.error !== message) {
      setUpdateState({ status: "error", progress: 0, error: message });
      addLog("error", mt("A frissítés telepítése nem sikerült", "Update installation failed"), message);
    }
  });
}

async function checkForInstallerUpdates(manual = false) {
  if (updateCheckPromise) return updateCheckPromise;
  updateCheckPromise = (async () => {
    setUpdateState({ status: "checking", availableVersion: "", notes: "", features: [], progress: 0, error: "" });
    availableUpdate = null;
    installerUpdateRequested = false;
    try {
      const result = await autoUpdater.checkForUpdates();
      const info = result?.updateInfo;
      if (info?.version && compareAppVersions(info.version, app.getVersion()) > 0) {
        const metadata = await featureMetadataFor(info.version);
        availableUpdate = {
          version: info.version,
          installerManaged: true,
          notes: metadata?.notes || updateReleaseNotes(info),
          features: metadata?.features || []
        };
        setUpdateState({
          status: "available",
          availableVersion: availableUpdate.version,
          notes: availableUpdate.notes,
          features: availableUpdate.features,
          progress: 0,
          error: ""
        });
        if (lastAnnouncedUpdateVersion !== availableUpdate.version) {
          lastAnnouncedUpdateVersion = availableUpdate.version;
          addLog("status", `${mt("Új CraftLive-frissítés érhető el", "A new CraftLive update is available")}: ${availableUpdate.version}`, availableUpdate.notes);
        }
      } else {
        lastAnnouncedUpdateVersion = "";
        setUpdateState({ status: "current", availableVersion: "", notes: "", features: [], progress: 0, error: "" });
        if (manual) addLog("status", mt("A CraftLive naprakész", "CraftLive is up to date"), `v${app.getVersion()}`);
      }
      return publicState();
    } catch (error) {
      const message = error?.message || String(error);
      setUpdateState({ status: "error", availableVersion: "", notes: "", features: [], progress: 0, error: message });
      if (manual) addLog("error", mt("A frissítés ellenőrzése nem sikerült", "Update check failed"), message);
      throw new Error(message);
    } finally {
      updateCheckPromise = null;
    }
  })();
  return updateCheckPromise;
}

async function checkForPortableUpdates(urlInput = config.updateManifestUrl, manual = false) {
  setUpdateState({ status: "checking", availableVersion: "", notes: "", features: [], progress: 0, error: "" });
  availableUpdate = null;
  try {
    const manifest = await fetchUpdateManifest(urlInput);
    if (compareAppVersions(manifest.version, app.getVersion()) > 0) {
      availableUpdate = manifest;
      setUpdateState({ status: "available", availableVersion: manifest.version, notes: manifest.notes, features: manifest.features, progress: 0, error: "" });
      if (lastAnnouncedUpdateVersion !== manifest.version) {
        lastAnnouncedUpdateVersion = manifest.version;
        addLog("status", `${mt("Új CraftLive-frissítés érhető el", "A new CraftLive update is available")}: ${manifest.version}`, manifest.notes);
      }
    } else {
      lastAnnouncedUpdateVersion = "";
      setUpdateState({ status: "current", availableVersion: "", notes: "", features: [], progress: 0, error: "" });
      if (manual) addLog("status", mt("A CraftLive naprakész", "CraftLive is up to date"), `v${app.getVersion()}`);
    }
    return publicState();
  } catch (error) {
    const message = error instanceof SyntaxError
      ? mt("A frissítési leíró nem érvényes JSON-fájl.", "The update manifest is not valid JSON.")
      : error?.message || String(error);
    setUpdateState({ status: "error", availableVersion: "", notes: "", features: [], progress: 0, error: message });
    if (manual) addLog("error", mt("A frissítés ellenőrzése nem sikerült", "Update check failed"), message);
    throw new Error(message);
  }
}

async function checkForUpdates(urlInput = config.updateManifestUrl, manual = false) {
  if (installerUpdaterAvailable()) return checkForInstallerUpdates(manual);
  return checkForPortableUpdates(urlInput || DEFAULT_UPDATE_MANIFEST_URL, manual);
}

async function installAvailableUpdate(featureSelections) {
  if (!availableUpdate || compareAppVersions(availableUpdate.version, app.getVersion()) <= 0) {
    throw new Error(mt("Nincs telepíthető új frissítés.", "No new update is ready to install."));
  }
  const selections = featureSelections && typeof featureSelections === "object" ? featureSelections : {};
  for (const feature of availableUpdate.features) {
    config.featureFlags[feature.id] = Object.hasOwn(selections, feature.id) && Boolean(selections[feature.id]);
  }
  await saveConfig();

  if (availableUpdate.installerManaged && installerUpdaterAvailable()) {
    installerUpdateRequested = true;
    setUpdateState({ status: "downloading", progress: 0, error: "" });
    try {
      await autoUpdater.downloadUpdate();
      return publicState();
    } catch (error) {
      installerUpdateRequested = false;
      const message = error?.message || String(error);
      if (updateState.error !== message) {
        setUpdateState({ status: "error", progress: 0, error: message });
        addLog("error", mt("A frissítés telepítése nem sikerült", "Update installation failed"), message);
      }
      throw new Error(message);
    }
  }

  throw new Error(mt(
    "Az automatikus frissítéshez a telepített CraftLive szükséges. Futtasd egyszer a hivatalos Setup EXE-t.",
    "Automatic updates require the installed CraftLive app. Run the official Setup EXE once."
  ));
}

function broadcastState() {
  if (!mainWindow || mainWindow.isDestroyed() || stateBroadcastTimer) return;
  const remaining = UI_BROADCAST_INTERVAL_MS - (Date.now() - lastStateBroadcastAt);
  if (remaining <= 0) {
    lastStateBroadcastAt = Date.now();
    mainWindow.webContents.send("craftlive:state", publicState());
    return;
  }
  stateBroadcastTimer = setTimeout(() => {
    stateBroadcastTimer = null;
    if (!mainWindow || mainWindow.isDestroyed()) return;
    lastStateBroadcastAt = Date.now();
    mainWindow.webContents.send("craftlive:state", publicState());
  }, remaining);
  stateBroadcastTimer.unref?.();
}

function addLog(type, message, details = "") {
  logs.unshift({
    id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
    time: new Date().toISOString(),
    type,
    message: String(message).slice(0, 240),
    details: String(details || "").slice(0, 400)
  });
  logs = logs.slice(0, LOG_LIMIT);
  broadcastState();
}

function safeText(value) {
  return String(value ?? "")
    .replace(/[\r\n\t]/g, " ")
    .replace(/[\\\"'`;]/g, "")
    .replace(/[^\p{L}\p{N}_. @+\-]/gu, "")
    .trim()
    .slice(0, 80);
}

function commandContext(event) {
  return {
    user: safeText(event.user || mt("néző", "viewer")),
    nickname: safeText(event.nickname || event.user || mt("néző", "viewer")),
    gift: safeText(event.giftName || event.giftId || mt("esemény", "event")),
    amount: String(clampNumber(event.count, 1, 100000, 1)),
    coins: String(clampNumber(event.coins, 0, 100000000, 0)),
    comment: safeText(event.comment || "")
  };
}

function renderCommands(slot, event) {
  const context = commandContext(event);
  return slot.commands
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"))
    .slice(0, 50)
    .map((line) => {
      let command = line.replace(/^\/+/, "");
      for (const [key, value] of Object.entries(context)) {
        command = command.replaceAll(`{${key}}`, value);
      }
      return command.slice(0, 512);
    })
    .filter(Boolean);
}

function enqueueSlot(slot, event, isTest = false) {
  if (!isTest && Math.random() * 100 >= slot.chance) {
    addLog("skip", `${slot.name}: ${mt("a véletlen esély miatt kimaradt", "skipped by random chance")}`);
    return false;
  }

  const now = Date.now();
  const lastRun = cooldowns.get(slot.id) || 0;
  if (!isTest && now - lastRun < slot.cooldownMs) {
    addLog("skip", `${slot.name}: ${mt("várakozási idő aktív", "cooldown is active")}`);
    return false;
  }

  const commands = renderCommands(slot, event);
  if (!commands.length) {
    addLog("error", `${slot.name}: ${mt("nincs végrehajtható parancs", "no executable action")}`);
    return false;
  }

  if (commandQueue.length + commands.length > QUEUE_LIMIT) {
    if (now - queueFullLogAt >= GLOBAL_INTERACTION_INTERVAL_MS) {
      queueFullLogAt = now;
      addLog(
        "skip",
        `${slot.name}: ${mt("az interakciós sor megtelt", "the interaction queue is full")}`,
        mt("A stabilitás érdekében az új esemény kimaradt.", "The new event was skipped to protect stability.")
      );
    }
    return false;
  }

  const interactionId = randomBytes(8).toString("hex");
  cooldowns.set(slot.id, now);
  commands.forEach((command, index) => {
    commandQueue.push({
      id: randomBytes(8).toString("hex"),
      interactionId,
      slotId: slot.id,
      slotName: slot.name,
      command,
      commandDelayMs: slot.commandDelayMs,
      notBefore: index === 0 ? now : Number.MAX_SAFE_INTEGER
    });
  });
  addLog(
    isTest ? "test" : "action",
    `${slot.name} ${mt("sorba állítva", "queued")}`,
    isTest
      ? `${mt("LIVE nélkül", "Without LIVE")} • Minecraft ${config.minecraftVersion === "latest" ? mt("1.21.5+ / legújabb", "1.21.5+ / latest") : config.minecraftVersion} • ${commands.length} ${mt("parancs", "actions")} • ${mt("fix 5 mp védelem", "fixed 5 s protection")}`
      : `${event.user || mt("Néző", "Viewer")} • ${commands.length} ${mt("parancs", "actions")} • ${mt("fix 5 mp védelem", "fixed 5 s protection")}`
  );
  return true;
}

function matchesText(actual, expected, mode) {
  if (!expected) return true;
  const left = String(actual || "").trim().toLocaleLowerCase("hu-HU");
  const right = String(expected).trim().toLocaleLowerCase("hu-HU");
  return mode === "contains" ? left.includes(right) : left === right;
}

function slotMatches(slot, event) {
  if (slot.trigger.kind !== event.kind) return false;
  const trigger = slot.trigger;
  if (event.kind === "gift") {
    const nameMatch = matchesText(event.giftName, trigger.value, trigger.mode);
    const idMatch = matchesText(event.giftId, trigger.value, trigger.mode);
    return (nameMatch || idMatch) && event.count >= trigger.minCount;
  }
  if (event.kind === "chat") {
    return matchesText(event.comment, trigger.value, trigger.mode);
  }
  return true;
}

function processLiveEvent(event) {
  const activeSlots = config.slots.filter((slot) => slot.enabled && slotMatches(slot, event));
  for (const slot of activeSlots) {
    if (event.kind === "like") {
      const total = (likeCounters.get(slot.id) || 0) + event.count;
      const threshold = Math.max(1, slot.trigger.minCount);
      const runs = Math.min(20, Math.floor(total / threshold));
      likeCounters.set(slot.id, total % threshold);
      for (let index = 0; index < runs; index += 1) enqueueSlot(slot, event);
    } else {
      enqueueSlot(slot, event);
    }
  }
  const now = Date.now();
  const noisyEvent = event.kind === "like" || event.kind === "member";
  const shouldLog = activeSlots.length > 0 || !noisyEvent || now - (eventLogTimes.get(event.kind) || 0) > 900;
  if (shouldLog) {
    eventLogTimes.set(event.kind, now);
    addLog(
      "event",
      `${eventLabel(event.kind)}: ${event.nickname || event.user || mt("ismeretlen", "unknown")}`,
      event.giftName || event.comment || (event.count > 1 ? `×${event.count}` : "")
    );
  }
}

function eventLabel(kind) {
  return {
    gift: mt("Ajándék", "Gift"),
    like: "Like",
    follow: mt("Követés", "Follow"),
    share: mt("Megosztás", "Share"),
    chat: mt("Komment", "Comment"),
    member: mt("Belépés", "Join"),
    subscribe: mt("Feliratkozás", "Subscribe")
  }[kind] || kind;
}

function userFrom(data) {
  const user = data?.user || data?.details?.user || {};
  return {
    user: user.uniqueId || user.displayId || "",
    nickname: user.nickname || user.nickName || user.uniqueId || ""
  };
}

function wireConnection(connection) {
  connection.on(WebcastEvent.GIFT, (data) => {
    const giftType = Number(data?.giftDetails?.giftType ?? data?.extendedGiftInfo?.giftType ?? 0);
    if (giftType === 1 && !data?.repeatEnd) return;
    const giftEvent = {
      kind: "gift",
      ...userFrom(data),
      giftId: String(data?.giftId ?? ""),
      giftName: data?.giftDetails?.giftName || data?.extendedGiftInfo?.name || "",
      count: clampNumber(data?.repeatCount, 1, 100000, 1),
      coins: clampNumber(
        (data?.extendedGiftInfo?.diamondCount || data?.giftDetails?.diamondCount || 0) *
          (data?.repeatCount || 1),
        0,
        100000000,
        0
      )
    };
    rememberGift(giftEvent);
    processLiveEvent(giftEvent);
  });
  connection.on(WebcastEvent.LIKE, (data) => {
    processLiveEvent({ kind: "like", ...userFrom(data), count: clampNumber(data?.likeCount, 1, 100000, 1) });
  });
  connection.on(WebcastEvent.FOLLOW, (data) => {
    processLiveEvent({ kind: "follow", ...userFrom(data), count: 1 });
  });
  connection.on(WebcastEvent.SHARE, (data) => {
    processLiveEvent({ kind: "share", ...userFrom(data), count: 1 });
  });
  connection.on(WebcastEvent.CHAT, (data) => {
    processLiveEvent({ kind: "chat", ...userFrom(data), comment: data?.comment || "", count: 1 });
  });
  connection.on(WebcastEvent.MEMBER, (data) => {
    processLiveEvent({ kind: "member", ...userFrom(data), count: 1 });
  });
  connection.on(WebcastEvent.SUB_NOTIFY || "subNotify", (data) => {
    const subscriber = userFrom(data);
    if (config.featureFlags["supporter-subscriptions"]) {
      supporterSubscriptionCount += 1;
      lastSupporterSubscriber = {
        username: safeText(subscriber.user),
        nickname: safeText(subscriber.nickname),
        time: new Date().toISOString()
      };
    }
    processLiveEvent({ kind: "subscribe", ...subscriber, count: 1 });
  });
  connection.on(ControlEvent.DISCONNECTED, () => {
    if (liveConnection !== connection || tiktokStatus !== "online") return;
    liveConnection = null;
    tiktokRoomId = null;
    if (liveMonitorActive) {
      addLog("status", mt("A TikTok LIVE-kapcsolat megszakadt", "TikTok LIVE connection was interrupted"), mt("Automatikus újracsatlakozás folyamatban", "Reconnecting automatically"));
      scheduleLiveRetry(LIVE_RECONNECT_DELAY_MS);
    } else {
      tiktokStatus = "offline";
      addLog("status", mt("A TikTok LIVE-kapcsolat megszakadt", "TikTok LIVE connection was interrupted"));
    }
  });
  connection.on(ControlEvent.ERROR, (error) => {
    if (liveConnection !== connection) return;
    lastError = safeText(error?.info || error?.message || mt("TikTok kapcsolati hiba", "TikTok connection error"));
    if (tiktokStatus === "online") addLog("error", mt("TikTok-kapcsolati hiba", "TikTok connection error"), lastError);
  });
  connection.on(WebcastEvent.STREAM_END, () => {
    if (liveConnection !== connection) return;
    liveConnection = null;
    tiktokRoomId = null;
    connection.disconnect().catch(() => {});
    addLog("status", mt("A LIVE véget ért", "The LIVE ended"), mt("A CraftLive vár a következő LIVE-ra", "CraftLive is waiting for the next LIVE"));
    scheduleLiveRetry(LIVE_RETRY_INTERVAL_MS);
  });
}

function rememberGift(event) {
  const key = String(event.giftId || event.giftName || "").toLocaleLowerCase("hu-HU");
  if (!key) return;
  recentGifts = recentGifts.filter((gift) => gift.key !== key);
  recentGifts.unshift({
    key,
    id: String(event.giftId || ""),
    name: String(event.giftName || event.giftId || mt("Ismeretlen ajándék", "Unknown gift")),
    coins: clampNumber(event.coins, 0, 100000000, 0),
    lastSeen: new Date().toISOString()
  });
  recentGifts = recentGifts.slice(0, 24);
}

function clearLiveRetryTimer() {
  if (liveRetryTimer) clearTimeout(liveRetryTimer);
  liveRetryTimer = null;
}

function scheduleLiveRetry(delay = LIVE_RETRY_INTERVAL_MS) {
  if (!liveMonitorActive) return;
  clearLiveRetryTimer();
  tiktokStatus = "waiting";
  tiktokRoomId = null;
  broadcastState();
  liveRetryTimer = setTimeout(() => {
    liveRetryTimer = null;
    attemptTikTokConnection().catch(() => {});
  }, delay);
  liveRetryTimer.unref?.();
}

async function attemptTikTokConnection() {
  if (!liveMonitorActive || liveAttemptInProgress) return publicState();
  liveAttemptInProgress = true;
  clearLiveRetryTimer();
  tiktokStatus = "connecting";
  lastError = "";
  broadcastState();
  const username = config.username;
  const connection = new TikTokLiveConnection(username, {
    processInitialData: false,
    enableExtendedGiftInfo: true,
    fetchRoomInfoOnConnect: true
  });
  liveConnection = connection;
  wireConnection(connection);
  try {
    const state = await connection.connect();
    if (liveConnection !== connection) return publicState();
    tiktokStatus = "online";
    tiktokRoomId = String(state?.roomId || "");
    waitingNoticeShown = false;
    addLog("status", `${mt("Kapcsolódva", "Connected")}: @${username}`, tiktokRoomId ? `${mt("Szoba", "Room")}: ${tiktokRoomId}` : "");
    return publicState();
  } catch (error) {
    if (liveConnection === connection) liveConnection = null;
    try {
      await connection.disconnect();
    } catch {
      // The connection may not have opened yet.
    }
    if (!liveMonitorActive) return publicState();
    lastError = "";
    if (!waitingNoticeShown) {
      waitingNoticeShown = true;
      addLog(
        "status",
        mt(`@${username} LIVE-ja még nem fut`, `@${username}'s LIVE is not running yet`),
        mt("A CraftLive 15 másodpercenként automatikusan újrapróbálja", "CraftLive retries automatically every 15 seconds")
      );
    }
    scheduleLiveRetry();
    return publicState();
  } finally {
    liveAttemptInProgress = false;
  }
}

async function connectTikTok(usernameInput) {
  const username = String(usernameInput || config.username || "").replace(/^@/, "").trim();
  if (!username) throw new Error(mt("Írd be a TikTok-felhasználónevet.", "Enter a TikTok username."));
  await disconnectTikTok();
  config.username = username.slice(0, 80);
  await saveConfig();
  liveMonitorActive = true;
  waitingNoticeShown = false;
  tiktokStatus = "connecting";
  lastError = "";
  addLog("status", `${mt("Automatikus LIVE-figyelés elindítva", "Automatic LIVE monitoring started")}: @${config.username}`);
  return attemptTikTokConnection();
}

async function disconnectTikTok() {
  liveMonitorActive = false;
  waitingNoticeShown = false;
  clearLiveRetryTimer();
  const connection = liveConnection;
  liveConnection = null;
  if (connection) {
    try {
      await connection.disconnect();
    } catch {
      // The socket may already be closed.
    }
  }
  tiktokStatus = "offline";
  tiktokRoomId = null;
  broadcastState();
}

function minecraftInputHelperPath() {
  return app.isPackaged
    ? path.join(process.resourcesPath, "input", "CraftLive.InputHelper.exe")
    : path.join(__dirname, "..", "native", "bin", "CraftLive.InputHelper.exe");
}

function runMinecraftInputHelper(action) {
  if (process.platform !== "win32") {
    return Promise.reject(new Error(mt("A mod nélküli Minecraft-vezérlés Windows rendszeren működik.", "No-mod Minecraft control works on Windows.")));
  }
  const helperPath = minecraftInputHelperPath();
  if (!existsSync(helperPath)) {
    return Promise.reject(new Error(mt(
      "A biztonságos Minecraft-vezérlő összetevő hiányzik. Telepítsd újra a CraftLive-ot.",
      "The secure Minecraft input component is missing. Reinstall CraftLive."
    )));
  }
  return new Promise((resolve, reject) => {
    let settled = false;
    const child = spawn(helperPath, [action], { windowsHide: true, stdio: "ignore" });
    const timeout = setTimeout(() => {
      if (settled) return;
      settled = true;
      child.kill();
      reject(new Error(mt("A Minecraft-vezérlő nem válaszolt.", "Minecraft control did not respond.")));
    }, 3000);
    timeout.unref?.();
    child.once("error", (error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      reject(error);
    });
    child.once("exit", (code) => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      if (code === 0) resolve();
      else reject(new Error(mt("A Minecraft ablaka nem található.", "Minecraft window was not found.")));
    });
  });
}

async function checkMinecraftWindow() {
  const wasDetected = minecraftWindowDetected;
  try {
    await runMinecraftInputHelper("detect");
    minecraftWindowDetected = true;
  } catch {
    minecraftWindowDetected = false;
  }
  minecraftStatusKnown = true;
  if (minecraftWindowDetected !== wasDetected) {
    addLog(
      "status",
      minecraftWindowDetected ? mt("Minecraft ablak megtalálva", "Minecraft window found") : mt("A Minecraft ablak bezárult", "Minecraft window closed")
    );
  } else {
    broadcastState();
  }
}

async function dispatchKeyboardCommand() {
  if (keyboardBusy || !minecraftWindowDetected || process.platform !== "win32") return;
  const now = Date.now();
  const nextItem = commandQueue[0];
  if (!nextItem || nextItem.notBefore > now) return;

  if (!activeInteractionId) {
    if (lastInteractionFinishedAt && now - lastInteractionFinishedAt < GLOBAL_INTERACTION_INTERVAL_MS) return;
    activeInteractionId = nextItem.interactionId;
  }
  if (nextItem.interactionId !== activeInteractionId) return;

  const item = commandQueue.shift();
  keyboardBusy = true;
  let commandFinished = true;
  const previousClipboard = clipboard.readText();
  const pastedCommand = `/${item.command.replace(/^\/+/, "")}`;
  clipboard.writeText(pastedCommand);
  broadcastState();
  try {
    await runMinecraftInputHelper("send");
  } catch (error) {
    minecraftWindowDetected = false;
    const retries = Number(item.retries || 0);
    if (retries < 3) {
      commandQueue.unshift({ ...item, retries: retries + 1, notBefore: Date.now() + 1800 });
      commandFinished = false;
    } else {
      addLog("error", `${item.slotName}: ${mt("a parancs nem küldhető el", "the action could not be sent")}`, error?.message || "");
    }
  } finally {
    await new Promise((resolve) => setTimeout(resolve, 120));
    if (clipboard.readText() === pastedCommand) clipboard.writeText(previousClipboard);
    if (commandFinished) {
      const followingCommand = commandQueue.find((candidate) => candidate.interactionId === item.interactionId);
      if (followingCommand) {
        followingCommand.notBefore = Date.now() + item.commandDelayMs;
      } else {
        activeInteractionId = "";
        lastInteractionFinishedAt = Date.now();
      }
    }
    keyboardBusy = false;
    broadcastState();
  }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1460,
    height: 930,
    minWidth: 1080,
    minHeight: 720,
    backgroundColor: "#07100d",
    title: "CraftLive",
    show: false,
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  mainWindow.removeMenu();
  mainWindow.loadFile(path.join(__dirname, "ui", "index.html"));
  mainWindow.once("ready-to-show", () => mainWindow.show());
  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

function registerIpc() {
  ipcMain.handle("craftlive:get-state", () => publicState());
  ipcMain.handle("craftlive:connect", (_event, username) => connectTikTok(username));
  ipcMain.handle("craftlive:disconnect", () => disconnectTikTok().then(() => publicState()));
  ipcMain.handle("craftlive:save-settings", async (_event, settings) => {
    config.username = String(settings?.username || "").replace(/^@/, "").trim().slice(0, 80);
    config.autoConnect = Boolean(settings?.autoConnect);
    config.updateManifestUrl = normalizeUpdateManifestUrl(settings?.updateManifestUrl || DEFAULT_UPDATE_MANIFEST_URL, false);
    config.featureFlags["supporter-subscriptions"] = Boolean(settings?.supporterSubscriptionsEnabled);
    config.minecraftVersion = normalizeMinecraftVersion(settings?.minecraftVersion);
    await saveConfig();
    broadcastState();
    return publicState();
  });
  ipcMain.handle("craftlive:save-slot", async (_event, rawSlot) => {
    const position = clampNumber(rawSlot?.position, 0, SLOT_COUNT - 1, 0);
    config.slots[position] = normalizeSlot(rawSlot, position);
    await saveConfig();
    addLog("status", `${config.slots[position].name} ${mt("mentve", "saved")}`);
    return publicState();
  });
  ipcMain.handle("craftlive:duplicate-slot", async (_event, slotId) => {
    const source = config.slots.find((slot) => slot.id === slotId);
    const target = config.slots.find((slot) => !slot.enabled && slot.id !== slotId);
    if (!source || !target) throw new Error(mt("Nincs szabad, kikapcsolt hely a másolathoz.", "No free disabled slot is available for the copy."));
    const copy = normalizeSlot({ ...source, name: `${source.name} ${mt("másolata", "copy")}`, enabled: false }, target.position);
    config.slots[target.position] = copy;
    await saveConfig();
    return publicState();
  });
  ipcMain.handle("craftlive:reset-slot", async (_event, slotId) => {
    const position = config.slots.findIndex((slot) => slot.id === slotId);
    if (position < 0) return publicState();
    config.slots[position] = blankSlot(position);
    await saveConfig();
    return publicState();
  });
  ipcMain.handle("craftlive:test-slot", async (_event, slotId) => {
    const slot = config.slots.find((candidate) => candidate.id === slotId);
    if (!slot) throw new Error(mt("Az interakció nem található.", "Interaction was not found."));
    enqueueSlot(
      slot,
      {
        kind: slot.trigger.kind,
        user: mt("TesztNéző", "TestViewer"),
        nickname: mt("Teszt Néző", "Test Viewer"),
        giftName: slot.trigger.value || "Rose",
        giftId: slot.trigger.value || "5655",
        comment: slot.trigger.value || "!teszt",
        count: slot.trigger.minCount,
        coins: 1
      },
      true
    );
    return publicState();
  });
  ipcMain.handle("craftlive:clear-log", () => {
    logs = [];
    broadcastState();
    return publicState();
  });
  ipcMain.handle("craftlive:export-config", async () => {
    const result = await dialog.showSaveDialog(mainWindow, {
      title: mt("CraftLive beállítások mentése", "Export CraftLive settings"),
      defaultPath: "craftlive-beallitasok.json",
      filters: [{ name: "JSON", extensions: ["json"] }]
    });
    if (result.canceled || !result.filePath) return { canceled: true };
    await writeFile(result.filePath, `${JSON.stringify(config, null, 2)}\n`, "utf8");
    return { canceled: false };
  });
  ipcMain.handle("craftlive:import-config", async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: mt("CraftLive beállítások betöltése", "Import CraftLive settings"),
      properties: ["openFile"],
      filters: [{ name: "JSON", extensions: ["json"] }]
    });
    if (result.canceled || !result.filePaths[0]) return { canceled: true, state: publicState() };
    const imported = JSON.parse(await readFile(result.filePaths[0], "utf8"));
    config = normalizeConfig(imported);
    await saveConfig();
    addLog("status", mt("Beállítások betöltve", "Settings imported"));
    return { canceled: false, state: publicState() };
  });
  ipcMain.handle("craftlive:open-external", (_event, url) => {
    if (typeof url === "string" && /^https:\/\//i.test(url)) return shell.openExternal(url);
    return false;
  });
  ipcMain.handle("craftlive:check-update", async (_event, manifestUrl) => {
    config.updateManifestUrl = normalizeUpdateManifestUrl(manifestUrl || DEFAULT_UPDATE_MANIFEST_URL);
    await saveConfig();
    return checkForUpdates(config.updateManifestUrl, true);
  });
  ipcMain.handle("craftlive:install-update", (_event, featureSelections) => installAvailableUpdate(featureSelections));
}

app.whenReady().then(async () => {
  appLanguage = app.getLocale().toLocaleLowerCase().startsWith("hu") ? "hu" : "en";
  await loadConfig();
  configureInstallerUpdater();
  registerIpc();
  createWindow();
  setInterval(broadcastState, 2000).unref();
  setInterval(() => checkMinecraftWindow().catch(() => {}), 2500).unref();
  setInterval(() => dispatchKeyboardCommand().catch(() => {}), 90).unref();
  setTimeout(() => checkMinecraftWindow().catch(() => {}), 400);
  if (config.autoConnect && config.username) {
    setTimeout(() => connectTikTok(config.username).catch(() => {}), 1200);
  }
  setTimeout(() => checkForUpdates(config.updateManifestUrl || DEFAULT_UPDATE_MANIFEST_URL, false).catch(() => {}), 3500);
  setInterval(() => checkForUpdates(config.updateManifestUrl || DEFAULT_UPDATE_MANIFEST_URL, false).catch(() => {}), UPDATE_CHECK_INTERVAL_MS).unref();
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});

app.on("activate", () => {
  if (!mainWindow) createWindow();
});

app.on("before-quit", () => {
  liveMonitorActive = false;
  clearLiveRetryTimer();
  liveConnection?.disconnect().catch(() => {});
});

export {
  blankSlot,
  clampNumber,
  makeDefaultConfig,
  matchesText,
  normalizeConfig,
  normalizeSlot,
  safeText
};
