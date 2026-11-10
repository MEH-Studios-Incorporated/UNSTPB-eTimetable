# Screens

What the apps show, screen by screen, derived from [brief.md](brief.md).
FAIMA's web prototype was only a loose picture of the idea; where this list
differs from it, this list is right.

## Navigation

A bottom bar with four tabs: Timetable, Subjects, Professors,
Announcements. The profile opens from the top bar on every tab. Tapping a
subject, room or professor anywhere opens its page.

The apps run in portrait only. A timetable reads top to bottom and every
screen is laid out for a phone held upright; a sideways version of each
would only be more to build and test. Android 16 and newer ignore the lock on screens at least
600 dp wide (tablets, unfolded foldables), where the screens stretch to
the width as they do on a wide phone.

## First start

1. Sign in with email and password, or create an account with first name,
   last name, email, phone number and the password typed twice. A "remember
   me" box keeps the session; without it the session ends when the app is
   closed. Each password field has its own show or hide control, so the
   confirmation can be checked without exposing both fields. The FAIMA seal
   sits above the form; the footer is a small Multimedia Entertainment Hub
   credit (mark and copyright, no links; those are on the About page). A RO /
   EN switch in the top corner changes the language before signing in.
2. A forgotten password is reset by email when the server has email
   configured, otherwise by an admin from the dashboard.
3. Enrollment: national or Erasmus.
4. National: programme, year, group, half-group if the group has halves.
   The programmes at FAIMA today are IMA, BEM, IEDEEE, IEICM and IBM.
5. Erasmus: search subjects across all programmes and years and add them,
   then pick a course and a lab slot for each one. A slot that overlaps one
   already picked is refused, naming the clash.
6. Notifications: ask for permission, explaining that announcements arrive
   this way.

Every choice can be changed later from the profile. Nothing is checked.

## Timetable

- Three layouts, chosen in the profile (Orar, Aspect); each has a day and
  a week view, and the profile also sets which of the two opens first:
  - Timeline (default): the day on an hour scale with a line at the
    current time; the week as columns of hours, Monday to Friday, Saturday
    only when it has something.
  - List: a card per class with the breaks between them written out
    ("Pauză · 4 h"); the week as an agenda, day by day, free days marked.
  - Table: one dense line per class (time, type letter, name, room); the
    week as a table of starting times by days, like the faculty's sheet.
- Above the day view, the next activity with its room and how long until
  it starts.
- The header shows the date range, the teaching week number and whether it
  is odd or even. Breaks are labelled as such instead of an empty week.
- The top bar always shows the current teaching week as a small ticket:
  the number in a solid block, "Săptămâna impară" beside it; "Vacanță"
  during a break. On Saturday and Sunday it shows the coming week.
- In the week view the arrows to the previous and next week sit next to
  the dates.
- A class card shows as much as its height allows: two hours or more get
  type, name on two lines, time, room and professor; an hour gets the name
  and one line with time, type and room.
- Swipe between days or weeks; a "today" control jumps back.
- Tapping an activity opens a sheet: subject, type, time, room and building
  (with a map link), who runs it, which weeks it runs, which groups attend.
  A course names its professor; a seminar, lab or project names its
  assistant and, below, the professor of the subject's course for the same
  series. When the timetable names nobody the sheet says so. Every name
  opens that professor's page.
- The class sheet has "Report a timetable problem". The form identifies
  the selected class and asks what is wrong and what should change, with a
  10 to 2,000 character description. It confirms only after the server
  accepts the report. A failed request keeps the text for retry; reports
  are not queued offline. Duplicate and hourly-limit messages explain why
  the report could not be sent. The timetable remains usable offline.

## Subjects

- A searchable list of all subjects, filtered by default to the student's
  programme and year, with a switch to see all.
- The subject page lists every activity grouped by type (course, seminar,
  lab, project): day, time, weeks, room, professor, groups.
- "Fits my timetable" filters the list to slots that do not overlap
  anything the student already has. This is how a national student finds a
  lab to recover or another group's lab to attend.
- A national student can add a slot to their own timetable, once (a single
  date) or for the rest of the semester. An Erasmus student adds and
  removes their picks here. Overlaps are refused in both cases.
- Once another weekly slot of the same kind is in the timetable, the
  group's own slot can be removed too ("Scoate"); it then reads "Grupa ta,
  scoasă" with "Adaugă înapoi". One weekly slot of each kind always stays:
  the last one has no "Scoate" and says "Ultima rămasă". A single visit can
  always be removed.

## Professors

Android has a Professors tab. Search matches a professor's name without
diacritics, email or subject name. A switch shows only people scheduled
to teach or hold office hours now. Each result opens the professor's page.
The current or next activity includes the room and available building
address. The page also shows the office, email and missing-contact labels.
Locations are inferred from the timetable, not tracked from the professor.

- A searchable list with each professor's current status in one line:
  teaching (subject, room, until when), office hours (room, until when),
  next activity today, or nothing more today.
- The professor page: status, the week's schedule (the coming week on a
  weekend), office hours, office, email.

## Announcements

Android can search titles, text and authors, show only unread items and
mark all downloaded announcements as read. A title or body edit marks the
item unread again; changing its pin alone does not.

- Previews, newest first, pinned ones on top, unread ones marked: title, two
  lines of text, date and author.
- Tapping one opens it in full, with its Markdown (paragraphs, lists, bold,
  italic, links) formatted, and marks it read.
- A push notification opens the announcement directly. Reading an
  announcement in the app removes its notification; signing out removes
  all of them.
