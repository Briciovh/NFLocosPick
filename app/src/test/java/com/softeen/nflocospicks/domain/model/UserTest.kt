package com.softeen.nflocospicks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserTest {

    private fun user(
        displayName: String = "",
        email: String = "",
        username: String? = null
    ) = User(uid = "u1", displayName = displayName, email = email, photoUrl = null, username = username)

    @Test
    fun `existing username is returned unchanged`() {
        val result = user(displayName = "Google", email = "google@gmail.com", username = "existing").suggestedUsername()

        assertEquals("existing", result)
    }

    @Test
    fun `display name with accents and spaces is slugified`() {
        val result = user(displayName = "Bricio Velázquez").suggestedUsername()

        assertEquals("bricio_velazquez", result)
    }

    @Test
    fun `email local part is used when display name is blank`() {
        val result = user(email = "bricio.v+test@gmail.com").suggestedUsername()

        assertEquals("bricio_v_test", result)
    }

    @Test
    fun `display name takes priority over email when both are present`() {
        val result = user(displayName = "Google", email = "googletest@gmail.com").suggestedUsername()

        assertEquals("google", result)
    }

    @Test
    fun `phone-only account with no display name or email has no suggestion`() {
        val result = user().suggestedUsername()

        assertNull(result)
    }

    @Test
    fun `long display name is truncated to at most 20 characters`() {
        val result = user(displayName = "A Very Long Display Name Indeed").suggestedUsername()

        assertEquals("a_very_long_display", result)
        assertEquals(true, (result?.length ?: 0) <= 20)
    }

    @Test
    fun `effectiveDisplayName returns displayName when it is not blank`() {
        val result = user(displayName = "Bricio", username = "bricio").effectiveDisplayName

        assertEquals("Bricio", result)
    }

    @Test
    fun `effectiveDisplayName falls back to username when displayName is blank`() {
        val result = user(displayName = "", username = "bricio").effectiveDisplayName

        assertEquals("bricio", result)
    }

    @Test
    fun `effectiveDisplayName is blank when both displayName and username are blank`() {
        val result = user(displayName = "", username = null).effectiveDisplayName

        assertEquals("", result)
    }
}
