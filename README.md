# Termux apps on Google Play

This repository contains the source code used in the [current Termux build](https://play.google.com/store/apps/details?id=com.termux&hl=en) on Google Play.

This is early work, if you can (and you're not a developer wanting to contribute this code) please [install Termux from F-Droid instead](https://f-droid.org/en/packages/com.termux/).

The plan is to get back to a single repository for Termux apps, this is a transitional repo while the main Termux app repository is not ready for the Google Play requirements.

# Overview of changes
- Changes to apps for them to be releasable on Google Play.
  - Don't `execve(2)` downloaded files directly, but instead execute `/system/bink/linker64 file-to-execute`.
  - Multiple permissions that was not accepted was removed, and functionality removed or adopted for that.
- Some unrelated changes for simplicity.
- Various unrelated minor changes.
- Some packages that did not build has been removed.
- The `termux-app`, `termux-styling`, `termux-boot`, `termux-widget` apps builds, wihle `termux-api`, `termux-float` and `termux-tasker` are not done yet.

More information will follow.
