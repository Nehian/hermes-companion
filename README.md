# Hermes Companion

**Reminders you cannot miss. Your AI assistant (or any script) schedules real alarms on your Android phone, and the phone rings them by itself, even offline and on silent.**

Version 0.2.0 · Android · open source (Apache-2.0) · runs on your own computer, no account, no subscription

![The dashboard: three reminders you can edit, switch off or mark done](docs/images/dashboard-today.png)

---

## What is it?
Hermes Companion is three small parts that work together:
1. **A server** that runs on your own computer and keeps your schedule.
2. **An Android app** that receives the schedule and rings the alarms.
3. **A command for your AI assistant** (called "Hermes" here, but any assistant that can run a command will do), so you can say *"remind me to take my tablet at 8"* and it just happens.

There is also an optional **dashboard**, a web page that shows your reminders and lets you change them.

## What problem does it solve?
Normal notifications get missed. They travel through a long chain: your assistant, a server, a push service, your phone's battery manager, an app. If any link breaks (silent mode, battery saving, no signal, a sleeping app), the reminder is late or never arrives. For things that matter, like medication, prayer times or a shift, that is not good enough.

## Why should I use it?
Because the schedule lives **on the phone**. Your assistant sends reminders ahead of time and the phone sets real Android alarms. When the time comes, the phone rings by itself.

| | Push notification apps (ntfy, Pushover and similar) | Phone automation (Tasker and similar) | **Hermes Companion** |
|---|---|---|---|
| Who decides the reminder | Your server, at the moment it fires | You, by hand on the phone | Your assistant, ahead of time |
| Needs a connection when it rings | Yes | No | **No** |
| Real alarm that overrides silent mode | Usually a notification | Yes, if you build it | **Yes, built in** |
| Your answer (Done, Snooze) goes back to the sender | Limited | Not without extra work | **Yes, stored and uploaded later** |
| Tapping Done cancels the rest of that reminder's follow-ups | No | Not without extra work | **Yes** |
| Needs an account with a company | Often | No | **No** |

Those tools are good at what they do. This one is for the case where a missed reminder is not acceptable.

## What can I achieve with it?
- **Medication:** a daily alarm with "Done" or "Snooze". Your assistant can later tell you whether you took it.
- **Prayer times:** a heads-up, a "last chance before it ends" alarm and a check-in for each prayer. Tapping *Prayed* cancels the rest of that prayer's reminders. This is the author's own first use.
- **Work and shifts:** hand-over alarms, "leave now" alarms, end-of-day reviews.
- **Habits:** water, exercise, sleep. The dashboard shows your streak.
- **Anything your assistant should be able to wake you for.**

## What do I need?
- An **Android 12 or newer** phone.
- A computer that stays on and runs your assistant: **Linux or macOS** (Windows works through WSL).
- **Docker** on that computer: <https://docs.docker.com/get-docker/>
- **Tailscale** (free) on that computer **and** on your phone, both signed into the **same** Tailscale account: <https://tailscale.com/download>. It lets the phone reach your computer privately from anywhere, with no ports opened to the internet.
- An AI assistant that can run a command (optional: you can also use the `hc` command yourself).

## How do I set it up?
It takes about ten minutes. Do the steps in order.

**1. Tools (once).** Install Docker. Install Tailscale on the computer and on the phone, and sign both into the same account.

**2. Install the server.** On the computer, run one command:
```sh
curl -fsSL https://raw.githubusercontent.com/Nehian/hermes-companion/main/install.sh | sh
```
It checks Docker and Tailscale, builds and starts the server, and prints the two things you need next:
```
Server address:  100.x.x.x
Pairing code:    ABCD2345     (valid for 60 minutes, works once)
```

