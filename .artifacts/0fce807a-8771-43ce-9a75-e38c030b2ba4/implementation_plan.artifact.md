# Plan de Enriquecimiento y Cobertura de Analytics

Este plan implementa la estrategia de analíticas detallada en `docs/plans/analytics-enrichment.md`, dividida en tres fases (PRs) para mejorar la observabilidad del comportamiento del usuario sin comprometer datos PII.

## User Review Required

> [!IMPORTANT]
> **Privacidad (No-PII):** Se implementará el uso de `Firebase User-ID` para rastrear usuarios de forma segura. Bajo ninguna circunstancia se enviarán nombres, emails o datos sensibles en los parámetros de los eventos.

> [!NOTE]
> **Cambio de Firma en Navegación:** El evento `group_opened` ahora requiere un parámetro `source`. Esto implica cambios menores en `GroupsScreen.kt` y sus componentes internos.

## Proposed Changes

El trabajo se divide en los tres PRs especificados en el plan maestro.

---

### PR-21: Infraestructura, User-ID y Enriquecimiento

Fase enfocada en la base técnica y en dotar de contexto a los eventos que ya existen.

#### [MODIFY] [AppLogger.kt](file:///C:/Users/brici/code/NFLocosPick/app/src/main/java/com/softeen/nflocospicks/analytics/AppLogger.kt)
- Añadir método `setUserId(uid: String?)`.

#### [MODIFY] [AppEvent.kt](file:///C:/Users/brici/code/NFLocosPick/app/src/main/java/com/softeen/nflocospicks/analytics/AppEvent.kt)
- Actualizar las firmas de los 16 eventos existentes con los nuevos parámetros (group_name, team_name, source, etc.).

#### [MODIFY] [AuthViewModel.kt](file:///C:/Users/brici/code/NFLocosPick/app/src/main/java/com/softeen/nflocospicks/presentation/auth/AuthViewModel.kt)
- Integrar `logger.setUserId(uid)` en `watchRole`.
- Gestionar limpieza de ID en `signOut` y `deleteAccount`.
- Registrar `GlobalGroupAutoJoined` (adelanto táctico de PR-22 por proximidad lógica).

#### [NEW] [ScreenTracking](file:///C:/Users/brici/code/NFLocosPick/app/src/main/java/com/softeen/nflocospicks/presentation/navigation/)
- `ScreenTrackingViewModel.kt`: Puente para loguear desde Compose.
- `ScreenNames.kt`: Mapeo de rutas a nombres legibles.
- `TrackScreenView.kt`: Efecto Compose para detección automática de pantallas.

#### [MODIFY] [NflTeams.kt](file:///C:/Users/brici/code/NFLocosPick/app/src/main/java/com/softeen/nflocospicks/presentation/common/NflTeams.kt)
- Añadir helper `nflTeamNameByAbbr`.

#### [MODIFY] [ViewModels & Screens (PR-21)](file:///C:/Users/brici/code/NFLocosPick/app/src/main/java/com/softeen/nflocospicks/presentation/)
- **GroupViewModel/GroupsScreen**: Enriquecer `group_created`, `group_joined`, `group_opened` (con source) y `scoring_completed`.
- **PickViewModel**: Enriquecer `pick_submitted`.
- **Leaderboard/History ViewModels**: Inyectar `GroupRepository` y enriquecer `leaderboard_viewed`/`pick_history_viewed`.
- **BoardViewModel/UiState**: Añadir `groupName` al estado y enriquecer eventos de chat/announcement.
- **SettingsViewModel**: Enriquecer `favorite_team_set`.

---

### PR-22: Cobertura de Acciones no Rastreadas

Fase enfocada en instrumentar las ~14 acciones que hoy no generan eventos.

#### [MODIFY] [AppEvent.kt](file:///C:/Users/brici/code/NFLocosPick/app/src/main/java/com/softeen/nflocospicks/analytics/AppEvent.kt)
- Añadir definiciones para los eventos 18 al 33 de la tabla del plan.

#### [MODIFY] [ViewModels (PR-22)](file:///C:/Users/brici/code/NFLocosPick/app/src/main/java/com/softeen/nflocospicks/presentation/)
- **PickViewModel**: Instrumentar `week_tab_selected`, `pick_refresh` y `pick_auto_refresh_failed`.
- **Leaderboard/History**: Instrumentar `leaderboard_tab_selected` y `history_week_toggled`.
- **Settings**: Instrumentar cambios de escala de fuente e iconos.
- **UserManagement/Account/ChangePassword**: Inyectar `AppLogger` e instrumentar cambios de rol, guardado de perfil, carga de fotos, vinculación de cuenta y cambio de password.

---

### PR-23: Documentación y Consola GA4

#### [MODIFY] [CLAUDE.md](file:///C:/Users/brici/code/NFLocosPick/CLAUDE.md)
- Documentar el inventario final de eventos y parámetros en la sección de constraints.

## Verification Plan

### Automated Tests
- Ejecutar `./gradlew test` para verificar que las inyecciones de Hilt y la lógica de los ViewModels no se rompan.
- Actualizar tests unitarios existentes para verificar que los eventos se envían con los parámetros correctos usando `verify { logger.logEvent(match { ... }) }`.

### Manual Verification
- Inspección de Logcat filtrando por `[Analytics]` para confirmar el flujo de eventos y el `setUserId`.
- (Opcional) Uso de `adb shell setprop debug.firebase.analytics.app com.softeen.nflocospicks` para ver datos en el DebugView de Firebase.
