package com.softeen.nflocospicks.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.softeen.nflocospicks.BuildConfig
import com.softeen.nflocospicks.data.remote.espn.EspnApiService
import com.softeen.nflocospicks.data.remote.espn.EspnCompetition
import com.softeen.nflocospicks.data.remote.espn.EspnCompetitor
import com.softeen.nflocospicks.data.remote.espn.EspnEvent
import com.softeen.nflocospicks.data.remote.espn.EspnScoreboardResponse
import com.softeen.nflocospicks.data.remote.espn.EspnSeason
import com.softeen.nflocospicks.data.remote.espn.EspnStatus
import com.softeen.nflocospicks.data.remote.espn.EspnStatusType
import com.softeen.nflocospicks.data.remote.espn.EspnTeam
import com.softeen.nflocospicks.data.remote.espn.EspnWeek
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.model.SeasonType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Instant

class ScheduleRepositoryImplTest {

    private val api = mockk<EspnApiService>()
    private val firestore = mockk<FirebaseFirestore>()
    private val repo = ScheduleRepositoryImpl(api, firestore)

    private companion object {
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
        const val EVENT_DATE = "2025-09-07T17:00Z"
        // Exact instant EVENT_DATE resolves to — assertions use this, never `now`.
        val EVENT_KICKOFF_MS = Instant.parse("2025-09-07T17:00:00Z").toEpochMilli()
        const val EXPECTED_WEEK_ID = "2025-week-01" // season.type=2, week.number=1, year from kickoff
    }

    private fun event(id: String, completed: Boolean, statusName: String): EspnEvent = EspnEvent(
        id = id,
        date = EVENT_DATE,
        season = EspnSeason(type = 2),
        week = EspnWeek(number = 1),
        competitions = listOf(
            EspnCompetition(
                competitors = listOf(
                    EspnCompetitor(
                        homeAway = "home", score = null,
                        team = EspnTeam(displayName = "Home $id", abbreviation = "H$id")
                    ),
                    EspnCompetitor(
                        homeAway = "away", score = null,
                        team = EspnTeam(displayName = "Away $id", abbreviation = "A$id")
                    )
                ),
                status = EspnStatus(type = EspnStatusType(name = statusName, completed = completed))
            )
        )
    )

    /** Stubs `groups/g1/weeks/{EXPECTED_WEEK_ID}` and returns the leaf doc ref. */
    private fun stubFirestoreChain(): DocumentReference {
        val groupsCol = mockk<CollectionReference>()
        val groupDoc = mockk<DocumentReference>()
        val weeksCol = mockk<CollectionReference>()
        val weekDoc = mockk<DocumentReference>()
        every { firestore.collection("groups") } returns groupsCol
        every { groupsCol.document("g1") } returns groupDoc
        every { groupDoc.collection("weeks") } returns weeksCol
        every { weeksCol.document(EXPECTED_WEEK_ID) } returns weekDoc
        every { weekDoc.set(any()) } returns Tasks.forResult<Void>(null)
        return weekDoc
    }

    @Test
    fun `getCurrentWeekGames applies the debug kickoff offset only to SCHEDULED games`() = runBlocking {
        // withDebugKickoffOffset is gated on BuildConfig.DEBUG. This project only
        // has a debug unit-test variant, so DEBUG is true here; guard it anyway so
        // the test is skipped (not failed) if a release unit-test variant is ever added.
        assumeTrue(BuildConfig.DEBUG)
        stubFirestoreChain()
        coEvery { api.getScoreboard(any()) } returns EspnScoreboardResponse(
            events = listOf(
                event("1", completed = false, statusName = "STATUS_SCHEDULED"),
                event("2", completed = true, statusName = "STATUS_FINAL")
            )
        )

        val now = System.currentTimeMillis()
        val games = repo.getCurrentWeekGames("g1")

        val scheduled = games.first { it.status == GameStatus.SCHEDULED }
        val finalGame = games.first { it.status == GameStatus.FINAL }

        // SCHEDULED game was pushed ~24h into the future (relative to test start).
        assertThat(scheduled.kickoffTime).isGreaterThan(now + ONE_DAY_MS - 60_000L)
        assertThat(scheduled.kickoffTime).isLessThan(System.currentTimeMillis() + ONE_DAY_MS + 60_000L)
        // FINAL game keeps its real kickoff — offset NOT applied.
        assertThat(finalGame.kickoffTime).isEqualTo(EVENT_KICKOFF_MS)
    }

