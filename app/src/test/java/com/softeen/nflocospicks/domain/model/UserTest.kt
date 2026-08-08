package com.softeen.nflocospicks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserTest {

    private fun user(
        displayName: String = "",
        email: String = "",
        username: String? = null,
        phoneNumber: String? = null
    ) = User(
        uid = "u1",
        displayName = displayName,
        email = email,
        photoUrl = null,
        username = username,
        phoneNumber = phoneNumber
    )

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

    @Test
    fun `isProfileComplete is true with username and email`() {
        val result = user(username = "bricio", email = "bricio@gmail.com").isProfileComplete

        assertEquals(true, result)
    }

    @Test
    fun `isProfileComplete is true with username and phone`() {
        val result = user(username = "bricio", phoneNumber = "+14708460176").isProfileComplete

        assertEquals(true, result)
    }

    @Test
    fun `isProfileComplete is true with username, email, and phone`() {
        val result = user(
            username = "bricio",
            email = "bricio@gmail.com",
            phoneNumber = "+14708460176"
        ).isProfileComplete

        assertEquals(true, result)
    }

    @Test
    fun `isProfileComplete is false without a username even with email and phone`() {
        val result = user(
            username = null,
            email = "bricio@gmail.com",
            phoneNumber = "+14708460176"
        ).isProfileComplete

        assertEquals(false, result)
    }

    @Test
    fun `isProfileComplete is false with a blank username`() {
        val result = user(username = "  ", email = "bricio@gmail.com").isProfileComplete

        assertEquals(false, result)
    }

    @Test
    fun `isProfileComplete is false with username but no email or phone`() {
        val result = user(username = "bricio").isProfileComplete

        assertEquals(false, result)
    }
}
