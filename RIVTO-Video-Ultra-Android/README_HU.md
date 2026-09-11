# RIVTO Video Ultra Android v0.1

Ez egy Android projekt, amely:

1. importál egy videót a telefonról,
2. valós időben előnézetet mutat,
3. egyetlen **RIVTO ULTRA grafika** kapcsolóval ráteszi a képi presetet,
4. MP4-be exportálja,
5. a kész fájlt a **Movies/RIVTO** mappába menti.

## Mit csinál az ULTRA mód?

A jelenlegi v0.1 GPU-s Media3 effektekkel dolgozik:

- kontrollált kontrasztemelés,
- enyhe fényesség-korrekció,
- nagyobb színtelítettség,
- finom meleg filmes színkarakter.

A preset célja a korábban megbeszélt RIVTO-szerű, tisztább és látványosabb kép úgy, hogy mobilon is gyors legyen.

**Fontos:** ez videó-utófeldolgozás. Nem valódi DLSS, és nem tud új geometriát, textúrát vagy tükröződést létrehozni, ami az eredeti képkockán nincs jelen.

## Technika

- Android minSdk: 29 (Android 10)
- Java 17
- Jetpack Media3 1.11.0
- ExoPlayer előnézet
- Media3 Transformer export
- H.264 + AAC MP4
- MediaStore mentés a Galériába

## APK build

A `build-rivto-video-ultra.yml` GitHub Actions workflow debug APK-t készít és artifactként feltölti.
