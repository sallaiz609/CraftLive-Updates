const $ = (selector, parent = document) => parent.querySelector(selector);
const $$ = (selector, parent = document) => [...parent.querySelectorAll(selector)];

const staticEnglish = {
  "Főmenü": "Main menu",
  "Interakciók": "Interactions",
  "Hosting": "Hosting",
  "Beállítások": "Settings",
  "Támogatás": "Support",
  "KAPCSOLATOK": "CONNECTIONS",
  "Nincs kapcsolat": "Not connected",
  "Játékablakra vár": "Waiting for game window",
  "Mod nélkül": "No mod",
  "VEZÉRLŐPULT": "DASHBOARD",
  "TikTok felhasználónév": "TikTok username",
  "LIVE figyelése": "Watch LIVE",
  "Beállítás állapota": "Setup status",
  "Az alkalmazás fut": "Application is running",
  "Minecraft ablak": "Minecraft window",
  "Indítsd el a játékot": "Start the game",
  "Csatlakozz a LIVE-hoz": "Connect to LIVE",
  "Áttekintés": "Overview",
  "Aktív interakció": "Active interactions",
  "Várakozó interakció": "Queued interactions",
  "Legutóbbi esemény": "Latest event",
  "Még nincs esemény": "No events yet",
  "A 20 interakciós helyed": "Your 20 interaction slots",
  "Minden hely külön eseményt és tetszőleges Minecraft-parancsokat kezel.": "Each slot handles its own event and Minecraft actions.",
  "Fix túlterhelés-védelem:": "Fixed overload protection:",
  "két interakció között mindig legalább 5 másodperc telik el.": "there are always at least 5 seconds between interactions.",
  "Keresés…": "Search…",
  "Interakciók szűrése": "Filter interactions",
  "Mind": "All",
  "Csak aktív": "Enabled only",
  "Ajándék": "Gift",
  "Közösségi": "Social",
  "Komment": "Comment",
  "Nincs találat": "No results",
  "Próbálj másik keresést vagy szűrőt.": "Try another search or filter.",
  "Élő napló": "Live log",
  "ÉLŐ": "LIVE",
  "Napló törlése": "Clear log",
  "Teszt": "Test",
  "Itt jelennek meg az események": "Events will appear here",
  "Kapcsolódás után látni fogod az ajándékokat, like-okat és a Minecraft-műveleteket.": "After connecting, gifts, likes, and Minecraft actions will appear here.",
  "Támogasd a CraftLive-ot": "Support CraftLive",
  "KÉSZÍTŐI TIKTOK-PROFIL": "CREATOR TIKTOK PROFILE",
  "Ha tetszik a CraftLive, egy TikTok-követéssel önkéntesen támogathatod a további fejlesztését.": "If you enjoy CraftLive, you can voluntarily support its continued development by following the creator on TikTok.",
  "Bekövetem TikTokon": "Follow on TikTok",
  "Teljesen önkéntes.": "Completely optional.",
  "A CraftLive minden funkciója követés nélkül is használható.": "Every CraftLive feature remains available without following.",
  "Interakció szerkesztése": "Edit interaction",
  "Bezárás": "Close",
  "Elnevezés": "Name",
  "Így találod meg gyorsan a listában.": "This helps you find it quickly.",
  "Név": "Name",
  "Szín": "Color",
  "Zöld": "Green",
  "Kék": "Blue",
  "Lila": "Purple",
  "Narancs": "Orange",
  "Piros": "Red",
  "Mi indítsa el?": "What triggers it?",
  "Válassz TikTok LIVE-eseményt.": "Choose a TikTok LIVE event.",
  "Követés": "Follow",
  "Megosztás": "Share",
  "Belépés": "Join",
  "Feliratkozás": "Subscribe",
  "Ajándék neve vagy ID-ja": "Gift name or ID",
  "Egyezés": "Match",
  "Pontosan ez": "Exact",
  "Tartalmazza": "Contains",
  "Minimum darab": "Minimum count",
  "LIVE-ban legutóbb látott ajándékok": "Recently seen LIVE gifts",
  "Mi történjen a játékban?": "What happens in the game?",
  "Válassz sablont, aztán módosítsd szabadon.": "Choose a preset, then customize it freely.",
  "Zombi": "Zombie",
  "Villám": "Lightning",
  "Gyógyítás": "Healing",
  "Vakság": "Blindness",
  "Gyémánt": "Diamond",
  "Vihar": "Storm",
  "Éjszaka": "Night",
  "Mob kiválasztása parancs nélkül": "Choose a mob without commands",
  "Mob keresése": "Search mobs",
  "Pl. Warden, Zombie, Creeper": "E.g. Warden, Zombie, Creeper",
  "Kategória": "Category",
  "Összes mob": "All mobs",
  "Ellenséges": "Hostile",
  "Békés / semleges": "Passive / neutral",
  "Vízi": "Aquatic",
  "Főellenség": "Boss",
  "A legújabb verzióban elérhető mobok": "Mobs available in the latest version",
  "Darabszám": "Count",
  "Megjelenés helye": "Spawn position",
  "A játékos körül": "Around the player",
  "A játékos felett": "Above the player",
  "A játékos helyén": "At the player",
  "Mob használata": "Use mob",
  "Haladó: nyers Minecraft-parancsok": "Advanced: raw Minecraft commands",
  "Minecraft-parancsok": "Minecraft commands",
  "egy sor = egy parancs, / jel nélkül": "one command per line, without /",
  "Beilleszthető adatok:": "Available placeholders:",
  "Finomhangolás": "Fine tuning",
  "Védelem a túl sok egyidejű esemény ellen.": "Protection against too many simultaneous events.",
  "Várakozás (ms)": "Cooldown (ms)",
  "Parancsok között (ms)": "Between commands (ms)",
  "Esély (%)": "Chance (%)",
  "Próbáld ki éles LIVE nélkül": "Test without an active LIVE",
  "A teszt sorba állítja a parancsokat; az 5 másodperces fix védelem itt is érvényes. Aktív verzió:": "The test queues the actions; the fixed 5-second protection applies here too. Active version:",
  "Teszt indítása": "Start test",
  "Mod nélküli működés": "No-mod operation",
  "A játékban engedélyezd a parancsokat. Az app röviden megnyitja a chatet, majd automatikusan beírja a műveletet.": "Enable commands in the world. The app briefly opens chat and enters the action automatically.",
  "Interakció bekapcsolva": "Interaction enabled",
  "Hely ürítése": "Clear slot",
  "Mégse": "Cancel",
  "Mentés": "Save",
  "Alkalmazás nyelve: magyar, a Windows beállítása alapján.": "Application language: English, based on Windows settings.",
  "Felhasználónév": "Username",
  "Automatikus LIVE-figyelés az alkalmazás indításakor": "Automatically watch LIVE when the app starts",
  "A figyelés akkor is elindítható, ha még nem fut a LIVE. A CraftLive 15 másodpercenként ellenőrzi, és automatikusan kapcsolódik az indulásakor.": "Monitoring can start before the LIVE. CraftLive checks every 15 seconds and connects automatically when it begins.",
  "Minecraft – mod nélkül": "Minecraft — no mod",
  "Aktív Minecraft Java-verzió": "Active Minecraft Java version",
  "Fix beállítás: a CraftLive mindig csak a legújabb kiadást használja.": "Fixed setting: CraftLive always uses only the latest release.",
  "A CraftLive automatikusan megkeresi a futó Minecraft Java ablakát. Eseménynél előtérbe hozza, megnyitja a chatet a": "CraftLive automatically finds the running Minecraft Java window. For an event it brings the game forward, opens chat with",
  "billentyűvel, beilleszti a parancsot és elküldi. Nem szükséges Forge mod vagy JAR.": ", inserts the action, and sends it. No Forge mod or JAR is required.",
  "A CraftLive kizárólag a legújabb Minecraft Java-kiadást támogatja. A saját, már elmentett nyers parancsokat az alkalmazás biztonsági okból nem írja át automatikusan.": "CraftLive supports only the latest Minecraft Java release. For safety, previously saved raw commands are not rewritten automatically.",
  "Szükséges": "Required",
  "Fontos": "Important",
  "A világban legyenek engedélyezve a csalások/parancsok, és a chat billentyűje maradjon T.": "Cheats/commands must be enabled in the world and the chat key must remain T.",
  "CraftLive-frissítések": "CraftLive updates",
  "Jelenlegi verzió: 0.6.6": "Current version: 0.6.6",
  "Nincs beállítva frissítési forrás.": "No update source is configured.",
  "Frissítés": "Update",
  "Frissítési csatorna": "Update channel",
  "A hivatalos CraftLive GitHub-kiadások ellenőrzése automatikus: indításkor, majd 10 percenként megtörténik.": "Official CraftLive GitHub releases are checked automatically at startup and every 10 minutes.",
  "Ha új verzió érhető el, annak telepítése kötelező; csak az új opcionális funkciók bekapcsolásáról dönt a felhasználó.": "If a new version is available, installing it is mandatory; the user only chooses which new optional features to enable.",
  "Frissítés keresése": "Check for updates",
  "Helyi frissítő ZIP (tartalék)": "Local update ZIP (fallback)",
  "Új frissítés érhető el": "A new update is available",
  "Új funkciók és javítások.": "New features and fixes.",
  "Melyik új funkciót szeretnéd engedélyezni?": "Which new features would you like to enable?",
  "A választásaid az újraindítás után lépnek életbe.": "Your choices take effect after restart.",
  "Újraindítás": "Restart",
  "Az új verzió telepítése kötelező. A gomb megnyomásakor a CraftLive bezárul, majd automatikusan újraindul.": "Installing the new version is mandatory. When you press the button, CraftLive will close and restart automatically.",
  "Frissítés és újraindítás": "Update and restart",
  "Biztonsági mentés": "Backup",
  "Beállítások mentése": "Export settings",
  "Beállítások betöltése": "Import settings",
  "Egyetlen alkalmazás, JAR nélkül": "One application, no JAR",
  "A beállításaid a saját gépeden maradnak. A TikTok-fiókod jelszavát nem kéri és nem tárolja az alkalmazás.": "Your settings remain on your computer. The application never requests or stores your TikTok password.",
  "@felhasználónév": "@username",
  "A lista kizárólag a legújabb Minecraft Java-kiadás vanilla mobjait tartalmazza.": "The list contains only vanilla mobs from the latest Minecraft Java release.",
  "MINECRAFT SZERVER": "MINECRAFT SERVER",
  "Falix megnyitása": "Open Falix",
  "FALIX CLOUD HOSTING": "FALIX CLOUD HOSTING",
  "A szerver nem a saját gépedet terheli": "The server does not load your own PC",
  "A CraftLive külön Hosting füle a saját Falix vezérlőpultodhoz vezet. A szerver futtatását és erőforrásait a Falix kezeli.": "CraftLive's dedicated Hosting tab takes you to your own Falix control panel. Falix handles server runtime and resources.",
  "Saját Falix szerverpanel": "Your Falix server panel",
  "Másold be a saját szervered Falix-címét. A CraftLive ezt helyben menti, és nem osztja meg más felhasználókkal.": "Paste your own server's Falix URL. CraftLive stores it locally and does not share it with other users.",
  "Falix panel HTTPS-címe": "Falix panel HTTPS URL",
  "Cím mentése": "Save URL",
  "Vezérlőpult megnyitása": "Open control panel",
  "Indítsd el a szervert": "Start the server",
  "A Falix vezérlőpultján válaszd ki a kívánt szervert, majd indítsd el. A bejelentkezést továbbra is a Falix biztonságos oldalán végzed.": "Select your server in the Falix control panel and start it. You still sign in on Falix's secure website.",
  "Interakciók külön fülön": "Interactions in a separate tab",
  "Válts vissza az Interakciók fülre a TikTok-ajándékok, tesztek és Minecraft-műveletek kezeléséhez.": "Switch back to the Interactions tab to manage TikTok gifts, tests, and Minecraft actions.",
  "A CraftLive nem tárol Falix-jelszót, és nem ígér folyamatos ingyenes üzemidőt. A tárhelycsomag szabályait a Falix határozza meg.": "CraftLive does not store your Falix password and cannot promise continuous free uptime. Falix defines the hosting plan rules."
};

