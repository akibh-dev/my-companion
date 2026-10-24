package com.akib.mycompanion.domain.whatsapp

/**
 * Decides whether a WhatsApp row in the phone's contacts belongs to a given number.
 *
 * WhatsApp stores the user id as `<digits>@s.whatsapp.net` in DATA1 and a label such as
 * `Voice call +880 1711-123456` in DATA3. Numbers saved without a country code are matched
 * on their last [SIGNIFICANT_DIGITS] digits.
 */
object WhatsAppNumberMatcher {
    const val SIGNIFICANT_DIGITS = 10

    fun matches(wantedDigits: String, data1: String?, data3: String? = null): Boolean {
        if (wantedDigits.isEmpty()) return false
        val fromJid = data1?.substringBefore('@')?.filter(Char::isDigit).orEmpty()
        val fromLabel = data3?.filter(Char::isDigit).orEmpty()
        return sameNumber(wantedDigits, fromJid) || sameNumber(wantedDigits, fromLabel)
    }

    fun sameNumber(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        return a.length >= SIGNIFICANT_DIGITS &&
            b.length >= SIGNIFICANT_DIGITS &&
            a.takeLast(SIGNIFICANT_DIGITS) == b.takeLast(SIGNIFICANT_DIGITS)
    }
}
