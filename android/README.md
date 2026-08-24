# CraftLive Android

Android-first CraftLive client for controlling a local Minecraft Bedrock world on the same phone. It does not require a hosted Minecraft server.

## Included

- Local Bedrock WebSocket bridge bound only to `127.0.0.1:19134`.
- Background command delivery without opening or typing into Minecraft chat for every event.
- TikTok LIVE gift, like, follow, subscription, share and comment event adapter.
- 20 editable Standard interaction slots.
- PLUS tab unlocked after 5 hours of verified connected TikTok LIVE time.
- Fixed, non-optional 5-second delay between Minecraft interactions.
- Test mode that works before starting a LIVE.
- Hungarian or English UI based on the Android system language.
- Visible app version and startup update check.
- In-app APK download, SHA-256 verification, and Android installer hand-off.
- GitHub Actions workflow for signed APK releases.

Read [README_HU.md](README_HU.md) for the Hungarian setup and release guide.

## Important limitations

- Commands/cheats and WebSocket connections must be enabled in the Minecraft world.
- Minecraft must connect to the local bridge with `/wsserver ws://127.0.0.1:19134` after a full Minecraft or bridge restart.
- A local unencrypted `ws://` connection requires Minecraft's encrypted-WebSocket requirement to be disabled.
- Android requires user confirmation for every APK installation. Silent updates are not available to normal sideloaded apps.
- TikTok LIVE connectivity uses a reverse-engineered, unofficial API and can require connector updates when TikTok changes its protocol.
- GitHub sideload distribution is the intended first release path.

## Build

Use Android Studio with JDK 17, or run the included GitHub Actions workflow. The workflow needs the four signing secrets described in `README_HU.md`.
