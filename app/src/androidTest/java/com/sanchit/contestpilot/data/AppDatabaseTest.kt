package com.sanchit.contestpilot.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanchit.contestpilot.data.local.AppDatabase
import com.sanchit.contestpilot.data.local.entity.AutomationStatusEntity
import com.sanchit.contestpilot.data.local.entity.ContestEntity
import com.sanchit.contestpilot.data.local.entity.NotificationScheduleEntity
import com.sanchit.contestpilot.data.local.entity.RegistrationStateEntity
import com.sanchit.contestpilot.data.local.entity.SyncStateEntity
import com.sanchit.contestpilot.domain.model.Contest
import com.sanchit.contestpilot.domain.model.ContestDivision
import com.sanchit.contestpilot.domain.model.ContestPhase
import com.sanchit.contestpilot.domain.model.Platform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun contestEntity(
        id: String,
        platform: Platform = Platform.CODEFORCES,
        startTimeSeconds: Long? = 1_800_000_000
    ) = ContestEntity(
        id = Contest.buildId(platform, id),
        platform = platform.name,
        platformContestId = id,
        name = "Contest $id",
        phase = ContestPhase.BEFORE.name,
        startTimeSeconds = startTimeSeconds,
        durationSeconds = 7_200,
        division = ContestDivision.DIV_2.name,
        url = "https://example.invalid/$id",
        fetchedAtEpochSeconds = 1_799_000_000
    )

    @Test
    fun contestsRoundTripThroughTheDomainModel() = runTest {
        val dao = database.contestDao()
        dao.upsertAll(listOf(contestEntity("1")))

        val contest = dao.observeAll().first().single().toDomain()

        assertEquals("CODEFORCES:1", contest.id)
        assertEquals(Platform.CODEFORCES, contest.platform)
        assertEquals(ContestDivision.DIV_2, contest.division)
        assertEquals(ContestPhase.BEFORE, contest.phase)
    }

    @Test
    fun replacingOnePlatformLeavesTheOtherUntouched() = runTest {
        val dao = database.contestDao()
        dao.upsertAll(
            listOf(
                contestEntity("1"),
                contestEntity("2"),
                contestEntity("START1", Platform.CODECHEF)
            )
        )

        dao.replacePlatformContests(
            Platform.CODEFORCES.name,
            listOf(contestEntity("2"))
        )

        assertEquals(
            setOf("CODEFORCES:2", "CODECHEF:START1"),
            dao.observeAll().first().map { it.id }.toSet()
        )
    }

    @Test
    fun replacingWithAnEmptyListClearsOnlyThatPlatform() = runTest {
        val dao = database.contestDao()
        dao.upsertAll(listOf(contestEntity("1"), contestEntity("START1", Platform.CODECHEF)))

        dao.replacePlatformContests(Platform.CODEFORCES.name, emptyList())

        assertEquals(listOf("CODECHEF:START1"), dao.observeAll().first().map { it.id })
    }

    @Test
    fun contestsWithoutAStartTimeSortLast() = runTest {
        val dao = database.contestDao()
        dao.upsertAll(
            listOf(
                contestEntity("later", startTimeSeconds = 1_800_000_200),
                contestEntity("unscheduled", startTimeSeconds = null),
                contestEntity("sooner", startTimeSeconds = 1_800_000_100)
            )
        )

        assertEquals(
            listOf("sooner", "later", "unscheduled"),
            dao.observeAll().first().map { it.platformContestId }
        )
    }

    @Test
    fun registrationStatesSurviveAContestRefreshAndOrphansAreRemoved() = runTest {
        val contestDao = database.contestDao()
        val registrationDao = database.registrationStateDao()

        contestDao.upsertAll(listOf(contestEntity("1")))
        registrationDao.upsertAll(
            listOf(
                RegistrationStateEntity("CODEFORCES:1", "REGISTERED", null, 1_800_000_000),
                RegistrationStateEntity("CODEFORCES:999", "REGISTERED", null, 1_800_000_000)
            )
        )

        registrationDao.deleteOrphans()

        assertEquals(
            listOf("CODEFORCES:1"),
            registrationDao.observeAll().first().map { it.contestId }
        )
    }

    @Test
    fun notificationSchedulesTrackDeliveryPerContest() = runTest {
        val dao = database.notificationScheduleDao()
        val schedule = NotificationScheduleEntity(
            contestId = "CODEFORCES:1",
            contestName = "Codeforces Round 1",
            platformName = "Codeforces",
            requestCode = 42,
            triggerAtEpochMillis = 1_800_000_000_000,
            contestStartEpochMillis = 1_800_000_600_000,
            leadMinutes = 10,
            delivered = false,
            exact = true,
            scheduledAtEpochMillis = 1_799_000_000_000
        )
        dao.upsert(schedule)

        assertEquals(1, dao.getPending().size)

        dao.markDelivered("CODEFORCES:1")

        assertTrue(dao.getPending().isEmpty())
        assertTrue(dao.getAll().single().delivered)
    }

    @Test
    fun upsertingAScheduleReplacesTheEarlierOne() = runTest {
        val dao = database.notificationScheduleDao()
        val schedule = NotificationScheduleEntity(
            contestId = "CODEFORCES:1",
            contestName = "Codeforces Round 1",
            platformName = "Codeforces",
            requestCode = 42,
            triggerAtEpochMillis = 1_800_000_000_000,
            contestStartEpochMillis = 1_800_000_600_000,
            leadMinutes = 10,
            delivered = false,
            exact = true,
            scheduledAtEpochMillis = 1_799_000_000_000
        )
        dao.upsert(schedule)
        dao.upsert(schedule.copy(triggerAtEpochMillis = 1_800_000_111_000))

        assertEquals(1_800_000_111_000, dao.getAll().single().triggerAtEpochMillis)
    }

    @Test
    fun deletingASchedulePreventsItFromBeingRestored() = runTest {
        val dao = database.notificationScheduleDao()
        dao.upsert(
            NotificationScheduleEntity(
                contestId = "CODEFORCES:1",
                contestName = "Codeforces Round 1",
                platformName = "Codeforces",
                requestCode = 42,
                triggerAtEpochMillis = 1_800_000_000_000,
                contestStartEpochMillis = 1_800_000_600_000,
                leadMinutes = 10,
                delivered = false,
                exact = false,
                scheduledAtEpochMillis = 1_799_000_000_000
            )
        )

        dao.deleteByContestId("CODEFORCES:1")

        assertTrue(dao.getAll().isEmpty())
        assertTrue(dao.getPending().isEmpty())
    }

    @Test
    fun syncStateIsStoredPerSource() = runTest {
        val dao = database.syncStateDao()
        dao.upsert(SyncStateEntity("CODEFORCES", "SUCCESS", 100, 100, null))
        dao.upsert(SyncStateEntity("CODECHEF", "FAILED", null, 200, "offline"))
        dao.upsert(SyncStateEntity("CODEFORCES", "FAILED", 100, 300, "offline"))

        val states = dao.observeAll().first().associateBy { it.source }

        assertEquals(2, states.size)
        assertEquals(100L, states.getValue("CODEFORCES").lastSuccessEpochSeconds)
        assertEquals(300L, states.getValue("CODEFORCES").lastAttemptEpochSeconds)
        assertNull(states.getValue("CODECHEF").lastSuccessEpochSeconds)
    }

    @Test
    fun automationStatusKeepsASingleRow() = runTest {
        val dao = database.automationStatusDao()
        dao.upsert(
            AutomationStatusEntity(
                runStatus = "SUCCESS",
                message = "ok",
                generatedAtEpochSeconds = 100,
                workflowRunUrl = null,
                codeforcesHandle = "tourist",
                registrationsAttempted = 1,
                registrationsSucceeded = 1,
                registrationsFailed = 0
            )
        )
        dao.upsert(
            AutomationStatusEntity(
                runStatus = "FAILED",
                message = "later",
                generatedAtEpochSeconds = 200,
                workflowRunUrl = null,
                codeforcesHandle = "tourist",
                registrationsAttempted = 2,
                registrationsSucceeded = 0,
                registrationsFailed = 2
            )
        )

        val status = requireNotNull(dao.observe().first()).toDomain()

        assertEquals(200L, status.generatedAtEpochSeconds)
        assertEquals(2, status.registrationsFailed)
    }

    @Test
    fun anUnknownStoredEnumFallsBackInsteadOfCrashing() = runTest {
        val dao = database.syncStateDao()
        dao.upsert(SyncStateEntity("NOT_A_SOURCE", "SUCCESS", 1, 1, null))

        assertNull(dao.observeAll().first().single().toDomain())
        assertFalse(dao.observeAll().first().isEmpty())
    }
}
