package com.akib.mycompanion.domain.model

/**
 * A person the user wants one-tap access to.
 *
 * @property phone international (E.164) format, e.g. `+8801711123456`. Always produced by [PhoneNumbers.normalize].
 */
data class Contact(
    val id: Long = 0,
    val name: String,
    val phone: String,
) {
    /** Digits only, the form WhatsApp uses in its ids and in wa.me links. */
    val digits: String get() = phone.filter(Char::isDigit)
}
