package online.paychek.app.services.smartpopup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.paychek.app.config.AppConfig
import online.paychek.app.data.repository.PaymentRepository
import online.paychek.app.utils.SecurePreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object SmartPopupLoader {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun loadLast7Days(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
        if (token.isEmpty()) {
            return@withContext Result.failure(Exception("লগইন সেশন পাওয়া যায়নি"))
        }

        val endDate = dateFmt.format(Date())
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        val startDate = dateFmt.format(cal.time)

        val repo = PaymentRepository()
        val all = mutableListOf<online.paychek.app.data.remote.dto.TransactionItem>()
        var page = 1
        var hasMore = true

        while (hasMore && page <= 20) {
            val result = repo.fetchTransactionHistory(
                token = token,
                page = page,
                limit = 100,
                provider = "all",
                startDate = startDate,
                endDate = endDate
            )
            val pageResult = result.getOrElse { return@withContext Result.failure(it) }
            all.addAll(pageResult.items)
            hasMore = pageResult.hasMore == true && pageResult.items.isNotEmpty()
            page++
        }

        SmartPopupCache.save(context.applicationContext, all)
        Result.success(all.size)
    }
}