function englishMode() {
  return state?.language === "en";
}

function tx(hungarian, english) {
  return englishMode() ? english : hungarian;
}

function applyStaticLanguage() {
  document.documentElement.lang = englishMode() ? "en" : "hu";
  if (!englishMode()) return;
  Object.assign(triggerLabels, {
    gift: "Gift", like: "Like", follow: "Follow", share: "Share",
    chat: "Comment", member: "Join", subscribe: "Subscribe"
  });
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    const value = node.nodeValue.trim();
    if (staticEnglish[value]) node.nodeValue = node.nodeValue.replace(value, staticEnglish[value]);
  }
  for (const element of document.querySelectorAll("[placeholder], [title], [aria-label]")) {
    for (const attribute of ["placeholder", "title", "aria-label"]) {
      const value = element.getAttribute(attribute);
      if (value && staticEnglish[value]) element.setAttribute(attribute, staticEnglish[value]);
    }
  }
  $("#noModExplanation").innerHTML = "CraftLive automatically finds the running Minecraft Java window. For an event it brings the game forward, opens chat with the <b>T</b> key, inserts the action, and sends it. No Forge mod or JAR is required.";
}

const triggerLabels = {
  gift: "Ajándék",
  like: "Like",
  follow: "Követés",
  share: "Megosztás",
  chat: "Komment",
  member: "Belépés",
  subscribe: "Feliratkozás"
};

