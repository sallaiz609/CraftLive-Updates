# CraftLive Android 0.1.8

- A Bedrock chat megnyitása után a CraftLive külön a tényleges szövegmezőre fókuszál.
- Többlépcsős parancsbevitel: `commitText`, composing text és virtuális billentyűesemény tartalék módszer.
- Enter csak akkor kerül elküldésre, ha a parancs ténylegesen bekerült vagy a nyers billentyűeseményeket a mező fogadta.
- Sikertelen bevitelkor a chat automatikusan bezáródik.
- Sikeres interakciónál a chat csak röviden villan fel.
- Az engedélyek állapotának megőrzése és a kézi chatgomb-kalibráció változatlanul megmaradt.

## English

- CraftLive explicitly focuses Bedrock's actual chat input field after opening chat.
- Multi-stage input uses `commitText`, composing text and virtual key events as fallbacks.
- Enter is sent only after verified input or accepted raw key events.
- Chat closes automatically when text entry fails.
- On success, chat only flashes briefly.
- Permission-state preservation and manual chat-button calibration remain available.
