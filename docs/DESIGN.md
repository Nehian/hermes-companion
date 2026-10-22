# App design guide

The Android app looks like one product because every screen is built from the same tokens in
`apps/frontend/app/src/main/java/com/hermes/companion/ui/Theme.kt` and the same building blocks in `Neu.kt`.
**Do not use raw colours, sizes or fonts in a screen. Use the tokens, or add one here first.**

## Look
Soft white "Neumorphism": one light source (top left), dark text, one amber accent. The alarm screen is the exception: it is
flat, white, very large and very high contrast, in light and dark mode, because it is read half asleep or in sunlight.

## Colours (`HermesColors`, light / dark)
| Token | Light | Dark | Use |
|---|---|---|---|
| surface | `#EEF0F4` | `#1C1F27` | screens, cards |
| ink | `#1A1D26` | `#ECEEF3` | text |
| muted | `#4A5160` | `#A7AEBD` | secondary text |
| edge | `#7B8396` | `#8790A3` | outline of anything you tap (3:1) |
| accent | `#9A5200` | `#E3A85A` | the one accent: primary buttons, "next", priority |
| done | `#1B6E42` | `#7FC49A` | done |
| missed | `#AE3426` | `#E58A7D` | missed, needs attention |

All text pairs are at least 4.5:1 on the surface. Colour never carries meaning alone: Done, Missed and Needs attention always
have a word or an icon as well.

## Type (`HermesTypography`, Plus Jakarta Sans, tabular numbers)
Display 34/40 bold, alarm time 84/88 extra-bold, title 17/22 semibold, body 16/22 semibold, secondary 14/20, label 13 caps.
Nothing below 12 sp; text follows the phone's font size setting and wraps instead of being cut.

## Space and shape
Steps `Sp`: 4, 8, 12, 16, 20, 28 dp. Screen edge 20, gap between cards 12, between sections 20.
Radii `Radius`: control 16, card 22, panel 28, pill 26. Tap targets at least `MinTap` (48 dp).

## Depth (`Modifier.raised`, `Modifier.inset`)
* **raised**: cards and buttons. **raised high** (depth 8, blur 18): panels and the nav bar.
* **inset**: inputs, pressed buttons, the selected tab, the "next" activity.
* **flat**: the alarm screen only.

Soft shadows never carry meaning on their own: every control also has a 1.5 dp outline in `edge`.

## Components (`Neu.kt`)
`NeuCard`, `NeuButton` (outlined; `primary` = filled accent; `small`), `NeuField`, `NeuSwitch`, `SectionLabel`, `StateWord`,
`SettingRow`, and the icon set `HermesIcons` (24 px grid, 2 px strokes, rounded ends, never emoji).

## Rules
* One primary action per screen.
* Press: a raised button sinks to inset. Reduced-motion settings must be respected by any animation added.
* The alarm screen keeps the first answer as the big filled button (green when it completes the reminder) and the other
  answers outlined. A priority reminder shows a "Priority" tag.
* Dark mode follows the phone; check every text pair again when adding a colour.
