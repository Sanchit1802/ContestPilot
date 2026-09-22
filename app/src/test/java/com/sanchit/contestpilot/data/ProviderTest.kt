package com.sanchit.contestpilot.data

import com.sanchit.contestpilot.data.provider.CodeChefContestProvider
import com.sanchit.contestpilot.data.provider.CodeforcesContestProvider
import com.sanchit.contestpilot.data.provider.ProviderError
import com.sanchit.contestpilot.data.provider.ProviderResult
import com.sanchit.contestpilot.data.remote.codechef.CodeChefApi
import com.sanchit.contestpilot.data.remote.codechef.CodeChefContestDto
import com.sanchit.contestpilot.data.remote.codechef.CodeChefContestListResponse
import com.sanchit.contestpilot.data.remote.codeforces.CodeforcesApi
import com.sanchit.contestpilot.data.remote.codeforces.CodeforcesContestDto
import com.sanchit.contestpilot.data.remote.codeforces.CodeforcesContestListResponse
import com.sanchit.contestpilot.domain.model.ContestDivision
import com.sanchit.contestpilot.domain.model.ContestPhase
import com.sanchit.contestpilot.domain.model.Platform
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.OffsetDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeCodeforcesApi(
    private val response: (() -> CodeforcesContestListResponse)
) : CodeforcesApi {
    override suspend fun getContests(gym: Boolean): CodeforcesContestListResponse = response()
}

private class FakeCodeChefApi(
    private val response: (() -> CodeChefContestListResponse)
) : CodeChefApi {
    override suspend fun getContests(
        sortBy: String,
        sortingOrder: String,
        offset: Int,
        mode: String
    ): CodeChefContestListResponse = response()
}

class CodeforcesContestProviderTest {

    @Test
    fun `contests are mapped with their division and canonical url`() = runTest {
        val provider = CodeforcesContestProvider(
            FakeCodeforcesApi {
                CodeforcesContestListResponse(
                    status = "OK",
                    comment = null,
                    result = listOf(
                        CodeforcesContestDto(
                            id = 1234,
                            name = "Codeforces Round 999 (Div. 1 + Div. 2)",
                            phase = "BEFORE",
                            durationSeconds = 7_200,
                            startTimeSeconds = 1_800_000_000
                        )
                    )
                )
            }
        )

        val result = provider.fetchContests() as ProviderResult.Success
        val contest = result.contests.single()

        assertEquals("CODEFORCES:1234", contest.id)
        assertEquals(Platform.CODEFORCES, contest.platform)
        assertEquals(ContestDivision.DIV_1_2, contest.division)
        assertEquals(ContestPhase.BEFORE, contest.phase)
        assertEquals("https://codeforces.com/contest/1234", contest.url)
    }

    @Test
    fun `an unrecognised phase becomes UNKNOWN rather than a guess`() = runTest {
        val provider = CodeforcesContestProvider(
            FakeCodeforcesApi {
                CodeforcesContestListResponse(
                    "OK",
                    null,
                    listOf(
                        CodeforcesContestDto(1, "Round", "SOMETHING_NEW", 7_200, 1_800_000_000)
                    )
                )
            }
        )

        val result = provider.fetchContests() as ProviderResult.Success

        assertEquals(ContestPhase.UNKNOWN, result.contests.single().phase)
    }

    @Test
    fun `a contest missing its id or name is dropped instead of faked`() = runTest {
        val provider = CodeforcesContestProvider(
            FakeCodeforcesApi {
                CodeforcesContestListResponse(
                    "OK",
                    null,
                    listOf(
                        CodeforcesContestDto(null, "No id", "BEFORE", 7_200, 1L),
                        CodeforcesContestDto(2, null, "BEFORE", 7_200, 1L),
                        CodeforcesContestDto(3, "  ", "BEFORE", 7_200, 1L),
                        CodeforcesContestDto(4, "Good", "BEFORE", 7_200, 1L)
                    )
                )
            }
        )

        val result = provider.fetchContests() as ProviderResult.Success

        assertEquals(listOf("4"), result.contests.map { it.platformContestId })
    }

    @Test
    fun `a contest with no announced start time is kept with a null start`() = runTest {
        val provider = CodeforcesContestProvider(
            FakeCodeforcesApi {
                CodeforcesContestListResponse(
                    "OK",
                    null,
                    listOf(CodeforcesContestDto(7, "Unscheduled", "BEFORE", 7_200, null))
                )
            }
        )

        val result = provider.fetchContests() as ProviderResult.Success

        assertEquals(null, result.contests.single().startTimeSeconds)
    }

    @Test
    fun `a FAILED envelope surfaces the API comment`() = runTest {
        val provider = CodeforcesContestProvider(
            FakeCodeforcesApi {
                CodeforcesContestListResponse("FAILED", "gym: Field should be boolean", null)
            }
        )

        val result = provider.fetchContests() as ProviderResult.Failure

        assertEquals(ProviderError.SERVICE_UNAVAILABLE, result.error)
        assertEquals("gym: Field should be boolean", result.message)
    }

