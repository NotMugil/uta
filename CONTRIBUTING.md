# Contributing to Uta

Thank you for your interest in contributing to Uta! Whether you want to report a bug, suggest a feature, or submit code changes, your help is welcome.

## Getting Started

### Prerequisites

- **JDK 17** or higher
- **Android Studio** (latest stable release recommended)
- **Android SDK** (API 34+)

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/NotMugil/uta.git
   cd uta
   ```
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Run tests:
   ```bash
   ./gradlew test
   ```

## How to Contribute

### Reporting Issues

- Check existing [issues](https://github.com/NotMugil/uta/issues) before opening a new one to avoid duplicates.
- Clearly describe the bug or feature request, including steps to reproduce, device/OS details, server type (e.g. Navidrome, Subsonic), and screenshots or logs where applicable.

### Submitting Pull Requests

1. **Fork** the repository and create a new feature branch off `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. **Make your changes**, adhering to standard Kotlin and Jetpack Compose best practices.
3. **Verify your changes** on a device or emulator and ensure `./gradlew test` passes.
4. **Commit** with clear, descriptive commit messages.
5. **Open a Pull Request** against the `main` branch with an explanation of your changes and references to relevant issues.

### Translations

We welcome translations into other languages! Translations are managed via **[Crowdin](https://crowdin.com/project/uta-android)**:
1. Join the [Uta Crowdin Project](https://crowdin.com/project/uta-android) to translate strings directly in your browser.
2. If your language is not listed, [open an issue](https://github.com/NotMugil/uta/issues) to request it.
3. Completed translations on Crowdin will be automatically synchronized with the repository.

## Code Style

- Follow standard [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Keep composables modular, clean, and idiomatic.
