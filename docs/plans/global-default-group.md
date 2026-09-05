# Grupo Global "NFLocos de Corazón" — Plan

## Contexto

Se requiere un grupo por default, presente para todos los usuarios de la app, que:
- se llame "NFLocos de Corazón" con el ícono `nflocos_picks_icon` (ya existe en `app/src/main/res/drawable/nflocos_picks_icon.png`),
- esté siempre pinneado en primer lugar en la lista de grupos de cada usuario,
- muestre su feed (board) también en `GroupsScreen`, ocupando un tercio del espacio disponible al fondo, con margen respecto a los FABs de crear/unirse,
- tenga como único admin (único que puede marcar mensajes como anuncio) al usuario con correo `nezaboost@gmail.com` / username `saulbrisniega`,
- incluya por default a todos los usuarios activos en su tabla de standings,
- deshabilite cuentas (y las retire de standings) tras un año completo de inactividad.

La exploración del código (agosto 2026) confirmó que **ninguna de estas piezas tiene precedente hoy**: no hay flag de pinneo/grupo-de-sistema, la membresía siempre es explícita (`arrayUnion` al unirse), "admin" es únicamente `Group.createdBy`, los standings se crean de forma perezosa solo cuando la Cloud Function de scoring puntúa un pick (nunca al unirse), y no existe ningún campo de actividad (`lastActive`/`isActive`) en `User` ni ninguna Cloud Function programada para inactividad.

Patrones reutilizables identificados:
- `GroupViewModel.observeGroups` ya antepone un grupo sintético a la lista (precedente directo para el pinneo).
- `usernames/{username}` → `{userId}` ya existe en Firestore — permite resolver el UID de `saulbrisniega` sin pedirlo a mano ni hardcodear su email en el código.
- `functions/src/scoring.ts` (`onSchedule`, cron Sun/Mon/Thu) es el patrón exacto a clonar para una Cloud Function de inactividad programada.
- `firestore.rules` en `groups/{groupId}.update` ya permite que cualquier usuario autenticado se auto-agregue a `memberIds` de cualquier grupo (mismo mecanismo que unirse por código) — el auto-join al grupo global no requiere cambios de reglas.

## Decisiones de arquitectura

| Decisión | Opción elegida | Por qué |
|---|---|---|
| Standings "todos los usuarios por default" | Auto-afiliar a todo usuario a `memberIds` del grupo global (alta + backfill único) y sembrar standing en 0 pts vía Cloud Function | El grupo global se comporta igual que cualquier otro grupo; sin casos especiales de lectura en el leaderboard |
| Admin del board | `nezaboost@gmail.com` (resuelto vía `usernames/saulbrisniega`) como `createdBy` literal del grupo global | Reutiliza el 100% de la lógica de permisos existente (`BoardViewModel.isGroupAdmin`, reglas de `board/{messageId}`), sin código nuevo de "usuario privilegiado hardcodeado" |
| Feed en `GroupsScreen` | Panel fijo de solo lectura (últimos mensajes/anuncios, sin scroll propio), tap abre el board completo | Evita dos scrolls verticales compitiendo dentro de la misma pantalla |
| Reactivación tras deshabilitar por inactividad | Automática: cualquier login reactiva la cuenta y reinicia el año de inactividad | Comportamiento simple, sin flujo de recuperación manual que hoy no existe |

Consecuencia de la decisión de admin: como `createdBy` también otorga poder de borrar el grupo bajo la regla actual, PR-16 agrega una excepción explícita en `firestore.rules` que bloquea el `delete` del grupo global sin importar quién sea `createdBy`.

## Desglose de PRs

Implementación uno-a-la-vez, cada uno en su propia rama, siguiendo la convención de este repo (`CLAUDE.md` § PR Roadmap).

### PR-16 — Global Group Foundation
**Branch:** `feature/16-global-group-foundation`

