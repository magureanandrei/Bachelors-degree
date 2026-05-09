package com.example.diabetesapp.ui.screens

data class TopicDef(
    val title: String,
    val subtitle: String,
    val body: String,
    val learnMoreLabel: String,
    val learnMoreUrl: String
)

const val SECTION_1_HEADER = "Understanding Your Body"
const val SECTION_2_HEADER = "Exercise & Insulin"
const val SECTION_3_HEADER = "Using This App"

const val INTERACTIVE_TOPIC_TITLE = "How to Read the Algorithm Rationale"
const val INTERACTIVE_TOPIC_SUBTITLE = "Understanding what each suggestion means"
const val INTERACTIVE_SAMPLE_CONTEXT = "Sample: BG 180 mg/dL · 30g carbs · Aerobic Medium 60 min · IOB 1.5U · MDI"
const val INTERACTIVE_SAMPLE_NOTE =
    "This is a sample calculation for illustration only. " +
    "Your actual recommendations are based on your personal settings and real-time data."
const val INTERACTIVE_HINT_TEXT = "Tap any entry to learn what it means"

const val SAMPLE_ENTRY_MEAL_LABEL = "Meal Bolus"
const val SAMPLE_ENTRY_MEAL_DESC = "Meal: 3.0U (30g ÷ 10.0 ICR)."
const val SAMPLE_ENTRY_CORRECTION_LABEL = "Correction Bolus"
const val SAMPLE_ENTRY_CORRECTION_DESC = "Correction: +1.6U (BG 180 → 100 target, ISF 50.0)"
const val SAMPLE_ENTRY_SPORT_LABEL = "Sport Reduction"
const val SAMPLE_ENTRY_SPORT_DESC = "Aerobic: −50% meal. Meal component: −1.50U."
const val SAMPLE_ENTRY_IOB_LABEL = "Active Insulin (IOB)"
const val SAMPLE_ENTRY_IOB_DESC =
    "IOB of 1.5U partially offsets correction. " +
    "Correction reduced from 1.6U to 0.1U. Meal bolus of 1.5U unchanged."
const val SAMPLE_ENTRY_BASAL_LABEL = "Missing Basal Insulin"
const val SAMPLE_ENTRY_BASAL_DESC = "No basal insulin has been logged today."

val INTERACTIVE_ENTRY_EXPLANATIONS = listOf(
    "This is the insulin needed to cover the carbohydrates you entered. It's calculated as " +
        "carbs ÷ your ICR for this time of day. This component is never reduced by IOB — " +
        "it always covers your food in full.",
    "This is extra insulin to bring an elevated blood glucose back to your target. " +
        "Calculated as (current BG − target) ÷ ISF. This component CAN be reduced by " +
        "active insulin (IOB).",
    "Your planned exercise reduces the meal bolus to prevent hypoglycemia during activity. " +
        "The percentage depends on exercise type, intensity, and duration per the " +
        "Riddell 2017 consensus guidelines.",
    "Insulin from previous doses is still active. The app subtracts it from your correction " +
        "(not your meal) to prevent stacking. Your food always gets full coverage.",
    "For MDI users, the app checks whether long-acting insulin was logged today. " +
        "Missing basal insulin can cause persistent hyperglycemia and ketone risk."
)

private val dawnBody = """
The dawn phenomenon is a predictable rise in blood glucose that occurs in the early morning hours, typically between 4:00 and 8:00 AM. It affects the majority of people with Type 1 Diabetes (Perriello et al., 1991).

The cause is hormonal. During the early morning, your body releases cortisol and growth hormone to prepare for the day. In people without diabetes, the pancreas automatically releases extra insulin to counteract this rise. In T1D, this compensation doesn't happen automatically — leaving glucose elevated before breakfast even without eating anything.

This is why your ICR and ISF settings may need to be higher in the morning than at other times of day (Riddell et al., 2017). The app's time-segmented profile system allows you to configure different ratios for each hour of the day to account for this.

For AID users: your pump's algorithm actively adapts to the dawn phenomenon by increasing micro-corrections overnight. You may still notice slightly elevated morning readings — this is normal and manageable.
""".trimIndent()

