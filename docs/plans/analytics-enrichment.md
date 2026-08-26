# Enriquecimiento y Cobertura de Analytics — Plan

> Especificación autoritativa y autocontenida — no asume contexto adicional fuera de lo que
> aquí se describe. Cada cambio referencia el archivo exacto, la función/línea aproximada
> (agosto 2026, puede haber corrido levemente), y la firma exacta propuesta.

## 1. Contexto y problema

El dueño de la app revisa Firebase Analytics y no puede interpretarlo:
- 475 eventos `screen_view` (automáticos de GA4, generados por Compose de un solo Activity) sin forma de saber qué pantalla.
- 231 eventos `pick_submitted` sin forma de saber qué usuario los generó.
- Grupos creados sin ver su nombre ni quién los administra.
- Eventos `favorite_team_set` sin forma de saber qué equipo es más popular.

Se requiere (1) enriquecer los 16 eventos que ya existen con contexto no-PII, y (2) instrumentar ~14 acciones de usuario que hoy no generan ningún evento.

## 2. Infraestructura existente (referencia, no re-descubrir)

- **`app/src/main/java/com/softeen/nflocospicks/analytics/AppLogger.kt`** — `@Singleton class AppLogger @Inject constructor(private val firebaseAnalytics: FirebaseAnalytics)`, un único método `fun logEvent(event: AppEvent)` que hace `Timber.i("[Analytics] ${event.name} params=${event.params}")` y luego `firebaseAnalytics.logEvent(event.name, event.params.toBundle())`. `Map<String,Any>.toBundle()` (privado, mismo archivo) solo soporta `String/Int/Long/Double/Boolean` — cualquier otro tipo cae a `.toString()`.
- **`app/src/main/java/com/softeen/nflocospicks/analytics/AppEvent.kt`** — `sealed class AppEvent(val name: String, val params: Map<String, Any> = emptyMap())`, una `data class`/`object` por evento, nombres en snake_case. Único lugar donde se agregan/modifican eventos.
- **DI**: `FirebaseAnalytics` se provee como singleton Hilt en `di/FirebaseModule.kt`. `AppLogger` tiene `@Inject constructor` — no necesita módulo Hilt explícito. Se inyecta directo en constructores de ViewModel — **nunca** en Composables ni Repositories.
- **`AppEvent.ScreenViewed(screenName)`** ya existe pero es código muerto — nadie lo llama. `AppEvent.ErrorOccurred` y `AppEvent.ApiLatency` también son código muerto — fuera de alcance de este plan, no tocar.
- **Inventario actual de 16 eventos**: `sign_in{method}`, `sign_up{method}`, `sign_out{}`, `account_deleted{}`, `group_created{group_id}`, `group_joined{group_id}`, `group_opened{group_id}`, `scoring_completed{group_id,scored_count}`, `pick_submitted{group_id,week_id,game_id,team_abbr}`, `leaderboard_viewed{group_id}`, `pick_history_viewed{group_id}`, `favorite_team_set{team_abbr}`, `language_changed{language_tag}`, `board_message_sent{group_id,message_type}`, `board_message_deleted{group_id}`, `board_announcement_toggled{group_id,is_announcement}`. Ninguno lleva `user_id`, nombre de grupo, ni nombre de equipo legible.

### Navegación (relevante para tracking de pantallas)

- `presentation/navigation/Screen.kt` — rutas top-level: `login`, `groups`, `create_group`, `join_group`, `picks/{groupId}` (**registrada pero jamás navegada — código muerto, confirmado por grep**), `leaderboard/{groupId}` (**ídem, código muerto**), `history/{groupId}` (sí se navega, vía literal `"history/$groupId"`), `settings`, `account`, `change_password`, `team_selection`, `user_management`, `group_session/{groupId}`.
- `presentation/navigation/NavGraph.kt` — `NavHost` top-level, `startDestination = Screen.Login.route`. `TrackScreenView` se agrega como composable hermano después del bloque `NavHost(...) { ... }` (cierra junto con `CompositionLocalProvider`/`NFLocosPickTheme`) y antes del `LaunchedEffect(authState)` existente.
- `presentation/navigation/GroupSessionScreen.kt` — `NavHost` anidado propio, `startDestination = "picks_content/$groupId"`, con 3 rutas de bottom-nav definidas en `sealed class BottomNavItem`: `BottomNavItem.Picks` (`"picks_content/{groupId}"`), `BottomNavItem.Leaderboard` (`"leaderboard_content/{groupId}"`), `BottomNavItem.Board` (`"board_content/{groupId}"`). `TrackScreenView` se agrega justo después de `val navController = rememberNavController()`.

## 3. Decisión de producto confirmada (no se re-discute)

Los términos de Firebase Analytics/GA4 prohíben enviar PII (nombres, emails) como parámetros de evento o propiedades de usuario — el riesgo es la suspensión de la propiedad de Analytics. Se usa la función nativa de **User-ID de Firebase** (`firebaseAnalytics.setUserId(uid)`) — el `uid` de Firebase Auth **no** se considera PII por la política de Google — en vez de pasar `display_name`/`email` en cualquier evento. El dueño cruza el `user_id` visible en el dashboard contra la colección `users` de Firestore (o vía BigQuery Export más adelante) para resolverlo a un nombre real. Ninguna resolución de nombre ocurre en el cliente ni en ningún evento.

