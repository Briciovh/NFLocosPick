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
