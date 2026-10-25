package com.akib.mycompanion.core.util

/** The entry with this stored name, or null (names of entries removed in a later version read as null). */
inline fun <reified E : Enum<E>> enumOf(name: String?): E? = enumValues<E>().firstOrNull { it.name == name }

/** Stored names back to entries, skipping unknown ones. */
inline fun <reified E : Enum<E>> enumsOf(
    names: Collection<String>?
): Set<E>? = names?.mapNotNull { enumOf<E>(it) }?.toSet()