Reglas derivadas, aplicables a **todo** el trabajo de este plan:
- Ningún evento lleva `display_name` ni `email` como parámetro, nunca.
- `group.name` y el nombre de equipo (apodo, ej. "Chiefs") sí son seguros de loguear directo — son contenido de la app, no PII.
- `is_group_admin: Boolean` (derivado de `Group.createdBy == currentUserId`) es seguro.
- Un `uid` ajeno usado como identificador crudo (ej. `target_user_id` en un cambio de rol) es seguro — no es un nombre.

## 4. Cambios de infraestructura

### 4.1 `AppLogger.setUserId`

**Archivo:** `analytics/AppLogger.kt`. Agregar un método nuevo al lado de `logEvent`:

```kotlin
fun setUserId(uid: String?) {
    Timber.i("[Analytics] setUserId=$uid")
    firebaseAnalytics.setUserId(uid)
}
```

Mismo patrón que `logEvent`: Timber primero, luego la llamada real a Firebase. No requiere cambios en `di/FirebaseModule.kt`.

### 4.2 Punto único de disparo — `AuthViewModel.watchRole`

**Archivo:** `presentation/auth/AuthViewModel.kt`

`private fun watchRole(uid: String)` es llamado desde **todos** los caminos que llegan a `AuthUiState.Authenticated`: restauración síncrona en `init`, restauración reactiva vía `currentUserFlow.collect`, `signIn()` (Google), `signInWithEmail()`, `signUpWithEmail()`, la rama `PhoneVerificationEvent.AutoVerified` de `startPhoneVerification()`, y `verifyPhoneCode()`.

**Cambio:** agregar `logger.setUserId(uid)` como primera línea del cuerpo de `watchRole(uid: String)`, antes de `roleWatcherJob?.cancel()`. Cubre los 7 puntos de entrada con una sola edición.

**Limpieza al cerrar sesión / borrar cuenta:**
- `signOut()` — agregar `logger.setUserId(null)` inmediatamente después de `logger.logEvent(AppEvent.SignOut)`.
- `deleteAccount()` `.onSuccess` — agregar `logger.setUserId(null)` inmediatamente después de `logger.logEvent(AppEvent.AccountDeleted)`.

Con este cambio, **ningún evento nuevo o enriquecido en este plan necesita jamás un parámetro `uid`/`user_id`** — el User-ID de Firebase ya identifica la sesión completa.

### 4.3 Nuevas dependencias inyectadas por ViewModel

| ViewModel | Archivo | Dependencia nueva | Constructor actual (verificado) |
|---|---|---|---|
| `UserManagementViewModel` | `presentation/usermanagement/UserManagementViewModel.kt` | `private val logger: AppLogger` | `@Inject constructor(private val userRepository: UserRepository)` |
| `AccountViewModel` | `presentation/account/AccountViewModel.kt` | `private val logger: AppLogger` | `@Inject constructor(userRepository, updateUserProfileUseCase, uploadProfilePhotoUseCase)` |
| `ChangePasswordViewModel` | `presentation/account/ChangePasswordViewModel.kt` | `private val logger: AppLogger` | `@Inject constructor(private val userRepository: UserRepository)` |
| `LeaderboardViewModel` | `presentation/leaderboard/LeaderboardViewModel.kt` | `private val groupRepository: GroupRepository` | ya tiene `logger: AppLogger` inyectado; agregar `groupRepository` |
| `HistoryViewModel` | `presentation/history/HistoryViewModel.kt` | `private val groupRepository: GroupRepository` | ya tiene `logger: AppLogger` inyectado; agregar `groupRepository` |

**Nota importante:** `SettingsViewModel` (`presentation/settings/SettingsViewModel.kt`) **ya tiene `AppLogger` inyectado**. Gracias al cambio de §4.2 (User-ID de sesión), **no hace falta inyectarle `UserRepository`** — los nuevos eventos `font_scale_changed`/`icon_scale_changed` (§7.7-7.8) no necesitan un `uid` como parámetro. **No modificar el constructor de `SettingsViewModel`.**

`GroupViewModel`, `PickViewModel`, `BoardViewModel` ya tienen tanto `AppLogger` como (donde aplica) `GroupRepository`/`UserRepository` — no requieren cambios de constructor.

No se requieren cambios en ningún módulo Hilt (`di/*.kt`) — todas estas son inyecciones de constructor puras, mismo mecanismo que ya usan `GroupViewModel`/`BoardViewModel`/`PickViewModel` hoy.

## 5. Tracking de pantallas (screen views)

### 5.1 Archivos nuevos

**`presentation/navigation/ScreenTrackingViewModel.kt`** (nuevo):
```kotlin
@HiltViewModel
class ScreenTrackingViewModel @Inject constructor(
    private val logger: AppLogger
) : ViewModel() {
    fun trackScreen(screenName: String) {
        logger.logEvent(AppEvent.ScreenViewed(screenName))
    }
}
```
Existe únicamente porque `AppLogger` no puede inyectarse en un Composable — se necesita este ViewModel mínimo como puente.

