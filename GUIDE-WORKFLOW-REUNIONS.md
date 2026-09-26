# Workflow « Réunion pédagogique » / « Cours expérimental »

Ce guide décrit comment l'application accompagne, de bout en bout, une
réunion pédagogique ou un cours expérimental — de la planification jusqu'au
compte-rendu final.

## 1. Planification

Créez une tâche de type **« Réunion pédagogique »** ou **« Cours
expérimental »**. Deux champs supplémentaires apparaissent pour ces deux
types :

- **Heure**, à côté de l'échéance (date) — utile puisque le courrier
  d'information doit préciser la date *et* l'heure.
- **Enseignant(e)s invité(e)s** — une liste à cocher de tous vos
  enseignants enregistrés (avec leur établissement et leur PPR s'il est
  renseigné sur leur fiche). Cochez tous les enseignants concernés, même
  s'ils viennent d'établissements différents.

Le PPR se renseigne une fois pour toutes sur la fiche de chaque enseignant
(champ **PPR**, à côté de la matière et du cycle).

## 2. Courrier d'information (avec bordereau)

Depuis le détail de la tâche, générez un document **« Lettre
d'information »** :

- le **destinataire** est pré-rempli avec **« Service pédagogique »**
  (au lieu du/de la directeur(trice) de l'établissement, utilisé pour les
  autres types de tâches) ;
- le **corps de la lettre** mentionne automatiquement le sujet, la date et
  l'heure ;
- la liste des **enseignant(e)s invité(e)s** (nom, PPR, établissement)
  est reprise dans un tableau à l'impression / l'export Word.

Un **bordereau de transmission** est généré automatiquement dès que la
tâche passe au statut « Clôturée » (voir plus bas) ; vous pouvez aussi en
générer un manuellement à tout moment depuis le détail de la tâche pour
accompagner ce courrier dès son envoi.

## 3. Liste de présence et attestations

- **Liste de présence** : générez le document « Liste de présence » — les
  participants sont pré-remplis directement à partir des enseignant(e)s
  invité(e)s cochés à l'étape 1 (et non plus de tous les enseignants de
  l'établissement).
- **Attestations de présence** : le bouton **« Attestations des
  invité(e)s (N) »**, dans le détail de la tâche, génère en une seule fois
  une attestation par enseignant(e) invité(e) — plus besoin de répéter le
  formulaire pour chacun(e). Un second clic ne crée pas de doublon si des
  attestations existent déjà.

## 4. Documents de travail utilisés

Dans le détail de la tâche, la section **« Documents de travail utilisés
»** permet de joindre directement les documents (PDF, Word, image) utilisés
pendant la réunion ou le cours — ils restent attachés à la tâche et
consultables à tout moment.

## 5. Compte-rendu et bordereau final

Après la séance, générez le document **« Compte-rendu de réunion »** — le
champ « Présents » est pré-rempli avec la liste des enseignant(e)s
invité(e)s. Une fois le compte-rendu rédigé (ordre du jour, points
discutés, décisions, plan d'action — éventuellement avec l'aide de
l'assistant IA, voir `GUIDE-ASSISTANT-IA.md`), faites passer la tâche au
statut **« Clôturée »** : un bordereau de transmission est alors généré
**automatiquement**, avec en pièces jointes la liste des documents déjà
produits pour cette tâche (lettre d'information, présence, attestations,
compte-rendu) — prêt à être envoyé à l'administration.
