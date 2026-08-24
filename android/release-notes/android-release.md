# CraftLive Android 0.1.5

- Javítva az automatikus Bedrock-chatmegnyitás a fekvő Minecraftban.
- A CraftLive megvárja, amíg a visszanyitott játék ténylegesen fogadja az érintéseket.
- A valós ablakméret és a gyártói tájolás alapján több biztonságos képernyőgeometriát próbál.
- A rendszer által megszakított automatikus érintést önállóan újrapróbálja.

---

- Fixed automatic Bedrock chat opening in landscape Minecraft.
- CraftLive waits until the resumed game can actually receive touches.
- It tries safe screen geometries based on the real window size and vendor orientation reporting.
- System-cancelled automatic gestures are retried automatically.