private val insulinResistanceBody = """
Two numbers drive almost every insulin decision your app makes:

Insulin-to-Carbohydrate Ratio (ICR)
Your ICR tells you how many grams of carbohydrate one unit of insulin covers. An ICR of 10 means 1 unit covers 10 grams of carbs. A higher ICR means you need less insulin per gram — you are more insulin sensitive. A lower ICR means you need more — you are more insulin resistant (Walsh, 2012).

Insulin Sensitivity Factor (ISF)
Your ISF tells you how much one unit of insulin lowers your blood glucose. An ISF of 50 mg/dL means one unit drops your glucose by 50 points. Like ICR, your ISF changes throughout the day and with exercise, illness, and stress.

Both values are not fixed — they vary with time of day, hormonal state, exercise, and illness. This is why the app uses time-segmented profiles and contextual modifiers rather than a single number (Riddell et al., 2017).

Insulin resistance means your body responds less effectively to insulin — you need more units to achieve the same glucose-lowering effect. It can be caused temporarily by illness, stress, high-fat meals, or hormonal changes, or chronically by factors like weight and disease duration.
""".trimIndent()

private val stressDepressionBody = """
Psychological stress directly affects blood glucose — not just through behavior, but through biology.

When you experience acute stress, your body releases cortisol and adrenaline. These hormones trigger the liver to release stored glucose and simultaneously reduce insulin sensitivity, causing blood glucose to rise. In people without diabetes, extra insulin is automatically released. In T1D, this doesn't happen — leaving stress-induced glucose rises uncorrected (Lloyd & Orchard, 1999).

Chronic stress and depression have a longer-term effect. Clinical evidence shows that depression measurably impairs insulin sensitivity over time, with improvements in glucose control observed when depression is successfully treated (Diep et al., 2012). This means periods of low mood or burnout can require higher insulin doses even without other obvious causes.

The app accounts for acute stress through the Stress modifier in the context factors panel (+15% dose adjustment). Chronic stress or depression cannot be algorithmically distinguished and should be discussed with your care team — they may recommend temporary ISF or ICR adjustments.
""".trimIndent()

private val iobStackingBody = """
Insulin on Board (IOB) is the amount of insulin still active in your body from previous doses. Rapid-acting insulin doesn't work instantly and then stop — it peaks around 60–90 minutes after injection and continues acting for 3–5 hours (Mudaliar et al., 1999).

Insulin stacking happens when you give a correction dose before the previous one has finished working. If you correct at BG 180, then correct again 30 minutes later because your glucose hasn't dropped yet — you may be stacking two doses that will both act simultaneously, causing a dangerous low (Walsh, 2012).

This is why the app subtracts your active IOB from correction suggestions but never from meal boluses. Your previous insulin may still be correcting your glucose — it just hasn't finished yet. Your meal insulin is always calculated in full because the food you are eating requires coverage regardless of prior doses.

The IOB shown on the home screen is calculated from your logged doses and, for CGM users, from your pump's reported active insulin via xDrip+.
""".trimIndent()

private val aerobicAnaerobicBody = """
Exercise is not a single glycemic event — the type of exercise you do produces fundamentally different blood glucose responses.

Aerobic exercise (running, cycling, swimming, continuous cardio) consistently lowers blood glucose during and after activity. Contracting muscles take up glucose through insulin-independent mechanisms, driving glucose down rapidly. This presents the highest acute risk of hypoglycemia during exercise (Zivkovic et al., 2026).

Anaerobic exercise (heavy lifting, sprinting, HIIT) triggers the release of stress hormones — adrenaline and cortisol — which cause the liver to release stored glucose faster than the muscles can consume it. Blood glucose often rises during anaerobic exercise, sometimes significantly. Without a functioning pancreas to auto-correct, this post-exercise hyperglycemia can persist for hours (Marliss & Vranic, 2002).

Mixed exercise (circuit training, team sports, moderate gym sessions) combines both effects — aerobic periods lower glucose while anaerobic bursts spike it. The net effect is highly variable and patient-specific (Zaharieva & Riddell, 2017).

A note on anaerobic variability: True anaerobic exercise — very heavy loads, long rest periods between sets — is rarer than people think. If your training includes moderate weights with shorter rests and some cardio elements, Mixed is often a more accurate classification than Anaerobic.
""".trimIndent()

