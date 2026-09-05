package com.softeen.nflocospicks.presentation.navigation

private const val JOIN_LINK_SCHEME = "https"
private const val JOIN_LINK_HOST = "nflocospicks.web.app"
private const val JOIN_LINK_PATH = "/join"
private val INVITE_CODE_PATTERN = Regex("^[A-Z0-9]{6}$")

/**
 * Extracts and validates the "code" query parameter from an incoming
 * join-link URL string (e.g. "https://nflocospicks.web.app/join?code=ABC123").
 * Pure string parsing — no android.net.Uri — so this is directly
 * JVM-unit-testable. Returns null unless the link is EXACTLY
 * scheme=https, host=nflocospicks.web.app, path=/join (or /join/), and
 * "code" is a 6-char [A-Z0-9] value — this is a trust boundary
 * (MainActivity's VIEW intent-filter is exported, so any app can send an
 * arbitrary Intent at it), not just a convenience parser, so matching is
 * exact, not substring.
 */
fun extractInviteCodeFromJoinLink(link: String): String? {
    val uri = runCatching { java.net.URI(link) }.getOrNull() ?: return null
    if (!uri.scheme.equals(JOIN_LINK_SCHEME, ignoreCase = true)) return null
    if (!uri.host.equals(JOIN_LINK_HOST, ignoreCase = true)) return null
    if (uri.path != JOIN_LINK_PATH && uri.path != "$JOIN_LINK_PATH/") return null

    val encodedCode = (uri.query ?: return null)
        .split('&')
        .map { it.split('=', limit = 2) }
        .firstOrNull { it.size == 2 && it[0] == "code" }
        ?.get(1)
        ?: return null

    val rawCode = runCatching { java.net.URLDecoder.decode(encodedCode, "UTF-8") }
        .getOrNull()
        ?.trim()
        ?.uppercase()
        ?: return null

    return rawCode.takeIf { INVITE_CODE_PATTERN.matches(it) }
}
