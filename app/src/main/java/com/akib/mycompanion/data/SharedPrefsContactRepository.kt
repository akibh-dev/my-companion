package com.akib.mycompanion.data

import android.content.Context
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.repository.ContactRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tiny on-device store (SharedPreferences + JSON). Fine for a handful of contacts;
 * swap for Room later (see plan/03-learning-roadmap.md) without touching the UI.
 */
class SharedPrefsContactRepository(context: Context) : ContactRepository {
    private val prefs = context.applicationContext
        .getSharedPreferences("companion_contacts", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(load())
    override val contacts: StateFlow<List<Contact>> = state

    override fun add(name: String, phone: String) {
        val updated = state.value + Contact(UUID.randomUUID().toString(), name.trim(), phone.trim())
        save(updated)
    }

    override fun remove(id: String) {
        save(state.value.filterNot { it.id == id })
    }

    private fun save(list: List<Contact>) {
        val array = JSONArray()
        list.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone))
        }
        prefs.edit().putString(KEY, array.toString()).apply()
        state.value = list
    }

    private fun load(): List<Contact> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Contact(o.getString("id"), o.getString("name"), o.getString("phone"))
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY = "contacts"
    }
}
