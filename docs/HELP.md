# Scooter Link - Help

A more detailed companion to the in-app help (Help section in the side menu). For installation,
supported models and the technical background see the [main README](../README.md).

## Connecting

- **First time**: "Add scooter" → cloud login (password or QR code) → the app fetches the
  Bluetooth key (`ltmk`) once and stores it, encrypted, on the phone. From then on it never talks
  to the cloud again for that scooter.
- **Every time after**: tap the scooter's tile in the device list. The app waits for the scooter's
  own Bluetooth advertisement, then connects directly - this also means the scooter has to be
  reachable (switched on, or - for models that support it - locked and in its sleep state; see
  "Standby" below) and within range.
- **"Verbinde per Bluetooth ..." / "Connecting via Bluetooth ..." takes a while**: a connection
  attempt is retried up to 5 times, 3 seconds each. On a weak or distant connection this can take
  15 seconds or more before it either succeeds or gives up with an error.
- **The connection drops on its own once**: the app reconnects by itself, once, if the connection
  is lost while the device list is showing (e.g. right after the scooter woke up from its sleep
  state, which resets its Bluetooth). If that single retry also fails, the tile shows why.
- **A scooter that stops answering for 15 seconds** counts as disconnected, even if the Bluetooth
  link itself never formally reports a drop - this is deliberate, so the app never keeps showing
  values that are actually stale.

## The dashboard tabs

Each tab mirrors one group of the scooter's own properties. All of them are also summarised in
the in-app Help section with the current wording; this section goes a little further on the parts
that aren't obvious from the property names alone.

### Overview
The two big numbers are the range and the battery level. The range switches automatically to your
own measured consumption once there's enough data for the current riding mode (see "History"
below) - until then, or with that feature off, it shows the scooter's own estimate. "Ride time" on
this tab is the app's own timer (see "The ride timer" below), not the scooter's raw value.

### Ride / Battery / Settings / Vehicle / Identification
Plain lists of the scooter's own properties, grouped by topic rather than by the scooter's
internal numbering (which mixes unrelated things together in places). Everything here is a direct
read (or, where settable, write) of one property - no interpretation happens in between, except
for the "Ride time" tile described above.

Two notes:
- **Cruise control and the tail light** show a legal notice before you turn them on for the first
  time, because whether they're allowed depends on your country and local rules - the app has no
  way to know your jurisdiction, so it asks you to confirm rather than silently allowing or
  blocking either.
- **Standby**: when the scooter reports its sleep state ("Ruhezustand", visible on the Vehicle
  tab), every value it reports is frozen - it will, for example, keep claiming it's charging long
  after the charger was unplugged. The app detects this and shows "Standby" in place of every
  value instead of displaying stale numbers as if they were live; only "Find scooter" keeps
  working. Everything is read again the moment the scooter wakes up.

### Ride log
The scooter itself only remembers its 5 most recent rides (it overwrites the oldest whenever a
new one finishes). This app copies new rides into its own, unbounded ride book on the phone every
time it reads the scooter, grouped by day. A ride that has already been overwritten in the
scooter's own memory before the app got a chance to read it is gone for good - there is no way to
recover it afterwards.

Two known limitations, both accepted design trade-offs rather than bugs:
- Rides recorded before the ride book existed on this phone have no date (shown under "No date").
- If the scooter's own 5 slots happen to be re-read while a ride is still being written (a stop
  briefly followed by more riding, or the app reconnecting mid-ride), very old app versions could
  log the same ride multiple times as it grew. This is fixed from version 3.7 on, and existing ride
  books get a one-time automatic cleanup for it on first launch after updating.

### History
This is the app's own consumption analysis, independent of the scooter's ride log above - it is
built from the rider's own recorded rides, live, while the phone stays connected during a ride and
the "ride tracking" setting is on. It only ever looks at the most recent 300 km per riding mode, so
an ageing battery or worn tyres show up in the numbers on their own without anyone resetting
anything manually. "Reset history" is for a known, one-off event instead (e.g. a battery
replacement) - it discards everything recorded so far for a clean start.

The recent-rides chart and cards on this tab use the same underlying data, grouped into "trips"
(a trip is one or more of these recorded segments with no gap longer than 10 minutes between
them). Because this list only ever contains rides recorded while genuinely connected, it commonly
does **not** match the ride log/ride book above exactly - that one comes from the scooter's own,
independent memory, which keeps counting even when the phone wasn't connected. Neither list is
wrong; they simply measure different things.

A related, deliberate accuracy trade-off: a very short ride interrupted before the battery has
ticked down by even one full percentage point is discarded entirely rather than counted
partially, to avoid a ride that happens to straddle a charging session from corrupting the
consumption average. In everyday riding with frequent short stops (traffic lights, etc.) this
means the History tab's totals tend to run a little lower than what the scooter's own odometer or
ride log would show for the same period - again, not an error, just where the line is drawn.

### App settings
The "range overlay" is the one feature here worth calling out specifically: once switched on, it
puts a small, draggable window with the range, battery level, trip distance and the app's own ride
timer on top of whatever else you're using (a maps app, for instance), and keeps updating even
with Scooter Link itself minimized - which is also what keeps the Bluetooth connection and ride
recording alive in the background in general, via a small ongoing notification Android requires
for that. Tap the overlay to bring the app back to the front; drag it to reposition it. It needs
the "display over other apps" permission, requested the first time you turn it on.

## The ride timer
The scooter's own "riding time" value resets to zero the moment it briefly stops reporting motion
- at a red light, for instance - even mid-ride. The app keeps its own, separate timer instead: it
starts when a ride begins, keeps running through a short stop, and only really ends once the
scooter has been standing still for a full 10 minutes (matching the gap the History tab's own trip
grouping uses) or the connection ends. It is shown on the Overview tile "Ride time" and, if
enabled, in the floating overlay - it never affects the consumption calculation described above,
which is entirely separate and untouched by any of this.

## Backup, export and privacy

- **Export** (per scooter): a code containing that scooter's Bluetooth key, for sharing access
  with someone else who is legitimately allowed to use it (family, etc.) - no cloud account needed
  on their end.
- **Backup** (all scooters at once): one file with every saved scooter's key, documents, history
  and the app's own settings, optionally password-protected.
- Both stay entirely on the device(s) involved - there is no server of this project's own, and
  nothing is uploaded anywhere by either feature. See the main README for the full privacy
  statement.

## Getting more help

If something here doesn't cover your situation, the "Diagnose kopieren" / "Copy diagnostics"
button in App settings puts together a short, scrubbed report (app/phone/scooter model, your
settings and the last few error messages - no MAC address, keys or documents) that's useful to
attach to a GitHub issue.
