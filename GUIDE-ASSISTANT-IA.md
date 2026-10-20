# Activer l'assistant de rédaction IA (gratuit, une seule fois)

L'application peut proposer des synthèses, des comptes-rendus, des rapports,
des recommandations, un plan d'action et des traductions français ↔ arabe,
générés par **Google Gemini** — directement dans les formulaires où c'est
utile (bilan, compte-rendu de réunion, grille d'observation, rapport, lettre
d'information, rapport d'inspection).

Comme pour Google Drive, il s'agit de **votre propre clé, personnelle et
gratuite** : rien ne transite par Claude ni par Anthropic, aucun serveur
intermédiaire — chaque génération appelle directement les serveurs Google
depuis votre navigateur, avec le niveau gratuit de votre compte Google.

## Étape 1 — Obtenir une clé API Gemini gratuite

1. Rendez-vous sur **https://aistudio.google.com/apikey** et connectez-vous
   avec votre compte Google.
2. Touchez **« Create API key »** (créer une clé API), dans un nouveau
   projet ou un projet existant.
3. Copiez la clé générée : elle commence par `AIza…`.

## Étape 2 — Coller la clé dans l'application

1. Ouvrez **Paramètres → Assistant IA**.
2. Collez la clé copiée à l'étape précédente dans le champ **« Clé API
   Google Gemini »**, puis touchez **Enregistrer**.
3. Les boutons IA apparaissent alors automatiquement dans les formulaires
   concernés (ils restent masqués tant qu'aucune clé n'est enregistrée).

Pour désactiver l'assistant, revenez sur cet écran et touchez
**« Réinitialiser »** : les boutons IA disparaissent jusqu'à l'enregistrement
d'une nouvelle clé.

## Ce que propose l'assistant

- **Rédiger avec l'IA** — bilan, compte-rendu (points discutés), grille
  d'observation (synthèse), rapport, lettre d'information, rapport
  d'inspection (recommandations) : un brouillon est généré à partir des
  informations déjà saisies, que vous pouvez ensuite modifier librement
  avant d'enregistrer ou d'imprimer.
- **Proposer un plan d'action** — dans un compte-rendu de réunion, génère
  3 à 5 actions concrètes (avec un responsable suggéré) à partir de l'ordre
  du jour, des points discutés et des décisions ; l'échéance de chaque
  action reste à votre charge, l'IA ne pouvant pas connaître vos
  contraintes de calendrier réelles.
- **Traduire** — traduit sur place le texte déjà présent dans un champ,
  du français vers l'arabe standard moderne ou inversement (le sens est
  détecté automatiquement) — utile notamment pour les documents dont la
  mise en forme est déjà en arabe (compte-rendu, lettre d'information,
  attestation, liste de présence, bordereau).

Le texte généré est toujours une **proposition à relire** : vérifiez-le et
corrigez-le avant d'imprimer ou d'enregistrer un document officiel, comme
vous le feriez avec le brouillon d'un(e) collègue.

## Questions fréquentes

**Est-ce payant ?** Non. Le niveau gratuit de l'API Gemini couvre largement
un usage personnel courant (rédaction ponctuelle de documents
administratifs).

**Ma clé est-elle partagée avec d'autres inspecteurs utilisant
l'application ?** Non. Chaque personne colle sa propre clé dans son propre
navigateur ; elle n'est jamais codée dans le fichier de l'application ni
publiée sur GitHub.

**« Clé API Gemini invalide ou refusée » ?** Regardez d'abord le message
exact affiché sous le bouton IA (l'application affiche désormais le message
d'erreur renvoyé par Google, pas seulement un message générique) — il indique
laquelle de ces causes s'applique :

1. **Le message parle de la clé elle-même** (« API key not valid », clé
   invalide…) : vérifiez que vous avez bien copié toute la clé, sans espace
   ni retour à la ligne avant/après (elle commence par `AIza…`, ou pour les
   clés les plus récentes par `AQ.…`), et qu'elle provient bien du compte
   Google que vous utilisez ici. **Si votre clé fonctionnait avant et a
   cessé de fonctionner en 2026** : Google a retiré les anciennes clés API
   « standard » (celles créées avant mi-2026) au profit d'un nouveau type de
   clé plus sécurisé — retournez sur **aistudio.google.com/apikey**, touchez
   **« Create API key »** pour créer une toute nouvelle clé, puis remplacez
   l'ancienne dans **Paramètres → Assistant IA**.
2. **Le message parle de « user location is not supported »
   (localisation)** : ce n'est **pas** un problème de clé — en créer une
   nouvelle ne changera rien, même répété plusieurs fois. Google détecte la
   provenance réseau de la connexion (pas votre position réelle) : un VPN,
   un proxy, ou le réseau de l'établissement/de l'opérateur qui achemine le
   trafic via un autre pays peut déclencher ce refus. Désactivez tout VPN ou
   économiseur de données, puis réessayez en Wifi ou en 4G/5G directe.
3. **Le message parle de l'API non activée sur le projet** (« Generative
   Language API has not been used… ») : la clé a été créée depuis la
   console Google Cloud plutôt que directement sur aistudio.google.com/apikey
   — recréez-en une depuis aistudio.google.com/apikey, qui active
   automatiquement ce qu'il faut.
