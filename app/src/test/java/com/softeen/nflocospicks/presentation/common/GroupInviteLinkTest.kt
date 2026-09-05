package com.softeen.nflocospicks.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupInviteLinkTest {

    @Test
    fun `builds the exact expected join link for an invite code`() {
        assertEquals(
            "https://nflocospicks.web.app/join?code=ABC123",
            buildGroupInviteLink("ABC123")
        )
    }
}
