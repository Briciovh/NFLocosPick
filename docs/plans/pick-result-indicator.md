# Pick Result Indicator (Picks Screen)

## Context

Feedback de usuarios: después de que un juego termina (`GameStatus.FINAL`), la pantalla de Picks no da ninguna señal visual de si el pick del usuario fue correcto — solo se ve el marcador crudo, y hay que hacer el cálculo mentalmente comparando equipos y puntaje. Se pidió agregar una señal visual de acierto/error sobre la card del equipo elegido.

Propuesta original del usuario: outline verde/rojo alrededor de la card elegida. Al explorar el código se encontraron dos datos que cambiaron el diseño:

1. El estado "seleccionado" hoy **no es un outline** — es un fill sólido dorado (`appColors.primary`) en `TeamPickButton` (`PickScreen.kt:412-448`). Poner un outline de color sobre un botón ya relleno de dorado se ve saturado y confuso.
2. Ya existe un precedente idéntico en la app: `HistoryScreen.kt:298-314` (`GamePickRow`) ya resuelve exactamente este problema con un badge de ícono (Check/Close) dentro de un círculo con tinte verde/rojo — pero usa un verde hardcoded (`Color(0xFF2E7D32)`) y `contentDescription = null` (sin accesibilidad real).

Decisiones ya confirmadas con el usuario (no reabrir):
1. **Ícono + color**, no outline — badge tipo "avatar badge" en una esquina de la card del equipo elegido, dejando el fill dorado de "seleccionado" intacto como señal aparte. Razón: rojo/verde solo-color es la combinación más problemática para daltonismo (deuteranopía/protanopía); ícono+color es más accesible y reutiliza el lenguaje visual ya establecido en History.
2. **Formalizar `success`/`error` en `AppColors`** (no hardcoded) — reutilizable entre Picks e History, seguible desde `LocalAppColors` como el resto del theme.
3. **Juego FINAL sin pick del usuario → no se muestra nada** (comportamiento actual, sin cambios).

Objetivo del cambio: agregar el badge en Picks, formalizar los colores en el theme, y (por consistencia, ver Paso 5) migrar el badge ya existente en History al mismo color/theme/accesibilidad — sin tocar nada fuera de ese scope.

---

## Resumen de diseño

- `success`/`error` se agregan como **campos con valor default** en `AppColors` (`SuccessGreen = 0xFF2ECC71`, `ErrorRed = 0xFFE53935` — más saturados que el verde 0x2E7D32 de History para que se vean bien sobre el fondo navy Blue Steel). Con defaults, ningún constructor existente de `AppColors` (`LocalAppColors`, `NflTeamColors.toAppColors()`) necesita cambiar.
- `GamePickItem.isCorrect: Boolean? = null` también con default — solo el constructor real (`PickViewModel.buildSuccess()`) necesita pasar el valor real; los otros 4 sitios (mock picks, previews, tests) compilan sin cambios y siguen sin mostrar badge.
- El badge se coloca dentro del `BoxWithConstraints` que ya envuelve `TeamLogo` (`PickScreen.kt:459-461`), alineado `Alignment.BottomEnd` — es el patrón clásico de "avatar badge", diff mínimo, sin colisión con los `Text` de arriba/abajo.
- El fondo del círculo del badge en Picks usa `appColors.onSurface.copy(alpha = 0.85f)` (casi opaco) en vez del `tint.copy(alpha = 0.15f)` de History, porque en Picks el badge siempre cae sobre el fill dorado de "seleccionado" — un tinte al 15% se lavaría contra el dorado. `onSurface` es blanco tanto en Blue Steel default como en todas las variantes por equipo, así que no es un literal hardcoded.
- Condición exacta para mostrar el badge en `TeamPickButton`: `item.isCorrect.takeIf { item.pickedTeam == <abbr de este botón> && game.status == GameStatus.FINAL }` — cubre las 3 condiciones (FINAL + este fue el pick + ya se puntuó) en una sola expresión, y si el pick aún no se puntuó (`isCorrect == null`) el badge se sigue ocultando.

