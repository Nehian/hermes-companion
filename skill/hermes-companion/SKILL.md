---
name: hermes-companion
description: "Set, change, cancel and check reminders that ring as real alarms on the user's Android phone, even offline and on silent. Use whenever the user asks to be reminded of something, asks what reminders they have, or asks whether they did something they were reminded of."
version: 1.0.0
platforms: [linux, macos]
---

# Hermes Companion

One command, `{{HC}}`, sends reminders to the user's phone through their own server. The phone rings them itself.

## Rule 1: ask how, every time
For each NEW reminder, unless the user already said how, ask one short question and wait for the answer:

> Alarm on your phone, a message in this chat, or both?

- A repeating reminder ("every morning at 8") is asked once, for the whole series.
- Do not remember the answer for next time. The next new reminder gets the question again.
- If the user already said ("set an alarm", "just message me"), do not ask.
- Answer "chat" or "message": do NOT use `{{HC}}`. Remind them through your normal messaging.
- Answer "alarm": use `{{HC}} send`. Answer "both": do both.

## Commands (exact)
Create:
```
{{HC}} send --title "Take tablet" --at "2026-10-23 08:00"
{{HC}} send --title "Stand up" --in 30m
{{HC}} send --title "Take tablet" --at 08:00 --repeat daily --days 14
```
Options: `--message "text"`, `--style alarm|alarm-silent|banner|banner-silent` (default alarm), `--category name`,
`--group name` (reminders in one group cancel each other when the user taps Done), `--expires MINUTES` (default 120),
`--priority` (opens full screen on the phone even while it is in use: only when the user says it is important).

Use the user's local time, 24-hour clock. If am/pm or the day is unclear, ask before sending.

Look:
```
{{HC}} list              # reminders waiting to ring
{{HC}} answers           # what the phone answered (Done, Snoozed, Missed)
```
Cancel:
```
{{HC}} cancel JOB_ID
{{HC}} cancel --group GROUP
```
Check everything works (add `--ring` to ring a test alarm): `{{HC}} doctor`

## After sending
Reply in ONE sentence: what, when (local time), and how (alarm or banner). Example: "Done: alarm at Fri 23 Oct 08:00 for 'Take tablet'." Nothing else.

## If a command fails
1. It prints one line starting with `ERROR:`. Show that exact line to the user. Do not paraphrase it.
2. Run `{{HC}} doctor` once and show its last line.
3. STOP. Do not try workarounds, other tools, direct web requests or changes to files.
4. Offer once: "Shall I remind you by message instead?" and do that only if they say yes.

## Never
- Never read, print, copy or ask for the admin key or the file `docker/.env`. `{{HC}}` handles it.
- Never run `docker`, `curl` or edit server files for a reminder. Use `{{HC}}` only.
- Never send a reminder the user did not ask for.
- Never say a reminder was sent unless `{{HC}}` printed `"ok": true`.

## Answers
`{{HC}} answers` shows what the user did on their phone. Use it when they ask "did I take it?" or "what did I miss today?".
Answers show as Done, Missed or Snoozed with the time.

## The dashboard
If the user wants to see or change their reminders on a screen, tell them to run `dashboard.sh` in the Hermes Companion folder and open the address it prints. Do not run it for them: it asks them to choose a password.