private val walkingBody = """
Walking occupies a unique position among exercise types. Unlike structured aerobic or anaerobic exercise, walking produces moderate, sustained glucose reductions without the aggressive acute drops associated with running or cycling (Zivkovic et al., 2026). This makes it the lowest-risk exercise modality overall — but it still requires careful management.

How to gauge intensity:

• Low: Leisurely pace, easy conversation, flat terrain. Typical cadence < 90 steps/min.
• Medium: Purposeful walking, slightly elevated breathing, mild incline. ~90–110 steps/min.
• High: Brisk walking, hilly terrain, weighted pack, or extended duration (>60 min). >110 steps/min.

Duration matters more for walking than for other exercise types. A 20-minute walk has minimal glycemic impact. A 60–90 minute walk, especially at moderate cadence, can have a significant and prolonged glucose-lowering effect that persists for several hours post-walk.

Step count as a guide: If you tracked your walk in the app via Health Connect, step count and cadence are automatically captured. A 45-minute walk at >100 steps/min is functionally similar to a low-intensity aerobic session in terms of glycemic impact.

The app automatically detects walks from Health Connect data (minimum 7 minutes, minimum 55 steps/minute average) and logs them as SPORT events for IOB and post-exercise awareness.
""".trimIndent()

private val delayedHypoBody = """
Exercise doesn't end its glycemic effects when you stop moving. The risk of hypoglycemia follows a biphasic pattern — an immediate acute risk during exercise, and a delayed secondary risk window 7 to 11 hours later (McMahon et al., 2007).

Why does this happen? During and after exercise, your muscles replenish their glycogen stores using glucose from the bloodstream. This process continues for up to 24–48 hours, increasing your whole-body insulin sensitivity well beyond the exercise session itself (Richter & Hargreaves, 2013).

The nocturnal risk: Large-scale CGM analysis confirms a clear secondary band of hypoglycemia risk during nighttime hours (00:00–06:00), regardless of whether exercise was performed in the morning or afternoon (Zivkovic et al., 2026). This means an afternoon workout can cause a 2 AM low.

What to do:

• MDI: Reduce your evening basal dose by ~20% and consider a bedtime snack with protein and complex carbohydrates (ISPAD, 2022).
• Pump Standard: Set a temporary basal reduction of 20% for 6 hours at bedtime.
• AID: Keep your pump's sleep or activity target active. Monitor overnight CGM closely.

The app will alert you when you are in the 7–11 hour post-exercise window.
""".trimIndent()

private val ketoneBody = """
Ketones are produced when your body burns fat for energy instead of glucose — which happens when insulin levels are too low. In T1D, elevated ketones can progress to Diabetic Ketoacidosis (DKA), a life-threatening emergency (ISPAD, 2022).

When to check ketones:

• BG consistently above 250–300 mg/dL
• Nausea, vomiting, or abdominal pain
• Before exercise when BG > 250 mg/dL
• During illness
• If your pump infusion set may have failed

What the numbers mean:
< 0.6 mmol/L — Normal. No action needed.
0.6–1.4 mmol/L — Moderate. Postpone exercise. Administer corrective insulin. Recheck in 1–2 hours. Contact care team if not improving.
≥ 1.5 mmol/L — High. Exercise is strictly contraindicated. Administer insulin immediately. Seek medical attention if vomiting or unable to keep fluids down (ISPAD, 2022).

Exercise and ketones: The app will warn you when BG is above 250 mg/dL before exercise. At these levels, if you have any symptoms of DKA or have not taken insulin recently, check ketones before proceeding. Exercise with high ketones can worsen DKA rapidly.

Note: The app does not have access to ketone readings. This guidance is for self-management reference only. Always follow your care team's protocols.
""".trimIndent()

