package io.amichne.kapture.core.http

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TicketLookupResultTest {

    @Test
    fun `Found result contains status`() {
        val result = TicketLookupResult.Found("IN_PROGRESS")

        assertEquals("IN_PROGRESS", result.status)
    }

    @Test
    fun `NotFound result is singleton`() {
        val result1 = TicketLookupResult.NotFound
        val result2 = TicketLookupResult.NotFound

        assertSame(result1, result2)
    }

    @Test
    fun `Error result contains message`() {
        val result = TicketLookupResult.Error("timeout")

        assertEquals("timeout", result.message)
    }

    @Test
    fun `Found results are equal when status matches`() {
        val result1 = TicketLookupResult.Found("DONE")
        val result2 = TicketLookupResult.Found("DONE")

        assertEquals(result1, result2)
    }

    @Test
    fun `Found results are not equal when status differs`() {
        val result1 = TicketLookupResult.Found("DONE")
        val result2 = TicketLookupResult.Found("IN_PROGRESS")

        assertNotEquals(result1, result2)
    }

    @Test
    fun `Error results are equal when message matches`() {
        val result1 = TicketLookupResult.Error("timeout")
        val result2 = TicketLookupResult.Error("timeout")

        assertEquals(result1, result2)
    }

    @Test
    fun `Error results are not equal when message differs`() {
        val result1 = TicketLookupResult.Error("timeout")
        val result2 = TicketLookupResult.Error("not found")

        assertNotEquals(result1, result2)
    }

    @Test
    fun `different result types are not equal`() {
        val found = TicketLookupResult.Found("DONE")
        val notFound = TicketLookupResult.NotFound
        val error = TicketLookupResult.Error("error")

        assertNotEquals(found, notFound)
        assertNotEquals(found, error)
        assertNotEquals(notFound, error)
    }

    @Test
    fun `Found result toString includes status`() {
        val result = TicketLookupResult.Found("IN_PROGRESS")

        assertTrue(result.toString().contains("IN_PROGRESS"))
    }

    @Test
    fun `Error result toString includes message`() {
        val result = TicketLookupResult.Error("connection failed")

        assertTrue(result.toString().contains("connection failed"))
    }
}
