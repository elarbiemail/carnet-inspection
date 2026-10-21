# Échange de données Carnet d'inspection ↔ iNSPECTAPP

Format commun, lu et produit par les deux applications, pour faire circuler
**établissements, enseignant(e)s, zones et tâches** dans les deux sens, sans doublon.

## Transports

| Sens | Google Drive (automatique) | Fichier (manuel) |
|---|---|---|
| Carnet → InspectApp | `Mon Drive/Carnet d'Inspection — Données/Échange InspectApp/carnet-vers-inspectapp.json` | `Echange_Carnet_vers_InspectApp_AAAA-MM-JJ.json` |
| InspectApp → Carnet | `…/Échange InspectApp/inspectapp-vers-carnet.json` | n'importe quel nom `.json` |

Sur le PC, InspectApp lit et écrit ce dossier par **Google Drive pour ordinateur**
(par exemple `G:\Mon Drive\Carnet d'Inspection — Données\Échange InspectApp\`).
Dans le carnet : *Paramètres → InspectApp* (« Échanger maintenant », échange
automatique à chaque synchronisation, export / import de fichier).

## Enveloppe

```json
{
  "format": "carnet-inspectapp",
  "version": 1,
  "source": "carnet" | "inspectapp",
  "exportedAt": "2026-10-21T08:00:00Z",
  "inspecteur": { "nom": "", "nomAr": "", "matiere": "", "academie": "", "direction": "" },
  "zones": [], "etablissements": [], "enseignants": [], "taches": []
}
```

Toutes les dates sont en ISO 8601 ; `date` d'une tâche en `AAAA-MM-JJ`, heures en `HH:MM`.
Chaque élément porte **`carnetId`** (identifiant dans le carnet) et/ou **`inspectappId`**
(identifiant dans InspectApp) et **`updatedAt`**. Chaque application conserve l'identifiant
de l'autre (le carnet dans `extIds.inspectapp`) et le renvoie au prochain échange.

## Éléments

**Zone** : `carnetId, inspectappId, nom, academie, direction, commune, cycle, updatedAt`

**Établissement** : `carnetId, inspectappId, nom, nomAr, nomFr, codeMassar, type (public|prive),
cycle (primaire|college|qualifiant|college_qualifiant), pionnier (bool), adresse, telephone,
latitude, longitude, zone {carnetId|inspectappId, nom}, updatedAt`

**Enseignant(e)** : `carnetId, inspectappId, nom, nomAr, nomFr, ppr, genre (F|H), matiere, cycle,
cadre, statut (titulaire|stagiaire…), sessionTitularisation, telephone, email,
etablissement {carnetId|inspectappId, codeMassar, nom}, updatedAt`

**Tâche** :

| Champ | Contenu |
|---|---|
| `type` | `Inspection`, `Visit`, `Meeting`, `ExperimentalLesson`, `ContinuingEducation`, `ActionResearch`, `ExamObservation`, `ExamDesign`, `ServiceTableValidation`, `Titularisation`, `Other` (le carnet accepte aussi les libellés français) |
| `typeCarnet` | type exact du carnet (aller-retour sans perte) |
| `statut` | `Planned`, `InProgress`, `Completed`, `Cancelled` |
| `statutCarnet` | `a_planifier`, `planifiee`, `realisee`, `rapport`, `cloturee` |
| autres | `titre, date, heureDebut, heureFin, lieu, description, priorite, note` (Insuffisant/Satisfaisant/Bien/Très bien), `voitureService` (bool), `enseignant {carnetId, inspectappId, ppr, nom}`, `etablissement {…}`, `participants [{carnetId, inspectappId, ppr, nom, etablissement}]`, `updatedAt` |

Correspondance des statuts : `a_planifier`, `planifiee` → `Planned` ; `realisee`, `rapport` →
`InProgress` ; `cloturee` → `Completed`. Dans l'autre sens : `Planned` → `planifiee`,
`InProgress` → `realisee`, `Completed` → `cloturee`. Une tâche `Cancelled` inconnue du carnet
est ignorée ; si elle est déjà connue, elle est marquée « [Annulée dans InspectApp] ».

## Règles de rapprochement (identiques des deux côtés)

1. identifiant connu (`carnetId` ou `inspectappId`) ;
2. sinon clé naturelle :
   * établissement : **code MASSAR**, puis nom (français, arabe ou latin, sans accents ni casse) ;
   * enseignant(e) : **PPR**, puis nom dans le même établissement ;
   * tâche : type + date + enseignant(e) (ou établissement, ou titre).
3. Mise à jour : un champ local vide est complété ; un champ rempli n'est remplacé que si
   l'élément reçu est plus récent (`updatedAt`). **Rien n'est jamais supprimé.**