**3. Install the app.** On your phone, open the [latest release](https://github.com/Nehian/hermes-companion/releases/latest), download `HermesCompanion-0.2.0.apk` and open it. Android will ask you to allow installing from this source. Say yes.

**4. Pair the phone.** Open the app, go to **Settings**, type the **server address** and the **pairing code**, and tap **Pair**. Then tap **Allow** on each row that says "Needs attention" (alarms, full-screen, battery). Tap **Ring a test alarm** to hear it.

**5. Teach your assistant.** On the computer, run:
```sh
~/hermes-companion/connect-agent.sh
```
It copies a short instruction file into your assistant's skills folder (Hermes, Claude Code and Codex are found automatically). If it finds none, it prints one sentence to paste into your assistant's chat.

**6. Check that everything works.**
```sh
~/hermes-companion/hc doctor --ring
```
It checks the server, the phone and Tailscale, then rings a test alarm in one minute. It ends with `ALL GOOD` or one line saying what to fix.

**7. Optional: turn on the dashboard.**
```sh
~/hermes-companion/dashboard.sh
```
It asks you to choose a password, then prints the address to open.

## How do I use it every day?
Just talk to your assistant: *"Remind me to take my tablet every morning at 8 for two weeks."*
- Your assistant asks one short question each time: **alarm on your phone, a message in your chat, or both?** Your answer applies to that reminder only.
- The reminder reaches your phone the next time the app syncs (when you open it, about every 15 minutes in the background, and just before each alarm).
- At the time, the phone rings. Tap **Done** or **Snooze**.
- Ask *"Did I take it?"* and your assistant reads your answers back.

You can also use the command yourself:
```sh
hc send --title "Stand up" --in 30m
hc send --title "Take tablet" --at 08:00 --repeat daily --days 14
hc list          # what is waiting
hc answers       # what you answered
hc cancel JOB_ID
```

## What does the dashboard do?
It is a web page served by your own server, reachable over Tailscale. It is **off** until you run `dashboard.sh`, and it has a password you choose.
- **Today and Upcoming:** every reminder, grouped by activity, with its state (upcoming, done, missed, switched off).
- **History:** the last 14 days, with a done/missed strip for each habit.
- **Phone:** which phones are paired and when each last synced.
- It refreshes itself every few seconds, so an answer you give on the phone shows up without reloading.

![The dashboard at phone width](docs/images/dashboard-phone-width.png)

## What can I customise?
**On the phone (Settings → Sound):** the alarm tone, how loud it rings, and whether it always vibrates.

**On the dashboard, for each reminder:** its time, its title, its style, an on/off switch, and a **Mark done** button for the whole activity.
Styles: *alarm with sound*, *alarm, silent*, *banner with sound*, *banner, silent*. Your phone picks up the change the next time it syncs. A reminder you switch off stays visible and can be switched back on.

The split is simple: the phone decides **how it sounds**, the dashboard and your assistant decide **what each reminder does**.

## Is it safe and private?
- Your schedule and answers stay on **your own computer**. There is no cloud service and no account.
- The server answers only to your own Tailscale network and the computer itself. Nothing is opened to the internet.
- Each phone gets its own revocable key when it pairs. The pairing code works once.
- Your assistant never sees the admin key: the `hc` command reads it from a file by itself.
- The dashboard is off until you switch it on, asks for a password, and stores only a scrambled copy of it.
- Alarm buttons do a fixed set of things (done, snooze, and so on). Nothing can run code on your phone.
- Anyone who is signed into **your Tailscale account** can reach the server's address, so protect that account. See [SECURITY.md](SECURITY.md) to report a problem privately.

## What are the limits?
- **Android only**, version 12 or newer.
- You **install the app yourself** (from the release page). Google Play restricts the exact-alarm permission this app needs.
- It is built for **one person**: one server per person, as many phones as you like.
- Reminders reach the phone when it syncs, so **send them ahead of time** (minutes or hours, not seconds).
- Do not **Force stop** the app: Android then clears its alarms until you open it again.
- On some phones (Samsung especially) switch off battery optimisation for the app, or Android may delay it.
- Tested by the author on one real Samsung phone and the Android 16 emulator. Reports from other phones are very welcome.

## Something went wrong. What do I do?
Run `hc doctor`. It ends with one line saying what to fix. The common ones:

| It says | Do this |
|---|---|
| Docker is not running | Start Docker, run the install command again |
| Tailscale is not signed in | Run `sudo tailscale up` and sign in, then run the install command again |
| no phone is paired | Run `./pair.sh` for a fresh code and enter it in the app |
| no phone has synced yet | Open the app once |
| the pairing code is wrong or has expired | Run `./pair.sh` for a new one |

Still stuck? Open an issue. Include the output of `hc doctor` (it never prints secrets).

## How does it work inside?
```
 your assistant ── hc send ──►  your server  ◄── sync ──►  the app on your phone
                                (FastAPI + SQLite, Docker)       rings exact alarms,
                                      ▲                          sends your answers back
                                      └── the dashboard reads the same server
```
The server stores reminders and answers. The phone downloads the schedule, sets exact Android alarms, and rings them without any connection. A short check just before each alarm drops reminders that were cancelled in the meantime, so you are never rung for something you already handled.

For the full technical reference see [docs/API.md](docs/API.md). To build the app or run the server by hand, see [docs/BUILD.md](docs/BUILD.md).

## Can I help?
Yes. Apache License 2.0, see [LICENSE](LICENSE) and [NOTICE](NOTICE). Contributions are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md).