---

## Paso 1 — Theme: colores `success`/`error` en `AppColors`

**Archivos:**
- `app/src/main/java/com/softeen/nflocospicks/presentation/theme/Color.kt`
- `app/src/main/java/com/softeen/nflocospicks/presentation/theme/AppColors.kt`

**`Color.kt`** — agregar debajo del bloque Blue Steel existente (no llevan prefijo `BS` porque son constantes semánticas, independientes del tema por equipo):
```kotlin
// ── Semantic feedback colors (constant across all team themes) ────────────────
val SuccessGreen = Color(0xFF2ECC71)   // pick correcto
val ErrorRed     = Color(0xFFE53935)   // pick incorrecto
```

**`AppColors.kt`** — agregar dos campos con default (así ningún constructor existente necesita tocarse):
```kotlin
data class AppColors(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val header: Color,
    val success: Color = SuccessGreen,   // pick correcto — constante, no varía por equipo
    val error: Color = ErrorRed          // pick incorrecto — constante, no varía por equipo
)
```
`Theme.kt` no se toca: el badge lee `appColors.success`/`error` directo vía `LocalAppColors`, no vía `MaterialTheme.colorScheme`.

**Verificación:** `./gradlew assembleDebug` — compila sin tocar ningún otro archivo (confirma que `NflTeamColors.kt` sigue compilando con los defaults).

---

## Paso 2 — Data plumbing: `GamePickItem.isCorrect`

**Archivos:**
- `app/src/main/java/com/softeen/nflocospicks/presentation/picks/PickUiState.kt`
- `app/src/main/java/com/softeen/nflocospicks/presentation/picks/PickViewModel.kt`
- `app/src/test/java/com/softeen/nflocospicks/presentation/picks/PickViewModelTest.kt`

**`PickUiState.kt`:**
```kotlin
data class GamePickItem(
    val game: Game,
    val pickedTeam: String?,   // null = sin pick aún
    val isLocked: Boolean,     // true cuando kickoffTime ya pasó
    val isCorrect: Boolean? = null   // null hasta que el juego sea FINAL y Pick se puntúe (ver Pick.isCorrect)
)
```

**`PickViewModel.buildSuccess()`** (único constructor con datos reales, ~línea 182-188):
```kotlin
val items = games.sortedBy { it.kickoffTime }.map { game ->
    GamePickItem(
        game       = game,
        pickedTeam = picks[game.id]?.pickedTeam,
        isCorrect  = picks[game.id]?.isCorrect,
        isLocked   = now >= game.kickoffTime || game.status != GameStatus.SCHEDULED
    )
}
```

**`observeMockPicks()` (~línea 225-253) — adición recomendada (hallazgo de cross-review, ver Cross-Review Log):** en vez de dejar `isCorrect` siempre en `null` en la ruta mock, calcularlo localmente cuando `simulateGamesStarted` está activo, comparando `homeScore`/`awayScore` simulados contra `pickedTeam`. Esto permite probar visualmente el badge desde la sesión mock (toggle "Simular juegos") sin necesitar emulador ni `@Preview` — encaja con la Regla 7 (no lanzar emulador sin permiso).
```kotlin
val items = games.map { game ->
    val picked = session.realUserPicks[game.id]
    val isCorrect = if (game.status == GameStatus.FINAL && picked != null) {
        if (game.homeScore != null && game.awayScore != null) {
            val homeWins = game.homeScore > game.awayScore
            val awayWins = game.awayScore > game.homeScore
            if (picked == game.homeTeamAbbr) homeWins
            else if (picked == game.awayTeamAbbr) awayWins
            else false
        } else null
    } else null

    GamePickItem(
        game       = game,
        pickedTeam = picked,
        isCorrect  = isCorrect,
        isLocked   = prefs.simulateGamesStarted
    )
}
```

**Otros 3 sitios de construcción — confirmados sin cambios:** `PreviewData.kt` (2 fakes), `PickScreenTest.kt` androidTest (2 sitios).

