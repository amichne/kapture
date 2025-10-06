package io.amichne.kapture.core.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BranchUtilsTest {
    @Test
    fun `extract task using named group`() {
        val task = BranchUtils.extractTask(
            branch = "PROJ-123/implement-feature",
            pattern = "^(?<task>[A-Z]+-\\d+)/[a-z0-9._-]+$"
        )
        assertEquals("PROJ-123", task)
    }

    @Test
    fun `extract task returns null when pattern mismatches`() {
        val task = BranchUtils.extractTask(
            branch = "main",
            pattern = "^(?<task>[A-Z]+-\\d+)/[a-z0-9._-]+$"
        )
        assertNull(task)
    }
}
