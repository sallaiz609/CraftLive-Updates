# CraftLive Android 0.1.3

- Javítva az az eset, amikor a Bedrock teszt után a Minecraft chat sem nyílt meg.
- A CraftLive kötelező fekvő módban fut, a Bedrock teszt pedig valóban zombit idéz.
- A koppintási hely a Minecraft tényleges fekvő kijelzőméretéből számolódik.
- A két gyakori mobil Bedrock HUD chatgombhelyét automatikusan kipróbálja.
- Az Enter, Küldés és Kész Android-műveleteket is kezeli a különböző telefonok miatt.
- A teszt rövid állapotüzenetekkel jelzi, hogy a chat vagy a parancsküldés hibázott-e.

---

- Fixed the case where Minecraft chat did not open after starting the Bedrock test.
- CraftLive now runs in mandatory landscape mode and the Bedrock test actually summons a zombie.
- Tap positions now use Minecraft's actual landscape display dimensions.
- CraftLive automatically tries both common mobile Bedrock HUD chat locations.
- Enter, Send and Done Android actions are supported for broader phone compatibility.
- Short test messages identify whether chat opening or command submission failed.