- While the app is open, a new announcement shows as a slim bar above the
  tab bar instead of a notification: "Anunț nou" and the title. Tapping it
  opens the announcement; it hides itself after 6 seconds. When another
  arrives while it shows, it is replaced by "Anunțuri noi, 2 anunțuri de
  citit", which opens the announcements tab. The bar appears and goes
  without animation. If the announcement is not on the phone yet, the
  announcements tab shows until the sync brings it.

## Profile and settings

The profile is a menu, like the phone's own settings: a header with the
student's initials, name, email and phone, then grouped rows that show
their current value and open the next step:

- Grupa și orarul: change group or subjects (shows the group, or the number
  of Erasmus picks), update now (with the time of the last update, or that
  a change waits to be sent).
- Aplicația: Setări and Despre aplicație, each its own page.
- Sign out, in red, on its own.

Setări has two groups. General: language (same as the phone, Română or
English; on Android 13 and newer this is the same setting as the phone's
per-app language) and theme (same as the phone, light, dark). Orar: the
layout and the view it opens on. Each row opens a sheet with the options
and a short line about each. Despre aplicație has the FAIMA seal, the
version, the privacy note, the FAIMA site and the Multimedia Entertainment
Hub credit.

Pages opened over a tab (profile, a professor, a subject, an announcement,
changing the group) have the same top bar: a round back button and the
page's name, which stay in place while the page scrolls.

Changing the language fades the screen out and back in, since the app is
drawn again in the new language.

## About and legal

Opened from the profile. It shows the FAIMA seal, the app version and the
last sync, then:

- Privacy policy: what the server keeps (email, password hash, name, phone
  number, enrollment, group or picked slots, language, the phone's push
  token while signed in) and what it does not (location, contacts,
  analytics or tracking), how to delete the account. New announcements
  reach the phone through Google's Firebase Cloud Messaging, which carries
  the title and the first lines of the text. Timetable reports and their
  class details are visible to the staff managing the timetable and are
  deleted with the account.
- Terms of use: the timetable is for information; the faculty's official
  announcements prevail.
- Open source licenses: generated at build time from the dependencies,
  plus Libre Franklin (SIL Open Font License).
- Report a problem: a form that sends a description and an optional
  screenshot.
- FAIMA's website.

Then the MEH mark next to "Multimedia Entertainment Hub LLC" and "© 2026.
All rights reserved.", links to https://mehstudios.net/ and to
support@mehstudios.net, and a line saying the app was commissioned by
FAIMA, UNSTPB, and that the FAIMA and UNSTPB names and logos belong to the
university.

## Offline

After the first sync every screen works without a connection. A thin bar
says "Offline, timetable from <date and time>" while there is no
connection; nothing else changes.

## Widgets

One widget, "Orarul de azi", that changes its layout with the size it is
given on the home screen (design/mockups/Widgets.png):

| Size | Shows |
| --- | --- |
| Small (2 x 2) | The class happening now ("Acum, până la 14:00") or the next one ("În 25 min", "Azi", "Mâine", a day name): time, subject, type and room |
| Medium (4 x 2) | The date and the teaching week, then the rest of today, one line per class with its room; once today is over, the next day with classes |
| Large (4 x 4) | Today's remaining classes in full, then the next day with classes |

It reads the app's offline copy, so it works without the app open and
without signal, and uses the phone's light or dark theme. It is redrawn
after every update of the app's data, every 5 minutes while a class starts
within the hour, otherwise at the next class's start or end and at least
hourly. Tapping it opens the app.

## Public page (web, part of the server)

At `/`, in Romanian or English (`?lang=en`, or the browser's language): what
the app does, download buttons from `ETT_ANDROID_URL` and `ETT_IOS_URL`
("în curând" while one is not set), two screenshots of the app (one light,
one dark, different screens), today's teaching week from the current
semester, three steps to get started, and a dark ending with the way to the
dashboard for the secretariat, a line drawing of the Rectorat behind it, the
UNSTPB and FAIMA logos and the Multimedia Entertainment Hub credit.

## Admin dashboard (web, part of the server)

The Gestionare orar section searches subject names, professors, rooms and
groups across the current semester. It can filter by day or show classes
without a room or professor. Staff select up to 200 activities, then choose
a new room, a new professor, a shift of the times in minutes, or any mix of
the three, and preview before confirming.
The preview lists the resulting slots and any new booking conflicts.
Moves across midnight are refused. A changed semester revision requires
staff to reload instead of overwriting another editor's work.
The announcement list has 20 posts per page, with links to older posts.

A dark sidebar with the sections, each page with a title, a context line and
its main actions on the right: an overview (today's week, counts, a list of
what still needs doing), semesters with their calendar, CSV import and
export, groups by series and each group's week; subjects (search, filters,
rename, every activity, one professor for all of a subject's labs or
seminars); single activities (add, edit, delete, with the importer's rules
for days, times and weeks; a new room adds itself); professors (contact,
office, office hours one per line, their activities); rooms by building
(building names and addresses, each room's week); announcements (write,
preview, publish, edit, pin); student accounts (administrators only: search,
a new password that signs them out, delete); admin accounts.

Timetable reports have a sidebar entry and an open count on the overview.
The list separates open reports from closed ones. A report shows the
student's name and email, description, original class details, submission
time and status.
"Edit class" opens the activity's existing editor when the class still
exists. Staff save any correction there, then return to the report and
mark it fixed. "Dismiss" closes a report without changing the timetable.
Closed reports remain available as history until the student deletes the
account. Both administrators and editors can handle reports.