**`presentation/navigation/ScreenNames.kt`** (nuevo) — función **no** `@Composable` (a diferencia de `SeasonWeek.tabLabel()` en `WeekTabLabels.kt`, que sí es `@Composable` por usar `stringResource` y por eso no sirve para este propósito):
```kotlin
fun routeToScreenName(route: String?): String? = when (route) {
    Screen.Login.route            -> "login"
    Screen.Groups.route           -> "groups"
    Screen.CreateGroup.route      -> "create_group"
    Screen.JoinGroup.route        -> "join_group"
    Screen.Settings.route         -> "settings"
    Screen.Account.route          -> "account"
    Screen.ChangePassword.route   -> "change_password"
    Screen.TeamSelection.route    -> "team_selection"
    Screen.UserManagement.route   -> "user_management"
    Screen.History.route          -> "pick_history"
    Screen.GroupSession.route     -> "group_session"
    BottomNavItem.Picks.route       -> "picks"
    BottomNavItem.Leaderboard.route -> "leaderboard"
    BottomNavItem.Board.route       -> "board"
    null                           -> null
    else                           -> route  // fallback: nunca perder un route no mapeado
}
```
`Screen.Picks.route` (`"picks/{groupId}"`) y `Screen.Leaderboard.route` (`"leaderboard/{groupId}"`) **no** se incluyen en el mapeo — son rutas registradas pero jamás navegadas (código muerto preexistente, fuera de alcance arreglar aquí). Si algún día se navegaran, caerían en el `else -> route`.

**`presentation/navigation/TrackScreenView.kt`** (nuevo):
```kotlin
@Composable
fun TrackScreenView(navController: NavHostController) {
    val viewModel: ScreenTrackingViewModel = hiltViewModel()
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(route) {
        routeToScreenName(route)?.let { viewModel.trackScreen(it) }
    }
}
```
**Punto crítico:** la key de `LaunchedEffect` es `route` (el patrón de ruta estable), **no** el `NavBackStackEntry` completo — este último es una instancia nueva en cada restauración de estado, lo que dispararía el efecto de nuevo sin haber cambiado de pantalla. Usar `route` como key garantiza cero duplicados en recomposición.

### 5.2 Wiring (2 sitios)

- **`presentation/navigation/NavGraph.kt`** — agregar `TrackScreenView(navController)` como composable hermano dentro del bloque `CompositionLocalProvider(LocalAppColors provides appColors) { NFLocosPickTheme(...) { ... } }`, inmediatamente después de que cierra el `NavHost(...) { ... }`, antes del `LaunchedEffect(authState)` existente. Al estar fuera de cualquier bloque `composable{}`, `hiltViewModel()` dentro de `TrackScreenView` resuelve al `ViewModelStoreOwner` de la Activity — mismo scope que `authViewModel`/`settingsViewModel`.
- **`presentation/navigation/GroupSessionScreen.kt`** — agregar `TrackScreenView(navController)` inmediatamente después de `val navController = rememberNavController()`, antes del `Scaffold`. Como `GroupSessionScreen` corre dentro del `composable(Screen.GroupSession.route) { ... }` de `NavGraph.kt`, aquí `hiltViewModel()` resuelve al `NavBackStackEntry` de `group_session/{groupId}` — una instancia nueva de `ScreenTrackingViewModel` por sesión de grupo, sin estado propio.

Con este mecanismo, **los cambios de tab del bottom-nav de `GroupSessionScreen` (Picks/Leaderboard/Board) quedan cubiertos automáticamente** — no se necesita ningún evento nuevo para ellos.

## 6. Enriquecimiento de los 16 eventos existentes

### 6.1-6.4 `SignIn`, `SignUp`, `SignOut`, `AccountDeleted` — sin cambios de parámetros (ver §4.2 para `setUserId`)

### 6.5 `GroupCreated`
- **Firma nueva:** `data class GroupCreated(val groupId: String, val groupName: String) : AppEvent("group_created", mapOf("group_id" to groupId, "group_name" to groupName))`
- **Archivo/función:** `presentation/groups/GroupViewModel.kt`, `createGroup(name: String)`. `name` ya está en el parámetro de la función — pasar directo: `logger.logEvent(AppEvent.GroupCreated(group.id, name))`.

### 6.6 `GroupJoined`
- **Firma nueva:** `data class GroupJoined(val groupId: String, val groupName: String) : AppEvent("group_joined", mapOf("group_id" to groupId, "group_name" to groupName))`
- **Archivo/función:** `GroupViewModel.kt`, `joinGroup(inviteCode: String)`. El `Group` completo ya viene de `joinGroupUseCase(inviteCode, userId)` — usar `group.name`.

### 6.7 `GroupOpened`
- **Firma nueva:** `data class GroupOpened(val groupId: String, val groupName: String?, val source: String) : AppEvent("group_opened", buildMap { put("group_id", groupId); groupName?.let { put("group_name", it) }; put("source", source) })`
- **Archivo/función:** `GroupViewModel.kt`, `onGroupClicked(groupId: String)` — **cambiar firma a `onGroupClicked(groupId: String, source: String)`**. Resolver el nombre desde la lista ya cargada en memoria (puede ser `null` si la lista aún no cargó — aceptable).
- **Cambios en cascada (verificado en `GroupsScreen.kt`):**
  - `GroupsScreen.kt` línea ~118: `onGroupClicked = { viewModel.onGroupClicked(it) }` → `onGroupClicked = { id, source -> viewModel.onGroupClicked(id, source) }`.
  - `GroupsScreenContent` — cambiar el parámetro `onGroupClicked: (String) -> Unit` (línea ~136) a `onGroupClicked: (String, String) -> Unit`.
  - Línea ~249 (`GroupCard`): `onClick = { onGroupClicked(group.id) }` → `onClick = { onGroupClicked(group.id, "group_list") }`.
  - Línea ~269 (`GlobalGroupFeedPanel`): `onClick = { onGroupClicked(GlobalGroupConstants.GROUP_ID) }` → `onClick = { onGroupClicked(GlobalGroupConstants.GROUP_ID, "global_feed_panel") }`.
  - Dos previews al final del archivo (`onGroupClicked = {}`) necesitan actualizar su firma lambda a `{ _, _ -> }`.
  - Esto también cubre "clic en el panel de feed global" del inventario de acciones sin tracking — **no genera un evento nuevo**, solo diferencia `source`.