**Test nuevo en `PickViewModelTest.kt`** (junto a `after init, success state contains one GamePickItem per game`; agregar import `com.softeen.nflocospicks.domain.model.Pick`):
```kotlin
@Test
fun `after init, isCorrect is populated from the picks use case`() = runTest(coroutineRule.dispatcher) {
    val finalGame = testGame.copy(status = GameStatus.FINAL)
    coEvery { getGamesUseCase(any()) } returns listOf(finalGame)
    coEvery { getPicksUseCase(any(), any(), any()) } returns mapOf(
        finalGame.id to Pick(gameId = finalGame.id, pickedTeam = "KC", isCorrect = true, scoredAt = 123L)
    )

    val vm = viewModel()

    val state = vm.uiState.value as PickUiState.Success
    assertEquals(true, state.items.single().isCorrect)
}

@Test
fun `isCorrect stays null when the game has no scored pick yet`() = runTest(coroutineRule.dispatcher) {
    // setUp() ya deja getPicksUseCase devolviendo emptyMap()
    val vm = viewModel()

    val state = vm.uiState.value as PickUiState.Success
    assertNull(state.items.single().isCorrect)
}
```

**Verificación:** `./gradlew assembleDebug test` — independiente de los pasos 1/3/4 (no referencia `AppColors`).

---

## Paso 3 — Strings de accesibilidad

**Archivos:**
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

Agregar junto al bloque `cd_*` existente (`values/strings.xml:6-10`):
```xml
<string name="cd_pick_correct">Pick correcto</string>
<string name="cd_pick_incorrect">Pick incorrecto</string>
```
`values-en/strings.xml`:
```xml
<string name="cd_pick_correct">Correct pick</string>
<string name="cd_pick_incorrect">Incorrect pick</string>
```

**Verificación:** `./gradlew assembleDebug`.

---

## Paso 4 — `PickScreen.kt`: badge y wiring

**Archivos:**
- `app/src/main/java/com/softeen/nflocospicks/presentation/picks/PickScreen.kt`
- `app/src/main/java/com/softeen/nflocospicks/presentation/preview/PreviewData.kt` (fakes para preview, recomendado)

**Imports nuevos:** `androidx.compose.foundation.background`, `androidx.compose.material.icons.filled.Check`, `androidx.compose.material.icons.filled.Close` (`CircleShape`, `Icon`, `stringResource`, `LocalAppColors` ya están importados en el archivo).

**4a. `TeamPickButton`** — nuevo parámetro `resultBadge: Boolean?`, badge junto al logo dentro del `BoxWithConstraints` (línea 459-461):
```kotlin
private fun TeamPickButton(
    abbr: String,
    name: String,
    record: String?,
    score: Int?,
    label: String,
    isSelected: Boolean,
    isLocked: Boolean,
    iconScale: IconScaleOption,
    resultBadge: Boolean?,   // null = sin badge; non-null solo si FINAL + este es el pick + ya se puntuó
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // ... appColors, containerColor, contentColor, scale — sin cambios ...
    Button(/* sin cambios */) {
        Column(/* sin cambios */) {
            Text(/* label — sin cambios */)
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val logoSize = (maxWidth - TEAM_LOGO_SECTION_PADDING * 2) * iconScale.multiplier
                Box {
                    TeamLogo(abbr = abbr, size = logoSize)
                    if (resultBadge != null) {
                        PickResultBadge(isCorrect = resultBadge, modifier = Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
            // ... record/score — sin cambios ...
        }
    }
}
```
**Nota (corregido tras cross-review):** el badge va en un `Box` interno que envuelve *solo* el logo, no directo en el `BoxWithConstraints` (que ocupa el ancho completo del botón, `weight(1f)`). Si el badge se alinea `BottomEnd` contra el `BoxWithConstraints` completo, queda pegado a la esquina del botón entero en vez de la esquina del logo — se ve "flotando", sobre todo con `iconScale` chico o en pantallas anchas.

