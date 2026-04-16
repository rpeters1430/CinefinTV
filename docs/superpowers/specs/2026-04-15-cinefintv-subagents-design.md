# Design Doc: CinefinTV Specialized Subagents

This document outlines the design for 5 specialized subagents for the CinefinTV project. These agents are designed to operate as autonomous experts within their respective domains, following the project's established architectural patterns and engineering standards.

## Goals
- Reduce context bloat in the main agent by delegating domain-specific tasks.
- Ensure strict adherence to Android TV (10-foot UI) best practices.
- Maintain high code quality through specialized reviews and automated testing.
- Accelerate development by providing agents with deep knowledge of specific libraries (Media3, Hilt, Retrofit).

## Subagent Architecture

All subagents will be defined in the `.gemini/agents/` directory of the project. They will have full tool access (`*`) to allow for autonomous implementation and verification, but will be governed by specific instructions.

### 1. `tv-ui-specialist`
- **Domain**: D-pad navigation, focus management, TV Material 3, and 10-foot UX.
- **Tools**: `*`
- **Key Responsibilities**:
    - Ensure logical focus traversal (Up/Down/Left/Right).
    - Implement visual focus indicators for all interactive elements.
    - Adhere to legibility standards (18sp+ text).
    - Use TV-specific components (e.g., `TvMediaCard`).

### 2. `jetpack-compose-expert`
- **Domain**: UI State management, performance optimization, and Compose best practices.
- **Tools**: `*`
- **Key Responsibilities**:
    - Manage state hoisting and Unidirectional Data Flow (UDF).
    - Optimize recomposition by using stable types and `remember`.
    - Enforce clean `@Composable` functions (logic-free).
    - Implement complex animations and transitions.

### 3. `media-playback-expert`
- **Domain**: AndroidX Media3, ExoPlayer, and Jellyfin streaming integration.
- **Tools**: `*`
- **Key Responsibilities**:
    - Manage the Media3 `Player` lifecycle and `MediaSession`.
    - Implement playback controls, subtitle selection, and audio track switching.
    - Handle network buffering, error mapping, and transcoding logic.
    - Ensure background playback stability.

### 4. `jellyfin-data-architect`
- **Domain**: Data layer, Dependency Injection (Hilt), and Networking (Retrofit/OkHttp).
- **Tools**: `*`
- **Key Responsibilities**:
    - Design and implement Retrofit API interfaces.
    - Enforce the `ApiResult<T>` pattern for all repository calls.
    - Manage local persistence with DataStore and encrypted Keystore.
    - Maintain Hilt modules and dependency graph integrity.

### 5. `android-tv-tester`
- **Domain**: Unit testing, UI testing (androidTest), and focus regression testing.
- **Tools**: `*`
- **Key Responsibilities**:
    - Write JUnit 4 tests using MockK and Turbine.
    - Implement `AppNavigationSmokeUiTest.kt` style navigation tests.
    - Verify ViewModel logic and UI state transitions.
    - Create robust mocks for hardware/system capabilities (e.g., `DeviceCapabilities`).

## Implementation Plan

1. Create the `.gemini/agents/` directory.
2. Generate 5 `.md` files, one for each subagent, with YAML frontmatter and detailed system instructions.
3. Validate each subagent by performing a small, domain-specific research task (e.g., "Review focus logic in X").

## Success Criteria
- Subagents can be invoked via `@name` syntax.
- Subagents correctly identify and apply project-specific patterns (`ApiResult`, TV Material 3).
- Delegation reduces the overall token usage of the main session.
