package com.pesaflow.app.parsers

import com.pesaflow.app.data.ledger.ContactBook
import com.pesaflow.app.data.ledger.ContactEntry
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test


class ContactBookTest {

    @Test
    fun `relationships list covers common roles`() {
        val rels = ContactBook.relationships()
        assertTrue(rels.contains("Friend"))
        assertTrue(rels.contains("Brother"))
        assertTrue(rels.contains("Sister"))
        assertTrue(rels.contains("Family"))
        assertTrue(rels.contains("Landlord"))
        assertTrue(rels.contains("Boss"))
        assertTrue(rels.contains("Business"))
        assertTrue(rels.contains("School"))
        assertTrue(rels.contains("Other"))
    }

    @Test
    fun `contact entry holds all fields`() {
        val e = ContactEntry(
            name = "samson",
            displayName = "Samson",
            relationship = "Friend",
            category = "Food",
            scope = "BOTH",
            notes = "College buddy",
            transactionCount = 5,
            lastSeen = 1000L,
            totalIn = 2000.0,
            totalOut = 500.0,
            matchTerms = "Nancy,Daniel Mayhvjh"
        )
        assertEquals("samson", e.name)
        assertEquals("Samson", e.displayName)
        assertEquals("Friend", e.relationship)
        assertEquals("Food", e.category)
        assertEquals("BOTH", e.scope)
        assertEquals("College buddy", e.notes)
        assertEquals(5, e.transactionCount)
        assertEquals(2000.0, e.totalIn, 0.001)
        assertEquals(500.0, e.totalOut, 0.001)
        assertEquals("Nancy,Daniel Mayhvjh", e.matchTerms)
    }

    @Test
    fun `transaction type enum has income and expense`() {
        assertEquals(TransactionType.INCOME, TransactionType.valueOf("INCOME"))
        assertEquals(TransactionType.EXPENSE, TransactionType.valueOf("EXPENSE"))
    }
}
