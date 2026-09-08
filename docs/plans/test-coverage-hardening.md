# Plan: Serie de Fortalecimiento de Cobertura de Tests

> Ubicación in-repo (Rule 8). El reviewer CLI (`agy.exe`) lee esta ruta.
> **AGY es el único reviewer.** Codex quedó deshabilitado por completo del flujo (2026-09-07).

---

## Context

En revisiones anteriores del proyecto se identificó deuda de cobertura de tests que nunca
se abordó porque siempre quedó "fuera de alcance" del PR en curso:

- **`CLAUDE.md` (entrada PR-24):** *"the project currently has zero repository-layer or
  Cloud Functions unit tests (a pre-existing gap...)"*. PR-24 quedó congelado indefinidamente,
  así que ese gap sigue abierto.
- **`docs/plans/group-deletion-and-group-settings-screen.md`:** *"No hay harness de tests
  para `functions/` (gap preexistente, CLAUDE.md PR-24) — no se añade aquí."*
- **`docs/plans/global-default-group.md`:** la revisión de seguridad endureció ~15 cláusulas
  de `firestore.rules`, verificadas solo por compilación de `firebase deploy`, sin un solo test.

Estado actual verificado (exploración 2026-09-07):

| Área | Hoy |
|---|---|
| Android JVM (`app/src/test/`) | 31 clases de test, ~177 `@Test`. ViewModels principales cubiertos; use cases principales cubiertos. |
| Android instrumentado (`app/src/androidTest/`) | 4 clases, composables `*Content` stateless. |
| `functions/` (TypeScript) | **0 tests, 0 framework, 0 config de emulador, 0 hook de CI.** |
| `firestore.rules` / `storage.rules` | **0 tests.** |
| Data layer Android sin cobertura | `UserPreferencesRepositoryImpl`, `MockSessionRepositoryImpl`, `MockDataProvider`, `ScheduleRepositoryImpl`, ~6 data sources Firestore, `UserRepositoryImpl`. |
| Helpers puros sin cobertura | `Typography.scaledBy`, `Font/IconScaleOption.fromKey`, `routeToScreenName`, `NflTeamColors.toAppColors`, `espnTeamLogoUrl`, `AppEvent` params, ~15 use cases delegados. |

**Resultado buscado:** una red de seguridad automatizada real para la lógica de puntuación
server-side, las reglas de seguridad y la lógica pura del cliente — entregada como serie de
PRs pequeños, cada uno construíble/testeable de forma aislada y aprobado de a uno.

### Decisiones de alcance (confirmadas con el usuario)

1. **Alcance completo:** Android + Cloud Functions + tests de reglas Firestore.
2. **Capa de datos Android: solo bajo riesgo.** Se prioriza lógica pura/casi-pura y se
   **dejan explícitamente fuera de alcance** los data sources pesados atados a Firestore
   (`FirebaseHistoryDataSource`, `FirebaseLeaderboardDataSource`, `FirebasePickDataSource`,
   `FirebaseBoardDataSource`, `FirebaseAuthDataSource`) y `UserRepositoryImpl` — los tests
   con mockk de la cadena `FirebaseFirestore→Query→Snapshot` son frágiles y de alto esfuerzo.
   Se documenta como deuda pendiente al final de la serie.
3. **Sin extracción de infra compartida.** Se mantienen los fakes `Capturing*` por archivo;
   cada PR trae sus propios fakes locales. Menos churn en archivos existentes.

---

## Convenciones y utilidades existentes a reutilizar

- **`app/src/test/java/com/softeen/nflocospicks/util/MainCoroutineRule.kt`** —
  `UnconfinedTestDispatcher` sobre `Dispatchers.Main`. Regla estándar para ViewModels.
- **`.../util/MainDispatcherRule.kt`** — `TestDispatcher` configurable; usar
  `StandardTestDispatcher` aquí cuando se necesite `advanceTimeBy(...)` (p. ej. el debounce
  de 400 ms de `AccountViewModel`).
- **Patrón data source Firebase:** `FirebaseGroupDataSourceTest.kt` — `mockk` de la cadena
  Firestore + `com.google.android.gms.tasks.Tasks.forResult(...)`. Plantilla si algún data
  source entra a alcance (por ahora, evitar).
- **`app/src/test/resources/espn_scoreboard.json`** — fixture ESPN. Se copia a
  `functions/test/fixtures/` para el test de paridad de `espn.ts`.
- **`EspnMapperTest.kt`** — referencia de paridad para `buildWeekId` (Android ↔ Functions).
- **Aserciones:** el repo mezcla `org.junit.Assert.*` y Truth `assertThat`. Seguir la
  convención del archivo/paquete vecino. TC-1 usa Truth (como `LeaderboardRankingTest`).
- Libs de test ya declaradas: JUnit4, MockK 1.14.11, Turbine 1.2.1, kotlinx-coroutines-test
  1.11.0, MockWebServer, Truth. Robolectric 4.16.1 declarado pero **sin usar** — solo
  introducirlo si un test lo justifica (p. ej. `android.os.Bundle` real en `AppLogger.toBundle`).
  Nota TC-1: los tipos Compose tocados (`Typography`/`TextStyle`/`TextUnit`, `Color`,
  `BottomNavItem` con `ImageVector`) cargan y corren en JVM plano — **no hizo falta Robolectric**.

---

## Serie de PRs

> Etiquetadas `TC-N` para no chocar con el roadmap PR-N de `CLAUDE.md`. Ramas sugeridas
> `feature/tc-NN-<slug>`. Cada PR: `./gradlew assembleDebug` + `./gradlew test` verdes
> antes de commit (Rule 3); cross-review de implementación con AGY (Rule 10);
> aprobación de a un PR por el usuario (Rule 1).

### TC-1 — Helpers puros de presentación y analytics (JVM, sin deps nuevas) — ✅ IMPLEMENTADO 2026-09-07

Máximo valor / mínimo riesgo. Solo archivos de test nuevos, cero cambios de producción.

