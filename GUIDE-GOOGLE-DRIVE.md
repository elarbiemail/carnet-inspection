# Connecter l'application à votre Google Drive (gratuit, une seule fois)

Cette application peut sauvegarder vos tâches et documents dans **votre propre**
compte Google Drive, directement depuis votre navigateur. Elle n'a besoin
d'aucun serveur, n'est facturée à personne, et n'a accès **qu'aux fichiers
qu'elle crée elle-même** dans votre Drive (jamais le reste de votre compte).

Pour cela, il faut créer une seule fois un « identifiant client OAuth »
gratuit sur le site des développeurs Google. Cela prend environ 10 minutes
et ne demande aucune compétence technique particulière — suivez simplement
les étapes ci-dessous dans l'ordre.

⚠️ **Avant de commencer** : vous devez d'abord avoir déployé l'application
sur GitHub Pages (voir `GUIDE-GITHUB-PAGES.md`) pour connaître son adresse
finale (quelque chose comme `https://votre-nom.github.io/carnet-inspection/`).
Vous en aurez besoin à l'étape 4.

## Étape 1 — Créer un projet Google Cloud

1. Rendez-vous sur **https://console.cloud.google.com/** et connectez-vous
   avec votre compte Google (Gmail).
2. En haut de la page, cliquez sur le sélecteur de projet, puis sur
   **« Nouveau projet »**.
3. Donnez-lui un nom, par exemple `Carnet Inspection`, puis cliquez sur
   **Créer**. Attendez quelques secondes que le projet soit créé et
   sélectionné (en haut de la page).

## Étape 2 — Activer l'API Google Drive

1. Dans le menu de gauche (☰), allez dans **« API et services » → « Bibliothèque »**.
2. Recherchez **« Google Drive API »** et cliquez dessus.
3. Cliquez sur **Activer**.

## Étape 3 — Configurer l'écran de consentement OAuth

1. Toujours dans **« API et services »**, allez dans **« Écran de consentement OAuth »**.
2. Choisissez le type **« Externe »**, puis **Créer**.
3. Remplissez les champs obligatoires : nom de l'application (ex.
   `Carnet Inspection`), adresse e-mail d'assistance (votre e-mail), et en
   bas, l'e-mail de contact du développeur (le même).
4. Continuez (Enregistrer et continuer) jusqu'à l'étape **« Champs
   d'application »** (Scopes) : cliquez sur **« Ajouter ou supprimer des
   champs d'application »**, cherchez `drive.file`
   (*« Voir, modifier, créer et supprimer uniquement les fichiers Google
   Drive spécifiques que vous utilisez avec cette application »*), cochez-le,
   puis **Mettre à jour**.
