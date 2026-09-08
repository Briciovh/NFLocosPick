# Plan — Eliminar grupo (solo dueño/admin) + Pantalla de configuración de grupo

---

## Context

Hoy **no existe** ningún mecanismo para eliminar un grupo, ni una pantalla de configuración de
grupo. Lo único que hay:

- `firestore.rules` (`groups/{groupId}` → `allow delete`, líneas 92-94) *permitiría* al creador
  borrar el doc del grupo, pero **ningún código cliente lo invoca**, y borrar solo ese doc dejaría
  huérfanas las subcolecciones (`weeks/**`, `board/**`, `results`) y todo `standings/{groupId}/**`
  (que son `allow write: if false` para clientes).
- La única superficie de "configuración de grupo" es `GroupImagePickerDialog` (solo foto/ícono),
  abierta desde el lápiz en `GroupHeaderBar` y desde la tarjeta en `GroupsScreen`. La ruta que sale
  de *Ajustes de usuario* es `UserManagementScreen` — gestión de rol INSIDER global, no es de grupo.

**Objetivo:** una `GroupSettingsScreen` nueva (renombrar + foto/ícono + eliminar grupo), accesible
solo para el admin (`group.createdBy == currentUserId`) desde (a) un ícono de engrane en
`GroupHeaderBar` dentro de la sesión de grupo y (b) una acción en la tarjeta de grupo en
`GroupsScreen`. El borrado es completo e irreversible y corre en una Cloud Function con Admin SDK.

**Admin = creador del grupo.** No hay campo de rol por grupo; el patrón `group.createdBy == currentUserId`
ya se usa en `GroupHeaderBar.kt:52`, `GroupViewModel.kt:185/199`, `BoardViewModel.kt:72`.

**Grupo global** (`GlobalGroupConstants.GROUP_ID = "global_nflocos_de_corazon"`): nunca borrable,
y el engrane/acción de settings se ocultan para él (como ya se ocultan copiar/compartir código).

> **Nota Rule 10:** Codex quedó deshabilitado por completo del flujo de cross-review (decisión
> del usuario, 2026-09-07). El cross-review corre solo con Antigravity (`agy.exe`). El review de
> plan con AGY ya está hecho — ver "Cross-Review Log".

---

## Paso 1 — Cloud Function `deleteGroup` + cerrar `allow delete` en reglas

**Estado actual:** no hay función de borrado; el doc del grupo es borrable por el creador desde el cliente.
**Estado objetivo:** una callable `deleteGroup` borra grupo + subcolecciones + standings + foto de Storage;
el cliente ya no puede borrar el doc directamente.

- **`functions/src/groupDeletion.ts`** (nuevo) — helper `deleteGroupCompletely(groupId, uid)`:
  - `const db = getFirestore();` leer `groups/{groupId}`; si no existe → lanzar `HttpsError("not-found", ...)`.
  - Validar `groupSnap.data()?.createdBy === uid` → si no, `HttpsError("permission-denied", ...)`.
  - Validar `groupId !== GLOBAL_GROUP_ID` (import de `./globalGroup`) → si no, `HttpsError("permission-denied", ...)`.
  - **`await groupRef.delete();` PRIMERO** (borra solo el doc del grupo) — así, si `scoreGroupForWeek`
    corre concurrentemente, su `groups/{groupId}.get()` devuelve `!exists` y aborta temprano en lugar
    de re-crear `weeks/**/results` huérfanos tras el barrido (mitiga hallazgo CR-5).
  - `await db.recursiveDelete(db.collection("groups").doc(groupId));` — barre `weeks/**` (incl. `picks`,
    `results`) + `board/**` (opera sobre las subcolecciones aunque el doc ya no exista).
    (`recursiveDelete` está disponible: `firebase-admin` 12.7.0.)
  - `await db.recursiveDelete(db.collection("standings").doc(groupId));` — borra el árbol `members/**`.
  - Borrar la foto de Storage en `try/catch`; en el catch **`logger.warn`** (no `logger.info` silencioso)
    y **no** re-lanzar — el borrado de Firestore ya se completó, propagar un fallo de Storage reportaría
    como fallida una eliminación ya efectiva (hallazgo CR-4, adoptado parcialmente).
    `getStorage().bucket().file(\`group_photos/${groupId}\`).delete()`.
  - `logger.info(\`deleteGroup: grupo ${groupId} eliminado por ${uid}\`);`
