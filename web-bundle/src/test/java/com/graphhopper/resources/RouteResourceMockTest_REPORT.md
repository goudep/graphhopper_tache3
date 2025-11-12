# RouteResourceMockTest - Test Design Documentation

## 1. Choix des classes testées (Class Selection)

### 1.1 Classe sous test : `RouteResource`

**Justification du choix :**
- `RouteResource` est la classe principale qui gère les requêtes de routage via l'endpoint `/route`
- Elle orchestre plusieurs dépendances (GraphHopper, ProfileResolver, GHRequestTransformer) pour traiter les requêtes de routage
- La méthode `doPost` est la méthode principale à tester car elle gère les requêtes POST pour le routage
- Cette classe contient la logique métier qui coordonne différents services, ce qui en fait un candidat idéal pour les tests unitaires avec des mocks

**Responsabilités principales :**
- Reçoit les requêtes HTTP POST avec des objets `GHRequest`
- Transforme les requêtes en utilisant `GHRequestTransformer`
- Résout les profils de routage en utilisant `ProfileResolver`
- Exécute le routage via `GraphHopper.route()`
- Gère les erreurs et retourne des réponses HTTP appropriées

### 1.2 Classe de test : `RouteResourceMockTest`

**Décision de conception :**
- Classe de test dédiée utilisant JUnit 5 et Mockito
- Utilise `@ExtendWith(MockitoExtension.class)` pour l'intégration Mockito
- Suit le pattern Arrange-Act-Assert (AAA) pour la structure des tests
- Isole la classe sous test en mockant toutes les dépendances externes

## 2. Choix des classes simulées et définition des mocks (Mocked Classes Selection and Mock Definitions)

### 2.1 `GraphHopper` (Mock 1)

**Justification du choix :**
- Moteur de routage principal qui effectue le calcul de route réel
- `RouteResource.doPost()` appelle `graphHopper.route(request)` pour obtenir les résultats de routage
- Le mock permet de contrôler la réponse de routage sans nécessiter un graphe réel
- Permet de tester à la fois les scénarios de succès et d'erreur

**Définition du mock :**
- `getProperties()` : Retourne `mockStorableProperties` pour fournir les métadonnées du graphe
- `route(GHRequest)` : Retourne soit un `GHResponse` réussi, soit un avec des erreurs

### 2.2 `ProfileResolver` (Mock 2)

**Justification du choix :**
- Résout les profils de routage en fonction des hints de la requête
- Appelé dans `doPost()` via `profileResolver.resolveProfile(profileResolverHints)`
- Le mock permet de tester avec différentes configurations de profil sans configuration réelle
- Essentiel pour tester la logique de résolution de profil

**Définition du mock :**
- `resolveProfile(PMap)` : Retourne un nom de profil (par exemple "car") en fonction des hints de la requête

### 2.3 `HttpServletRequest` (Mock 3)

**Justification du choix :**
- Paramètre requis pour la signature de la méthode `doPost()`
- Utilisé à des fins de journalisation (adresse distante, locale, User-Agent)
- Le mock évite la dépendance aux objets de requête HTTP réels
- Permet un test contrôlé des métadonnées de requête

**Définition du mock :**
- `getRemoteAddr()` : Retourne "127.0.0.1"
- `getLocale()` : Retourne `Locale.ENGLISH`
- `getHeader("User-Agent")` : Retourne "Test-Agent"

### 2.4 `GraphHopperConfig` (Mock 4)

**Justification du choix :**
- Paramètre requis du constructeur pour `RouteResource`
- Utilisé dans le constructeur pour lire les valeurs de configuration (par exemple, `routing.snap_preventions_default`)
- Utilisé dans `doPost()` pour obtenir les informations de copyright pour la réponse
- Le mock permet de tester sans fichiers de configuration réels

**Définition du mock :**
- `getString(String, String)` : Retourne la valeur par défaut (ou une chaîne vide si null)
- `getCopyrights()` : Retourne une liste de chaînes de copyright (en utilisant `lenient()` pour éviter les avertissements de stubbing inutile)

### 2.5 `GHRequestTransformer` (Mock 5)

**Justification du choix :**
- Transforme les objets `GHRequest` entrants avant le routage
- Appelé dans `doPost()` via `ghRequestTransformer.transformRequest(request)`
- Le mock permet de tester la logique de transformation indépendamment
- Comportement par défaut : retourne la requête originale inchangée

**Définition du mock :**
- `transformRequest(GHRequest)` : Retourne la requête originale (transformation identité)

### 2.6 `StorableProperties` (Mock 6)

**Justification du choix :**
- Retourné par `GraphHopper.getProperties()`
- Utilisé dans le constructeur de `RouteResource` pour extraire la date OSM : `graphHopper.getProperties().getAll().get("datareader.data.date")`
- Le mock évite la dépendance aux propriétés de graphe réelles
- Permet de tester sans graphe chargé

**Définition du mock :**
- `getAll()` : Retourne un `HashMap<String, String>` vide

