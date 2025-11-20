# Tâche 3 - Documentation

## Table des matières
1. [Modification du workflow GitHub Actions](#1-modification-du-workflow-github-actions)
2. [Choix et justification des tests avec Mockito](#2-choix-et-justification-des-tests-avec-mockito)
3. [Implémentation du Rickroll](#3-implémentation-du-rickroll)
4. [Validation des modifications](#4-validation-des-modifications)

## Note sur l'ordre de réalisation

Selon les exigences de la Tâche 3, l'ordre théorique de réalisation serait :
1. **Modifier le workflow GitHub Actions** (section 1)
2. **Documenter les choix de conception** (section 1)
3. **Écrire les tests Mockito** (section 2)
4. **Documenter les choix de tests** (section 2)
5. **Implémenter le rickroll** (section 3)

**Note pratique** : Dans notre implémentation, nous avons suivi un ordre légèrement différent mais tout aussi valide :
- Les tests Mockito ont été développés en premier (car ils sont nécessaires pour établir un baseline de mutation score)
- Le workflow GitHub Actions a été modifié ensuite (pour monitorer le score)
- Le rickroll a été ajouté en dernier (pour compléter l'élément humoristique)

Cet ordre est acceptable car :
- Les tâches sont relativement indépendantes
- Le workflow nécessite des tests existants pour calculer un score initial
- La documentation couvre tous les aspects requis, indépendamment de l'ordre de réalisation

Le code complet de tous les fichiers modifiés est disponible dans le dépôt :
- `.github/workflows/mutation-test.yml` : Workflow de mutation testing
- `.github/actions/rickroll-action/action.yml` : Action rickroll réutilisable
- `web-bundle/src/test/java/com/graphhopper/resources/RouteResourceMockTest.java` : Tests Mockito pour RouteResource (9 tests)
- `web-bundle/src/test/java/com/graphhopper/resources/InfoResourceMockTest.java` : Tests Mockito pour InfoResource (3 tests)

---

## 1. Modification du workflow GitHub Actions

### 1.1 Objectif
Modifier le workflow GitHub Actions pour que le processus de build échoue si le score de mutation baisse après un commit.

### 1.2 Choix de conception

#### 1.2.1 Persistance du score de mutation
**Problème** : GitHub Actions ne permet pas de comparer directement le score actuel avec le score du commit précédent dans le même workflow.

**Solution choisie** : Utilisation d'artifacts GitHub Actions pour persister le score de mutation entre les exécutions.

**Justification** :
- Les artifacts GitHub Actions permettent de stocker des fichiers entre les exécutions de workflow
- L'action `dawidd6/action-download-artifact@v11` (version la plus récente, voir [GitHub](https://github.com/dawidd6/action-download-artifact)) permet de télécharger des artifacts de runs précédents (contrairement à l'action standard qui ne peut télécharger que les artifacts du run actuel)
- Cette approche est simple, fiable et ne nécessite pas de base de données externe

#### 1.2.2 Calcul du score de mutation
**Implémentation** : Le score est calculé à partir du rapport XML généré par PITest (`mutations.xml`). Le script parse le fichier XML pour compter le nombre total de mutations et le nombre de mutations tuées, puis calcule le pourcentage : `(mutations tuées / mutations totales) × 100`.

**Justification** :
- PITest génère un rapport XML standardisé (`mutations.xml`)
- Le calcul est simple et direct
- Utilisation de `xmlstarlet` pour un parsing robuste du XML

#### 1.2.3 Gestion du premier run
**Problème** : Lors du premier run, il n'y a pas de score précédent à comparer.

**Solution** : Si le fichier de score précédent n'existe pas, on initialise `PREV_SCORE=0`.

**Justification** :
- Permet au workflow de fonctionner dès le premier commit
- Un score de 0% est une valeur par défaut sûre qui ne bloquera pas le premier run
- Le workflow peut démarrer sans historique

#### 1.2.4 Upload du score
**Implémentation** : Le score actuel est uploadé comme artifact avec `if: always()`.

**Justification** :
- `if: always()` garantit que le score est sauvegardé même si le workflow échoue
- Cela permet de maintenir un historique cohérent
- Le prochain run pourra toujours comparer avec le dernier score connu

### 1.3 Structure du workflow

Le workflow `mutation-test.yml` suit cette séquence :

1. **Checkout** : Récupération du code source
2. **Setup JDK 17** : Configuration de l'environnement Java
3. **Download previous score** : Téléchargement du score précédent depuis les artifacts
4. **Build Project** : Installation des dépendances (sans tests pour gagner du temps)
5. **Run PITest** : Exécution des tests de mutation sur le module `web-bundle`
6. **Check Mutation Score** : Calcul et comparaison du score
7. **Save mutation score** : Sauvegarde du score actuel comme artifact
8. **Rickroll on Failure** : Action humoristique en cas d'échec

### 1.4 Points d'échec

Le workflow échoue dans les cas suivants :
- Le rapport PITest n'est pas généré
- Le score de mutation actuel est inférieur au score précédent : `CURRENT_SCORE < PREV_SCORE`

---

## 2. Choix et justification des tests avec Mockito

### 2.1 Classes testées

Nous avons choisi de tester **deux classes** de GraphHopper pour répondre à l'exigence "choisir une ou deux classes" :

#### 2.1.1 Classe 1 : `RouteResource`

**Justification du choix** :
- `RouteResource` est une classe critique qui orchestre la logique de routage
- Elle a plusieurs dépendances (GraphHopper, ProfileResolver, GHRequestTransformer, GraphHopperConfig) qui peuvent être facilement mockées
- Elle contient de la logique métier importante qui mérite d'être testée en isolation
- Les tests d'intégration existants testent déjà le comportement end-to-end, donc des tests unitaires avec mocks complètent bien la couverture

#### 2.1.2 Classe 2 : `InfoResource`

**Justification du choix** :
- `InfoResource` est une classe plus simple qui fournit des informations système
- Elle complète bien `RouteResource` en montrant un type différent de ressource (information vs. routage)
- Elle a des dépendances claires (GraphHopperConfig, GraphHopper, EncodingManager, BaseGraph, StorableProperties) qui peuvent être facilement mockées
- Elle permet de tester différents scénarios (gestion des profils, elevation, dates, encoded values)

### 2.2 Classes mockées (pour RouteResource)

#### 2.2.1 `GraphHopper` (Mock 1)
**Rôle** : Moteur de routage principal qui calcule les itinéraires.

**Justification** :
- C'est une dépendance externe complexe qui nécessite un graphe chargé
- En mockant GraphHopper, on peut tester la logique d'orchestration de RouteResource sans dépendre de données réelles
- Permet de simuler différents scénarios (succès, erreurs) rapidement

**Valeurs simulées** :
- Retourne un `GHResponse` avec un `ResponsePath` contenant distance, temps, points et instructions
- Permet de tester le comportement de RouteResource face à différentes réponses

#### 2.2.2 `ProfileResolver` (Mock 2)
**Rôle** : Résout le profil de routage à partir des hints de la requête.

**Justification** :
- La résolution de profil est une logique importante qui peut varier selon la configuration
- En mockant ProfileResolver, on peut tester que RouteResource utilise correctement le profil résolu
- Permet de tester différents scénarios de résolution de profil

**Valeurs simulées** :
- Retourne le nom du profil (ex: "car", "bike", "foot")
- Simule le comportement standard où le profil demandé est retourné tel quel

#### 2.2.3 `GHRequestTransformer` (Mock 5)
**Rôle** : Transforme la requête avant le routage (peut ajouter des paramètres par défaut, transformer les coordonnées, etc.).

**Justification** :
- C'est une dépendance qui peut modifier la requête avant le routage
- Il est important de vérifier que RouteResource utilise la requête transformée, pas l'originale
- Permet de tester l'intégration avec le transformer

**Valeurs simulées** :
- Par défaut, retourne la requête originale (pas de transformation)
- Dans le test spécifique, simule une transformation qui ajoute un algorithme par défaut

#### 2.2.4 `GraphHopperConfig` (Mock 4)
**Rôle** : Fournit la configuration de GraphHopper (paramètres, copyrights, etc.).

**Justification** :
- RouteResource lit des valeurs de configuration (comme `routing.snap_preventions_default`)
- Il est important de tester que ces valeurs sont correctement lues et utilisées
- Permet de tester le comportement avec différentes configurations

**Valeurs simulées** :
- `getString(key, defaultValue)` retourne la valeur par défaut fournie
- `getCopyrights()` retourne une liste de copyrights standard
- Dans le test spécifique, simule une configuration avec des snap preventions par défaut

#### 2.2.5 `HttpServletRequest` (Mock 3)
**Rôle** : Représente la requête HTTP entrante.

**Justification** :
- RouteResource utilise HttpServletRequest pour logger des informations (adresse IP, locale, User-Agent)
- C'est une dépendance du framework web qui doit être mockée pour les tests unitaires

**Valeurs simulées** :
- `getRemoteAddr()` retourne "127.0.0.1"
- `getLocale()` retourne `Locale.ENGLISH`
- `getHeader("User-Agent")` retourne "Test-Agent"

#### 2.2.6 `StorableProperties` (Mock 6)
**Rôle** : Propriétés stockables du graphe (comme la date OSM).

**Justification** :
- RouteResource accède aux propriétés via `graphHopper.getProperties()`
- Nécessaire pour initialiser correctement RouteResource

**Valeurs simulées** :
- `getAll()` retourne une Map vide (les propriétés ne sont pas critiques pour les tests)

### 2.3 Classes mockées (pour InfoResource)

#### 2.3.1 `GraphHopperConfig` (Mock 1)
**Rôle** : Fournit la configuration de GraphHopper (profils, GTFS, encoded values privés).

**Justification** :
- InfoResource lit des valeurs de configuration importantes
- Permet de tester le comportement avec différentes configurations

**Valeurs simulées** :
- `getProfiles()` retourne une liste de profils (ex: "car", "bike")
- `has("gtfs.file")` retourne true/false selon la configuration
- `getString("graph.encoded_values.private", "")` retourne la liste des encoded values privés

#### 2.3.2 `GraphHopper` (Mock 2)
**Rôle** : Instance principale de GraphHopper qui fournit l'accès aux autres composants.

**Justification** :
- InfoResource accède à EncodingManager, BaseGraph et StorableProperties via GraphHopper
- Permet d'isoler InfoResource de la complexité de GraphHopper

**Valeurs simulées** :
- `getEncodingManager()` retourne le mock EncodingManager
- `getBaseGraph()` retourne le mock BaseGraph
- `getProperties()` retourne le mock StorableProperties

#### 2.3.3 `EncodingManager` (Mock 3)
**Rôle** : Gère les encoded values du graphe.

**Justification** :
- InfoResource doit lister et catégoriser les encoded values
- Permet de tester la logique de filtrage et de catégorisation

**Valeurs simulées** :
- `getEncodedValues()` retourne une liste vide par défaut (peut être configurée dans les tests)
- `hasEncodedValue(name)` retourne false par défaut

#### 2.3.4 `BaseGraph` (Mock 4)
**Rôle** : Graphe de base qui contient les nœuds et arêtes.

**Justification** :
- InfoResource lit les bounds du graphe pour créer le bbox
- Permet de tester la création correcte du bbox

**Valeurs simulées** :
- `getBounds()` retourne un BBox avec des coordonnées de test (40.0, 41.0, -74.0, -73.0)

#### 2.3.5 `StorableProperties` (Mock 5)
**Rôle** : Propriétés stockables du graphe (dates d'import, dates de données).

**Justification** :
- InfoResource lit les dates d'import et de données
- Permet de tester l'affichage correct des métadonnées

**Valeurs simulées** :
- `get("datareader.import.date")` retourne "2024-01-01T00:00:00Z"
- `get("datareader.data.date")` retourne "2024-01-15T00:00:00Z"

### 2.4 Cas de test ajoutés pour RouteResource

#### 2.4.1 Test 1 : `testRoutePost_HappyPath`
**Objectif** : Tester le scénario de succès avec un routage normal.

**Classes mockées principales** :
- `GraphHopper` : Retourne une réponse réussie
- `ProfileResolver` : Résout le profil "car"

**Valeurs simulées** :
- Points : (40, -74) et (40.1, -74.1)
- Distance : 1000.0 mètres
- Temps : 120000 ms
- Profil : "car"

**Vérifications** :
- Le statut HTTP est 200
- Le type de contenu est JSON
- GraphHopper.route() est appelé exactement une fois
- Le profil passé à route() est correct ("car")

**Justification** : Ce test vérifie le chemin heureux et s'assure que RouteResource orchestre correctement les appels aux dépendances.

#### 2.4.2 Test 2 : `testRoutePost_ErrorPath_PointNotFound`
**Objectif** : Tester la gestion des erreurs lorsque GraphHopper retourne une erreur.

**Classes mockées principales** :
- `GraphHopper` : Retourne une réponse avec une erreur `PointNotFoundException`
- `ProfileResolver` : Résout le profil "car"

**Valeurs simulées** :
- Erreur : `PointNotFoundException("Simulated Error: Point 0 not found.", 0)`

**Vérifications** :
- Une `MultiException` est levée
- L'exception contient l'erreur `PointNotFoundException`
- Le message et l'index du point sont corrects

**Justification** : Ce test vérifie que RouteResource propage correctement les erreurs de GraphHopper sous forme de MultiException, ce qui est important pour le traitement des erreurs côté client.

#### 2.4.3 Test 3 : `testRoutePost_GHRequestTransformer_TransformsRequest` (NOUVEAU)
**Objectif** : Vérifier que `GHRequestTransformer` est correctement invoqué et que la requête transformée est utilisée pour le routage.

**Classes mockées principales** :
- `GHRequestTransformer` : Transforme la requête en ajoutant un algorithme par défaut
- `GraphHopper` : Reçoit la requête transformée
- `ProfileResolver` : Résout le profil "bike"

**Valeurs simulées** :
- Requête originale : profil "bike", pas d'algorithme
- Requête transformée : profil "bike", algorithme "dijkstra"

**Vérifications** :
- `GHRequestTransformer.transformRequest()` est appelé avec la requête originale
- `GraphHopper.route()` est appelé avec la requête transformée (qui contient l'algorithme "dijkstra")

**Justification** : Ce test est crucial car il vérifie que RouteResource utilise bien la requête transformée et non l'originale. Cela tue les mutants qui ignoreraient la transformation ou utiliseraient la mauvaise requête.

#### 2.4.4 Test 4 : `testRoutePost_GraphHopperConfig_ReadsSnapPreventionsDefault` (NOUVEAU)
**Objectif** : Vérifier que `GraphHopperConfig` est correctement utilisé pour lire les valeurs de configuration par défaut, notamment `routing.snap_preventions_default`.

**Classes mockées principales** :
- `GraphHopperConfig` : Retourne "tunnel,bridge" pour `routing.snap_preventions_default`
- `GraphHopper` : Reçoit une requête avec les snap preventions par défaut
- `ProfileResolver` : Résout le profil "foot"

**Valeurs simulées** :
- Configuration : `routing.snap_preventions_default = "tunnel,bridge"`
- Requête : Pas de snap preventions spécifiées (doit utiliser les valeurs par défaut)

**Vérifications** :
- `GraphHopperConfig.getString()` est appelé pour lire la configuration
- La requête passée à `GraphHopper.route()` contient les snap preventions par défaut ("tunnel" et "bridge")

**Justification** : Ce test vérifie que RouteResource lit correctement la configuration et applique les valeurs par défaut. C'est important car la configuration peut varier selon l'environnement et doit être correctement propagée.

### 2.5 Cas de test ajoutés pour InfoResource

#### 2.5.1 Test 1 : `testGetInfo_ReturnsCompleteSystemInformation`
**Objectif** : Vérifier que InfoResource retourne correctement toutes les informations système (bounds, profils, elevation, dates).

**Classes mockées principales** :
- `GraphHopperConfig` : Fournit les profils ("car", "bike")
- `BaseGraph` : Fournit les bounds du graphe
- `StorableProperties` : Fournit les dates d'import et de données

**Valeurs simulées** :
- Bounds : (40.0, 41.0, -74.0, -73.0) - BBox avec coordonnées de test
- Profils : "car" et "bike" - Liste de profils de routage
- Elevation : false - Pas d'élévation dans le graphe
- Import date : "2024-01-01T00:00:00Z" - Date d'import du graphe
- Data date : "2024-01-15T00:00:00Z" - Date des données OSM

**Vérifications** :
- Le bbox est correctement créé avec les bounds du graphe (minX, maxX, minY, maxY)
- Les profils sont correctement listés dans l'ordre
- L'elevation est correctement indiquée comme false
- Les dates sont correctement récupérées depuis StorableProperties
- `getBounds()` est appelé 4 fois (une fois pour chaque propriété : minLon, maxLon, minLat, maxLat)

**Justification** : Ce test vérifie le chemin heureux et s'assure que InfoResource récupère et retourne correctement toutes les informations système. Il teste l'intégration avec GraphHopperConfig, BaseGraph et StorableProperties.

#### 2.5.2 Test 2 : `testGetInfo_HandlesElevationFlag`
**Objectif** : Vérifier que InfoResource gère correctement le flag d'elevation.

**Classes mockées principales** :
- `GraphHopperConfig` : Fournit les profils

**Valeurs simulées** :
- Elevation : true (différent du défaut false)
- Profils : "car"

**Vérifications** :
- L'elevation est correctement indiquée comme true dans la réponse
- InfoResource est recréé avec `hasElevation = true` pour tester ce scénario

**Justification** : Ce test vérifie que le flag d'elevation est correctement propagé dans la réponse. Il teste que le constructeur d'InfoResource accepte correctement le paramètre `hasElevation` et l'utilise dans la réponse.

#### 2.5.3 Test 3 : `testGetInfo_AddsPtProfileWhenGtfsConfigured`
**Objectif** : Vérifier que InfoResource ajoute le profil "pt" (public transport) lorsque GTFS est configuré.

**Classes mockées principales** :
- `GraphHopperConfig` : Fournit les profils et la configuration GTFS

**Valeurs simulées** :
- Profils : "car" - Profil de routage standard
- GTFS file : configuré (`has("gtfs.file")` retourne true)

**Vérifications** :
- Le profil "pt" est ajouté à la liste des profils après les profils standards
- La liste contient 2 profils : "car" et "pt"
- `has("gtfs.file")` est appelé pour vérifier la configuration GTFS

**Justification** : Ce test vérifie que la logique conditionnelle pour ajouter le profil "pt" fonctionne correctement. Il teste que InfoResource détecte la présence de GTFS et ajoute automatiquement le profil de transport public.

### 2.6 Stratégie de mock

**Approche générale** :
- Utilisation de `@Mock` avec `MockitoExtension` pour une intégration propre avec JUnit 5
- Configuration des mocks dans `@BeforeEach` pour éviter la duplication
- Utilisation de `lenient()` pour les mocks qui ne sont pas utilisés dans tous les tests
- Utilisation d'`ArgumentCaptor` pour vérifier les arguments passés aux méthodes mockées

**Justification** :
- Cette approche permet d'isoler complètement les classes testées de leurs dépendances
- Les tests sont rapides car ils n'exécutent pas de code réel (pas de chargement de graphe, pas d'accès réseau)
- Les tests sont déterministes car les valeurs mockées sont contrôlées
- Les tests sont maintenables car les mocks sont clairement définis et documentés

**Résumé** :
- **Classes testées** : 2 (RouteResource et InfoResource)
- **Tests totaux** : 12 (9 pour RouteResource + 3 pour InfoResource)
- **Classes mockées différentes** : Au moins 8 classes différentes
  - Pour RouteResource : GraphHopper, ProfileResolver, GHRequestTransformer, GraphHopperConfig, HttpServletRequest, StorableProperties
  - Pour InfoResource : GraphHopperConfig, GraphHopper, EncodingManager, BaseGraph, StorableProperties
- **Fichiers de test** :
  - `web-bundle/src/test/java/com/graphhopper/resources/RouteResourceMockTest.java` (9 tests)
  - `web-bundle/src/test/java/com/graphhopper/resources/InfoResourceMockTest.java` (3 tests)

---

## 3. Implémentation du Rickroll

### 3.1 Objectif
Ajouter un élément d'humour dans la suite de tests : rickroll quand un cas de test de GraphHopper échoue.

### 3.2 Choix de conception

#### 3.2.1 Action réutilisable
**Solution choisie** : Création d'une action composite réutilisable dans `.github/actions/rickroll-action/`.

**Justification** :
- **Réutilisabilité** : L'action peut être utilisée dans plusieurs workflows (mutation-test.yml, build.yml)
- **Maintenabilité** : Un seul endroit pour modifier le message de rickroll
- **Simplicité** : Action composite simple qui ne nécessite pas de code externe (Rust, etc.)
- **Standard** : Suit les bonnes pratiques GitHub Actions pour les actions réutilisables

#### 3.2.2 Structure de l'action
L'action est définie dans `.github/actions/rickroll-action/action.yml` :
- Type : `composite` (exécute des commandes shell)
- Output : URL du rickroll (pour référence future)
- Steps : Un seul step qui affiche le message de rickroll

**Justification** :
- Action composite est la solution la plus simple pour ce cas d'usage
- Pas besoin de créer une action Docker ou JavaScript
- Facile à comprendre et modifier

### 3.3 Intégration dans les workflows

#### 3.3.1 Intégration dans les workflows
L'action rickroll est intégrée dans deux workflows :
- **`mutation-test.yml`** : S'exécute en cas d'échec du workflow de mutation testing
- **`build.yml`** : S'exécute en cas d'échec des tests unitaires standards

**Justification** :
- Utilise `if: failure()` pour s'exécuter uniquement en cas d'échec
- Utilise l'action réutilisable locale (`.github/actions/rickroll-action`)
- Ajoute de l'humour et de la cohérence entre les workflows
- Le code complet est disponible dans les fichiers workflow respectifs

### 3.4 Contenu du rickroll
Le message inclut :
- Un message d'erreur humoristique
- Les paroles de "Never Gonna Give You Up"
- Un lien vers la vidéo YouTube classique

**Justification** :
- Élément culturel reconnaissable dans la communauté développeur
- Ajoute de la légèreté à un moment frustrant (échec de tests)
- Le lien est explicite (pas de surprise non désirée)

---

## 4. Validation des modifications

### 4.1 Validation du workflow de mutation testing

#### 4.1.1 Test manuel
Pour valider que le workflow fonctionne correctement :

1. **Premier run** :
   - Le workflow doit s'exécuter sans erreur
   - Un artifact `mutation-score-report` doit être créé avec le score initial

2. **Run suivant avec score amélioré** :
   - Modifier le code pour améliorer la couverture de tests
   - Le workflow doit passer avec succès
   - Le nouveau score doit être sauvegardé

3. **Run suivant avec score dégradé** :
   - Modifier le code pour réduire la couverture (par exemple, supprimer une assertion)
   - Le workflow doit échouer avec le message d'erreur approprié
   - Le rickroll doit s'afficher

#### 4.1.2 Vérification des artifacts
- Vérifier que l'artifact `mutation-score-report` est créé à chaque run
- Vérifier que le fichier `score.txt` contient un nombre entre 0 et 100

#### 4.1.3 Vérification des logs
- Les logs doivent afficher :
  - "Current Score: X%"
  - "Previous Score: Y%"
  - "✅ Score is stable or improved." ou "::error::Mutation score dropped..."

### 4.2 Validation des tests Mockito

#### 4.2.1 Exécution des tests
Les tests peuvent être exécutés avec `mvn test -Dtest=RouteResourceMockTest`. Tous les 9 tests doivent passer rapidement (pas de chargement de graphe).

#### 4.2.2 Vérification de la couverture de mutation
La couverture de mutation peut être vérifiée avec `mvn -pl web-bundle org.pitest:pitest-maven:mutationCoverage`. Les nouveaux tests doivent tuer des mutants supplémentaires et maintenir ou améliorer le score de mutation.

#### 4.2.3 Analyse des mutants tués
Les nouveaux tests devraient tuer des mutants comme :
- Mutants qui ignorent la transformation de requête
- Mutants qui ne lisent pas la configuration
- Mutants qui utilisent la mauvaise requête

### 4.3 Validation du rickroll

#### 4.3.1 Test manuel
Pour tester le rickroll :

1. **Forcer un échec de test** :
   - Modifier temporairement un test pour qu'il échoue
   - Commiter et pousser
   - Vérifier que le rickroll s'affiche dans les logs

2. **Vérifier l'affichage** :
   - Les logs doivent contenir le message de rickroll
   - Le lien YouTube doit être présent

#### 4.3.2 Test dans différents workflows
- Vérifier que le rickroll fonctionne dans `mutation-test.yml`
- Vérifier que le rickroll fonctionne dans `build.yml`

### 4.4 Métriques de validation

#### 4.4.1 Score de mutation
- **Avant** : Score de mutation initial (baseline)
- **Après** : Score de mutation après ajout des nouveaux tests
- **Objectif** : Maintenir ou améliorer le score

#### 4.4.2 Couverture de code
- Vérifier que les nouveaux tests couvrent les branches de code ajoutées
- Vérifier que les mocks testent les interactions importantes

#### 4.4.3 Temps d'exécution
- Les tests avec mocks doivent être rapides (< 1 seconde par test)
- Le workflow de mutation testing doit rester raisonnable (< 10 minutes)

### 4.5 Observations sur le score de mutation initial

#### 4.5.1 Score de mutation (2-4%)
**Observation** : Le score de mutation initial était relativement bas (2-3%), avec seulement 6-8 mutations tuées sur 298 mutations totales. Après l'ajout des tests pour InfoResource, le score a augmenté à 4%.

**Raisons possibles** :
1. **Complexité de RouteResource** : La classe `RouteResource` est une classe orchestratrice complexe avec de nombreuses dépendances et chemins d'exécution
2. **Couverture limitée** : Les tests unitaires avec mocks se concentrent sur la logique d'orchestration, mais ne couvrent pas tous les chemins de code
3. **Méthode doGet non testée** : Seule la méthode `doPost` est testée, la méthode `doGet` n'est pas couverte par les tests Mockito
4. **Mutations dans le code non testé** : Beaucoup de mutations se trouvent dans des branches de code qui ne sont pas exercées par les tests actuels

#### 4.5.2 Stratégie d'amélioration
Pour améliorer le score de mutation à l'avenir, on pourrait :
- Ajouter des tests pour la méthode `doGet`
- Tester plus de cas limites et de branches conditionnelles
- Ajouter des assertions plus spécifiques pour détecter les mutations
- Tester les méthodes utilitaires comme `removeLegacyParameters` et `initHints`

#### 4.5.3 Validation du workflow malgré le score bas
**Important** : Même avec un score initial bas (2-3%), le workflow fonctionne correctement :
- ✅ Le workflow détecte correctement les changements de score
- ✅ Le workflow échoue lorsque le score baisse (démontré en désactivant des tests)
- ✅ Le rickroll s'affiche correctement en cas d'échec
- ✅ Le système de persistance du score fonctionne correctement

**Conclusion** : Le score initial bas n'affecte pas la fonctionnalité du workflow. Le système de monitoring fonctionne indépendamment du niveau de score, ce qui permet de :
- Établir un baseline (même bas)
- Détecter toute dégradation future
- Maintenir la qualité des tests au fil du temps

---

## Conclusion

Cette implémentation répond à tous les objectifs de la Tâche 3 :

1. ✅ **Workflow GitHub Actions** : Le build échoue si le score de mutation baisse (voir `.github/workflows/mutation-test.yml`)
2. ✅ **Documentation** : Ce document explique tous les choix de conception et d'implémentation
3. ✅ **Tests Mockito** : 12 tests répartis sur 2 classes testées, mockant au moins 8 classes différentes
   - `RouteResourceMockTest.java` : 9 tests pour la classe RouteResource
   - `InfoResourceMockTest.java` : 3 tests pour la classe InfoResource
4. ✅ **Documentation des tests** : Justification complète des choix de classes, mocks et valeurs (section 2)
5. ✅ **Rickroll** : Action réutilisable qui rickroll en cas d'échec de tests (voir `.github/actions/rickroll-action/`)

Les modifications sont maintenables, testables et suivent les bonnes pratiques de développement.