- **`functions/src/index.ts`** — `import { deleteGroupCompletely } from "./groupDeletion";` y
  `export const deleteGroup = onCall<{ groupId?: string }>(async (request) => { ... })` calcado de
  `scoreGroupWeek` (líneas 57-87): check `request.auth?.uid` → `unauthenticated`; `request.data.groupId`
  → `invalid-argument`; delega en el helper (que lanza `HttpsError` con los códigos correctos);
  `return { success: true };`. Mensajes de error en español neutro mexicano.
- **`firestore.rules`** — `groups/{groupId}` `allow delete`: cambiar de la condición actual a
  `allow delete: if false;` con comentario: "el borrado de grupo va exclusivamente por la Cloud
  Function `deleteGroup` (Admin SDK), que además limpia subcolecciones y `standings/{groupId}` —
  ver `functions/src/groupDeletion.ts`". El grupo global ya queda cubierto (nadie puede borrar).
- **Verificación:** `cd functions && npm run build` (tsc) verde. `firestore.rules` compila.
  No hay harness de tests para `functions/` (gap preexistente, CLAUDE.md PR-24) — no se añade aquí.
- **Deploy (lo corre el usuario):** `firebase deploy --only firestore:rules,storage` (Rule 6, ambas
  juntas) **y** `firebase deploy --only functions`.

---

## Paso 2 — Capa domain + data: `deleteGroup` y `renameGroup`

**Estado objetivo:** `GroupRepository` expone borrar (vía callable) y renombrar (update simple);
existen los use cases correspondientes.

- **`domain/repository/GroupRepository.kt`** — añadir:
  `suspend fun deleteGroup(groupId: String)` y `suspend fun renameGroup(groupId: String, newName: String)`.
- **`data/remote/firebase/FirebaseGroupDataSource.kt`**:
  - Inyectar `FirebaseFunctions` en el constructor (hoy `(firestore, storage)`; ya provisto por
    `di/FirebaseModule.kt:34`).
  - `suspend fun deleteGroup(groupId)` → `functions.getHttpsCallable("deleteGroup").call(mapOf("groupId" to groupId)).await()`
    — mismo patrón que `FirebaseScoringDataSource.scoreWeek`.
  - `suspend fun renameGroup(groupId, newName)` → `firestore.collection(COLLECTION).document(groupId).update("name", newName).await()`
    (las reglas ya permiten `name` al `createdBy`, `firestore.rules:72-76`).
- **`data/repository/GroupRepositoryImpl.kt`** — delegar ambos (`renameGroup` sin `runCatching`, como
  `getGroupById`; `deleteGroup` igual).
- **`domain/usecase/DeleteGroupUseCase.kt`** y **`RenameGroupUseCase.kt`** (nuevos) — calcados de
  `ScoreWeekPicksUseCase` (`@Inject constructor(groupRepository)`, `suspend operator fun invoke(...)`).
- **Fakes de test que implementan `GroupRepository`** (añadir los overrides nuevos, normalmente
  `throw NotImplementedError()` salvo donde se prueben):
  `CreateGroupUseCaseTest.kt`, `JoinGroupUseCaseTest.kt`, `UploadGroupPhotoUseCaseTest.kt`,
  `integration/GroupViewModelIntegrationTest.kt`.
- **Tests nuevos:**
  - `DeleteGroupUseCaseTest` / `RenameGroupUseCaseTest` — estilo fake escrito a mano (como
    `JoinGroupUseCaseTest`), verifican que se delega con los args correctos.
  - `FirebaseGroupDataSourceTest` — casos nuevos con MockK sobre `FirebaseFirestore`/`FirebaseFunctions`
    (`Tasks.forResult(...)`): `deleteGroup` invoca la callable con `{groupId}`; `renameGroup` invoca
    `document(id).update("name", ...)`.
- **Verificación:** `./gradlew assembleDebug` + `./gradlew test`.