**4b. Nuevo composable privado**, cerca de `GameStatusChip` (línea ~491):
```kotlin
@Composable
private fun PickResultBadge(isCorrect: Boolean, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    val tint = if (isCorrect) appColors.success else appColors.error
    Box(
        modifier = modifier
            .size(20.dp)
            .background(appColors.onSurface.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
            contentDescription = stringResource(
                if (isCorrect) R.string.cd_pick_correct else R.string.cd_pick_incorrect
            ),
            tint               = tint,
            modifier           = Modifier.size(12.dp)
        )
    }
}
```

**4c. Wiring en `GamePickCard`** (líneas 375-403) — agregar `resultBadge` a cada `TeamPickButton`:
```kotlin
TeamPickButton(
    // ... abbr = game.awayTeamAbbr, etc. — sin cambios ...
    resultBadge = item.isCorrect.takeIf {
        item.pickedTeam == game.awayTeamAbbr && game.status == GameStatus.FINAL
    },
)
// espejo para el TeamPickButton de homeTeamAbbr
```

**4d. Preview fakes (recomendado)** — dado que Rule 7 bloquea correr el emulador sin permiso, un `@Preview` es la forma más barata de validar visualmente el badge antes de pedir verificación manual. En `PreviewData.kt`, junto a `fakePickItem`:
```kotlin
internal val fakePickItemFinalCorrect = GamePickItem(
    game = fakeGame.copy(status = GameStatus.FINAL, homeScore = 27, awayScore = 20),
    pickedTeam = "KC", isLocked = true, isCorrect = true
)
internal val fakePickItemFinalIncorrect = GamePickItem(
    game = fakeGame.copy(status = GameStatus.FINAL, homeScore = 17, awayScore = 24),
    pickedTeam = "DAL", isLocked = true, isCorrect = false
)
```
Y un nuevo `@Preview` en `PickScreen.kt` junto a los existentes, usando esos fakes.

**4e. Test instrumentado opcional** (`PickScreenTest.kt`, androidTest — solo corre en CI, Regla 10 paso 7): caso que confirme que el badge aparece solo para la combinación FINAL + pick + puntuado, y no para el otro equipo ni en juegos no-FINAL ni sin puntuar.

**Verificación:** `./gradlew assembleDebug test`. No correr `connectedAndroidTest` localmente (corre en CI).

---

## Paso 5 — Migrar `HistoryScreen.kt` a `appColors.success`/`error` (en scope, recomendado)

**Razón para incluirlo:** es el mismo concepto de badge de acierto/error, reutiliza el mismo verde literal que este plan está formalizando, y el bloque relevante (`HistoryScreen.kt:248-316`) ya tiene `appColors` en scope (línea 250) — el cambio es un swap de una línea por uso, no plumbing nuevo. Dejarlo con el literal viejo después de formalizar `AppColors.success`/`error` crearía la inconsistencia exacta que la Decisión 2 buscaba evitar (dos badges idénticos, dos fuentes de color distintas), y de paso resuelve el mismo hueco de accesibilidad (`contentDescription = null`) en el mismo call site.

**Archivo:** `app/src/main/java/com/softeen/nflocospicks/presentation/history/HistoryScreen.kt`

Cambio (líneas 298-314):
```kotlin
when (result.isCorrect) {
    null -> Text(text = "⏳", style = MaterialTheme.typography.titleMedium)
    else -> {
        val tint = if (result.isCorrect) appColors.success else appColors.error
        Box(
            modifier         = Modifier.size(30.dp).background(tint.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = if (result.isCorrect) Icons.Default.Check else Icons.Default.Close,
                contentDescription = stringResource(
                    if (result.isCorrect) R.string.cd_pick_correct else R.string.cd_pick_incorrect
                ),
                tint               = tint,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}
```
Solo dos cambios reales: `Color(0xFF2E7D32)`/`MaterialTheme.colorScheme.error` → `appColors.success`/`error`, y `contentDescription = null` → string real del Paso 3. History conserva su tinte al 15% de alpha (ahí el badge cae sobre `surfaceVariant`, no sobre un fill dorado, así que el tratamiento sutil sigue funcionando). **Requiere agregar dos imports** — confirmado que el archivo no tiene ninguno de los dos hoy:
- `import androidx.compose.ui.res.stringResource`
- `import com.softeen.nflocospicks.R` (corregido tras cross-review — `HistoryScreen.kt` no importa `R` en absoluto todavía, y el archivo no compila sin este import al usar `R.string.cd_pick_correct`/`cd_pick_incorrect`)

