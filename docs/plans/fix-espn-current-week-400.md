# Fix: HTTP 400 al abrir Picks (resolución de "semana actual" vía ESPN)

## Contexto

Bug reportado por el usuario: al dar tap en la tarjeta de un grupo, la pantalla de Picks se queda en el tab "WEEK 1" mostrando "HTTP 400" con un botón Retry que no arregla nada. Cambiar a otra semana y volver a Week 1 "arregla" el error para el resto de la sesión. Reportado el 2026-09-16, justo en el hueco entre que terminaron los partidos de la Semana 1 y antes de que arranque la Semana 2.

Reproducido en el emulador (`adb logcat` con la app corriendo):

```
GET https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?dates=20260915-20260921
<-- 400 https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?dates=20260915-20260921 (1544ms, unknown-length body)
```

### Root cause

`PickViewModel.init` (`presentation/picks/PickViewModel.kt:83-89`) llama una sola vez, para cualquier grupo real, a `resolveCurrentWeekAndLoad()` (líneas 96-116). Esa función pide a ESPN "qué semana es la actual" vía `getCurrentWeekGamesUseCase` → `ScheduleRepositoryImpl.getCurrentWeekGames` (`data/repository/ScheduleRepositoryImpl.kt:33-43`) → `espnApiService.getScoreboard(dates = currentNflWeekDatesParam())` — un query con `dates=YYYYMMDD-YYYYMMDD` (**rango**), calculado en `EspnDateRange.kt:21-34` como "el martes más reciente 00:00 UTC hasta el lunes siguiente", puramente en función de `System.currentTimeMillis()`.

Se confirmó **empíricamente, contra la API real de ESPN** (no solo leyendo código) que este endpoint rechaza con 400 **cualquier** valor de `dates` que sea un rango — sin importar qué rango, qué temporada, ni siquiera qué deporte — y que solo una fecha individual (`dates=YYYYMMDD`) devuelve 200. Ver "Evidencia" abajo. Esto significa que el bug **no es específico del hueco Semana 1 → Semana 2**: el mecanismo de rango está roto siempre, desde que se introdujo (commit `1c3620b`, 12 de agosto de 2026) — probablemente pasó inadvertido porque solo se dispara una vez por apertura en frío de un grupo, y cambiar de tab (que usa un endpoint distinto, ver abajo) "tapa" el síntoma visible por el resto de la sesión.

Por qué el tab que se ve seleccionado es "WEEK 1": `_selectedWeekIndex` se inicializa en `NflSeasonCalendar.DEFAULT_INDEX` (línea 66, Semana 1 regular) en la declaración del `StateFlow` — no es que se esté consultando "semana 1" con fechas equivocadas; es que el resolver de "cuál es la semana actual" revienta (va al `catch` de la línea 112-114 y deja `PickUiState.Error`) antes de llegar a la línea 106 donde reasignaría `_selectedWeekIndex` al valor correcto. El tab por default simplemente nunca se actualiza.

Por qué cambiar de tab y volver "arregla" el error: `onWeekSelected` (línea 126) → `loadWeek` (línea 151) siempre usa `getGamesForWeekUseCase(seasonType, weekNumber)` → `ScheduleRepositoryImpl.getGamesForWeek` (línea 50-51) → `espnApiService.getScoreboardForWeek(seasontype, week)` — un endpoint **completamente distinto**, con query explícito (`seasontype=`/`week=`), que nunca pasa por `currentNflWeekDatesParam()`/`dates=`. Ese camino siempre ha funcionado y sobrescribe el estado de error con un `Success` cacheado (`weekCache`, línea 197) — no repara nada del resolver roto, solo lo vuelve invisible.

### Evidencia (reproducida, no solo inferida)

`adb logcat` (emulador, app real corriendo) confirmó el request exacto que falla. Luego, contra la API real de ESPN (`curl`):

