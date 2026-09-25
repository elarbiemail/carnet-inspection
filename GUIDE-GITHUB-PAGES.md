# Publier l'application sur GitHub Pages (gratuit)

GitHub Pages permet d'héberger gratuitement votre application à une adresse
fixe (`https://votre-nom.github.io/carnet-inspection/`), accessible depuis
n'importe quel appareil, et installable comme une vraie application sur
Android. Aucune ligne de commande n'est nécessaire — tout se fait dans le
navigateur.

## Étape 1 — Créer un compte GitHub (si vous n'en avez pas)

1. Allez sur **https://github.com/** et cliquez sur **« Sign up »**.
2. Suivez les instructions (e-mail, mot de passe, nom d'utilisateur). C'est
   gratuit.

## Étape 2 — Créer un dépôt (« repository »)

1. Une fois connecté, cliquez sur le **« + »** en haut à droite, puis
   **« New repository »**.
2. Donnez-lui un nom, par exemple `carnet-inspection`.
3. Laissez-le en **« Public »** (nécessaire pour la version gratuite de
   GitHub Pages).
4. Ne cochez rien d'autre (pas de README), puis cliquez sur
   **« Create repository »**.

## Étape 3 — Envoyer les fichiers de l'application

1. Sur la page de votre nouveau dépôt (vide), cliquez sur le lien
   **« uploading an existing file »** (ou allez dans l'onglet **« Add file »
   → « Upload files »**).
2. Faites glisser (ou sélectionnez) **tous les fichiers** fournis :
   - `index.html`
   - `manifest.json`
   - `sw.js`
   - `icon-192.png`
   - `icon-512.png`
   - `icon-512-maskable.png`
   - `icon-180.png`
   - `favicon-32.png`

   ⚠️ Important : déposez les fichiers **à la racine** du dépôt (pas dans un
   sous-dossier), pour que les liens entre eux (icônes, manifeste) fonctionnent.
3. En bas de page, cliquez sur **« Commit changes »** (le message par défaut
   convient très bien).

## Étape 4 — Activer GitHub Pages

1. Dans votre dépôt, allez dans l'onglet **« Settings »** (en haut).
2. Dans le menu de gauche, cliquez sur **« Pages »**.
3. Sous **« Build and deployment » → « Source »**, choisissez
   **« Deploy from a branch »**.
4. Sous **« Branch »**, sélectionnez **`main`** et le dossier **`/ (root)`**,
   puis cliquez sur **Save**.
5. Attendez 1 à 2 minutes. Rafraîchissez la page : une bannière verte
   indique l'adresse de votre site, du type :
   `https://votre-nom.github.io/carnet-inspection/`

C'est cette adresse que vous utiliserez pour ouvrir l'application, et que
vous indiquerez à l'étape 4 du guide Google Drive
(`GUIDE-GOOGLE-DRIVE.md`) — uniquement le domaine `https://votre-nom.github.io`
(sans le reste) comme « origine JavaScript autorisée ».

## Étape 5 — Ouvrir l'application et l'installer sur votre téléphone

1. Ouvrez l'adresse ci-dessus dans **Chrome** sur votre téléphone Android.
2. Ouvrez le menu du navigateur (⋮ en haut à droite) et choisissez
   **« Installer l'application »** (ou « Ajouter à l'écran d'accueil »).
3. Une icône dédiée à l'application (et non plus celle de Claude ou de
   Chrome) apparaît sur votre écran d'accueil, et l'application s'ouvre
   désormais en plein écran, indépendamment de tout navigateur ou
   application tierce.

## Mettre à jour l'application plus tard

Si vous (ou moi, dans une future session) modifiez `index.html` : ouvrez le
fichier dans votre dépôt GitHub, cliquez sur l'icône crayon (✏️) en haut à
droite pour l'éditer directement dans le navigateur, collez le nouveau
contenu, puis **« Commit changes »**. Le site se met à jour automatiquement
en une à deux minutes, à la même adresse — vos utilisateurs n'ont rien à
refaire (leurs données, elles, restent sur leur propre appareil/navigateur
ou dans leur propre Google Drive, elles ne sont jamais touchées par une
mise à jour du site).

## Vos données restent-elles en sécurité pendant tout ça ?

Oui. Le dépôt GitHub ne contient que le **code** de l'application (le même
pour tout le monde). Vos tâches, enseignants, établissements et documents
sont stockés uniquement dans le navigateur de votre appareil (et, si vous
l'activez, dans votre propre Google Drive) — jamais dans le dépôt GitHub ni
sur un serveur tiers.
