package com.mohart.loansmsscanner

import android.content.Context
import android.net.Uri

data class Loan(
    val lender: String,
    val outstanding: Double,
    val lastDate: Long,
    val snippet: String
) {
    val cleared: Boolean get() = outstanding <= 0.0
}

object SmsScanner {

    private const val DAYS_BACK = 120L
    private val amountAfterKeyword = Regex(
        "(outstanding|balance|owe|owing|due)[^0-9]{0,70}?(?:ksh|kes|kshs)\\.?\\s?([0-9,]+(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )
    private val clearedWords = listOf("fully paid", "fully repaid", "loan cleared", "has been cleared", "no outstanding")

    fun detectLender(address: String, body: String): String? {
        val a = address.uppercase()
        val b = body.uppercase()
        return when {
            "FULIZA" in b -> "Fuliza"
            "M-SHWARI" in b || "MSHWARI" in b || "M-SHWARI" in a -> "M-Shwari"
            "KCB" in a || ("KCB" in b && ("LOAN" in b || "M-PESA" in b)) -> "KCB M-Pesa"
            "EQUITY" in a || "EAZZY" in b || "EQUITEL" in a -> "Equity Bank"
            "TALA" in a || "BRANCH" in a || "OKASH" in a || "TIMIZA" in a || "ZENKA" in a -> address.trim()
            else -> null
        }
    }

    fun parseOutstanding(body: String): Double? {
        val lower = body.lowercase()
        if (clearedWords.any { it in lower }) return 0.0
        val m = amountAfterKeyword.find(body) ?: return null
        return m.groupValues[2].replace(",", "").toDoubleOrNull()
    }

    /** Reads the inbox (needs READ_SMS) and returns the latest known balance per lender. */
    fun scan(context: Context): List<Loan> {
        val since = System.currentTimeMillis() - DAYS_BACK * 24 * 60 * 60 * 1000
        val result = LinkedHashMap<String, Loan>()
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"),
            "date >= ?",
            arrayOf(since.toString()),
            "date DESC"
        ) ?: return emptyList()

        cursor.use {
            val iAddr = it.getColumnIndexOrThrow("address")
            val iBody = it.getColumnIndexOrThrow("body")
            val iDate = it.getColumnIndexOrThrow("date")
            while (it.moveToNext()) {
                val address = it.getString(iAddr) ?: continue
                val body = it.getString(iBody) ?: continue
                val lender = detectLender(address, body) ?: continue
                if (result.containsKey(lender)) continue // newest message wins
                val amount = parseOutstanding(body) ?: continue
                result[lender] = Loan(lender, amount, it.getLong(iDate), body.take(140))
            }
        }
        return result.values.toList()
    }

    fun strategies(loans: List<Loan>): List<String> {
        val open = loans.filter { !it.cleared }.sortedBy { it.outstanding }
        if (open.isEmpty()) return listOf("No outstanding loans detected. Keep it up!")
        val tips = mutableListOf<String>()
        val fuliza = open.firstOrNull { it.lender == "Fuliza" }
        if (fuliza != null) {
            tips += "Clear Fuliza first: it charges daily access fees, so it gets expensive quickly."
        }
        tips += "Snowball: pay the smallest balance first (${open.first().lender}, Ksh ${"%,.0f".format(open.first().outstanding)}), then roll that payment into the next loan."
        tips += "Avalanche: if you know the interest rates, clear the highest-rate loan first to pay the least overall."
        tips += "Pay at least the minimum on every loan to avoid penalties, and put any extra on your chosen target."
        tips += "Avoid borrowing from one lender to repay another."
        return tips
    }
}
