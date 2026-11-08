package com.akib.mycompanion.domain.whatsapp

/** Full-number identity only. No suffix, arbitrary text extraction, group or unknown JID matching. */
object WhatsAppNumberMatcher {
    private val separators = Regex("[\\s().-]")
    private val bangladeshLocal = Regex("01[3-9][0-9]{8}")
    private val bangladeshNational = Regex("1[3-9][0-9]{8}")
    private val jid = Regex("^([0-9]+)@s\\.whatsapp\\.net$")
    private val label = Regex("^Voice call\\s+(\\+?[0-9][0-9\\s().-]*)$", RegexOption.IGNORE_CASE)

    fun normalize(raw: String?): String? {
        val compact = raw?.trim()?.replace(separators, "") ?: return null
        if (!Regex("\\+?[0-9]+").matches(compact)) return null
        val digits = when {
            compact.startsWith("+") -> compact.drop(1)
            compact.startsWith("00") -> compact.drop(2)
            bangladeshLocal.matches(compact) -> "880" + compact.drop(1)
            bangladeshNational.matches(compact) -> "880$compact"
            else -> compact
        }
        return digits.takeIf { it.length in 8..15 && !it.startsWith('0') }
    }

    fun matches(wantedDigits: String, data1: String?, data3: String? = null): Boolean {
        val wanted = normalize(wantedDigits) ?: return false
        if (data1 != null && '@' in data1 && !jid.matches(data1)) return false
        val primary = data1?.let { value ->
            val number = jid.matchEntire(value)?.groupValues?.get(1)
            if (number != null) {
                normalize(if (bangladeshLocal.matches(number)) number else "+$number")
            } else {
                normalize(value)
            }
        }
        val secondary = data3?.let { label.matchEntire(it)?.groupValues?.get(1)?.let(::normalize) }
        val identities = listOfNotNull(primary, secondary).distinct()
        return identities.size == 1 && identities.single() == wanted
    }

    fun sameNumber(a: String, b: String): Boolean = normalize(a)?.let { it == normalize(b) } ?: false
}