| Query | Resultado |
|---|---|
| `dates=20260915-20260921` (el que falla en la app) | 400 — `{"code":400,"message":"Failed to get events endpoint."}` |
| `dates=20260901-20260907` (rango de una semana histórica, ya jugada) | 400 |
| `dates=20250905-20250908`, `dates=20250101-20250107` (temporada/fechas totalmente distintas) | 400 |
| `dates=2026091520260921` (rango concatenado sin guion) | 400 |
| `dates=20260916,20260917` (lista separada por coma) | 400 |
| `/basketball/nba/scoreboard?dates=20250101-20250107` (mismo formato, otro deporte) | 400 |
| `dates=20260915` (fecha única) | **200** |
| `dates=20260916` (fecha única) | **200** — body incluye `leagues[0].calendar` completo |

La tabla confirma que **ningún** formato de rango funciona hoy contra este endpoint — es un comportamiento del backend de ESPN, no algo específico a esta ventana de tiempo, a NFL, ni al formato exacto de guion que usa el código actual.

El body de una consulta de fecha única (200) trae esto, sin importar que ese día en particular no tenga partidos:

```json
"leagues": [{
  "calendar": [
    { "label": "Preseason", "value": "1", "entries": [
        { "label": "Hall of Fame Weekend", "value": "1", "startDate": "2026-08-06T07:00Z", "endDate": "2026-08-13T06:59Z" },
        { "label": "Preseason Week 1", "value": "2", "startDate": "2026-08-13T07:00Z", "endDate": "2026-08-20T06:59Z" },
        ...
    ]},
    { "label": "Regular Season", "value": "2", "entries": [
        { "label": "Week 1", "value": "1", "startDate": "2026-09-06T07:00Z", "endDate": "2026-09-16T06:59Z" },
        { "label": "Week 2", "value": "2", "startDate": "2026-09-16T07:00Z", "endDate": "2026-09-23T06:59Z" },
        ... (hasta Week 18)
    ]},
    { "label": "Postseason", "value": "3", "entries": [
        { "label": "Wild Card", "value": "1", ... },
        { "label": "Divisional Round", "value": "2", ... },
        { "label": "Conference Championship", "value": "3", ... },
        { "label": "Pro Bowl", "value": "4", ... },
        { "label": "Super Bowl", "value": "5", ... }
    ]}
  ]
}]
```

Esta tabla es exactamente lo que se necesita para resolver "en qué semana estamos ahorita" **localmente**, con una sola consulta segura (fecha única, siempre 200), sin depender de que ESPN acepte un rango, y sin hardcodear fechas de temporada en el cliente — que es justo lo que `SeasonWeek.kt` (comentario en la línea 4-9) ya evita a propósito porque el año de postemporada puede caer en el año calendario siguiente y una tabla local podría desincronizarse.

`EspnCalendarGroup.value` ("1"/"2"/"3") y `EspnCalendarEntry.value` (número de semana crudo, incluyendo que en pretemporada la entrada `value=1` es el Hall of Fame Game) calzan exactamente con la convención ya usada por `EspnSeason.type` / `EspnApiService.getScoreboardForWeek` / `SeasonWeek.isHallOfFame` — no hay mapeo nuevo que inventar ni ambigüedad que resolver.

### Diseño del fix

Reemplazar el mecanismo de "resolver semana actual": en vez de `getScoreboard(dates=<rango>)`, hacer `getScoreboard(dates=<hoy, fecha única>)` (seguro, siempre 200), leer `leagues[0].calendar` de esa respuesta, resolver localmente qué entrada contiene "ahora", y con el `seasonType`+`week` ya resueltos, delegar al endpoint **ya confiable** `getScoreboardForWeek(seasontype, week)` para traer los juegos reales de esa semana. Nunca se leen los `events` de la consulta de fecha única (pueden venir vacíos en días sin partidos — ese es justo el bug original, anterior a `currentNflWeekDatesParam`, que forzó a introducir el rango en primer lugar; ver comentario en `EspnApiService.kt:8-11`). `currentNflWeekDatesParam()` y el query de rango se eliminan por completo — no queda ningún caller de un `dates=<rango>`, y dejarlos muertos en el código sería un riesgo de que algo los vuelva a usar y reintroduzca el 400.

