package com.pesaflow.app.data.meals

// Campus guide: where to eat near your school (what plate, what price) and
// what student housing runs nearby. verified = confirmed by a real report
// (user examples, 2026 housing/fare press); unmarked bands are typical
// student ranges - every inserted row stays editable, so the crowd corrects
// the guide. Pure data, zero Android deps.
data class CampusSpot(
    val university: String,
    val spot: String,
    val item: String,
    val price: Double,
    val mealType: String,
    val component: String,
    val verified: Boolean = false
)

data class CampusRent(
    val university: String,
    val hint: String,
    val verified: Boolean = false
)

// Daily-allowance tiers: what kind of eating day the food budget buys.
// Pure, unit-tested; the planner shows the tier next to the daily figure.
enum class MealTier { COMFORT, BALANCED, STRETCH }

fun mealTier(dailyAllowance: Double): MealTier = when {
    dailyAllowance >= 400 -> MealTier.COMFORT
    dailyAllowance >= 150 -> MealTier.BALANCED
    else -> MealTier.STRETCH
}

fun mealTierLabel(tier: MealTier): String = when (tier) {
    MealTier.COMFORT -> "Comfort plates 🍛 — balanced picks all day"
    MealTier.BALANCED -> "Balanced plates 🍲 — kibanda-smart all day"
    MealTier.STRETCH -> "Stretch + kitchen stock 🫙 — staples carry you"
}

private fun plates(uni: String, spot: String) = listOf(
    CampusSpot(uni, spot, "Smocha", 70.0, "Lunch", "Complete"),
    CampusSpot(uni, spot, "Githeri", 50.0, "Lunch", "Complete"),
    CampusSpot(uni, spot, "Ugali + sukuma", 60.0, "Supper", "Complete"),
    CampusSpot(uni, spot, "Chai + mandazi", 40.0, "Breakfast", "Complete")
)

private val GUIDE: List<CampusSpot> = listOf(
    // UoN Main (Club 36 smocha confirmed by user report)
    CampusSpot("UoN", "Club 36", "Smocha", 70.0, "Lunch", "Complete", verified = true),
    CampusSpot("UoN", "Club 36", "Githeri", 50.0, "Lunch", "Complete"),
    CampusSpot("UoN", "Club 36", "Chai + mandazi", 40.0, "Breakfast", "Complete"),
    CampusSpot("UoN", "Mama Njoroge", "Ugali + sukuma + omena", 80.0, "Supper", "Complete"),
    // KU Kahawa
    CampusSpot("KU", "Main Gate kibanda", "Smocha", 70.0, "Lunch", "Complete"),
    CampusSpot("KU", "Main Gate kibanda", "Pilau", 100.0, "Lunch", "Complete"),
    CampusSpot("KU", "Main Gate kibanda", "Githeri", 60.0, "Supper", "Complete"),
    CampusSpot("KU", "Main Gate kibanda", "Chai + mandazi", 50.0, "Breakfast", "Complete"),
    // JKUAT Juja
    CampusSpot("JKUAT", "Juja kibanda", "Smocha", 70.0, "Lunch", "Complete"),
    CampusSpot("JKUAT", "Juja kibanda", "Ugali + omena", 80.0, "Supper", "Complete"),
    CampusSpot("JKUAT", "Juja kibanda", "Chapati + ndengu", 60.0, "Lunch", "Complete"),
    CampusSpot("JKUAT", "Juja kibanda", "Githeri", 50.0, "Supper", "Complete"),
    // Maseno
    CampusSpot("Maseno", "Maseno Town kibanda", "Githeri", 50.0, "Lunch", "Complete"),
    CampusSpot("Maseno", "Maseno Town kibanda", "Smocha", 70.0, "Lunch", "Complete"),
    CampusSpot("Maseno", "Maseno Town kibanda", "Ugali + sukuma", 60.0, "Supper", "Complete"),
    CampusSpot("Maseno", "Maseno Town kibanda", "Chai + mandazi", 40.0, "Breakfast", "Complete"),
    // Egerton Njoro
    CampusSpot("Egerton", "Njoro kibanda", "Githeri", 50.0, "Lunch", "Complete"),
    CampusSpot("Egerton", "Njoro kibanda", "Chapati + ndengu", 60.0, "Lunch", "Complete"),
    CampusSpot("Egerton", "Njoro kibanda", "Smocha", 70.0, "Lunch", "Complete"),
    CampusSpot("Egerton", "Njoro kibanda", "Chai + mandazi", 40.0, "Breakfast", "Complete")
) + plates("Moi", "Kesses gate kibanda")
    .map { it.copy(university = "Moi") } +
    plates("MMUST", "Kakamega gate kibanda").map { it.copy(university = "MMUST") } +
    plates("Kisii", "Kisii town kibanda").map { it.copy(university = "Kisii") } +
    plates("Rongo", "Rongo town kibanda").map { it.copy(university = "Rongo") } +
    plates("Strathmore", "Madaraka kibanda").map { it.copy(university = "Strathmore") } +
    plates("Daystar", "Athi River kibanda").map { it.copy(university = "Daystar") } +
    plates("USIU", "Kasarani kibanda").map { it.copy(university = "USIU") } +
    plates("CUEA", "Langata kibanda").map { it.copy(university = "CUEA") } +
    plates("TUK", "Ngara kibanda").map { it.copy(university = "TUK") } +
    plates("Kabarak", "Nakuru kibanda").map { it.copy(university = "Kabarak") } +
    plates("MKU", "Thika Rd kibanda").map { it.copy(university = "MKU") } +
    plates("Zetech", "Ruiru kibanda").map { it.copy(university = "Zetech") } +
    plates("KCA", "Roysambu kibanda").map { it.copy(university = "KCA") } +
    plates("Multimedia", "Rongai kibanda").map { it.copy(university = "Multimedia") } +
    plates("Dedan Kimathi", "Nyeri kibanda").map { it.copy(university = "Dedan Kimathi") } +
    plates("Karatina", "Karatina town kibanda").map { it.copy(university = "Karatina") } +
    plates("Meru", "Meru town kibanda").map { it.copy(university = "Meru") } +
    plates("Embu", "Embu town kibanda").map { it.copy(university = "Embu") } +
    plates("Chuka", "Chuka town kibanda").map { it.copy(university = "Chuka") } +
    plates("Murang'a", "Murang'a town kibanda").map { it.copy(university = "Murang'a") } +
    plates("Laikipia", "Nyahururu kibanda").map { it.copy(university = "Laikipia") } +
    plates("Maasai Mara", "Narok kibanda").map { it.copy(university = "Maasai Mara") } +
    plates("Taita Taveta", "Voi kibanda").map { it.copy(university = "Taita Taveta") } +
    plates("Pwani", "Kilifi kibanda").map { it.copy(university = "Pwani") } +
    plates("TUM", "Mombasa kibanda").map { it.copy(university = "TUM") } +
    plates("Kibabii", "Bungoma kibanda").map { it.copy(university = "Kibabii") } +
    plates("Machakos", "Machakos town kibanda").map { it.copy(university = "Machakos") } +
    plates("Garissa", "Garissa town kibanda").map { it.copy(university = "Garissa") }

