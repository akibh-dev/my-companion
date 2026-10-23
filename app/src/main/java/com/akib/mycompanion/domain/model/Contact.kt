package com.akib.mycompanion.domain.model

/** A person the user wants quick access to. [phone] is stored in international format, e.g. +8801XXXXXXXXX. */
data class Contact(
    val id: String,
    val name: String,
    val phone: String,
) {
    /** Digits only, as WhatsApp expects (no '+', spaces or dashes). */
    val digits: String get() = phone.filter { it.isDigit() }
}
