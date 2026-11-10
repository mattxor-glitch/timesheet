# Timesheet

A simple Android stopwatch timesheet. Projects appear as tiles; tap one to start its timer.
Starting another project stops the current one, and tapping the running tile stops it.

## Features

- **Project tiles**: number and name, with today's running total on each tile. Long-press a tile to add time, change the start time, or edit/remove it (history is kept).
- **Labour hours**: give a project its allotted hours (optional). Each tile shows the hours left on the job,
  counting all time ever logged on it. The tile turns **orange** at 10 hours or less and **red** at 0 or below,
  and keeps counting into negative. Clearing old history doesn't give hours back; deleting a mistaken entry does.
- **Daily reset**: every timer starts at zero each day. A timer left running at midnight is stopped,
  or carried into the new day if you turn on *Keep timer running past midnight*.
- **Time-stamped log**: every start and stop is recorded with its clock time. Tap *Today's log* on the
  Timers screen, or tap a day in History, to see each entry.
- **Add time**: long-press a tile → *Add time for today* to add a total (hours and minutes) without a start
  or stop time. It's logged as a **manual** entry stamped with when you added it, and can be deleted.
- **Change start time**: while a timer is running, tap *Change start* (or long-press its tile). The entry is
  marked **manual** and shows the original start and when it was changed. It can't go before midnight or
  before the previous timer's stop time.
- **Edit today's time**: tap *Edit* on the Today card to see every project's total for today, or long-press a
  tile → *Edit today's time*. Enter the total it should be; the difference is logged as a time-stamped
  **manual** adjustment (e.g. "Set total to 2:30 · was 2:45 · −0:15"). Delete the adjustment to undo it.
- **Edit and delete entries**: in a day's log, each entry can be deleted (any day) or edited (today):
  start/stop times, project, or a manual entry's amount. Edits can't overlap other entries or go into the
  future; they're marked **manual** with the edit time and the original values.
- **History**: previous days, with per-project and daily totals in `h:mm` and decimal hours.
- **History retention**: 1 week up to 5 years, or *Never delete* (the default).
- **Size warning**: a banner appears when the database is over 50 MB.
- **Clear history before a date**: Settings → *Clear history before a date…* (shown as mm/dd/yyyy).
  The database is compacted afterwards so the file actually shrinks.

## Web version

**Open it here: https://mattxor-glitch.github.io/timesheet/**

`docs/index.html` is the same app as a single HTML file with no dependencies, published with GitHub Pages
(Settings → Pages → Deploy from a branch → `main` / `/docs`). It also works saved to disk and opened directly. Data is saved in that browser (IndexedDB) and kept
between visits; use *Settings → Save backup* now and then, since clearing browser data deletes it.
The web and Android versions keep separate data.

## No launch warnings

- Targets the current Android API level (36), so there's no "built for an older version of Android" dialog.
- Requests no permissions, so there are no permission prompts.
- Contains no native libraries, so there's no 16 KB page-size compatibility dialog on Android 15+.
- Release builds are minified and signed with a fixed key, so updates install over the old version.
- Handles edge-to-edge drawing itself, as Android 15+ expects.

## Installing

Every push to `main` builds a signed APK with GitHub Actions and publishes it under
[Releases](../../releases/latest). On your phone, open the latest release, tap the `.apk`
under *Assets*, and allow your browser to install apps if Android asks.

## Signing

The release key is in `signing/`. Keep it. Updates only install over an existing copy
when they are signed with the same key.

## Building locally

Open the project in Android Studio, or run `./gradlew assembleRelease` with JDK 17 and the Android SDK.
