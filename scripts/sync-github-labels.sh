#!/usr/bin/env bash
set -euo pipefail

# Sync the label set used by the CinefinTV bug/improvement system.
# Usage:
#   gh auth login
#   ./scripts/sync-github-labels.sh rpeters1430/CinefinTV

REPO="${1:-}"
if [[ -z "$REPO" ]]; then
  echo "Usage: $0 owner/repo" >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required." >&2
  exit 1
fi

upsert_label() {
  local name="$1"
  local color="$2"
  local description="$3"

  if gh label view "$name" --repo "$REPO" >/dev/null 2>&1; then
    gh label edit "$name" --repo "$REPO" --color "$color" --description "$description"
  else
    gh label create "$name" --repo "$REPO" --color "$color" --description "$description"
  fi
}

# Workflow state
upsert_label "needs: triage" "FBCA04" "Needs first review, severity, area, and owner."
upsert_label "needs: repro" "D4C5F9" "Needs reliable reproduction steps or logs."
upsert_label "needs: logs" "D4C5F9" "Needs logcat, Jellyfin logs, screenshot, or video evidence."
upsert_label "ready: implementation" "0E8A16" "Issue has acceptance criteria and can be worked."
upsert_label "ready: review" "0E8A16" "Implementation exists and needs review/QA."
upsert_label "blocked" "B60205" "Blocked by dependency, missing info, or upstream issue."

# Types
upsert_label "type: bug" "D73A4A" "User-visible broken behavior."
upsert_label "type: improvement" "A2EEEF" "User-visible enhancement or polish."
upsert_label "type: docs" "0075CA" "Documentation, roadmap, checklist, or release notes."
upsert_label "type: refactor" "C5DEF5" "Code health change with minimal intended user-visible effect."
upsert_label "type: test" "C2E0C6" "Automated or manual test coverage work."

# Severity
upsert_label "severity: critical" "B60205" "Crash, data loss, security risk, or app unusable."
upsert_label "severity: high" "D93F0B" "Major feature broken or very visible regression."
upsert_label "severity: medium" "FBCA04" "Important bug with workaround or moderate impact."
upsert_label "severity: low" "C2E0C6" "Minor polish, annoyance, or cleanup."

# Areas
upsert_label "area: startup" "5319E7" "Cold start, session restore, server validation."
upsert_label "area: auth" "5319E7" "Login, Quick Connect, server discovery, credentials."
upsert_label "area: home" "5319E7" "Home screen, carousel, sections, discovery."
upsert_label "area: library" "5319E7" "Libraries, paging, sort/filter, collections."
upsert_label "area: detail" "5319E7" "Movie/show/season/person/detail pages."
upsert_label "area: playback" "5319E7" "Video/audio playback, subtitles, tracks, seek/resume."
upsert_label "area: tv-focus" "5319E7" "D-pad focus, first focus, back behavior, navigation traps."
upsert_label "area: performance" "5319E7" "Jank, repeated requests, memory/CPU, startup time."
upsert_label "area: architecture" "5319E7" "Repositories, DI, module boundaries, technical debt."
upsert_label "area: release" "5319E7" "Build, signing, update, Play/release workflow."

# Size / planning
upsert_label "size: small" "EDEDED" "Likely under half a day."
upsert_label "size: medium" "EDEDED" "Likely one to two days."
upsert_label "size: large" "EDEDED" "Multi-day or multi-file change."
upsert_label "epic" "3E4B9E" "Parent issue for several linked tasks."

# Sources
upsert_label "source: logcat" "BFD4F2" "Created from logcat evidence."
upsert_label "source: manual-qa" "BFD4F2" "Created from manual TV testing."
upsert_label "source: code-review" "BFD4F2" "Created from source audit."
upsert_label "source: crashlytics" "BFD4F2" "Created from Firebase Crashlytics evidence."
