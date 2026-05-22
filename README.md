# Movix TV

Application **Android TV** native (Kotlin + Leanback + Media3/ExoPlayer) adaptée du site open-source [MovixOpenSource](https://github.com/movixcorp/MovixOpenSource).

Pensée pour la **télécommande** Fire TV / Android TV : navigation case-par-case D-pad, focus géré nativement par les composants Leanback.

## Installation sur Fire TV (Downloader)

1. Sur ta Fire TV : `Paramètres → My Fire TV → Developer options → Apps from Unknown Sources` → **ON**
2. Installe l'app **Downloader** depuis l'App Store Amazon
3. Ouvre Downloader → tape l'URL de l'APK (release GitHub)
4. L'APK se télécharge et s'installe automatiquement
5. Movix TV apparaît dans la rangée « Vos applis » du launcher

## Stack

| Couche | Techno |
|---|---|
| UI TV | AndroidX **Leanback** (BrowseSupport, DetailsSupport, SearchSupport) |
| Lecteur | **Media3 / ExoPlayer** + LeanbackPlayerAdapter (HLS, DASH, MP4) |
| Réseau | Retrofit + OkHttp + Moshi + Coroutines |
| Catalogue | TMDB (popular, trending, top rated, tv) |
| Sources stream | API publique Movix `https://api.movix.health` |

## Architecture

```
data/
  ApiConfig.kt        URLs TMDB + Movix
  ApiClient.kt        Retrofit/OkHttp/Moshi
  TmdbApi.kt          popular/trending/details/seasons
  MovixApi.kt         /api/search + /api/tmdb/{type}/{id}
  Repository.kt       wrapper coroutines
  *Models.kt

MainFragment          rows TMDB
SearchFragment        clavier TV → Movix /api/search
VideoDetailsFragment  bouton Regarder, sélection saison/épisode
PlaybackVideoFragment ExoPlayer Leanback
```

## Limites connues

- **Sources "embed"** : Movix renvoie souvent des URLs `/embed/...` (pages HTML iframe), non lisibles par ExoPlayer. Il faudra brancher un extracteur (proxy `proxiesembed` ou WebView headless) pour récupérer le `.m3u8` final.
- **Clé TMDB v3** publique embarquée dans `ApiConfig.kt` — à remplacer par la tienne si révoquée.
- Schéma JSON de `/api/tmdb/{type}/{id}` modélisé d'après le code backend ; à ajuster après tests live si la réponse diffère.

## Build local

```sh
./gradlew :app:assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

## Licence

Code source dérivé de Movix sous **CC BY-NC 4.0** (non-commercial). Ce projet en hérite.
