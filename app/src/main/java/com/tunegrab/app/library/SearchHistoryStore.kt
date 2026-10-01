package com.tunegrab.app.library

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Recent search queries, most-recent first. Backs the history chips
 * under the search field.
 */
class SearchHistoryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "tunegrab_search_history", Context.MODE_PRIVATE
    )

    private val _history = MutableStateFlow(load())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    suspend fun record(query: String) = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext
        val updated = (_history.value - q).take(MAX - 1)
        val next = listOf(q) + updated
        _history.value = next
        prefs.edit().putStringSet(KEY, next.toSet()).apply()
        // String sets lose ordering; persist the ordered list too.
        prefs.edit().putString(KEY_ORDER, next.joinToString("\u0001")).apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        _history.value = emptyList()
        prefs.edit().remove(KEY).remove(KEY_ORDER).apply()
    }

    private fun load(): List<String> {
        val ordered = prefs.getString(KEY_ORDER, null)
        if (ordered != null) return ordered.split("\u0001").filter { it.isNotEmpty() }.take(MAX)
        return prefs.getStringSet(KEY, emptySet()).orEmpty().take(MAX)
    }

    companion object {
        private const val KEY = "queries"
        private const val KEY_ORDER = "queries_ordered"
        private const val MAX = 12
    }
}
