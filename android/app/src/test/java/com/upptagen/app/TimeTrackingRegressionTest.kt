package com.upptagen.app

import android.app.Application
import androidx.room.Room
import com.upptagen.app.data.AppDatabase
import com.upptagen.app.data.csv.CsvExporter
import com.upptagen.app.data.csv.CsvImporter
import com.upptagen.app.data.model.TagEntity
import com.upptagen.app.data.model.TimeEntryEntity
import com.upptagen.app.data.model.TimeEntryWithTags
import com.upptagen.app.data.repository.TimeTrackingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class TimeTrackingRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: TimeTrackingRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = TimeTrackingRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun failedStopRollsBackAndCanBeRetried() = runBlocking {
        repository.startTimer("Rust", "Ownership", listOf("book")).getOrThrow()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_stop BEFORE DELETE ON active_timer BEGIN SELECT RAISE(ABORT, 'simulated failure'); END"
        )
        assertTrue(repository.stopTimer().isFailure)
        assertTrue(repository.getAllEntriesWithTags().isEmpty())
        assertTrue(db.tagDao().getAllTagsSync().isEmpty())
        assertNotNull(repository.getActiveTimer())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_stop")
        repository.stopTimer().getOrThrow()
        assertTrue(repository.stopTimer().isFailure)
        assertEquals(1, repository.getAllEntriesWithTags().size)
    }

    @Test
    fun concurrentStopsSaveOneEntry() = runBlocking {
        repository.startTimer("Rust", null, emptyList()).getOrThrow()
        val results = (1..2).map { async(Dispatchers.Default) { repository.stopTimer() } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, repository.getAllEntriesWithTags().size)
        assertNull(repository.getActiveTimer())
    }

    @Test
    fun concurrentStartsDoNotReplaceAnExistingTimer() = runBlocking {
        val results = (1..2).map { index ->
            async(Dispatchers.Default) { repository.startTimer("Rust $index", null, emptyList()) }
        }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(results.single { it.isSuccess }.getOrThrow(), repository.getActiveTimer())
    }

    @Test
    fun csvRoundTripPreservesMultilineFieldsAndSkipsDuplicates() = runBlocking {
        val description = "Read \"ownership\", then practise\r\nBorrowing\nNext step"
        val entry = TimeEntryWithTags(
            TimeEntryEntity(
                id = 1, project = "Rust", description = description,
                startTime = "2026-10-26T10:00:00.123Z", endTime = "2026-10-26T11:00:00.456Z",
                durationSeconds = 3600
            ),
            listOf(TagEntity(id = 1, name = "book"), TagEntity(id = 2, name = "rust"))
        )
        val csv = CsvExporter.exportToCsvString(listOf(entry))
        assertEquals(1, CsvImporter.importCsv(repository, csv.byteInputStream()).getOrThrow())
        val saved = repository.getAllEntriesWithTags().single()
        assertEquals(description, saved.entry.description)
        assertEquals(entry.entry.startTime, saved.entry.startTime)
        assertEquals(listOf("book", "rust"), saved.tagNames)
        assertEquals(0, CsvImporter.importCsv(repository, csv.byteInputStream()).getOrThrow())
    }

    @Test
    fun invalidCsvRowsFailAndRollBackEarlierRows() = runBlocking {
        for (badRow in listOf(
            "Rust,Bad,not-a-date,2026-10-26T11:00:00Z",
            "Rust,Bad,2026-10-26T12:00:00Z,2026-10-26T11:00:00Z",
            "Rust,Missing columns"
        )) {
            val csv = "Project,Description,Start Time,End Time\n" +
                "Rust,Valid,2026-10-26T09:00:00Z,2026-10-26T10:00:00Z\n" + badRow + "\n"
            val result = CsvImporter.importCsv(repository, csv.byteInputStream())
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("CSV record 2"))
            assertTrue(repository.getAllEntriesWithTags().isEmpty())
        }
    }

    @Test
    fun csvWriteFailureIsReported() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_import BEFORE INSERT ON entries BEGIN SELECT RAISE(ABORT, 'simulated failure'); END"
        )
        val csv = "Project,Start Time,End Time\nRust,2026-10-26T10:00:00Z,2026-10-26T11:00:00Z\n"
        val result = CsvImporter.importCsv(repository, csv.byteInputStream())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("CSV record 1"))
        assertTrue(repository.getAllEntriesWithTags().isEmpty())
    }

    @Test
    fun malformedQuotedCsvFailsWithoutSavingEntries() = runBlocking {
        val csv = "Project,Start Time,End Time\n\"unclosed,2026-10-26T10:00:00Z,2026-10-26T11:00:00Z\n"
        assertTrue(CsvImporter.importCsv(repository, csv.byteInputStream()).isFailure)
        assertTrue(repository.getAllEntriesWithTags().isEmpty())
    }
    @Test
    fun metadataEditPreservesTimestampsAndRecordedDuration() = runBlocking {
        val original = TimeEntryEntity(
            project = "Rust", description = "Old description",
            startTime = "2026-10-26T10:00:59.900Z", endTime = "2026-10-26T10:01:01.100Z",
            durationSeconds = 2
        )
        val id = db.timeEntryDao().insertEntry(original)
        val start = java.time.Instant.parse(original.startTime)
        val end = java.time.Instant.parse(original.endTime)
        val zone = java.time.ZoneId.of("Europe/Stockholm")
        val edited = com.upptagen.app.util.DateTimeUtils.editedEntryTimes(
            start, end, start.atZone(zone).toLocalDate(),
            start.atZone(zone).toLocalTime(), end.atZone(zone).toLocalTime(), zone
        )
        repository.editEntry(id, null, "New description", edited.first, edited.second, null).getOrThrow()
        val saved = repository.getAllEntriesWithTags().single().entry
        assertEquals(original.startTime, saved.startTime)
        assertEquals(original.endTime, saved.endTime)
        assertEquals(original.durationSeconds, saved.durationSeconds)
        assertEquals("New description", saved.description)
    }

    @Test
    fun emptyCsvIsANoOpAndMissingHeadersFail() = runBlocking {
        assertEquals(0, CsvImporter.importCsv(repository, "".byteInputStream()).getOrThrow())
        assertTrue(CsvImporter.importCsv(repository, "Project,Description\nRust,Book\n".byteInputStream()).isFailure)
        assertTrue(repository.getAllEntriesWithTags().isEmpty())
    }

}
