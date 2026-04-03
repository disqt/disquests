# UI Improvements Batch

**Branch:** `feat/ui-improvements`

## Wave 1 (parallel -- no shared files)

| Plan | File |
|------|------|
| Autocomplete dropdown fixes | `2026-04-02-autocomplete-dropdown-fixes.md` |
| Tag picker stale tags | `2026-04-02-tag-picker-stale-tags.md` |
| Search tag improvements | `2026-04-02-search-tag-improvements.md` |
| Remove "no tags" label | `2026-04-02-remove-no-tags-label.md` |

## Wave 2 (parallel -- no shared files)

| Plan | File |
|------|------|
| Mouse back/forward navigation | `2026-04-02-mouse-back-forward-navigation.md` |
| Merge Join/Request buttons | `2026-04-02-merge-join-request-buttons.md` |

## Wave 3 (sequential -- both touch QuestScreen)

| Plan | File | Order |
|------|------|-------|
| Back arrow replaces Close | `2026-04-02-back-arrow-replace-close.md` | First |
| Quest detail button visibility | `2026-04-02-quest-detail-button-visibility.md` | Second |

## Test Port Assignment

Each parallel agent gets unique ports (offset +100):

| Agent | Server Port | RCON Port |
|-------|------------|-----------|
| 1 | 25565 | 25575 |
| 2 | 25665 | 25675 |
| 3 | 25765 | 25775 |
| 4 | 25865 | 25875 |
