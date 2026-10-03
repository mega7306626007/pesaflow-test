package com.pesaflow.app.data.parsers

import android.content.Context
import com.pesaflow.app.data.models.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern


object MpesaParser {


    private val p2pRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*You\\s+have\\s+sent\\s+KSh\\s*([0-9,.]+)\\s+to\\s+([^.]+?)\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val merchantRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*KSh\\s*([0-9,.]+)\\s+paid\\s+to\\s+([^.]+?)\\.\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val receiveRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*You\\s+have\\s+received\\s+KSh\\s*([0-9,.]+)\\s+from\\s+([^.]+?)\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val airtimeRegex = Pattern.compile(
        "(?i)(?:([A-Z0-9]{8,12})\\s+)?Confirmed\\.\\s*You\\s+bought\\s+KSh\\s*([0-9,.]+)\\s+of\\s+Airtime\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val withdrawRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.(?:\\s*on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+))?\\s*Withdraw\\s+KSh\\s*([0-9,.]+)\\s+from\\s+(.+?)(?:\\s+New\\s+(?:M-?PESA|Account)\\s+balance|\\s+M-?PESA\\s+balance|\\s+M-Shwari\\s+balance|\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)|$)"
    )
    private val paybillRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*KSh\\s*([0-9,.]+)\\s+sent\\s+to\\s+([^.]+?)(?:\\s+for\\s+account\\s+([0-9A-Za-z-]+))?\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    // Date-first person send ("Ksh2,100.00 sent to BRIAN MBUGUA 0723447655 on
    // 17/9/13 at 3:16 PM") — ahead of paybill, whose account group is optional
    // and would otherwise swallow person sends. The phone is dropped, not filed.
    private val personSendRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*KSh\\s*([0-9,.]+)\\s+sent\\s+to\\s+([A-Za-z' .]+?)\\s+(\\d{10,13})\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val tillRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*KSh\\s*([0-9,.]+)\\s+paid\\s+to\\s+(?:Till\\s+)?([0-9]{5,9})\\s*-?\\s*([^.]*?)\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val giftedAirtimeRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*You\\s+have\\s+sent\\s+KSh\\s*([0-9,.]+)\\s+worth\\s+of\\s+airtime\\s+to\\s+([0-9+]+)\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val bundleRegex = Pattern.compile(
        "(?i)Confirmed\\.[^.]{0,80}?(?:bought|purchased)[^.]{0,80}?bundles?[^.]{0,80}?KSh\\s*([0-9,.]+)"
    )
    // Amount rides either side of the verb — "transferred KSh X" and the
    // equally real "KSh X transferred". Branches read group(2) ?: group(3).
    // Date/time groups ride the tail ("... to M-Shwari account on d/M/yy at h:mm AM").
    private val mshwariRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.[^.]{0,80}?(?:transferred\\s+KSh\\s*([0-9,.]+)|KSh\\s*([0-9,.]+)\\s+transferred)(?:\\s+from\\s+(?:your\\s+)?M-?PESA\\s+)?\\s*to\\s+M-?SHWARI(?:\\s+account)?\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val bankInRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.[^.]{0,80}?(?:transferred\\s+KSh\\s*([0-9,.]+)|KSh\\s*([0-9,.]+)\\s+transferred)\\s+from\\s+((?!M-?SHWARI)[A-Za-z\\- ]+?)\\s+to\\s+M-?PESA"
    )
    private val bankOutRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.[^.]{0,80}?(?:transferred\\s+KSh\\s*([0-9,.]+)|KSh\\s*([0-9,.]+)\\s+transferred\\s+from\\s+M-?PESA)\\s+(?:from\\s+M-?PESA\\s+)?to\\s+(KCB|EQUITY|CO-?OP|STANBIC|ABSA|FAMILY|DTB|NCBA)"
    )
    private val fulizaRegex = Pattern.compile(
        "([A-Z0-9]{8,12})\\s*Confirmed\\.[^.]{0,80}?borrowed\\s+KSh\\s*([0-9,.]+)",
        Pattern.CASE_INSENSITIVE
    )
    private val ziidiActionAmountRegex = Pattern.compile(
        "(?i)(?:confirmed[.!]?\\s*)?(?:you\\s+have\\s+)?(?:successfully\\s+)?(?:transferred|sent|invested|deposited|withdrawn|redeemed|received|moved)\\b[^.]{0,60}?(?:KSh|KES)\\s*([0-9,.]+)"
    )
    private val ziidiAmountActionRegex = Pattern.compile(
        "(?i)(?:KSh|KES)\\s*([0-9,.]+)[^.]{0,60}?(?:transferred|sent|invested|deposited|withdrawn|redeemed|received|moved)\\b"
    )
    // Fuliza limit/balance notices ("your Fuliza balance is KSh 200") — money you
    // could touch, still a loan. Parsed so the user gets asked, never auto-spent.
    private val fulizaBalanceRegex = Pattern.compile(
        "(?i)fuliza[^.]{0,80}?(?:limit|balance|available|loan)[^.]{0,60}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
    )
    // Bank SMS (sender is the bank, not M-Pesa): require an explicit bank name
    // plus a credit/debit verb plus an amount — never amount-alone.
    private val bankCreditRegex = Pattern.compile(
        "(?i)(KCB|EQUITY|CO-?OP(?:ERATIVE)?|ABSA|STANBIC|FAMILY|DTB|NCBA|I&M|STANCHART|BANK).{0,120}?(?:(credited|deposited|paid in|payment received|received).{0,60}?KSh\\s*([0-9,.]+)|KSh\\s*([0-9,.]+).{0,60}?(credited|deposited|paid in|payment received|received))"
    )
    private val bankDebitRegex = Pattern.compile(
        "(?i)(KCB|EQUITY|CO-?OP(?:ERATIVE)?|ABSA|STANBIC|FAMILY|DTB|NCBA|I&M|STANCHART|BANK).{0,120}?(?:(debited|withdrawn|paid out|charged|deducted|purchase).{0,60}?KSh\\s*([0-9,.]+)|KSh\\s*([0-9,.]+).{0,60}?(debited|withdrawn|paid out|charged|deducted|purchase))"
    )
    // HELB upkeep/disbursement mentions outside the standard M-Pesa receive shape.
    private val helbRegex = Pattern.compile(
        "(?i)HELB[^.]{0,80}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
    )
    // Okoa Jahazi / emergency airtime advances (debt-like, tracked as Airtime).
    // KSh marker often missing ("Okoa 50!") and USSD codes (*456*9#) must not
    // parse as amounts — guarded both sides of the digits.
    private val okoaRegex = Pattern.compile(
        "(?i)(?:okoa(?: jahazi)?|emergency airtime)[^.]{0,60}?(?:(?:KSh|KES|Ksh)\\s*)?(?<![*#0-9,.])([0-9,.]+)(?!#)\\s*(?:bob)?"
    )
    // Agent deposit: cash → M-Pesa (money entering the tracked wallet).
    // Tolerates the date-first ordering ("Confirmed. on d/M/yy at h:mm AM Give ...").
    private val depositRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.[^.]{0,60}?deposited\\s+(?:KSh|KES|Ksh)\\s*([0-9,.]+)\\s+to\\s+([^.]+?)\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    // M-Shwari back to wallet: internal move, spendable later. Tolerates
    // "from your M-Shwari account" and captures the tail date/time.
    private val mshwariOutRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.[^.]{0,80}?(?:transferred\\s+KSh\\s*([0-9,.]+)|KSh\\s*([0-9,.]+)\\s+transferred)\\s+from\\s+(?:your\\s+)?M-?SHWARI(?:\\s+account)?(?:\\s+to\\s+M-?PESA)?\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    // SACCO deposits (Stima, Unaitas, Mwalimu, police, harambee): real savings.
    private val saccoRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.[^.]{0,80}?(?:(?:deposited|paid|sent)\\s+KSh\\s*([0-9,.]+)|KSh\\s*([0-9,.]+)\\s+(?:deposited|paid|sent))[^.]{0,60}?(sacco|stima\\s*sacco|unaitas|mwalimu|police\\s*sacco|harambee\\s*sacco)"
    )
    // Digital lenders: money in (disbursed) vs money out (repayment).
    private val loanInRegex = Pattern.compile(
        "(?i)(?:loan|tala|branch|zenka|m-?kopa|timiza|kashwaya|opesa)[^.]{0,80}?(?:(?:approved|disbursed|disbursement|advanced|credited)[^.]{0,60}?KSh\\s*([0-9,.]+)|KSh\\s*([0-9,.]+)[^.]{0,60}?(?:approved|disbursed|disbursement|advanced|credited))"
    )
    private val loanOutRegex = Pattern.compile(
        "(?i)(?:(repayment|repaid|loan\\s+payment|loan\\s+repaid)[^.]{0,60}?KSh\\s*([0-9,.]+)|KSh\\s*([0-9,.]+)[^.]{0,60}?(repayment|repaid|loan\\s+payment|loan\\s+repaid))"
    )
    // Pochi La Biashara wallet moves — transfers, never spending.
    private val pochiSendRegex = Pattern.compile("(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*KSh\\s*([0-9,.]+)\\s+sent\\s+to\\s+Pochi La Biashara[^.]{0,30}?\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)")
    private val pochiReceiveRegex = Pattern.compile("(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*You\\s+have\\s+received\\s+KSh\\s*([0-9,.]+)\\s+to\\s+Pochi La Biashara\\s+from\\s+([^.]+?)\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)")
    private val pochiWithdrawRegex = Pattern.compile("(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*You\\s+have\\s+withdrawn\\s+KSh\\s*([0-9,.]+)\\s+from\\s+Pochi La Biashara[^.]{0,30}?\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)")
    // Hustler Fund borrow/repay/save.
    private val hustlerBorrowRegex = Pattern.compile("(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*You\\s+have\\s+borrowed\\s+KSh\\s*([0-9,.]+)\\s+from\\s+Hustler Fund\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)")
    private val hustlerRepayRegex = Pattern.compile("(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*Hustler Fund repayment of KSh\\s*([0-9,.]+)\\s+received\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)")
    private val hustlerSaveRegex = Pattern.compile("(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*You\\s+have\\s+saved\\s+KSh\\s*([0-9,.]+)\\s+to\\s+Hustler Fund[^.]{0,30}?\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)")
    // Lipa Mdogo Mdogo device installments.
    private val lipaMdogoRegex = Pattern.compile("(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*KSh\\s*([0-9,.]+)\\s+paid\\s+to\\s+Lipa Mdogo Mdogo\\s+for\\s+([^.]+?)\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)")
    // Airtel Money (TID-based, no "Confirmed" — sender-agnostic, TID fingerprints).
    private val airtelSendRegex = Pattern.compile("(?i)You have sent KSh\\s*([0-9,.]+)\\s+to\\s+([A-Za-z' .]+?)\\s+(0\\d{9})\\.?\\s+TID:\\s*([A-Za-z0-9]+)")
    private val airtelReceiveRegex = Pattern.compile("(?i)You have received KSh\\s*([0-9,.]+)\\s+from\\s+([A-Za-z' .]+?)\\s+(0\\d{9})\\.?\\s+TID:\\s*([A-Za-z0-9]+)")
    private val airtelPaybillRegex = Pattern.compile("(?i)You have paid KSh\\s*([0-9,.]+)\\s+to\\s+([A-Za-z0-9' .&\\-]+?)\\s+(?:Paybill\\s+|Bill\\s+)?(\\d{5,7})\\b.*?TID:\\s*([A-Za-z0-9]+)")
    private val airtelWithdrawRegex = Pattern.compile("(?i)You have withdrawn KSh\\s*([0-9,.]+).*?Agent\\s*([A-Za-z0-9' .&\\-]*?)\\.?\\s*TID:\\s*([A-Za-z0-9]+)")
    private val airtelDepositRegex = Pattern.compile("(?i)You have deposited KSh\\s*([0-9,.]+).*?TID:\\s*([A-Za-z0-9]+)")
    private val airtelAirtimeRegex = Pattern.compile("(?i)You have bought.*?airtime.*?KSh\\s*([0-9,.]+).*?TID:\\s*([A-Za-z0-9]+)")
    private val airtelBundleRegex = Pattern.compile("(?i)You have bought (.+?) data bundle for KSh\\s*([0-9,.]+).*?TID:\\s*([A-Za-z0-9]+)")
    private val airtelDateRegex = Pattern.compile("(?i)Date:\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\s+(\\d{1,2}:\\d{2})")
    // Fuliza repaid: debt serviced, not new spending power.
    private val fulizaRepayRegex = Pattern.compile(
        "(?i)fuliza[^.]{0,60}?(?:(?:repaid|repayment|paid\\s+back|recovered)[^.]{0,60}?KSh\\s*([0-9,.]+)|KSh\\s*([0-9,.]+)[^.]{0,60}?(?:repaid|repayment|paid\\s+back|recovered))"
    )
    // Cashback rewards and savings interest: small, real income.
    private val cashbackRegex = Pattern.compile(
        "(?i)cash\\s*-?\\s*back[^.]{0,60}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
    )
    private val interestRegex = Pattern.compile(
        "(?i)interest[^.]{0,60}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
    )
    // Generic verb-typed catcher: any other "Confirmed ... KSh X" shape.
    // Verbs decide the type; party = text after the amount. Low confidence,
    // human-reviewed — breadth without pretending precision.
    private val genericMoveRegex = Pattern.compile(
        "(?i)Confirmed\\.?[^.]{0,120}?KSh\\s*([0-9,.]+)([^.]{0,80})"
    )
    // Other telcos (Airtel/Telkom/Equitel): no Confirmed header, "successfully" shape.
    private val telcoSentRegex = Pattern.compile(
        "(?i)(?:successfully\\s+)?(?:sent\\s+(?:KSh|KES|Ksh)\\s*([0-9,.]+)|(?:KSh|KES|Ksh)\\s*([0-9,.]+)\\s+sent)\\s+to\\s+([0-9+\\s]{7,15}|[^.,]+?)(?:\\s+on\\s+|\\s*,|\\s*\\.|$)"
    )
    private val telcoReceivedRegex = Pattern.compile(
        "(?i)(?:successfully\\s+)?(?:(?:received|credited)[^.]{0,40}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)|(?:KSh|KES|Ksh)\\s*([0-9,.]+)[^.]{0,40}?(?:received|credited))(?:\\s+[A-Za-z]+)?\\s+from\\s+([0-9+\\s]{7,15}|[^.,]+?)(?:\\s+on\\s+|\\s+at\\s+|\\s*,|\\s*\\.|$)"
    )
    // "purchased KSh20 airtime" alongside the classic "bought KSh X of Airtime".
    private val airtimeBuyRegex = Pattern.compile(
        "(?i)(?:bought|purchased)\\s+(?:KSh|KES|Ksh)\\s*([0-9,.]+)[^.]{0,30}?(?:airtime|bundles|data)"
    )
    // Bank bodies that omit the bank name (the sender carries it — passed in).
    private val bankCreditBareRegex = Pattern.compile(
        "(?i)(?:(credited|deposited|paid in|payment received).{0,60}?KSh\\s*([0-9,.]+)|KSh\\s*([0-9,.]+).{0,60}?(credited|deposited|paid in|payment received))"
    )
    private val bankDebitBareRegex = Pattern.compile(
        "(?i)(?:(debited|withdrawn|withdrawal|paid out|charged|deducted|purchase of).{0,60}?KSh\\s*([0-9,.]+)|KSh\\s*([0-9,.]+).{0,60}?(debited|withdrawn|withdrawal|paid out|charged|deducted|purchase of))"
    )
    // Amount-then-confirmation receipts ("Payment of KSh X confirmed") — real
    // money that leads with the amount. Narrow nouns only, never bare amounts.
    private val amountFirstConfirmRegex = Pattern.compile(
        "(?i)(?:payment|transaction|transfer)\\s+of\\s+KSh\\s*([0-9,.]+)[^.]{0,40}?(?:confirmed|successful|completed)"
    )
    // Swahili confirmations ("Umetuma KSh X kwa NAME", "Umepokea X kutoka kwa NAME").
    private val swahiliSentRegex = Pattern.compile(
        "(?i)umetuma\\s+KSh\\s*([0-9,.]+)\\s+kwa\\s+([^.,;]+?)(?:\\s+tarehe|\\s+saa|\\s+on\\s+|\\s*,|\\s*\\.|$)"
    )
    private val swahiliReceivedRegex = Pattern.compile(
        "(?i)umepokea\\s+KSh\\s*([0-9,.]+)\\s+kutoka\\s+kwa\\s+([^.,;]+?)(?:\\s+tarehe|\\s+saa|\\s+on\\s+|\\s*,|\\s*\\.|$)"
    )
    // Header-less paid shapes, either order ("paid KSh X to Y", "KSh X paid to Y").
    private val telcoPaidRegex = Pattern.compile(
        "(?i)(?:paid\\s+KSh\\s*([0-9,.]+)|KSh\\s*([0-9,.]+)\\s+paid)\\s+to\\s+([^.,;]+?)(?:\\s+for\\s+account\\s+([0-9A-Za-z-]+))?(?:\\s+on\\s+|\\s*,|\\s*\\.|$)"
    )
    // Bundle bought amount-last ("purchased 1GB ... for KSh99", no Confirmed header).
    private val bundleLastRegex = Pattern.compile(
        "(?i)(?:bought|purchased)[^.]{0,40}?(\\d+(?:\\.\\d+)?\\s*(?:gb|mb))[^.]{0,40}?KSh\\s*([0-9,.]+)"
    )
    // Header-less SACCO confirmation ("STIMA SACCO: Deposit of KSh X confirmed").
    private val saccoBareRegex = Pattern.compile(
        "(?i)(sacco|stima\\s*sacco|unaitas|mwalimu|police\\s*sacco|harambee\\s*sacco)[^.]{0,80}?KSh\\s*([0-9,.]+)[^.]{0,40}?(?:confirmed|deposited|received|credited|paid)"
    )
    // Agent cash-in phrased as instruction ("Give KSh X cash to AGENT ... on ... at ...").
    // Date may lead ("Confirmed. on d/M/yy at h:mm AM Give ...") or trail the party.
    private val agentGiveRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.(?:\\s*on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+))?[^.]{0,60}?[Gg]ive\\s+KSh\\s*([0-9,.]+)\\s+cash\\s+to\\s+([^.]+?)(?:\\s+New\\s+(?:M-?PESA|Account)\\s+balance|\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)|$)"
    )
    private val BANK_SENDERS = listOf("KCB", "EQUITY", "CO-OP", "COOP", "ABSA", "STANBIC", "FAMILY", "DTB", "NCBA", "I&M", "STANCHART")

    // Senders worth opening even without a "Confirmed" header.
    // Official money senders ONLY: telcos, banks, HELB, SACCOs. Anything
    // else (phone numbers, contact names) is a person — a friend's
    // "nimetuma 500" or "Confirmed nitakutumia" is never money, no matter
    // how money-shaped the body looks.
    private val OFFICIAL_SENDER_KEYWORDS = listOf(
        "mpesa", "safaricom", "airtel", "telkom", "equitel", "t-kash",
        "kcb", "equity", "co-op", "coop", "absa", "stanbic", "family",
        "dtb", "ncba", "i&m", "stanchart",
        "helb", "sacco", "stima", "unaitas", "mwalimu", "harambee", "ziidi"
    )

    fun isOfficialSender(sender: String): Boolean {
        val s = sender.lowercase()
        return OFFICIAL_SENDER_KEYWORDS.any { s.contains(it) }
    }

    fun isTransactionalSender(sender: String): Boolean = isOfficialSender(sender)

    fun bankNameOfSender(sender: String): String? {
        val s = sender.uppercase()
        return BANK_SENDERS.firstOrNull { s.contains(it) }
    }
    private val fallbackRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})[^.]{0,60}?Confirmed\\.?[^.]{0,80}?KSh\\s*([0-9,.]+)"
    )
    private val bareReversalRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})[^.]{0,60}?(reversed|reversal)\\.?[^.]{0,80}?KSh\\s*([0-9,.]+)"
    )
    // Refund notices: amount rides BEFORE the currency ("of 10Ksh has been
    // refunded by ...") — the standard KSh-first shapes never match these.
    private val refundRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*confirmed\\.?[^.]{0,80}?(?:of\\s+)?([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(?:Ksh|KES|Ksh)[^.]{0,60}?refunded"
    )


    // Shared promo/ad markers (gate below + scan summaries): Safaricom-style
    // ads with no movement verb. Cashback/congratulations excluded on purpose —
    // real cashback earnings ("earned cashback of KSh45") must still parse.
    private val adMarkers = listOf("unbeatable", "special offer", "offer", "promo", "discount")

    // True for promo texts (ads, not money) — used to label scan leftovers
    // separately from genuinely unreadable texts.
    fun isPromoAd(smsBody: String): Boolean {
        val low = smsBody.lowercase()
        if (adMarkers.none { low.contains(it) }) return false
        return !(low.contains("paid") || low.contains("received") || low.contains("confirmed") ||
            low.contains("debited") || low.contains("credited") || low.contains("deposited"))
    }

    // Entry point: text dates win when sane; otherwise the carrier stamp
    // (inbox date on scans, SMSC time on live receive) beats a now-default.
    // No carrier stamp (manual paste, tests) means today's behaviour unchanged.
    fun parseMessage(smsBody: String, sender: String = "", fallbackTs: Long = Long.MIN_VALUE): PendingTransaction? {
        val carrierTs = fallbackTs.takeIf { it > 0 }
        val parsed = parseTransaction(smsBody, sender, carrierTs) ?: return null
        val now = System.currentTimeMillis()
        val freshDefault = kotlin.math.abs(parsed.dateTimestamp - now) < 120_000
        // Only rewinds fresh-defaulted rows (dateless or garbage dates) —
        // real text dates are days old and never satisfy freshDefault.
        return if (freshDefault && carrierTs != null && kotlin.math.abs(carrierTs - now) > 120_000) {
            parsed.copy(dateTimestamp = carrierTs)
        } else parsed
    }

    private fun parseTransaction(smsBody: String, sender: String, carrierTs: Long?): PendingTransaction? {
        // Spellings converge before matching: "KES"/"Kshs" become "KSh" so
        // every pattern below only needs one currency literal. Word-boundary
        // guarded — merchant names like "KESHAM" are untouched.
        val sanitized = smsBody.replace("\n", " ").trim()
            .replace(Regex("(?i)\\bkes\\b"), "KSh")
            .replace(Regex("(?i)\\bkshs\\b"), "KSh")
        val senderBank = bankNameOfSender(sender)

        // Sender authority gate: a non-blank unofficial sender (phone number,
        // contact name) is a person — never money, skip before shapes run.
        // Blank sender = manual/test/share flows, human-gated downstream.
        if (sender.isNotBlank() && !isOfficialSender(sender)) return null

        // Shadows the member below: every branch below calls THIS, gaining
        // harvested-date arbitration for free. Branch-captured `on…at…`
        // stamps keep full trust (restored-SMS backups rewrite inbox dates
        // but body dates stay true); harvested stamps are low-confidence, so
        // a carrier stamp that disagrees by 48h+ overrules them (due-dates
        // and wrong-year ghosts live in these bodies).
        fun buildPending(
            code: String?, amountStr: String?, party: String?,
            dateStr: String?, timeStr: String?, type: TransactionType, raw: String,
            confidence: Float = 0.95f,
            method: PaymentMethod = PaymentMethod.MPESA
        ): PendingTransaction? {
            val usedHarvest = dateStr == null || timeStr == null
            val p = buildPendingRow(code, amountStr, party, dateStr, timeStr, type, raw, confidence, method) ?: return null
            if (usedHarvest && carrierTs != null && carrierTs > 0 &&
                kotlin.math.abs(p.dateTimestamp - System.currentTimeMillis()) > 120_000 &&
                kotlin.math.abs(p.dateTimestamp - carrierTs) > HARVEST_AGREE_WINDOW_MS
            ) {
                return p.copy(dateTimestamp = carrierTs)
            }
            return p
        }

        // Failed/cancelled/insufficient texts move no money — never parse.
        val low0 = sanitized.lowercase()
        if ((low0.contains("failed") || low0.contains("unsuccessful") || low0.contains("cancelled") || low0.contains("canceled") || low0.contains("declined") || low0.contains("rejected") || low0.contains("insufficient") || low0.contains("timed out") || low0.contains("not completed") || low0.contains("bounced") || low0.contains("dishonoured") || low0.contains("dishonored")) && !low0.contains("revers")) return null
        // OTP / verification codes authorize future movement — never money.
        if (low0.contains("otp") || low0.contains("one-time pass") || low0.contains("one time pass") || low0.contains("verification code")) return null
        // Balance inquiries ("Your M-PESA balance was Ksh339.00") move no money —
        // only skip when no movement verb proves a transaction happened.
        val movementVerb = low0.contains("sent") || low0.contains("received") || low0.contains("paid") ||
            low0.contains("transferred") || low0.contains("withdraw") || low0.contains("deposited") ||
            low0.contains("bought") || low0.contains("credited") || low0.contains("debited")
        if (!movementVerb && (low0.contains("balance was") || low0.contains("balance request"))) return null
        // Prize scams ("won ... claim") mimic send shapes — quarantine outright.
        if ((low0.contains("won") || low0.contains("congratulations") || low0.contains("lucky winner")) && low0.contains("claim")) return null
        // Due/overdue REMINDERS aren't payments — only pass when a payment verb
        // proves money moved (paid, received, confirmed, debited...).
        val looksDue = low0.contains("due") || low0.contains("overdue") || low0.contains("reminder")
        if (looksDue && !(low0.contains("paid") || low0.contains("received") || low0.contains("confirmed") || low0.contains("debited") || low0.contains("deducted") || low0.contains("successful"))) return null
        // Loan/advance OFFERS and Safaricom PROMOS aren't money ("eligible",
        // "you qualify", "unbeatable offers") — skip unless a movement verb
        // proves it landed.
        val looksOffer = low0.contains("eligible") || low0.contains("pre-approved") || low0.contains("preapproved") || low0.contains("you qualify") || low0.contains("apply now") ||
            adMarkers.any { low0.contains(it) }
        if (looksOffer && !(low0.contains("paid") || low0.contains("received") || low0.contains("confirmed") || low0.contains("debited") || low0.contains("credited") || low0.contains("deposited"))) return null
        // Future payments aren't movements: bookings, reservations, pay-on-arrival
        // and pay-on-delivery quote amounts without moving them. Only past-tense
        // verbs (paid, received, repaid...) prove money moved — "confirmed" alone
        // doesn't, reservations get "confirmed" all the time.
        val looksFuture = low0.contains("booking") || low0.contains("reservation") || low0.contains("on arrival") || low0.contains("on delivery")
        if (looksFuture && !(low0.contains("paid") || low0.contains("received") || low0.contains("debited") || low0.contains("deducted") || low0.contains("repaid") || low0.contains("repayment"))) return null

        // Ziidi sends its own wallet notice as well as the M-Pesa confirmation.
        // Normalize both into one named savings movement so the scan/ledger can
        // recognize a mirrored notice without treating it as another purchase.
        if (low0.contains("ziidi") && listOf(
                "transferred", "sent", "invested", "deposited", "withdrawn",
                "redeemed", "received", "moved"
            ).any { low0.contains(it) }
        ) {
            val ziidiMatcher = ziidiActionAmountRegex.matcher(sanitized)
            val amount = if (ziidiMatcher.find()) ziidiMatcher.group(1) else {
                ziidiAmountActionRegex.matcher(sanitized).let { reverse ->
                    if (reverse.find()) reverse.group(1) else null
                }
            }
            if (amount != null) {
                val withdrawal = low0.contains("withdraw") || low0.contains("redeem") ||
                    (low0.contains("from ziidi") || low0.contains("ziidi to m-pesa"))
                val code = Regex("^\\s*([A-Z0-9]{8,12})\\s*Confirmed", RegexOption.IGNORE_CASE)
                    .find(sanitized)?.groupValues?.get(1)
                return buildPending(
                    code = code,
                    amountStr = amount,
                    party = "Ziidi",
                    dateStr = null,
                    timeStr = null,
                    type = if (withdrawal) TransactionType.INCOME else TransactionType.SAVING,
                    raw = sanitized,
                    confidence = 0.85f
                )?.copy(category = "Savings", subcategory = "Ziidi transfer", merchant = "Ziidi")
            }
        }
        
        // Match standard Sent Money
        var matcher = p2pRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = cleanParty(matcher.group(3)),
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.EXPENSE,
                raw = sanitized
            )
        }

        // Date-first person send — name only, phone dropped. Skips shapes the
        // paybill branch owns ("for account", paybill/till keywords).
        matcher = personSendRegex.matcher(sanitized)
        if (matcher.find()) {
            val name = matcher.group(3).trim()
            if (!name.contains("account", ignoreCase = true) &&
                !name.contains("paybill", ignoreCase = true) &&
                !name.contains("till", ignoreCase = true)
            ) {
                return buildPending(
                    code = matcher.group(1),
                    amountStr = matcher.group(2),
                    party = name,
                    dateStr = matcher.group(5),
                    timeStr = matcher.group(6),
                    type = TransactionType.EXPENSE,
                    raw = sanitized
                )
            }
        }


        // Pochi La Biashara wallet moves (ahead of merchant/paybill: "sent to
        // Pochi" wears paybill's shape but it is a wallet transfer, not spending).
        matcher = pochiSendRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "Pochi La Biashara",
                dateStr = matcher.group(3),
                timeStr = matcher.group(4),
                type = TransactionType.TRANSFER,
                raw = sanitized,
                confidence = 0.85f
            )?.copy(category = "Transfers")
        }
        matcher = pochiReceiveRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = cleanParty(matcher.group(3) ?: "Pochi customer"),
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.85f
            )?.copy(category = "Other")
        }
        matcher = pochiWithdrawRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "Pochi La Biashara",
                dateStr = matcher.group(3),
                timeStr = matcher.group(4),
                type = TransactionType.TRANSFER,
                raw = sanitized,
                confidence = 0.85f
            )?.copy(category = "Transfers")
        }
        // Hustler Fund (ahead of Fuliza: "borrowed KSh" wears its shape —
        // without this, hustler loans misfile as Fuliza).
        matcher = hustlerBorrowRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "Hustler Fund",
                dateStr = matcher.group(3),
                timeStr = matcher.group(4),
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.85f
            )?.copy(category = "Debt")
        }
        matcher = hustlerRepayRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "Hustler Fund",
                dateStr = matcher.group(3),
                timeStr = matcher.group(4),
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.85f
            )?.copy(category = "Debt")
        }
        matcher = hustlerSaveRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "Hustler Fund",
                dateStr = matcher.group(3),
                timeStr = matcher.group(4),
                type = TransactionType.SAVING,
                raw = sanitized,
                confidence = 0.85f
            )?.copy(category = "Savings")
        }
        // Lipa Mdogo Mdogo device installments ("paid to X for Y" has no dot —
        // the merchant pattern is blind to it).
        matcher = lipaMdogoRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "Lipa Mdogo Mdogo (" + (matcher.group(3) ?: "device").trim() + ")",
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.85f
            )?.copy(category = "Shopping")
        }


        // Airtel Money (TID fingerprint, "Balance:" tails, "Date: dd/MM/yyyy
        // HH:mm"). No "Confirmed" — nothing above can have claimed these.
        // Airtel is a separate wallet: method OTHER, never MPESA.
        fun airtelDate(): Pair<String?, String?> {
            val dm = airtelDateRegex.matcher(sanitized)
            return if (dm.find()) (dm.group(1) to dm.group(2)) else (null to null)
        }
        matcher = airtelSendRegex.matcher(sanitized)
        if (matcher.find()) {
            val (ad, at) = airtelDate()
            return buildPending(
                code = matcher.group(4),
                amountStr = matcher.group(1),
                party = (matcher.group(2) ?: "Airtel contact").trim(),
                dateStr = ad,
                timeStr = at,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.9f,
                method = PaymentMethod.OTHER
            )
        }
        matcher = airtelReceiveRegex.matcher(sanitized)
        if (matcher.find()) {
            val (ad, at) = airtelDate()
            return buildPending(
                code = matcher.group(4),
                amountStr = matcher.group(1),
                party = (matcher.group(2) ?: "Airtel contact").trim(),
                dateStr = ad,
                timeStr = at,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.9f,
                method = PaymentMethod.OTHER
            )
        }
        matcher = airtelPaybillRegex.matcher(sanitized)
        if (matcher.find()) {
            val (ad, at) = airtelDate()
            return buildPending(
                code = matcher.group(4),
                amountStr = matcher.group(1),
                party = (matcher.group(2) ?: "Airtel paybill").trim(),
                dateStr = ad,
                timeStr = at,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.85f,
                method = PaymentMethod.OTHER
            )
        }
        matcher = airtelWithdrawRegex.matcher(sanitized)
        if (matcher.find()) {
            val (ad, at) = airtelDate()
            return buildPending(
                code = matcher.group(3),
                amountStr = matcher.group(1),
                party = (matcher.group(2) ?: "Airtel agent").trim().ifEmpty { "Airtel agent" },
                dateStr = ad,
                timeStr = at,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.85f,
                method = PaymentMethod.OTHER
            )
        }
        matcher = airtelDepositRegex.matcher(sanitized)
        if (matcher.find()) {
            val (ad, at) = airtelDate()
            return buildPending(
                code = matcher.group(2),
                amountStr = matcher.group(1),
                party = "Airtel deposit",
                dateStr = ad,
                timeStr = at,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.85f,
                method = PaymentMethod.OTHER
            )
        }
        matcher = airtelAirtimeRegex.matcher(sanitized)
        if (matcher.find()) {
            val (ad, at) = airtelDate()
            return buildPending(
                code = matcher.group(2),
                amountStr = matcher.group(1),
                party = "Airtel",
                dateStr = ad,
                timeStr = at,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.85f,
                method = PaymentMethod.OTHER
            )?.copy(category = "Airtime")
        }
        matcher = airtelBundleRegex.matcher(sanitized)
        if (matcher.find()) {
            val (ad, at) = airtelDate()
            return buildPending(
                code = matcher.group(3),
                amountStr = matcher.group(2),
                party = "Airtel Data",
                dateStr = ad,
                timeStr = at,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.85f,
                method = PaymentMethod.OTHER
            )?.copy(category = "Data")
        }


        // Match Lipa Na M-Pesa Buy Goods/Paybill
        matcher = merchantRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = matcher.group(3),
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.EXPENSE,
                raw = sanitized
            )
        }


        // Match Received Money
        matcher = receiveRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = cleanParty(matcher.group(3) ?: "M-Pesa customer"),
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.INCOME,
                raw = sanitized
            )
        }


        // Match Airtime purchase (no payee merchant)
        matcher = airtimeRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "Safaricom Airtime",
                dateStr = matcher.group(3),
                timeStr = matcher.group(4),
                type = TransactionType.EXPENSE,
                raw = sanitized
            )
        }


        // Match Agent withdrawal — date may lead ("Confirmed. on d/M/yy at
        // h:mm AM Withdraw ...") and the party stops at the balance tail.
        matcher = withdrawRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(4),
                party = cleanParty(matcher.group(5) ?: "M-Pesa agent"),
                dateStr = matcher.group(2) ?: matcher.group(6),
                timeStr = matcher.group(3) ?: matcher.group(7),
                // Cash in hand is a move, not spending — spending is logged
                // when the cash is actually used (avoids double-counting).
                type = TransactionType.TRANSFER,
                raw = sanitized
            )?.copy(category = "Transfers")
        }


        // Match Paybill with optional account number
        matcher = paybillRegex.matcher(sanitized)
        if (matcher.find()) {
            val business = cleanParty(matcher.group(3) ?: "Paybill")
            val account = matcher.group(4)?.trim().orEmpty()
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = if (account.isNotEmpty()) "$business • $account" else business,
                dateStr = matcher.group(5),
                timeStr = matcher.group(6),
                type = TransactionType.EXPENSE,
                raw = sanitized
            )
        }


        // Match Till payment (shop name optional — Shopping fallback when unknown)
        matcher = tillRegex.matcher(sanitized)
        if (matcher.find()) {
            val till = matcher.group(3) ?: ""
            val name = (matcher.group(4) ?: "").trim()
            val merchant = name.ifEmpty { "Till $till" }
            val pending = buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = merchant,
                dateStr = matcher.group(5),
                timeStr = matcher.group(6),
                type = TransactionType.EXPENSE,
                raw = sanitized
            )
            return pending?.copy(
                category = if (pending.category == "Other") "Shopping" else pending.category,
                confidenceScore = 0.85f
            )
        }


        // Match gifted airtime ("worth of airtime to 0712...")
        matcher = giftedAirtimeRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "Airtime to ${matcher.group(3)}",
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.EXPENSE,
                raw = sanitized
            )
        }


        // Match data-bundle purchase (dateless Safaricom format — timestamp now)
        matcher = bundleRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = "Safaricom Data",
                dateStr = null,
                timeStr = null,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.8f
            )?.copy(category = "Data")
        }


        // Match M-Shwari deposit — real savings, excluded from spending insights
        matcher = mshwariRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2) ?: matcher.group(3),
                party = "M-Shwari",
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.SAVING,
                raw = sanitized,
                confidence = 0.85f
            )
        }


        // Match bank → M-Pesa (money entering the tracked wallet)
        matcher = bankInRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2) ?: matcher.group(3),
                party = "${matcher.group(4)?.trim()} transfer",
                dateStr = null,
                timeStr = null,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.8f
            )?.copy(category = "Transfers", subcategory = "Own account transfer")
        }


        // Match M-Pesa → bank (money leaving the tracked wallet, kept as saving)
        matcher = bankOutRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2) ?: matcher.group(3),
                party = "${matcher.group(4)?.trim()} transfer",
                dateStr = null,
                timeStr = null,
                type = TransactionType.SAVING,
                raw = sanitized,
                confidence = 0.8f
            )?.copy(category = "Transfers")
        }


        // Match Fuliza overdraft (borrowed, spendable now — user sorts the debt)
        matcher = fulizaRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "Fuliza",
                dateStr = null,
                timeStr = null,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.8f
            )
        }


        // Match HELB mentions with an amount (bank-side or upkeep texts that
        // miss the standard M-Pesa receive shape — M-Pesa HELB already matched above).
        matcher = helbRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = "HELB",
                dateStr = null,
                timeStr = null,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.85f
            )
        }


        // Match bank credit SMS (money entering a bank account).
        matcher = bankCreditRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(3) ?: matcher.group(4),
                party = (matcher.group(1) ?: "Bank").trim(),
                dateStr = null,
                timeStr = null,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.7f,
                method = PaymentMethod.BANK_TRANSFER
            )
        }


        // Match bank debit SMS (money leaving a bank account).
        matcher = bankDebitRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(3) ?: matcher.group(4),
                party = (matcher.group(1) ?: "Bank").trim(),
                dateStr = null,
                timeStr = null,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.7f,
                method = PaymentMethod.BANK_TRANSFER
            )
        }


        // Match Okoa Jahazi / emergency airtime advances.
        matcher = okoaRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = "Okoa Jahazi",
                dateStr = null,
                timeStr = null,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.7f
            )
        }


        // Match Fuliza limit/balance notices — a credit LIMIT, not money in.
        // Parsing these as income inflates earnings, so skip outright —
        // unless it smells like a real disbursement (falls to loan patterns).
        matcher = fulizaBalanceRegex.matcher(sanitized)
        if (matcher.find()) {
            val fl = sanitized.lowercase()
            val moves = fl.contains("disbursed") || fl.contains("disbursement") || fl.contains("approved") || fl.contains("advanced") || fl.contains("borrowed")
            val repays = fl.contains("repaid") || fl.contains("repayment") || fl.contains("paid back") || fl.contains("recovered")
            if (!moves && !repays) return null
        }


        // Match agent deposit (cash → M-Pesa wallet). SACCO deposits look
        // identical but head the other way — savings out, not income in.
        matcher = depositRegex.matcher(sanitized)
        if (matcher.find()) {
            val depParty = (matcher.group(3) ?: "M-Pesa agent").trim()
            if (depParty.contains("sacco", ignoreCase = true)) {
                return buildPending(
                    code = matcher.group(1),
                    amountStr = matcher.group(2),
                    party = "SACCO",
                    dateStr = matcher.group(4),
                    timeStr = matcher.group(5),
                    type = TransactionType.SAVING,
                    raw = sanitized,
                    confidence = 0.85f
                )?.copy(category = "Savings")
            }
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = depParty,
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.9f
            )
        }


        // Match digital-loan disbursement: money in, user sorts the debt.
        // Ahead of the telco catchers on purpose — "loan repayment ... received
        // from ..." names a lender, not income.
        matcher = loanInRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1) ?: matcher.group(2),
                party = "Digital loan",
                dateStr = null,
                timeStr = null,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.7f
            )?.copy(category = "Debt")
        }


        // Match loan repayment: money out to a lender.
        matcher = loanOutRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(2) ?: matcher.group(3),
                party = "Loan repayment",
                dateStr = null,
                timeStr = null,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.7f
            )?.copy(
                category = "Debt",
                subcategory = if (low0.contains("fuliza")) "Fuliza repayment" else ""
            )
        }


        // Match Fuliza repaid: debt serviced.
        matcher = fulizaRepayRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1) ?: matcher.group(2),
                party = "Fuliza",
                dateStr = null,
                timeStr = null,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.75f
            )?.copy(category = "Debt", subcategory = "Fuliza repayment")
        }


        // Match other-telco sends (Airtel/Telkom/Equitel shape).
        matcher = telcoSentRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1) ?: matcher.group(2),
                party = cleanParty(matcher.group(3) ?: "Telco transfer"),
                dateStr = null,
                timeStr = null,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.7f
            )
        }


        // Match other-telco receipts.
        matcher = telcoReceivedRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1) ?: matcher.group(2),
                party = cleanParty(matcher.group(3) ?: "Telco transfer"),
                dateStr = null,
                timeStr = null,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.7f
            )
        }


        // Match header-less paid shapes (Airtel/T-Kash merchant payments).
        matcher = telcoPaidRegex.matcher(sanitized)
        if (matcher.find()) {
            val business = (matcher.group(3) ?: "Merchant").trim()
            val account = matcher.group(4)?.trim().orEmpty()
            return buildPending(
                code = null,
                amountStr = matcher.group(1) ?: matcher.group(2),
                party = if (account.isNotEmpty()) "$business • $account" else business,
                dateStr = null,
                timeStr = null,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.6f
            )
        }


        // Match "purchased KSh20 airtime" variant.
        matcher = airtimeBuyRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = "Safaricom Airtime",
                dateStr = null,
                timeStr = null,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.75f
            )
        }


        // Match bank SMS whose body omits the bank name (sender carries it).
        if (senderBank != null) {
            matcher = bankCreditBareRegex.matcher(sanitized)
            if (matcher.find()) {
                return buildPending(
                code = null,
                amountStr = matcher.group(2) ?: matcher.group(3),
                party = senderBank,
                dateStr = null,
                timeStr = null,
                type = TransactionType.INCOME,
                    raw = sanitized,
                    confidence = 0.7f,
                    method = PaymentMethod.BANK_TRANSFER
                )
            }
            matcher = bankDebitBareRegex.matcher(sanitized)
            if (matcher.find()) {
                return buildPending(
                code = null,
                amountStr = matcher.group(2) ?: matcher.group(3),
                party = senderBank,
                dateStr = null,
                timeStr = null,
                type = TransactionType.EXPENSE,
                    raw = sanitized,
                    confidence = 0.7f,
                    method = PaymentMethod.BANK_TRANSFER
                )
            }
        }


        // Match M-Shwari → wallet: internal move, spendable later.
        matcher = mshwariOutRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2) ?: matcher.group(3),
                party = "M-Shwari",
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.TRANSFER,
                raw = sanitized,
                confidence = 0.85f
            )
        }


        // Match SACCO deposits: real savings.
        matcher = saccoRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2) ?: matcher.group(3),
                party = "SACCO",
                dateStr = null,
                timeStr = null,
                type = TransactionType.SAVING,
                raw = sanitized,
                confidence = 0.8f
            )?.copy(category = "Savings")
        }


        // (Loan branches live above, ahead of the telco catchers: a "loan
        // repayment ... received from ..." names a lender, not income.)


        // Match cashback rewards: small real income.
        matcher = cashbackRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = "Cashback",
                dateStr = null,
                timeStr = null,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.7f
            )?.copy(category = "Other")
        }


        // Match savings interest: small real income.
        matcher = interestRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = "Interest",
                dateStr = null,
                timeStr = null,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.7f
            )?.copy(category = "Savings")
        }


        // Refund of a paid transaction ("Your Pay Shop transaction X of 10Ksh
        // has been refunded by ...") — amount rides BEFORE the currency word.
        matcher = refundRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "Refund",
                dateStr = null,
                timeStr = null,
                // Returned money is restored, not earned — keep it out of income.
                type = TransactionType.TRANSFER,
                raw = sanitized,
                confidence = 0.8f
            )?.copy(category = "Transfers")
        }


        // Match reversal notices without the standard "Confirmed." header
        matcher = bareReversalRegex.matcher(sanitized)
        if (matcher.find()) {
            // The only Ksh figure may be the balance tail ("Your account
            // balance is now Ksh5,987.00") — a balance is not the reversed
            // amount, and the amount is unknowable from the text. Skip.
            val pre = sanitized.substring(0, matcher.start(3)).lowercase()
            if (!pre.contains("balance")) {
                return buildPending(
                    code = matcher.group(1),
                    amountStr = matcher.group(3),
                    party = "M-Pesa Reversal",
                    dateStr = null,
                    timeStr = null,
                    // Returned money is restored, not earned — keep it out of income.
                    type = TransactionType.TRANSFER,
                    raw = sanitized,
                    confidence = 0.8f
                )?.copy(category = "Transfers")?.copy(category = "Transfers")
            }
        }


    // Salary / net pay credits that name no bank ("Your salary of KES X").
    val salaryRegex = Pattern.compile(
        "(?i)(?:salary|net\\s*pay|payroll|wages)[^.]{0,60}?(?:KES|KSh|Ksh)\\s*([0-9,.]+)"
    )
    // Standing orders and direct debits out.
    val standingRegex = Pattern.compile(
        "(?i)(?:standing\\s+order|direct\\s+debit)[^.]{0,60}?(?:KES|KSh|Ksh)\\s*([0-9,.]+)[^.]{0,60}?(?:to|for)\\s+([^.,]+)"
    )
    // Cheques cleared: money in (bounced ones die in the failed-guard).
    val chequeRegex = Pattern.compile(
        "(?i)cheque.{0,80}?(?:(?:cleared|deposited|credited|paid)[^.]{0,40}?KSh\\s*([0-9,.]+)|KSh\\s*([0-9,.]+)[^.]{0,40}?(?:cleared|deposited|credited|paid))"
    )
    // Bare ATM cash-out (bank sender variants that skip Confirmed shapes).
    val atmRegex = Pattern.compile(
        "(?i)\\batm\\b[^.]{0,60}?(?:KES|KSh|Ksh)\\s*([0-9,.]+)"
    )
    // Card/POS/online purchases name their merchant.
    val posRegex = Pattern.compile(
        "(?i)(?:\\bpos\\b|online\\s+purchase|card\\s+purchase|card\\s+payment)[^.]{0,40}?(?:KES|KSh|Ksh)\\s*([0-9,.]+)[^.]{0,40}?(?:at|from|to)\\s+([^.,]+)"
    )
        // Generic verb-typed catcher: any other "Confirmed ... KSh X" shape.
        // Verbs decide the type; party = text after the amount. Low
        // confidence, human-reviewed — breadth without pretending precision.
        // (Salary/standing/cheque/ATM/POS live here too: real shapes the
        // strict patterns miss, all reviewable at 0.6–0.7.)
        matcher = salaryRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = "Salary",
                dateStr = null,
                timeStr = null,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.7f
            )
        }


        // Match standing orders and direct debits out.
        matcher = standingRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = (matcher.group(2) ?: "Standing order").trim(),
                dateStr = null,
                timeStr = null,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.65f
            )
        }


        // Match cleared cheques: money in.
        matcher = chequeRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1) ?: matcher.group(2),
                party = "Cheque deposit",
                dateStr = null,
                timeStr = null,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.7f
            )
        }


        // Match bare ATM cash-outs: money moved to pocket, not spent.
        matcher = atmRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = "ATM withdrawal",
                dateStr = null,
                timeStr = null,
                type = TransactionType.TRANSFER,
                raw = sanitized,
                confidence = 0.6f
            )
        }


        // Match card/POS/online purchases with their merchant.
        matcher = posRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = (matcher.group(2) ?: "Card purchase").trim(),
                dateStr = null,
                timeStr = null,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.7f
            )
        }


        // Narrow gap patterns live here: every strict shape above had its
        // chance, the generic catcher below takes anything left. All 0.6–0.7,
        // all human-reviewed.
        // Match amount-then-confirmation receipts ("Payment of KSh X confirmed").
        matcher = amountFirstConfirmRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = "M-Pesa",
                dateStr = null,
                timeStr = null,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.6f
            )
        }


        // Match Swahili confirmations (Umetuma / Umepokea).
        matcher = swahiliSentRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = (matcher.group(2) ?: "M-Pesa").trim(),
                dateStr = null,
                timeStr = null,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.65f
            )
        }
        matcher = swahiliReceivedRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = (matcher.group(2) ?: "M-Pesa").trim(),
                dateStr = null,
                timeStr = null,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.65f
            )
        }


        // Match amount-last bundle buys ("purchased 1GB ... for KSh99").
        matcher = bundleLastRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(2),
                party = "Safaricom Data",
                dateStr = null,
                timeStr = null,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.7f
            )
        }


        // Match header-less SACCO confirmations: real savings, reviewable.
        matcher = saccoBareRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(2),
                party = "SACCO",
                dateStr = null,
                timeStr = null,
                type = TransactionType.SAVING,
                raw = sanitized,
                confidence = 0.65f
            )?.copy(category = "Savings")
        }


        // Match agent cash-in phrased as instruction ("Give KSh X cash to ...").
        matcher = agentGiveRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(4),
                party = (matcher.group(5) ?: "M-Pesa agent").trim(),
                dateStr = matcher.group(2) ?: matcher.group(6),
                timeStr = matcher.group(3) ?: matcher.group(7),
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.85f
            )
        }


        val gm = genericMoveRegex.matcher(sanitized)
        if (gm.find()) {
            val amountStr = gm.group(1) ?: ""
            // Balance tails ("Your M-PESA balance was Ksh339.00") are not
            // movements — a balance-preceded amount means no money moved.
            val pre = sanitized.substring(0, gm.start(1)).lowercase()
            if (!pre.contains("balance")) {
                val low = sanitized.lowercase()
                val type = when {
                    low.contains("received") || low.contains("credited") || low.contains("deposited") -> TransactionType.INCOME
                    low.contains("transferred") || low.contains("withdrawn") || low.contains("reversed") -> TransactionType.TRANSFER
                    else -> TransactionType.EXPENSE
                }
                var party = (gm.group(2) ?: "").split(Regex("\\s+on\\s+|\\s+at\\s+"))[0].trim()
                repeat(3) {
                    party = party.replace(Regex("^(to|from|for|of|paid|sent|received|credited|deposited|transferred|withdrawn)\\s+", RegexOption.IGNORE_CASE), "").trim()
                }
                if (party.length > 48) party = party.take(48)
                if (party.isEmpty()) party = "M-Pesa"
                return buildPending(
                    code = null,
                    amountStr = amountStr,
                    party = cleanParty(party),
                    dateStr = null,
                    timeStr = null,
                    type = type,
                    raw = sanitized,
                    confidence = 0.5f
                )
            }
        }


        // Generic fallback: confirmed code + amount, lower confidence.
        // Reversals return money, everything else unknown leaves as expense.
        // Balance-preceded amounts are balance tails, not movements — skip.
        matcher = fallbackRegex.matcher(sanitized)
        if (matcher.find()) {
            val pre = sanitized.substring(0, matcher.start(2)).lowercase()
            if (!pre.contains("balance")) {
                val reversal = sanitized.contains("revers", ignoreCase = true)
                return buildPending(
                    code = matcher.group(1),
                    amountStr = matcher.group(2),
                    party = if (reversal) "M-Pesa Reversal" else "M-Pesa",
                    dateStr = null,
                    timeStr = null,
                    type = if (reversal) TransactionType.TRANSFER else TransactionType.EXPENSE,
                    raw = sanitized
                )
            }
        }


        return null
    }


    // Fallback date harvest: branches that don't capture dates (banks,
    // loans, bundles, Swahili, telco) often still carry one. Anchored
    // positions ("on", "tarehe", "Date:") are the transaction's own stamp
    // and win; bare formats are a last resort — due-dates live in bodies too,
    // and parseDateTime's sanity gate is the final backstop.
    private val harvestDatePatterns = listOf(
        "(?i)\\bon\\s+(\\d{4}-\\d{1,2}-\\d{1,2})",
        "(?i)\\bon\\s+(?!\\d{4}-)(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})(?![/-]?\\d)",
        "(?i)tarehe\\s+(?!\\d{4}-)(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})(?![/-]?\\d)",
        "(?i)\\bdate\\s*:?\\s*(?!\\d{4}-)(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})(?![/-]?\\d)",
        "(?i)\\bon\\s+(\\d{1,2}(?:st|nd|rd|th)?\\s+[A-Za-z]{3,9}\\s+\\d{2,4})",
        "(?i)(?<!\\d)(\\d{1,2}(?:st|nd|rd|th)?\\s+[A-Za-z]{3,9}\\s+\\d{2,4})(?!\\d)",
        "(?i)(?<!\\d)(\\d{1,2}-[A-Za-z]{3,9}-\\d{2,4})(?!\\d)",
        "(?i)(?<!\\d)(\\d{4}-\\d{1,2}-\\d{1,2})(?!\\d)",
        "(?i)(?<!\\d)(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})(?![/-]?\\d)"
    )
    private val harvestTimePatterns = listOf(
        "(?i)(?:\\bat|saa)\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:AM|PM)?)",
        "(?i)\\b(\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:AM|PM|hrs|HRS))",
        "(?i)(?<!\\d)(\\d{1,2}:\\d{2})(?!\\d)"
    )

    // Party cleanup: real texts glue phone numbers to names — "JOHN DOE
    // 0722000000" (trailing) or "254729639024 MORRIS M." (leading). The
    // number is not the merchant; filing it splits one person into many.
    private fun cleanParty(raw: String): String {
        var p = raw.trim()
        p = p.replace(Regex("\\s+\\d{10,13}$"), "")
        p = p.replace(Regex("^\\d{10,13}\\s+"), "")
        return p.trim()
    }

    private fun harvestDateTime(body: String): Pair<String?, String?> {
        var date: String? = null
        for (p in harvestDatePatterns) {
            val m = Pattern.compile(p).matcher(body)
            if (m.find()) {
                date = m.group(1)
                break
            }
        }
        var time: String? = null
        for (p in harvestTimePatterns) {
            val m = Pattern.compile(p).matcher(body)
            if (m.find()) {
                time = m.group(1)
                break
            }
        }
        return date to time
    }

    private const val HARVEST_AGREE_WINDOW_MS = 48L * 60 * 60 * 1000

    private fun buildPendingRow(
        code: String?, amountStr: String?, party: String?,
        dateStr: String?, timeStr: String?, type: TransactionType, raw: String,
        confidence: Float = 0.95f,
        method: PaymentMethod = PaymentMethod.MPESA
    ): PendingTransaction? {
        return try {
            // Trailing dots are sentence punctuation, not decimals: "KSh99.00."
            // must parse as 99.00 — otherwise every end-of-sentence amount throws.
            val rawAmount = amountStr?.replace(",", "")?.trimEnd('.')?.toDouble() ?: return null
            // Zero-amount "movements" (Okoa balance 0, fee waivers) are noise.
            if (!rawAmount.isFinite() || rawAmount <= 0) return null
            // Transaction costs, merged rule: single-digit x.xx / 0.xx pocket
            // change is always a fee (5.30, 0.75); 10–60 needs a fee word in
            // the text (cost|charge|fee|deducted|levy) or it is a real
            // micro-purchase (smocha 28.00). Anything bigger, or xxx.xx
            // decimals (123.45), is real money — decimalled xxx.xx is
            // Okoa/Fuliza territory, already routed by inferCategory below.
            val cleanAmt = amountStr?.replace(",", "")?.trimEnd('.')?.trim() ?: ""
            val amtVal = cleanAmt.toDoubleOrNull()
            val shaped = amtVal != null && amtVal > 0 && amtVal <= 60.0 &&
                cleanAmt.matches(Regex("""^\d{1,2}\.\d{1,2}$"""))
            val feeWord = Regex("(?i)cost|charge|\\bfee\\b|deducted|levy").containsMatchIn(raw)
            val isCost = shaped && (amtVal!! < 10.0 || feeWord)
            // Strip paybill account suffixes ("... for account 12345") and balance tails
            val merchant = (party?.trim() ?: "Unknown Party")
                .split(" for account")[0]
                .split("  ")[0]
                .trim()
                .ifEmpty { "Unknown Party" }
            val timestamp = run {
                // Branches that don't capture dates (banks, loans, bundles,
                // Swahili, telco) often still carry one — harvest the body's
                // own stamp before defaulting to now.
                val harvested = if (dateStr == null || timeStr == null) harvestDateTime(raw) else null to null
                parseDateTime(dateStr ?: harvested.first, timeStr ?: harvested.second)
            }

            val ziidiMovement = merchant.contains("ziidi", ignoreCase = true)
            val safaricomData = type == TransactionType.EXPENSE &&
                merchant.contains("safaricom", ignoreCase = true) &&
                Regex("(?i)\\b(bundle|bundles|data)\\b").containsMatchIn(raw)
            PendingTransaction(
                amount = rawAmount,
                type = type,
                category = when {
                    ziidiMovement -> "Savings"
                    safaricomData -> "Data"
                    else -> inferCategory(merchant, type)
                },
                // Fee flag rides subcategory, leaving displayMerchant /
                // displayCategory free for identity memory (alias + learned
                // category stamped by the scan pipeline, not the parser).
                subcategory = when {
                    isCost -> "Transaction Cost"
                    merchant.contains("Fuliza", ignoreCase = true) && type == TransactionType.INCOME -> "Borrowed funds"
                    merchant.contains("Fuliza", ignoreCase = true) && type == TransactionType.EXPENSE -> "Fuliza repayment"
                    ziidiMovement -> "Ziidi transfer"
                    else -> ""
                },
                merchant = when {
                    safaricomData && !code.isNullOrBlank() -> "Safaricom"
                    safaricomData -> "Safaricom Data"
                    ziidiMovement && !code.isNullOrBlank() -> "M-Pesa Ziidi"
                    ziidiMovement -> "Ziidi"
                    else -> merchant
                },
                dateTimestamp = timestamp,
                paymentMethod = method,
                source = TransactionSource.MPESA_SMS,
                sourceTransactionId = code,
                rawText = raw,
                confidenceScore = confidence
            )
        } catch (e: Exception) {
            null
        }
    }


    internal fun parseDateTime(dateStr: String?, timeStr: String?): Long {
        val now = System.currentTimeMillis()
        if (dateStr == null) return now
        return try {
            // Normalize KE-traffic variants before strict parsing: day-first
            // dash dates, ordinal suffixes ("12th Sep"), "Sept" spelling.
            var date = dateStr.trim()
                .replace(Regex("(?i)(\\d)(st|nd|rd|th)\\b"), "$1")
                .replace(Regex("(?i)\\bsept\\b"), "Sep")
            if (date.matches(Regex("\\d{1,2}-\\d{1,2}-\\d{4}"))) date = date.replace('-', '/')
            // Dateless-time bodies (bank "on 12/9/26" with no clock) land at
            // noon — never midnight, which would misfile them a day early.
            val cleanTime = (timeStr ?: "12:00").trim()
                .replace(Regex("(?i)\\s*(hrs|eat)\\.?"), "")
                .replace("PM", " PM").replace("AM", " AM")
                .replace("\\s+".toRegex(), " ").trim()
            // 4-digit years first (a "12/09/2026" forced through dd/MM/yy lands
            // in 2020 — the "confirm yesterday" ghost). Strict, newest wins.
            // 12h-with-marker patterns lead: "dd/MM/yyyy H:mm" would swallow
            // "3:16 PM" as 03:16 and ignore the PM. 24h times fail h (1-12)
            // and fall through to H:mm below.
            val tries = listOf(
                "dd/MM/yyyy h:mm a", "dd/MM/yyyy H:mm",
                "dd/MM/yy h:mm a", "dd/MM/yy H:mm",
                "dd-MMM-yyyy h:mm a", "dd-MMM-yyyy H:mm",
                "dd-MMM-yy h:mm a", "dd-MMM-yy H:mm",
                "dd MMM yyyy h:mm a", "dd MMM yyyy H:mm",
                "dd MMM yy h:mm a", "dd MMM yy H:mm",
                "dd MMMM yyyy h:mm a", "dd MMMM yyyy H:mm",
                "yyyy-MM-dd h:mm a", "yyyy-MM-dd H:mm",
                "dd/MM/yyyy", "dd/MM/yy",
                "dd-MMM-yyyy", "dd-MMM-yy", "dd MMMM yyyy", "yyyy-MM-dd"
            )
            for (pattern in tries) {
                try {
                    val format = SimpleDateFormat(pattern, Locale.US)
                    format.isLenient = false
                    val dated = pattern.contains('H') || pattern.contains('h')
                    val t = format.parse(if (dated) "$date $cleanTime" else date)?.time ?: continue
                    // Sanity: text dates are the past, never the far future
                    // (due-date ghosts) nor years off.
                    if (t > now + 24L * 60 * 60 * 1000) continue
                    if (now - t > 370L * 24 * 60 * 60 * 1000) continue
                    return t
                } catch (e: Exception) {
                    // Try the next pattern.
                }
            }
            now
        } catch (e: Exception) {
            now
        }
    }


    fun inferCategory(merchant: String, type: TransactionType): String {
        val lower = merchant.lowercase()
        // Transfers and loans first — they beat the INCOME default below.
        if (lower.contains("fuliza")) return "Debt"
        if (lower.contains("ziidi")) return "Savings"
        if (lower.contains("m-shwari") || lower.contains("mshwari")) return "Savings"
        if (lower.contains("sacco")) return "Savings"
        if (lower.contains("loan") || lower.contains("tala") || lower.contains("branch") || lower.contains("zenka") || lower.contains("mkopa") || lower.contains("m-kopa") || lower.contains("hustler")) return "Debt"
        if (lower.contains("transfer") || lower.contains("agent") || lower.contains("pochi") || lower.contains("kcb") || lower.contains("equity") || lower.contains("absa") || lower.contains("stanbic") || lower.contains("co-op") || lower.contains("coop") || lower.contains("family") || lower.contains("dtb") || lower.contains("ncba") || lower.contains("bank")) return "Transfers"
        if (type == TransactionType.INCOME) return "Salary"
        return when {
            lower.contains("bundle") || lower.contains("data") -> "Data"
            lower.contains("safaricom") || lower.contains("airtime") || lower.contains("okoa") || lower.contains("saf") -> "Airtime"
            lower.contains("kplc") || lower.contains("token") || lower.contains("electric") -> "Electricity"
            lower.contains("supermarket") || lower.contains("naivas") || lower.contains("quickmart") || lower.contains("carrefour") || lower.contains("chandarana") || lower.contains("duka") || lower.contains("jumia") || lower.contains("kilimall") || lower.contains("jiji") || lower.contains("eastmatt") || lower.contains("cleanshelf") || lower.contains("magunas") || lower.contains("kibo") || lower.contains("lipa mdogo") -> "Shopping"
            // Bookshops are School, not print shops — checked before "book".
            lower.contains("text book") || lower.contains("textbook") -> "School"
            // Printing outranks Food: "Cyber Cafe" is a print shop, not lunch.
            lower.contains("cyber") || lower.contains("print") || lower.contains("book") || lower.contains("stationery") || lower.contains("photocopy") -> "Printing"
            lower.contains("java") || lower.contains("hotel") || lower.contains("cafe") || lower.contains("kiosk") || lower.contains("kibanda") || lower.contains("vibanda") || lower.contains("lunch") || lower.contains("supper") || lower.contains("breakfast") || lower.contains("dinner") || lower.contains("chapo") || lower.contains("chips") || lower.contains("smokie") || lower.contains("mutura") || lower.contains("ndengu") || lower.contains("ugali") ||             lower.contains("sukuma") || lower.contains("pilau") || lower.contains("chapati") || lower.contains("nyama") || lower.contains("kuku") || lower.contains("mama") || lower.contains("rest") || lower.contains("food") || lower.contains("eat") || lower.contains("artcaffe") || lower.contains("kfc") || lower.contains("big square") || lower.contains("galitos") || lower.contains("chicken inn") || lower.contains("pizza inn") -> "Food"
            lower.contains("matatu") || lower.contains("uber") || lower.contains("bolt") || lower.contains("lavender") || lower.contains("stage") || lower.contains("fare") || lower.contains("boda") || lower.contains("motorbike") || lower.contains("grability") || lower.contains("ride") || lower.contains("car") || lower.contains("parking") || lower.contains("expressway") || lower.contains("ntsa") -> "Transport"
            // "house" alone doesn't mean rent (coffee houses, food houses) —
            // real rent texts say rent/hostel/apartment/nyumba.
            lower.contains("hostel") || lower.contains("rent") || lower.contains("apartment") -> "Rent"
            lower.contains("water") || lower.contains("nairobi water") -> "Water"
            lower.contains("school") || lower.contains("fees") || lower.contains(" fee ") || lower.contains("university") || lower.contains("tuition") || lower.contains("exam") || lower.contains("strathmore") -> "School"
            lower.contains("reversal") || lower.contains("revers") -> "Other"
            lower.contains("salon") || lower.contains("barber") || lower.contains("hair") || lower.contains("nails") -> "Kujibamba"
            lower.contains("shirt") || lower.contains("trouser") || lower.contains("shoe") || lower.contains("clothe") || lower.contains("dress") || lower.contains("jacket") || lower.contains("jeans") || lower.contains("wear") -> "Clothes"
            lower.contains("hospital") || lower.contains("clinic") || lower.contains("pharmacy") || lower.contains("medicine") || lower.contains("drug") || lower.contains("goodlife") || lower.contains("haltons") || lower.contains("mydawa") || lower.contains("khan") || lower.contains("shah") -> "Health"
            lower.contains("wifi") || lower.contains("internet") || lower.contains("modem") || lower.contains("zuku") -> "Data"
            lower.contains("shif") || lower.contains("nhif") -> "Health"
            lower.contains("nssf") -> "Savings"
            lower.contains("netflix") || lower.contains("spotify") || lower.contains("showmax") || lower.contains("dstv") || lower.contains("gotv") || lower.contains("startimes") || lower.contains("sportpesa") || lower.contains("betika") -> "Kujibamba"
            lower.contains("fuel") || lower.contains("petrol") || lower.contains("shell") || lower.contains("rubis") || lower.contains("totalenergies") || lower.contains("ola energy") -> "Transport"
            else -> "Other"
        }
    }
}