| Objetivo | Archivo de producción | Archivo de test nuevo |
|---|---|---|
| `Typography.scaledBy(factor)` | `presentation/theme/Type.kt` | `presentation/theme/TypographyScaledByTest.kt` — identidad con `factor=1f`; escala lineal de `fontSize`+`lineHeight` en los 15 roles; preserva weight/family/letterSpacing; no muta en sitio. |
| `FontScaleOption.fromKey` / `IconScaleOption.fromKey` | `presentation/theme/FontScaleOption.kt`, `IconScaleOption.kt` | `presentation/theme/FontScaleOptionTest.kt`, `IconScaleOptionTest.kt` — key válida, `null`, key desconocida / mal capitalizada → fallback (`NORMAL` / `GRANDE`); round-trip; orden de multipliers. |
| `routeToScreenName(route)` | `presentation/navigation/ScreenNames.kt` | `presentation/navigation/RouteToScreenNameTest.kt` — `null`→`null`; cada `Screen.*` y `BottomNavItem.*`; passthrough de ruta desconocida y string vacío. |
| `NflTeamColors.toAppColors()` + `Color.darken` | `presentation/common/NflTeamColors.kt` | `presentation/common/NflTeamColorsTest.kt` — short-circuit del default a Blue Steel; paleta derivada de accent/header; `darken` preserva alpha y escala RGB (`*(1-factor)`); las 32 entradas del mapa no lanzan. |
| `espnTeamLogoUrl(abbr)` | `presentation/common/EspnLogoUrl.kt` | `presentation/common/EspnLogoUrlTest.kt` — lowercase + plantilla CDN 500px; los 32 equipos dan URL bien formada. |
| `nflTeamNameByAbbr` / `nflTeams` | `presentation/common/NflTeams.kt` | `presentation/common/NflTeamsTest.kt` — 32 franquicias, abbr únicas, sin blancos; mapa nombre-por-abbr completo y `null` para desconocido; `nflTeamColorMap` cubre exactamente las mismas abbr. |
| `AppEvent` subclases | `analytics/AppEvent.kt` | `analytics/AppEventTest.kt` — `name` y `params` por evento; **omisión (no null-fill)** del param opcional ausente en los eventos `buildMap` (`GroupOpened`, `PickSubmitted`, `LeaderboardViewed`, `PickHistoryViewed`, `FavoriteTeamSet`, `BoardMessage*`, `BoardAnnouncementToggled`); tipos (`Int`/`Long`/`Boolean`) preservados; nombres únicos en la jerarquía. |

**Verificación:** `./gradlew clean test assembleDebug` ✅ (2026-09-07). Corre en el job `verify`
de CI de PR existente sin cambios. Cross-review AGY del diff: hecho (ver Cross-Review Log →
Revisión de implementación → TC-1).

### TC-2 — Repositorios sobre DataStore + transformaciones de MockDataProvider (JVM) — ✅ IMPLEMENTADO 2026-09-07

Lógica de (de)serialización hecha a mano — donde se esconden los bugs. Fake in-memory
`DataStore<Preferences>` (`data/repository/FakePreferencesDataStore.kt`, `MutableStateFlow` +
`updateData`) — sin file IO, sin Robolectric, sin `Context`. (Es un test double compartido por
los 2 tests de este PR, no infra cross-PR; vive en el paquete de test de sus consumidores.)

| Objetivo | Archivo de test nuevo | Foco |
|---|---|---|
| `MockSessionRepositoryImpl` | `data/repository/MockSessionRepositoryImplTest.kt` | store vacío → mapas vacíos; `generateAndSaveScores` → 1 par por juego en rango 0..50; round-trip via repo fresco sobre el mismo store; `saveRealUserPick` acumula/sobrescribe, `clearRealUserPick` quita uno, no-op en store vacío; `clearSession` limpia ambos; **malformado → mapa vacío** (`runCatching`) para scores y picks; string en blanco → vacío; picks parten en el primer `:`. |
| `UserPreferencesRepositoryImpl` | `data/repository/UserPreferencesRepositoryImplTest.kt` | store vacío → `UserPreferences()` default; cada setter con valor se refleja; setters nullables **eliminan la clave** (no guardan `"null"` — se verifica `contains(key) == false`); booleanos vuelven a `false` explícito; clave preexistente se surfacea. |
| `MockDataProvider.applyScores` / `getPicksForMockUser` | `data/mock/MockDataProviderTest.kt` | `applyScores` solo marca `FINAL` los juegos en el mapa, resto intacto; mapa vacío → lista idéntica; ids desconocidos ignorados. `getPicksForMockUser` 1 pick por juego por `game.id`; determinístico por paridad `(userIndex+gameIndex)%2`; usuarios consecutivos eligen lados opuestos; estable entre llamadas. |

**Verificación:** `./gradlew clean test assembleDebug` ✅ (2026-09-07), sin warnings nuevos.
25 tests nuevos (todos `failures=0`/`errors=0`). Cross-review AGY del diff: hecho (ver
Cross-Review Log → TC-2).

### TC-3 — Use cases sin cobertura + delegación de repos + branching de ScheduleRepositoryImpl — ✅ IMPLEMENTADO 2026-09-07

Nota: `RenameGroupUseCase` y `SetGroupIconUseCase` **ya estaban cubiertos** (clases de test
dentro de `DeleteGroupUseCaseTest.kt` y `UploadGroupPhotoUseCaseTest.kt` respectivamente —
el inventario inicial estaba desactualizado). Alcance real de TC-3:

| Objetivo | Archivo de test nuevo | Foco |
|---|---|---|
| Board use cases (`Send`/`Update`/`Delete`/`SetAnnouncement`/`WatchBoardMessages`) | `domain/usecase/BoardUseCasesTest.kt` | método exacto de `BoardRepository` con args verbatim; `WatchBoardMessages` re-emite el flow; excepción del repo se propaga. |
| Query use cases (`GetCurrentWeekGames`, `GetGamesForWeek`, `GetGroupsForUser`, `GetLeaderboard`, `GetPickHistory`, `GetWeekPicks`) | `domain/usecase/QueryUseCasesTest.kt` | delegación con args verbatim; `Get*` de `Flow` re-emiten; resultado devuelto tal cual. |
| `UploadProfilePhotoUseCase` | `domain/usecase/UploadProfilePhotoUseCaseTest.kt` | reenvía `uid`+`uri`; `Result` de éxito y de fallo pasan sin transformar (`isSameInstanceAs`). |
| Thin `*RepositoryImpl` (`Board`, `History`, `Leaderboard`, `Scoring`, `Group`) | `data/repository/ThinRepositoryDelegationTest.kt` | delegación 1:1 al data source (mockk); **`GroupRepositoryImpl.uploadGroupPhoto`/`setGroupIcon` envuelven en `runCatching`** → `Result.success` cuando el DS retorna, `Result.failure(ese)` cuando lanza. |
| `ScheduleRepositoryImpl` | `data/repository/ScheduleRepositoryImplTest.kt` | `withDebugKickoffOffset` (activo bajo `testDebugUnitTest` porque `BuildConfig.DEBUG==true`): solo empuja `SCHEDULED` ~24h, deja `FINAL` con su kickoff real; `getCurrentWeekGames` cachea bajo `groups/{g}/weeks/{weekId}` con payload que tiene clave `games`; falla de Firestore es **no fatal** (devuelve juegos igual); schedule vacío → no toca Firestore; `getGamesForWeek` **no** aplica offset ni cachea, y mapea `SeasonType`→`seasontype` (PRE=1/REG=2/POST=3). |

