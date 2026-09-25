# Créer le fichier .apk avec Android Studio

Ce dossier contient un **projet Android Studio prêt à ouvrir**, pas un fichier
.apk directement : la compilation d'une application Android nécessite le kit
de développement Android (SDK, outils de compilation) que je n'ai pas accès
depuis mon environnement de travail. Votre PC, avec Android Studio déjà
installé, a tout ce qu'il faut — la compilation prend 2 à 5 minutes, sans
écrire une seule ligne de code.

## Pourquoi ce projet n'est-il pas juste « l'appli dans une WebView » ?

Ce projet utilise une **Trusted Web Activity (TWA)** : au lieu d'afficher
l'application dans une fenêtre web embarquée classique (WebView), il l'ouvre
dans le vrai Chrome de votre téléphone, sans barre d'adresse — visuellement
identique à une application native. C'est un choix technique nécessaire, pas
esthétique : **Google refuse la connexion (Google Sign-In / Drive) dans une
WebView embarquée classique** ; elle ne fonctionne que dans un vrai
navigateur. Avec une TWA, la synchronisation Google Drive continue donc de
fonctionner exactement comme dans le navigateur.

## Étape 1 — Ouvrir le projet

1. Lancez Android Studio.
2. **File → Open…**, puis sélectionnez ce dossier (`twa-app`, celui qui
   contient `build.gradle`, `settings.gradle`, le dossier `app/`, etc.).
3. Laissez Android Studio synchroniser le projet (« Gradle sync », icône
   en bas). Cela peut demander une mise à jour automatique de Gradle/du
   plugin Android au premier lancement — acceptez, c'est normal, cela ne
   modifie rien à l'application elle-même.

## Étape 2 — Générer le fichier .apk

1. Menu **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
2. Une fois terminé, une notification apparaît en bas à droite avec un lien
   **« locate »** : cliquez dessus pour ouvrir le dossier contenant le
   fichier, généralement :
   `app/build/outputs/apk/debug/app-debug.apk`
3. Ce fichier `.apk` est utilisable immédiatement : envoyez-le sur votre
   téléphone Android (par câble, e-mail, ou Google Drive) et touchez-le
   pour l'installer (Android peut demander d'autoriser « Installer des
   applications inconnues » pour la source utilisée — c'est normal pour un
   fichier installé hors du Play Store).

C'est un APK de type « debug » : parfaitement fonctionnel pour votre usage
personnel. Il n'est pas nécessaire de le publier sur le Play Store pour
l'utiliser sur votre propre téléphone.

## Étape 3 (facultative) — Supprimer la fine barre d'adresse Chrome

Par défaut, l'application s'ouvre avec une très fine barre en haut (comme un
onglet Chrome simplifié) tant que Google n'a pas vérifié que vous êtes bien
propriétaire du site. Pour l'enlever complètement (rendu 100% natif) :

1. Vous avez besoin d'un dépôt GitHub **nommé exactement
   `elarbiemail.github.io`** (un dépôt « page utilisateur », différent de
   `carnet-inspection`) — car ce fichier de vérification doit être servi à
   la racine du domaine, pas dans un sous-dossier. Si vous ne l'avez pas
   encore, cette étape peut attendre : tout fonctionne déjà très bien sans.
2. Récupérez l'empreinte SHA-256 de la clé qui a signé votre APK. Pour l'APK
   de debug généré ci-dessus, ouvrez un terminal et lancez :
   ```
   keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
   (sur Mac/Linux : remplacez `%USERPROFILE%\.android\debug.keystore` par
   `~/.android/debug.keystore`). Copiez la ligne **SHA256:** (une longue
   série de paires de caractères séparées par `:`).
3. Dans le dépôt `elarbiemail.github.io`, créez le fichier
   `.well-known/assetlinks.json` avec ce contenu (remplacez
   `VOTRE_EMPREINTE_SHA256` par la valeur copiée, sans les `:`, ou avec —
   les deux formats sont acceptés) :
   ```json
   [{
     "relation": ["delegate_permission/common.handle_all_urls"],
     "target": {
       "namespace": "android_app",
       "package_name": "net.fm6education.carnetinspection",
       "sha256_cert_fingerprints": ["VOTRE_EMPREINTE_SHA256"]
     }
   }]
   ```
4. Republiez l'application (une nouvelle installation de l'APK suffit,
   inutile de recompiler). Après quelques minutes, la barre d'adresse
   disparaît automatiquement.

Notez qu'un APK « debug » est resigné à chaque machine/réinstallation dans
certains cas ; si la vérification cesse de fonctionner après un moment,
regénérez l'empreinte avec la commande ci-dessus et mettez à jour le
fichier `assetlinks.json`.

## Personnaliser (nom du dépôt, icône…)

- Si votre dépôt ou votre adresse GitHub Pages changent, modifiez les trois
  lignes en haut de `app/src/main/res/values/strings.xml`
  (`launch_url`, `host_name`, `path_prefix`), puis reconstruisez.
- L'icône de l'application est générée à partir de l'icône de l'application
  web (`icon-512.png`) — pour la changer, remplacez les fichiers dans
  `app/src/main/res/mipmap-*/ic_launcher.png` (une taille par dossier ;
  utilisez un outil comme https://icon.kitchen si vous changez de logo).
