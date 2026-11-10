# Design

The look of the apps and the dashboard. The tokens here are the source for
the Compose theme, the SwiftUI theme and the dashboard's CSS; change them
here first.

## Direction

FAIMA's own identity is monochrome: a black laurel seal and a near-black
website (`#292929`) with small red and yellow accents. Multimedia
Entertainment Hub works the same way: black and `rgb(20 20 20)` surfaces, the
maple red `#d92a21` and the two blues of the MEH mark. The app keeps that:
neutral surfaces, ink-coloured text, and colour only where it carries
information (the current time, the subject, a change, something to tap).
Two of FAIMA's own colours are used on purpose: its near-black `#292929`
is the text colour in the light theme, and its yellow `#F5C753` marks
changes to the timetable. Nothing else of the website's look is copied.

Things we do not do: gradients (except the event page's fade into the
background), glows, glass effects, emoji, oversized
greeting headlines, stat tiles that repeat what the timetable already
shows, cards inside cards, and illustrations as filler.

## Colour

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| `background` | `#FAFAF9` | `#0A0A0A` | Behind everything |
| `surface` | `#FFFFFF` | `#141414` | Sheets, list rows, timetable blocks |
| `surfaceAlt` | `#F2F2F0` | `#1E1E1E` | Grouped sections, the week grid |
| `border` | `#E3E3E0` | `#2A2A2A` | Hairlines, 1 px |
| `text` | `#292929` | `#EDEDED` | Body text (FAIMA's near-black) |
| `textMuted` | `#5C5C5A` | `#9A9A9A` | Secondary text |
| `primary` | `#1D6FA5` | `#2EBFF6` | Links, selected tab, buttons |
| `onPrimary` | `#FFFFFF` | `#0A0A0A` | Text on `primary` |
| `now` | `#D92A21` | `#F0564C` | The current-time line, "live" status, unread marks |
| `change` | `#F5C753` | `#F5C753` | Background of change notices, such as a moved room (text `#292929`, 9.1:1) |
| `error` | `#D92A21` | `#F0564C` | Errors and refused picks |

`primary` is a darker step of the MEH blue `#2689ca`, which only reaches
3.8:1 on white. Measured contrast (WCAG formula): `text` on `background`
13.9:1 (light) and 15.7:1 (dark), `textMuted` 6.4:1 (light) and 6.6:1 (dark), `primary` 5.4:1 on
white and 8.7:1 on `#141414`, `now` 4.9:1 on white and 5.4:1 on `#141414`.

### Subject colours

Each subject gets one of eight muted colours, assigned in a fixed order
and changeable by the student, as in Smart Timetable. It appears as a 4 px
bar on the left of a timetable block and a tint behind it (12% light, 20%
dark), never as a full fill.

`#4C6A92` slate blue, `#2F7F7A` teal, `#6F7F2F` olive, `#B07A1F` ochre,
`#A84B3A` brick, `#7A4C7F` plum, `#4B4F9E` indigo, `#3F7A4F` moss.

### Activity types

Shown as a short text label, not a colour: Curs, Seminar, Laborator,
Proiect (Course, Seminar, Lab, Project in English).

## Type

Libre Franklin (SIL Open Font License, bundled with the apps), the MEH
typeface. Times use tabular figures so columns of hours line up.

| Style | Size / line | Weight |
| --- | --- | --- |
| Title | 22 / 28 | 600 |
| Heading | 17 / 22 | 600 |
| Body | 15 / 21 | 400 |
| Label | 13 / 18 | 500 |
| Caption | 12 / 16 | 400 |
| Time (timetable) | 13 / 16 | 600, tabular |

Sentence case everywhere, including buttons and tabs.

## Shape and space

- 4 px grid. Screen gutters 16 px on phones, 24 px on tablets.
- Corner radius 8 px for blocks and sheets, 6 px for buttons and inputs.
- Separation by hairline borders and spacing. Shadows only on sheets and
  menus that float over content.
- Touch targets at least 48 x 48 dp (Android) and 44 x 44 pt (iOS).
- Event cards: all the same size, the picture full width on top cut to
  16:9 from the centre, clipped by the card's 8 px corners, on `surfaceAlt`
  while it loads; under it the title in Title and one Caption line in
  `textMuted`, one line each. No text over the picture on a card. Past
  events at 70 % opacity.
- The event page is the one place a picture goes behind content: it fills
  the top (1:1 to 3:4), stays clear for its upper half and fades to the
  `background` colour at its foot, where the title starts. This is a fade
  into the page, not a decorative gradient; it works the same in both
  themes.

## Motion

Short and functional: 150 ms for state changes, 250 ms for sheets and
screen changes, standard platform easing. Nothing animates on its own while
the student is not touching the screen, apart from the current-time line.

## Logos

- The FAIMA seal appears on the sign-in screen (168 dp wide, 112 dp while
  creating an account) and on the About screen (72 dp). In the dark theme it
  is shown in white. It is never recoloured, stretched, cropped or placed on
  a subject colour.
- The Multimedia Entertainment Hub mark appears next to the copyright on
  the About screen (34 dp, with the site and support links) and, smaller and
  without links, in the sign-in footer (20 dp).
- The public page (`/`) shows the seal in its top bar (black, white in the
  dark theme) and ends on FAIMA's near-black (`#292929`, `#141414` in the
  dark theme) with the white seal, the faculty's and the university's names
  and the Multimedia Entertainment Hub mark beside "Dezvoltată de".