**Verificación:** `./gradlew clean test assembleDebug` ✅ (2026-09-07), sin warnings nuevos.
27 tests nuevos (todos `failures=0`/`errors=0`). Cross-review AGY del diff: hecho (ver
Cross-Review Log → TC-3).

### TC-4 — Expandir tests delgados de ViewModel (JVM) — ✅ IMPLEMENTADO 2026-09-07

| Objetivo | Archivo | A cubrir |
|---|---|---|
| `AccountViewModel` — máquina de username | **nuevo** `AccountViewModelUsernameAvailabilityTest.kt` (`MainDispatcherRule(StandardTestDispatcher())` + `advanceTimeBy`) | blank→`Unknown`; igual al actual (case-insensitive)→`Unchanged` sin tocar repo; `Checking`→`Available`/`Taken`; edits rápidos → 1 sola verificación (del último); `.catch`→`Error` y la cadena sigue viva para queries posteriores; normalización trim+lowercase antes del repo. |
| `AccountViewModel` — phone-link | **editado** `AccountViewModelTest.kt` (reemplaza un stub vacío) | `verifyPhoneLinkCode` sin sesión → `Error(VERIFICATION_SESSION_EXPIRED)` sin log; `CodeSent`→verify OK → `Idle` + log `phone_link_verified`; verify fallido → `Error(LINK_PHONE_FAILED)` sin log. |
| `ScreenTrackingViewModel` | **nuevo** `ScreenTrackingViewModelTest.kt` | `trackScreen(name)` → `logEvent(ScreenViewed)` con `screen_name`; una llamada por evento. |
| `HistoryViewModel` | **editado** `HistoryViewModelTest.kt` | `toggleWeek` expande→colapsa el mismo id; cambia a otro id; no-op antes de cargar (estado `Loading`, sin log); `loadHistory` con excepción → `Error(mensaje)` sin log; sin usuario logueado → queda `Loading`. |
| `SettingsViewModel` | **editado** `SettingsViewModelTest.kt` | `setUseTestingData(false)` → `clearSession()` + `setSimulateGamesStarted(false)` + `setUseTestingData(false)`; `(true)` no toca la sesión mock; `setSimulateGamesStarted(true)` → `generateAndSaveScores` antes del flag, sin `clearSession`; `(false)` → `clearSession`, sin generar; `setFavoriteTeam(null)` limpia y no loguea. |
| `UserManagementViewModel` | **editado** `UserManagementViewModelTest.kt` | `setRole` → `updateUserRole` (INSIDER y REGULAR) + analytics; `users` expone el stream del repo. |
| `ChangePasswordViewModel` | **editado** `ChangePasswordViewModelTest.kt` | éxito → `Success` + log; `AuthException` conserva su `AuthError`; excepción no tipada → `Error(PASSWORD_CHANGE_FAILED)`; ninguna falla loguea. |

**Verificación:** `./gradlew clean test assembleDebug` ✅ (2026-09-07), sin warnings nuevos.
27 tests nuevos (todos `failures=0`/`errors=0`). Cross-review AGY del diff: hecho (ver
Cross-Review Log → TC-4).

### TC-5 — Harness de test para Cloud Functions + cobertura de lógica pura — ✅ IMPLEMENTADO 2026-09-07

Primer PR de infra nueva, **aislado a `functions/`** (+ CI).

- `functions/package.json`: devDeps `jest@^29.7.0`, `ts-jest@^29.4.12`, `@types/jest@^29.5.14`
  (compatibles con Node 22 / TS 5.5). Scripts `"test": "jest"`, `"test:watch": "jest --watch"`.
  **Desviación del plan:** `firebase-functions-test` **no** se agrega aquí (TC-5 es lógica pura,
  no lo usa) — se añade en TC-6 con el emulador, para no meter una dep sin usar (Rule 3).
- `functions/jest.config.js` (`preset: ts-jest`, `testEnvironment: node`, `roots: [test]`) +
  `functions/tsconfig.test.json` (extiende el base, `types: [jest, node]`, incluye `src`+`test`).
- **Cambio de producción mínimo:** `export` agregado a `buildWeekId` y `toSeasonType` en
  `functions/src/espn.ts` (funciones puras con obligación documentada de "keep in sync" con
  Android — exportarlas fortalece ese contrato). `eventToGame` sigue privada — se prueba vía
  `fetchCurrentWeekGames`.
- `functions/test/espn.test.ts` (15 tests):
  - `toSeasonType`: 1/2/3 + fallback a `REGULAR` para cualquier otro código.
  - `buildWeekId` **paridad con `EspnMapperTest.kt`**: `{year}-week-{NN}` regular, padding a 2
    dígitos, prefijo `pre-` en preseason, postseason sin prefijo (misma ambigüedad documentada).
  - `computeWinners`: gana local / visitante / empate→`null` / no-`FINAL` excluido / score
    ausente = 0.
  - `fetchCurrentWeekGames` (con `global.fetch` mockeado): mapea el fixture a `Game[]`;
    `computeWinners` recibe el mismo `gameId`; `res.ok===false` → lanza
    `ESPN scoreboard request failed: N`; evento malformado se descarta sin romper el fetch.
- `functions/test/fixtures/espn_scoreboard.json` — copia de `app/src/test/resources/espn_scoreboard.json`.
- CI: job `functions-verify` en `pr-checks.yml` y `main-checks.yml` (`actions/setup-node@v4`
  Node 22 + cache npm, `npm ci`, `npm test`, `npm run build`; `working-directory: functions`).

**Verificación:** `cd functions && npm ci && npm test && npm run build` ✅ (2026-09-07, 16/16).
Cross-review AGY del diff: hecho (ver Cross-Review Log → TC-5).

### TC-6 — Cloud Functions: lógica atada a Firestore vía emulador — ✅ IMPLEMENTADO 2026-09-07

**Desviaciones del plan (justificadas):**
- Solo se emula **`firestore`** (no `auth` ni `storage`). El único uso de Auth (`getAuth().deleteUser`
  en `deleteUserAccount`) se **stubea** con `jest.mock("firebase-admin/auth")` — un emulador de
  Auth solo añadía una dependencia frágil (`createUser` fallaba de forma intermitente). Las
  escrituras a Storage ya están en `try/catch` y sin `storageBucket` configurado fallan rápido
  y local ("Bucket name not specified") — es la ruta esperada.