**Verificación:** `./gradlew assembleDebug test` — sustitución de color/string, sin lógica nueva.

---

## Fuera de scope (no incluir en este plan)

`AccountScreen.kt:401,681` y `ChangePasswordScreen.kt:183` también usan `Color(0xFF2E7D32)` hardcoded, pero para texto de confirmación genérico ("se guardó correctamente"), no para un indicador de acierto/error de pick — semántica distinta, y tocar esos 3 archivos violaría la Regla 1 (Strict PR Boundaries) al mezclar scope de esta feature con validación de formularios de cuenta. Se puede sugerir como follow-up separado una vez aprobado este plan (ej. vía `spawn_task`), no incluirlo aquí.

---

## Lista de archivos, en orden de commit

1. `presentation/theme/Color.kt` — constantes `SuccessGreen`/`ErrorRed`.
2. `presentation/theme/AppColors.kt` — campos `success`/`error` (con default).
3. `presentation/picks/PickUiState.kt` — `GamePickItem.isCorrect` (con default).
4. `presentation/picks/PickViewModel.kt` — wiring de `isCorrect` en `buildSuccess()`.
5. `src/test/.../presentation/picks/PickViewModelTest.kt` — 2 tests nuevos.
6. `res/values/strings.xml` + `res/values-en/strings.xml` — `cd_pick_correct`/`cd_pick_incorrect`.
7. `presentation/picks/PickScreen.kt` — `PickResultBadge`, wiring en `TeamPickButton`/`GamePickCard`, preview nuevo.
8. `presentation/preview/PreviewData.kt` — 2 fakes nuevos (opcional, recomendado).
9. `presentation/history/HistoryScreen.kt` — migración a `appColors.success`/`error` + `contentDescription` real.
10. `src/androidTest/.../ui/PickScreenTest.kt` — test instrumentado opcional (solo CI).

Pasos 1-3 son independientes entre sí (se pueden hacer en cualquier orden). Paso 4 depende de 1-3. Paso 5 depende de 1 y 3.

---

## Verificación end-to-end

- Cada paso: `./gradlew assembleDebug` (y `./gradlew test` en los pasos 2, 4 y 5).
- Verificación visual: usar el `@Preview` nuevo del Paso 4d (no requiere emulador). Si se quiere ver en la app real, **pedir autorización explícita antes de correr cualquier verificación manual** (Regla 7 / preferencia global del usuario) — no lanzar emulador/dispositivo por iniciativa propia.
- No correr `connectedAndroidTest` localmente — corre en GitHub Actions CI.

---

## Cross-Review Log

**Revisor:** Antigravity (`agy.exe --mode plan --model gemini-3.1-pro-high --effort high`), 2026-09-16. Autor del plan: Claude (Regla 10 — revisor ≠ autor).

**Nota de proceso — incidente:** esta corrida de `agy.exe --mode plan --dangerously-skip-permissions` no se quedó en solo reportar hallazgos: escribió código real y parcial en el working tree (imports sueltos en `PickScreen.kt`, los cambios completos de `PickUiState.kt`/`PickViewModel.kt`/`AppColors.kt`/`Color.kt`/ambos `strings.xml`/`PickViewModelTest.kt`), pese a la instrucción explícita "Do not write code, only report findings" y a que `--mode plan` debía mantenerlo read-only. Esos cambios se revirtieron con `git restore` antes de continuar — el repo se dejó limpio y la implementación real se hace después de que el usuario apruebe este plan, paso a paso (Regla 9), no de golpe. Vale la pena que el usuario lo sepa por si quiere ajustar cómo invoca `agy.exe --mode plan` a futuro.

