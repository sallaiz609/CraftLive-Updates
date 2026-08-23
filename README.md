# CraftLive

Ingyenes, JAR és Minecraft mod nélkül futó TikTok LIVE–Minecraft interakciós
Windows alkalmazás.
Húsz külön helyet ad, egy helyen belül pedig legfeljebb 50 egymás után futó
Minecraft-parancs állítható be. A nyers parancsmező miatt az alkalmazás nincs
egy rövid, előre megadott műveletlistára korlátozva.

## Mit tud?

- TikTok-ajándék, like, követés, megosztás, komment, belépés és feliratkozás.
- A LIVE indulásának automatikus figyelése és kapcsolódás 15 másodpercen belül.
- A LIVE vége után automatikus várakozás a következő közvetítésre.
- LIVE nélküli teszt minden interakcióhoz; csak a Minecraftnak kell futnia.
- Szabadon megadható Minecraft Java-verzió 1.7.x-től az aktuális kiadásig.
- Verziófüggő, kereshető mobválasztó: csak az adott kiadásban már elérhető
  vanilla mobokat mutatja, és a summon parancsot automatikusan elkészíti.
- Verziófüggő beépített parancssablonok a régi, modern JSON és modern SNBT
  parancsformátumokhoz.
- 20 ki- és bekapcsolható interakciós hely.
- Több parancs egy eseményhez, parancsonkénti késleltetéssel.
- Fix globális túlterhelés-védelem: két interakció között mindig legalább
  5 másodperc telik el. Ez nem kapcsolható ki és nem állítható át.
- Korlátozott, sorrendtartó interakciós sor: nagy LIVE-terhelésnél sem engedi a
  Minecraft-parancsokat egyszerre a játékra zúdulni.
- Várakozási idő, százalékos esély és minimum darabszám.
- `{user}`, `{gift}`, `{amount}`, `{coins}` és `{comment}` változók.
- Kész sablonok, kereső, szűrő, tesztgomb, élő napló és biztonsági mentés.
- Minecraft Java Edition; Forge és vanilla klienssel is használható.
- Nem kell JAR, plugin, datapack vagy külön Minecraft-kiegészítő.
- A felület nyelve a Windows nyelvéhez igazodik: magyar Windows esetén magyar,
  más Windows-nyelvnél angol.
- Beépített CraftLive-frissítésfigyelés: új kiadásnál kötelező frissítési ablak,
  kiadási jegyzetek, ellenőrzött letöltés és automatikus újraindítás.
- Az új kiadás opcionális funkcióiról a felhasználó külön kapcsolókkal dönthet;
  a választások csak az új verzió indulásakor lépnek életbe.
- Opcionális támogatói feliratkozás-figyelő: a LIVE alatt számolja a fizetős
  TikTok-feliratkozásokat, megmutatja a legutóbbi támogatót, és egy gombbal
  megnyitja a beállított TikTok-profilt. A funkció később is kikapcsolható.
- Fix Frissítés gomb a bal oldali menüben, közvetlenül a Beállítások alatt.
  Mindig látható, és kézi ellenőrzést indít vagy megnyitja az elérhető frissítést.

## Windows-telepítő készítése

Node.js 22 és .NET 8 SDK telepítése után nyisd meg a projekt mappáját egy
parancssorban, majd futtasd:

```text
npm ci --no-audit --no-fund
npm run dist:win
```

A buildhez .NET 8 SDK szükséges, mert a Minecraft vezérlését végző kis natív
Windows-összetevő is ekkor készül el. A futó CraftLive nem indít PowerShellt.

A kész `CraftLive-Setup-0.6.4.exe`, `latest.yml` és
`CraftLive-Setup-0.6.4.exe.blockmap` a `KESZ` mappába kerül. A Setup EXE-t csak
egyszer kell lefuttatni; ezután a CraftLive telepített alkalmazásként, asztali
és Start menü parancsikonnal használható. Ha nincs megfelelő Node.js a gépen,
a script egy hordozható példányt tölt le. Java, Gradle vagy Git nem szükséges a
fordításhoz.

Ha a folyamat megszakad, a pontos npm-hiba a projekt gyökerében található
`build-install.log` vagy `build-package.log` fájlba kerül.

## Hogyan működik?

A Windows alkalmazás fogadja a TikTok LIVE-eseményeket, a beépített natív
Windows-összetevővel megkeresi a futó Minecraft-ablakot, majd megnyitja a chatet,
beilleszti a parancsot és elküldi. Emiatt a világban engedélyezni kell a
csalásokat/parancsokat, a chat billentyűjének pedig T-nek kell maradnia.

