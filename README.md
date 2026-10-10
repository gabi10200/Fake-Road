# FakeRoute – trajet GPS simulé (Android)

Appli Kotlin minimaliste : tu poses des points sur une carte OpenStreetMap (partout dans le monde),
tu règles la vitesse (1 à 1000 km/h, modifiable en direct) et l'appli envoie une position fictive
chaque seconde au système (GPS + réseau) via l'API officielle de « position fictive » d'Android.

## Compiler
1. Installe Android Studio, puis `File > Open` sur ce dossier (il télécharge Gradle tout seul).
2. Branche ton téléphone (débogage USB) ou lance un émulateur, puis `Run`.

## Activer la position fictive (indispensable)
1. Active les **Options pour développeurs** (Paramètres > À propos > touche 7 fois « Numéro de build »).
2. Options pour développeurs > **Sélectionner l'application de position fictive** > FakeRoute.
3. Dans l'appli : touche la carte pour poser ≥ 2 points, règle la vitesse, **Démarrer**.

## Notes
- Les points sont reliés en ligne droite (pas de routage sur les routes).
- Un trajet très long à vitesse réaliste dure très longtemps : monte la vitesse ou utilise « Boucler ».
- Utilisation prévue : tests d'applis, confidentialité, démos. Beaucoup d'applis (jeux, banques, VTC)
  détectent les positions fictives et peuvent bannir le compte : à utiliser à tes risques.