Los casos que `PickViewModel` ya maneja siguen funcionando sin cambios: si "ahora" cae fuera de cualquier entrada del calendario (antes de pretemporada, después del Super Bowl) o cae en una semana que `NflSeasonCalendar` excluye a propósito (Postemporada semana 4 = bye de Pro Bowl, sin tab), el repositorio devuelve lista vacía, `resolvedIndex` da `-1`, y el `else` de la línea 108-111 cae al tab por default — comportamiento intencional ya existente, no se toca.

---

## Paso 1 — DTOs de calendario ESPN + resolver puro (sin wiring todavía)

Paso aislado y 100% Kotlin puro — no toca `PickViewModel` ni `ScheduleRepositoryImpl`, así que es compilable y testeable de forma independiente.

**Archivos:**
- `app/src/main/java/com/softeen/nflocospicks/data/remote/espn/EspnDtos.kt` (editar)
- `app/src/main/java/com/softeen/nflocospicks/data/remote/espn/EspnMapper.kt` (editar: exponer el formateador de fecha existente)
- `app/src/main/java/com/softeen/nflocospicks/data/remote/espn/EspnDateRange.kt` → **renombrar/reescribir** a `EspnCurrentWeekResolver.kt`
- `app/src/test/java/com/softeen/nflocospicks/data/remote/espn/EspnDateRangeTest.kt` → **renombrar/reescribir** a `EspnCurrentWeekResolverTest.kt`

**`EspnDtos.kt`** — agregar `leagues` (opcional, default `null` para no romper ningún fixture/test existente que construye `EspnScoreboardResponse` sin ese campo) y los DTOs del calendario:

```kotlin
data class EspnScoreboardResponse(
    val events: List<EspnEvent>,
    val leagues: List<EspnLeague>? = null
)

// ...

data class EspnLeague(
    val calendar: List<EspnCalendarGroup>? = null
)

data class EspnCalendarGroup(
    val value: String,                       // "1"=pre, "2"=regular, "3"=post — mismo valor que EspnSeason.type
    val entries: List<EspnCalendarEntry>
)

data class EspnCalendarEntry(
    val value: String,                       // número de semana crudo, mismo valor que EspnWeek.number
    val startDate: String,                   // ISO 8601 UTC, mismo formato que EspnEvent.date ("2026-09-16T07:00Z")
    val endDate: String
)
```

**`EspnMapper.kt`** — el `SimpleDateFormat` privado de la línea 11 ya parsea exactamente este formato (`"yyyy-MM-dd'T'HH:mm'Z'"`, sin segundos); en vez de duplicarlo, exponerlo para reusarlo. **`SimpleDateFormat` no es thread-safe** (hallazgo de la revisión de Antigravity, ver "Cross-review" abajo) — hoy ya es un riesgo latente porque `toDomain()`/`toGame()` puede correr en distintas coroutines/threads (auto-refresh + carga manual de otra semana al mismo tiempo), y este paso lo vuelve más visible al agregar un tercer punto de uso (parseo del calendario). Se resuelve con `ThreadLocal` en vez de un solo `val` compartido, sin cambiar la API en los call sites (`espnDateFormat.parse(...)` sigue igual en todo el archivo):

```kotlin
// antes: private val espnDateFormat = SimpleDateFormat(...).apply { ... }
private val espnDateFormatThreadLocal = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
internal val espnDateFormat: SimpleDateFormat get() = espnDateFormatThreadLocal.get()
```

(`internal` en vez de `private`: mismo módulo, visible para el nuevo archivo del mismo paquete. El resto de `EspnMapper.kt` no cambia — `espnDateFormat.parse(date)` sigue funcionando igual porque `espnDateFormat` sigue resolviendo a un `SimpleDateFormat`, ahora uno por thread.)

**`EspnCurrentWeekResolver.kt`** (reemplaza el contenido completo de `EspnDateRange.kt`):