### 6.8 `ScoringCompleted`
- **Firma nueva:** `data class ScoringCompleted(val groupId: String, val scoredCount: Int, val source: String) : AppEvent("scoring_completed", mapOf("group_id" to groupId, "scored_count" to scoredCount, "source" to source))`
- **Archivo/función 1:** `GroupViewModel.kt`, `onScoreClicked(groupId: String)` — `logger.logEvent(AppEvent.ScoringCompleted(groupId, count, source = "group_card"))`.
- **Archivo/función 2 (nuevo call site):** `presentation/picks/PickViewModel.kt`, `triggerSync()` (verificado, línea 310) — hoy descarta el resultado de `scoreWeekPicksUseCase(groupId)`. Capturarlo: `val count = scoreWeekPicksUseCase(groupId)` y agregar `logger.logEvent(AppEvent.ScoringCompleted(groupId, count, source = "pick_manual_sync"))` antes de recargar la semana.

### 6.9 `PickSubmitted`
- **Firma nueva:** `data class PickSubmitted(val groupId: String, val weekId: String, val gameId: String, val teamAbbr: String, val teamName: String?, val seasonType: String, val weekNumber: Int) : AppEvent("pick_submitted", buildMap { put("group_id", groupId); put("week_id", weekId); put("game_id", gameId); put("team_abbr", teamAbbr); teamName?.let { put("team_name", it) }; put("season_type", seasonType); put("week_number", weekNumber) })`
- **Archivo/función:** `PickViewModel.kt`, `submitPick(gameId, teamAbbr, kickoffTime, status)`. `targetIndex` ya se captura en el cuerpo de la función. Agregar `val week = NflSeasonCalendar.WEEKS[targetIndex]` justo después, y pasar `teamName = nflTeamNameByAbbr[teamAbbr]`, `seasonType = week.seasonType.name`, `weekNumber = week.weekNumber`. `seasonType` se loguea como el nombre crudo del enum (`"PRESEASON"`/`"REGULAR"`/`"POSTSEASON"`) — **no** usar `SeasonWeek.tabLabel()` porque es `@Composable` y no puede llamarse desde un ViewModel.

### 6.10 `LeaderboardViewed`
- **Firma nueva:** `data class LeaderboardViewed(val groupId: String, val groupName: String?) : AppEvent("leaderboard_viewed", buildMap { put("group_id", groupId); groupName?.let { put("group_name", it) } })`
- **Archivo/función:** `LeaderboardViewModel.kt`, bloque `init`. Mover el `logger.logEvent(...)` a dentro de una corrutina que primero resuelve el nombre: `viewModelScope.launch { val groupName = runCatching { groupRepository.getGroupById(groupId).name }.getOrNull(); logger.logEvent(AppEvent.LeaderboardViewed(groupId, groupName)) }`. Mismo patrón defensivo (`runCatching` + `.getOrNull()`, best-effort) que ya usa `BoardViewModel.loadInitialData()` para `isGroupAdmin`. Requiere `groupRepository` de §4.3.

### 6.11 `PickHistoryViewed`
- **Firma nueva:** `data class PickHistoryViewed(val groupId: String, val groupName: String?) : AppEvent("pick_history_viewed", buildMap { put("group_id", groupId); groupName?.let { put("group_name", it) } })`
- **Archivo/función:** `HistoryViewModel.kt`, `loadHistory()`. Antes de loguear, resolver `val groupName = runCatching { groupRepository.getGroupById(groupId).name }.getOrNull()`. Requiere `groupRepository` de §4.3.

### 6.12 `FavoriteTeamSet`
- **Firma nueva:** `data class FavoriteTeamSet(val teamAbbr: String, val teamName: String?) : AppEvent("favorite_team_set", buildMap { put("team_abbr", teamAbbr); teamName?.let { put("team_name", it) } })`
- **Archivo/función:** `SettingsViewModel.kt`, `setFavoriteTeam(abbr: String?)` — `logger.logEvent(AppEvent.FavoriteTeamSet(abbr, nflTeamNameByAbbr[abbr]))`.

### 6.13 `LanguageChanged` — sin cambios

### 6.14 `BoardMessageSent`
- **Firma nueva:** `data class BoardMessageSent(val groupId: String, val messageType: String, val action: String, val isGroupAdmin: Boolean, val groupName: String?) : AppEvent("board_message_sent", buildMap { put("group_id", groupId); put("message_type", messageType); put("action", action); put("is_group_admin", isGroupAdmin); groupName?.let { put("group_name", it) } })`
  - `action`: `"sent"` para mensaje nuevo, `"edited"` para edición — este mismo cambio de firma cubre el evento de edición del board (§7.9), no hace falta un evento separado.