val SECTION_1_TOPICS = listOf(
    TopicDef(
        title = "Dawn Phenomenon",
        subtitle = "Why insulin needs are higher in the morning",
        body = dawnBody,
        learnMoreLabel = "Perriello et al. (1991)",
        learnMoreUrl = "https://link.springer.com/article/10.1007/BF00404020"
    ),
    TopicDef(
        title = "Insulin Resistance Basics",
        subtitle = "Understanding ICR, ISF, and how insulin works",
        body = insulinResistanceBody,
        learnMoreLabel = "Riddell et al. (2017)",
        learnMoreUrl = "https://scholar.google.com/scholar?hl=en&as_sdt=0%2C5&q=Exercise+management+in+type+1+diabetes+Riddell+2017&btnG="
    ),
    TopicDef(
        title = "Stress, Depression & Insulin Resistance",
        subtitle = "How mental state affects your glucose",
        body = stressDepressionBody,
        learnMoreLabel = "Lloyd & Orchard (1999)",
        learnMoreUrl = "https://onlinelibrary.wiley.com/doi/pdf/10.1002/dmrr.394?casa_token=N0EpP0LwINIAAAAA:5xf3Dni0-as8D-7zee-Z4xuBV3itXTaPwKfaEC6r2wyy8F7qzhbrC3slQ2GnHJx5chcykYMhhkYUin-2xA"
    ),
    TopicDef(
        title = "IOB & Insulin Stacking",
        subtitle = "Why you can't just keep correcting",
        body = iobStackingBody,
        learnMoreLabel = "Mudaliar et al. 1999",
        learnMoreUrl = "https://diabetesjournals.org/care/article-pdf/22/9/1501/450683/10480516.pdf?casa_token=kN3fWeOcivoAAAAA:wOsSPrz7JN9BRM5ZULJQlqVeFNsJHoqD04QQ4nGmYQNL_HzH5aITFnb09iO__hIlPFNfqZ_3wCI"
    )
)

val SECTION_2_TOPICS = listOf(
    TopicDef(
        title = "Aerobic vs Anaerobic Exercise",
        subtitle = "Why the same effort can have opposite effects",
        body = aerobicAnaerobicBody,
        learnMoreLabel = "Zivkovic et al. (2026)",
        learnMoreUrl = "https://link.springer.com/article/10.1007/s00125-026-06672-y"
    ),
    TopicDef(
        title = "Walking: Effort, Cadence & Duration",
        subtitle = "How to classify your walks correctly",
        body = walkingBody,
        learnMoreLabel = "Zivkovic et al. (2026)",
        learnMoreUrl = "https://link.springer.com/article/10.1007/s00125-026-06672-y"
    ),
    TopicDef(
        title = "The Delayed Hypoglycemia Window",
        subtitle = "Why lows happen hours after exercise ends",
        body = delayedHypoBody,
        learnMoreLabel = "McMahon et al. (2007)",
        learnMoreUrl = "https://academic.oup.com/jcem/article-pdf/92/3/963/9053887/jcem0963.pdf"
    )
)

val SECTION_3_TOPICS = listOf(
    TopicDef(
        title = "Ketone Management Guide",
        subtitle = "When to check, what the numbers mean",
        body = ketoneBody,
        learnMoreLabel = "ISPAD Guidelines (2022)",
        learnMoreUrl = "https://endoped.ro/wp-content/uploads/2023/09/Cap_2_Stages-of-diabetes.pdf"
    )
)