// Ordered: distinctive names first, bare city fallbacks last.
private val UNI_KEYS: List<Pair<String, Set<String>>> = listOf(
    "KU" to setOf("kenyatta", "ku", "kahawa"),
    "JKUAT" to setOf("jkuat", "jomo kenyatta", "juja"),
    "UoN" to setOf("uon", "university of nairobi", "nairobi"),
    "Maseno" to setOf("maseno"),
    "Egerton" to setOf("egerton", "njoro"),
    "Moi" to setOf("moi university", "moi", "eldoret", "kesses"),
    "MMUST" to setOf("mmust", "masinde", "muliro", "kakamega"),
    "Kisii" to setOf("kisii university", "kisii"),
    "Rongo" to setOf("rongo"),
    "Strathmore" to setOf("strathmore", "ole sangale"),
    "Daystar" to setOf("daystar", "athi river"),
    "USIU" to setOf("usiu", "kasarani"),
    "CUEA" to setOf("cuea", "catholic", "karen"),
    "TUK" to setOf("technical university of kenya", "tuk", "ngara", "shauri moyo"),
    "Kabarak" to setOf("kabarak"),
    "MKU" to setOf("mku", "mount kenya", "thika"),
    "Zetech" to setOf("zetech", "ruiru"),
    "KCA" to setOf("kca"),
    "Multimedia" to setOf("multimedia university", "multimedia", "rongai"),
    "Dedan Kimathi" to setOf("dedan", "kimathi", "nyeri"),
    "Karatina" to setOf("karatina"),
    "Meru" to setOf("meru university", "meru"),
    "Embu" to setOf("embu"),
    "Chuka" to setOf("chuka"),
    "Murang'a" to setOf("murang'a", "muranga"),
    "Laikipia" to setOf("laikipia", "nyahururu"),
    "Maasai Mara" to setOf("maasai", "mara", "narok"),
    "Taita Taveta" to setOf("taita", "taveta", "voi"),
    "Pwani" to setOf("pwani", "kilifi"),
    "TUM" to setOf("technical university of mombasa", "tum", "mombasa"),
    "Kibabii" to setOf("kibabii", "bungoma"),
    "Machakos" to setOf("machakos university", "machakos"),
    "Garissa" to setOf("garissa")
)