- **Archivo:** `presentation/board/BoardViewModel.kt`.
  - **Prerequisito verificado:** `BoardUiState` (`presentation/board/BoardUiState.kt`) hoy tiene `messages, isLoading, error, snackbarMessage, currentUserId, isGroupAdmin, inputText, editingMessage` — agregar campo nuevo `groupName: String? = null`.
  - `loadInitialData()` — donde ya se resuelve `isGroupAdmin` vía `groupRepository.getGroupById(groupId).createdBy == currentUserId`, capturar también `groupName` en la misma llamada y guardarlo en el `_uiState.update { ... }`.
  - `sendOrSaveMessage()`:
    - Rama de mensaje nuevo — `logger.logEvent(AppEvent.BoardMessageSent(groupId, "chat", action = "sent", isGroupAdmin = _uiState.value.isGroupAdmin, groupName = _uiState.value.groupName))`.
    - Rama de edición — **hoy no loguea nada**. Agregar al final de esa rama: `logger.logEvent(AppEvent.BoardMessageSent(groupId, "chat", action = "edited", isGroupAdmin = _uiState.value.isGroupAdmin, groupName = _uiState.value.groupName))`.

### 6.15 `BoardMessageDeleted`
- **Firma nueva:** `data class BoardMessageDeleted(val groupId: String, val isGroupAdmin: Boolean, val isOwnMessage: Boolean, val groupName: String?) : AppEvent("board_message_deleted", buildMap { put("group_id", groupId); put("is_group_admin", isGroupAdmin); put("is_own_message", isOwnMessage); groupName?.let { put("group_name", it) } })`
- **Archivo/función:** `BoardViewModel.kt`, `deleteMessage(message: BoardMessage)`:
  ```kotlin
  val state = _uiState.value
  logger.logEvent(AppEvent.BoardMessageDeleted(
      groupId,
      isGroupAdmin = state.isGroupAdmin,
      isOwnMessage = message.senderId == state.currentUserId,
      groupName    = state.groupName
  ))
  ```

### 6.16 `BoardAnnouncementToggled`
- **Firma nueva:** `data class BoardAnnouncementToggled(val groupId: String, val isAnnouncement: Boolean, val groupName: String?) : AppEvent("board_announcement_toggled", buildMap { put("group_id", groupId); put("is_announcement", isAnnouncement); groupName?.let { put("group_name", it) } })`
  - **No** se agrega `is_group_admin` — `toggleAnnouncement()` ya está gateado por `if (!_uiState.value.isGroupAdmin) return`, siempre sería `true`, sería redundante.
- **Archivo/función:** `BoardViewModel.kt`, `toggleAnnouncement(message: BoardMessage)`.

### 6.17 Helper nuevo requerido — `nflTeamNameByAbbr`

**Archivo:** `presentation/common/NflTeams.kt`. Agregar, después de la lista `nflTeams`:
```kotlin
val nflTeamNameByAbbr: Map<String, String> = nflTeams.associate { it.abbr to it.name }
```
Usado por `PickSubmitted` (§6.9) y `FavoriteTeamSet` (§6.12). No es PII — son apodos de equipos, contenido público de la app.

## 7. Eventos nuevos para acciones sin tracking

### 7.1 `screen_viewed` — activación del caso muerto
Ver §5 completo. No requiere cambios en `AppEvent.kt`.

### 7.2 Sync manual desde `PickScreen` → reutiliza `ScoringCompleted`
Ver §6.8, segundo call site. **No es un evento nuevo.**

### 7.3 `week_tab_selected` (nuevo)
- **Firma:** `data class WeekTabSelected(val groupId: String, val seasonType: String, val weekNumber: Int) : AppEvent("week_tab_selected", mapOf("group_id" to groupId, "season_type" to seasonType, "week_number" to weekNumber))`
- **Archivo/función:** `PickViewModel.kt`, `onWeekSelected(index: Int)` (verificado, línea 125). Después de la guarda de retorno temprano, agregar `val week = NflSeasonCalendar.WEEKS[index]` y `logger.logEvent(AppEvent.WeekTabSelected(groupId, week.seasonType.name, week.weekNumber))`.

### 7.4 `pick_refresh` (nuevo, solo manual) + `pick_auto_refresh_failed` (nuevo, solo fallas)
- **Firmas:**
  - `data class PickRefresh(val groupId: String) : AppEvent("pick_refresh", mapOf("group_id" to groupId, "trigger" to "manual"))`
  - `data class PickAutoRefreshFailed(val groupId: String) : AppEvent("pick_auto_refresh_failed", mapOf("group_id" to groupId))`
- **Archivo/función:** `PickViewModel.kt` (funciones verificadas: `refresh()` línea 141, `loadWeek(index, showLoading)` línea 147, `startAutoRefresh()` línea 207, `triggerSync()` línea 310).
  - `refresh()` — cambiar firma a `fun refresh(manual: Boolean = true)`. Antes de llamar a `loadWeek(...)`, agregar `if (manual) logger.logEvent(AppEvent.PickRefresh(groupId))`.
  - `startAutoRefresh()` — cambiar la llamada interna de `refresh()` a `refresh(manual = false)`.
  - `loadWeek(index: Int, showLoading: Boolean)` — cambiar firma a `loadWeek(index: Int, showLoading: Boolean, manual: Boolean = true)`. En la rama `catch` para `!showLoading` (hoy solo hace `Timber.w`), agregar `if (!manual) logger.logEvent(AppEvent.PickAutoRefreshFailed(groupId))`. Propagar `manual` desde `refresh()`.
  - Los demás call sites de `loadWeek(...)` (`resolveCurrentWeekAndLoad`, `loadData`, `onWeekSelected`, `triggerSync`) usan `showLoading = true`, donde `manual` no tiene efecto — dejar su valor default `true`.
