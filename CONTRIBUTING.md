 # Contributing to CalGPT
 
 Thanks for your interest in contributing.
 
 This project aims to stay small, readable, and maintainable. Contributions that improve reliability, UX, and security are especially welcome.
 
 ## Before you start
 
 - Check existing issues (or open one) to discuss larger changes.
 - For UX changes, include screenshots or a short screen recording.
 - For networking or parsing changes, include test cases when feasible.
 
 ## Development setup
 
 - Android Studio (recent stable)
 - JDK 17 (use Android Studio’s embedded JDK if unsure)
 
 Build:
 
 ```bash
 ./gradlew :app:assembleDebug
 ```
 
 ## Pull requests
 
 - Keep PRs focused (one feature/fix per PR).
 - Update UI text/resources when user-facing behavior changes.
 - Prefer small, composable functions and clear naming.
 - Avoid introducing new dependencies unless necessary.
 
 ### Security & secrets
 
 - Do not commit API keys, credentials, or personal calendar URLs.
 - Do not add logging that prints secrets (OpenAI key, CalDAV credentials, event contents).
 
 ## Code style
 
 - Kotlin formatting should be consistent with the existing codebase.
 - Keep Compose UI state predictable (single source of truth in ViewModels where appropriate).
 
 ## Reporting bugs
 
 When filing a bug, include:
 
 - Device / Android version
 - Steps to reproduce
 - Expected vs actual behavior
 - Relevant logs (redact secrets)
 
 ## License
 
 By contributing, you agree that your contributions will be licensed under the project’s license (see `LICENSE`).
