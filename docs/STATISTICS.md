# Statistics guide

Every statistic and chart the app can show: what it answers, how it is
calculated, and when it can mislead. The app shows the short version of each
explanation when you tap a value or a card title on the statistics screen.

## Why the defaults look the way they do

Habit counts such as cigarettes per day are **skewed**: most days are alike and a
few (a party, a stressful day) are far higher. The mean and the standard deviation
react strongly to those few days. The median breaks down only when half the days
are extreme, a 10% trimmed mean when 10% are, and the mean with a single one. So
the defaults lead with robust statistics (median, trimmed mean, the middle half)
and with *when* you do it. The plain mean, standard deviation and variance stay
available as options.

Research behind the choices (mostly from smoking cessation):

- Time to the first cigarette of the day is the strongest single indicator of
  nicotine dependence; a later first cigarette is a clear sign of progress
  (Muscat et al., 2009; review PMC7358112).
- Craving and mood vary within the day and predict the next cigarette, so *when* you
  smoke matters as much as how much (Berkman et al., 2011).
- Missing a day barely affects habit formation, so "days within target" is used
  instead of a streak that resets to zero (Lally et al., UCL).
- Robust statistics: breakdown points of the mean, trimmed mean and median; the
  IQR as a robust spread (standard statistics references).

## Common rules

- **Window**: everything uses the History window (all time by default).
- **Periods**: values are per day, week, month or year, following the tab you pick.
  Periods without entries count as 0.
- **The period still running** (today, this week…) is left out of per-period
  statistics, so a half-finished day doesn't count as a low one, unless it is the
  only period.

## Default statistics

| Statistic | Answers | How it is calculated |
| --- | --- | --- |
| **Typical (median)** | What is a normal day? | The middle value of the per-period counts: half are lower, half higher. |
| **Trimmed average** | A typical day, using more of the data | The mean after dropping the highest and lowest 10% of periods (rounded down). |
| **Middle half** | How much do my days vary? | Q1–Q3: the range of the middle 50% of periods (linear interpolation between values). |
| **Range** | My lowest and highest | Minimum–maximum of the per-period counts. |
| **Usual first** | How early do I start? | Median time of the day's first entry at or after the day start (5:00 by default, or the *Day starts at* setting), over finished days that have one. |
| **Since last** | How long have I held off? | Time since the most recent entry. |
| **vs previous 7** | Am I doing more or less lately? | Total of the last 7 finished periods compared with the 7 before: (recent − before) / before. Needs 14 periods. |

## Optional statistics

| Statistic | How it is calculated | Watch out |
| --- | --- | --- |
| **Average** | Mean of the per-period counts. | One unusual day pulls it up. |
| **Std. dev.** | Population standard deviation of the per-period counts. | Inflated by unusual days. |
| **Variance** | Square of the standard deviation (for one series, its covariance with itself). | Same. |
| **Min / Max** | Lowest / highest per-period count. | |
| **Total** | Every entry in the window, including the running period. | |
| **With none** | Periods with no entry, of all periods. | |
| **Within target** | Of the last 30 periods, how many stayed at or under the target set in *Customize*. | Shows "–" without a target. |
| **Usual gap** | Median time between consecutive entries. | Includes the overnight gap. |
| **Usual last** | Median time of the day's last entry. | |

## Charts

- **History**: one bar per period. The line is the **rolling median** of the last 7
  periods (a trend that one unusual day doesn't distort). A dot marks an
  **unusually high** period: above Q3 + 1.5 × IQR of the finished periods (Tukey's
  fence; needs at least 4 periods).
- **Time of Day** (Day tab): the share of days with an entry *around* each time.
  Each entry adds a bell curve of height 1 (σ = 20 min); curves of one day combine as
  "at least one entry around this time", and the result is averaged over all days.
  The axis can start at the *Day starts at* hour.
- **Day of Week / Day of Month / Month of Year** (Week, Month, Year tabs): for each
  weekday, day of the month or month, the share of those days (months) that had at
  least one entry. Each day of the month is compared only with the months that have it.
- **Entries**: every entry of the selected period.

## Customising

*Customize* on the statistics card (or *Statistics* in the habit editor, also when
creating a habit) chooses, per habit: the statistics shown, which cards appear, a
per-period target, and the hour the day starts. *Reset to Default* restores the set
above.