- The launcher icon is the white seal on `#292929`, 60 dp wide inside the
  108 dp adaptive icon, so launchers that cut a circle keep the lettering.
  The same image is the monochrome (themed) icon.
- Files are in `design/brand/`: `faima-seal.png` (250 x 191, black) and
  `faima-seal-white.png` (512 x 505, cut from the 1267 px white logo on
  faima.upb.ro, without the faculty name under the seal). We still need a
  vector version from FAIMA before the first store release.

## Icons

Material Symbols (outlined, weight 400) on Android and in the dashboard, SF
Symbols on iOS. No custom illustrations.

## Dashboard

The dashboard uses the same tokens, with Libre Franklin served by the server
itself. Its layout was chosen on 10 November 2026 from three drawn options
(the one called "Evoluție": the existing tokens made consistent, no new
brand colours):

- A page frame on every page: the ink sidebar (`#292929`, `#141414` in the
  dark theme) with the sections grouped under Orar, Comunicare and Conturi,
  each with a stroke icon, and a count of open problem reports; a top bar
  with the teaching week as the app shows it ("7 · Săptămâna impară"), the
  current semester and the theme switch.
- One table component (`tableCard` in `admin/Components.kt`): a filter bar,
  uppercase hairline headers, row hover, and a footer with "11-20 din 243",
  10, 25 or 50 rows per page and the page links. Every list with more than
  a handful of rows uses it; the paging happens in the database where the
  list comes from there.
- Empty tables say what is missing and what to do, never just "nimic".
- One week grid (`weekGrid` in `admin/WeekGrid.kt`) wherever the dashboard
  shows a week: Gestionare orar's "Săptămână" view and the group, room and
  professor pages. An hour axis from 08:00 to 20:00 (stretched when a class
  falls outside), a column per weekday, each class a block placed by its
  times with the 4 px subject bar and tint, its type as text, room, time
  and an "Impară" or "Pară" mark. Overlapping classes share the day in
  sub-columns. Subject colours go by the subjects' ids in sorted order, so
  a subject keeps its colour between pages. Office hours are neutral
  blocks. Toate / Impare / Pare are links that fade the other weeks'
  classes. On a phone the grid scrolls sideways inside its card.
- A flow of several steps (the timetable import) shows a stepper on top:
  numbered steps, the done ones with a green check and a link back, the
  current one underlined in the primary colour. Changes are tagged
  "Adăugată" (green outline), "Modificată" (yellow), "Eliminată" (red
  dashed) and "Eroare" (red); a changed value shows the old one struck
  through and the new one on yellow, as on the app's "Modificat" cards.
- Dark theme from the dark column of the colour table. It follows the
  system until the admin picks one with the switch; the choice is a cookie,
  so the server renders the right theme and nothing flashes.
- Motion: 150 ms for hover and focus, 180 ms for a page's content rising
  into place. `prefers-reduced-motion` turns all of it off.
- JavaScript is optional: `admin/static/admin.js` adds the theme switch,
  applies the rows-per-page choice as soon as it changes and keeps the
  phone preview on the compose pages in step with the form (the Markdown
  body through `POST /admin/preview/markdown`, about 300 ms after the last
  key; a chosen picture at once, from the file on the computer). Without it
  every page still works through plain links and forms.
- The compose pages for announcements and events put the form on the left
  and a 320 px phone on the right (under the form on narrow screens). The
  phone uses the app's own tokens in either theme, whatever the
  dashboard's theme, and its Card / Pagină and Luminos / Întunecat
  switches are radio buttons read by CSS.

## Public page

The page at `/` uses the same tokens and the same direction ("Evoluție"):
a top bar with the seal, links to the sections, the RO / EN switch and
"Descarcă"; the hero with the teaching week as the app's ticket ("7 ·
Săptămâna impară · 9 - 13 noiembrie"), the headline, the store buttons and
three short facts; one app screenshot in a 28 px rounded frame (the light
one, the dark one under `prefers-color-scheme: dark`); "Cum funcționează"
in four numbered steps on a `surface` band; "Ce face aplicația" as rows
with a stroke icon and a hairline under each; "Pentru secretariat" as one
bordered panel with the link to `/admin`; the privacy note; the footer.

- It follows the system theme, with the dark column of the colour table.
- A store button without a link is a grey dashed block with "în curând".
- Motion: 150 ms for hover and focus colour changes and the buttons' 1 px
  lift; smooth scrolling to the sections only without
  `prefers-reduced-motion`, which also turns the transitions off.
- From 720 px down the section links move to a second row of the top bar;
  from 520 px down the gutters are 16 px and the store buttons full width.
  Nothing scrolls sideways at 390 px.
- Everything comes from the server: Libre Franklin from
  `/admin/static`, the pictures from `/static`, no scripts.

## Platform behaviour

Each app follows its platform: Material 3 components and navigation on
Android, standard SwiftUI navigation and sheets on iOS. The tokens above
keep them recognisably the same app.

## Mockups

The approved screens are in [`design/mockups/`](../design/mockups/) as PNG
files, with their sources in `design/mockups/source/`. After changing a
source, regenerate the images:

```sh
cd tools/render-mockups && npm install && npx playwright install chromium
node render.mjs            # all screens
node render.mjs Main Week  # only these
```

The mockups use made-up names, rooms and announcements. Only the group code
format (`1541E`, half-groups A and B) and the building codes follow FAIMA's
real ones.
