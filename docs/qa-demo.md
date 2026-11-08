# Local Android and browser acceptance

Use this checklist for the Android and browser demonstration. iOS is
deferred.

## Start the current build

Use PowerShell in the repository folder. Run each command on its own line.
Stop the previous development server with Ctrl+C before starting another
one on the same port and database.

```powershell
.\gradlew.bat --max-workers=1 :server:runDev
```

Leave that terminal running. In a second terminal, with the phone connected:

```powershell
.\gradlew.bat --max-workers=1 :androidApp:installDebug
$adb = ".\build\android-sdk\platform-tools\adb.exe"
& $adb reverse tcp:8080 tcp:8080
& $adb shell am force-stop net.mehstudios.etimetable
& $adb shell monkey -p net.mehstudios.etimetable 1
```

Open `http://localhost:8080/admin` for the dashboard. The development-only
dashboard login is documented in CONTRIBUTING.md. The student app uses a
separate account created in the app. Sample groups, names and rooms are
test data; this pass does not replace them with official faculty data.

## Schedule management

- Open Gestionare orar in the sidebar. Search by subject, teacher, room
  and group. A group's search should also find its series courses.
- Filter to a weekday, then to classes missing a teacher or room. Clear
  the filters to return to the full semester.
- Select a class. Choose another room and preview. Verify the proposed
  room, teacher and time before confirming. Cancel once and check that
  nothing changed. Repeat and confirm, then sync the phone and check the
  class details.
- Select two classes and shift by 30 minutes. Check the preview, confirm
  and check that unselected classes stayed unchanged. Restore the times
  afterwards using -30 minutes.
- Preview a change in one browser tab, save another timetable edit in a
  second tab, then confirm the first preview. Expect a conflict requiring
  a reload, rather than overwriting the second tab's edit.
- Try moving an early class before midnight with a negative shift. Expect
  a validation error. If a preview causes a room or teacher double booking,
  expect the warning before confirmation.

## Find Teacher

- Open Teachers (Profesori in Romanian). Search by name, email and subject,
  including a name with Romanian diacritics typed without accents.
- Open a result. Check office and email when supplied, missing-contact
  labels when not, office hours and the current or coming week's schedule.
- Confirm that a current or later activity identifies its room. The label
  explains that this is timetable information, not a live location.
- Enable the active-now filter. It is normal to see no results outside
  teaching and office hours. Clear it to see everyone.
- Turn off the phone's network after syncing. The directory and teacher
  details should still work, with the offline notice. Restore connectivity.

## Announcements

- In the dashboard, preview and publish a post with bold text, a list and
  an https link. Sync the phone. Confirm the post appears, its formatting
  renders and opening it marks it read.
- Search by its title and author. Try a search with no match. Use the
  unread filter and Mark all as read. Clearing filters restores the list.
- Edit a post that was read and sync again. Its changed title or body
  should become unread. Pinning alone should preserve its read state.
- Pin a post and check that it appears above unpinned posts on the phone.
  Old pinned posts should remain visible even after more than 100 newer
  posts. Delete a post, sync and verify it disappears.
- After more than 20 published posts, use the dashboard's older/newest
  links and edit an older post. Previewing must not publish another copy.
- Repeat the Android checks in Romanian and English. The English bottom
  navigation uses News as a short label; the page title is Announcements.

## Earlier feature regressions

- Report a class from Android, verify the student's identity in the
  dashboard, reject a duplicate and resolve or dismiss a report.
- Disable connectivity before sending. Expect unconfirmed delivery and
  a retained form, not an offline queue or a false success.
- Check the password visibility control on sign-in and both sign-up
  password fields. Each should toggle independently.
- Check timetable navigation, profile enrollment and cached timetable
  access after restarting the app.

## Follow-ups outside this pass

Useful later work: an audit trail for timetable edits, a recoverable
snapshot before a CSV import, and a clearer indication when a server sync
fails while the phone still has internet. Dashboard localization and an
offline report outbox remain separate product decisions. APNs and the iOS
student screens remain deferred.
