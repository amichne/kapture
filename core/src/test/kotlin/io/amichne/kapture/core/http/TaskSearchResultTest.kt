package io.amichne.kapture.core.http

import io.amichne.kapture.core.model.task.TaskSearchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskSearchResultTest {

    @Test
    fun `Found result contains status`() {
        val result = TaskSearchResult.Found("IN_PROGRESS")

        assertEquals("IN_PROGRESS", result.status)
    }

    @Test
    fun `NotFound result is singleton`() {
        val result1 = TaskSearchResult.NotFound
        val result2 = TaskSearchResult.NotFound

        assertSame(result1, result2)
    }

    @Test
    fun `Error result contains message`() {
        val result = TaskSearchResult.Error("timeout")

        assertEquals("timeout", result.message)
    }

    @Test
    fun `Found results are equal when status matches`() {
        val result1 = TaskSearchResult.Found("DONE")
        val result2 = TaskSearchResult.Found("DONE")

        assertEquals(result1, result2)
    }

    @Test
    fun `Found results are not equal when status differs`() {
        val result1 = TaskSearchResult.Found("DONE")
        val result2 = TaskSearchResult.Found("IN_PROGRESS")

        assertNotEquals(result1, result2)
    }

    @Test
    fun `Error results are equal when message matches`() {
        val result1 = TaskSearchResult.Error("timeout")
        val result2 = TaskSearchResult.Error("timeout")

        assertEquals(result1, result2)
    }

    @Test
    fun `Error results are not equal when message differs`() {
        val result1 = TaskSearchResult.Error("timeout")
        val result2 = TaskSearchResult.Error("not found")

        assertNotEquals(result1, result2)
    }

    @Test
    fun `different result types are not equal`() {
        val found = TaskSearchResult.Found("DONE")
        val notFound = TaskSearchResult.NotFound
        val error = TaskSearchResult.Error("error")

        assertNotEquals(found, notFound)
        assertNotEquals(found, error)
        assertNotEquals(notFound, error)
    }

    @Test
    fun `Found result toString includes status`() {
        val result = TaskSearchResult.Found("IN_PROGRESS")

        assertTrue(result.toString().contains("IN_PROGRESS"))
    }

    @Test
    fun `Error result toString includes message`() {
        val result = TaskSearchResult.Error("connection failed")

        assertTrue(result.toString().contains("connection failed"))
    }
}
