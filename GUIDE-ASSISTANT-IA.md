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

**« Clé API Gemini invalide ou refusée » ?** Deux causes possibles :

1. Vérifiez que vous avez bien copié toute la clé (elle commence par
   `AIza…`, sans espace avant/après), et qu'une clé API a bien été créée sur
   aistudio.google.com avec ce compte Google.
2. **Si votre clé fonctionnait avant et a cessé de fonctionner en 2026** :
   Google a changé sa politique de sécurité et a retiré les anciennes clés
   API « standard » (celles créées avant mi-2026), au profit d'un nouveau
   type de clé plus sécurisé. La solution est simple : retournez sur
   **aistudio.google.com/apikey**, touchez **« Create API key »** pour créer
   une **toute nouvelle clé** (les clés créées depuis mi-2026 sont
   automatiquement du nouveau type), puis remplacez l'ancienne clé dans
   **Paramètres → Assistant IA** par cette nouvelle clé. Aucune autre
   manipulation n'est nécessaire — l'application a été mise à jour pour
   suivre le format actuel recommandé par Google.

**Puis-je changer de clé plus tard ?** Oui, à tout moment, dans
**Paramètres → Assistant IA** — collez la nouvelle clé et touchez
Enregistrer, elle remplace l'ancienne.
