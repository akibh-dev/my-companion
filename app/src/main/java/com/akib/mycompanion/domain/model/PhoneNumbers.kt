package com.akib.mycompanion.domain.model

/**
 * Turns whatever the user typed (or what the phone book returned) into E.164 (`+<country><number>`).
 *
 * Rules, in order:
 * - spaces, dashes, dots and brackets are ignored
 * - `00` prefix means international, same as `+`
 * - `+…` is kept as is
 * - a leading `0` is a local trunk prefix: replaced by [defaultCountryCode] (`01711…` → `+8801711…`)
 * - digits already starting with [defaultCountryCode] get a `+`
 * - any other bare digits are treated as a national number without the trunk `0`
 *
 * Returns null when the input is not a plausible phone number (E.164 allows at most 15 digits).
 */
object PhoneNumbers {
    const val DEFAULT_COUNTRY_CODE = "880"
    private const val MIN_DIGITS = 8
    private const val MAX_DIGITS = 15
    private const val MIN_NATIONAL_DIGITS = 6
    private val separators = Regex("[\\s\\-.()/]")

    fun normalize(raw: String, defaultCountryCode: String = DEFAULT_COUNTRY_CODE): String? {
        val compact = raw.trim().replace(separators, "")
        if (compact.isEmpty()) return null

        val international = compact.startsWith("+")
        val body = if (international) compact.drop(1) else compact
        if (body.isEmpty() || !body.all(Char::isDigit)) return null

        val digits = when {
            international -> body
            body.startsWith("00") -> body.drop(2)
            body.startsWith("0") -> national(body.drop(1), defaultCountryCode) ?: return null
            body.startsWith(defaultCountryCode) && body.length > defaultCountryCode.length + 6 -> body
            else -> national(body, defaultCountryCode) ?: return null
        }
        if (digits.startsWith("0")) return null
        return if (digits.length in MIN_DIGITS..MAX_DIGITS) "+$digits" else null
    }

    private fun national(number: String, countryCode: String): String? =
        if (number.length >= MIN_NATIONAL_DIGITS) countryCode + number else null

    fun isValid(raw: String, defaultCountryCode: String = DEFAULT_COUNTRY_CODE): Boolean =
        normalize(raw, defaultCountryCode) != null
}