- Prerequisito manual único: resolver el UID de `nezaboost@gmail.com` leyendo `usernames/saulbrisniega` (Admin SDK/consola).
- Script/paso único vía Admin SDK (no una Cloud Function permanente) que crea `groups/{GLOBAL_GROUP_ID}` con `name = "NFLocos de Corazón"`, `createdBy` = ese UID, `memberIds = [ese uid]`. `GLOBAL_GROUP_ID` es un id fijo y reservado, compartido entre cliente y `firestore.rules`.
- `GroupAvatar.kt` (`presentation/common/`) — nuevo parámetro `localIconRes: Int?` en el fallback chain (antes de `iconId`/letra) para pintar `nflocos_picks_icon.png`.
- `GroupViewModel.observeGroups` — ordena la lista real de Firestore para que `id == GLOBAL_GROUP_ID` quede siempre primero.
- `firestore.rules` — nueva cláusula en `groups/{groupId}.delete` que bloquea el borrado cuando `groupId == GLOBAL_GROUP_ID`, sin importar `createdBy`. Deploy inmediato.

### PR-17 — Global Group Auto-Membership & Standings Seeding
**Branch:** `feature/17-global-group-membership`

- `UserRepositoryImpl.upsertAndResolveRole` — en la rama `isNewUser`, auto-agregar al usuario a `memberIds` del grupo global vía `arrayUnion` (ya permitido por la regla `update` existente).
- Nueva Cloud Function `onCall` (ej. `ensureGlobalStanding`) que siembra `standings/{GLOBAL_GROUP_ID}/members/{userId}` en `{ totalPoints: 0, weeklyBreakdown: {} }` si no existe, invocada tras el auto-join (`standings` tiene `allow write: if false` para clientes).
- Script único de backfill (Admin SDK) que agrega a todos los usuarios existentes a `memberIds` del grupo global y siembra su standing en 0 puntos.

### PR-18 — Global Board Admin Verification & Rule Tightening
**Branch:** `feature/18-global-board-admin`

- Verificar que `BoardViewModel.isGroupAdmin` funciona sin cambios para el grupo global, con cobertura de test explícita.
- Endurecer `firestore.rules` en `board/{messageId}.update` para que el toggle de `isAnnouncement` use `diff(resource.data).affectedKeys()` (patrón ya usado en `groups/{groupId}.photoUrl/iconId`), cerrando el gap donde cualquier update por autor/`createdBy` permite tocar `isAnnouncement` sin distinguir el campo.

### PR-19 — Global Feed Panel on GroupsScreen
**Branch:** `feature/19-global-feed-panel`

- Nuevo composable `GlobalGroupFeedPanel` (`presentation/groups/`) — panel fijo de solo lectura vía `WatchBoardMessagesUseCase` (ya existe); tap navega al board completo (`GroupSessionScreen` con `groupId = GLOBAL_GROUP_ID`).
- `GroupsScreenContent` — la lista de grupos ocupa ~2/3 (`weight`), el panel del feed ~1/3 al fondo, con padding inferior suficiente para que los FABs de crear/unirse nunca lo tapen.

### PR-20 — Account Inactivity Deactivation
**Branch:** `feature/20-inactivity-deactivation`

- `User` (domain) + `users/{uid}` — nuevo campo `lastActive`, auto-escrito por el dueño en cada sign-in dentro de `upsertAndResolveRole` (sin cambios de reglas para este campo). Implementa la reactivación automática.
- `users/{uid}` — nuevo campo `isActive`/`disabledAt`, bloqueado contra escritura de cliente (nueva cláusula con `diff().affectedKeys()` en `firestore.rules`).
- Nueva Cloud Function `onSchedule` (`functions/src/inactivity.ts`, cron diario, mismo patrón que `scheduledScoring`) que marca `isActive = false` a quienes tengan `lastActive` de más de un año y remueve/oculta su entrada de standings en todos sus grupos (mismo patrón de iteración que `accountDeletion.ts`). La reactivación no requiere lógica adicional: la siguiente corrida vuelve a incluir a quien tenga `lastActive` reciente.

## Verificación

Cada PR sigue las reglas 2, 4 y 5 de `CLAUDE.md`: `./gradlew assembleDebug` (+ `test` si hay lógica) antes de cada commit, deploy inmediato de `firestore.rules`/`storage.rules` tras cualquier cambio a esos archivos, y ninguna verificación manual en emulador/dispositivo sin autorización explícita previa.

## Estado real de implementación (septiembre 2026)

PR-16 a PR-20 se implementaron y mergearon a `main` como un solo commit/branch (`feature/global-group`, commit `ae9ab89`), no como 5 PRs separados según el desglose original. El código de este commit es la base de la revisión de seguridad de abajo.

## Revisión de seguridad post-implementación (septiembre 2026)