- **No se usa `firebase-functions-test`.** Las funciones bajo prueba (`scoreGroupForWeek`,
  `deactivateInactiveUsers`, `reactivateUser`, `deleteUserAccount`, `deleteGroupCompletely`,
  `seedGlobalStanding`) son funciones async normales exportadas — se llaman directo contra el
  emulador vía Admin SDK. `firebase-functions-test` solo hace falta para envolver los handlers
  `onCall`/`onSchedule` de `index.ts`, que son wrappers delgados de guard de auth.
- **Aislamiento por worker:** cada worker de jest usa su propio `projectId` de emulador
  (`demo-nflocospicks-w${JEST_WORKER_ID}` en `setup.ts`) para que los 5 archivos corran en
  paralelo sin que el `clearEmulatorData` de uno borre los datos en vuelo de otro (con
  `maxWorkers:1` el cliente gRPC compartido de `firebase-admin` se corrompía entre archivos).
- CI: al job `functions-verify` se le agrega JDK 21 + cache de emuladores + paso
  `npm run test:integration`.

- `firebase.json`: bloque `emulators` (`firestore` port 8080, `ui` disabled).
- `functions/jest.config.js`: proyectos jest `unit` (puro, sin emulador — `npm test`) e
  `integration` (`test/integration/*.test.ts`, `setupFilesAfterEnv` → `setup.ts`).
- Runner: `firebase emulators:exec --project demo-nflocospicks --only firestore
  "jest --selectProjects integration"` (script `test:integration`).
- `functions/test/integration/setup.ts` — init Admin SDK, `clearEmulatorData()` vía REST
  `DELETE /emulator/v1/...` en `beforeEach`.
