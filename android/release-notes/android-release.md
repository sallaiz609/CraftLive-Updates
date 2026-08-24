# CraftLive Android 0.2.0

- Új helyi Bedrock WebSocket-híd a `127.0.0.1:19134` címen.
- Az interakciók a háttérben futnak: többé nem nyílik meg a Minecraft chat minden eseménynél.
- Külön, valós idejű jelzés mutatja, hogy a híd várakozik vagy a Minecraft kapcsolódott.
- Beépített gomb másolja a `/wsserver` kapcsolási parancsot.
- A teszt csak aktív Minecraft-kapcsolatnál kerül a sorba, ezért nincs több félrevezető „elküldve” üzenet.
- A korábbi kisegítő lehetőség és CraftLive-billentyűzet nem szükséges a normál működéshez.
- A kötelező, fix 5 másodperces interakciós védelem megmaradt.

## English

- New local Bedrock WebSocket bridge on `127.0.0.1:19134`.
- Interactions run in the background; Minecraft chat no longer opens for every event.
- A live status clearly shows whether the bridge is waiting or Minecraft is connected.
- A built-in button copies the `/wsserver` connection command.
- Tests are queued only when Minecraft is actually connected, avoiding false “sent” messages.
- The former accessibility helper and CraftLive keyboard are no longer required for normal use.
- The mandatory fixed five-second interaction safety delay remains active.