Revisión cruzada de `ae9ab89` con tres fuentes independientes — Codex CLI (`codex exec review --commit ae9ab89`), Antigravity CLI (Gemini 3.8, revisión manual guiada), y verificación línea por línea por Claude Code contra el código actual en `main`. Los tres coincidieron en el problema raíz de seguridad; Antigravity encontró 3 hallazgos adicionales que ni Codex ni Claude habían detectado.

| # | Severidad | Área | Archivo:línea | Encontrado por | Estado |
|---|---|---|---|---|---|
| 1 | Crítica | Seguridad Firestore | `firestore.rules` — regla `update` de `groups/{groupId}` (líneas ~58-67) | Codex + Antigravity + Claude (verificado) | **En progreso** |
| 2 | Alta | Seguridad Firestore | `firestore.rules` — `weeks/{weekId}` `allow write` (líneas ~79-84) | Antigravity (verificado por Claude) | **En progreso** |
| 3 | Alta | Condición de carrera | `UserRepositoryImpl.kt:421-427` — `role` se persiste antes de que `ensureGlobalGroupMembership` termine; fallo se traga en silencio (`runCatching`/`Timber.w`), nunca se reintenta | Antigravity (verificado por Claude) | **Resuelto** |
| 4 | Media | Condición de carrera | `functions/src/globalGroup.ts:24-27` — `seedGlobalStanding` hace `get()`+`set()` no transaccional; puede pisar puntos si el scoring corre en medio | Antigravity (verificado por Claude) | **Resuelto (parcial, ver nota)** |
| 5 | Crítica (lógica de negocio) | Pérdida de datos | `functions/src/inactivity.ts:48-54` — `.delete()` del standing completo; ninguna reactivación lo restaura, `weeklyBreakdown`/`totalPoints` históricos se pierden para siempre | Codex + Antigravity + Claude (verificado) | **Resuelto** |
| 6 | Media (diseño) | Escalabilidad | Un solo documento (`groups/global_nflocos_de_corazon`) acumula el UID de *todos* los usuarios de la app en `memberIds[]` — límite de Firestore de ~1 MiB por doc (~25-30k UIDs) y contención recomendada de ~1 escritura/seg por documento | Antigravity | Documentado como PR futuro (ver roadmap) |

### Detalle — hallazgo #1 (raíz de 4 variantes de ataque)

La regla `update` actual solo protege `photoUrl`/`iconId` como campos "solo-creador"; no protege `createdBy`, `memberIds` ni `name`/`inviteCode`. Como *todo* usuario de la app es miembro del grupo global (auto-join de PR-17), cualquier usuario autenticado puede hoy:
- Escribir `createdBy = <su propio uid>` y volverse admin del board del grupo global.
- Escribir `memberIds = [su uid]`, expulsando a todos los demás miembros (incluido el admin legítimo `saulbrisniega`).
- Cambiar `name`/`inviteCode` del grupo.
- Colar cualquiera de los tres cambios anteriores en la misma llamada con la que se auto-afilia (self-join).

**Fix:** `createdBy` se vuelve inmutable vía `update` (transferir dueño requeriría un mecanismo dedicado que no existe hoy); `name`/`inviteCode` se agregan a la lista de campos "solo-creador" junto con `photoUrl`/`iconId`; `memberIds` solo puede crecer en +1 elemento (self-join) y nunca puede perder miembros existentes, salvo que quien escriba sea el creador (deja abierta la puerta a un futuro "expulsar miembro" hecho por el admin).

### Detalle — hallazgo #2

`weeks/{weekId}` (el documento con `games[]`, cacheado por `ScheduleRepositoryImpl.cacheGamesToFirestore` — **este es un write legítimo del cliente, no se puede bloquear con `if false`** sin romper el cacheo de horarios para todos los grupos) tiene `allow write` abierto a cualquier miembro, sin restringir qué campos puede tocar. Cualquier usuario podría sobrescribir el documento con contenido arbitrario, no solo `games[]`.

**Fix:** restringir el `write` a que el documento resultante solo contenga la clave `games` (`request.resource.data.keys().hasOnly(['games'])`), igual que el shape que ya escribe `ScheduleRepositoryImpl`. Esto no valida que el *contenido* de `games` sea real (eso requeriría validación server-side, fuera de alcance de esta corrección), pero cierra la posibilidad de inyectar campos arbitrarios o reemplazar el documento con basura fuera de forma.

### Detalle — hallazgo #3 (resuelto)

