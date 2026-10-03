package com.pesaflow.app.parsers

import com.pesaflow.app.data.database.AppDatabase
import org.junit.Assert.*
import org.junit.Test

// Migration chain guard: every upgrade step 12→23 must have a registered
// Migration object. Schemas 12..23 are exported (app/schemas); 9..11
// predate schema export and cannot be instrument-tested retroactively —
// destructive fallback on UPGRADE is therefore disabled (downgrade-only)
// so a missing path crashes loudly instead of wiping the ledger.
class MigrationChainTest {

    @Test
    fun `migrations cover 12 to 23 without gaps`() {
        val all = listOf(
            AppDatabase.MIGRATION_12_13,
            AppDatabase.MIGRATION_13_14,
            AppDatabase.MIGRATION_14_15,
            AppDatabase.MIGRATION_15_16,
            AppDatabase.MIGRATION_16_17,
            AppDatabase.MIGRATION_17_18,
            AppDatabase.MIGRATION_18_19,
            AppDatabase.MIGRATION_19_20,
            AppDatabase.MIGRATION_20_21,
            AppDatabase.MIGRATION_21_22,
            AppDatabase.MIGRATION_22_23
        )
        assertEquals(11, all.size)
        all.forEachIndexed { i, m ->
            assertEquals(12 + i, m.startVersion)
            assertEquals(13 + i, m.endVersion)
        }
    }

    @Test
    fun `legacy 9 to 12 migrations still registered`() {
        // Devices on very old installs must still upgrade without wipe.
        listOf(
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12
        ).forEach { assertNotNull(it) }
    }
}
