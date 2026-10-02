package com.pesaflow.app.parsers

import android.content.SharedPreferences
import com.pesaflow.app.data.ledger.CategoryMemory
import com.pesaflow.app.data.ledger.ConfidenceMemory
import org.junit.Assert.*
import org.junit.Test


// Learned merchant → category: corrections stick, "Other" never sticks,
// oldest entries evict past the cap. In-memory prefs fake.
class CategoryMemoryTest {

    private class FakePrefs : SharedPreferences {
        val map = mutableMapOf<String, String>()
        inner class Ed : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, String?>()
            private var clearAll = false
            override fun clear(): SharedPreferences.Editor { clearAll = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) { map.clear(); clearAll = false }
                pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
                pending.clear()
            }
            override fun putBoolean(k: String, v: Boolean) = this
            override fun putFloat(k: String, v: Float) = this
            override fun putInt(k: String, v: Int) = this
            override fun putLong(k: String, v: Long) = this
            override fun putString(k: String, v: String?): SharedPreferences.Editor { pending[k] = v; return this }
            override fun putStringSet(k: String, v: MutableSet<String>?) = this
            override fun remove(k: String): SharedPreferences.Editor { pending[k] = null; return this }
        }
        override fun contains(k: String) = map.containsKey(k)
        override fun edit(): SharedPreferences.Editor = Ed()
        override fun getAll(): Map<String, *> = map.toMap()
        override fun getBoolean(k: String, d: Boolean) = d
        override fun getFloat(k: String, d: Float) = d
        override fun getInt(k: String, d: Int) = d
        override fun getLong(k: String, d: Long) = d
        override fun getString(k: String, d: String?) = map[k] ?: d
        override fun getStringSet(k: String, d: MutableSet<String>?) = d
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun `learn then lookup case-insensitively`() {
        val p = FakePrefs()
        CategoryMemory.learn(p, "Naivas", "Groceries")
        assertEquals("Groceries", CategoryMemory.lookup(p, "NAIVAS"))
    }

    @Test
    fun `other never sticks and blanks ignored`() {
        val p = FakePrefs()
        CategoryMemory.learn(p, "X", "Other")
        CategoryMemory.learn(p, "", "Food")
        CategoryMemory.learn(p, "Y", "")
        assertNull(CategoryMemory.lookup(p, "X"))
        assertNull(CategoryMemory.lookup(p, "Y"))
    }

    @Test
    fun `latest correction wins`() {
        val p = FakePrefs()
        CategoryMemory.learn(p, "Kibanda", "Food")
        CategoryMemory.learn(p, "kibanda", "Groceries")
        assertEquals("Groceries", CategoryMemory.lookup(p, "Kibanda"))
    }

    @Test
    fun `confidence moves with verdicts inside rails`() {
        val p = FakePrefs()
        repeat(3) { ConfidenceMemory.record(p, "Mama", approved = true) }
        val up = ConfidenceMemory.effective(p, "mama", 0.7f)
        assertTrue(up > 0.7f && up <= 0.99f)
        repeat(30) { ConfidenceMemory.record(p, "Mama", approved = false) }
        val down = ConfidenceMemory.effective(p, "mama", 0.7f)
        assertTrue(down < up && down >= 0.5f)
    }
}
