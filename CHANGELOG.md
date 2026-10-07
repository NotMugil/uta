# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0-alpha.3] - 2026-10-07

### Added

- Option to disable ambient page top-left gradient.
- Smooth song switching transitions and cubic easing in Default player layout.
- Fill gaps in lyrics with music icon.
- Added multiple styles for fullscreen lyrics page.
- Artist action modal sheet with actions like rate, favourites, add to playlist.
- Snap fling behavior for horizontal carousel rows across Home screen sections.
- Dynamic theme toggle in Display Settings with conditional Dynamic Color Source selection.

### Changed

- Reworked album art dynamic color extractor.
- Changed next and previous track icons for player pages to match miniplayer.
- Redesign fullscreen lyrics pages with ambient background.
- New & redesigned cinematic player page style

### Fixed

- Fix animated album artwork resolution and playback on the album details page.
- Fix duplicate back navigation button on the artist page during initial loading.

## [1.0.0-alpha.2] - 2026-10-04

### Added

- Real-time audio stream stats displayed under the seekbar.
- Prefetching upcoming tracks in the playback queue and user preference to customize the no.of tracks prefetched.
- Reorderable home screen sections with the ability to turn them off.
- Bottom navigation bar tab buttons customization.
- Miniplayer action buttons customization.
- Estimated download size calculation and confirmation dialogs for albums, playlists, and individual tracks.
- GitHub Actions workflow for continuous integration and automated release builds.

### Fixed

- Changing transcoding format or streaming bitrate during playback reloads the active track and queue using the new format at the current position.
- Fix Opus decoder crashes and configure audio renderers for Opus playback.
- Fix artist and album item mismatch on home screen section rows.
- Fix miniplayer contrast and background colors in light mode.
- Improve Subsonic error message formatting.

## [1.0.0-alpha.1] - 2026-09-30

### Added

- Initial alpha release of Uta music player for Subsonic-compatible servers.
- Core playback engine with ExoPlayer / Media3 background playback service.
- Full player sheets with 6 layout styles: Default, Modern, Cinematic, Lyrics, Cover, and Landscape.
- Offline playback and track download manager.
- Library browsing for artists, albums, playlists, genres, and favorites.
- Search with instant filtering across library tracks, albums, and artists.
- Sleep timer and scrobbling support.

[unreleased]: https://github.com/NotMugil/uta/compare/v1.0.0-alpha.3...HEAD
[1.0.0-alpha.3]: https://github.com/NotMugil/uta/compare/v1.0.0-alpha.2...v1.0.0-alpha.3
[1.0.0-alpha.2]: https://github.com/NotMugil/uta/compare/v1.0.0-alpha1...v1.0.0-alpha.2
[1.0.0-alpha.1]: https://github.com/NotMugil/uta/releases/tag/v1.0.0-alpha1
