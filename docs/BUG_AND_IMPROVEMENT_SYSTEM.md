# CinefinTV Bug & Improvement System

**Created:** 2026-05-18  
**Purpose:** Give CinefinTV one consistent place to capture bugs, improvements, QA evidence, priorities, and AI-assisted implementation work.

This system is designed around the way the app actually fails on Android TV: playback reliability, D-pad focus, startup/session restore, Jellyfin server decisions, and real-device performance. It also fits the existing repo automation that already supports `@gemini-cli` issue comments.

---

## 1. Issue intake

Use GitHub Issues as the source of truth. The repo now has issue forms for:

| Template | Use for | Auto-labels |
|---|---|---|
| Bug report | General reproducible app bugs | `type: bug`, `needs: triage` |
| Playback bug | Video/audio/subtitle/direct-play/transcode/resume issues | `type: bug`, `area: playback`, `needs: triage` |
| TV focus/navigation bug | D-pad, first focus, back behavior, tab behavior, focus traps | `type: bug`, `area: tv-focus`, `needs: triage` |
| Performance regression | Cold startup, repeated requests, jank, slow loads, memory/CPU | `type: bug`, `area: performance`, `needs: triage` |
| Improvement / feature request | Product polish, UX ideas, features | `type: improvement`, `needs: triage` |
| Documentation / planning task | Stale docs, release checklists, project status updates | `type: docs`, `needs: triage` |

### Intake rule

Every issue should contain:

1. **What happened**
2. **Expected behavior**
3. **Actual behavior**
4. **Reproduction path**
5. **Device/app/server context**
6. **Evidence** when available: logcat, Jellyfin server log, screenshot, screen recording, or Crashlytics link
7. **Acceptance criteria** for what counts as fixed

Do not start implementation until the issue has enough detail to reproduce or verify the fix.

---

## 2. Label taxonomy

Run this once after pushing the branch:

```bash
./scripts/sync-github-labels.sh rpeters1430/CinefinTV
```

### Required labels during triage

Every actionable issue should have one label from each group:

- **Type:** `type: bug`, `type: improvement`, `type: docs`, `type: refactor`, `type: test`
- **Severity:** `severity: critical`, `severity: high`, `severity: medium`, `severity: low`
- **Area:** `area: startup`, `area: auth`, `area: home`, `area: library`, `area: detail`, `area: playback`, `area: tv-focus`, `area: performance`, `area: architecture`, `area: release`
- **State:** `needs: triage`, `needs: repro`, `needs: logs`, `ready: implementation`, `ready: review`, or `blocked`
- **Size:** `size: small`, `size: medium`, `size: large` for implementation work

### Severity definitions

| Severity | Meaning | Examples |
|---|---|---|
| Critical | App unusable, crash loop, data loss, security issue | Startup crash, credentials lost, playback always crashes |
| High | Major flow broken or highly visible TV regression | Cannot navigate to Play, subtitles never render, severe startup jank |
| Medium | Important issue with workaround | Some libraries slow, focus lands wrong but recoverable |
| Low | Cosmetic, docs, cleanup, minor annoyance | Text alignment, stale doc, better logging |

---

## 3. Triage workflow

### New issue triage checklist

1. Confirm it is not a duplicate.
2. Apply missing `type`, `area`, `severity`, `size`, and `source` labels.
3. Decide whether it needs more evidence:
   - Add `needs: repro` if steps are unclear.
   - Add `needs: logs` if a logcat/server log is needed.
   - Add `blocked` if external info or upstream fixes are required.
4. Add acceptance criteria.
5. Move from `needs: triage` to `ready: implementation` only when the fix can be verified.

### Recommended priority order

1. `severity: critical`
2. `area: startup` and `area: playback` high-severity bugs
3. `area: tv-focus` high-severity bugs
4. Performance regressions with measurable evidence
5. User-visible polish
6. Refactors and docs cleanup

---

## 4. Project-board columns

Use these columns if you create a GitHub Project board:

1. **Inbox** — new issues with `needs: triage`
2. **Needs Repro / Logs** — not actionable yet
3. **Ready** — `ready: implementation`
4. **In Progress** — assigned work
5. **Needs QA** — PR merged or build available, needs TV testing
6. **Done** — verified or intentionally closed
7. **Blocked / Waiting** — upstream, device-only, missing info

Useful board fields:

- Area
- Severity
- Size
- Device
- Playback mode
- App version
- Jellyfin version
- Source: manual QA / logcat / Crashlytics / code review

---

## 5. AI-assisted workflow with the existing Gemini automation

The repo already has dispatch support for `@gemini-cli` commands. Use the issue system to make those commands safer and more useful.

### Planning only

Use this on an issue after triage:

```text
@gemini-cli Please inspect the relevant files and propose a fix plan. Do not edit code yet. Include files likely to change, risks, and test commands.
```

The existing workflow expects the agent to post a plan and wait for approval.

### Approve implementation

After reviewing the posted plan:

```text
@gemini-cli /approve
```

### Review a PR

```text
@gemini-cli /review Focus on Android TV D-pad behavior, playback regressions, recomposition risk, and whether tests cover the linked issue.
```

### Triage an issue

```text
@gemini-cli /triage
```

---

## 6. Manual QA checklist for Android TV

Use this for every release candidate and for any issue touching navigation or playback.

### Startup / session restore

- [ ] Fresh install shows server connection flow.
- [ ] Existing session restores without showing login briefly.
- [ ] Bad server URL fails gracefully.
- [ ] Expired credentials return to login without crash loop.
- [ ] Cold startup does not repeat the same validation/network work excessively.

### TV focus / navigation

- [ ] Home first focus is predictable.
- [ ] Left navigation rail / tabs do not steal focus unexpectedly.
- [ ] D-pad up from content returns to the expected top anchor.
- [ ] Detail screens land on Play/Resume or the intended primary action.
- [ ] Back from player returns to the same detail item and sane focus.
- [ ] Empty rows, missing images, and long titles do not create focus traps.

### Playback

- [ ] Movie direct play.
- [ ] Movie transcode.
- [ ] TV episode resume.
- [ ] Next episode flow.
- [ ] Home video playback.
- [ ] Subtitle switch during playback.
- [ ] Audio track switch during playback.
- [ ] Seek forward/back.
- [ ] Exit and resume from detail screen.

### Performance

- [ ] Home loads without visible repeated reloads.
- [ ] Library grid scroll is smooth on a real TV device.
- [ ] Detail screen image loading does not block focus.
- [ ] Player controls do not cause visible stutter.
- [ ] Logcat does not show repeated identical validation or network spam.

---

## 7. Definition of done

A bug or improvement is done only when:

- [ ] Acceptance criteria are met.
- [ ] A relevant automated test exists, or the PR explains why manual QA is enough.
- [ ] Real TV or emulator QA steps are documented in the PR.
- [ ] No private server URLs, tokens, or personal log data are committed.
- [ ] Docs/checklists are updated when behavior changes.
- [ ] The issue is closed by the PR or manually linked.

---

## 8. Where current work lives

- Current generated backlog: `docs/triage/CURRENT_BUG_BACKLOG_2026-05-18.md`
- Release checklist: `docs/RELEASE_CHECKLIST.md`
- Historical review: `docs/2026-03-16-review-bugs-and-improvements.md`
- Latest project status: `docs/2026-05-06-project-status-report.md`
- Label sync script: `scripts/sync-github-labels.sh`
- Issue templates: `.github/ISSUE_TEMPLATE/`
- PR template: `.github/PULL_REQUEST_TEMPLATE.md`