- **Justificación:** el loop de auto-refresh corre cada 5 minutos indefinidamente — loguear cada ejecución exitosa saturaría el dashboard sin valor de producto. Solo interesa el refresh manual del usuario y las fallas silenciosas del automático.

### 7.5 `leaderboard_tab_selected` (nuevo)
- **Firma:** `data class LeaderboardTabSelected(val groupId: String, val seasonType: String) : AppEvent("leaderboard_tab_selected", mapOf("group_id" to groupId, "season_type" to seasonType))`
- **Archivo/función:** `LeaderboardViewModel.kt`, `onTabSelected(seasonType: SeasonType)` — loguear al inicio del cuerpo.

### 7.6 `history_week_toggled` (nuevo)
- **Firma:** `data class HistoryWeekToggled(val groupId: String, val weekId: String, val expanded: Boolean) : AppEvent("history_week_toggled", mapOf("group_id" to groupId, "week_id" to weekId, "expanded" to expanded))`
- **Archivo/función:** `HistoryViewModel.kt`, `toggleWeek(weekId: String)`. Calcular el nuevo estado (`expanded`) antes de aplicarlo y loguear ambos sentidos (expandir y colapsar) — acción explícita de tap, volumen bajo, no necesita reducción de ruido como el auto-refresh.

### 7.7 `font_scale_changed` (nuevo)
- **Firma:** `data class FontScaleChanged(val scale: String) : AppEvent("font_scale_changed", mapOf("scale" to scale))`
- **Archivo/función:** `SettingsViewModel.kt`, `setFontScale(key: String?)` — loguear solo si `key != null`.

### 7.8 `icon_scale_changed` (nuevo)
- **Firma:** `data class IconScaleChanged(val scale: String) : AppEvent("icon_scale_changed", mapOf("scale" to scale))`
- **Archivo/función:** `SettingsViewModel.kt`, `setIconScale(key: String?)` — mismo patrón.

### 7.9 Edición de mensaje del board
Ya cubierto en §6.14 — no es un evento nuevo, es `action="edited"` sobre `BoardMessageSent`.

### 7.10 `group_photo_uploaded` (nuevo)
- **Firma:** `data class GroupPhotoUploaded(val groupId: String) : AppEvent("group_photo_uploaded", mapOf("group_id" to groupId))`
- **Archivo/función:** `GroupViewModel.kt`, `uploadGroupPhoto(group, requesterUserId, uri)` `.onSuccess`.

### 7.11 `group_icon_set` (nuevo)
- **Firma:** `data class GroupIconSet(val groupId: String, val iconId: String) : AppEvent("group_icon_set", mapOf("group_id" to groupId, "icon_id" to iconId))`
- **Archivo/función:** `GroupViewModel.kt`, `setGroupIcon(group, requesterUserId, iconId)` `.onSuccess`.

### 7.12 `user_role_changed` (nuevo)
- **Firma:** `data class UserRoleChanged(val targetUserId: String, val newRole: String) : AppEvent("user_role_changed", mapOf("target_user_id" to targetUserId, "new_role" to newRole))` — `targetUserId` es un uid ajeno, permitido por §3 (no es un nombre).
- **Archivo/función:** `UserManagementViewModel.kt`, `setRole(uid: String, role: UserRole)` (constructor verificado: hoy solo `userRepository`). Requiere `logger: AppLogger` (§4.3).

### 7.13 `profile_saved` (nuevo)
- **Firma:** `object ProfileSaved : AppEvent("profile_saved")` — sin params, `username`/`displayName` serían PII.
- **Archivo/función:** `AccountViewModel.kt`, `saveProfile(uid, username, displayName)` `.onSuccess`. Requiere `logger: AppLogger` (§4.3).

### 7.14 `profile_photo_uploaded` (nuevo)
- **Firma:** `object ProfilePhotoUploaded : AppEvent("profile_photo_uploaded")`
- **Archivo/función:** `AccountViewModel.kt`, `uploadPhoto(uid, uri)` `.onSuccess`.

### 7.15 `account_email_link_sent` (nuevo)
- **Firma:** `object AccountEmailLinkSent : AppEvent("account_email_link_sent")` — nombre deliberadamente distinto de cualquier evento de login; es para *vincular* un email a una cuenta ya existente.
- **Archivo/función:** `AccountViewModel.kt`, `sendEmailLink(email)` `.onSuccess`.

### 7.16 `phone_link_verified` (nuevo)
- **Firma:** `object PhoneLinkVerified : AppEvent("phone_link_verified")`
- **Archivo/función:** `AccountViewModel.kt`, `verifyPhoneLinkCode(smsCode)` `.onSuccess`. **No** instrumentar `startPhoneLink()` — su rama `CodeSent` es un paso intermedio, no una acción completada.

### 7.17 `password_changed` (nuevo)
- **Firma:** `object PasswordChanged : AppEvent("password_changed")`
- **Archivo/función:** `ChangePasswordViewModel.kt`, `changePassword(currentPassword, newPassword)` `.onSuccess`. Requiere `logger: AppLogger` (§4.3).

