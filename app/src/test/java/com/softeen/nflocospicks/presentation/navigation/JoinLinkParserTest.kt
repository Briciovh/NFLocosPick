package com.softeen.nflocospicks.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JoinLinkParserTest {

    @Test
    fun `valid link returns the invite code`() {
        assertEquals(
            "ABC123",
            extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?code=ABC123")
        )
    }

    @Test
    fun `valid link with trailing slash on the path returns the invite code`() {
        assertEquals(
            "ABC123",
            extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join/?code=ABC123")
        )
    }

    @Test
    fun `wrong host returns null`() {
        assertNull(extractInviteCodeFromJoinLink("https://evil.example/join?code=ABC123"))
    }

    @Test
    fun `wrong scheme returns null`() {
        assertNull(extractInviteCodeFromJoinLink("http://nflocospicks.web.app/join?code=ABC123"))
    }

    @Test
    fun `path that only contains join but is not exactly join returns null`() {
        assertNull(extractInviteCodeFromJoinLink("https://nflocospicks.web.app/joining?code=ABC123"))
        assertNull(extractInviteCodeFromJoinLink("https://nflocospicks.web.app/not/join?code=ABC123"))
        assertNull(extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join/extra?code=ABC123"))
    }

    @Test
    fun `missing code param returns null`() {
        assertNull(extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?other=1"))
        assertNull(extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join"))
    }

    @Test
    fun `empty code value returns null`() {
        assertNull(extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?code="))
    }

    @Test
    fun `percent-encoded code value is decoded before validation`() {
        // %41%42%43%31%32%33 decodes to ABC123
        assertEquals(
            "ABC123",
            extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?code=%41%42%43%31%32%33")
        )
    }

    @Test
    fun `repeated code params - the first match wins`() {
        assertEquals(
            "ABC123",
            extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?code=ABC123&code=XYZ999")
        )
    }

    @Test
    fun `extra unrelated query params alongside a valid code are ignored`() {
        assertEquals(
            "ABC123",
            extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?utm_source=chat&code=ABC123&ref=friend")
        )
    }

    @Test
    fun `lowercase code is uppercased before pattern validation, not rejected`() {
        // Confirms uppercase() happens BEFORE the INVITE_CODE_PATTERN check, not after —
        // if reversed, a lowercase code would fail validation instead of being normalized.
        assertEquals(
            "ABC123",
            extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?code=abc123")
        )
    }

    @Test
    fun `code with wrong length returns null`() {
        assertNull(extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?code=ABC12"))
        assertNull(extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?code=ABC1234"))
    }

    @Test
    fun `code with non-alphanumeric characters returns null`() {
        assertNull(extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?code=AB-123"))
        assertNull(extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?code=AB_123"))
    }

    @Test
    fun `a trailing URL fragment does not leak into the code value`() {
        // java.net.URI separates #fragment from the query per RFC 3986, so this must
        // return "ABC123", not "ABC123#invite" — regression guard for a concern raised
        // (and rejected as stale against this implementation) during plan review.
        assertEquals(
            "ABC123",
            extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?code=ABC123#invite")
        )
    }

    @Test
    fun `malformed or garbage input does not throw and returns null`() {
        assertNull(extractInviteCodeFromJoinLink(""))
        assertNull(extractInviteCodeFromJoinLink("not a url at all"))
        assertNull(extractInviteCodeFromJoinLink("::::"))
        assertNull(extractInviteCodeFromJoinLink("https://"))
    }

    @Test
    fun `malformed percent-encoding in the code value does not throw and returns null`() {
        // Regression guard for a real DoS found in implementation review: MainActivity's VIEW
        // intent-filter is exported, so any app could send an Intent with a malformed escape
        // sequence like "%2" or "%ZZ" — URLDecoder.decode throws IllegalArgumentException for
        // these, which used to propagate uncaught out of this function and crash the app.
        assertNull(extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?code=%2"))
        assertNull(extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?code=%ZZ"))
        assertNull(extractInviteCodeFromJoinLink("https://nflocospicks.web.app/join?code=AB%"))
    }

    @Test
    fun `uppercase or mixed-case scheme and host are still accepted`() {
        // Android's OS-level Intent/App-Link resolution is case-insensitive for scheme and
        // host (unlike path, which stays case-sensitive) — an Intent that the OS already
        // decided belongs to this app must not then get silently rejected here.
        assertEquals(
            "ABC123",
            extractInviteCodeFromJoinLink("HTTPS://NFLOCOSPICKS.WEB.APP/join?code=ABC123")
        )
        assertEquals(
            "ABC123",
            extractInviteCodeFromJoinLink("Https://NfLocosPicks.Web.App/join?code=ABC123")
        )
    }
}