const triggerIcons = {
  gift: "🎁",
  like: "♥",
  follow: "+",
  share: "↗",
  chat: "☵",
  member: "→",
  subscribe: "★"
};

function minecraftVersionLabel(version) {
  return tx(`Legújabb (${version})`, `Latest (${version})`);
}

function latestPresets() {
  const entity = (name) => `minecraft:${name}`;
  const message = (text, color) => `title @a actionbar {text:\"${text}\",color:\"${color}\"}`;
  const effect = (name, seconds, amplifier) => `effect give @p minecraft:${name} ${seconds} ${amplifier} true`;

  return {
    zombie: {
      name: tx("Zombi támadás", "Zombie attack"),
      commands: `${message(tx("{user} zombit küldött!", "{user} sent a zombie!"), "green")}\nsummon ${entity("zombie")} ~ ~1 ~`
    },
    creeper: {
      name: tx("Creeper támadás", "Creeper attack"),
      commands: `${message(tx("{user} Creepert küldött!", "{user} sent a Creeper!"), "red")}\nsummon ${entity("creeper")} ~2 ~ ~2`
    },
    lightning: { name: tx("Villámcsapás", "Lightning strike"), commands: `summon ${entity("lightning_bolt")} ~ ~ ~` },
    tnt: {
      name: tx("TNT-eső", "TNT rain"),
      commands: [
        `summon ${entity("tnt")} ~2 ~8 ~ {Fuse:60}`,
        `summon ${entity("tnt")} ~-2 ~9 ~1 {Fuse:70}`,
        `summon ${entity("tnt")} ~ ~10 ~-2 {Fuse:80}`
      ].join("\n")
    },
    heal: {
      name: tx("Gyógyítás", "Healing"),
      commands: `${effect("regeneration", 10, 2)}\n${effect("absorption", 15, 1)}`
    },
    blindness: {
      name: tx("Vakság", "Blindness"),
      commands: `${effect("blindness", 12, 0)}\n${message(tx("{user} elvette a látásod!", "{user} took your sight!"), "dark_purple")}`
    },
    diamond: { name: tx("Gyémánt jutalom", "Diamond reward"), commands: "give @p minecraft:diamond {amount}" },
    storm: { name: tx("Vihar", "Storm"), commands: "weather thunder 90" },
    night: { name: tx("Legyen éjszaka", "Set night"), commands: "time set night" },
    teleport: { name: tx("Véletlen teleport", "Random teleport"), commands: "spreadplayers ~ ~ 20 80 false @p" }
  };
}

let state = null;
let editingSlot = null;
let selectedTrigger = "gift";
let renderedUpdateVersion = "";
let activeView = "interactions";

