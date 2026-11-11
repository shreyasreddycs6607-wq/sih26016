package com.focusforce.app.permissions

import android.os.Build

/** OEMs whose battery/auto-start managers are known to kill or restrict blockers. */
enum class Oem(val tipsKey: String) {
    XIAOMI("xiaomi"), OPPO("oppo"), VIVO("vivo"), SAMSUNG("samsung"), REALME("realme");

    companion object {
        /** Pure function so it is unit-testable; [detect] feeds it the device values. */
        fun fromBuild(manufacturer: String, brand: String): Oem? {
            val key = "$manufacturer $brand".lowercase()
            return when {
                // realme must be tested before oppo: realme devices historically list oppo in some fields.
                "realme" in key -> REALME
                "xiaomi" in key || "redmi" in key || "poco" in key -> XIAOMI
                "oppo" in key -> OPPO
                "vivo" in key || "iqoo" in key -> VIVO
                "samsung" in key -> SAMSUNG
                else -> null
            }
        }

        fun detect(): Oem? = fromBuild(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty())
    }
}