private val RENT_HINTS: List<CampusRent> = listOf(
    CampusRent("UoN", "Ngara/Pangani bedsitters ~6-15k/mo; on-campus single 21.5k/yr, double 15.5k/yr", verified = true),
    CampusRent("KU", "Kahawa bedsitters ~5-8k/mo", verified = true),
    CampusRent("JKUAT", "Juja bedsitters ~4-8k/mo; on-campus ~7.8k/yr", verified = true),
    CampusRent("Maseno", "Singles ~3k/mo, bedsitters ~4k, 1BR ~7.5k", verified = true),
    CampusRent("Egerton", "Njoro bedsitters ~3-6k/mo (typical)"),
    CampusRent("Moi", "Pioneer bedsitters ~4.5-7k/mo; on-campus 5-13k/yr", verified = true),
    CampusRent("MMUST", "Kakamega bedsitters ~5.5k/mo; on-campus 10-16k/yr", verified = true),
    CampusRent("Kisii", "Town bedsitters ~4-7k/mo (typical)"),
    CampusRent("Rongo", "Town bedsitters ~3-5k/mo (typical)"),
    CampusRent("Strathmore", "Madaraka hostels 13-50k/mo; South B bedsitters 8-15k", verified = true),
    CampusRent("Daystar", "Athi River ~6-10k/mo (typical); Valley Rd side 10-25k"),
    CampusRent("USIU", "Kasarani bedsitters ~5-8k/mo", verified = true),
    CampusRent("CUEA", "Langata/Rongai ~7-15k/mo", verified = true),
    CampusRent("TUK", "Ngara/Shauri shared ~5-12k/mo", verified = true),
    CampusRent("Kabarak", "Double bed-only 18,750/sem; Nakuru town varies", verified = true),
    CampusRent("MKU", "Thika Rd corridor ~5-10k/mo", verified = true),
    CampusRent("Zetech", "Ruiru ~6-9k/mo", verified = true),
    CampusRent("KCA", "Roysambu ~7-10k/mo", verified = true),
    CampusRent("Multimedia", "Rongai ~7-12k/mo (typical)"),
    CampusRent("Dedan Kimathi", "Nyeri ~5-8k/mo (typical)"),
    CampusRent("Karatina", "Town ~4-6k/mo (typical)"),
    CampusRent("Meru", "Meru town ~4-7k/mo (typical)"),
    CampusRent("Embu", "Embu town ~4-7k/mo (typical)"),
    CampusRent("Chuka", "Chuka town ~3-6k/mo (typical)"),
    CampusRent("Murang'a", "Murang'a town ~4-6k/mo (typical)"),
    CampusRent("Laikipia", "Nyahururu ~4-6k/mo (typical)"),
    CampusRent("Maasai Mara", "Narok ~4-7k/mo (typical)"),
    CampusRent("Taita Taveta", "Voi ~4-6k/mo (typical)"),
    CampusRent("Pwani", "Kilifi ~4-7k/mo (typical)"),
    CampusRent("TUM", "Mombasa ~6-12k/mo (typical)"),
    CampusRent("Kibabii", "Bungoma ~3-6k/mo (typical)"),
    CampusRent("Machakos", "Machakos town ~5-8k/mo (typical)"),
    CampusRent("Garissa", "Garissa town ~4-6k/mo (typical)")
)

private fun matchUni(universityName: String): String? {
    val low = universityName.trim().lowercase()
    if (low.isEmpty()) return null
    val tokens = low.split(Regex("[^a-z']+")).toSet()
    return UNI_KEYS.firstOrNull { (_, keys) ->
        keys.any { k -> if (' ' in k) k in low else k in tokens }
    }?.first
}

// Matches the onboarding university text ("UoN Main", "Kenyatta", "Moi Eldoret"...).
// Kenyatta checks before Nairobi to avoid city-name collisions.
fun spotsFor(universityName: String): List<CampusSpot> {
    val key = matchUni(universityName) ?: return emptyList()
    return GUIDE.filter { it.university == key }
}

// Student housing bands near the school, for the rent box + planner context.
fun rentHintFor(universityName: String): String? {
    val key = matchUni(universityName) ?: return null
    return RENT_HINTS.firstOrNull { it.university == key }?.hint
}