Az alkalmazás futás közben nem használ PowerShellt, kódolt parancsot vagy
végrehajtásiházirend-megkerülést. A régi ZIP-es önfrissítő nincs benne: a
frissítés kizárólag a telepített alkalmazás GitHubos frissítőjével történik.

Az események egy közös, sorrendtartó sorba kerülnek. Egy interakció összetartozó
parancsai lefutnak, majd a következő interakció csak legalább 5 másodperc múlva
indulhat. A fix időköz a LIVE-eseményekre és a tesztgombra is érvényes, nem
felhasználói beállítás. Ha a biztonsági sor megtelik, az új eseményt az app
kihagyja ahelyett, hogy korlátlan memóriahasználatot vagy parancsáradatot
okozna.

A Minecraft-verzió a bal oldalsáv fix választójában írható be. A választás az ezután
beillesztett sablonok parancsformátumát szabja meg; a saját, korábban elmentett
nyers parancsokat az alkalmazás biztonsági okból nem módosítja automatikusan.

Az interakció szerkesztőjében a mobokat név és kategória szerint lehet keresni.
A lista a kiválasztott verzióhoz igazodik: például a Warden 1.18.2-ben még nem,
1.19-től viszont megjelenik. A darabszám és a megjelenési hely kiválasztása után
a CraftLive állítja elő a szükséges parancsot. A Haladó részben továbbra is
megadható bármilyen saját parancs. A lista a vanilla Java Edition mobjait
tartalmazza; modok egyedi mobjai csak saját nyers paranccsal használhatók.

## CraftLive-frissítések kiadása

A 0.6.0-tól a frissítést a telepített alkalmazás kezeli. Induláskor, majd 10
percenként lekéri a `sallaiz609/CraftLive-Updates` GitHub-tárház legfrissebb
kiadását. Új verziónál kötelező ablakot mutat; a felhasználó csak az opcionális
funkciókról dönt. A letöltés után a telepítő csendben lecseréli az alkalmazást,
majd az új verziót automatikusan elindítja. A verzió-összehasonlítás a tényleges
`app.getVersion()` értéket használja, ezért a már telepített verziót nem ajánlja
fel újra.

Minden új GitHub Release Assets részéhez pontosan az ugyanabból a buildből
származó három fájlt kell feltölteni:

- `CraftLive-Setup-X.Y.Z.exe`
- `CraftLive-Setup-X.Y.Z.exe.blockmap`
- `latest.yml`

A kiadás címkéje legyen `vX.Y.Z`, ne legyen Draft vagy Pre-release, és legyen
Latest kiadás. A `latest.yml` fájlt nem kell kézzel szerkeszteni: a build
automatikusan a telepítőhöz tartozó verziót és SHA-512 ellenőrzőösszeget írja
bele. Régi `latest.yml` és új Setup EXE nem keverhető, mert az ellenőrzés
szándékosan leállítja a telepítést.

A mellékelt GitHub Actions munkafolyamat kézzel is indítható. Az Actions lapon
a **CraftLive Windows installer → Run workflow** egyetlen futása lefordítja a
natív vezérlőt, elkészíti a három frissítési fájlt, létrehozza a csomag verziója
szerinti címkét, majd Latest kiadásként közzéteszi. A fájlokat nem kell külön
letölteni és visszatölteni.

Az opcionális funkciók leírása továbbra is a beépített
`src/update-config.js` szerinti `latest.json` fájlból olvasható. Ha annak
`version` mezője megegyezik a GitHub-kiadás verziójával, a funkciókapcsolók és
a lokalizált leírások megjelennek. Ha nem egyezik, a frissítés akkor is működik,
csak új funkciókapcsoló nem jelenik meg.

A 0.6.0-ra egyszer kézzel kell átállni a Setup EXE futtatásával, mert a korábbi
0.5.x ZIP-változat saját futó fájljait nem tudja minden gépen megbízhatóan
lecserélni. A telepítés után kizárólag a telepített parancsikont kell indítani;
a régi kicsomagolt mappa törölhető. A támogatói eseményeket csak az aktív
LIVE-kapcsolat ideje alatt tudja érzékelni; a TikTok korábbi feliratkozóinak
teljes listáját nem tölti le.

## Ingyenesség és korlát

Az alkalmazásban nincs előfizetés és fizetős interakciós szint. A TikTok LIVE
nem kínál nyilvános, hivatalos ajándékesemény-API-t, ezért a projekt a
`tiktok-live-connector` közösségi kapcsolatát használja annak ingyenes
közösségi korlátain belül. Ha a TikTok megváltoztatja a LIVE protokollját, az
alkalmazást frissíteni kellhet.

## Licenc

AGPL-3.0-only. A részletek a `LICENSE` és `THIRD_PARTY_NOTICES.md` fájlokban
találhatók.