### 2.7 Stratégie de configuration des mocks

**Ordre de configuration :**
1. Configurer tous les mocks **avant** de créer l'instance `RouteResource`
2. Ceci est critique car le constructeur `RouteResource` appelle des méthodes sur les mocks (par exemple, `config.getString()`)
3. Utiliser `@BeforeEach` pour garantir des mocks frais pour chaque test

**Stubbing lenient :**
- Utilisé `lenient()` pour `getCopyrights()` car tous les tests n'utilisent pas cette méthode
- Évite `UnnecessaryStubbingException` lorsque certains tests ne déclenchent pas tous les chemins de code

## 3. Choix des valeurs simulées (Simulated Values Selection)

### 3.1 Test Case 1 : Happy Path

**Valeurs simulées pour la requête :**
- Points géographiques : `(40, -74)` et `(40.1, -74.1)` - Coordonnées valides dans la région de New York
- Profil : `"car"` - Profil de routage standard pour les véhicules

**Justification :**
- Coordonnées réalistes qui représentent un trajet valide
- Profil "car" est un profil commun et standard

**Valeurs simulées pour la réponse :**
- Distance : `1000.0` mètres - Distance de test représentative
- Temps : `120000` millisecondes (2 minutes) - Temps de trajet réaliste
- Points : Liste contenant les deux points de départ et d'arrivée
- Instructions : `InstructionList` vide initialisée avec la traduction anglaise

**Justification :**
- Valeurs numériques réalistes pour un trajet court
- `InstructionList` vide évite les exceptions lors de l'accès aux instructions pendant la sérialisation JSON
- Les points correspondent aux points de la requête pour maintenir la cohérence

**Valeurs simulées pour les mocks :**
- `ProfileResolver.resolveProfile()` : Retourne `"car"` - Correspond au profil de la requête
- `GraphHopper.route()` : Retourne un `GHResponse` avec un `ResponsePath` valide

**Justification :**
- Le profil résolu correspond au profil demandé, testant le flux normal
- La réponse simulée représente un routage réussi

### 3.2 Test Case 2 : Error Path

**Valeurs simulées pour la requête :**
- Points géographiques : `(0, 0)` et `(1, 1)` - Coordonnées qui peuvent ne pas être trouvées
- Profil : `"car"` - Même profil que le test précédent pour la cohérence

**Justification :**
- Points choisis pour simuler un scénario où un point n'est pas trouvé
- Profil identique permet d'isoler le test sur la gestion d'erreur

**Valeurs simulées pour l'erreur :**
- Type d'erreur : `PointNotFoundException`
- Message : `"Simulated Error: Point 0 not found."`
- Index du point : `0` - Indique que le premier point n'a pas été trouvé

**Justification :**
- `PointNotFoundException` est une erreur courante dans GraphHopper
- Le message et l'index permettent de vérifier que l'erreur est correctement propagée
- L'index 0 indique clairement quel point pose problème

**Valeurs simulées pour les mocks :**
- `ProfileResolver.resolveProfile()` : Retourne `"car"` - Même comportement que le test précédent
- `GraphHopper.route()` : Retourne un `GHResponse` avec une erreur ajoutée via `addError()`

**Justification :**
- Le mock retourne une réponse avec erreur plutôt que de lancer une exception, ce qui correspond au comportement réel de GraphHopper
- Permet de tester que `RouteResource` détecte correctement les erreurs via `hasErrors()` et lance `MultiException`

### 3.3 Valeurs par défaut dans setUp()

**Valeurs simulées pour `GraphHopperConfig` :**
- `getString(String, String)` : Retourne la valeur par défaut fournie (ou chaîne vide si null)
- `getCopyrights()` : Retourne `["GraphHopper", "OpenStreetMap contributors"]`

**Justification :**
- Comportement par défaut réaliste qui correspond à une configuration standard
- Les copyrights sont des valeurs standard utilisées dans les réponses GraphHopper

**Valeurs simulées pour `HttpServletRequest` :**
- `getRemoteAddr()` : `"127.0.0.1"` - Adresse localhost standard
- `getLocale()` : `Locale.ENGLISH` - Locale par défaut
- `getHeader("User-Agent")` : `"Test-Agent"` - Valeur de test simple

**Justification :**
- Valeurs standard pour les tests qui ne nécessitent pas de valeurs spécifiques
- Permet de tester la journalisation sans dépendre de valeurs réelles de requête HTTP

**Valeurs simulées pour `GHRequestTransformer` :**
- `transformRequest(GHRequest)` : Retourne la requête originale inchangée

**Justification :**
- Transformation identité simplifie les tests en permettant de tester la logique de routage sans transformation complexe
- Les tests individuels peuvent surcharger ce comportement si nécessaire

**Valeurs simulées pour `StorableProperties` :**
- `getAll()` : Retourne un `HashMap` vide

**Justification :**
- Map vide suffit car les tests ne dépendent pas de propriétés spécifiques du graphe
- Évite la nécessité de configurer des propriétés complexes
