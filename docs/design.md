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

Things we do not do: gradients, glows, glass effects, emoji, oversized
greeting headlines, stat tiles that repeat what the timetable already
shows, cards inside cards, and illustrations as filler.

## Colour

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| `background` | `#FAFAF9` | `#0A0A0A` | Behind everything |
| `surface` | `#FFFFFF` | `#141414` | Sheets, list rows, timetable blocks |
| `surfaceAlt` | `#F2F2F0` | `#1E1E1E` | Grouped sections, the week grid |
| `border` | `#E3E3E0` | `#2A2A2A` | Hairlines, 1 px |
| `text` | `#141414` | `#EDEDED` | Body text |
| `textMuted` | `#5C5C5A` | `#9A9A9A` | Secondary text |
| `primary` | `#1D6FA5` | `#2EBFF6` | Links, selected tab, buttons |
| `onPrimary` | `#FFFFFF` | `#0A0A0A` | Text on `primary` |
| `now` | `#D92A21` | `#F0564C` | The current-time line, "live" status, unread marks |
| `change` | `#F5C753` | `#F5C753` | Background of change notices (text stays `#141414`) |
| `error` | `#D92A21` | `#F0564C` | Errors and refused picks |

`primary` is a darker step of the MEH blue `#2689ca`, which only reaches
3.8:1 on white. Measured contrast (WCAG formula): `text` on `background`
17.6:1, `textMuted` 6.4:1 (light) and 6.6:1 (dark), `primary` 5.4:1 on
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

## Motion

Short and functional: 150 ms for state changes, 250 ms for sheets and
screen changes, standard platform easing. Nothing animates on its own while
the student is not touching the screen, apart from the current-time line.

## Icons

Material Symbols (outlined, weight 400) on Android and in the dashboard, SF
Symbols on iOS. No custom illustrations.

## Platform behaviour

Each app follows its platform: Material 3 components and navigation on
Android, standard SwiftUI navigation and sheets on iOS. The tokens above
keep them recognisably the same app.