    @Test
    fun `an OK envelope with no result is treated as malformed`() = runTest {
        val provider = CodeforcesContestProvider(
            FakeCodeforcesApi { CodeforcesContestListResponse("OK", null, null) }
        )

        val result = provider.fetchContests() as ProviderResult.Failure

        assertEquals(ProviderError.MALFORMED_RESPONSE, result.error)
    }

    @Test
    fun `network failures are classified and never thrown at the caller`() = runTest {
        val timeout = CodeforcesContestProvider(
            FakeCodeforcesApi { throw SocketTimeoutException("too slow") }
        ).fetchContests() as ProviderResult.Failure
        assertEquals(ProviderError.TIMEOUT, timeout.error)

        val offline = CodeforcesContestProvider(
            FakeCodeforcesApi { throw IOException("offline") }
        ).fetchContests() as ProviderResult.Failure
        assertEquals(ProviderError.NETWORK_UNAVAILABLE, offline.error)
    }

    @Test
    fun `failure messages never contain exception detail`() = runTest {
        val result = CodeforcesContestProvider(
            FakeCodeforcesApi { throw IOException("connect to 10.0.0.1:443 failed") }
        ).fetchContests() as ProviderResult.Failure

        assertTrue(result.message == ProviderError.NETWORK_UNAVAILABLE.userMessage)
    }
}

class CodeChefContestProviderTest {

    private fun dto(
        code: String? = "START257",
        name: String? = "Starters 257",
        start: String? = "2026-09-23T20:00:00+05:30",
        end: String? = "2026-09-23T22:00:00+05:30",
        duration: String? = "120"
    ) = CodeChefContestDto(code, name, start, end, duration)

    @Test
    fun `future and present contests are mapped with the right phase`() = runTest {
        val provider = CodeChefContestProvider(
            FakeCodeChefApi {
                CodeChefContestListResponse(
                    status = "success",
                    message = null,
                    futureContests = listOf(dto()),
                    presentContests = listOf(dto(code = "START256", name = "Starters 256"))
                )
            }
        )

        val result = provider.fetchContests() as ProviderResult.Success

        assertEquals(2, result.contests.size)
        assertEquals(ContestPhase.BEFORE, result.contests[0].phase)
        assertEquals(ContestPhase.CODING, result.contests[1].phase)
        assertTrue(result.contests.all { it.platform == Platform.CODECHEF })
    }

    @Test
    fun `the ISO start time is converted to an absolute epoch second`() = runTest {
        val provider = CodeChefContestProvider(
            FakeCodeChefApi {
                CodeChefContestListResponse("success", null, listOf(dto()), emptyList())
            }
        )

        val result = provider.fetchContests() as ProviderResult.Success

        assertEquals(
            OffsetDateTime.parse("2026-09-23T20:00:00+05:30").toEpochSecond(),
            result.contests.single().startTimeSeconds
        )
        assertEquals(7_200L, result.contests.single().durationSeconds)
        assertEquals("https://www.codechef.com/START257", result.contests.single().url)
    }

    @Test
    fun `duration falls back to the reported minutes when the end time is unusable`() = runTest {
        val provider = CodeChefContestProvider(
            FakeCodeChefApi {
                CodeChefContestListResponse(
                    "success",
                    null,
                    listOf(dto(end = null, duration = "180")),
                    emptyList()
                )
            }
        )

        val result = provider.fetchContests() as ProviderResult.Success

        assertEquals(10_800L, result.contests.single().durationSeconds)
    }

    @Test
    fun `CodeChef contests are never assigned a Codeforces division`() = runTest {
        val provider = CodeChefContestProvider(
            FakeCodeChefApi {
                CodeChefContestListResponse(
                    "success",
                    null,
                    listOf(dto(name = "Some Div 2 sounding contest")),
                    emptyList()
                )
            }
        )

        val result = provider.fetchContests() as ProviderResult.Success

        assertEquals(ContestDivision.OTHER, result.contests.single().division)
    }

    @Test
    fun `rows with an unparseable or missing start time are dropped`() = runTest {
        val provider = CodeChefContestProvider(
            FakeCodeChefApi {
                CodeChefContestListResponse(
                    "success",
                    null,
                    listOf(
                        dto(code = "A", start = "not-a-date"),
                        dto(code = "B", start = null),
                        dto(code = null),
                        dto(code = "D", name = null),
                        dto(code = "E")
                    ),
                    emptyList()
                )
            }
        )

        val result = provider.fetchContests() as ProviderResult.Success

        assertEquals(listOf("E"), result.contests.map { it.platformContestId })
    }

    @Test
    fun `a non-success status is reported as a service failure`() = runTest {
        val provider = CodeChefContestProvider(
            FakeCodeChefApi {
                CodeChefContestListResponse("error", "Something broke", null, null)
            }
        )

        val result = provider.fetchContests() as ProviderResult.Failure

        assertEquals(ProviderError.SERVICE_UNAVAILABLE, result.error)
        assertEquals("Something broke", result.message)
    }

    @Test
    fun `an empty listing is a success with no contests`() = runTest {
        val provider = CodeChefContestProvider(
            FakeCodeChefApi { CodeChefContestListResponse("success", null, null, null) }
        )

        val result = provider.fetchContests() as ProviderResult.Success

        assertTrue(result.contests.isEmpty())
    }
}
