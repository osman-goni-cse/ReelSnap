package com.reelsnap

/** Apps in which the capture loop is allowed to run. */
enum class TargetApp(val packageName: String, val label: String, val shortName: String) {
    INSTAGRAM("com.instagram.android", "Instagram", "ig"),
    INSTAGRAM_LITE("com.instagram.lite", "Instagram Lite", "iglite"),
    FACEBOOK("com.facebook.katana", "Facebook", "fb"),
    FACEBOOK_LITE("com.facebook.lite", "Facebook Lite", "fblite");

    companion object {
        val allPackages: Set<String> = entries.map { it.packageName }.toSet()
        fun fromPackage(pkg: String?): TargetApp? = entries.firstOrNull { it.packageName == pkg }
    }
}
