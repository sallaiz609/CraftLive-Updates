# CraftLive Android – helyi Bedrock

Ez a külön Android-alkalmazás a telefonon futó Minecraft Bedrockot vezérli. Nem indít Minecraft-szervert és nem használja a Java-hostingot.

## Első beállítás a telefonon

1. Telepítsd a legújabb `CraftLive-Android-*.apk` fájlt a GitHub-kiadásból.
2. Nyisd meg a CraftLive-ot, majd engedélyezd az értesítéseket.
3. A Minecraft világában engedélyezd a csalásokat és a WebSocket-kapcsolatokat.
4. A CraftLive-ban másold ki a kapcsolási parancsot.
5. Nyisd meg a Minecraft világ chatjét, és küldd el ezt: `/wsserver ws://127.0.0.1:19134`.
6. Ha a CraftLive „Minecraft kapcsolódva” állapotot mutat, a Bedrock teszt és a LIVE-interakciók már a háttérben futnak.

A Minecraft teljes bezárása vagy a CraftLive-híd újraindítása után a kapcsolási parancsot ismét el kell küldeni. A világ beállításaiban a WebSocket-kapcsolatok legyenek engedélyezve; helyi `ws://` kapcsolatnál a titkosított WebSocket kötelezővé tétele legyen kikapcsolva.

A kisegítő lehetőség, a külön CraftLive-billentyűzet és a chatgomb kalibrálása az új WebSocket-hídhoz nem szükséges.

## Egytelefonos TikTok LIVE-közvetítés (béta)

A 0.4.0-s verzióban a CraftLive a telefonon futó Minecraft képét közvetítheti, és csak a továbbított adás jobb oldalára rajzolja rá az aktív interakciókat. A játékos a saját kijelzőjén nem látja ezt a sávot. Az ajándékos soroknál az ajándék képe jelenik meg, mellette csak a Minecraft-esemény, például `→ zombi`.

Ehhez olyan TikTok-fiók szükséges, amelynél a TikTok megad közvetítési szervercímet és közvetítési kulcsot. A CraftLive-ban:

1. Írd be a TikToktól kapott `rtmp://` vagy `rtmps://` szervercímet.
2. Írd be a közvetítési kulcsot.
3. Nyomd meg a **Közvetítés indítása** gombot, és engedélyezd a képernyő, illetve a hang továbbítását.
4. A Minecraft automatikusan megnyílik. Az adás leállításához térj vissza a CraftLive-ba.

A CraftLive a közvetítési kulcsot nem menti el és nem írja naplóba. A funkció béta: az első éles LIVE előtt rövid, nem nyilvános próba ajánlott. A TikTok-fiókhoz tartozó közvetítési kulcsot soha ne töltsd fel GitHubra, és ne küldd el másnak.

## GitHubos, alkalmazáson belüli frissítés

Az alkalmazás induláskor ellenőrzi ezt a fájlt:

`https://raw.githubusercontent.com/sallaiz609/CraftLive-Updates/main/android-latest.json`

Ha nagyobb `versionCode` található benne, magyar vagy angol változáslistát mutat, letölti az APK-t, ellenőrzi a SHA-256 értékét, majd megnyitja az Android telepítőjét. A telepítést a felhasználónak egyszer jóvá kell hagynia; ezt az Android nem engedi megkerülni.

Nagyon fontos: minden Android-kiadást ugyanazzal a JKS kiadási kulccsal kell aláírni. Ha elveszik vagy megváltozik a kulcs, a már telepített alkalmazás nem frissíthető rá.

## A kiadási kulcs egyszeri létrehozása

Windows PowerShellben, a projekt mappájából:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\create-android-signing-key.ps1
```

A létrejövő `signing/craftlive-release.jks` fájlt és a jelszót legalább két biztonságos, privát helyen őrizd meg. A `signing` mappát soha ne töltsd fel GitHubra.

A GitHub-tárolóban menj ide: Settings → Secrets and variables → Actions → New repository secret. Add hozzá:

- `ANDROID_KEYSTORE_BASE64`: a `signing/ANDROID_KEYSTORE_BASE64.txt` teljes tartalma;
- `ANDROID_KEYSTORE_PASSWORD`: a megadott jelszó;
- `ANDROID_KEY_ALIAS`: `craftlive`;
- `ANDROID_KEY_PASSWORD`: ugyanaz a jelszó, ha a mellékelt szkriptet használtad.

## Új Android-verzió kiadása

1. Írd át a magyar és angol változáslistát a `release-notes/android-hu.txt` és `android-en.txt` fájlban.
2. GitHubon nyisd meg az Actions fület.
3. Válaszd a „CraftLive Android release” folyamatot.
4. Kattints a „Run workflow” gombra.
5. Írd be az új verziót, például `0.1.1`, és válaszd ki, kötelező-e.
6. A folyamat teszteli és aláírja az APK-t, létrehozza az `android-v0.1.1` kiadást, feltölti az APK-t, majd frissíti az `android-latest.json` fájlt.

Ezután a már telepített Android-app automatikusan észleli az új kiadást.

A közös `CraftLive-Updates` tárolóban az Android-projekt az `android/` mappába kerül, a két workflow pedig a gyökér `.github/workflows/` mappájába. Így a meglévő Windows-forrás és a Windows-frissítő nem íródik felül.

## Biztonság és korlátok

- A WebSocket-kiszolgáló kizárólag a telefon helyi `127.0.0.1` címén figyel; más hálózati eszköz nem éri el.
- A parancs csak aktív Minecraft WebSocket-kapcsolatnál kerül elküldésre.
- A PLUS romboló parancsai világvesztést okozhatnak, ezért készíts biztonsági másolatot.
- A TikTok-kapcsolat nem hivatalos, visszafejtett protokollt használ; TikTok-változáskor a csatlakozót frissíteni kellhet.
- Az egytelefonos közvetítő módhoz a TikTok által kiadott RTMP/RTMPS szervercím és kulcs szükséges; ez nem minden TikTok-fiókban érhető el.