// M-Pesa wallet balance: every transaction SMS carries a balance tail
// ("New M-PESA balance is KSh X"). Read it, store it, show it — display-only,
// the ledger stays the source of truth for math.
private val balanceRegex = Pattern.compile(
    "(?i)(?:new\\s+|your\\s+)?M-?PESA\\s+balance\\s*(?:is|was|:)?\\s*(?:KSh|KES|Ksh)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
)
// Fallback (from PesaFlow main): an SMS that is solely a KSh amount.
// Guarded by the blocklist so Fuliza/loan/fee tails never read as wallet.
private val balanceLooseRegex = Pattern.compile(
    "(?i)^\\s*KSh\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*$"
)
private val balanceBlocklist = listOf("fuliza", "loan", "deni", "madeni", "bill", "fees", "overdue", "owed", "m-shwari", "mshwari")
// Airtel tail ("Balance: KSh4,000.00") — tried before the loose fallback so a
// bare-amount SMS never steals an Airtel balance read.
private val airtelBalanceRegex = Pattern.compile(
    "(?i)Balance:\\s*(?:KSh|KES|Ksh)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
)

fun parseBalance(smsBody: String): Double? {
    return try {
        val clean = smsBody.replace("\n", " ")
        val m = balanceRegex.matcher(clean)
        if (m.find()) return m.group(1)?.replace(",", "")?.toDoubleOrNull()
        val ma = airtelBalanceRegex.matcher(clean)
        if (ma.find()) return ma.group(1)?.replace(",", "")?.toDoubleOrNull()
        val low = clean.lowercase()
        if (balanceBlocklist.any { low.contains(it) }) return null
        val m2 = balanceLooseRegex.matcher(clean)
        if (m2.find()) m2.group(1)?.replace(",", "")?.toDoubleOrNull() else null
    } catch (e: Exception) {
        null
    }
}

