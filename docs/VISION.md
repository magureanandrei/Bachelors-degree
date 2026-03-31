# Product Vision & Design Philosophy

## Goal

This app aims to be a **fully context-aware Type 1 Diabetes tracker and clinical decision support system (CDSS)**. It should act as an intelligent companion that helps the user make the safest possible insulin and nutrition decisions at every moment — not just when they explicitly ask.

## Core Principles

### 1. Maximum Context Awareness

Every decision the algorithm makes should consider the fullest picture available: current BG, CGM trend velocity, active IOB, time of day (ICR/ISF segments), planned or recent exercise (type, intensity, duration), external factors (illness, stress, heat), therapy type constraints (MDI vs Pump vs AID), passive activity data (steps, workouts from Health Connect), and any other available signal. The more context the system has, the safer the suggestion.

### 2. Proactive, Not Just Reactive

The app should evolve toward anticipating needs rather than waiting to be asked. This includes: hypo prediction warnings before they happen, pre-sport strategy suggestions when a workout is planned, post-sport late-hypo alerts in the 7–11h window, reminders for missed basal doses, and eventually push notifications for time-sensitive situations. The user should feel like the app is watching out for them.

### 3. Transparency & Explainability

Every suggestion must come with a clear, readable rationale explaining how the system arrived at its conclusion. The user (and their clinician) should always be able to trace back why a dose was adjusted, why rescue carbs were suggested, or why a warning was triggered. No black boxes — the `clinicalRationale` string and `DoseBreakdownCard` exist specifically for this. When adding new logic to the algorithm, always update the rationale text to reflect it.

### 4. Safety-First Defaults

When uncertain, err on the side of caution. Suggest lower insulin rather than higher. Suggest rescue carbs rather than skip them. Show warnings even if they might be false positives. The cost of a missed hypo is always higher than the cost of an unnecessary alert.

### 5. Therapy-Aware Behavior

MDI, Standard Pump, and AID users have fundamentally different safety profiles. The app must never give identical advice across therapy types. AID users need less interference (their pump auto-adjusts). MDI users need more proactive carb suggestions (they can't suspend basal). Every new feature should ask: "does this behave differently per therapy type?"

## Thesis Context

This app implements guidelines from:

- **T1DEXIP study** — sport-specific insulin reduction percentages (Aerobic/Mixed/Anaerobic × intensity × duration)
- **ISPAD guidelines** — general T1D management framework

Key academic features to highlight:

- 4-block time-segmented ICR/ISF
- Sport type × intensity × duration matrix
- CGM trend velocity modifier
- Therapy-aware algorithm (MDI vs Pump vs AID behave differently)
- Late-onset hypoglycemia prediction (7–11h window)
- IOB decay model with CGM integration
- Passive activity context (Health Connect steps/workouts)

## Future Direction

- **Proactive notifications:** time-sensitive alerts (pre-sport, post-sport hypo window, missed basal, trending low) without requiring the user to open the app
- **Pattern learning:** detecting recurring hypo/hyper patterns at specific times or after specific activities
- **Personalized thresholds:** adapting safety margins based on the user's own history rather than population averages
- **Richer passive context:** weather/temperature data, sleep quality, menstrual cycle tracking — all factors that affect insulin sensitivity
- **Clinician view:** exportable reports that show the algorithm's decision history for doctor appointments