### 7.18 `global_group_auto_joined` (nuevo)
- **Firma:** `object GlobalGroupAutoJoined : AppEvent("global_group_auto_joined")`
- **NO instrumentar `UserRepositoryImpl.ensureGlobalGroupMembership`** — violaría la regla de que `AppLogger` nunca se inyecta en Repositories.
- **Archivo/función real:** `AuthViewModel.kt`, en las mismas ramas donde ya se loguea `AppEvent.SignUp(...)` condicionado a `isNewUser` (verificado, 3 sitios): `signIn()` (Google, `if (result.isNewUser) ...`), rama `AutoVerified` de `startPhoneVerification()` (`if (event.result.isNewUser) ...`), `verifyPhoneCode()` (`if (result.isNewUser) ...`) — agregar `logger.logEvent(AppEvent.GlobalGroupAutoJoined)` junto a cada `SignUp` condicionado. `signUpWithEmail()` no tiene chequeo `isNewUser` (siempre es alta nueva, verificado) — agregar el evento incondicionalmente junto al `SignUp("email")` existente.
- **Advertencia de diseño (documentar con comentario en el código):** señal **inferida/optimista** — coincide con la misma condición que dispara el auto-join real, pero no confirma que `ensureGlobalGroupMembership` tuvo éxito (esa función atrapa sus propios errores en silencio).

### 7.19 Acciones que NO generan evento nuevo (ya cubiertas)
- Cambios de tab del bottom-nav de `GroupSessionScreen` → cubierto por `screen_viewed` (§5).
- Clic en el panel de feed global en `GroupsScreen` → cubierto por el parámetro `source` de `group_opened` (§6.7).

### 7.20 Explícitamente fuera de alcance
- Desactivación por inactividad (`functions/src/inactivity.ts`, PR-20) — Cloud Function pura server-side, sin llamada a Analytics/Measurement Protocol.

## 8. Inventario final de eventos (estado tras implementar este plan completo)

| # | `name` | Params completos (post-plan) | Estado |
|---|---|---|---|
| 1 | `sign_in` | `method` | sin cambios |
| 2 | `sign_up` | `method` | sin cambios |
| 3 | `sign_out` | — | sin cambios (dispara `setUserId(null)`) |
| 4 | `account_deleted` | — | sin cambios (dispara `setUserId(null)`) |
| 5 | `group_created` | `group_id, group_name` | modificado |
| 6 | `group_joined` | `group_id, group_name` | modificado |
| 7 | `group_opened` | `group_id, group_name?, source` | modificado |
| 8 | `scoring_completed` | `group_id, scored_count, source` | modificado |
| 9 | `pick_submitted` | `group_id, week_id, game_id, team_abbr, team_name?, season_type, week_number` | modificado |
| 10 | `leaderboard_viewed` | `group_id, group_name?` | modificado |
| 11 | `pick_history_viewed` | `group_id, group_name?` | modificado |
| 12 | `favorite_team_set` | `team_abbr, team_name?` | modificado |
| 13 | `language_changed` | `language_tag` | sin cambios |
| 14 | `board_message_sent` | `group_id, message_type, action, is_group_admin, group_name?` | modificado |
| 15 | `board_message_deleted` | `group_id, is_group_admin, is_own_message, group_name?` | modificado |
| 16 | `board_announcement_toggled` | `group_id, is_announcement, group_name?` | modificado |
| 17 | `screen_viewed` | `screen_name` | activado (existía muerto) |
| 18 | `week_tab_selected` | `group_id, season_type, week_number` | nuevo |
| 19 | `pick_refresh` | `group_id, trigger="manual"` | nuevo |
| 20 | `pick_auto_refresh_failed` | `group_id` | nuevo |
| 21 | `leaderboard_tab_selected` | `group_id, season_type` | nuevo |
| 22 | `history_week_toggled` | `group_id, week_id, expanded` | nuevo |
| 23 | `font_scale_changed` | `scale` | nuevo |
| 24 | `icon_scale_changed` | `scale` | nuevo |
| 25 | `group_photo_uploaded` | `group_id` | nuevo |
| 26 | `group_icon_set` | `group_id, icon_id` | nuevo |
| 27 | `user_role_changed` | `target_user_id, new_role` | nuevo |
| 28 | `profile_saved` | — | nuevo |
| 29 | `profile_photo_uploaded` | — | nuevo |
| 30 | `account_email_link_sent` | — | nuevo |
| 31 | `phone_link_verified` | — | nuevo |
| 32 | `password_changed` | — | nuevo |
| 33 | `global_group_auto_joined` | — | nuevo |

Sin cambios: `error_occurred`, `api_latency` (siguen muertos, fuera de alcance).

## 9. Lista consolidada de archivos a tocar

**Nuevos:**
- `presentation/navigation/ScreenTrackingViewModel.kt`
- `presentation/navigation/ScreenNames.kt`
- `presentation/navigation/TrackScreenView.kt`

