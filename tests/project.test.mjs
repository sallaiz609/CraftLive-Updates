import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { MOB_CATALOGS } from "../src/mob-catalog.js";

const read = (relativePath) => readFile(new URL(`../${relativePath}`, import.meta.url), "utf8");

test("the app exposes exactly twenty configurable slots", async () => {
  const main = await read("src/main.js");
  assert.match(main, /const SLOT_COUNT = 20;/);
  assert.match(main, /Array\.from\(\{ length: SLOT_COUNT \}/);
});

test("the no-jar mode controls only a detected Minecraft window", async () => {
  const main = await read("src/main.js");
  const helper = await read("native/Program.cs");
  assert.match(main, /CraftLive\.InputHelper\.exe/);
  assert.match(main, /runMinecraftInputHelper\("detect"\)/);
  assert.match(main, /runMinecraftInputHelper\("send"\)/);
  assert.match(main, /clipboard\.writeText\(pastedCommand\)/);
  assert.match(helper, /EnumWindows/);
  assert.match(helper, /Contains\("Minecraft"/);
  assert.match(helper, /SendInput/);
  assert.doesNotMatch(main, /powershell\.exe|ExecutionPolicy|EncodedCommand|WScript\.Shell/);
  assert.doesNotMatch(main, /createServer|bridgePort|127\.0\.0\.1/);
});

test("commands have bounded count and length", async () => {
  const main = await read("src/main.js");
  assert.match(main, /\.slice\(0, 50\)/);
  assert.match(main, /return command\.slice\(0, 512\)/);
});

test("LIVE monitoring waits and retries automatically", async () => {
  const main = await read("src/main.js");
  const renderer = await read("src/ui/app.js");
  assert.match(main, /const LIVE_RETRY_INTERVAL_MS = 15000;/);
  assert.match(main, /tiktokStatus = "waiting";/);
  assert.match(main, /attemptTikTokConnection\(\)\.catch/);
  assert.match(renderer, /Automatikus LIVE-figyelés elindult/);
});

test("Minecraft version profiles and LIVE-free testing are available", async () => {
  const main = await read("src/main.js");
  const renderer = await read("src/ui/app.js");
  const html = await read("src/ui/index.html");
  assert.match(main, /minecraftVersion: DEFAULT_MINECRAFT_VERSION/);
  assert.match(renderer, /function minecraftCommandProfile/);
  assert.match(renderer, /legacy-1-7/);
  assert.match(renderer, /modern-snbt/);
  assert.match(html, /id="minecraftVersion"/);
  const versionPosition = html.indexOf('id="minecraftVersion"');
  const sidebarStatusPosition = html.indexOf('class="sidebar-status"');
  const settingsPosition = html.indexOf('id="settingsDialog"');
  assert.ok(versionPosition > 0 && versionPosition < sidebarStatusPosition && versionPosition < settingsPosition);
  assert.doesNotMatch(html.slice(settingsPosition), /id="minecraftVersion"/);
  assert.match(renderer, /async function saveSidebarMinecraftVersion/);
  assert.match(html, /Próbáld ki éles LIVE nélkül/);
});

test("mob catalogs follow Minecraft release availability", async () => {
  const html = await read("src/ui/index.html");
  const renderer = await read("src/ui/app.js");
  assert.equal(MOB_CATALOGS["1.18.2"].some((mob) => mob.id === "warden"), false);
  assert.equal(MOB_CATALOGS["1.19"].some((mob) => mob.id === "warden"), true);
  assert.equal(MOB_CATALOGS["1.20.5"].some((mob) => mob.id === "breeze"), false);
  assert.equal(MOB_CATALOGS["1.21"].some((mob) => mob.id === "breeze"), true);
  assert.equal(MOB_CATALOGS["1.21.3"].some((mob) => mob.id === "creaking"), false);
  assert.equal(MOB_CATALOGS["1.21.4"].some((mob) => mob.id === "creaking"), true);
  assert.match(html, /id="mobSelect"/);
  assert.match(renderer, /function useSelectedMob/);
});

test("the interface language follows the Windows application locale", async () => {
  const main = await read("src/main.js");
  const renderer = await read("src/ui/app.js");
  assert.match(main, /app\.getLocale\(\)/);
  assert.match(main, /language: appLanguage/);
  assert.match(renderer, /function applyStaticLanguage/);
  assert.match(renderer, /based on Windows settings/);
});

test("mandatory app updates and opt-in feature choices are wired", async () => {
  const main = await read("src/main.js");
  const preload = await read("src/preload.cjs");
  const renderer = await read("src/ui/app.js");
  const html = await read("src/ui/index.html");
  assert.match(main, /async function checkForUpdates/);
  assert.match(main, /electron-updater/);
  assert.match(main, /autoUpdater\.downloadUpdate\(\)/);
  assert.match(main, /autoUpdater\.quitAndInstall\(true, true\)/);
  assert.match(main, /UPDATE_CHECK_INTERVAL_MS = 10 \* 60 \* 1000/);
  assert.match(main, /app-update\.yml/);
  assert.match(main, /featureFlags/);
  assert.match(main, /lastAnnouncedUpdateVersion/);
  assert.match(preload, /installUpdate/);
  assert.match(html, /id="updateDialog"/);
  assert.match(html, /id="updateFeatureList"/);
  assert.doesNotMatch(html, />Később</);
  assert.match(renderer, /addEventListener\("cancel", \(event\) => event\.preventDefault\(\)\)/);
  assert.match(renderer, /data-update-feature/);
});

test("the Windows distribution is an installable and auto-updatable NSIS app", async () => {
  const packageJson = JSON.parse(await read("package.json"));
  const workflow = await read(".github/workflows/release-windows.yml");
  assert.equal(packageJson.version, "0.6.3");
  assert.equal(packageJson.build.win.target[0].target, "nsis");
  assert.equal(packageJson.build.nsis.perMachine, false);
  assert.equal(packageJson.build.nsis.runAfterFinish, true);
  assert.equal(packageJson.build.publish[0].provider, "github");
  assert.equal(packageJson.dependencies["electron-updater"], "6.8.9");
  assert.equal(packageJson.build.extraResources[0].to, "input/CraftLive.InputHelper.exe");
  assert.match(workflow, /actions\/setup-dotnet@v4/);
  assert.match(workflow, /gh release upload.*--clobber/);
  assert.match(workflow, /gh release create/);
  assert.match(workflow, /RELEASE_NOTES_\$\{version\}\.md/);
});

test("a fixed update button sits directly below Settings in the sidebar", async () => {
  const html = await read("src/ui/index.html");
  const renderer = await read("src/ui/app.js");
  const settingsPosition = html.indexOf('id="openSettingsButton"');
  const updatePosition = html.indexOf('id="sidebarUpdateButton"');
  const navEndPosition = html.indexOf("</nav>", settingsPosition);
  assert.ok(settingsPosition >= 0 && updatePosition > settingsPosition && updatePosition < navEndPosition);
  assert.doesNotMatch(html.match(/<button class="nav-item" id="sidebarUpdateButton"[^>]*>/)?.[0] || "", /hidden/);
  assert.match(renderer, /sidebarUpdateButton.*addEventListener\("click", checkUpdateFromUi\)/);
  assert.match(renderer, /sidebarUpdateBadge/);
});

test("all interactions use a fixed global five-second safety interval", async () => {
  const main = await read("src/main.js");
  const renderer = await read("src/ui/app.js");
  const html = await read("src/ui/index.html");
  assert.match(main, /const GLOBAL_INTERACTION_INTERVAL_MS = 5000;/);
  assert.match(main, /now - lastInteractionFinishedAt < GLOBAL_INTERACTION_INTERVAL_MS/);
  assert.match(main, /queuedInteractions: new Set/);
  assert.match(main, /commandQueue\.length \+ commands\.length > QUEUE_LIMIT/);
  assert.match(main, /const UI_BROADCAST_INTERVAL_MS = 150;/);
  assert.match(main, /now - queueFullLogAt >= GLOBAL_INTERACTION_INTERVAL_MS/);
  assert.match(renderer, /state\.status\.queuedInteractions/);
  assert.match(html, /Fix túlterhelés-védelem:/);
  assert.match(html, /két interakció között mindig legalább 5 másodperc/);
  assert.doesNotMatch(html, /id="(?:global)?InteractionInterval"/i);
});

test("optional paid supporter subscriptions are monitored and linked", async () => {
  const main = await read("src/main.js");
  const renderer = await read("src/ui/app.js");
  const html = await read("src/ui/index.html");
  assert.match(main, /supporter-subscriptions/);
  assert.match(main, /supporterSubscriptionCount \+= 1/);
  assert.match(main, /WebcastEvent\.SUB_NOTIFY/);
  assert.match(html, /id="supporterSubscriptionsEnabled"/);
  assert.match(html, /id="supporterSubscribeLink"/);
  assert.match(renderer, /https:\/\/www\.tiktok\.com\/@/);
  assert.match(renderer, /encodeURIComponent\(username\)/);
});

test("new users start with an empty LIVE account while the optional supporter link stays fixed", async () => {
  const main = await read("src/main.js");
  assert.match(main, /const SUPPORTER_TIKTOK_ACCOUNT = "venom_hun_";/);
  assert.match(main, /username: "",/);
  assert.match(main, /account: SUPPORTER_TIKTOK_ACCOUNT/);
  assert.doesNotMatch(main, /username: "venom_hun_"/);
});

test("every catalog contains unique summon-safe mob identifiers", () => {
  for (const mobs of Object.values(MOB_CATALOGS)) {
    assert.equal(new Set(mobs.map((mob) => mob.commandId)).size, mobs.length);
    for (const mob of mobs) assert.match(mob.commandId, /^(?:minecraft:)?[A-Za-z0-9_]+$/);
  }
});

test("renderer uses the isolated preload API", async () => {
  const main = await read("src/main.js");
  const preload = await read("src/preload.cjs");
  assert.match(main, /contextIsolation: true/);
  assert.match(main, /nodeIntegration: false/);
  assert.match(preload, /contextBridge\.exposeInMainWorld\("craftlive"/);
});