`UserRepositoryImpl.upsertAndResolveRole` desacopla el reintento de afiliación al grupo global de `isNewUser`: ahora, en cada login, si el doc del usuario no tiene `globalGroupJoined == true`, se vuelve a intentar `ensureGlobalGroupMembership` (que ahora retorna `Boolean` en vez de `Unit`), y solo se marca `globalGroupJoined = true` cuando el intento realmente tiene éxito (arrayUnion + Cloud Function ambos sin excepción). Como `arrayUnion` y `ensureGlobalStanding` son idempotentes, reintentar tras un fallo parcial es seguro. No se tocó `firestore.rules` — el campo nuevo cae dentro de "cualquier otro campo del dueño", ya permitido.

### Detalle — hallazgo #4 (resuelto parcialmente)

`seedGlobalStanding` ahora hace el check-then-set dentro de una transacción de Firestore (`db.runTransaction`), cerrando la mitad de la carrera que le correspondía a esta función. **Queda una carrera más amplia sin cerrar**: `scoreGroupForWeek` (en `scoring.ts`) también hace un `get()` + `batch.set()` no transaccional sobre el mismo documento de standing, así que un `batch.set()` de scoring que ocurra justo después de que la transacción de `seedGlobalStanding` ya confirmó "no existe" podría seguir pisando el `{ totalPoints: 0 }` recién sembrado. Cerrar esto por completo requeriría convertir la escritura de standings dentro de `scoreGroupForWeek` (que itera sobre múltiples usuarios en un solo `batch`) también a transaccional — un cambio más grande a una función de scoring ya en producción, fuera del alcance acordado para esta corrección. Se deja documentado aquí para decidir después si vale la pena.

### Detalle — hallazgo #5 (resuelto)

Decisión: archivar, no borrar. `deactivateInactiveUsers` ya no hace `.delete()` sobre el standing — lo marca `hidden: true`/`hiddenAt`. Nueva función `reactivateUser(uid)` (`functions/src/inactivity.ts`) y Cloud Function callable `reactivateAccount` (`functions/src/index.ts`) ponen `isActive=true`, limpian `disabledAt`, y des-ocultan (`hidden` se borra del doc) los standings archivados en todos los grupos del usuario — restaurando `totalPoints`/`weeklyBreakdown` intactos. El cliente (`UserRepositoryImpl.upsertAndResolveRole`) llama a `reactivateAccount` en cada login mientras `isActive` siga `false`; ambos lados son idempotentes, así que reintentar es seguro. `FirebaseLeaderboardDataSource` filtra client-side los docs con `hidden == true` (no se usó una query `hidden != true` en Firestore porque excluiría también los docs normales que nunca tuvieron el campo seteado). No se tocó `firestore.rules` — `standings` ya tenía `allow write: if false` para clientes.

### Detalle — hallazgo #6 (documentado como PR futuro, no implementado)

Alcance real descubierto al inventariar el código: `memberIds` no es exclusivo del grupo global — está incrustado en 15 puntos de `firestore.rules`, la query principal de "mis grupos" (`whereArrayContains`), 3 Cloud Functions (`scoring.ts`, `inactivity.ts`, `accountDeletion.ts`), 2 scripts de backfill, y 6 suites de test. Migrar a subcolección (`groups/{groupId}/members/{uid}`) es la solución correcta a largo plazo, pero es del tamaño de un PR completo — reescribe el modelo de membresía de **todos** los grupos existentes (no solo el global) y requiere una migración de datos en producción, irreversible una vez apagado el camino viejo. Se agregó como PR-24 al roadmap de `CLAUDE.md`/`AGENTS.md` (ver esa sección para el diseño detallado) en vez de implementarse ad hoc dentro de esta corrección.

### Pendiente de deploy

El fix del hallazgo #4 vive en `functions/src/globalGroup.ts` — a diferencia de `firestore.rules`/`storage.rules` (Regla 6, deploy inmediato), este repo no tiene una regla equivalente de "deploy inmediato" para Cloud Functions, así que el cambio **no se ha desplegado todavía**: hace falta `firebase deploy --only functions` (o el script `npm run deploy` en `functions/`) antes de que `seedGlobalStanding` corra con la versión corregida en producción. El fix del hallazgo #3 vive en el cliente Android (`UserRepositoryImpl.kt`) y solo toma efecto cuando se publique una nueva versión de la app — no requiere ningún deploy de backend.