5. À l'étape **« Utilisateurs test »**, cliquez sur **« Ajouter des
   utilisateurs »** et entrez **votre propre adresse Gmail** (celle que vous
   utiliserez avec l'application). C'est important : tant que l'application
   reste en mode « Test » (ce qui est très bien pour un usage personnel),
   seuls les comptes ajoutés ici peuvent se connecter.
6. Terminez l'assistant (Enregistrer et continuer, puis Retour au tableau de bord).

## Étape 4 — Créer l'identifiant client OAuth

1. Allez dans **« API et services » → « Identifiants »**.
2. Cliquez sur **« Créer des identifiants »** → **« ID client OAuth »**.
3. Type d'application : **« Application Web »**.
4. Donnez-lui un nom, par exemple `Carnet Inspection Web`.
5. Dans **« Origines JavaScript autorisées »**, cliquez sur **« Ajouter un URI »**
   et entrez l'adresse de votre application déployée, **sans le chemin final**,
   par exemple :
   `https://votre-nom.github.io`
   (juste le domaine, pas `/carnet-inspection/index.html`).
6. Cliquez sur **Créer**. Une fenêtre affiche votre **ID client** — une longue
   chaîne qui se termine par `.apps.googleusercontent.com`. **Copiez-la.**

## Étape 5 — Coller l'identifiant dans l'application

Plus besoin de modifier le fichier `index.html` ni de toucher à GitHub : tout
se fait directement dans l'application.

1. Ouvrez votre application (ou rafraîchissez la page si elle était déjà
   ouverte). Au tout premier lancement, un écran **« Connexion Google
   Drive »** s'ouvre automatiquement pour vous demander cet identifiant —
   si vous l'avez fermé ou si vous devez le changer plus tard, retrouvez-le
   à tout moment dans **Paramètres → Synchronisation → « Configurer »
   (ou « Modifier »)** à côté de « Identifiant client Google ».
2. Collez l'identifiant copié à l'étape précédente (il se termine par
   `.apps.googleusercontent.com`) dans le champ, puis touchez **Enregistrer**.
3. Si vous devez un jour repartir de zéro (par exemple après avoir recréé un
   identifiant), touchez **« Réinitialiser »** sur ce même écran : l'écran de
   configuration s'affichera à nouveau au prochain lancement, comme à
   l'installation.

## Étape 6 — Se connecter depuis l'application

1. Une fois l'identifiant enregistré, touchez **« Connecter »** (sur l'écran
   d'accueil, ou dans **Paramètres → Synchronisation**).
2. Une fenêtre Google s'ouvre. Choisissez le compte que vous avez ajouté
   comme « utilisateur test » à l'étape 3.
3. Google affichera un écran **« Cette application n'est pas vérifiée »** —
   c'est normal pour une application personnelle non publiée publiquement.
   Cliquez sur **« Paramètres avancés »** puis **« Accéder à Carnet Inspection
   (non sécurisé) »**, et enfin **Autoriser**. Vous ne verrez cet écran
   qu'à la première connexion.
4. C'est terminé : l'application peut désormais créer, dans votre Drive, un
   dossier principal « Carnet d'Inspection — Données » avec deux
   sous-dossiers (« Sauvegardes » et « Documents générés ») ainsi qu'une
   feuille de calcul, pour synchroniser vos tâches et documents.

## Que synchronise exactement le bouton de synchronisation ?

Touchez l'icône ↻ (ou **Paramètres → Synchronisation**) sur chaque appareil
où vous utilisez l'application. À chaque synchronisation :

- Les **tâches** sont fusionnées avec la feuille Google Sheets liée (la
  version la plus récente de chaque tâche est conservée), puis la feuille
  est entièrement réécrite.
- **Zones, établissements, enseignants, documents, modèles de documents et
  le référentiel des académies régionales / directions provinciales**
  (modifiable dans **Paramètres → Régions / DP**) sont fusionnés de la même
  façon via un fichier `sauvegarde-donnees.json` dans le dossier
  « Sauvegardes » — c'est ce qui vous permet de retrouver les mêmes données
  à jour sur un ordinateur, une tablette ou un téléphone différents, du
  moment que vous vous connectez avec le même compte Google sur chacun.

En cas de modification du même enregistrement sur deux appareils avant
synchronisation, c'est toujours la modification la plus récente qui est
conservée.

## Questions fréquentes

**Est-ce payant ?** Non. L'utilisation de l'API Google Drive dans les
volumes d'une application personnelle est entièrement gratuite, et rien ne
transite par les comptes ou serveurs de Claude/Anthropic.

**L'application voit-elle tous mes fichiers Drive ?** Non. Le champ
d'application `drive.file` limite l'accès aux seuls fichiers que
l'application crée elle-même (le dossier « Carnet Inspection » et sa feuille
de calcul). Le reste de votre Drive reste invisible pour elle.

**Puis-je ajouter un autre inspecteur/collègue plus tard ?** Oui, revenez à
l'étape 3 et ajoutez son adresse e-mail comme utilisateur test (jusqu'à 100
utilisateurs en mode Test).

**« Erreur 400 : redirect_uri_mismatch » ou message similaire ?** Vérifiez
qu'à l'étape 4 vous avez bien saisi l'origine exacte de votre site (avec
`https://`, sans slash final, sans le nom du fichier).