```kotlin
package com.softeen.nflocospicks.data.remote.espn

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val paramDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

/**
 * Fecha de "hoy" en UTC, formato "YYYYMMDD" — único valor de `dates` que
 * ESPN acepta de forma confiable (ver docs/plans/fix-espn-current-week-400.md):
 * cualquier rango "YYYYMMDD-YYYYMMDD" devuelve 400 sin importar la fecha.
 */
fun todayDateParam(nowMillis: Long = System.currentTimeMillis()): String =
    paramDateFormat.format(java.util.Date(nowMillis))

/**
 * Resuelve (seasonType crudo de ESPN, número de semana crudo) para el
 * instante [nowMillis], usando leagues[0].calendar de una respuesta de
 * scoreboard cualquiera (incluida una de fecha única sin eventos). Entradas
 * mal formadas se ignoran; null si ninguna entrada contiene [nowMillis]
 * (fuera de temporada, antes de pretemporada o después del Super Bowl).
 */
fun List<EspnLeague>?.resolveCurrentSeasonWeek(nowMillis: Long = System.currentTimeMillis()): Pair<Int, Int>? {
    this.orEmpty().forEach { league ->
        league.calendar.orEmpty().forEach { group ->
            val seasonType = group.value.toIntOrNull()
            if (seasonType != null) {
                group.entries.forEach { entry ->
                    val week = entry.value.toIntOrNull()
                    val start = runCatching { espnDateFormat.parse(entry.startDate)?.time }.getOrNull()
                    // endDate llega sin segundos ("...T06:59Z") y la siguiente entrada
                    // arranca en "...T07:00Z" — literalmente son 60s aparte, no 0. Sin
                    // este +59999ms, el segundo 06:59:00.001-06:59:59.999 no cae dentro
                    // de NINGUNA entrada (hallazgo de la revisión de Antigravity, ver
                    // "Cross-review" abajo). Normalizamos el end a "fin de ese minuto".
                    val end = runCatching { espnDateFormat.parse(entry.endDate)?.time?.plus(59_999L) }.getOrNull()
                    if (week != null && start != null && end != null && nowMillis in start..end) {
                        return seasonType to week
                    }
                }
            }
        }
    }
    return null
}
```

**Tests (`EspnCurrentWeekResolverTest.kt`)** — reemplazan los de `EspnDateRangeTest.kt` (esos probaban `currentNflWeekDatesParam`, que ya no existe):

- `todayDateParam` con un `nowMillis` fijo → arma el fixture con dos-tres `EspnLeague`/`EspnCalendarGroup`/`EspnCalendarEntry` (basado en la tabla real de Evidencia) y verifica el string `YYYYMMDD` exacto.
- `resolveCurrentSeasonWeek`:
  - "ahora" a mitad de la ventana de Week 2 (regular) → retorna `2 to 2`.
  - "ahora" exactamente en el límite `startDate` de una entrada → esa entrada gana (inclusive).
  - "ahora" exactamente en el límite `endDate` de la entrada anterior → esa entrada gana, no la siguiente (evita depender de qué entrada se evalúa primero si los límites se llegaran a traslapar).
  - "ahora" en el segundo 06:59:30 (dentro del minuto truncado de un `endDate` tipo "...T06:59Z", antes del `startDate` "...T07:00Z" de la siguiente entrada) → debe resolver a la entrada que termina en `06:59Z`, no a `null`. Cubre directamente el hallazgo de la revisión (ver "Cross-review" abajo): sin el `+59_999L`, este segundo cae en un hueco entre dos entradas.
  - "ahora" antes de la primera entrada de pretemporada → `null`.
  - "ahora" después de la última entrada de postemporada (Super Bowl) → `null`.
  - `leagues = null` o `calendar = null` → `null`, sin excepción.
  - entrada con `value` no numérico → se ignora sin excepción (usa `runCatching`/`toIntOrNull`, no debería lanzar, pero el test documenta el contrato).

**Verificación:** `./gradlew test --tests "com.softeen.nflocospicks.data.remote.espn.EspnCurrentWeekResolverTest"` y `./gradlew assembleDebug` (nada más en el repo referencia todavía estas funciones ni el DTO nuevo, así que esto compila y pasa de forma aislada).

---

## Paso 2 — Wiring en `ScheduleRepositoryImpl` + actualizar tests existentes

**Archivos:**
- `app/src/main/java/com/softeen/nflocospicks/data/remote/espn/EspnApiService.kt` (editar KDoc; la firma de `getScoreboard` no cambia, solo cómo se le llama)
- `app/src/main/java/com/softeen/nflocospicks/data/repository/ScheduleRepositoryImpl.kt` (editar `getCurrentWeekGames`)
- `app/src/test/java/com/softeen/nflocospicks/data/repository/ScheduleRepositoryImplTest.kt` (editar los tests de `getCurrentWeekGames`)