---

## Paso 3 — `GroupViewModel`: acciones de eliminar y renombrar

**Estado objetivo:** el VM expone estado/acciones para borrar y renombrar, con guard de admin y
efecto de un disparo para el feedback.

- **`presentation/groups/GroupUiState.kt`**:
  - Nuevo `sealed class GroupSettingsUiState { Idle; Working; Error(message) }` (cubre borrar y renombrar).
  - `GroupUiEffect` — añadir `data class GroupDeleted(val groupName: String) : GroupUiEffect()`.
- **`presentation/groups/GroupViewModel.kt`**:
  - Inyectar `DeleteGroupUseCase`, `RenameGroupUseCase`.
  - `_groupSettingsState: MutableStateFlow<GroupSettingsUiState>` + `val groupSettingsState`.
  - `fun deleteGroup(group: Group, requesterUserId: String)`:
    - guard `if (group.createdBy != requesterUserId) return` (igual que `uploadGroupPhoto:185`),
    - guard `if (group.id == GlobalGroupConstants.GROUP_ID) return`,
    - guard mock: `if (group.id == MockDataProvider.MOCK_GROUP.id) return` (evita callable con id falso),
    - `Working` → `deleteGroupUseCase(group.id)` → éxito: `effects.send(GroupUiEffect.GroupDeleted(group.name))`,
      `logger.logEvent(AppEvent.GroupDeleted(group.id))`, dejar estado en `Idle`; error: `Error(msg)`.
  - `fun renameGroup(group, requesterUserId, newName)`: mismos guards; `renameGroupUseCase(group.id, newName.trim())`;
    `logger.logEvent(AppEvent.GroupRenamed(group.id))`. (El nombre nuevo llega solo por el listener en vivo.)
  - `fun resetGroupSettingsState()`.
- **`analytics/AppEvent.kt`** — añadir `data class GroupDeleted(val groupId: String)` →
  `AppEvent("group_deleted", mapOf("group_id" to groupId))` y `GroupRenamed` análogo, siguiendo
  `GroupCreated` (líneas 13-14). Actualizar la tabla "Event Inventory" en `CLAUDE.md` y
  `.agents/rules/constraints-analytics.md` (registrar como Custom Dimensions es paso de consola, fuera de alcance).
- **Tests** — `GroupViewModelTest` (MockK): borrar OK emite `GroupDeleted` + loguea; no-admin es no-op;
  grupo global es no-op; error deja `GroupSettingsUiState.Error`; renombrar OK loguea.
- **Verificación:** `./gradlew assembleDebug` + `./gradlew test`.

---

## Paso 4 — `GroupSettingsScreen` + ruta + wiring en `NavGraph`

**Estado objetivo:** pantalla nueva en el NavHost raíz, con renombrar / cambiar imagen / eliminar,
solo admin.

