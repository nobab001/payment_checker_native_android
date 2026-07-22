package online.paychek.app.services.smartpopup

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import online.paychek.app.data.remote.dto.TransactionItem

object SmartPopupCache {
    private const val PREF = "smart_popup_cache_v1"
    private const val KEY_ITEMS = "items_json"
    private const val KEY_LOADED_AT = "loaded_at_ms"

    private val gson = Gson()
    private val listType = object : TypeToken<List<TransactionItem>>() {}.type

    fun save(context: Context, items: List<TransactionItem>) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, gson.toJson(items))
            .putLong(KEY_LOADED_AT, System.currentTimeMillis())
            .apply()
    }

    fun load(context: Context): List<TransactionItem> {
        val json = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<TransactionItem>>(json, listType) }
            .getOrDefault(emptyList())
    }

    fun loadedAtMs(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_LOADED_AT, 0L)

    fun search(context: Context, query: String): List<TransactionItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val all = load(context)
        // Prefer exact TrxID match first (scan target), then partial / sender
        val exact = all.filter { it.trxId.equals(q, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact
        return all.filter { trx ->
            trx.trxId.contains(q, ignoreCase = true) ||
                (trx.senderNumber?.contains(q, ignoreCase = true) == true) ||
                trx.amount.toString().contains(q)
        }
    }

    /** Merge server hits into local 7-day cache (keeps newest SMS available for next search). */
    fun upsert(context: Context, items: List<TransactionItem>) {
        if (items.isEmpty()) return
        val merged = LinkedHashMap<Int, TransactionItem>()
        load(context).forEach { merged[it.id] = it }
        items.forEach { merged[it.id] = it }
        save(context, merged.values.toList())
    }

    fun markSoldOutLocal(context: Context, transactionId: Int) {
        val updated = load(context).map { item ->
            if (item.id == transactionId) item.copy(isUsed = 1) else item
        }
        save(context, updated)
    }
}
