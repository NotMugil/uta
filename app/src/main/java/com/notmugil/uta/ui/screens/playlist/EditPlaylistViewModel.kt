package com.notmugil.uta.ui.screens.playlist

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notmugil.uta.data.PlaylistCoverManager
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.repository.LibraryRepository
import com.notmugil.uta.domain.model.PlaylistItem
import com.notmugil.uta.domain.model.TrackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject

data class EditPlaylistTrackEntry(
    val editId: String = java.util.UUID.randomUUID().toString(),
    val track: TrackItem
)

data class EditPlaylistState(
    val playlist: PlaylistItem? = null,
    val initialTrackIds: List<String> = emptyList(),
    val name: String = "",
    val comment: String = "",
    val isPublic: Boolean = false,
    val pendingCroppedBitmap: Bitmap? = null,
    val isCoverRemoved: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class EditPlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val subsonicRepository: SubsonicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val playlistId: String = checkNotNull(savedStateHandle["playlistId"])

    private val _uiState = MutableStateFlow(EditPlaylistState())
    val uiState: StateFlow<EditPlaylistState> = _uiState.asStateFlow()

    val tracks = mutableStateListOf<EditPlaylistTrackEntry>()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // Fetch latest from server/Room and native Navidrome API
                val playlist = libraryRepository.getPlaylistFlow(playlistId).firstOrNull()
                val navidromeInfo = subsonicRepository.getNavidromePlaylistInfo(playlistId)
                val isSmart = navidromeInfo?.isSmart ?: (playlist?.isSmart ?: false)
                val isSync = navidromeInfo?.isSync ?: (playlist?.isSync ?: false)
                val isOwner = navidromeInfo?.isOwner ?: (playlist?.isOwner ?: true)
                val resolvedPlaylist = playlist?.copy(
                    isSmart = isSmart,
                    isSync = isSync,
                    isOwner = isOwner
                )

                val playlistTracksResult = libraryRepository.fetchPlaylistTracks(playlistId)
                val trackList = playlistTracksResult.getOrNull()
                    ?: libraryRepository.getTracksForPlaylistFlow(playlistId).firstOrNull()
                    ?: emptyList()

                tracks.clear()
                tracks.addAll(trackList.map { EditPlaylistTrackEntry(track = it) })

                _uiState.update {
                    it.copy(
                        playlist = resolvedPlaylist,
                        initialTrackIds = trackList.map { t -> t.id },
                        name = resolvedPlaylist?.name.orEmpty(),
                        comment = resolvedPlaylist?.comment.orEmpty(),
                        isPublic = resolvedPlaylist?.isPublic ?: false,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[EditPlaylist] Failed to load playlist $playlistId")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load playlist"
                    )
                }
            }
        }
    }

    fun setName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun setComment(comment: String) {
        _uiState.update { it.copy(comment = comment) }
    }

    fun setIsPublic(isPublic: Boolean) {
        _uiState.update { it.copy(isPublic = isPublic) }
    }

    fun setPendingCroppedBitmap(bitmap: Bitmap?) {
        _uiState.update { it.copy(pendingCroppedBitmap = bitmap, isCoverRemoved = false) }
    }

    fun removeCover() {
        _uiState.update { it.copy(pendingCroppedBitmap = null, isCoverRemoved = true) }
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        val canEdit = _uiState.value.playlist?.canEditTracks ?: true
        if (!canEdit) return
        if (fromIndex in tracks.indices && toIndex in tracks.indices && fromIndex != toIndex) {
            val item = tracks.removeAt(fromIndex)
            tracks.add(toIndex, item)
        }
    }

    fun removeTrack(index: Int) {
        val canEdit = _uiState.value.playlist?.canEditTracks ?: true
        if (!canEdit) return
        if (index in tracks.indices) {
            tracks.removeAt(index)
        }
    }

    fun savePlaylist(onSuccess: () -> Unit) {
        val state = _uiState.value
        val cleanName = state.name.trim()
        if (cleanName.isEmpty() || state.isSaving || state.isDeleting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                if (state.pendingCroppedBitmap != null) {
                    val bitmap = state.pendingCroppedBitmap
                    val imageBytes = withContext(Dispatchers.IO) {
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        stream.toByteArray()
                    }

                    withContext(Dispatchers.IO) {
                        PlaylistCoverManager.saveCustomCover(context, playlistId, bitmap)
                    }

                    try {
                        val uploaded = subsonicRepository.uploadPlaylistImage(playlistId, imageBytes)
                        Timber.d("[EditPlaylist] uploadPlaylistImage completed with result=$uploaded")
                    } catch (e: Exception) {
                        Timber.w(e, "[EditPlaylist] uploadPlaylistImage failed, saved locally")
                    }
                } else if (state.isCoverRemoved) {
                    withContext(Dispatchers.IO) {
                        PlaylistCoverManager.deleteCustomCover(context, playlistId)
                    }
                    try {
                        val deleted = subsonicRepository.deletePlaylistImage(playlistId)
                        Timber.d("[EditPlaylist] deletePlaylistImage completed with result=$deleted")
                    } catch (e: Exception) {
                        Timber.w(e, "[EditPlaylist] deletePlaylistImage failed, deleted locally")
                    }
                }

                val originalPl = state.playlist
                val cleanComment = state.comment.trim()
                val nameChanged = cleanName != originalPl?.name
                val commentChanged = cleanComment != originalPl?.comment.orEmpty()
                val publicChanged = state.isPublic != (originalPl?.isPublic ?: false)

                if (nameChanged || commentChanged || publicChanged) {
                    libraryRepository.updatePlaylistMeta(
                        playlistId = playlistId,
                        name = cleanName,
                        comment = cleanComment.takeIf { it.isNotEmpty() },
                        isPublic = state.isPublic
                    )
                }

                val canEditTracks = state.playlist?.canEditTracks ?: true
                val currentTrackIds = tracks.map { it.track.id }
                if (canEditTracks && currentTrackIds != state.initialTrackIds) {
                    libraryRepository.setPlaylistSongs(playlistId, currentTrackIds)
                }

                // Refresh tracks & playlist in Room
                try {
                    libraryRepository.fetchPlaylistTracks(playlistId)
                } catch (_: Exception) {}

                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            } catch (e: Exception) {
                Timber.e(e, "[EditPlaylist] Failed to save playlist $playlistId")
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = e.localizedMessage ?: "Failed to save playlist"
                    )
                }
            }
        }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        val state = _uiState.value
        if (state.isDeleting || state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
            try {
                withContext(Dispatchers.IO) {
                    PlaylistCoverManager.deleteCustomCover(context, playlistId)
                }
                libraryRepository.deletePlaylist(playlistId)
                _uiState.update { it.copy(isDeleting = false) }
                onDeleted()
            } catch (e: Exception) {
                Timber.e(e, "[EditPlaylist] Failed to delete playlist $playlistId")
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = e.localizedMessage ?: "Failed to delete playlist"
                    )
                }
            }
        }
    }
}
