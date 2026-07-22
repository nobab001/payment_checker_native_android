package online.paychek.app.services.smartpopup

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import online.paychek.app.R
import online.paychek.app.data.remote.dto.TransactionItem
import online.paychek.app.utils.BanglaDateTimeFormat
import java.text.DecimalFormat
import java.util.Locale

class SmartPopupTransactionAdapter(
    private val onSoldOut: (TransactionItem) -> Unit
) : RecyclerView.Adapter<SmartPopupTransactionAdapter.VH>() {

    private val items = mutableListOf<TransactionItem>()

    fun submitList(list: List<TransactionItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.smart_popup_transaction_row, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], onSoldOut)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stripe = itemView.findViewById<View>(R.id.providerStripe)
        private val tvProvider = itemView.findViewById<TextView>(R.id.tvProvider)
        private val tvTrxId = itemView.findViewById<TextView>(R.id.tvTrxId)
        private val tvTime = itemView.findViewById<TextView>(R.id.tvTime)
        private val tvDevice = itemView.findViewById<TextView>(R.id.tvDevice)
        private val tvAmount = itemView.findViewById<TextView>(R.id.tvAmount)
        private val btnSoldOut = itemView.findViewById<TextView>(R.id.btnSoldOut)
        private val moneyFmt = DecimalFormat("#,##0.00")

        fun bind(item: TransactionItem, onSoldOut: (TransactionItem) -> Unit) {
            val key = item.providerTag.lowercase(Locale.US)
            val providerColor = when {
                key.contains("bkash") || key.contains("বিকাশ") -> Color.parseColor("#E2136E")
                key.contains("nagad") || key.contains("নগদ") -> Color.parseColor("#F97316")
                key.contains("rocket") || key.contains("রকেট") -> Color.parseColor("#8B5CF6")
                key.contains("upay") || key.contains("উপায়") || key.contains("উপায়") -> Color.parseColor("#0D9488")
                else -> Color.parseColor("#22D3EE")
            }

            stripe.setBackgroundColor(providerColor)
            tvProvider.setTextColor(providerColor)
            tvProvider.text = item.providerTag
            tvTrxId.text = "TrxID: ${item.trxId}"
            tvTime.text = BanglaDateTimeFormat.formatTrxCard(item.smsTimestamp)
            tvAmount.text = "৳ ${moneyFmt.format(item.amount)}"
            tvDevice.text = buildDeviceLine(item)

            if (item.isUsed == 1) {
                btnSoldOut.text = itemView.context.getString(R.string.smart_popup_soldout)
                btnSoldOut.setTextColor(Color.parseColor("#EF4444"))
                btnSoldOut.setBackgroundResource(R.drawable.bg_smart_popup_badge_sold)
                btnSoldOut.isClickable = false
                btnSoldOut.setOnClickListener(null)
            } else {
                btnSoldOut.text = itemView.context.getString(R.string.smart_popup_available)
                btnSoldOut.setTextColor(Color.parseColor("#10B981"))
                btnSoldOut.setBackgroundResource(R.drawable.bg_smart_popup_badge_ok)
                btnSoldOut.isClickable = true
                btnSoldOut.setOnClickListener { onSoldOut(item) }
            }
        }

        private fun buildDeviceLine(item: TransactionItem): String {
            val name = item.deviceName?.takeIf {
                it.isNotBlank() &&
                    it.lowercase(Locale.US) != "unknown" &&
                    it.lowercase(Locale.US) != "unknown device"
            } ?: item.deviceId?.takeIf { it.isNotBlank() } ?: "Unknown Device"
            val sim = buildString {
                if (item.simSlot != null) append(" • SIM ${item.simSlot}")
                item.simNumber?.takeIf { it.isNotBlank() }?.let { append(" - $it") }
            }
            return "Device: $name$sim"
        }
    }
}