- **`presentation/navigation/Screen.kt`** — `data object GroupSettings : Screen("group_settings/{groupId}") { fun createRoute(groupId: String) = "group_settings/$groupId" }`.
- **`presentation/groups/GroupSettingsScreen.kt`** (nuevo) — `Scaffold` + `TopAppBar` con flecha atrás
  (`title = R.string.group_settings_title`), colores de `LocalAppColors` como el resto de pantallas.
  Firma: `group: Group?`, `currentUserId: String?`, `photoUiState: GroupPhotoUiState`,
  `settingsState: GroupSettingsUiState`, `onRename: (String) -> Unit`, `onUploadPhoto: (Uri) -> Unit`,
  `onSetIcon: (String) -> Unit`, `onDeleteGroup: () -> Unit`, `onDismissPhotoPicker: () -> Unit`,
  `onNavigateBack: () -> Unit`, `onExitToGroups: () -> Unit`.
  - Si `group == null` (con `groupListState is Success`) o `group.createdBy != currentUserId` →
    `LaunchedEffect { onNavigateBack() }` (defensa; la UI no debería llegar aquí para no-admins).
  - **Sección renombrar:** `OutlinedTextField` con `group.name` inicial + botón "Guardar"
    (`R.string.group_settings_rename_action`), deshabilitado si vacío o sin cambios o `settingsState is Working`.
  - **Sección imagen:** fila "Cambiar imagen del grupo" que abre el `GroupImagePickerDialog` existente
    (reutilizado tal cual).
  - **Sección peligro:** botón "Eliminar grupo" (color error) → `AlertDialog` de advertencia calcado de
    `AccountScreen.kt:228-250` (`title`/`body`/`confirm`/`Cancelar`) → al confirmar llama `onDeleteGroup()`.
    Mostrar `settingsState.message` si `Error`.
  - **Salida tras borrar:** `var deleteRequested by remember { mutableStateOf(false) }`; se pone `true`
    al confirmar; `LaunchedEffect(groupListState, deleteRequested) { if (deleteRequested && groupListState is GroupListUiState.Success && group == null) onExitToGroups() }`.
    **Gate obligatorio en `groupListState is Success`** — `group` también es `null` durante
    `GroupListUiState.Loading`, sin ese gate la pantalla saldría sola en el arranque (hallazgo CR-1b).
    `onExitToGroups` navega **directo a `Screen.Groups`**, no `popBackStack()` — volver un nivel dejaría
    al admin en el `GroupSessionScreen` ya roto del grupo borrado (hallazgo CR-2). El snackbar
    "Grupo eliminado" lo muestra `GroupsScreen` al consumir `GroupUiEffect.GroupDeleted`: el `Channel`
    es `BUFFERED`, así que el efecto se entrega cuando `GroupsScreen` reanuda su colector al recomponerse
    (hallazgo CR-3, cubierto por CR-2).
- **`presentation/navigation/NavGraph.kt`** — `composable(Screen.GroupSettings.route)` calcado del bloque
  `Screen.History` (líneas 193-213): `groupsEntry` → `hiltViewModel(groupsEntry)` (mismo `GroupViewModel`
  compartido), sacar `groupListState`, `group`, `currentUserId`, `photoUiState`, y `groupSettingsState`. Wire:
  `onRename = { name -> group?.let { g -> currentUserId?.let { u -> groupViewModel.renameGroup(g, u, name) } } }`,
  `onDeleteGroup = { group?.let { g -> currentUserId?.let { u -> groupViewModel.deleteGroup(g, u) } } }`,
  `onUploadPhoto`/`onSetIcon`/`onDismissPhotoPicker` idénticos al bloque `GroupSession` (líneas 240-242),
  `onNavigateBack = { navController.popBackStack() }`,
  `onExitToGroups = { navController.navigate(Screen.Groups.route) { popUpTo(Screen.Groups.route) { inclusive = false }; launchSingleTop = true } }`.
- **Verificación:** `./gradlew assembleDebug` + `./gradlew test` (previews compilan).

---

## Paso 5 — Puntos de acceso: engrane en `GroupHeaderBar` + acción en `GroupCard`

**Estado objetivo:** el admin puede abrir la pantalla nueva desde la sesión de grupo y desde la lista.

- **`presentation/common/GroupHeaderBar.kt`**:
  - Nuevo param `onSettingsClick: (() -> Unit)? = null`.
  - Dentro de `if (canEdit)` (línea 127), **antes** del `Box` del lápiz, añadir un `Box` gemelo con
    `Icons.Filled.Settings`, `contentDescription = R.string.cd_group_settings`,
    `testTag(TestTags.GROUP_SETTINGS_BUTTON)`, `clickable { onSettingsClick?.invoke() }` — solo si
    `onSettingsClick != null` (el grupo global ya no llega aquí porque `canEdit` implica ser creador y
    el grupo global lo administra `nezaboost`; aun así, gate extra `group.id != GlobalGroupConstants.GROUP_ID`).
  - `TestTags.kt` — `const val GROUP_SETTINGS_BUTTON = "group_settings_button"`.
- **`presentation/navigation/GroupSessionScreen.kt`**:
  - Nuevo param `onNavigateToGroupSettings: (String) -> Unit`.
  - En el lambda `groupHeader` (líneas 78-84): `onSettingsClick = { onNavigateToGroupSettings(groupId) }`.