function showView(view) {
  activeView = view === "hosting" ? "hosting" : "interactions";
  $("#interactionsView").classList.toggle("hidden", activeView !== "interactions");
  $("#hostingView").classList.toggle("hidden", activeView !== "hosting");
  $("#interactionsTabButton").classList.toggle("active", activeView === "interactions");
  $("#hostingTabButton").classList.toggle("active", activeView === "hosting");
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function actionSummary(commands) {
  const first = String(commands || "").split(/\r?\n/).find((line) => line.trim()) || tx("Nincs parancs", "No command");
  const words = first.replace(/^execute .* run /, "").split(" ");
  const names = {
    summon: tx("Mob / esemény idézése", "Summon mob / event"),
    effect: tx("Játékos effekt", "Player effect"),
    give: tx("Tárgy adása", "Give item"),
    weather: tx("Időjárás", "Weather"),
    time: tx("Napszak", "Time"),
    title: tx("Képernyőüzenet", "Screen message"),
    spreadplayers: tx("Teleportálás", "Teleport"),
    damage: tx("Sebzés", "Damage"),
    setblock: tx("Blokk lerakása", "Place block"),
    fill: tx("Terület módosítása", "Modify area")
  };
  return names[words[0]] || names[words.at(-2)] || first;
}

function triggerSummary(slot) {
  const trigger = slot.trigger;
  if (trigger.kind === "gift") return trigger.value ? `${trigger.value} ×${trigger.minCount}` : `${tx("Bármely ajándék", "Any gift")} ×${trigger.minCount}`;
  if (trigger.kind === "like") return `${trigger.minCount} like`;
  if (trigger.kind === "chat") return trigger.value ? `„${trigger.value}”` : tx("Bármely komment", "Any comment");
  return triggerLabels[trigger.kind] || trigger.kind;
}

function render() {
  if (!state) return;
  const active = state.config.slots.filter((slot) => slot.enabled).length;
  const tiktokOnline = state.status.tiktok === "online";
  const tiktokWaiting = state.status.tiktok === "waiting";
  const tiktokConnecting = state.status.tiktok === "connecting";
  const minecraftOnline = state.status.minecraft === "online";

  if (document.activeElement !== $("#usernameInput")) {
    $("#usernameInput").value = state.config.username || "";
  }
  if (document.activeElement !== $("#hostingPanelUrl")) {
    $("#hostingPanelUrl").value = state.config.hostingPanelUrl || "https://client.falixnodes.net/";
  }
  $("#minecraftVersionValue").textContent = minecraftVersionLabel(state.config.minecraftVersion);
  $("#navActiveCount").textContent = String(active);
  $("#activeMetric").textContent = `${active} / 20`;
  $("#queueMetric").textContent = String(state.status.queuedInteractions || 0);
  $("#lastEventMetric").textContent = state.logs[0]?.message || tx("Még nincs esemény", "No events yet");

  updateStatus("#sideTikTokDot", state.status.tiktok);
  updateStatus("#sideMinecraftDot", state.status.minecraft);
  $("#sideTikTokText").textContent = tiktokOnline
    ? `@${state.config.username}`
    : tiktokWaiting
      ? tx("LIVE-ra vár…", "Waiting for LIVE…")
      : tiktokConnecting
        ? tx("LIVE ellenőrzése…", "Checking LIVE…")
        : tx("Nincs kapcsolat", "Not connected");
  $("#sideMinecraftText").textContent = minecraftOnline
    ? `${tx("Játékablak", "Game window")} • ${minecraftVersionLabel(state.config.minecraftVersion)}`
    : `${tx("Játékablakra vár", "Waiting for game window")} • ${minecraftVersionLabel(state.config.minecraftVersion)}`;
  $("#minecraftSetupStep").classList.toggle("done", minecraftOnline);
  $("#minecraftSetupText").textContent = minecraftOnline
    ? `${tx("Ablak megtalálva", "Window found")} • ${minecraftVersionLabel(state.config.minecraftVersion)}`
    : `${tx("Indítsd el ezt a verziót:", "Start this version:")} ${minecraftVersionLabel(state.config.minecraftVersion)}`;
  $("#tiktokSetupStep").classList.toggle("done", tiktokOnline);
  $("#tiktokSetupText").textContent = tiktokOnline
    ? `@${state.config.username}`
    : tiktokWaiting
      ? tx("Automatikusan vár a LIVE-ra", "Waiting for LIVE automatically")
      : tiktokConnecting
        ? tx("Ellenőrzés folyamatban…", "Checking…")
        : tx("Indítsd el a LIVE-figyelést", "Start LIVE monitoring");
  $("#connectButton").textContent = tiktokOnline
    ? tx("Leválasztás", "Disconnect")
    : tiktokWaiting
      ? tx("Figyelés leállítása", "Stop monitoring")
      : tiktokConnecting
        ? tx("Ellenőrzés…", "Checking…")
        : tx("LIVE figyelése", "Watch LIVE");
  $("#connectButton").disabled = tiktokConnecting;

  renderSlots();
  renderLogs();
  renderUpdatePanel();
  if ($("#editorDialog").open) {
    renderRecentGifts();
    renderMobCatalog();
  }
}

function updateStatusMessage(update) {
  if (!state.config.updateManifestUrl && update.status === "idle") return tx("Nincs beállítva frissítési forrás.", "No update source is configured.");
  return {
    idle: tx("Automatikus ellenőrzés indításkor és 10 percenként.", "Automatic check at startup and every 10 minutes."),
    checking: tx("Frissítés keresése…", "Checking for updates…"),
    current: tx("A CraftLive naprakész.", "CraftLive is up to date."),
    available: `${tx("Kötelező frissítés érhető el", "A mandatory update is available")}: v${update.availableVersion}`,
    downloading: `${tx("Frissítés letöltése", "Downloading update")}… ${update.progress || 0}%`,
    installing: tx("Telepítés és újraindítás…", "Installing and restarting…"),
    error: update.error || tx("A frissítés ellenőrzése nem sikerült.", "Update check failed.")
  }[update.status] || "";
}

function populateUpdateDialog(update) {
  renderedUpdateVersion = update.availableVersion;
  $("#updateFromVersion").textContent = update.currentVersion;
  $("#updateToVersion").textContent = update.availableVersion;
  $("#updateNotes").textContent = update.notes || tx("Új funkciók és javítások.", "New features and fixes.");
  const features = Array.isArray(update.features) ? update.features : [];
  $("#updateFeaturesSection").classList.toggle("hidden", features.length === 0);
  $("#updateFeatureList").innerHTML = features.map((feature) => {
    const previouslyChosen = Object.hasOwn(state.config.featureFlags || {}, feature.id)
      ? Boolean(state.config.featureFlags[feature.id])
      : false;
    return `<label class="update-feature-option">
      <input type="checkbox" data-update-feature="${escapeHtml(feature.id)}" ${previouslyChosen ? "checked" : ""} />
      <span><strong>${escapeHtml(feature.name)}</strong>${feature.description ? `<small>${escapeHtml(feature.description)}</small>` : ""}</span>
    </label>`;
  }).join("");
}

function renderUpdatePanel() {
  if (!state?.update) return;
  const update = state.update;
  $("#sidebarVersion").textContent = update.currentVersion;
  $("#cornerVersion").textContent = `v${update.currentVersion}`;
  $("#sidebarUpdateButton").dataset.status = update.status;
  $("#sidebarUpdateBadge").textContent = update.status === "available"
    ? tx("ÚJ", "NEW")
    : update.status === "checking"
      ? "…"
      : update.status === "downloading"
        ? `${update.progress || 0}%`
        : update.status === "installing"
          ? "…"
          : update.status === "error"
            ? "!"
            : update.status === "current"
              ? "✓"
              : "";
  $("#updateVersionText").textContent = `${tx("Jelenlegi verzió", "Current version")}: ${update.currentVersion}`;
  $("#updateStatusText").textContent = updateStatusMessage(update);
  $("#updateStatusCard").dataset.status = update.status;
  const hasMandatoryUpdate = Boolean(update.availableVersion) && ["available", "downloading", "installing", "error"].includes(update.status);
  $("#showUpdateButton").classList.toggle("hidden", !hasMandatoryUpdate);
  $("#updateDownloadStatus").textContent = update.status === "error" ? update.error : updateStatusMessage(update);
  const installButton = $("#installUpdateButton");
  installButton.disabled = ["downloading", "installing"].includes(update.status);
  installButton.textContent = update.status === "downloading"
    ? `${tx("Letöltés", "Downloading")}… ${update.progress || 0}%`
    : update.status === "installing"
      ? tx("Telepítés…", "Installing…")
      : update.status === "error"
        ? tx("Újrapróbálás", "Retry")
        : tx("Frissítés és újraindítás", "Update and restart");

  if (!hasMandatoryUpdate) return;
  if (renderedUpdateVersion !== update.availableVersion) populateUpdateDialog(update);
  if (!$("#updateDialog").open) {
    $$(`dialog[open]`).filter((dialog) => dialog.id !== "updateDialog").forEach((dialog) => dialog.close());
    $("#updateDialog").showModal();
  }
}

function updateStatus(selector, status) {
  const element = $(selector);
  element.className = `status-dot ${status === "online" ? "online" : ["connecting", "waiting"].includes(status) ? "connecting" : ""}`;
}

function renderSlots() {
  const query = $("#searchInput").value.trim().toLocaleLowerCase("hu-HU");
  const filter = $("#filterSelect").value;
  const slots = state.config.slots.filter((slot) => {
    const matchesQuery = !query || `${slot.name} ${triggerSummary(slot)} ${actionSummary(slot.commands)}`.toLocaleLowerCase("hu-HU").includes(query);
    const matchesFilter = filter === "all" ||
      (filter === "enabled" && slot.enabled) ||
      (filter === "social" && ["follow", "share", "member", "subscribe"].includes(slot.trigger.kind)) ||
      filter === slot.trigger.kind;
    return matchesQuery && matchesFilter;
  });

  $("#slotsGrid").innerHTML = slots.map((slot) => `
    <article class="slot-card ${escapeHtml(slot.accent)} ${slot.enabled ? "" : "disabled"}" data-slot-id="${slot.id}">
      <div class="slot-top">
        <div>
          <span class="slot-index">HELY ${String(slot.position + 1).padStart(2, "0")}</span>
          <h3 title="${escapeHtml(slot.name)}">${escapeHtml(slot.name)}</h3>
        </div>
        <button class="mini-switch ${slot.enabled ? "on" : ""}" data-action="toggle" title="${slot.enabled ? tx("Kikapcsolás", "Disable") : tx("Bekapcsolás", "Enable")}" aria-label="${slot.enabled ? tx("Kikapcsolás", "Disable") : tx("Bekapcsolás", "Enable")}"></button>
      </div>
      <div class="slot-flow">
        <div class="flow-chip"><small>${tx("INDÍTÓ", "TRIGGER")}</small><strong>${triggerIcons[slot.trigger.kind] || "•"} ${escapeHtml(triggerSummary(slot))}</strong></div>
        <span class="flow-arrow">→</span>
        <div class="flow-chip"><small>${tx("MŰVELET", "ACTION")}</small><strong>${escapeHtml(actionSummary(slot.commands))}</strong></div>
      </div>
      <div class="slot-actions">
        <button data-action="edit">${tx("Szerkesztés", "Edit")}</button>
        <button data-action="duplicate">${tx("Másolás", "Duplicate")}</button>
        <button class="test" data-action="test" title="${tx("Teszt", "Test")}">▶</button>
      </div>
    </article>
  `).join("");
  $("#emptyState").classList.toggle("hidden", slots.length > 0);
}

function renderLogs() {
  const hasLogs = state.logs.length > 0;
  $("#activityEmpty").classList.toggle("hidden", hasLogs);
  $("#activityList").classList.toggle("hidden", !hasLogs);
  $("#activityList").innerHTML = state.logs.slice(0, 80).map((entry) => {
    const icon = entry.type === "error" ? "!" : entry.type === "event" ? "↯" : entry.type === "test" ? "▶" : entry.type === "skip" ? "–" : "✓";
    const time = new Date(entry.time).toLocaleTimeString(englishMode() ? "en-US" : "hu-HU", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
    return `<div class="activity-item ${escapeHtml(entry.type)}"><span class="activity-dot">${icon}</span><div class="activity-copy"><strong>${escapeHtml(entry.message)}</strong>${entry.details ? `<p>${escapeHtml(entry.details)}</p>` : ""}<time>${time}</time></div></div>`;
  }).join("");
}

function openEditor(slot) {
  editingSlot = structuredClone(slot);
  selectedTrigger = editingSlot.trigger.kind;
  $("#editorSlotNumber").textContent = `${tx("INTERAKCIÓ", "INTERACTION")} ${String(slot.position + 1).padStart(2, "0")}`;
  $("#slotName").value = slot.name;
  $("#slotAccent").value = slot.accent;
  $("#triggerValue").value = slot.trigger.value;
  $("#triggerMode").value = slot.trigger.mode;
  $("#triggerMinCount").value = slot.trigger.minCount;
  $("#slotCommands").value = slot.commands;
  $("#slotCooldown").value = slot.cooldownMs;
  $("#slotDelay").value = slot.commandDelayMs;
  $("#slotChance").value = slot.chance;
  $("#slotEnabled").checked = slot.enabled;
  $("#testMinecraftVersion").textContent = minecraftVersionLabel(state.config.minecraftVersion);
  updateTriggerEditor();
  $("#editorDialog").showModal();
  renderMobCatalog();
}

function updateTriggerEditor() {
  $$("[data-trigger]", $("#triggerPicker")).forEach((button) => button.classList.toggle("active", button.dataset.trigger === selectedTrigger));
  const valueField = $("#triggerValueField");
  const modeField = $("#triggerModeField");
  const valueLabel = $("#triggerValueLabel");
  const minLabel = $("#minCountLabel");
  const input = $("#triggerValue");
  const needsValue = ["gift", "chat"].includes(selectedTrigger);
  valueField.classList.toggle("hidden", !needsValue);
  modeField.classList.toggle("hidden", !needsValue);
  if (selectedTrigger === "gift") {
    valueLabel.textContent = tx("Ajándék neve vagy ID-ja", "Gift name or ID");
    input.placeholder = tx("Pl. Rose — üresen bármelyik", "E.g. Rose — empty means any");
    minLabel.textContent = tx("Minimum darab", "Minimum count");
  } else if (selectedTrigger === "chat") {
    valueLabel.textContent = tx("Komment vagy parancs", "Comment or command");
    input.placeholder = tx("Pl. !boom — üresen bármelyik", "E.g. !boom — empty means any");
    minLabel.textContent = tx("Minimum darab", "Minimum count");
  } else if (selectedTrigger === "like") {
    minLabel.textContent = tx("Like-küszöb", "Like threshold");
  } else {
    minLabel.textContent = tx("Minimum darab", "Minimum count");
  }
  renderRecentGifts();
}

function renderRecentGifts() {
  const visible = selectedTrigger === "gift" && Array.isArray(state?.recentGifts) && state.recentGifts.length > 0;
  $("#recentGiftsBox").classList.toggle("hidden", !visible);
  $("#recentGiftsList").innerHTML = visible
    ? state.recentGifts.map((gift) => `<button type="button" data-gift-value="${escapeHtml(gift.name || gift.id)}" title="ID: ${escapeHtml(gift.id)}">🎁 ${escapeHtml(gift.name || gift.id)}</button>`).join("")
    : "";
}

function mobCategoryLabel(category) {
  return {
    boss: tx("Főellenség", "Boss"),
    hostile: tx("Ellenséges", "Hostile"),
    passive: tx("Békés", "Passive"),
    water: tx("Vízi", "Aquatic"),
    neutral: tx("Semleges", "Neutral")
  }[category] || tx("Egyéb", "Other");
}

function renderMobCatalog() {
  const select = $("#mobSelect");
  if (!select || !state) return;
  const previousValue = select.value;
  const query = $("#mobSearch").value.trim().toLocaleLowerCase("hu-HU");
  const category = $("#mobCategory").value;
  const mobs = (state.mobs || []).filter((mob) => {
    const matchesSearch = !query || `${mob.name} ${mob.id}`.toLocaleLowerCase("hu-HU").includes(query);
    const matchesCategory = category === "all" || mob.category === category ||
      (category === "passive" && mob.category === "neutral");
    return matchesSearch && matchesCategory;
  });
  select.innerHTML = mobs.map((mob) =>
    `<option value="${escapeHtml(mob.id)}">${escapeHtml(mob.name)} — ${escapeHtml(mobCategoryLabel(mob.category))}</option>`
  ).join("");
  if (mobs.some((mob) => mob.id === previousValue)) select.value = previousValue;
  $("#mobResultCount").textContent = `${mobs.length} ${tx("mob", "mobs")}`;
  $("#mobCatalogVersion").textContent = `Minecraft ${state.minecraftCatalogVersion}`;
  $("#mobEmptyHelp").textContent = mobs.length
    ? tx("A lista kizárólag a legújabb Minecraft Java-kiadás vanilla mobjait tartalmazza.", "The list contains only vanilla mobs from the latest Minecraft Java release.")
    : tx("Ebben a keresésben nincs elérhető mob.", "No mobs match this search.");
}

function mobCoordinates(position, index) {
  if (position === "exact") return "~ ~1 ~";
  if (position === "above") {
    const x = (index % 3) - 1;
    const z = (Math.floor(index / 3) % 3) - 1;
    return `~${x || ""} ~${5 + Math.floor(index / 9)} ~${z || ""}`;
  }
  const offsets = [[2, 2], [-2, 2], [2, -2], [-2, -2], [3, 0], [-3, 0], [0, 3], [0, -3]];
  const [x, z] = offsets[index % offsets.length];
  return `~${x} ~ ~${z}`;
}

function useSelectedMob() {
  const mob = (state.mobs || []).find((candidate) => candidate.id === $("#mobSelect").value);
  if (!mob) return toast(tx("Nincs kiválasztott mob", "No mob selected"), tx("Válassz egy mobot a legújabb listából.", "Choose a mob from the latest list."), "error");
  const count = Math.min(20, Math.max(1, Number($("#mobCount").value) || 1));
  const position = $("#mobPosition").value;
  const commands = Array.from({ length: count }, (_, index) =>
    `summon ${mob.commandId} ${mobCoordinates(position, index)}`
  );
  $("#slotName").value = `${mob.name} ×${count}`;
  $("#slotCommands").value = commands.join("\n");
  toast(tx("Mob beállítva", "Mob selected"), `${mob.name} ×${count} • Minecraft ${state.minecraftCatalogVersion}`);
}

function slotFromEditor() {
  return {
    ...editingSlot,
    enabled: $("#slotEnabled").checked,
    name: $("#slotName").value.trim(),
    accent: $("#slotAccent").value,
    trigger: {
      kind: selectedTrigger,
      value: $("#triggerValue").value.trim(),
      mode: $("#triggerMode").value,
      minCount: Number($("#triggerMinCount").value || 1)
    },
    commands: $("#slotCommands").value,
    cooldownMs: Number($("#slotCooldown").value || 0),
    commandDelayMs: Number($("#slotDelay").value || 0),
    chance: Number($("#slotChance").value || 100)
  };
}

function toast(title, message = "", type = "success") {
  const element = document.createElement("div");
  element.className = `toast ${type}`;
  element.innerHTML = `<strong>${escapeHtml(title)}</strong>${message ? `<span>${escapeHtml(message)}</span>` : ""}`;
  $("#toastStack").append(element);
  setTimeout(() => element.remove(), 3500);
}

async function handleConnect() {
  try {
    if (state.status.tiktok !== "offline") {
      state = await window.craftlive.disconnect();
      toast(tx("LIVE-figyelés leállítva", "LIVE monitoring stopped"));
    } else {
      const username = $("#usernameInput").value.trim();
      if (!username) return toast(tx("Hiányzik a felhasználónév", "Username missing"), tx("Írd be a TikTok-nevedet.", "Enter your TikTok username."), "error");
      state = await window.craftlive.connect(username);
      if (state.status.tiktok === "online") {
        toast(tx("TikTok LIVE kapcsolódva", "TikTok LIVE connected"), `@${state.config.username}`);
      } else {
        toast(tx("Automatikus LIVE-figyelés elindult", "Automatic LIVE monitoring started"), tx("A CraftLive kapcsolódik, amint elindul a LIVE.", "CraftLive will connect as soon as the LIVE starts."));
      }
    }
    render();
  } catch (error) {
    toast(tx("Nem sikerült elindítani a figyelést", "Could not start monitoring"), error.message || String(error), "error");
  }
}

async function testSlot(slotId) {
  try {
    state = await window.craftlive.testSlot(slotId);
    render();
    toast(
      tx("LIVE nélküli teszt elindítva", "LIVE-free test started"),
      state.status.minecraft === "online"
        ? `${tx("A CraftLive ezt a verziót használja:", "CraftLive is using this version:")} ${minecraftVersionLabel(state.config.minecraftVersion)}.`
        : tx("A parancs várakozik, amíg elindul a Minecraft.", "The action is queued until Minecraft starts.")
    );
  } catch (error) {
    toast(tx("A teszt nem sikerült", "Test failed"), error.message || String(error), "error");
  }
}

function openSettings() {
  $("#appLanguageText").textContent = tx(
    "Alkalmazás nyelve: magyar, a Windows beállítása alapján.",
    "Application language: English, based on Windows settings."
  );
  $("#settingsUsername").value = state.config.username || "";
  $("#autoConnect").checked = state.config.autoConnect;
  $("#updateManifestUrl").value = state.config.updateManifestUrl || "";
  renderUpdatePanel();
  $("#settingsDialog").showModal();
}

async function saveHostingPanel() {
  try {
    state = await window.craftlive.saveSettings({
      username: state.config.username,
      autoConnect: state.config.autoConnect,
      updateManifestUrl: state.config.updateManifestUrl,
      hostingPanelUrl: $("#hostingPanelUrl").value
    });
    render();
    toast(tx("Falix-cím elmentve", "Falix URL saved"));
  } catch (error) {
    toast(tx("Mentési hiba", "Save error"), error.message || String(error), "error");
  }
}

function openFalixPanel() {
  window.craftlive.openExternal(state.config.hostingPanelUrl || "https://client.falixnodes.net/");
}

async function checkUpdateFromUi() {
  if (["downloading", "installing"].includes(state.update.status)) return;
  if (state.update.availableVersion) {
    populateUpdateDialog(state.update);
    if (!$("#updateDialog").open) $("#updateDialog").showModal();
    return;
  }
  try {
    state = await window.craftlive.checkUpdate(state.config.updateManifestUrl);
    render();
    if (state.update.status === "current") {
      toast(tx("A CraftLive naprakész", "CraftLive is up to date"), `v${state.update.currentVersion}`);
    }
  } catch (error) {
    render();
    toast(tx("A frissítés ellenőrzése nem sikerült", "Update check failed"), error.message || String(error), "error");
  }
}

async function init() {
  state = await window.craftlive.getState();
  applyStaticLanguage();
  render();
  window.craftlive.onState((nextState) => {
    state = nextState;
    render();
  });

  $("#connectButton").addEventListener("click", handleConnect);
  $("#usernameInput").addEventListener("keydown", (event) => { if (event.key === "Enter") handleConnect(); });
  $("#searchInput").addEventListener("input", renderSlots);
  $("#filterSelect").addEventListener("change", renderSlots);
  $("#openSettingsButton").addEventListener("click", openSettings);
  $("#interactionsTabButton").addEventListener("click", () => showView("interactions"));
  $("#hostingTabButton").addEventListener("click", () => showView("hosting"));
  $("#saveHostingButton").addEventListener("click", saveHostingPanel);
  $("#openFalixButton").addEventListener("click", openFalixPanel);
  $("#openFalixSecondaryButton").addEventListener("click", openFalixPanel);
  $("#openSupportButton").addEventListener("click", () => $("#supportDialog").showModal());
  $("#sidebarUpdateButton").addEventListener("click", checkUpdateFromUi);
  $("#clearLogButton").addEventListener("click", async () => { state = await window.craftlive.clearLog(); render(); });

  $("#slotsGrid").addEventListener("click", async (event) => {
    const button = event.target.closest("button[data-action]");
    const card = event.target.closest("[data-slot-id]");
    if (!button || !card) return;
    const slot = state.config.slots.find((candidate) => candidate.id === card.dataset.slotId);
    if (!slot) return;
    if (button.dataset.action === "edit") openEditor(slot);
    if (button.dataset.action === "test") testSlot(slot.id);
    if (button.dataset.action === "duplicate") {
      try {
        state = await window.craftlive.duplicateSlot(slot.id);
        render();
        toast(tx("Interakció lemásolva", "Interaction duplicated"), tx("Az első szabad, kikapcsolt helyre került.", "It was placed in the first free disabled slot."));
      } catch (error) {
        toast(tx("Nem másolható", "Cannot duplicate"), error.message || String(error), "error");
      }
    }
    if (button.dataset.action === "toggle") {
      state = await window.craftlive.saveSlot({ ...slot, enabled: !slot.enabled });
      render();
    }
  });

  $("#triggerPicker").addEventListener("click", (event) => {
    const button = event.target.closest("[data-trigger]");
    if (!button) return;
    selectedTrigger = button.dataset.trigger;
    if (selectedTrigger === "like" && Number($("#triggerMinCount").value) === 1) $("#triggerMinCount").value = 100;
    updateTriggerEditor();
  });

  $("#presetRow").addEventListener("click", (event) => {
    const button = event.target.closest("[data-preset]");
    if (!button) return;
    const preset = latestPresets()[button.dataset.preset];
    if (!preset) return;
    $("#slotName").value = preset.name;
    $("#slotCommands").value = preset.commands;
    toast(tx("Legújabb verzióhoz készült sablon", "Latest-version preset"), `Minecraft ${state.config.minecraftVersion} • ${tx("Még szabadon módosíthatod.", "You can still customize it.")}`);
  });

  $("#mobSearch").addEventListener("input", renderMobCatalog);
  $("#mobCategory").addEventListener("change", renderMobCatalog);
  $("#insertMobButton").addEventListener("click", useSelectedMob);
  $("#mobSelect").addEventListener("dblclick", useSelectedMob);
  $("#supportFollowButton").addEventListener("click", () => {
    window.craftlive.openExternal("https://www.tiktok.com/@venom_hun_");
  });

  $("#updateDialog").addEventListener("cancel", (event) => event.preventDefault());
  $("#showUpdateButton").addEventListener("click", () => {
    populateUpdateDialog(state.update);
    if (!$("#updateDialog").open) $("#updateDialog").showModal();
  });
  $("#checkUpdateButton").addEventListener("click", checkUpdateFromUi);
  $("#installUpdateButton").addEventListener("click", async () => {
    const selections = Object.fromEntries(
      $$(`[data-update-feature]`, $("#updateFeatureList")).map((input) => [input.dataset.updateFeature, input.checked])
    );
    try {
      state = await window.craftlive.installUpdate(selections);
      render();
    } catch (error) {
      render();
      toast(tx("A frissítés telepítése nem sikerült", "Update installation failed"), error.message || String(error), "error");
    }
  });

  $("#recentGiftsList").addEventListener("click", (event) => {
    const button = event.target.closest("[data-gift-value]");
    if (!button) return;
    $("#triggerValue").value = button.dataset.giftValue;
    $("#triggerMode").value = "exact";
    toast(tx("Ajándék kiválasztva", "Gift selected"), button.dataset.giftValue);
  });

  $(".placeholder-row").addEventListener("click", (event) => {
    const button = event.target.closest("[data-placeholder]");
    if (!button) return;
    const textarea = $("#slotCommands");
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    textarea.setRangeText(button.dataset.placeholder, start, end, "end");
    textarea.focus();
  });

  $("#editorForm").addEventListener("submit", async (event) => {
    if (event.submitter?.value === "cancel") return;
    event.preventDefault();
    const slot = slotFromEditor();
    if (!slot.name || !slot.commands.trim()) return toast(tx("Hiányos interakció", "Incomplete interaction"), tx("Adj nevet és legalább egy műveletet.", "Add a name and at least one action."), "error");
    state = await window.craftlive.saveSlot(slot);
    $("#editorDialog").close();
    render();
    toast(tx("Interakció elmentve", "Interaction saved"), slot.name);
  });
  $("#editorTestButton").addEventListener("click", async () => {
    editingSlot = slotFromEditor();
    state = await window.craftlive.saveSlot(editingSlot);
    await testSlot(editingSlot.id);
  });
  $("#resetSlotButton").addEventListener("click", async () => {
    if (!editingSlot) return;
    state = await window.craftlive.resetSlot(editingSlot.id);
    $("#editorDialog").close();
    render();
    toast(tx("A hely kiürítve", "Slot cleared"));
  });

  $("#settingsForm").addEventListener("submit", async (event) => {
    if (event.submitter?.value === "cancel") return;
    event.preventDefault();
    try {
      state = await window.craftlive.saveSettings({
        username: $("#settingsUsername").value,
        autoConnect: $("#autoConnect").checked,
        updateManifestUrl: $("#updateManifestUrl").value,
        hostingPanelUrl: state.config.hostingPanelUrl
      });
      $("#settingsDialog").close();
      render();
      toast(tx("Beállítások elmentve", "Settings saved"));
    } catch (error) {
      toast(tx("Mentési hiba", "Save error"), error.message || String(error), "error");
    }
  });
  $("#exportButton").addEventListener("click", async () => {
    const result = await window.craftlive.exportConfig();
    if (!result.canceled) toast(tx("Biztonsági mentés elkészült", "Backup created"));
  });
  $("#importButton").addEventListener("click", async () => {
    try {
      const result = await window.craftlive.importConfig();
      if (!result.canceled) {
        state = result.state;
        render();
        toast(tx("Beállítások betöltve", "Settings imported"));
      }
    } catch (error) {
      toast(tx("Nem olvasható a fájl", "Cannot read file"), error.message || String(error), "error");
    }
  });

  document.addEventListener("keydown", (event) => {
    if (event.ctrlKey && event.key.toLocaleLowerCase() === "s" && $("#editorDialog").open) {
      event.preventDefault();
      $("#editorForm").requestSubmit();
    }
  });
}

init().catch((error) => toast(tx("Az alkalmazás nem indult el", "Application failed to start"), error.message || String(error), "error"));