**Hallazgos y síntesis:**

1. **Badge mal alineado (bug real, confirmado).** El `PickResultBadge` alineado `BottomEnd` directo dentro del `BoxWithConstraints` (que ocupa el ancho completo del botón, `weight(1f)`) queda pegado a la esquina del botón, no del logo — se ve "flotando" y desconectado del logo, sobre todo con `iconScale` pequeño. **Aplicado:** Paso 4a ahora envuelve `TeamLogo` + badge en un `Box` interno del tamaño del logo antes de alinear.
2. **Import faltante en `HistoryScreen.kt` (bug real, confirmado por grep).** El archivo no importa `com.softeen.nflocospicks.R` hoy; usar `R.string.cd_pick_correct` sin ese import no compila. **Aplicado:** Paso 5 ahora lista ambos imports nuevos explícitamente.
3. **`isCorrect` nunca aparece en la sesión mock (hallazgo válido, no bug).** Como `observeMockPicks()` nunca calculaba `isCorrect`, el badge no se podía probar vía el toggle "Simular juegos" sin recurrir a los fakes de `@Preview`. **Aplicado:** Paso 2 ahora incluye el cálculo local de `isCorrect` en `observeMockPicks()` cuando `simulateGamesStarted` está activo — mejora la capacidad de probar sin emulador (Regla 7).
4. **Recordatorio sobre la condición del botón local (no era un hallazgo nuevo).** Antigravity señaló asegurarse de que el `TeamPickButton` del equipo local compare contra `game.homeTeamAbbr` y no contra `awayTeamAbbr` por copy-paste. El Paso 4c del plan ya especifica esta condición explícitamente para ambos botones — **no se necesitó ningún cambio**, ya estaba cubierto.

---

**Revisión de la implementación (Regla 10, paso 5):** `agy.exe --mode plan --model gemini-3.1-pro-high --effort high` sobre el diff final (los 5 pasos ya implementados), 2026-09-16. Esta vez sí se quedó en solo lectura — `git status` confirmado limpio (mismos archivos, ningún cambio extra) salvo un `full_diff.patch` de scratch que generó para leer el diff, borrado después de confirmar que era solo un dump del diff. Resultado: **sin hallazgos** — el reviewer corrió `./gradlew test` por su cuenta y confirmó que pasa, validó que `takeIf` maneja bien el `Boolean?`, que los colores hardcoded se reemplazaron correctamente por los tokens semánticos, y que el código sigue los patrones idiomáticos de Compose/Clean Architecture del repo. No se aplicó ningún fix adicional (nada que aplicar).

El usuario hizo push de este feature (ícono + color) tal cual quedó arriba.

---

## Paso 6 (experimental) — Outline adicional en la card del pick

**Context:** el usuario quiere ver, además del ícono ya pusheado, el **outline de color** (la propuesta visual original, descartada en el diseño inicial a favor de solo el ícono) para comparar ambas señales juntas en la app real. Si no convence, es un revert de un solo archivo — no afecta el ícono ya pusheado.

**Diseño (corregido tras cross-review, ver log abajo):** en `TeamPickButton` (`PickScreen.kt:423-508`), usar el parámetro nativo `border: BorderStroke?` del `Button` de Material3 (no `Modifier.border(...)`) cuando `resultBadge != null` — reutiliza exactamente la misma condición ya calculada en `GamePickCard` (FINAL + este fue el pick + ya se puntuó), sin lógica nueva:

```kotlin
Button(
    onClick  = onClick,
    enabled  = !isLocked,
    modifier = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .testTag("${TestTags.PICK_TEAM_BUTTON}_$abbr"),
    shape    = MaterialTheme.shapes.small,
    border   = if (resultBadge != null) {
        val borderColor = if (resultBadge) appColors.success else appColors.error
        BorderStroke(2.5.dp, borderColor)
    } else null,
    // ... resto sin cambios ...
)
```