**`ScheduleRepositoryImpl.kt`** — reemplazar el cuerpo de `getCurrentWeekGames`:

```kotlin
override suspend fun getCurrentWeekGames(groupId: String): List<Game> {
    val calendarResponse = espnApiService.getScoreboard(dates = todayDateParam())
    val (seasonType, weekNumber) = calendarResponse.leagues.resolveCurrentSeasonWeek()
        ?: return emptyList()

    val games = espnApiService.getScoreboardForWeek(seasonType, weekNumber)
        .toDomain()
        .withDebugKickoffOffset()

    if (games.isNotEmpty()) {
        cacheGamesToFirestore(groupId, games)
    }

    return games
}
```

(imports: quitar `currentNflWeekDatesParam`, agregar `todayDateParam`/`resolveCurrentSeasonWeek`.)

**`EspnApiService.kt`** — el KDoc de `getScoreboard` (líneas 7-11) ya no aplica ("dates es obligatorio ... usar currentNflWeekDatesParam"); actualizar a algo como:

```kotlin
/**
 * Scoreboard de una fecha específica ("dates" es una sola fecha YYYYMMDD —
 * ESPN rechaza con 400 cualquier valor de rango, ver
 * docs/plans/fix-espn-current-week-400.md). Se usa para dos cosas: traer los
 * juegos de un día puntual, y leer `leagues[0].calendar` de la respuesta
 * (presente incluso sin eventos ese día) para resolver la semana actual —
 * ver [resolveCurrentSeasonWeek].
 */
@GET("scoreboard")
suspend fun getScoreboard(@Query("dates") dates: String): EspnScoreboardResponse
```

**`ScheduleRepositoryImplTest.kt`** — las 4 pruebas actuales de `getCurrentWeekGames` (líneas 81-156) solo mockean `api.getScoreboard`; ahora el flujo hace **dos** llamadas (`getScoreboard` para el calendario, `getScoreboardForWeek` para los juegos), así que cada una necesita mockear ambas:

- Agregar un helper `calendarResponse(seasonType: String, week: String, start: String, end: String)` que arme un `EspnScoreboardResponse(events = emptyList(), leagues = listOf(...))` reutilizable entre pruebas, con una ventana que contenga `System.currentTimeMillis()` (o inyectar `nowMillis` si se prefiere hacer el resolver más testeable desde el repo — evaluar en el paso si vale la pena exponerlo, no es obligatorio).
- `getCurrentWeekGames applies the debug kickoff offset...`, `...caches the games...`, `...still returns games when Firestore write fails`: cambiar `coEvery { api.getScoreboard(any()) }` para devolver el calendario (no los eventos), y agregar `coEvery { api.getScoreboardForWeek(any(), any()) }` devolviendo los `EspnEvent` que antes iban en el mock de `getScoreboard`.
- `getCurrentWeekGames does not touch Firestore when the schedule is empty`: dos variantes ahora, no una — (a) el calendario no resuelve ninguna semana (`leagues = null` o `nowMillis` fuera de todas las entradas) → nunca debería llamar a `getScoreboardForWeek`; (b) el calendario sí resuelve una semana pero `getScoreboardForWeek` devuelve `events = emptyList()` → no debe cachear en Firestore. Verificar `coVerify(exactly = 0) { api.getScoreboardForWeek(any(), any()) }` para el caso (a).
- Nuevo test: `getCurrentWeekGames delegates to getScoreboardForWeek with the seasonType/week resolved from the calendar` — `coVerify` explícito de que se llamó `getScoreboardForWeek(<seasonType resuelto>, <week resuelto>)`.

**Verificación:** `./gradlew test --tests "com.softeen.nflocospicks.data.repository.ScheduleRepositoryImplTest"` y `./gradlew assembleDebug`. Este paso ya deja resuelto el bug reportado a nivel de datos — `getCurrentWeekGamesUseCase` ya no puede lanzar por el 400 de rango.

---

