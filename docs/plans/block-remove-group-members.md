# Plan — Admin bloquea o elimina miembros de un grupo (desde la pantalla de configuración)

> **Ubicación canónica (Rule 8):** este archivo. Copiado desde el borrador de plan-mode
> (`.claude/plans/`) el 2026-09-07. Esta es la versión de referencia; usar esta ruta en todos los
> comandos de cross-review y en la implementación.

---

## Context

Hoy un grupo no tiene forma de sacar a un miembro. La única "gestión" de grupo es
`GroupSettingsScreen` (renombrar / imagen / eliminar grupo), añadida en el trabajo ya mergeado de
`feature/group-configuration` (PR #48, ver `docs/plans/group-deletion-and-group-settings-screen.md`).
El admin de un grupo = su creador (`group.createdBy == currentUserId`); no existe rol por grupo.

Se quiere que **el administrador del grupo** pueda, desde `GroupSettingsScreen`:

- **Eliminar** a un miembro → sale del grupo; puede volver a unirse con el código de invitación.
- **Bloquear** a un miembro → sale del grupo **y** no puede volver a unirse con el código, hasta que
  el admin lo **Desbloquee**. Una sección "Bloqueados" en la misma pantalla lista a los bloqueados
  con acción "Desbloquear".
- Al sacar a un miembro (elimine o bloquee), su fila en la tabla de posiciones del grupo se
  **oculta** (`hidden: true` en `standings/{groupId}/members/{uid}`), reutilizando el patrón de
  desactivación por inactividad — el leaderboard ya filtra `hidden == true`
  (`FirebaseLeaderboardDataSource`). Es reversible: si la persona vuelve al grupo, el siguiente
  puntuado reescribe su standing (el `batch.set` de `scoring.ts:87` no es merge → limpia `hidden`) y
  su historial reaparece intacto (`weeklyBreakdown` se conserva). Ver "Limitación aceptada".

**Estado actual relevante:**

- `firestore.rules` `groups/{groupId}` `update` (líneas 68-85): **el creador ya puede reescribir
  `memberIds` libremente** — la rama `request.auth.uid == resource.data.createdBy` lo exime del
  chequeo "solo self-join". El comentario de la línea 62 lo anticipa: "el creador sí puede
  modificarlo libremente (ej. expulsar a alguien a futuro)". Un no-creador solo puede auto-agregarse
  (≤1 uid) y nunca quitar a nadie.
- `standings/{groupId}/members/{uid}` es `allow write: if false` — **el cliente no puede ocultar ni
  borrar el standing**; eso obliga a una Cloud Function (Admin SDK), igual que `deleteGroup`.
- `domain/model/Group.kt` **no** tiene `blockedIds`. No hay lista de bloqueo en ninguna capa.
- **No hay UI de lista de miembros** en ningún lado (`group.memberIds` solo se usa para el conteo en
  `GroupsScreen`). No hay `UserRepository.getUsersByIds(...)`; `getAllUsers(): Flow<List<User>>` sí
  existe (lo usa `UserManagementViewModel`).
- El grupo global (`GlobalGroupConstants.GROUP_ID = "global_nflocos_de_corazon"`) y el grupo mock
  (`MockDataProvider.MOCK_GROUP_ID`) ya quedan fuera vía `GroupViewModel.canManageGroup(...)`, y
  `GroupSettingsScreen` ni siquiera se abre para el grupo global.

**Enfoque:** una Cloud Function callable `removeGroupMember({groupId, targetUid, block})` + otra
`unblockGroupMember({groupId, targetUid})`, calcadas de `deleteGroup` / `groupDeletion.ts`. Un campo
nuevo `Group.blockedIds`. Un cambio mínimo en `firestore.rules` (negar self-join si estás en
`blockedIds`; proteger `blockedIds` como `name`/`photoUrl`). Secciones "Miembros" y "Bloqueados"
dentro de `GroupSettingsScreen`, gestionadas por el `GroupViewModel` compartido, con el guard
`canManageGroup` + guard extra "no puedes quitarte a ti mismo". Sin `firestore.rules` como única
defensa: guard en VM + validación en la CF + reglas, en ese orden de profundidad.

> **Nota Rule 10:** Codex quedó deshabilitado por completo del flujo de cross-review (decisión del
> usuario, 2026-09-07). Antigravity (`agy.exe`) es el único reviewer, para plan e implementación.

---

## Cross-Review (Rule 10)

- **Review de plan: HECHO (2026-09-07).** AGY corrido — único reviewer. 5 hallazgos sintetizados
  contra el código — ver "Cross-Review Log" al final. Hallazgos #1 y #2 se incorporaron al plan
  (endurecer regla de `picks`; carve-out de `standings` + des-ocultar en `joinGroup`); #3 se
  documenta y no se aplica; #4 y #5 ya están resueltos en el código actual.
- **Ubicación:** este archivo ya vive en `docs/plans/` (Rule 8) — usar esta ruta en todo comando.
- **Review de implementación (Rule 10.5):** tras cada paso, contra el diff acumulado —
  `agy.exe --mode plan --dangerously-skip-permissions -p "Review the current uncommitted git diff for correctness, security, and design issues" --model gemini-3.1-pro-high --effort high`.
  Documentar en el log.

---

## Paso 1 — Cloud Functions `removeGroupMember` / `unblockGroupMember` + `firestore.rules`

**Estado actual:** no hay función para quitar miembros; el creador podría hacer `arrayRemove` en
`memberIds` desde el cliente pero no puede tocar `standings` ni existe `blockedIds`.
**Estado objetivo:** dos callables (Admin SDK) hacen la mutación completa y atómica; las reglas
niegan el re-ingreso de un bloqueado y protegen `blockedIds`.

### `functions/src/groupMembers.ts` (nuevo)

Imports: `getFirestore`, `FieldValue`, `logger`, `HttpsError`, `GLOBAL_GROUP_ID` de `./globalGroup`.

- `export async function removeGroupMember(groupId: string, requesterUid: string, targetUid: string, block: boolean): Promise<void>`
  - `groupId === GLOBAL_GROUP_ID` → `HttpsError("permission-denied", "En el grupo global no se administran miembros.")`.
  - `groups/{groupId}.get()`; `!exists` → `HttpsError("not-found", "El grupo no existe.")`.
  - `data.createdBy !== requesterUid` → `HttpsError("permission-denied", "Solo el administrador del grupo puede hacer esto.")`.
  - `targetUid === data.createdBy` → `HttpsError("invalid-argument", "El administrador no puede quitarse a sí mismo.")`.
  - Ocultar el standing (mismo patrón que `inactivity.ts:setStandingsHidden`): `standings/{groupId}/members/{targetUid}.get()`; si `exists`, `.update({ hidden: true, hiddenAt: FieldValue.serverTimestamp() })`. Si no existe, no hacer nada (nunca puntuó ahí).
  - `groupRef.update({ memberIds: FieldValue.arrayRemove(targetUid), ...(block ? { blockedIds: FieldValue.arrayUnion(targetUid) } : {}) })`.
  - `logger.info(\`removeGroupMember: ${targetUid} quitado de ${groupId} por ${requesterUid} (block=${block})\`)`.
- `export async function unblockGroupMember(groupId: string, requesterUid: string, targetUid: string): Promise<void>`
  - Mismos guards de grupo global / not-found / `createdBy`.
  - `groupRef.update({ blockedIds: FieldValue.arrayRemove(targetUid) })`. **No** re-agrega a `memberIds`
    (la persona se vuelve a unir con el código) ni des-oculta el standing (des-ocultarlo sin que sea
    miembro reintroduciría la fila fantasma en el leaderboard de los demás; se des-oculta solo al
    volver a ser puntuada como miembro).
  - `logger.info(...)`.

### `functions/src/index.ts` — dos `onCall` calcados de `deleteGroup` (líneas 97-109)

```ts
import { removeGroupMember, unblockGroupMember } from "./groupMembers";

export const removeGroupMember = onCall<{ groupId?: string; targetUid?: string; block?: boolean }>(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const { groupId, targetUid } = request.data;
  if (!groupId || !targetUid) throw new HttpsError("invalid-argument", "Faltan datos.");
  await removeGroupMemberImpl(groupId, uid, targetUid, request.data.block === true);
  return { success: true };
});
// unblockGroupMember: idéntico, sin `block`.
```
*(el nombre del import del worker se aliasa —`removeGroupMemberImpl`— para no chocar con el `export const`.)*

### `firestore.rules` — `groups/{groupId}` `allow update`

1. Añadir `'blockedIds'` a la lista de claves solo-creador (línea 76):
   `.hasAny(['photoUrl', 'iconId', 'name', 'inviteCode', 'blockedIds'])`.
   El cliente nunca escribe `blockedIds` (lo hace la CF, que se salta reglas) — esto es solo
   endurecimiento: impide que un miembro no-creador inyecte `blockedIds` con un update genérico.
2. En la rama de self-join de no-creador (líneas 81-84) añadir la negación de bloqueo:
   ```
   || (
     request.resource.data.memberIds.hasAll(resource.data.memberIds)
     && request.resource.data.memberIds.size() <= resource.data.memberIds.size() + 1
     && !(request.auth.uid in resource.data.get('blockedIds', []))
   )
   ```
   `.get('blockedIds', [])` porque los docs de grupo viejos no tienen el campo. La rama del creador
   no se toca.

### `firestore.rules` — `groups/{groupId}/weeks/{weekId}/picks/{userId}` `allow write` (hallazgo AGY #1)

Hoy el write de picks solo exige `request.auth.uid == userId`; **no** exige membresía. Un usuario
eliminado/bloqueado puede seguir escribiendo su doc de picks vía SDK directo, y si luego el admin lo
desbloquea y se re-une antes del puntuado, esos picks "de contrabando" cuentan. Cerrar añadiendo el
chequeo de membresía, calcado del `allow read` de la línea de arriba y de las reglas de `weeks`/`board`:
```
match /picks/{userId} {
  allow read:  if request.auth != null
               && request.auth.uid in get(/databases/$(database)/documents/groups/$(groupId)).data.memberIds;
  allow write: if request.auth != null
               && request.auth.uid == userId
               && request.auth.uid in get(/databases/$(database)/documents/groups/$(groupId)).data.memberIds;
}
```
Un miembro legítimo está en `memberIds`, así que su envío de picks normal no cambia. El grupo mock
nunca escribe a Firestore real, así que no se ve afectado.

### `firestore.rules` — `standings/{groupId}/members/{userId}` carve-out para des-ocultar (hallazgo AGY #2)

`standings` es `allow write: if false`. Para que un miembro que **vuelve** al grupo recupere su
standing (hoy `hidden: true`), añadir un `allow update` estrechísimo — solo el dueño, solo si es
miembro, y **solo** puede tocar/borrar `hidden`/`hiddenAt` (nunca `totalPoints`/`weeklyBreakdown`,
la frontera anti-trampa se mantiene). Mismo patrón `affectedKeys().hasOnly([...])` que ya usan
`photoUrl`/`iconId` en `groups` e `isAnnouncement` en `board`:
```
match /standings/{groupId}/members/{userId} {
  allow read: if request.auth != null
              && request.auth.uid in get(/databases/$(database)/documents/groups/$(groupId)).data.memberIds;
  allow update: if request.auth != null
                && request.auth.uid == userId
                && request.auth.uid in get(/databases/$(database)/documents/groups/$(groupId)).data.memberIds
                && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['hidden', 'hiddenAt']);
  allow create, delete: if false;
}
```
El des-ocultado real lo dispara `FirebaseGroupDataSource.joinGroup` (Paso 2).

### Verificación Paso 1

- `cd functions && npm run build` (tsc) verde. `firestore.rules` compila (`firebase deploy` lo valida).
- **Tests de functions (nuevos, este paso):**
  - `functions/test/integration/groupMembers.test.ts` — calcado de `groupDeletion.test.ts`
    (importa los workers directamente, siembra con `db` del emulador, asevera `HttpsError.code` con
    `.rejects.toHaveProperty("code", ...)` y el estado del emulador):
    rechaza grupo global; `not-found`; `permission-denied` si el caller no es `createdBy`;
    `invalid-argument` si `targetUid === createdBy`; happy path: `targetUid` fuera de `memberIds`,
    `standings/.../{targetUid}` con `hidden: true`, y con `block: true` también en `blockedIds`;
    `unblockGroupMember` saca de `blockedIds` y deja `memberIds`/`hidden` intactos.
  - `functions/test/rules/firestore-rules.test.ts` — casos nuevos (usa `@firebase/rules-unit-testing`,
    helpers `alice()/bob()`, `seedGroup()`):
    · un grupo con `blockedIds: ["carol"]` → `updateDoc(doc(carol, "groups/g1"), { memberIds: [...old, "carol"] })` **falla**;
      el mismo update para un uid no bloqueado **sí** procede; un no-creador que escribe `blockedIds` **falla**.
    · `picks/{userId}` write: un miembro **puede** escribir su doc de picks; un **no-miembro** (uid
      fuera de `memberIds`) escribiendo su propio doc de picks **falla** (hallazgo AGY #1).
    · `standings/{groupId}/members/{userId}`: el dueño **miembro** puede `update` que solo borra
      `hidden`/`hiddenAt`; el mismo dueño intentando tocar `totalPoints` **falla**; un `update` de
      otro uid **falla**; `create`/`delete` desde cliente **fallan** (hallazgo AGY #2).
  - `npm test` (unit) sigue verde; integración/reglas corren donde haya emulador (CI o máquina del
    usuario), igual que se hizo con `groupDeletion` en PR #48/#49.
- **Deploy (Rule 6 + functions, lo corre el usuario o Claude tras autorización):**
  `firebase deploy --only firestore:rules,storage` **y** `firebase deploy --only functions`.

---

## Paso 2 — `Group.blockedIds` + capa domain/data + use cases

**Estado objetivo:** `GroupRepository` expone quitar/desbloquear (vía callables); `Group` transporta
`blockedIds`; existen los use cases.

- **`domain/model/Group.kt`** — añadir `val blockedIds: List<String> = emptyList()` (default → todas
  las construcciones de `Group(...)` existentes en tests/preview siguen compilando).
- **`data/remote/firebase/FirebaseGroupDataSource.kt`**:
  - `toGroup()` (final del archivo) — `blockedIds = (get("blockedIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()`.
  - `suspend fun removeGroupMember(groupId: String, targetUserId: String, block: Boolean)` →
    `functions.getHttpsCallable("removeGroupMember").call(mapOf("groupId" to groupId, "targetUid" to targetUserId, "block" to block)).await()` — mismo patrón que `deleteGroup` (línea 121).
  - `suspend fun unblockGroupMember(groupId: String, targetUserId: String)` → callable
    `unblockGroupMember` con `mapOf("groupId" to groupId, "targetUid" to targetUserId)`.
  - **`joinGroup` — des-ocultar el standing al (re)unirse (hallazgo AGY #2):** tras el
    `update("memberIds", FieldValue.arrayUnion(userId))` existente y su re-lectura, añadir un intento
    best-effort de limpiar `hidden`/`hiddenAt` del propio standing:
    ```kotlin
    runCatching {
        firestore.collection("standings").document(doc.id)
            .collection("members").document(userId)
            .update(mapOf("hidden" to FieldValue.delete(), "hiddenAt" to FieldValue.delete()))
            .await()
    }   // NOT_FOUND si nunca puntuó en ese grupo — se ignora
    ```
    Solo corre en la rama `alreadyMember == false` (no hay nada que des-ocultar si ya era miembro).
    La regla nueva de `standings` (Paso 1) lo permite; `FieldValue.delete()` sobre claves ausentes es
    no-op, así que un standing sin `hidden` tampoco falla la regla `hasOnly`.
- **`domain/repository/GroupRepository.kt`** — añadir ambas firmas con KDoc (mencionar que la CF valida
  `createdBy` y rechaza el grupo global, igual que `deleteGroup`).
- **`data/repository/GroupRepositoryImpl.kt`** — delegar ambas directo (sin `runCatching`, como
  `deleteGroup`/`renameGroup`).
- **`domain/usecase/RemoveGroupMemberUseCase.kt`** y **`UnblockGroupMemberUseCase.kt`** (nuevos,
  uno por archivo como `DeleteGroupUseCase.kt`/`RenameGroupUseCase.kt`):
  `@Inject constructor(private val groupRepository: GroupRepository)`,
  `suspend operator fun invoke(groupId: String, targetUserId: String, block: Boolean) = groupRepository.removeGroupMember(...)`
  (y la de unblock sin `block`). Sin trimming ni lógica — son delegadores.
- **Fakes de `GroupRepository` en tests** — añadir los 2 overrides nuevos (normalmente
  `throw NotImplementedError()`, salvo donde se prueben) en:
  `domain/usecase/DeleteGroupUseCaseTest.kt` (`CapturingDeleteRenameRepository`),
  `domain/usecase/CreateGroupUseCaseTest.kt`, `domain/usecase/JoinGroupUseCaseTest.kt`,
  `domain/usecase/UploadGroupPhotoUseCaseTest.kt`, `integration/GroupViewModelIntegrationTest.kt`.
- **Tests nuevos:**
  - `RemoveGroupMemberUseCaseTest` / `UnblockGroupMemberUseCaseTest` (pueden ir juntos en
    `RemoveGroupMemberUseCaseTest.kt`, precedente `DeleteGroupUseCaseTest.kt`) — fake escrito a mano
    que captura `(groupId, targetUserId, block)`; aseveran que se delega con los args correctos.
  - `FirebaseGroupDataSourceTest` — casos nuevos con MockK sobre `FirebaseFunctions`
    (`HttpsCallableReference` + `Tasks.forResult(mockk<HttpsCallableResult>())`), calcados del test de
    `deleteGroup`: `removeGroupMember` invoca la callable `"removeGroupMember"` con
    `mapOf("groupId" to ..., "targetUid" to ..., "block" to ...)`; `unblockGroupMember` análogo.
    Además: los tests existentes de `joinGroup` que hacen `verify { docRef.update(...) }` ahora deben
    stubbear también la cadena `firestore.collection("standings").document(id).collection("members")
    .document(uid).update(...)` → `Tasks.forResult<Void>(null)` (y un caso que la haga fallar con
    `Tasks.forException(...)` para comprobar que `joinGroup` igual retorna OK — el `runCatching`).
- **Verificación:** `./gradlew assembleDebug` + `./gradlew test`.

---

## Paso 3 — `GroupViewModel`: acciones de quitar / desbloquear + analytics

**Estado objetivo:** el VM compartido expone las acciones con guard de admin + guard "no a ti mismo",
la lista de usuarios para resolver nombres, y loguea los eventos.

- **`presentation/groups/GroupViewModel.kt`**:
  - Inyectar `RemoveGroupMemberUseCase`, `UnblockGroupMemberUseCase`.
  - `val allUsers: StateFlow<List<User>> = userRepository.getAllUsers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())`
    — espejo de `UserManagementViewModel`; frío hasta que `GroupSettingsScreen` lo colecta.
  - `fun removeGroupMember(group: Group, requesterUserId: String, targetUserId: String, block: Boolean)`:
    - `if (!canManageGroup(group, requesterUserId)) return` (ya cubre global + mock + no-creador),
    - `if (targetUserId == group.createdBy) return` (nadie quita al admin — la CF también lo valida),
    - `Working` → `removeGroupMemberUseCase(group.id, targetUserId, block)` → éxito: `Idle` +
      `logger.logEvent(AppEvent.GroupMemberRemoved(group.id, targetUserId, block))`; error:
      `GroupSettingsUiState.Error(e.message ?: "Error al quitar al miembro")`.
  - `fun unblockGroupMember(group, requesterUserId, targetUserId)`: mismo guard `canManageGroup`;
    `Working` → `unblockGroupMemberUseCase(group.id, targetUserId)` → `Idle` +
    `logger.logEvent(AppEvent.GroupMemberUnblocked(group.id, targetUserId))`; error → `Error`.
  - **Sin `GroupUiEffect` nuevo:** la fila del miembro desaparece sola cuando el listener en vivo
    (`groupListState`) reemite el grupo con `memberIds`/`blockedIds` actualizados; el error se muestra
    inline (la pantalla ya renderiza `settingsState.message`). Igual que `renameGroup`. Se preserva la
    invariante "solo `GroupsScreen` consume `effects`".
- **`analytics/AppEvent.kt`** — en la sección Groups, tras `GroupDeleted`:
  ```kotlin
  data class GroupMemberRemoved(val groupId: String, val targetUserId: String, val blocked: Boolean) :
      AppEvent("group_member_removed",
          mapOf("group_id" to groupId, "target_user_id" to targetUserId, "blocked" to blocked))

  data class GroupMemberUnblocked(val groupId: String, val targetUserId: String) :
      AppEvent("group_member_unblocked",
          mapOf("group_id" to groupId, "target_user_id" to targetUserId))
  ```
  `target_user_id` es un UID crudo, nunca un nombre (mismo criterio que `UserRoleChanged`). No es PII.
- **`app/src/test/java/.../analytics/AppEventTest.kt`** — añadir una instancia de cada evento nuevo a
  `allEventSamples` y subir `EXPECTED_EVENT_TYPES` en +2.
- **`CLAUDE.md`** — tabla "Event Inventory (Current)": añadir filas 36 `group_member_removed`
  (`group_id`, `target_user_id`, `blocked` | Groups) y 37 `group_member_unblocked`
  (`group_id`, `target_user_id` | Groups). Si `.agents/rules/constraints-analytics.md` refleja la
  misma tabla, actualizarla igual (registrar Custom Dimensions es paso de consola, fuera de alcance).
- **Tests** — `presentation/groups/GroupViewModelTest.kt` (MockK): añadir los 2
  `mockk<...UseCase>()` al constructor del VM y `every { userRepo.getAllUsers() } returns flowOf(emptyList())`
  en `setUp()`. Casos, calcados de los de `deleteGroup`:
  quitar OK → `Idle` + loguea `group_member_removed` con `blocked=false`; con `block=true` →
  `blocked=true`; no-creador / grupo global / grupo mock → no-op (`coVerify(exactly = 0)`);
  `targetUserId == group.createdBy` → no-op; error del use case → `GroupSettingsUiState.Error`;
  desbloquear OK → loguea `group_member_unblocked`; desbloquear no-creador → no-op.
- **Verificación:** `./gradlew assembleDebug` + `./gradlew test`.

---

## Paso 4 — `GroupSettingsScreen`: secciones "Miembros" y "Bloqueados" + wiring en `NavGraph`

**Estado objetivo:** el admin ve y gestiona miembros/bloqueados dentro de la pantalla de config actual.

- **`presentation/groups/GroupSettingsScreen.kt`** — nuevos params:
  ```kotlin
  members: List<User>,          // resuelto desde memberIds; el creador va primero, marcado
  blockedMembers: List<User>,   // resuelto desde blockedIds
  onRemoveMember: (targetUserId: String, block: Boolean) -> Unit,
  onUnblockMember: (targetUserId: String) -> Unit,
  ```
  - **Sección "── Miembros ──"** (antes de la "Zona de peligro"): `Text` de encabezado + un `Column`
    simple (NO `LazyColumn` — la pantalla ya es `verticalScroll`; listas de decenas como máximo)
    mapeando `members`. Cada fila: avatar + `user.effectiveDisplayName` (fallback a un placeholder si
    el `User` aún no cargó de `getAllUsers()` — construir la fila desde el uid y enriquecer, estilo
    `FirebaseLeaderboardDataSource`: `displayName → username → uid`), etiqueta "(admin)" en la fila
    del `createdBy` y "(tú)" si `user.uid == currentUserId`. Trailing: `IconButton` de overflow
    (`Icons.Filled.MoreVert`) **solo si** `user.uid != group.createdBy` — abre un `AlertDialog`
    (title = nombre; text explicando) con dos `TextButton` apilados: "Eliminar del grupo"
    (`onRemoveMember(uid, block = false)`) y "Bloquear — no podrá volver a entrar"
    (`onRemoveMember(uid, block = true)`), más "Cancelar". El diálogo ES la confirmación (sin doble
    confirm), mismo criterio que el `AlertDialog` de borrar mensaje del board.
  - **Sección "── Bloqueados ──"**: renderizar solo si `blockedMembers.isNotEmpty()`. Encabezado +
    filas (avatar + nombre) con un `TextButton` "Desbloquear" → `onUnblockMember(uid)`.
  - Todos los controles nuevos deshabilitados si `val working = settingsState is GroupSettingsUiState.Working`
    (ya existe). El `LaunchedEffect` de auto-salida y el `isAdmin` no cambian.
  - Avatar: reusar el patrón de `UserManagementScreen.UserRow` / `UserAvatar` (Coil `AsyncImage` de
    `photoUrl`, si no `Box` con la inicial). Si conviene, extraer `UserAvatar` a
    `presentation/common/` y que `UserManagementScreen` lo importe; si no, replicar inline (pequeño).
  - Actualizar `GroupSettingsScreenPreview` (pasar `members`/`blockedMembers` con 1-2 `User` fake de
    `PreviewData`, y `{ _, _ -> }` / `{}` para los callbacks).
- **`presentation/navigation/NavGraph.kt`** — bloque `composable(Screen.GroupSettings.route)`
  (≈ líneas 277-317):
  - `val allUsers by groupViewModel.allUsers.collectAsStateWithLifecycle()`.
  - `val members = group?.memberIds.orEmpty().map { uid -> allUsers.find { it.uid == uid } ?: User(uid = uid, displayName = "", email = "", photoUrl = null) }`
    con el `createdBy` reordenado al frente; `blockedMembers` análogo desde `group?.blockedIds`.
  - `onRemoveMember = { target, block -> group?.let { g -> currentUserId?.let { u -> groupViewModel.removeGroupMember(g, u, target, block) } } }`,
    `onUnblockMember = { target -> group?.let { g -> currentUserId?.let { u -> groupViewModel.unblockGroupMember(g, u, target) } } }`.
- **`app/src/main/res/values/strings.xml` + `values-en/strings.xml`** (ambos en sync, español neutro
  mexicano tuteo — Rule 5): claves nuevas siguiendo la convención existente
  (`group_settings_*`, títulos como pregunta `¿…?`, sin pronombre):
  `group_settings_members` ("Miembros"), `group_settings_blocked` ("Bloqueados"),
  `group_member_tag_admin` ("admin"), `group_member_tag_you` ("tú"),
  `cd_group_member_actions` ("Acciones del miembro"),
  `group_member_actions_title` (nombre — se pasa como arg o se usa el nombre directo),
  `group_member_actions_body` ("Elige qué hacer con este miembro."),
  `group_member_remove` ("Eliminar del grupo"),
  `group_member_block` ("Bloquear — no podrá volver a entrar"),
  `group_member_unblock` ("Desbloquear"),
  `group_settings_member_error` (si se quiere un texto genérico; si no, se reusa el mensaje de la
  excepción como hace rename/delete).
- **Verificación:** `./gradlew assembleDebug` + `./gradlew test` (previews compilan).

---

## Paso 5 — Barrido final, tests de reglas y cross-review de implementación

- Correr toda la suite: `./gradlew test` + `./gradlew assembleDebug`; `cd functions && npm run build && npm test`.
  Integración/reglas de functions donde haya emulador.
- Confirmar que `CLAUDE.md` (Event Inventory) y `.agents/rules/constraints-analytics.md` quedaron
  actualizados (Paso 3) y que `AppEventTest.EXPECTED_EVENT_TYPES` cuadra.
- **Cross-review de implementación** (Rule 10.5) contra el diff acumulado, tras cada paso y un pase
  final: `agy.exe --mode plan --dangerously-skip-permissions -p "Review the current uncommitted git diff for correctness, security, and design issues" --model gemini-3.1-pro-high --effort high`.
  Aplicar lo que proceda, verificando cada hallazgo contra el código; documentar en el "Cross-Review Log".
- **Gradle:** no cambian archivos Gradle ⇒ no `./gradlew dependencies`.
- **Branch:** feature fuera del roadmap (p. ej. `feature/group-member-moderation`); el usuario decide
  numeración. Commits/push siempre los hace el usuario (Rule 10.8, y [[git-workflow-preference]]).

---

## Notas de diseño / limitaciones aceptadas

- **Des-ocultado al re-unirse:** `FirebaseGroupDataSource.joinGroup` limpia `hidden`/`hiddenAt` del
  propio standing en la misma secuencia del `arrayUnion` (Paso 2), así que un miembro que vuelve
  recupera su historial **de inmediato**, sin esperar al siguiente puntuado (cierra el hallazgo AGY
  #2: `scoreGroupForWeek` salta a los usuarios sin picks gradeables — `if (unsettled.length === 0)
  continue;` —, así que depender del puntuado dejaría oculto para siempre a quien vuelve en pretemporada
  o semana de bye).
- **`unblockGroupMember` no des-oculta ni re-agrega:** a propósito. Des-ocultar el standing de alguien
  que aún no es miembro reintroduciría su fila fantasma en el leaderboard de los demás (el listener de
  standings lista todos los `members/**` y solo filtra `hidden == true`). El des-ocultado ocurre
  cuando la persona efectivamente vuelve, vía `joinGroup`.
- **Alternativa descartada (hard-delete):** en vez de `hidden: true` se podría borrar el doc de
  standing en la CF (como hace `groupDeletion.ts` con todo el árbol). Es más simple y no toca las
  reglas de `standings`, pero pierde el historial de forma irreversible — el usuario pidió
  explícitamente "Ocultar (reversible)", así que se mantiene el `hidden`.
- **Admin único = `createdBy`:** si el creador borra su cuenta, `accountDeletion.ts:removeMemberFromGroup`
  **ya transfiere `createdBy` a `remaining[0]`**, así que el grupo no queda sin admin (hallazgo AGY #5
  — ya resuelto en el código actual, sin cambios en este plan).
- **Resolución de nombres vía `getAllUsers()`:** se descarta un `getUsersByIds` dedicado (hallazgo
  AGY #3). El proyecto tiene techo declarado de "unas decenas de usuarios" (ver PR-24, congelada por
  ese mismo motivo) y `UserManagementViewModel` ya usa `getAllUsers()` para exactamente este fin. Si
  la escala cambiara, el swap a `getUsersByIds(memberIds + blockedIds)` es local a `NavGraph` + una
  nueva función de repo.

---

## Verificación end-to-end (tras autorización del usuario para prueba manual — Rule 7)

1. Como **admin** de un grupo no global: `GroupSettingsScreen` muestra "Miembros" con todos los
   `memberIds` resueltos a nombre + avatar; la fila del admin lleva "(admin)" y no tiene botón de
   acciones. Como **no-admin** no se llega a esta pantalla (auto-salida existente).
2. Overflow en un miembro → diálogo → "Eliminar del grupo": la fila desaparece; ese usuario deja de
   ver el grupo (listener en vivo — `NavGraph` ya lo saca de `GroupSessionScreen`/`History` vía el
   `LaunchedEffect(group == null)` de PR #48, líneas 216-220 / 254-258) y ya no aparece en el
   leaderboard (standing `hidden`). Puede volver a unirse con el código.
3. Overflow → "Bloquear": la fila sale de "Miembros" y aparece en "Bloqueados". Ese usuario, al
   intentar unirse con el código, recibe error (regla de self-join + validación de `joinGroup`).
4. "Bloqueados" → "Desbloquear": la fila desaparece de "Bloqueados"; el usuario ya puede volver a
   unirse con el código. Al re-unirse, su standing se des-oculta en el acto (`joinGroup` borra
   `hidden`) y su historial reaparece.
5. Firebase console / emulador: `groups/{id}.memberIds` sin el uid; `blockedIds` con/sin el uid según
   el caso; `standings/{id}/members/{uid}.hidden == true` tras quitar, y sin `hidden` tras re-unirse.
   `picks`/`results` intactos (inertes).
6. Como usuario **eliminado o bloqueado**: un `set` directo por SDK a
   `groups/{id}/weeks/{w}/picks/{miUid}` → **PERMISSION_DENIED** (regla nueva de membresía).
7. Intentar `removeGroupMember` sobre el grupo global, desde un no-creador, o con
   `targetUid == createdBy` → `HttpsError` (`permission-denied` / `invalid-argument`).
8. `./gradlew test` + `./gradlew assembleDebug` verdes; `cd functions && npm run build && npm test` verdes.

---

## Cross-Review Log

### Review de plan

- **Codex:** deshabilitado por completo del flujo de cross-review (decisión del usuario,
  2026-09-07). AGY es el único reviewer; no hay revisión de Codex pendiente para este plan.

- **Antigravity (`agy.exe`, gemini-3.1-pro-high, effort high)** — 2026-09-07, único reviewer.
  5 hallazgos, revisados contra el código:

  | # | Hallazgo AGY | Veredicto | Acción |
  |---|---|---|---|
  | 1 | `firestore.rules` `picks/{userId}` `allow write` solo exige `uid == userId`, no membresía → un usuario quitado/bloqueado puede seguir escribiendo picks por SDK; si lo desbloquean y re-une antes del puntuado, cuentan. | **Válido.** Confirmado en `firestore.rules:117-121`. Pre-existente, pero este feature lo vuelve explotable a propósito (evasión de bloqueo). | **Adoptado** — Paso 1 añade `&& request.auth.uid in get(.../groups/$(groupId)).data.memberIds` al write de `picks/{userId}`, + casos en `firestore-rules.test.ts`. |
  | 2 | El "des-ocultado al re-unirse" del plan dependía del siguiente `scoreGroupForWeek`, que salta a usuarios sin picks gradeables (`if (unsettled.length === 0) continue;` — `scoring.ts:57`) → oculto para siempre en pretemporada / semana de bye. | **Válido.** La "limitación" era en realidad un bug. | **Adoptado (Opción A de AGY)** — Paso 1 añade un `allow update` estrechísimo a `standings/{groupId}/members/{userId}` restringido a `affectedKeys().hasOnly(['hidden','hiddenAt'])` para el propio miembro; Paso 2 hace que `joinGroup` borre `hidden`/`hiddenAt` best-effort (`runCatching`). Se descartó Opción B (mover `joinGroup` a CF) por ser mucho más invasivo. |
  | 3 | Usar `getAllUsers()` (toda la colección `users`) para resolver nombres de miembros es poco escalable. | **Válido en principio, no aplicado.** El proyecto tiene techo declarado de "unas decenas de usuarios" (PR-24 congelada por ese motivo) y `UserManagementViewModel` **ya** usa `getAllUsers()` para lo mismo. Swap a `getUsersByIds` es local si cambia la escala. | **No aplicado** — documentado en "Notas de diseño". |
  | 4 | El plan no cubre al miembro que está dentro de `GroupSessionScreen` cuando el admin lo quita. | **Ya resuelto en el código.** `NavGraph.kt:216-220` (bloque `GroupSession`) y `:254-258` (bloque `History`) ya tienen `LaunchedEffect(groupListState, group) { if (Success && group == null) navigate(Groups){ popUpTo(Groups) } }` desde PR #48. El grupo desaparece del snapshot `getGroupsForUser` del quitado → auto-salida. | **No requiere cambios** — añadida línea de verificación al e2e (paso 2). |
  | 5 | Si el admin (único, `createdBy`) borra su cuenta, el grupo queda sin admin. | **Ya resuelto en el código.** `accountDeletion.ts:70` `removeMemberFromGroup` ya transfiere `createdBy = remaining[0]` cuando el que se va era el creador. | **No requiere cambios** — anotado en "Notas de diseño". |

### Review de implementación

*(pendiente — se corre AGY contra el diff acumulado tras cada paso)*
