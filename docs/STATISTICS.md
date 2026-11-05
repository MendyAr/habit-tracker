# Statistics guide

Every statistic and chart the app can show: what it answers, how it is
calculated, and when it can mislead. The app shows the short version of each
explanation when you tap a value or a card's title on the statistics screen
(the charts themselves only select what you touch).

## Why the defaults look the way they do

Habit counts such as cigarettes per day are **skewed**: most days are alike and a
few (a party, a stressful day) are far higher. The mean and the standard deviation
react strongly to those few days. The median breaks down only when half the days
are extreme, a 10% trimmed mean when 10% are, and the mean with a single one. So
the defaults lead with robust statistics and show the mean and standard deviation
next to them, with a note saying what pulls them up.

The defaults are exactly the set of the owner's research report, *What to measure
when tracking smoking* ("Habit tracking metrics"): a typical day without the party
effect, cigarettes per day with flagged outlier days, the share by hour, the first
cigarette of the day, the median per weekday, what sets it off, and for Box
Breathing the median time to calm with its trend. Everything else is an option in
*Customize*.

Research behind the choices (mostly from smoking cessation):

- Time to the first cigarette of the day is the strongest single indicator of
  nicotine dependence; a later first cigarette is a clear sign of progress
  (Muscat et al., 2009; review PMC7358112).
- Craving and mood vary within the day and predict the next cigarette, so *when* you
  smoke matters as much as how much (Berkman et al., 2011).
- Smoking differs by weekday (teen smokers smoke more at weekends), but one heavy
  Saturday shouldn't make every Saturday look heavy, hence the median per weekday.
- Missing a day barely affects habit formation, so "days within target" is used
  instead of a streak that resets to zero (Lally et al., UCL).
- Robust statistics: breakdown points of the mean, trimmed mean and median; the
  IQR as a robust spread; Tukey's fence for outliers (standard statistics references).

## Common rules

- **Period**: values are per day, week, month or year. The period is chosen in
  *Customize* (Day by default) and remembered per habit; the explanations name it
  ("Your typical week…"). Periods without entries count as 0.
- **Window**: everything uses the chart window (all time by default; tap the dates
  on the per-period chart to narrow it).
- **The period still running** (today, this week…) is left out of per-period
  statistics, so a half-finished day doesn't count as a low one, unless it is the
  only period.

## The statistics card

The statistics are shown in groups: **Typical**, **Spread**, **Timing** and
**Progress**, each value with a one-line note.

### Default

| Statistic | Group | Answers | How it is calculated |
| --- | --- | --- | --- |
| **Median** | Typical | What is a normal day? | The middle value of the per-period counts: half are lower, half higher. |
| **Trimmed mean** | Typical | A typical day, using more of the data | The mean after dropping the highest and lowest 10% of periods (rounded down). |
| **Mean** | Typical | The plain average | Mean of the per-period counts. Note: pulled up by heavy days. |
| **Mean without flagged days** | Typical | What is an ordinary day? | The mean of the periods that are not flagged as unusually high (see below); the note says how many were flagged. |
| **IQR (middle half)** | Spread | How much do my days vary? | Q3 − Q1 (linear interpolation between values); the note shows Q1–Q3. |
| **Standard deviation** | Spread | The classic spread | Sample standard deviation (n − 1) of the per-period counts. Note: inflated by heavy days. |

### Optional

| Statistic | Group | How it is calculated | Watch out |
| --- | --- | --- | --- |
| **Range** | Spread | Minimum–maximum of the per-period counts. | |
| **Variance** | Spread | Square of the standard deviation (for one series, its covariance with itself). | Inflated by unusual days. |
| **Min / Max** | Spread | Lowest / highest per-period count. | |
| **Usual first** | Timing | Median time of the day's first entry at or after the day start. | The *First Entry of the Day* card shows it with Q1 and Q3. |
| **Usual last** | Timing | Median time of the day's last entry. | |
| **Since last** | Timing | Time since the most recent entry. | |
| **Usual gap** | Timing | Median time between consecutive entries. | Includes the overnight gap. |
| **vs previous 7** | Progress | Total of the last 7 finished periods against the 7 before: (recent − before) / before. | Needs 14 periods. |
| **Within target** | Progress | Of the last 30 periods, how many stayed at or under the target set in *Customize*. | Shows "–" without a target. |
| **With none** | Progress | Periods with no entry, of all periods. | |
| **Total** | Progress | Every entry in the window, including the running period. | |

## Cards

Default for count habits, in this order:

- **Entries per Day** (Week, Month, Year): one bar per period. A period above
  **Q3 + 1.5 × IQR** of the finished periods (Tukey's fence; needs at least 4) is
  **flagged** in red. A solid line marks the **median** and a dashed one the
  **mean**. The caption gives the number of flagged periods and the range of all of
  them. Tap or drag across the bars to select one.
- **Share by Hour**: of all entries in the window, the share in each clock hour.
  Every entry counts (a busy hour weighs more). The **three busiest hours** are darker
  and the caption gives their combined share: where a nudge or a replacement ritual
  helps most.
- **First Entry of the Day**: the median, Q1 and Q3 of the time of each finished
  day's first entry at or after the day start (5:00 by default, or the *Day starts
  at* setting).
- **Day of the Week**: for each weekday, the median of its daily counts (bar) and its
  Q1 to Q3 (line); the weekday with the highest median is darker. Every finished day
  counts, days without entries as 0. With fewer than 4 of a weekday the caption says
  it is still rough.
- **What Sets It Off?**: of the entries in the window tagged with a trigger, the
  share of each trigger, most common first (darker). The caption says how many of the
  window's entries were tagged; untagged entries are left out of the shares.
- **Entries**: every entry of the selected period, with its trigger. Tap one to
  delete it or to set its trigger.

Optional: **Time of Day Curve** (the share of days with an entry *around* each time:
each entry adds a bell curve of height 1, σ = 20 min, combined per day as "at least
one entry around this time" and averaged over all days; with a Week, Month or Year
period it becomes the share of each weekday, day of the month or month with at
least one entry), and the per-period chart for duration habits.

## Duration trackers

Statistics of the lengths of the finished sessions that started in the window
(time to calm down is right-skewed too: a few slow sessions, so the same robust set
applies).

| Statistic | How it is calculated |
| --- | --- |
| **Median time** (default) | Median session length. |
| **Mean time** (default) | Mean session length; slow sessions pull it up. |
| **IQR (middle half)** (default) | Q3 − Q1 of the session lengths; the note shows Q1–Q3. |
| **Under target** (default) | Share of sessions shorter than the target: 5 minutes (the report's example), or the minutes set in *Customize*. |
| **Range** (option) | Shortest–longest session. |
| **Sessions** (option) | Number of finished sessions. |
| **Total time** (option) | All sessions added up. |

Cards: **Time per Session** (default) shows each session as a dot in the order they
happened, a line through the **median of the last 7 sessions** (using the sessions
available at the start), and a dashed line at the target. **Durations** (option)
shows every session as a bar, sorted from shortest to longest, with a line at the
median.

## Customising

*Customize* on the statistics card (or *Statistics* in the habit editor, also when
creating a habit) chooses, per habit: the period the statistics are grouped by, the
statistics shown, which cards appear, a target, and the hour the day starts. *Reset
to Default* restores the sets above (and keeps the period). Setups saved by 2.0 keep
their chosen statistics and hidden cards.