## Paso 3 (defensivo, independiente) — Fallback en `PickViewModel` si `resolveCurrentWeekAndLoad` falla por otra causa

El Paso 2 elimina la causa raíz confirmada, pero `resolveCurrentWeekAndLoad()` (`PickViewModel.kt:96-116`) sigue teniendo un `catch` que deja al usuario en un callejón sin salida (`PickUiState.Error`, sin reintento automático, sin forma de ver ninguna semana) ante *cualquier* falla — por ejemplo, sin conexión al abrir la app. El branch hermano de "ESPN no devuelve semana actual" (línea 108-111) ya sabe caer con gracia al tab por default; este paso hace que el `catch` haga lo mismo para fallas de red/transitorias, en vez de mostrar un error duro solo por el paso de "detectar cuál es la semana actual" (que es un detalle de UX, no algo que deba bloquear ver la app).

**Archivos:**
- `app/src/main/java/com/softeen/nflocospicks/presentation/picks/PickViewModel.kt`
- `app/src/test/java/com/softeen/nflocospicks/presentation/picks/PickViewModelTest.kt`

**`PickViewModel.kt`** — cambiar el `catch` de `resolveCurrentWeekAndLoad`:

```kotlin
} catch (e: Exception) {
    Timber.w(e, "No se pudo resolver la semana actual, cae al tab por default")
    _selectedWeekIndex.value = NflSeasonCalendar.DEFAULT_INDEX
    loadWeek(NflSeasonCalendar.DEFAULT_INDEX, showLoading = true)
}
```

Si `loadWeek` también falla (offline real), su propio `catch` (línea 163-173) sigue mostrando `PickUiState.Error` como ya hace hoy — este cambio no oculta fallas reales, solo evita que un fallo específico del paso de "detectar la semana actual" bloquee todo antes de intentar la ruta confiable.

**Test nuevo** (junto a `init falls back to the default tab when ESPN returns no games`, línea 234-245, mismo patrón): `init falls back to the default tab when resolving the current week throws` — mockear `getGamesUseCase` para que lance (`coEvery { getGamesUseCase(any()) } throws RuntimeException(...)`), mockear `getGamesForWeekUseCase(SeasonType.REGULAR, 1)` con datos válidos, y verificar `vm.uiState.value is PickUiState.Success` (no `Error`) y `vm.selectedWeekIndex.value == NflSeasonCalendar.DEFAULT_INDEX`.

**Verificación:** `./gradlew test --tests "com.softeen.nflocospicks.presentation.picks.PickViewModelTest"` y `./gradlew assembleDebug`.

---

## Paso 4 — Verificación manual en emulador (requiere autorización explícita del usuario antes de correr, Regla 7 / CLAUDE.md global)

No es parte del build/test automatizado — es la confirmación de que el bug reportado ya no se reproduce en la app real:

1. Reinstalar/reiniciar la app en el emulador (proceso nuevo → `PickViewModel.init` corre desde cero).
2. Abrir el grupo real desde `GroupsScreen` (el mismo flujo de la captura original).
3. Confirmar que **no** aparece "HTTP 400" y que el tab seleccionado automáticamente es la semana que corresponde a "ahora" (Semana 2, dado el estado real de la temporada), no Semana 1 por default.
4. Confirmar en logcat (`adb logcat | grep OkHttp`) que la única llamada a `getScoreboard` usa una fecha única (`dates=YYYYMMDD` sin guion) y devuelve 200.

---

## Cross-review (Regla 10)

Plan escrito por Claude (Sonnet 5) en esta sesión — investigación de causa raíz hecha con dos subagentes de exploración de código en paralelo + reproducción real en el emulador (logcat) + pruebas directas contra la API pública de ESPN (`curl`), no solo lectura de código. Pendiente: revisión de este plan por Antigravity antes de implementar (Regla 10 — el autor del plan no se revisa a sí mismo).

Comando a correr (usuario o headless):
```
agy.exe --mode plan --dangerously-skip-permissions -p "Review the plan at docs/plans/fix-espn-current-week-400.md. Look for gaps, risks, missing edge cases, and omissions given this codebase. Do not write code, only report findings." --model gemini-3.1-pro-high --effort high
```

