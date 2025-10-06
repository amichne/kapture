package io.amichne.kapture.core.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BranchUtilsTest {
    @Test
    fun `extract ticket using named group`() {
        val ticket = BranchUtils.extractTicket(
            branch = "PROJ-123/implement-feature",
            pattern = "^(?<ticket>[A-Z]+-\\d+)/[a-z0-9._-]+$"
        )
        assertEquals("PROJ-123", ticket)
    }

    @Test
    fun `extract ticket returns null when pattern mismatches`() {
        val ticket = BranchUtils.extractTicket(
            branch = "main",
            pattern = "^(?<ticket>[A-Z]+-\\d+)/[a-z0-9._-]+$"
        )
        assertNull(ticket)
    }
}
