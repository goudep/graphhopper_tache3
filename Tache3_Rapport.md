# Tâche 3 : Tests d'Intégration et CI/CD

**Membres du binôme :** Yudi Ma, 20236724; Ru Qian, 20234621

---

## 1. GitHub Actions et Rickroll

### 1.1 Objectif
Modifier le workflow GitHub Actions pour que le build échoue si le score de mutation baisse après un commit, tout en ajoutant une touche d'humour avec un rickroll en cas d'échec.

### 1.2 Choix de conception et implémentation

#### Persistance du score de mutation
**Le défi principal** : Les environnements GitHub Actions sont "stateless" (sans état). Il fallait trouver un moyen de conserver le score de mutation entre deux exécutions distinctes.

**Solution retenue** : Utilisation des **Artifacts GitHub** avec l'action tierce `dawidd6/action-download-artifact@v11` pour télécharger l'artifact du run précédent (l'action standard ne permet que de télécharger les artifacts du run actuel). Le score est stocké dans un fichier `score.txt` et passé d'un build à l'autre.

**Solution rejetée** : Le cache GitHub (`actions/cache`) a été écarté car les clés de cache sont immuables une fois créées, ce qui rend difficile la mise à jour fréquente du score de référence.

#### Calcul du score de mutation
**Implémentation** : Le score est calculé à partir du rapport XML généré par PITest (`mutations.xml`). Au lieu d'installer des parseurs XML lourds, nous utilisons des commandes shell standard (`grep`, `tr`, `wc`) directement dans le script YAML.

**Robustesse** : Des protections ont été ajoutées pour gérer les cas limites (division par zéro, valeurs vides) :
- nettoyage des valeurs extraites du rapport (`tr -d '[:space:]'`) ;
- initialisation de `TOTAL` et `KILLED` à 0 si les valeurs sont vides ;
- calcul du score uniquement si `TOTAL > 0`, sinon `CURRENT_SCORE` est forcé à 0 ;
- nettoyage du score précédent avec `tr -cd '0-9'` afin d’ignorer tout caractère non numérique dans `score.txt`.

#### Optimisation
Lancer PITest sur tout le projet GraphHopper prenait trop de temps et causait des timeouts. La portée des tests de mutation a été limitée au module `web-bundle` et au package `com.graphhopper.resources` via le fichier `pom.xml`.

#### Gestion du premier run
Si le fichier de score précédent n'existe pas, on initialise `PREV_SCORE=0`. Cela permet au workflow de fonctionner dès le premier commit sans bloquer.

#### Structure du workflow
Le workflow `mutation-test.yml` suit cette séquence :
1. Checkout du code source
2. Setup JDK 17
3. Téléchargement du score précédent depuis les artifacts
4. Build du projet (sans tests pour gagner du temps)
5. Exécution de PITest sur le module `web-bundle`
6. Calcul et comparaison du score
7. Sauvegarde du score actuel comme artifact (avec `if: always()` pour garantir la persistance même en cas d'échec)
8. Rickroll en cas d'échec

**Points d'échec** : Le workflow échoue si le rapport PITest n'est pas généré ou si `CURRENT_SCORE < PREV_SCORE`.

### 1.3 Implémentation du Rickroll

**Choix de conception** : Création d'une action composite réutilisable dans `.github/actions/rickroll-action/`. Cette approche permet :
- **Réutilisabilité** : L'action peut être utilisée dans plusieurs workflows (`mutation-test.yml`, `build.yml`)
- **Maintenabilité** : Un seul endroit pour modifier le message
- **Simplicité** : Action composite qui ne nécessite pas de code externe

**Intégration** : L'action rickroll est intégrée dans les workflows avec la condition `if: failure()` pour s'exécuter uniquement en cas d'échec. Le message inclut les paroles de "Never Gonna Give You Up" et un lien vers la vidéo YouTube classique.

### 1.4 Validation du workflow ("Crash Test")

Pour prouver que la logique de "non-régression" fonctionne, nous avons simulé un scénario d'échec réel :

1. **Établissement de la référence (Run 1)** : Push d'un code avec des tests solides (utilisant `ArgumentCaptor`). Le CI a réussi et a enregistré un score de mutation positif (4%).
2. **Sabotage (Run 2)** : Désactivation volontaire de plusieurs tests dans `RouteResourceMockTest.java` pour affaiblir la couverture sans casser la compilation.
3. **Résultat** : PITest a détecté que moins de mutants étaient tués. Le score a chuté. Le script a détecté la baisse et a **fait échouer le build**. Le rickroll s'est affiché dans les logs.
4. **Retour à la normale (Run 3)** : Réactivation des tests, le build est redevenu vert.

**Observation importante** : Le score de mutation initial était relativement bas (2-4%), mais cela n'affecte pas la fonctionnalité du workflow. Le système de monitoring fonctionne indépendamment du niveau de score, permettant d'établir un baseline et de détecter toute dégradation future.

---

## 2. Tests avec Mockito

### 2.1 Choix et justification des classes testées

Nous avons choisi de tester **deux classes** afin de couvrir deux types de comportements distincts dans l'application : une classe orientée orchestration métier et une classe orientée lecture et exposition de configuration.

#### 2.1.1 `RouteResource`

**Rôle**  
Point d'entrée du calcul d'itinéraires, responsable de la validation des données, du choix du profil, de la transformation de la requête et de l'appel au moteur GraphHopper.

**Justification du choix**
- **Classe centrale** : Cette classe est au cœur de la logique de routage dans GraphHopper. Elle orchestre plusieurs composants critiques (moteur de routage, résolution de profil, transformation de requête).
- **Plusieurs dépendances configurables** : La classe possède de nombreuses dépendances injectables (`GraphHopper`, `ProfileResolver`, `GHRequestTransformer`, `GraphHopperConfig`, `HttpServletRequest`), ce qui en fait une excellente candidate pour des tests unitaires basés sur des mocks.
- **Complémentarité avec les tests existants** : Les tests existants sont principalement end-to-end et testent le comportement global. Des tests unitaires avec Mockito permettent d'isoler et de valider spécifiquement l'orchestration interne, les interactions entre composants, et la propagation correcte des paramètres.
- **Complexité métier** : La classe contient une logique d'orchestration importante (validation, transformation, gestion d'erreurs) qui mérite d'être testée de manière isolée.

Cette approche garantit que les tests ne valident pas uniquement la sortie HTTP, mais également les interactions internes afin de détecter des mutations (par exemple : paramètres ignorés ou remplacés).

#### 2.1.2 `InfoResource`

**Rôle**  
Retourne les métadonnées serveur, notamment bounding box, profils, élévation et informations de construction.

**Justification du choix**
- **Complémentarité avec `RouteResource`** : Cette classe complète `RouteResource` en représentant un type différent de ressource (lecture d'informations vs. calcul de routes). Alors que `RouteResource` orchestre une logique métier complexe, `InfoResource` se concentre principalement sur la lecture et l'assemblage de données de configuration.
- **Test de l'intégration de composants** : La classe permet de tester l'intégration de plusieurs objets GraphHopper (`GraphHopperConfig`, `GraphHopper`, `EncodingManager`, `BaseGraph`, `StorableProperties`) configurés via mocks.
- **Simplicité et clarté** : La classe offre une logique plus simple mais différente, permettant de montrer comment les mocks peuvent être utilisés pour tester différents types de comportements (orchestration vs. agrégation de données).

Les mocks permettent de tester cette logique sans charger un graphe réel : la classe est évaluée uniquement sur sa capacité à assembler correctement les données provenant de ses dépendances.

---

### 2.2 Choix et justification des classes simulées

#### 2.2.1 Classes mockées pour `RouteResource`

Pour isoler `RouteResource` de ses dépendances externes, nous avons mocké les classes suivantes :

**1. `GraphHopper`**  
**Justification** : Le moteur de routage réel nécessite un graphe chargé en mémoire, ce qui est trop coûteux et lent pour des tests unitaires. En mockant `GraphHopper`, nous pouvons :
- Tester la logique d'orchestration sans dépendre de données réelles
- Simuler différents scénarios (succès, erreurs) rapidement
- Rendre les tests déterministes et reproductibles

**2. `ProfileResolver`**  
**Justification** : Cette dépendance résout le profil de routage à partir des hints de la requête. En mockant `ProfileResolver`, nous pouvons :
- Vérifier que les paramètres (hints, profil, curbsides, snap preventions) sont correctement transmis
- Tester différents scénarios de résolution de profil
- Isoler `RouteResource` de la logique de résolution de profil

**3. `GHRequestTransformer`**  
**Justification** : Cette dépendance transforme la requête avant le routage (peut ajouter des paramètres par défaut, transformer les coordonnées, etc.). En mockant `GHRequestTransformer`, nous pouvons :
- Vérifier que la requête transformée est réellement celle envoyée au moteur (et non l'originale)
- Tester l'intégration avec le transformer
- Détecter des mutations qui ignoreraient la transformation

**4. `GraphHopperConfig`**  
**Justification** : Cette dépendance fournit la configuration de GraphHopper (paramètres, copyrights, valeurs par défaut). En mockant `GraphHopperConfig`, nous pouvons :
- Tester que les valeurs de configuration (comme `routing.snap_preventions_default`) sont correctement lues et utilisées
- Tester le comportement avec différentes configurations
- Isoler `RouteResource` de la configuration système

**5. `HttpServletRequest`**  
**Justification** : Cette dépendance représente la requête HTTP entrante. En mockant `HttpServletRequest`, nous pouvons :
- Éviter toute dépendance à un conteneur web réel
- Simuler différentes requêtes HTTP (adresse IP, locale, User-Agent)
- Tester la logique de logging sans dépendre du framework web

**6. `StorableProperties`**  
**Justification** : Cette dépendance fournit les propriétés stockables du graphe (comme la date OSM). En mockant `StorableProperties`, nous pouvons :
- Initialiser correctement `RouteResource` sans charger un graphe réel
- Tester l'accès aux propriétés sans dépendre du stockage

#### 2.2.2 Classes mockées pour `InfoResource`

Pour isoler `InfoResource` de ses dépendances externes, nous avons mocké les classes suivantes :

**1. `GraphHopperConfig`**  
**Justification** : Fournit la configuration de GraphHopper (profils, GTFS, encoded values privés). En mockant `GraphHopperConfig`, nous pouvons :
- Simuler différentes configurations (ex : `gtfs.file` activé/désactivé)
- Tester le comportement avec différents profils
- Isoler `InfoResource` de la configuration système

**2. `GraphHopper`**  
**Justification** : Instance principale qui fournit l'accès aux autres composants. En mockant `GraphHopper`, nous pouvons :
- Retourner les mocks `EncodingManager`, `BaseGraph` et `StorableProperties`
- Isoler `InfoResource` de la complexité du moteur GraphHopper
- Tester la récupération des composants sans instancier un moteur réel

**3. `EncodingManager`**  
**Justification** : Gère les encoded values du graphe. En mockant `EncodingManager`, nous pouvons :
- Simuler différents encoded values (privés, publics)
- Tester la logique de filtrage et de catégorisation
- Isoler `InfoResource` de la gestion des encoded values

**4. `BaseGraph`**  
**Justification** : Graphe de base qui contient les nœuds et arêtes. En mockant `BaseGraph`, nous pouvons :
- Fournir des bounds de test sans charger un graphe réel
- Tester la création correcte du bbox
- Isoler `InfoResource` du stockage du graphe

**5. `StorableProperties`**  
**Justification** : Propriétés stockables du graphe (dates d'import, dates de données). En mockant `StorableProperties`, nous pouvons :
- Fournir des dates de test sans dépendre du stockage réel
- Tester l'affichage correct des métadonnées
- Isoler `InfoResource` du stockage des propriétés

---

### 2.3 Définition des mocks

#### 2.3.1 Configuration générale

**Approche technique** :
- Utilisation de l'annotation `@Mock` avec `MockitoExtension` pour une intégration propre avec JUnit 5
- Configuration centralisée des mocks dans la méthode `@BeforeEach setUp()` pour assurer cohérence et éviter la duplication
- Utilisation de `lenient()` pour les mocks qui ne sont pas utilisés dans tous les tests, évitant ainsi les exceptions `UnnecessaryStubbingException`

#### 2.3.2 Configuration des mocks pour `RouteResource`

**Mock `GraphHopper`** : Configuré dans chaque test selon le scénario. La méthode `route()` retourne un `GHResponse` avec un `ResponsePath` contenant distance, temps, points et instructions, ou une erreur selon le test.

**Mock `ProfileResolver`** : Configuré dans chaque test selon le profil attendu. La méthode `resolveProfile()` retourne le nom du profil (ex : "car", "bike").

**Mock `GHRequestTransformer`** : Par défaut, retourne la requête originale (pas de transformation). Dans les tests spécifiques, peut retourner une requête transformée (ex : ajout d'un algorithme par défaut).

**Mock `GraphHopperConfig`** : Par défaut, retourne la valeur par défaut fournie pour `getString(key, defaultValue)`. Retourne une liste de copyrights standard pour `getCopyrights()`. Dans les tests spécifiques, peut retourner des valeurs de configuration personnalisées.

**Mock `HttpServletRequest`** : Configuré avec des valeurs par défaut : adresse IP "127.0.0.1", locale `Locale.ENGLISH`, User-Agent "Test-Agent".

**Mock `StorableProperties`** : Configuré pour retourner une Map vide par défaut via `getAll()`. Les propriétés spécifiques peuvent être configurées dans les tests individuels.

#### 2.3.3 Configuration des mocks pour `InfoResource`

**Mock `GraphHopperConfig`** : Configuré pour retourner une liste de profils (ex : "car", "bike") via `getProfiles()`. Peut retourner `true`/`false` pour `has("gtfs.file")` selon le scénario testé.

**Mock `GraphHopper`** : Configuré pour retourner les mocks `EncodingManager`, `BaseGraph` et `StorableProperties` via leurs méthodes respectives (`getEncodingManager()`, `getBaseGraph()`, `getProperties()`).

**Mock `EncodingManager`** : Par défaut, retourne une liste vide pour `getEncodedValues()` et `false` pour `hasEncodedValue(name)`. Peut être configuré dans les tests spécifiques.

**Mock `BaseGraph`** : Configuré pour retourner un `BBox` avec des coordonnées de test (40.0, 41.0, -74.0, -73.0) via `getBounds()`.

**Mock `StorableProperties`** : Configuré pour retourner des dates de test : "2024-01-01T00:00:00Z" pour `get("datareader.import.date")` et "2024-01-15T00:00:00Z" pour `get("datareader.data.date")`.

#### 2.3.4 Utilisation d'`ArgumentCaptor`

`ArgumentCaptor` est utilisé pour capturer et vérifier les arguments passés aux méthodes mockées, garantissant que les interactions internes sont correctes. Par exemple, nous capturons les objets `GHRequest` passés à `GraphHopper.route()` et les objets `PMap` passés à `ProfileResolver.resolveProfile()` pour vérifier que les paramètres sont correctement propagés.

Cette approche garantit que les tests ne valident pas uniquement la sortie HTTP, mais également les interactions internes afin de détecter des mutations (par exemple : paramètres ignorés ou remplacés).

---

### 2.4 Choix et justification des valeurs simulées

#### 2.4.1 Valeurs simulées pour `RouteResource`

**Coordonnées géographiques** : Points `(40, -74)` et `(40.1, -74.1)`  
**Justification** : Coordonnées représentatives de la région de New York, faciles à vérifier et réalistes pour tester un parcours court.

**Distances et temps** : Distance `1000.0` mètres, temps `120000` ms (120 secondes)  
**Justification** : Valeurs cohérentes entre elles, représentatives d'un trajet réaliste, permettant de vérifier la propagation correcte des valeurs dans la réponse.

**Profils** : Profils "car" (par défaut), "bike" (pour tests de transformation), "foot" (pour tests de configuration)  
**Justification** : Profils standards et distincts permettant de tester la propagation et l'utilisation correcte des différents profils.

**Erreurs simulées** : `PointNotFoundException("Simulated Error: Point 0 not found.", 0)`  
**Justification** : Erreur représentative d'un scénario réel, permettant de tester la gestion et la propagation correcte des erreurs.

**Configuration** : `routing.snap_preventions_default = "tunnel,bridge"`  
**Justification** : Valeurs par défaut représentatives d'un environnement réel, permettant de tester que la configuration est correctement lue et appliquée.

**Requêtes transformées** : Algorithme "dijkstra" ajouté à une requête originale  
**Justification** : Transformation représentative permettant de tester que la requête transformée (avec algorithme) est utilisée au lieu de l'originale (sans algorithme).

**Adresses IP et locale** : IP "127.0.0.1", locale `Locale.ENGLISH`  
**Justification** : Valeurs standard représentatives d'un environnement de test local.

#### 2.4.2 Valeurs simulées pour `InfoResource`

**Bounding Box** : Bounds `(40.0, 41.0, -74.0, -73.0)`  
**Justification** : Coordonnées représentatives de la région de New York, faciles à vérifier, permettant de tester la création correcte du bbox avec minLon, maxLon, minLat, maxLat.

**Profils** : Profils "car" et "bike"  
**Justification** : Profils standards et représentatifs permettant de tester la liste des profils avec plusieurs éléments.

**Elevation** : Elevation `false` (par défaut) et `true` (dans certains tests)  
**Justification** : Valeurs distinctes permettant de tester que le flag d'elevation est correctement géré et propagé dans la réponse.

**Dates** : Import date `"2024-01-01T00:00:00Z"`, data date `"2024-01-15T00:00:00Z"`  
**Justification** : Dates ISO 8601 standard, distinctes entre elles, permettant de tester que les dates sont correctement récupérées depuis `StorableProperties`.

**GTFS** : `has("gtfs.file")` retourne `true`  
**Justification** : Configuration représentative d'un environnement avec GTFS activé, permettant de tester que le profil "pt" est automatiquement ajouté lorsque GTFS est configuré.

#### 2.4.3 Justification générale du choix des valeurs

**Principes de sélection** :
1. **Représentativité** : Les valeurs choisies sont représentatives d'un usage réel et typique de GraphHopper.
2. **Simplicité** : Les valeurs sont simples à mémoriser et à vérifier (coordonnées rondes, dates standardisées).
3. **Distinction** : Les valeurs diffèrent suffisamment entre les tests pour permettre de distinguer les scénarios.
4. **Cohérence** : Les valeurs sont cohérentes entre elles (ex : distance et temps correspondent à un trajet réaliste).

Ces choix permettent d'obtenir des tests déterministes, rapides, maintenables et robustes, indépendants des données externes ou des configurations système.

---

## 3. Validation

### 3.1 Validation du workflow

**Test manuel** :
1. Premier run : Le workflow s'exécute sans erreur, un artifact `mutation-score-report` est créé avec le score initial.
2. Run avec score amélioré : Le workflow passe avec succès, le nouveau score est sauvegardé.
3. Run avec score dégradé : Le workflow échoue avec le message d'erreur approprié, le rickroll s'affiche.

**Vérifications** :
- L'artifact `mutation-score-report` est créé à chaque run
- Le fichier `score.txt` contient un nombre entre 0 et 100
- Les logs affichent "Current Score: X%", "Previous Score: Y%", et le message de succès ou d'erreur approprié

### 3.2 Observations sur le score de mutation initial

**Score de mutation (2-4%)** : Le score initial était relativement bas (2-3%), avec seulement 6-8 mutations tuées sur 298 mutations totales. Après l'ajout des tests pour InfoResource, le score a augmenté à 4%.

**Raisons possibles** :
1. **Complexité de RouteResource** : Classe orchestratrice complexe avec de nombreuses dépendances et chemins d'exécution
2. **Couverture limitée** : Les tests unitaires avec mocks se concentrent sur la logique d'orchestration, mais ne couvrent pas tous les chemins de code
3. **Méthode doGet non testée** : Seule la méthode `doPost` est testée
4. **Mutations dans le code non testé** : Beaucoup de mutations se trouvent dans des branches de code qui ne sont pas exercées par les tests actuels

**Important** : Même avec un score initial bas, le workflow fonctionne correctement :
- ✅ Le workflow détecte correctement les changements de score
- ✅ Le workflow échoue lorsque le score baisse (démontré en désactivant des tests)
- ✅ Le rickroll s'affiche correctement en cas d'échec
- ✅ Le système de persistance du score fonctionne correctement

Le score initial bas n'affecte pas la fonctionnalité du workflow. Le système de monitoring fonctionne indépendamment du niveau de score, permettant d'établir un baseline et de détecter toute dégradation future.

---

## Conclusion

Cette implémentation répond à tous les objectifs de la Tâche 3 :

1. ✅ **Workflow GitHub Actions** : Le build échoue si le score de mutation baisse (voir `.github/workflows/mutation-test.yml`)
2. ✅ **Documentation** : Ce document explique tous les choix de conception et d'implémentation
3. ✅ **Tests Mockito** : 12 tests répartis sur 2 classes testées, mockant au moins 8 classes différentes
   - `RouteResourceMockTest.java` : 9 tests pour la classe RouteResource
   - `InfoResourceMockTest.java` : 3 tests pour la classe InfoResource
4. ✅ **Documentation des tests** : Justification complète des choix de classes, mocks et valeurs
5. ✅ **Rickroll** : Action réutilisable qui rickroll en cas d'échec de tests (voir `.github/actions/rickroll-action/`)

Les modifications sont maintenables, testables et suivent les bonnes pratiques de développement.