- **`NavGraph.kt`** bloque `Screen.GroupSession` (líneas 215-244):
  - Añadir `onNavigateToGroupSettings = { gId -> navController.navigate(Screen.GroupSettings.createRoute(gId)) }`.
  - **Auto-salida para cualquier miembro (no solo el admin)** — cuando otro grupo/otro dispositivo borra
    el grupo, el listener compartido deja de devolverlo y `group` pasa a `null` mientras el miembro está
    dentro de la sesión, dejando un `GroupSessionScreen` "fantasma" (hallazgo CR-1a). Añadir:
    `LaunchedEffect(groupListState, group) { if (groupListState is GroupListUiState.Success && group == null) navController.navigate(Screen.Groups.route) { popUpTo(Screen.Groups.route) { inclusive = false }; launchSingleTop = true } }`.
    Gate en `Success` para no expulsar durante `Loading` (CR-1b). Solo corre cuando `GroupSession` es el
    destino visible (Nav Compose no compone entradas del back stack que no están al frente), así que no
    colisiona con la salida propia de `GroupSettingsScreen`.
- **`presentation/groups/GroupsScreen.kt`**:
  - `GroupCard` — nuevo param `onOpenSettings: () -> Unit`; render un `IconButton` con
    `Icons.Filled.Settings` (`cd_group_settings`) al final del `Row` (línea 321), solo si
    `canEdit && group.id != GlobalGroupConstants.GROUP_ID`. `IconButton` propio ⇒ no dispara el
    `onClick` de la `Card` (mismo patrón que el `Box` de copiar código dentro de la card).
  - `GroupsScreenContent` — nuevo param `onOpenGroupSettings: (String) -> Unit`; pasarlo a `GroupCard`
    como `onOpenSettings = { onOpenGroupSettings(group.id) }`. Actualizar los 2 `@Preview` (pasar `{}`).
  - `GroupsScreen` — nuevo param `onNavigateToGroupSettings: (String) -> Unit`; enlazarlo.
  - Consumir el nuevo efecto en el `LaunchedEffect` de efectos (línea 94):
    `is GroupUiEffect.GroupDeleted -> snackbarHostState.showSnackbar(String.format(groupDeletedPattern, effect.groupName))`.
- **`NavGraph.kt`** bloque `Screen.Groups` (líneas 71-91) — añadir
  `onNavigateToGroupSettings = { id -> navController.navigate(Screen.GroupSettings.createRoute(id)) }`.
- **Verificación:** `./gradlew assembleDebug` + `./gradlew test`.

---

## Paso 6 — Strings, tests finales y cross-review

