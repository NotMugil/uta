package com.notmugil.uta.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import com.notmugil.uta.data.repository.LibraryRepository
import com.notmugil.uta.data.repository.SearchResults
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.PlaybackController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import com.notmugil.uta.data.NetworkMonitor
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.LocalMediaDao
import com.notmugil.uta.data.download.OfflineDownloadManager
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val libraryRepository: LibraryRepository = mockk(relaxed = true)
    private val subsonicRepository: SubsonicRepository = mockk(relaxed = true)
    private val localMediaDao: LocalMediaDao = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true)
    private val offlineDownloadManager: OfflineDownloadManager = mockk(relaxed = true)
    private val playbackController: PlaybackController = mockk(relaxed = true)
    private val searchHistoryFlow = MutableStateFlow<List<String>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { subsonicRepository.currentServerId } returns "srv_1"
        every { localMediaDao.getDownloadedTrackIdsFlow("srv_1") } returns MutableStateFlow(emptyList())
        every { networkMonitor.isOfflineModeActive } returns MutableStateFlow(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): SearchViewModel {
        return SearchViewModel(
            savedStateHandle = savedStateHandle,
            libraryRepository = libraryRepository,
            subsonicRepository = subsonicRepository,
            localMediaDao = localMediaDao,
            networkMonitor = networkMonitor,
            offlineDownloadManager = offlineDownloadManager,
            playbackController = playbackController
        )
    }

    @Test
    fun `debounced search query invokes repository and updates results`() = runTest(testDispatcher) {
        val fakeResults = SearchResults(
            tracks = listOf(TrackItem(id = "t1", title = "Test Song", artist = "Artist Name", durationSeconds = 120))
        )
        coEvery { libraryRepository.search("test") } returns fakeResults

        val viewModel = createViewModel()

        viewModel.onQueryChange("test")
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals("test", viewModel.state.value.query)
        assertEquals(1, viewModel.state.value.results.tracks.size)
        assertEquals("Test Song", viewModel.state.value.results.tracks.first().title)
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `empty query clears results immediately`() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("search_query" to "previous"))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.onQueryChange("")
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.query)
        assertEquals(0, viewModel.state.value.results.tracks.size)
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `playTrack delegates to playbackController`() = runTest(testDispatcher) {
        val track = TrackItem(id = "t1", title = "Song", artist = "Artist", durationSeconds = 100)
        val viewModel = createViewModel()

        viewModel.playTrack(track)

        coVerify { playbackController.playTrack(track, listOf(track)) }
    }
}
