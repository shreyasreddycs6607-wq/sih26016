package com.focusforce.app

import com.focusforce.app.domain.export.Csv
import com.focusforce.app.domain.export.FocusSessionRow
import com.focusforce.app.domain.export.UsageExport
import com.focusforce.app.domain.pro.Countable
import com.focusforce.app.domain.pro.EntitlementResolver
import com.focusforce.app.domain.pro.FreeLimits
import com.focusforce.app.domain.pro.ProFeature
import com.focusforce.app.domain.pro.ProPolicy
import com.focusforce.app.domain.usage.DayAppUsage
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    @Test fun plainCellsAreUntouchedAndSpecialOnesQuoted() {
        assertEquals("hello", Csv.cell("hello"))
        assertEquals("\"a,b\"", Csv.cell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.cell("say \"hi\""))
        assertEquals("\"line1\nline2\"", Csv.cell("line1\nline2"))
        assertEquals("", Csv.cell(null))
    }

    @Test fun formulaInjectionIsNeutralisedForTextButNotNumbers() {
        assertEquals("'=SUM(A1)", Csv.cell("=SUM(A1)"))
        assertEquals("'+1", Csv.cell("+1"))
        assertEquals("'-2", Csv.cell("-2"))
        assertEquals("'@cmd", Csv.cell("@cmd"))
        assertEquals("\"'=1+1,2\"", Csv.cell("=1+1,2"))   // still quoted when it also contains a comma
        assertEquals("-5", Csv.cell(-5))                  // real numbers stay numbers
        assertEquals("-2.5", Csv.cell(-2.5))
    }

    @Test fun documentUsesCrlfAndAFinalNewline() {
        assertEquals("a,b\r\n1,x\r\n2,\"y,z\"\r\n", Csv.document(listOf("a", "b"), listOf(listOf(1, "x"), listOf(2, "y,z"))))
    }

    private fun row(date: LocalDate, pkg: String, ms: Long, opens: Int) = DayAppUsage(date, pkg, ms, opens)

    @Test fun usageExportSortsNewestFirstAndRoundsMinutes() {
        val d1 = LocalDate.of(2026, 3, 9); val d2 = LocalDate.of(2026, 3, 10)
        val csv = UsageExport.usageCsv(
            listOf(row(d1, "a.b", 90_000, 2), row(d2, "c.d", 60_000, 1), row(d2, "e.f", 120_000, 3)),
        ) { if (it == "c.d") "=Evil,App" else it.uppercase() }
        val lines = csv.trimEnd().split("\r\n")
        assertEquals("date,package,app,foreground_minutes,opens", lines[0])
        assertEquals("2026-03-10,e.f,E.F,2.0,3", lines[1])
        assertEquals("2026-03-10,c.d,\"'=Evil,App\",1.0,1", lines[2])
        assertEquals("2026-03-09,a.b,A.B,1.5,2", lines[3])
    }

    @Test fun sessionsExport() {
        val csv = UsageExport.sessionsCsv(listOf(FocusSessionRow(LocalDate.of(2026, 3, 10), "2026-03-10T09:00", "2026-03-10T09:25", 25, true)))
        assertEquals("date,start,end,focus_minutes,completed\r\n2026-03-10,2026-03-10T09:00,2026-03-10T09:25,25,true\r\n", csv)
    }
}

class ProPolicyTest {
    @Test fun freeLimitsApplyAndProIsUnlimited() {
        assertTrue(ProPolicy.canAdd(Countable.GUARDED_APPS, FreeLimits.GUARDED_APPS - 1, pro = false))
        assertFalse(ProPolicy.canAdd(Countable.GUARDED_APPS, FreeLimits.GUARDED_APPS, pro = false))
        assertTrue(ProPolicy.canAdd(Countable.GUARDED_APPS, 500, pro = true))
        assertFalse(ProPolicy.canAdd(Countable.SCHEDULES, FreeLimits.SCHEDULES, false))
        assertFalse(ProPolicy.canAdd(Countable.USAGE_LIMITS, FreeLimits.USAGE_LIMITS, false))
    }

    @Test fun remainingAndBulkAdds() {
        assertEquals(2, ProPolicy.remaining(Countable.GUARDED_APPS, 3, false))
        assertEquals(0, ProPolicy.remaining(Countable.GUARDED_APPS, 9, false))  // downgraded with more than the free limit: nothing removed, nothing added
        assertNull(ProPolicy.remaining(Countable.GUARDED_APPS, 3, true))
        assertEquals(2, ProPolicy.allowedToAdd(Countable.GUARDED_APPS, 3, 10, false))
        assertEquals(0, ProPolicy.allowedToAdd(Countable.GUARDED_APPS, 9, 4, false))
        assertEquals(10, ProPolicy.allowedToAdd(Countable.GUARDED_APPS, 3, 10, true))
    }

    @Test fun proFeaturesNeedPro() {
        ProFeature.entries.forEach {
            assertFalse(ProPolicy.hasFeature(it, false))
            assertTrue(ProPolicy.hasFeature(it, true))
        }
    }

    @Test fun entitlementFollowsTheStoreAndToleratesBeingOfflineForAWhile() {
        val now = 100L * 24 * 60 * 60 * 1000
        assertTrue(EntitlementResolver.resolve(true, false, 0, now))
        assertFalse(EntitlementResolver.resolve(false, true, now, now))                    // the store says no: believe it
        assertTrue(EntitlementResolver.resolve(null, true, now - 10L * 24 * 3_600_000, now)) // offline, verified 10 days ago
        assertFalse(EntitlementResolver.resolve(null, true, now - 31L * 24 * 3_600_000, now)) // offline too long
        assertFalse(EntitlementResolver.resolve(null, false, now, now))
        assertFalse(EntitlementResolver.resolve(null, true, now + 1, now))                  // clock went backwards: do not trust
    }
}