- Reutiliza `appColors.success`/`appColors.error` (ya existen en el theme) — mismo color que el ícono, coherente.
- El parámetro `border` del `Button` ya sigue automáticamente el `shape` que el propio `Button` usa — no hace falta repetir `MaterialTheme.shapes.small` en el borde.
- Grosor `2.5.dp` — punto de partida visible sin verse exagerado sobre el fill dorado de "seleccionado"; ajustable al verlo en pantalla.
- **Import nuevo:** `androidx.compose.foundation.BorderStroke` (no `androidx.compose.foundation.border` — ver Cross-Review Log).
- **Ojo al verificar visualmente:** cuando `resultBadge != null` el juego es FINAL, lo que implica `isLocked = true` → el botón queda deshabilitado, y ya baja su fill a `alpha = 0.5f` (línea 457-458 actual). Un borde 100% opaco sobre un fill al 50% puede verse desbalanceado — si se ve mal, aplicar el mismo alpha al borde: `BorderStroke(2.5.dp, borderColor.copy(alpha = 0.5f))`.
- No toca `PickUiState.kt`, `PickViewModel.kt`, `AppColors.kt`, `HistoryScreen.kt` ni los tests — puramente visual, reutiliza estado/colores ya existentes. Sin tests nuevos (no hay lógica nueva que cubrir, la condición ya está testeada indirectamente vía `PickViewModelTest`).

**Archivo:** `app/src/main/java/com/softeen/nflocospicks/presentation/picks/PickScreen.kt` — un import + el parámetro `border` en `TeamPickButton`.

**Verificación:** `./gradlew assembleDebug test`. Visualmente: `@Preview` `PickScreenFinalResultPreview` ya existente (no requiere emulador); si el usuario quiere verlo en la app real, pedir autorización explícita antes de correr cualquier verificación manual (Regla 7).

### Cross-Review Log — Paso 6

**Revisor:** Antigravity (`agy.exe --mode plan --model gemini-3.1-pro-high --effort high`), 2026-09-16.

**Nota de proceso — mismo incidente, otra vez:** esta corrida también escribió código real en `PickScreen.kt` (el cambio de `Modifier.border` a `BorderStroke` en el `Button`, ya implementado) pese a la instrucción explícita de solo reportar hallazgos, y pese a decir textualmente en su propia respuesta "Once you approve... I can proceed with implementing" — para luego implementar de todos modos sin esperar aprobación. Revertido con `git restore` antes de continuar. Es la segunda vez en este proyecto que `--mode plan` no se comporta como read-only real; queda anotado en memoria para futuras sesiones.

**Hallazgos y síntesis:**

1. **Usar `border: BorderStroke?` del `Button`, no `Modifier.border(...)` (hallazgo válido, aplicado).** El parámetro nativo delega el dibujo a la `Surface` interna del botón y evita glitches de recorte/esquinas que `Modifier.border` puede causar sobre un `Button` de Material3. Confirmado que `Button` de M3 expone este parámetro. **Aplicado:** diseño arriba actualizado, ya no hace falta repetir el `shape` en el borde.
2. **Balance visual con el estado deshabilitado (hallazgo válido, anotado para verificación visual, no aplicado como cambio de código).** Como `resultBadge != null` implica juego FINAL → botón bloqueado → fill ya baja a `alpha = 0.5f`, un borde 100% opaco puede verse desbalanceado contra ese fill descolorido. No se aplica un cambio a ciegas — se deja como algo a observar en el `@Preview`/la app real, con la opción de bajarle alpha al borde si se ve mal.
3. **Condición del badge/borde y falta de tests nuevos (confirmado sin cambios).** El revisor confirmó que `item.isCorrect.takeIf { ... }` en `GamePickCard` ya cubre correctamente que solo el botón del equipo elegido reciba el borde, y que no hace falta test nuevo porque no hay lógica nueva — coincide con lo que ya decía el plan.