fun saveMpesaBalance(context: Context, amount: Double) {
    context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE).edit()
        .putString("mpesa_balance", amount.toString())
        .putLong("mpesa_balance_at", System.currentTimeMillis())
        .apply()
}

// Returns (amount, timestamp) or null when no SMS has ever carried a balance.
fun readMpesaBalance(context: Context): Pair<Double, Long>? {
    val p = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
    val amt = p.getString("mpesa_balance", null)?.toDoubleOrNull() ?: return null
    val at = p.getLong("mpesa_balance_at", 0L)
    if (at <= 0L) return null
    return amt to at
}

// Transaction costs ride as tails ("Transaction cost, KSh22.00") — tracked
// separately (from PesaFlow main) so fee bleed is visible, never mixed
// into spending.
// Month-keyed fee pot: rolls over automatically on the 1st.
private val feeRegex = Pattern.compile(
    "(?i)(?:transaction cost|access fee|service fee|service charge|withdrawal charge|transfer charge|fee charged|charges)[^.]{0,30}?(?:KSh|KES|Ksh)\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
)

fun parseFee(smsBody: String): Double? {
    return try {
        val m = feeRegex.matcher(smsBody.replace("\n", " "))
        if (m.find()) m.group(1)?.replace(",", "")?.toDoubleOrNull() else null
    } catch (e: Exception) {
        null
    }
}

fun saveFee(context: Context, amount: Double) {
    val p = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
    val month = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
    val total = if (p.getString("mpesa_fee_month", "") == month) {
        (p.getString("mpesa_fee_total", "0")?.toDoubleOrNull() ?: 0.0) + amount
    } else amount
    p.edit().putString("mpesa_fee_month", month).putString("mpesa_fee_total", total.toString()).apply()
}

fun readMonthFees(context: Context): Double {
    val p = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
    val month = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
    if (p.getString("mpesa_fee_month", "") != month) return 0.0
    return p.getString("mpesa_fee_total", "0")?.toDoubleOrNull() ?: 0.0
}

fun balanceAgeText(at: Long, now: Long): String {
    val mins = ((now - at) / 60000).coerceAtLeast(0)
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 60 * 24 -> "${mins / 60}h ago"
        else -> "${mins / (60 * 24)}d ago"
    }
}