- **`app/src/main/res/values/strings.xml` y `values-en/strings.xml`** (ambos, en sync) — nuevas claves:
  `group_settings_title`, `cd_group_settings`, `group_settings_rename_label`, `group_settings_rename_action`,
  `group_settings_change_image`, `group_settings_delete`, `group_settings_delete_dialog_title`,
  `group_settings_delete_dialog_body` ("Se eliminarán el grupo, sus semanas, todos los picks, los
  mensajes del muro y la tabla de posiciones de todos los miembros. Esta acción no se puede deshacer."),
  `group_settings_delete_dialog_confirm`, `group_settings_error`, `group_deleted_snackbar` ("Grupo %1$s eliminado").
  Español neutro mexicano (Rule 5).
- **Cobertura de tests** (solo unit / `./gradlew test`; los instrumentados corren en CI):
  `DeleteGroupUseCaseTest`, `RenameGroupUseCaseTest`, `FirebaseGroupDataSourceTest` (casos nuevos),
  `GroupViewModelTest` (casos nuevos), fakes de `GroupRepository` actualizados (Paso 2).
- **Cross-review de implementación:** **solo AGY** (Codex deshabilitado del flujo):
  `agy.exe --mode plan --dangerously-skip-permissions -p "Review the current uncommitted git diff for correctness, security, and design issues" --model gemini-3.1-pro-high --effort high`.
  Documentar hallazgos en el Cross-Review Log.
- **Gradle:** no cambian archivos Gradle ⇒ no `./gradlew dependencies`. Correr `./gradlew assembleDebug`
  y `./gradlew test`; arreglar y re-correr hasta verde.
- **Branch:** feature nueva fuera del roadmap (p. ej. `feature/group-deletion`); el usuario decide
  numeración/roadmap. Commits/push siempre los hace el usuario (Rule 10.8).

---

## Cross-Review Log

### Review de plan

- **Antigravity (`agy.exe`, gemini-3.1-pro-high, effort high)** — corrido el 2026-09-05. 5 hallazgos,
  todos revisados contra el código:

  | # | Hallazgo | Veredicto | Acción |
  |---|---|---|---|
  | CR-1a | Miembros (no admin) dentro de `GroupSessionScreen`/tabs quedan en pantalla "fantasma" cuando el grupo se borra desde otro lado (el listener compartido devuelve `group == null`). | **Válido.** `NavGraph` línea 230 hace `groups.find{...}`; `GroupSessionScreen` acepta `group: Group?` y degrada sin cerrarse. | Adoptado — `LaunchedEffect` de auto-salida a `Screen.Groups` en el bloque `Screen.GroupSession` de `NavGraph` (Paso 5). |
  | CR-1b | Cualquier auto-salida por `group == null` también dispararía durante `GroupListUiState.Loading`. | **Válido.** `group` es `null` en `Loading`. | Adoptado — todas las condiciones de auto-salida llevan gate `groupListState is GroupListUiState.Success` (Pasos 4 y 5). |
  | CR-2 | La auto-salida de `GroupSettingsScreen` vía `popBackStack()` deja al admin en el `GroupSessionScreen` roto. | **Válido.** | Adoptado — `onExitToGroups` navega directo a `Screen.Groups` con `popUpTo(Groups)` (Paso 4). |
  | CR-3 | El snackbar "Grupo eliminado" lo consume `GroupsScreen`, que puede no ser el destino activo. | **Válido pero ya cubierto por CR-2** — al ir directo a `Groups`, su colector reanuda y el `Channel.BUFFERED` no pierde el efecto. | Sin cambio extra; anotado en Paso 4. |
  | CR-4 | `try/catch` general al borrar la foto de Storage traga errores legítimos (IAM, red), no solo el 404. | **Válido parcialmente.** Re-lanzar tras borrar Firestore reportaría como fallida una eliminación ya hecha — peor. | Adoptado parcial — `logger.warn` (visible) en el catch, sin re-lanzar (Paso 1). |
  | CR-5 | `scoreGroupWeek`/`scheduledScoring` concurrente con `deleteGroup` podría re-crear `results/**`/`picks/**` huérfanos tras `recursiveDelete`. | **Válido, riesgo bajo.** `scoreGroupForWeek` lee `groups/{id}` primero. | Adoptado — `deleteGroup` hace `groupRef.delete()` **antes** del `recursiveDelete`, para que el scoring concurrente aborte temprano (Paso 1). Ventana residual mínima; huérfanos inertes. |

- **Codex** — deshabilitado por completo del flujo de cross-review (decisión del usuario,
  2026-09-07). Se corrió solo AGY; no hay revisión de Codex pendiente.

### Review de implementación

- **Paso 1 — Antigravity (`agy.exe`), 2026-09-05, contra el diff sin commitear** (`functions/src/groupDeletion.ts`
  nuevo, edits a `functions/src/index.ts` y `firestore.rules`): **sin hallazgos accionables.** Confirma
  auth/authorization (solo `createdBy`, grupo global protegido), el `groupRef.delete()` antes del
  `recursiveDelete` como mitigación correcta de CR-5, `allow delete: if false` correcto, y el `try/catch`
  + `logger.warn` de Storage acorde a CR-4. Nota pre-existente y fuera de alcance: la regla de
  `weeks/{weekId}/picks/{userId}` no verifica que el grupo exista, así que en teoría se podría escribir
  un pick huérfano a un grupo borrado — dato inaccesible (las reglas de lectura exigen membresía),
  fuga de almacenamiento insignificante. No se toca.
  - **Deploy hecho:** `firebase deploy --only firestore:rules,storage` corrido por Claude el 2026-09-05
    (`firestore.rules` compiló y se liberó). `firebase deploy --only functions` **pendiente** — se hará
    junto con un paso posterior o cuando el cliente ya llame a `deleteGroup`.
- **Paso 2 — Antigravity (`agy.exe`), 2026-09-05, contra el diff acumulado** (capa domain/data +
  use cases + tests): **sin hallazgos accionables.** Confirma el cambio de constructor de
  `FirebaseGroupDataSource` (+`FirebaseFunctions`), el patrón de invocación de la callable, y que
  `renameGroup` solo escribe el campo `name`. Único comentario: nota de estilo — `RenameGroupUseCaseTest`
  vive dentro de `DeleteGroupUseCaseTest.kt`. **No se aplica:** es exactamente el patrón del repo
  (`UploadGroupPhotoUseCaseTest.kt` ya contiene `UploadGroupPhotoUseCaseTest` + `SetGroupIconUseCaseTest`).
  `./gradlew assembleDebug test` verde.
- **Paso 3 — Antigravity (`agy.exe`), 2026-09-05, contra el diff acumulado** (`GroupViewModel` +
  `GroupUiState` + `AppEvent` + inventario analytics + strings + tests): **sin hallazgos estructurales.**
  Confirma el guard `canManageGroup` (creador, no global, no mock), las transiciones
  `Working→Idle/Error`, el efecto `GroupDeleted` por el `Channel` bufferizado y su consumo en
  `GroupsScreen`, y la corrección de corrutinas (`viewModelScope.launch`, `catch (Exception)` que
  también atrapa `CancellationException` — patrón ya usado en `onScoreClicked`). Sugerencia de
  cobertura (aplicada): añadidos tests de no-op para `renameGroup` en el grupo global y para
  `deleteGroup` en el grupo mock, espejando la exhaustividad del guard. `./gradlew assembleDebug test` verde.
  - Nota de alcance: el consumidor del efecto en `GroupsScreen` (`is GroupUiEffect.GroupDeleted ->`)
    y la string `group_deleted_snackbar` (es/en) se adelantaron del Paso 5/6 a este paso porque el
    `when` sobre el sealed `GroupUiEffect` deja de compilar sin la rama nueva.
- **Paso 4 — Antigravity (`agy.exe`), 2026-09-05, contra el diff acumulado** (`GroupSettingsScreen.kt`
  nuevo + `Screen.kt` + bloque `composable(GroupSettings)` en `NavGraph.kt` + `ScreenNames.kt` +
  strings es/en): **sin defectos.** Confirma la lógica del `LaunchedEffect` de auto-salida (gates
  `deleteRequested`/`Success`/`isAdmin` — el fallo de borrado deja `group != null` así que la
  pantalla no huye y se ve el error), el `remember(group?.name)` del campo de nombre + el `enabled`
  del botón alineado con el `trim()` del use case, la reutilización de `GroupImagePickerDialog`, el
  `AlertDialog` de confirmación, y el `getBackStackEntry(Groups)` + `hiltViewModel` compartido.
  - Observación menor (no defecto, **no se cambia**): si `groupListState` pasara a `Error`
    (fallo catastrófico de Firestore), `group` sería `null` y el gate `!is Success` bloquearía la
    salida → spinner indefinido. Es un caso negligible (los `Error` solo ocurren en carga inicial,
    que ya está descartada) y es exactamente cómo `HistoryScreen` maneja su `group: Group?`.
  - `ScreenNames.routeToScreenName` incluye `Screen.GroupSettings.route -> "group_settings"` para el
    evento `screen_viewed` (extra menor sobre lo que pedía el plan, alineado con el resto de rutas).
  - `./gradlew assembleDebug test` verde.
- **Paso 5 — Antigravity (`agy.exe`), 2026-09-05, contra el diff acumulado** (engrane en
  `GroupHeaderBar` + acción en `GroupCard` + `onNavigateToGroupSettings` en `GroupSessionScreen`/
  `NavGraph` + `LaunchedEffect` de auto-salida para miembros + `TestTags`). 4 hallazgos, revisados
  contra el código:

  | # | Hallazgo AGY | Veredicto | Acción |
  |---|---|---|---|
  | IR5-1 | La UI oculta el engrane para el grupo global, pero `GroupSettingsScreen` en sí no verifica el id global → accesible por deep link. | **Parcial.** No hay deep link externo a `group_settings/{id}` (no se declara `navDeepLink`), y el borrado/renombrado ya es no-op triple (CF + reglas + `canManageGroup` del VM). Pero nezaboost (creador del grupo global) *podría* abrir la pantalla y ver botones que no hacen nada. | **Adoptado** — el `LaunchedEffect` de la pantalla ahora también regresa si `group.id == GlobalGroupConstants.GROUP_ID`. |
  | IR5-2 | `LaunchedEffect(groupListState, group)` con `group` (val, no State) se cancela/reinicia en cada emisión por instancias nuevas. | **Incorrecto.** `Group`/`GroupListUiState.Success` son `data class`; `LaunchedEffect` compara keys con `==` estructural, no `===`. Una emisión con datos idénticos **no** reinicia el efecto. Y aunque se reejecute, el guard es idempotente (`navigate` con `launchSingleTop`). | No se cambia. |
  | IR5-3 | El `padding(20.dp)` del `Row` de la `Card` crea zona muerta alrededor del `IconButton` que dispara el `onClick` de la card. | **No es defecto.** El `IconButton` consume el gesto (no propaga al `clickable` de la `Card`). El margen de 20dp es superficie de la card por diseño — mismo comportamiento que el `Box` de copiar código que ya lleva en producción. | No se cambia. |
  | IR5-4a | `HistoryScreen` no tiene el `LaunchedEffect` de auto-salida — misma clase de "pantalla fantasma" que CR-1a. | **Válido.** Se llega a History desde dentro de la sesión de grupo; un miembro podría estar ahí cuando el admin borra. | **Adoptado** (más allá del alcance CR-1a original del plan) — mismo guard añadido al bloque `Screen.History` de `NavGraph`. |
  | IR5-4b | Al borrarse el grupo desde otro lado, `GroupSettingsScreen` hace `popBackStack()` → cae en `GroupSessionScreen` (roto) → segundo salto a Groups. | **Válido.** | **Adoptado** — el `LaunchedEffect` de la pantalla se simplificó: se eliminó `deleteRequested`; ahora `group == null` (borrado propio o externo) → `onExitToGroups()` en un solo salto; `!isAdmin`/grupo global con grupo presente → `onNavigateBack()`. |

  `./gradlew assembleDebug test` verde tras aplicar IR5-1/4a/4b.
- Paso 6 — pendiente (strings ya adelantadas en Pasos 3-5; falta el barrido final + cross-review completo).

---

## Verificación end-to-end (tras autorización del usuario para prueba manual — Rule 7)

1. Como **admin** de un grupo no global: abrir la sesión del grupo → aparece el engrane en
   `GroupHeaderBar`; en `GroupsScreen` la tarjeta muestra el ícono de settings. Como **no-admin**:
   ninguno de los dos aparece.
2. Engrane → `GroupSettingsScreen`. Renombrar → el nombre se actualiza en vivo en header y lista.
   Cambiar imagen → mismo comportamiento que hoy.
3. "Eliminar grupo" → diálogo de advertencia → confirmar. El admin aterriza en `GroupsScreen` (no en
   la sesión rota), el grupo desaparece de la lista de **todos** los miembros (listener en vivo), y
   `GroupsScreen` muestra el snackbar "Grupo … eliminado".
3b. Con un **segundo usuario** dentro de `GroupSessionScreen` de ese grupo en el momento del borrado:
   su pantalla se cierra sola de vuelta a `GroupsScreen` (CR-1a), sin quedarse en un header/tabs vacíos.
4. En consola Firebase / emulador: `groups/{id}`, `groups/{id}/weeks/**`, `groups/{id}/board/**`,
   `standings/{id}/members/**` y `group_photos/{id}` quedan borrados.
5. Intentar `deleteGroup` sobre el grupo global o desde un no-creador → `HttpsError permission-denied`.
6. `./gradlew test` y `./gradlew assembleDebug` verdes; `cd functions && npm run build` verde.