**Nota de proceso a vigilar** (ver `docs/plans/pick-result-indicator.md`, incidente registrado ahí): una corrida anterior de `agy.exe --mode plan --dangerously-skip-permissions` en este repo no se quedó en solo reportar — escribió código real en el working tree pese a `--mode plan` y a la instrucción explícita de solo reportar. Correr `git status`/`git diff` inmediatamente después de esta revisión y revertir con `git restore` cualquier cambio no solicitado antes de continuar.

---

**Revisor:** Antigravity (`agy.exe --mode plan --model gemini-3.1-pro-high --effort high`), 2026-09-16. Autor del plan: Claude Sonnet 5 (Regla 10 — revisor ≠ autor). Esta corrida sí se quedó en solo lectura — `git status` confirmado limpio antes y después (solo este archivo de plan, sin cambios de código).

Hallazgos y disposición (verificados contra el código real, no aplicados a ciegas):

1. **`SimpleDateFormat` no es thread-safe** (crítico) — **aplicado**. El Paso 1 ya tocaba la declaración de `espnDateFormat` (la volvía `internal` para reusarla); el hallazgo es válido — es un `val` compartido a nivel de archivo, y `toDomain()`/el nuevo resolver pueden correr en threads distintos si dos corrutinas (auto-refresh + carga manual) parsean fechas al mismo tiempo. Cambiado a `ThreadLocal<SimpleDateFormat>` sin tocar ningún call site existente (ver Paso 1 arriba, ya actualizado con el fix).
2. **Hueco de 59 segundos en los límites de semana** (alto) — **aplicado**. Confirmado con los datos reales de ESPN capturados en "Evidencia": `endDate` de una semana y `startDate` de la siguiente están literalmente 60s aparte (`...T06:59Z` → `...T07:00Z`, no `07:00` → `07:00`), porque ESPN trunca los segundos al serializar. Con comparación inclusiva simple, el rango `06:59:00.001`–`06:59:59.999` no caía en ninguna entrada. Corregido normalizando `endDate` a "fin de ese minuto" (`+ 59_999L`) antes de comparar — ver Paso 1, ya actualizado, con caso de prueba explícito para ese segundo.
3. **Doble falla de red si el dispositivo está sin conexión** (medio) — **no se cambia el diseño, documentado como trade-off aceptado**. Si `resolveCurrentWeekAndLoad()` falla por estar offline, el Paso 3 intenta `loadWeek(DEFAULT_INDEX)` como fallback, que también fallará offline — el usuario ve el error final ~2x más lento que antes (dos timeouts en vez de uno) en vez de fallar rápido en el primer intento. Distinguir "esto fue un error de conectividad, no reintentes" de "esto fue un error de ESPN, sí reintentes con el camino confiable" requeriría inspeccionar el tipo de excepción (`IOException` vs `HttpException`, etc.) — complejidad extra para un caso límite (approximadamente 2 timeouts en vez de 1 mientras el usuario ya está offline) que no cambia el resultado final (sigue mostrando el error), y no forma parte del bug reportado. Si el usuario quiere optimizar esto después, es un cambio aislado y de bajo riesgo sobre el `catch` del Paso 3.
4. **`currentWeekIndex == null` habilita el botón de Sync en todos los tabs** (medio) — **verificado, no es un riesgo nuevo, no requiere cambio**. Se confirmó en `PickScreen.kt:102` que `canSync = currentWeekIndex == null || selectedWeekIndex == currentWeekIndex` ya es el comportamiento actual, en producción, para el branch de temporada muerta ya existente (`resolvedIndex == -1`, línea 108-111 de `PickViewModel.kt`, que tampoco asigna `_currentWeekIndex` y lo deja en `null`) — y ese branch ya tiene test dedicado (`init falls back to the default tab when ESPN returns no games`, `PickViewModelTest.kt:234-245`) que confirma `assertNull(vm.currentWeekIndex.value)` como comportamiento esperado, no accidental. El Paso 3 solo extiende el mismo patrón ya probado a un trigger distinto (excepción en vez de índice `-1`) — no introduce un estado nuevo ni cambia la semántica de `canSync`.
