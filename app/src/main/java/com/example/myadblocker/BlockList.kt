package com.example.myadblocker

object BlockList {

    val domains = setOf(

        // Advertising
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adnxs.com",
        "adsrvr.org",
        "adform.net",
        "criteo.com",
        "taboola.com",
        "outbrain.com",
        "amazon-adsystem.com",

        // Tracking
        "scorecardresearch.com",
        "zedo.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "casalemedia.com",
        "adsafeprotected.com",
        "advertising.com",

        // Common analytics/ad tracking
        "quantserve.com",
        "mathtag.com",
        "demdex.net",
        "everesttech.net",
        "bluekai.com",
        "turn.com",
        "rlcdn.com"
    )

    fun isBlocked(host: String): Boolean {

        val normalized =
            host
                .lowercase()
                .trimEnd('.')

        return domains.any { domain ->

            normalized == domain ||
            normalized.endsWith(
                ".$domain"
            )
        }
    }
}