    @Test
    fun `getCurrentWeekGames caches the games under groups weeks weekId`() = runBlocking {
        val weekDoc = stubFirestoreChain()
        coEvery { api.getScoreboard(any()) } returns EspnScoreboardResponse(
            events = listOf(event("1", completed = false, statusName = "STATUS_SCHEDULED"))
        )

        repo.getCurrentWeekGames("g1")

        verify { firestore.collection("groups") }
        verify {
            weekDoc.set(match { payload ->
                payload is Map<*, *> && payload.containsKey("games")
            })
        }
    }

    @Test
    fun `getCurrentWeekGames still returns games when the Firestore write Task fails`() = runBlocking {
        // Realistic failure: collection().document()... resolves, but the set() Task
        // fails and .await() throws inside cacheGamesToFirestore's try/catch.
        val groupsCol = mockk<CollectionReference>()
        val groupDoc = mockk<DocumentReference>()
        val weeksCol = mockk<CollectionReference>()
        val weekDoc = mockk<DocumentReference>()
        every { firestore.collection("groups") } returns groupsCol
        every { groupsCol.document("g1") } returns groupDoc
        every { groupDoc.collection("weeks") } returns weeksCol
        every { weeksCol.document(EXPECTED_WEEK_ID) } returns weekDoc
        every { weekDoc.set(any()) } returns Tasks.forException(RuntimeException("write denied"))

        coEvery { api.getScoreboard(any()) } returns EspnScoreboardResponse(
            events = listOf(event("1", completed = false, statusName = "STATUS_SCHEDULED"))
        )

        val games = repo.getCurrentWeekGames("g1")

        assertThat(games).hasSize(1)
    }

    @Test
    fun `getCurrentWeekGames does not touch Firestore when the schedule is empty`() = runBlocking {
        coEvery { api.getScoreboard(any()) } returns EspnScoreboardResponse(events = emptyList())

        val games = repo.getCurrentWeekGames("g1")

        assertThat(games).isEmpty()
        verify(exactly = 0) { firestore.collection(any()) }
    }

    @Test
    fun `getGamesForWeek does not apply the debug offset and never caches`() = runBlocking {
        coEvery { api.getScoreboardForWeek(any(), any()) } returns EspnScoreboardResponse(
            events = listOf(event("1", completed = false, statusName = "STATUS_SCHEDULED"))
        )

        val games = repo.getGamesForWeek(SeasonType.REGULAR, 5)

        // Kickoff is the exact mocked instant, NOT now + 24h — no offset here.
        assertThat(games.single().kickoffTime).isEqualTo(EVENT_KICKOFF_MS)
        verify(exactly = 0) { firestore.collection(any()) }
    }

    @Test
    fun `getGamesForWeek maps the SeasonType to the ESPN seasontype param`() = runBlocking {
        coEvery { api.getScoreboardForWeek(any(), any()) } returns EspnScoreboardResponse(events = emptyList())

        repo.getGamesForWeek(SeasonType.PRESEASON, 3)
        repo.getGamesForWeek(SeasonType.REGULAR, 10)
        repo.getGamesForWeek(SeasonType.POSTSEASON, 2)

        coVerify(exactly = 1) { api.getScoreboardForWeek(1, 3) }
        coVerify(exactly = 1) { api.getScoreboardForWeek(2, 10) }
        coVerify(exactly = 1) { api.getScoreboardForWeek(3, 2) }
    }
}