**Modificados:**
- `analytics/AppEvent.kt` — todas las firmas nuevas/modificadas de §6, §7.
- `analytics/AppLogger.kt` — `setUserId(uid: String?)`.
- `presentation/auth/AuthViewModel.kt` — `watchRole` (setUserId), `signOut`/`deleteAccount` (setUserId null), 4 ramas de alta (GlobalGroupAutoJoined).
- `presentation/navigation/NavGraph.kt` — wiring de `TrackScreenView`.
- `presentation/navigation/GroupSessionScreen.kt` — wiring de `TrackScreenView`.
- `presentation/common/NflTeams.kt` — `nflTeamNameByAbbr`.
- `presentation/groups/GroupViewModel.kt` — `createGroup`, `joinGroup`, `onGroupClicked` (+firma), `onScoreClicked`, `uploadGroupPhoto`, `setGroupIcon`.
- `presentation/groups/GroupsScreen.kt` — `onGroupClicked` con `source` (2 call sites + firma de `GroupsScreenContent` + 2 previews).
- `presentation/picks/PickViewModel.kt` — `submitPick`, `onWeekSelected`, `refresh`, `startAutoRefresh`, `loadWeek` (+firma), `triggerSync`.
- `presentation/leaderboard/LeaderboardViewModel.kt` — constructor (+`GroupRepository`), `init`, `onTabSelected`.
- `presentation/history/HistoryViewModel.kt` — constructor (+`GroupRepository`), `loadHistory`, `toggleWeek`.
- `presentation/board/BoardUiState.kt` — nuevo campo `groupName: String? = null`.
- `presentation/board/BoardViewModel.kt` — `loadInitialData`, `sendOrSaveMessage`, `deleteMessage`, `toggleAnnouncement`.
- `presentation/settings/SettingsViewModel.kt` — `setFavoriteTeam`, `setFontScale`, `setIconScale`. **No** tocar el constructor.
- `presentation/usermanagement/UserManagementViewModel.kt` — constructor (+`AppLogger`), `setRole`.
- `presentation/account/AccountViewModel.kt` — constructor (+`AppLogger`), `saveProfile`, `uploadPhoto`, `sendEmailLink`, `verifyPhoneLinkCode`.
- `presentation/account/ChangePasswordViewModel.kt` — constructor (+`AppLogger`), `changePassword`.

**No tocar:** `di/FirebaseModule.kt`, cualquier otro módulo Hilt, `data/repository/UserRepositoryImpl.kt` (el comentario de diseño de §7.18 se documenta solo en `AuthViewModel.kt`).

## 10. Desglose de PRs

### PR-21 — Analytics Infrastructure, User-ID & Event Enrichment
**Branch:** `feature/21-analytics-infrastructure`

Incluye: §4 completo (infraestructura, User-ID, DI), §5 completo (tracking de pantallas), §6 completo (enriquecimiento de los 16 eventos existentes), §6.17 (helper `nflTeamNameByAbbr`). **No** incluye ningún evento nuevo de §7 — esos van en PR-22.

### PR-22 — Expanded Analytics Coverage for Untracked Actions
**Branch:** `feature/22-analytics-coverage`

Incluye: todo §7 (eventos 18-33 de la tabla de §8), las 3 inyecciones de `AppLogger` restantes (`UserManagementViewModel`, `AccountViewModel`, `ChangePasswordViewModel`).

### PR-23 — GA4 Console Custom Dimensions & Analytics Documentation
**Branch:** `feature/23-ga4-console-setup`

Sin cambios de código Kotlin. Pasos:
1. En Firebase Console → Analytics → Custom definitions, registrar como Custom Dimensions (event-scoped): `group_name`, `team_name`, `source`, `screen_name`, `season_type`, `week_number`, `is_group_admin`, `is_own_message`, `message_type`, `action`, `group_id` (~11, bajo el límite de 50). Omitir por ahora (quedan disponibles vía exportación cruda): `icon_id`, `target_user_id`, `new_role`, `language_tag`, `trigger`, `expanded`, `week_id`, `game_id`, `team_abbr`.
2. `user_id` no requiere registro — ya es utilizable nativamente en "User Explorer" en cuanto `setUserId` empiece a dispararse (PR-21).
3. Documentar en `CLAUDE.md` (sección "Key Constraints" → "Analytics") el inventario completo de la tabla de §8.
4. Recomendación para el dueño (no implementada en código): evaluar habilitar BigQuery Export si el límite de 50 dimensiones se vuelve limitante.

## 11. Verificación

Reglas 2, 4 y 5 de `CLAUDE.md` aplican a cada PR: `./gradlew assembleDebug` (+ `test` si hay lógica) antes de cada commit; nunca lanzar emulador/dispositivo sin autorización explícita previa.

### Tests unitarios (obligatorio, vía `./gradlew test`)
- Extender los tests existentes que ya usan `mockk<AppLogger>(relaxed = true)` (patrón confirmado en `BoardViewModelTest.kt`) con aserciones tipo:
  ```kotlin
  verify { logger.logEvent(match {
      it.name == "board_message_sent" &&
      it.params["action"] == "edited" &&
      it.params["is_group_admin"] == true
  }) }
  ```
- Crear archivos de test nuevos para los 3 ViewModels que ganan `AppLogger` por primera vez: `UserManagementViewModelTest.kt`, `AccountViewModelTest.kt`, `ChangePasswordViewModelTest.kt`.
- `LeaderboardViewModelTest.kt`/`HistoryViewModelTest.kt` necesitan mockear también `groupRepository: GroupRepository` con `coEvery { groupRepository.getGroupById(any()) } returns mockGroup`.

### Verificación manual (solo con autorización explícita del usuario)
- Logcat filtrado por el tag `[Analytics]` — `AppLogger.logEvent`/`setUserId` ya emiten `Timber.i` antes de llamar a Firebase.
- Alternativa: Firebase DebugView vía `adb shell setprop debug.firebase.analytics.app com.softeen.nflocospicks`. Recordar desactivarlo después con `adb shell setprop debug.firebase.analytics.app .none.` — el flag persiste entre reinicios de la app hasta que se limpia explícitamente.

### PR-23 no requiere verificación en código
Su entregable es 100% configuración manual de consola + documentación.
