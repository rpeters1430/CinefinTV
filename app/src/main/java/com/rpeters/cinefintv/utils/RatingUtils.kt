package com.rpeters.cinefintv.utils

import java.util.Locale

private val knownOfficialRatings = setOf(
    "G",
    "PG",
    "PG-13",
    "R",
    "NC-17",
    "TV-Y",
    "TV-Y7",
    "TV-G",
    "TV-PG",
    "TV-14",
    "TV-MA",
    "NR",
    "UR",
)

private val officialRatingAliases = mapOf(
    "PG13" to "PG-13",
    "NC17" to "NC-17",
    "NOT RATED" to "NR",
    "UNRATED" to "NR",
)

private val countryPrefixes = listOf(
    "US",
    "DE",
    "UK",
    "GB",
    "FR",
    "ES",
    "IT",
    "NL",
    "SE",
    "NO",
    "FI",
    "DK",
    "AU",
    "CA",
    "NZ",
    "BR",
    "MX",
)

private val systemPrefixes = listOf(
    "FSK",
    "PEGI",
    "BBFC",
)

fun normalizeOfficialRating(rawRating: String?): String? {
    if (rawRating.isNullOrBlank()) {
        return null
    }

    val trimmed = rawRating.trim()
    if (trimmed.isEmpty()) {
        return null
    }

    val upper = trimmed.uppercase(Locale.ROOT)
    officialRatingAliases[upper]?.let { return it }
    if (upper in knownOfficialRatings) {
        return upper
    }

    val countryPattern = "^(" + countryPrefixes.joinToString("|") + ")-"
    val systemPattern = "^(" + systemPrefixes.joinToString("|") + ")[ -]?"
    val stripped = upper
        .replaceFirst(Regex(countryPattern), "")
        .replaceFirst(Regex(systemPattern), "")
        .trim()

    officialRatingAliases[stripped]?.let { return it }
    if (stripped in knownOfficialRatings) {
        return stripped
    }

    val numericMatch = Regex("^-?(\\d{1,2})\\+?$").matchEntire(stripped)
    if (numericMatch != null) {
        val number = numericMatch.groupValues[1]
        return "$number+"
    }

    return stripped.ifBlank { null }
}