4. **Le message parle de « forte demande » / « high demand »
   (modèle surchargé)** : c'est le cas le plus courant en pratique. Le
   modèle Gemini est temporairement saturé côté Google — rien à voir avec
   votre clé. L'application réessaie déjà automatiquement 2 fois avant
   d'afficher ce message ; si ça persiste, patientez quelques minutes et
   retouchez simplement le bouton.

**Puis-je changer de clé plus tard ?** Oui, à tout moment, dans
**Paramètres → Assistant IA** — collez la nouvelle clé et touchez
Enregistrer, elle remplace l'ancienne.

## Option : IA locale, sans internet ni clé (sur le téléphone)

Au lieu de Gemini, l'assistant peut tourner **entièrement sur l'appareil**,
dans le navigateur (même moteur llama.cpp que l'application PocketPal) :
les observations et les rapports ne quittent jamais le téléphone, et la
rédaction fonctionne hors connexion.

1. Ouvrez **Paramètres → Assistant IA** et touchez **« IA locale (sur
   l'appareil) »**.
2. Choisissez un modèle selon la mémoire du téléphone :
   - **Qwen 2.5 3B** (1,9 Go, 6 Go de RAM ou plus) — meilleure rédaction ;
   - **Qwen 2.5 1.5B** (1 Go) — plus rapide ;
   - **Gemma 3 1B** (0,8 Go) — pour les téléphones modestes.
3. Touchez **« Télécharger le modèle »**, en Wifi : le téléchargement se
   fait une seule fois, le modèle reste ensuite sur l'appareil. Le bouton
   **« Tester »** vérifie que tout fonctionne.

   L'écran reste allumé automatiquement pendant le téléchargement. S'il est
   coupé (écran éteint, application quittée, réseau perdu), **rien n'est
   perdu** : le téléchargement reprend là où il s'est arrêté dès que vous
   revenez sur cet écran, ou en touchant « Reprendre le téléchargement ».

   **Autre méthode** (si la connexion est très instable) : ouvrez « Autre
   méthode : télécharger avec Chrome », touchez le lien du fichier — Chrome
   le télécharge dans « Téléchargements », même écran éteint — puis revenez
   et touchez **« Importer le fichier téléchargé (.gguf) »**. Vous pourrez
   ensuite supprimer le fichier de vos Téléchargements.

Ce réglage est **propre à chaque appareil** : vous pouvez garder Gemini sur
le PC et l'IA locale sur le téléphone. L'import d'une fiche par photo/PDF
reste assuré par Gemini (le modèle local ne lit pas les images).

**Structure du rapport d'inspection.** Dans le même écran, la zone
« Structure du rapport d'inspection » contient la consigne fixe que suit
l'IA. Dans un rapport d'inspection, saisissez ou dictez vos notes
d'observation dans le champ **Constats**, puis touchez **« Rédiger/compléter
avec l'IA »** : le rapport est rédigé selon cette structure, à partir de vos
seules notes, et s'affiche au fur et à mesure.

L'IA locale est plus lente que Gemini (de quelques dizaines de secondes à
environ une minute selon le téléphone) et ses textes demandent une relecture
attentive. Pour libérer l'espace, touchez **« Supprimer le modèle »**.

## Temps de réponse et mode raisonnement

Pendant chaque génération, un chronomètre s'affiche sous le bouton IA ; à la
fin, le bandeau indique la durée totale et le moteur utilisé.

**Mode raisonnement** (Paramètres → Assistant IA) : le modèle analyse d'abord
vos observations et la structure demandée avant de rédiger. Les textes sont
plus rigoureux, mais la génération est plus longue. Touchez « Voir le
raisonnement du modèle » sous le bouton IA pour lire sa réflexion.

- Avec **Gemini**, il suffit de l'activer.
- Avec l'**IA locale**, il faut un modèle **Qwen 3** (1.7B, 1,1 Go, ou 4B,
  1,9 Go) ; les autres modèles rédigent sans raisonnement. Sur téléphone,
  comptez plusieurs minutes par rapport en mode raisonnement.