- Suites (real, determinístico — Admin SDK contra el emulador, 27 tests):
  - **`scoring.test.ts` → `scoreGroupForWeek`** (el corazón sin cobertura): escribe en
    `weeks/{w}/results/{uid}` y **nunca** en `picks/{uid}`; idempotencia (segunda corrida no
    re-puntúa); recomputo de `weeklyBreakdown[weekId]` y `totalPoints` (suma); empate no da
    punto; filtro `unsettled` (pick ya con result se ignora); `batch.commit` solo si `scored>0`.
    Incluir un test que **documenta el techo de 500 writes por batch** (ver Cross-Review Log
    #1): con 2 writes/miembro, un grupo >250 miembros rompería `batch.commit()`. No se
    introduce chunking (cambio de producción fuera de alcance y contrario a la decisión de
    escala "few dozen users" de PR-24 congelado) — el test fija la conducta actual y deja el
    límite escrito.
  - **`inactivity.test.ts`** — `deactivateInactiveUsers` (cutoff 1 año, salta `isActive===false`,
    standings quedan `hidden:true` **no borradas**); `reactivateUser` (no-op si activo).
    Comentar en el test que la query no tiene `.limit()`/paginación (Cross-Review Log #2) —
    aceptable a la escala actual, se documenta como limitación conocida.
  - **`accountDeletion.test.ts`** — `removeMemberFromGroup` transfiere `createdBy` a
    `remaining[0]` cuando el que sale era el admin; **caso borde `remaining.length === 0`**
    (último miembro borra su cuenta): test que fija la conducta actual y documenta el grupo
    huérfano resultante (Cross-Review Log #3) — el fix es un bugfix aparte fuera de esta serie;
    `anonymizeBoardMessages` reescribe `senderName`/`senderPhotoUrl` denormalizados y también
    tiene el techo de 500 writes (documentar, no chunkear); borra `usernames/{lowercase}`.
  - **`groupDeletion.test.ts`** — `HttpsError` para `GLOBAL_GROUP_ID`, `not-found`,
    `createdBy !== uid`; borra el doc del grupo antes del `recursiveDelete`.
  - **`globalGroup.test.ts`** (2) — `seedGlobalStanding` siembra en 0 cuando falta;
    idempotente (no pisa puntos existentes) dentro de transacción.
- CI: `functions-verify` en ambos workflows extendido con `actions/setup-java` (JDK 21),
  `actions/cache` para `~/.cache/firebase/emulators`, y paso `npm run test:integration`.
  `firebase-tools` es devDep, así que `npm run test:integration` corre tras `npm ci`.

**Verificación:** `cd functions && npm ci && npm test && npm run test:integration && npm run build`
✅ (2026-09-07 — unit 16/16, integration 27/27, build limpio). Cross-review AGY del diff:
hecho (ver Cross-Review Log → TC-6).

### TC-7 — Tests de reglas de seguridad Firestore + Storage — ✅ IMPLEMENTADO 2026-09-07

**Desviaciones del plan (justificadas):**
- devDeps `@firebase/rules-unit-testing@^5` + `firebase@^12` (peer). En `functions/`, no un
  proyecto separado.
- **`firebase.emulator.json` (nuevo, raíz):** el `firebase.json` real mantiene su `storage`
  como array multi-bucket (necesario para `firebase deploy --only storage`), que el emulador
  no puede resolver sin un deploy target ("Must supply 'target'"). El config de test usa
  `storage` en forma de objeto para que el emulador de Storage arranque. También se **revirtió
  el bloque `emulators` que TC-6 había puesto en `firebase.json`** — todo el config de emulador
  vive ahora en `firebase.emulator.json`, y `test:integration` + `test:rules` usan
  `--config ../firebase.emulator.json`.
- **`group_photos` cross-service `firestore.get()` = `it.todo`.** El runtime de reglas del
  emulador de Storage es un proceso aparte y su `firestore.get()` devuelve `null` de forma
  consistente contra el emulador de Firestore bajo `rules-unit-testing` ("Null value error"
  en `storage.rules` L24) — no se puede ejercitar la cláusula creator-only. El permiso
  equivalente (solo el creador toca `photoUrl`/`iconId`) **sí** está cubierto en
  `firestore-rules.test.ts` sobre el doc `groups/{groupId}`.
- **Bug encontrado y corregido en los tests:** poner un campo a su valor actual **no** aparece
  en `affectedKeys()` de Firestore, así que las reglas basadas en `diff()` no se disparaban y
  los tests pasaban en falso. Todas las aserciones de update usan ahora valores distintos.
- El proyecto jest `rules` usa un `projectId` fijo (no por-worker) + `maxWorkers:1` para que
  el `firestore.get` de `storage.rules` resuelva contra el mismo proyecto de emulador donde
  se siembran los datos.

- `functions/jest.config.js`: tercer proyecto jest `rules` (`test/rules/*.test.ts`).
- `functions/test/rules/harness.ts` — `initEnv()` (`initializeTestEnvironment` con
  `firestore.rules` + `storage.rules` leídos de la raíz), `assertFails`/`assertSucceeds`
  re-exportados, `TINY_PNG` fixture.
- `functions/test/rules/firestore-rules.test.ts` (18 tests): `users` (read con auth, create
  solo propio, update sin `isActive`/`disabledAt`, no-tercero update/delete), `usernames`
  (claim solo con uid propio, `update: if false`, delete solo propio), `groups` (create
  `createdBy==uid` ∧ `uid∈memberIds`, read con auth, `createdBy` inmutable, `name`/`iconId`
  solo creador, self-join ≤1 sin quitar a nadie, creador saca miembros, `delete: if false`),
  `weeks` (read miembro, write `hasOnly(['games'])`), `picks` (dueño write, miembro read,
  no-miembro no), `results` (`write:if false`), `board` (create con `senderId==uid`,
  `isAnnouncement` solo admin, delete autor|admin), `standings` (read miembro, `write:if false`).
- `functions/test/rules/storage-rules.test.ts` (10 tests + 1 todo): `profile_photos` (owner-only
  write, `<5 MiB`, `image/.*`, read con auth, anon no lee); `group_photos` (requiere auth;
  cross-service creator-only = `it.todo`).
- CI: paso `npm run test:rules` en el job `functions-verify` de ambos workflows (reusa el
  JDK 21 + cache de emuladores de TC-6).

**Verificación:** `cd functions && npm run test:rules` ✅ (2026-09-07, 28 pass + 1 todo).
Cross-review AGY del diff: hecho (ver Cross-Review Log → TC-7).

---

## Fuera de alcance (deuda documentada para después de la serie)

- `UserRepositoryImpl` (transacción de reserva de username, `upsertAndResolveRole`,
  `callbackFlow` de phone-auth) — la clase sin cobertura más grande; requiere mockk extenso
  y frágil de Firestore/Auth.
- `FirebaseHistoryDataSource`, `FirebaseLeaderboardDataSource`, `FirebasePickDataSource`,
  `FirebaseBoardDataSource`, `FirebaseAuthDataSource` — mapping/join/sort sobre mapas crudos
  de Firestore; mejor cubiertos por un test de integración con emulador de Firestore desde
  Android (esfuerzo aparte, posible TC-8 futuro).
- Tests instrumentados / de Navigation Compose — corren en CI de GitHub Actions, no local
  (convención del proyecto).
- Scripts one-shot de `functions/src/scripts/` y `functions/scripts/` — código de migración
  de un solo uso, no desplegado.
- **Bugfixes de producción encontrados durante la revisión** (no se arreglan aquí;
  esta serie solo añade tests que documentan la conducta actual):
  - Grupo huérfano cuando el último miembro borra su cuenta (`accountDeletion.ts`,
    `remaining.length === 0` — Cross-Review Log del plan #3).
  - Techo de 500 writes/batch en `scoring.ts` y `anonymizeBoardMessages` (plan #1).
  - Query sin paginación en `deactivateInactiveUsers` (plan #2).
  - `fetchCurrentWeekGames` (`functions/src/espn.ts`) lee `week`/`season` de la raíz de la
    respuesta ESPN, no por-evento como `EspnMapper.kt` (TC-5 Cross-Review Log #1). Benigno para
    la semana actual; divergiría al navegar semanas arbitrarias (que la función no hace).

---

## Verificación end-to-end de la serie

| PR | Comando |
|---|---|
| TC-1..TC-4 | `./gradlew clean test assembleDebug` (Android) |
| TC-5 | `cd functions && npm ci && npm test && npm run build` |
| TC-6 | `cd functions && npm run test:integration` (Firestore emulator vía `firebase emulators:exec`) |
| TC-7 | `cd functions && npm run test:rules` (Firestore+Storage emulator) |

Suite completa de `functions/`: `cd functions && npm ci && npm test && npm run test:integration
&& npm run test:rules && npm run build` — unit 16, integration 27, rules 28 (+1 todo), build ✅.

CI: `functions-verify` (ambos workflows) corre en cada PR/push: `npm ci` → `npm test` (unit)
→ `npm run test:integration` → `npm run test:rules` → `npm run build`. Usa Node 22 + JDK 21 +
cache de `~/.cache/firebase/emulators`. No se tocan los workflows de release/auto-tag; el job
Android `verify` y `ui-test` quedan intactos.

## Cross-Review Log (Rule 10)

### Revisión del plan

**Antigravity (`agy.exe`, gemini-3.1-pro-high, effort high) — 2026-09-07:** completado.
Artefacto: `C:\Users\brici\.gemini\antigravity-cli\brain\9091cf58-...\plan_review.md`.

| # | Hallazgo AGY | Veredicto | Acción |
|---|---|---|---|
| 1 | `scoring.ts` / `anonymizeBoardMessages` acumulan writes en un solo `batch` (techo 500). Grupo >250 miembros o >500 mensajes rompe `commit()`. | **Parcial.** Riesgo real pero cambio de producción; contradice la decisión de escala "few dozen users" que congeló PR-24. No se agrega chunking. | TC-6: tests que fijan la conducta actual y **documentan el techo de 500**. Fix de chunking = bugfix aparte si el proyecto crece. |
| 2 | `deactivateInactiveUsers` hace `.get()` de todos los usuarios inactivos sin `.limit()`/paginación → riesgo OOM/timeout a escala. | **Parcial.** Mismo argumento de escala. | TC-6: comentar la limitación en el test; no se pagina en esta serie. |
| 3 | `removeMemberFromGroup` no maneja `remaining.length === 0` (último miembro borra cuenta) → grupo huérfano, imposible de borrar (rule exige `createdBy == uid`). | **Parcial — hallazgo válido.** Bug real pero menor a la escala actual; el fix es cambio de producción. | TC-6 `accountDeletion.test.ts`: test que fija/documenta la conducta huérfana. Fix = bugfix separado fuera de esta serie. |
| 4 | TC-7 solo cubría las cláusulas `update` endurecidas; faltaban tests de `create`/`read`/`delete` básicos. | **Adoptado.** Mejora barata y dentro de alcance. | TC-7 ampliado con create/read/delete por colección. |

**Codex:** deshabilitado por completo del flujo de cross-review (decisión del usuario,
2026-09-07). No se ejecutó ni se ejecutará; AGY es el único reviewer.

### Revisión de implementación (por PR)

**TC-1 — 2026-09-07 — AGY (`agy.exe`, gemini-3.1-pro-high, effort high):** completado.
Artefacto: `C:\Users\brici\.gemini\antigravity-cli\brain\0e4dd4fb-...\plan_review.md`.

| # | Hallazgo AGY | Veredicto | Acción |
|---|---|---|---|
| 1 | `RouteToScreenNameTest` no prueba rutas con args resueltos (riesgo cardinalidad GA). | **Adoptado (test de documentación).** El caller pasa el *patrón* de ruta (`{groupId}`), no la ruta resuelta — pero conviene fijar el contrato. | Nuevo test `a resolved path with arguments is not mapped and falls through to passthrough`. La sugerencia de devolver `"unknown"` = cambio de producción deliberadamente descartado (código: "never lose an unmapped route"). |
| 2 | `EspnLogoUrlTest` usa `startsWith`/`endsWith` (podría pasar URL malformada). | **Adoptado.** | Assertion de igualdad estricta en el loop de los 32 equipos. |
| 3 | `AppEventTest` lista manual de eventos = riesgo de olvidar uno nuevo. | **Adoptado (sin reflection).** `sealedSubclasses` metería dependencia de `kotlin-reflect` + warning de compilación. | Guard `EXPECTED_EVENT_TYPES = 37` contra el tamaño de la lista y de tipos distintos; comentario para bumpearlo. |
| 4 | `NflTeamColorsTest` solo prueba alpha con `1f` (un bug que forzara alpha=1 pasaría). | **Adoptado.** | Nuevo test con alpha parcial `0.4f` (exacto bajo cuantización sRGB de 8 bits); `ALPHA_TOL = 0.01f`. |
| 5 | `Font/IconScaleOption` case-sensitive → sugiere `ignoreCase`. | **No adoptado.** Las keys las escribe la app en DataStore (KDoc), no hay superficie de typo externa; el test documenta conducta intencional. | — |

Estado TC-1: 8 archivos de test (43 tests), `./gradlew clean test assembleDebug` ✅, sin
warnings nuevos. Listo para PR (el usuario commitea/pushea).

**TC-2 — 2026-09-07 — AGY (`agy.exe`, gemini-3.1-pro-high, effort high):** completado.
Artefacto: `C:\Users\brici\.gemini\antigravity-cli\brain\be1dd08a-...\tc_2_review_findings.md`.

| # | Hallazgo AGY | Veredicto | Acción |
|---|---|---|---|
| 1 | `FakePreferencesDataStore.updateData` no serializa transforms → no imita la atomicidad del DataStore real. | **Adoptado.** | `Mutex` + `withLock` alrededor del read-transform-write. |
| 2 | Test `picks values keep everything after the first colon` es tautológico — input `"g1:KC"` tiene un solo `:`. | **Adoptado.** | Input `"g1:KC:extra"`, se afirma valor `"KC:extra"`. |
| 3 | Faltan bordes del parser a mano: colisión de delimitador en id, coma final, corrupción parcial. | **Adoptado.** | +3 tests que fijan la conducta **all-or-nothing** actual (un segmento malo → mapa vacío; coma en id → mapa vacío; coma final → mapa vacío). Documentado, no se arregla el parser. |
| 4 | `applyScores` test itera `MOCK_GAMES` global y asume todos `SCHEDULED` → frágil a cambios futuros del mock de UI. | **Adoptado.** | Lista `scheduledGames` local con helper `scheduledGame(id)` para los 3 tests de `applyScores`. |

Estado TC-2: 4 archivos de test (25 tests), `./gradlew clean test assembleDebug` ✅, sin
warnings nuevos. Listo para PR (el usuario commitea/pushea).

**TC-3 — 2026-09-07 — AGY (`agy.exe`, gemini-3.1-pro-high, effort high):** completado.
Artefacto: `C:\Users\brici\.gemini\antigravity-cli\brain\93fe1dd5-...\review_plan.md`.

| # | Hallazgo AGY | Veredicto | Acción |
|---|---|---|---|
| 1 | `ScheduleRepositoryImplTest` depende de `BuildConfig.DEBUG` (rompería bajo variante release). Sugiere inyectar `isDebug`. | **Parcial.** El refactor de producción está fuera del alcance de una serie de tests (toca DI). Verificado: **este proyecto solo tiene `testDebugUnitTest`** (no existe `testReleaseUnitTest`), así que `DEBUG==true` siempre. | `assumeTrue(BuildConfig.DEBUG)` + comentario: el test se salta (no falla) si alguna vez se agrega variante release. |
| 2 | Assert de tiempo `isLessThan(now)` para la fecha 2025 (frágil). | **Adoptado.** | Se afirma el **instante exacto** `Instant.parse("2025-09-07T17:00:00Z").toEpochMilli()` — coincide con `espnDateFormat` (`yyyy-MM-dd'T'HH:mm'Z'`, UTC). Sin `now` en la aserción del kickoff. |
| 3 | El test de cache no verifica el `weekId` exacto (`document(any())`). | **Adoptado.** | `weeksCol.document("2025-week-01")` exacto en el stub. |
| 4 | Simulación de fallo irreal (`firestore.collection()` lanza en vez de fallar el `Task`). | **Adoptado.** | El test usa `Tasks.forException(...)` en `weekDoc.set()` → `.await()` lanza dentro del try/catch de `cacheGamesToFirestore`. |
| 5 | `ThinRepositoryDelegationTest` no verificaba `getGroupById`/`getGroupsForUser`. | **Adoptado.** | +`coVerify`/`verify(exactly=1)` para ambos. |
| 6 | `BoardUseCasesTest` usa `try/catch` manual con `error(...)`. | **Adoptado.** | `org.junit.Assert.assertThrows` (convención del repo, no `kotlin.test`). |
| 7 | Solo `DeleteBoardMessageUseCase` probaba propagación de excepción. | **Adoptado.** | Un test cubre Send/Update/Delete/SetAnnouncement propagando la excepción del repo. |

Estado TC-3: 5 archivos de test (27 tests), `./gradlew clean test assembleDebug` ✅, sin
warnings nuevos. Listo para PR (el usuario commitea/pushea).

**TC-4 — 2026-09-07 — AGY (`agy.exe`, gemini-3.1-pro-high, effort high):** completado.
Artefacto: `C:\Users\brici\.gemini\antigravity-cli\brain\c581d45a-...\review_report.md`.

| # | Hallazgo AGY | Veredicto | Acción |
|---|---|---|---|
| 1a | `AccountViewModelUsernameAvailabilityTest`: el `StateFlow` es lazy (`WhileSubscribed`), sin collector el pipeline no corre. | **No aplica — AGY se equivocó.** `_usernameAvailability` es un `MutableStateFlow` escrito por un `launchIn(viewModelScope)` siempre activo, no un `stateIn(WhileSubscribed)`. Los `coVerify` sobre `isUsernameAvailable` pasan → el pipeline sí corre. Comentario aclaratorio añadido al archivo. |
| 1b | `runTest {}` sin argumento usa un scheduler distinto al de `MainDispatcherRule` → `advanceTimeBy` avanza el reloj equivocado. | **Adoptado.** Patrón correcto (igual que los otros tests del repo). | Helper `test { }` = `runTest(mainDispatcherRule.testDispatcher, testBody = …)` en cada test. |
| 1c | Faltan ramas: cancelación de un check en vuelo al seguir tecleando; borrar el campo a blanco a mitad de check. | **Adoptado.** | +2 tests: keystroke nuevo cancela el `flatMapLatest` anterior y resuelve solo el último; borrar a blanco → `Unknown`. |
| 2 | `SettingsViewModelTest`: tests con nombre "antes de" usan `coVerify` sueltos, no verifican orden. | **Adoptado.** | `coVerifyOrder { … }` en los dos tests de cascada (`setUseTestingData(false)` y `setSimulateGamesStarted(true)`). |
| 3 | `ScreenTrackingViewModelTest` sin regla de coroutines → crash si el VM toca `viewModelScope`. | **Adoptado (defensivo).** `trackScreen` es síncrono hoy. | `MainCoroutineRule` añadida + comentario. |
| 4 | `HistoryViewModelTest`: "queda `Loading` sin usuario" codifica un posible anti-patrón de UX. | **No adoptado.** Cambio de producción/producto; `HistoryScreen` solo es accesible tras el auth gate. | Comentario en el test aclarando que fija la conducta actual a propósito, no la prescribe. |

Estado TC-4: 2 archivos nuevos + 5 editados (27 tests nuevos), `./gradlew clean test
assembleDebug` ✅, sin warnings nuevos. Listo para PR (el usuario commitea/pushea).

**TC-5 — 2026-09-07 — AGY (`agy.exe`, gemini-3.1-pro-high, effort high):** completado.
Artefacto: `C:\Users\brici\.gemini\antigravity-cli\brain\6715dd22-...\tc5_review_report.md`.

| # | Hallazgo AGY | Veredicto | Acción |
|---|---|---|---|
| 1 | `fetchCurrentWeekGames` (`espn.ts`) lee `week`/`season` de la **raíz** de la respuesta; `EspnMapper.kt` (Android) los lee **por-evento** (con comentario explícito de por qué evita la raíz). | **Divergencia preexistente de producción, fuera de alcance.** Un fix cambia la lógica de scoring desplegada → PR propio. Para la "semana actual" ambos valores coinciden, así que el scoring no se ve afectado hoy. | Test-only: +1 test en `espn.test.ts` que **expone y fija** que el TS lee de la raíz (fixture con raíz≠evento). Divergencia registrada abajo en "bugfixes fuera de alcance". |
| — | Config jest/ts-jest, mock de `fetch`, aserciones de paridad `buildWeekId`/`toSeasonType`, jobs de CI, `export` de las 2 funciones puras. | **Todo Correcto/Aceptable.** | Sin cambios. |

Estado TC-5: `functions/` harness + 16 tests + CI job, `npm ci && npm test && npm run build`
✅. Listo para PR (el usuario commitea/pushea).

**TC-6 — 2026-09-07 — AGY (`agy.exe`, gemini-3.1-pro-high, effort high):** completado.
Artefacto: `C:\Users\brici\.gemini\antigravity-cli\brain\01fe0d77-...\plan_test_coverage_review.md`.

**Veredicto AGY: "solid, correct, ready to be committed. No modifications are necessary."**
Sin issues bloqueantes, sin falsos positivos. Aprobó explícitamente: cobertura de ramas de
las 5 suites, aislamiento por worker (`JEST_WORKER_ID`), el mock de Auth, y el job de CI
(JDK 21 + cache + `npm run test:integration`). 3 observaciones menores, ninguna accionable:
(1) no hay assert explícito de que el delete de Storage se llamó — aceptable (interno,
try/catch); (2) el test de batch documenta la conducta actual sin chunking — alineado con
el plan; (3) `winners` vacío → 0 puntos, conducta predecible ya cubierta implícitamente.

Estado TC-6: `firebase.json` emulators + 5 suites de integración (27 tests) + CI ampliado,
`npm ci && npm test && npm run test:integration && npm run build` ✅. Listo para PR (el
usuario commitea/pushea). (Nota: TC-7 movió el bloque `emulators` de `firebase.json` a
`firebase.emulator.json` y `test:integration` ahora usa `--config`.)

**TC-7 — 2026-09-07 — AGY (`agy.exe`, gemini-3.1-pro-high, effort high):** completado.
Artefacto: `C:\Users\brici\.gemini\antigravity-cli\brain\8cc637c4-...\test-coverage-review.md`.

| # | Hallazgo AGY | Veredicto | Acción |
|---|---|---|---|
| 1 | Tests de `firestore-rules` con valores distintos para evitar falsos positivos de no-op update. | **"Excellent" — confirmado.** | Sin cambios. |
| 2 | AGY afirma que `firestore.get()` cross-service **sí funciona** si se siembra `groups/g1` dentro de `storage-rules.test.ts`, y dice haberlo probado local con éxito. | **Parcial — no reproducible de forma estable.** Probé la versión de AGY (seed en `beforeEach` del `describe`, + delay de 500 ms) **4-7 corridas**: falla ~1 de cada 3 con "Null value error" en `storage.rules` L24. El runtime de reglas de Storage es un proceso aparte y su `firestore.get` corre carrera con la escritura del seed. La corrida única de AGY tuvo suerte. | Se mantiene `it.todo` con comentario preciso ("flaky under rules-unit-testing", no "not testable"). El permiso creator-only equivalente está cubierto de forma determinística en `firestore-rules.test.ts` sobre el doc `groups/{groupId}`. |
| 3 | `projectId` fijo + `maxWorkers:1` para el proyecto jest `rules`. | **"Correct and necessary" — confirmado.** | Sin cambios. |
| 4 | `firebase.emulator.json` y el paso de CI `npm run test:rules`. | **"Great workaround", "perfectly matches" — confirmado.** | Sin cambios. |

Estado TC-7: harness `rules-unit-testing` + 28 tests (`firestore-rules` 18, `storage-rules` 10)
+ 1 `it.todo` documentado + CI ampliado. `npm run test:rules` ✅ estable. Listo para PR (el
usuario commitea/pushea).

---

## TC-7-FIX — CI flake en `functions-verify` / `npm run test:rules`

**Síntoma (run `34166390855` job `101878149314`, 2026-09-07):** paso 9 falla.
`firestore-rules.test.ts` pasa; `storage-rules.test.ts` da
`⚠ Permission denied because no Storage ruleset is currently loaded` → `storage/unauthorized`
sobre `''`.

**Causa raíz:** `firebase emulators:exec --only firestore,storage` arranca `jest` ~1.5 s
después de empezar a descargar `cloud-storage-rules-runtime-*.jar`, **sin esperar** a que ese
runtime esté listo. `initializeTestEnvironment` empuja `storage.rules` a un runtime inexistente.
El `Post` de `actions/cache` queda `skipped` (el job falla antes) → `~/.cache/firebase/emulators`
**nunca se guarda** → cada corrida repite la carrera en frío. Local pasa porque el JAR ya está
cacheado.

### Parte A — ✅ IMPLEMENTADO 2026-09-07

- `functions/scripts/ensure-emulators.mjs` (nuevo) — revisa el cache de emuladores
  (`FIREBASE_EMULATORS_PATH || ~/.cache/firebase/emulators`) y corre
  `firebase setup:emulators:{firestore,storage}` (binario local vía `node_modules/.bin` en el
  `PATH` del `execSync`, sin `npx`) **solo** para los JARs ausentes. Idempotente: en cache
  caliente imprime "already cached — skipping" y no re-descarga los ~190 MB (`setup:emulators:*`
  a secas siempre re-baja).
- `functions/package.json` — `"pretest:rules": "node scripts/ensure-emulators.mjs"` (hook `pre*`
  de npm: corre automático antes de `test:rules`, local y en CI).
- `.github/workflows/{pr-checks,main-checks}.yml` — key del `actions/cache` de emuladores ahora
  hasheada sobre `functions/package-lock.json` (un bump de `firebase-tools` la invalida) +
  `restore-keys` para warm-start.
- Saca la descarga del camino crítico de `emulators:exec`; en cuanto una corrida pase en verde,
  `actions/cache` por fin persiste el directorio.

**Verificación:** cache caliente → "skipping", `npm ci && npm test && npm run test:integration
&& npm run test:rules && npm run build` todo verde (unit 16, integration 27, rules 28+1 todo).
`execSync` resuelve el `firebase` local (15.29.0, no el global). Lógica de decisión: cache
vacío → descargaría ambos; poblado → ninguno.

**Cross-review AGY del diff — 2026-09-07:** 4 hallazgos.
| # | Hallazgo | Acción |
|---|---|---|
| 1 | Script hardcodeaba `~/.cache/...`; `firebase-tools` respeta `FIREBASE_EMULATORS_PATH`. | **Adoptado** — `process.env.FIREBASE_EMULATORS_PATH \|\| join(homedir(), ...)`. |
| 2 | Match por substring (`firestore-emulator`) no detecta version skew tras un bump de `firebase-tools`. | **Adoptado (parcial)** — comentario en el script + key de `actions/cache` hasheada en el lockfile (un bump la invalida). El fallback fino es la Parte B. |
| 3 | `npx --no-install` deprecado en npm 7+ (ignorado en npm 9+). | **Adoptado** — se llama `firebase` directo con `node_modules/.bin` prependido al `PATH` del `execSync` (fuerza el binario local sin `npx`). |
| 4 | Portabilidad Windows/Ubuntu del script. | **"Very well-designed" — sin acción.** |

### Parte B — ✅ IMPLEMENTADO 2026-09-07

- `functions/test/rules/harness.ts` — `initEnv()` es ahora un loop de **20 intentos**: cada
  intento crea el env con `initializeTestEnvironment` y lo **sondea** con un `uploadBytes`
  autenticado a `profile_photos/probe` (`{contentType:"image/png"}`) que solo succede si el
  ruleset de Storage está cargado; si falla → `env.cleanup()` blindado en `try/catch` +
  `sleep(1000)` + reintento. Al agotar, throw con el último error. Tras la sonda OK,
  `env.clearStorage()` (descarta el objeto de prueba) y `return env`. `RULES` y `TINY_PNG` se
  suben arriba del `initEnv()` para evitar TDZ.
- `functions/test/rules/{firestore,storage}-rules.test.ts` — `beforeAll(async () => { env =
  await initEnv(); }, 60_000)` (holgura sobre el peor caso de ~20 s de reintentos; el default
  de jest es 5 s y mataría el loop).

**Repro local de la carrera de CI (prueba definitiva):** sacar el JAR
`cloud-storage-rules-runtime-*.jar` del cache y correr `npm run test:rules --ignore-scripts`
(saltando la Parte A) → el log muestra `i storage: downloading ...` seguido de `Running script:
jest` (misma carrera que CI) → **`Test Suites: 2 passed`, 28 + 1 todo, exit 0**. La Parte B
se recupera de la carrera que sin ella tumbaba CI. JAR restaurado después.

**Verificación:** `npm ci && npm test && npm run test:integration && npm run test:rules &&
npm run build` verde (16 / 27 / 28+1todo / build); `test:rules` estable 3×; `tsc` limpio.

**Cross-review AGY del diff — 2026-09-07:** *"correct, secure, and well-designed. No code
modifications are necessary."* Verificados sin objeción: sin leaks en el loop (`cleanup()`
blindado por intento), ruta de éxito limpia, sonda sin riesgo de falso positivo, holgura de
timeout (60 s vs ~20-30 s peor caso), TDZ resuelto, loop acotado. Único `[!WARNING]` no
accionable: la sonda está acoplada a la forma de la regla `profile_photos/{userId}` — se
agregó un `NOTE` en el comentario para que un endurecimiento futuro de esa regla actualice la
sonda.

### Estado del fix (Parte A + B)

Archivos: `functions/scripts/ensure-emulators.mjs` (nuevo), `functions/package.json`
(`pretest:rules`), `functions/test/rules/harness.ts` (loop de reintento), `functions/test/
rules/{firestore,storage}-rules.test.ts` (`beforeAll(..., 60_000)`), `.github/workflows/
{pr-checks,main-checks}.yml` (key de cache hasheada). Listo para PR (el usuario commitea/pushea).

## Flujo por PR (Rule 10 — revisión de implementación)

1. Implementar el PR (un paso lógico; `./gradlew assembleDebug` + `./gradlew test` verdes
   antes de commit — Rule 3).
2. Cross-review del diff: `agy.exe --mode plan --dangerously-skip-permissions -p "Review the
   current uncommitted git diff for correctness, security, and design issues" --model
   gemini-3.1-pro-high --effort high`.
3. Aplicar fixes verificados; re-correr `./gradlew test` (o `npm test` / emulador).
4. Registrar hallazgos en el Cross-Review Log de arriba.
5. Listo para PR — el usuario crea commits y push.
