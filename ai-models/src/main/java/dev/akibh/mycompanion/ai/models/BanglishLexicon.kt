package dev.akibh.mycompanion.ai.models

import java.util.Locale

/**
 * Exact Bangla for common Banglish words, checked before the IndicXlit model.
 *
 * The model is good with names and rarer words but gets some of the most frequent ones wrong:
 * it writes "ch" as চ where Bangla needs ছ (achi → আচি instead of আছি), r as র where ড় is meant
 * (bari → বারি), and breaks short particles (ta → টিএ, e → ই). Those words have one right spelling, so
 * they come from this list, with no model load and no guessing. Everything else still goes to the model.
 */
object BanglishLexicon {

    /** The Bangla for [word] (any case), or null when the model should decide. */
    fun lookup(word: String): String? = WORDS[word.lowercase(Locale.ROOT)]

    private val WORDS: Map<String, String> = mapOf(
        // People
        "ami" to "আমি", "tumi" to "তুমি", "apni" to "আপনি", "amra" to "আমরা", "tomra" to "তোমরা",
        "amar" to "আমার", "tomar" to "তোমার", "apnar" to "আপনার", "amader" to "আমাদের", "tomader" to "তোমাদের",
        "amake" to "আমাকে", "tomake" to "তোমাকে", "apnake" to "আপনাকে", "se" to "সে", "tar" to "তার",
        "bhai" to "ভাই", "apu" to "আপু", "ammu" to "আম্মু", "amma" to "আম্মা", "abbu" to "আব্বু",
        // To be
        "achi" to "আছি", "acho" to "আছো", "achen" to "আছেন", "ache" to "আছে",
        "achhi" to "আছি", "achho" to "আছো", "achhe" to "আছে", "chilo" to "ছিল", "chilam" to "ছিলাম",
        "hobe" to "হবে", "hoy" to "হয়", "holo" to "হলো", "hoye" to "হয়ে", "hoyeche" to "হয়েছে",
        // Doing
        "kori" to "করি", "koro" to "করো", "kore" to "করে", "korchi" to "করছি", "korcho" to "করছো",
        "korbo" to "করবো", "korbe" to "করবে", "korechi" to "করেছি", "korecho" to "করেছো",
        "jabo" to "যাবো", "jao" to "যাও", "jachi" to "যাচ্ছি", "jacchi" to "যাচ্ছি", "gechi" to "গেছি",
        "ashchi" to "আসছি", "aschi" to "আসছি", "asho" to "আসো", "aso" to "আসো", "ashbo" to "আসবো",
        "asbo" to "আসবো", "eso" to "এসো",
        "boli" to "বলি", "bolo" to "বলো", "bolchi" to "বলছি", "bolbo" to "বলবো",
        "dekho" to "দেখো", "dekha" to "দেখা", "dekhbo" to "দেখবো", "dekhechi" to "দেখেছি",
        "khabo" to "খাবো", "khaiso" to "খাইছো", "kheyecho" to "খেয়েছো", "khabar" to "খাবার",
        "pathao" to "পাঠাও", "pathabo" to "পাঠাবো", "pathacchi" to "পাঠাচ্ছি",
        // Asking
        "ki" to "কি", "kemon" to "কেমন", "kothay" to "কোথায়", "kothai" to "কোথায়", "kobe" to "কবে",
        "keno" to "কেন", "ke" to "কে",
        // When and where
        "aj" to "আজ", "ajke" to "আজকে", "kal" to "কাল", "kalke" to "কালকে", "ekhon" to "এখন",
        "ekhuni" to "এখুনি", "ekhane" to "এখানে", "sekhane" to "সেখানে", "okhane" to "ওখানে",
        // Things
        "bari" to "বাড়ি", "basa" to "বাসা", "bangla" to "বাংলা", "brishti" to "বৃষ্টি", "nam" to "নাম",
        // Yes, no, glue
        "na" to "না", "hae" to "হ্যাঁ", "haa" to "হ্যাঁ", "accha" to "আচ্ছা", "acha" to "আচ্ছা", "thik" to "ঠিক",
        "bhalo" to "ভালো", "valo" to "ভালো", "onek" to "অনেক", "ektu" to "একটু", "shob" to "সব", "sob" to "সব",
        "kintu" to "কিন্তু", "tai" to "তাই", "jodi" to "যদি", "tahole" to "তাহলে", "ar" to "আর",
        "dhonnobad" to "ধন্যবাদ",
        // Particles: the model breaks these single syllables
        "ta" to "টা", "ti" to "টি", "e" to "এ", "te" to "তে", "er" to "এর", "o" to "ও", "to" to "তো",
    )
}
