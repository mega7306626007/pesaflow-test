package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isEarnedIncome
import com.pesaflow.app.data.parsers.MpesaParser
import org.junit.Assert.*
import org.junit.Test


class MpesaParserTest {

    @Test
    fun `sent money sms parses amount recipient and type`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh1,250.00 to John Doe on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1250.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(PaymentMethod.MPESA, tx.paymentMethod)
        assertEquals(TransactionSource.MPESA_SMS, tx.source)
        assertEquals("QWERTY1234", tx.sourceTransactionId)
        assertTrue(tx.merchant.contains("John"))
    }

    @Test
    fun `paybill sms parses as expense`() {
        val sms = "QWERTY1234 Confirmed. KSh500.00 paid to Naivas Supermarket. on 12/9/26 at 9:15 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Shopping", tx.category)
    }

    @Test
    fun `received money sms parses as income`() {
        val sms = "QWERTY1234 Confirmed. You have received KSh2,000.00 from Mary Jane on 12/9/26 at 11:00 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(2000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
    }

    @Test
    fun `non mpesa text returns null`() {
        assertNull(MpesaParser.parseMessage("Hello, how are you today?"))
        assertNull(MpesaParser.parseMessage(""))
        assertNull(MpesaParser.parseMessage("KSh without confirmation"))
    }

    @Test
    fun `malformed amount returns null`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh to John Doe on 12/9/26 at 10:30 AM"
        assertNull(MpesaParser.parseMessage(sms))
    }

    @Test
    fun `category inference covers student staples`() {
        assertEquals("Food", MpesaParser.inferCategory("Kibanda Lunch", TransactionType.EXPENSE))
        assertEquals("Transport", MpesaParser.inferCategory("Matatu Stage 46", TransactionType.EXPENSE))
        assertEquals("Airtime", MpesaParser.inferCategory("Safaricom Airtime", TransactionType.EXPENSE))
        assertEquals("Salary", MpesaParser.inferCategory("Anything", TransactionType.INCOME))
        assertEquals("Other", MpesaParser.inferCategory("Unknown Shop XYZ", TransactionType.EXPENSE))
    }

    @Test
    fun `paybill with account parses business and keeps account`() {
        val sms = "QWERTY1234 Confirmed. KSh1000.00 sent to KPLC PREPAID for account 123456 on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Electricity", tx.category)
        assertTrue(tx.merchant.contains("KPLC"))
        assertTrue(tx.merchant.contains("123456"))
    }

    @Test
    fun `till payment with shop name parses merchant`() {
        val sms = "QWERTY1234 Confirmed. KSh200.00 paid to Till 567890 - Mama Mboga on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(200.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertTrue(tx.merchant.contains("Mama Mboga"))
    }

    @Test
    fun `till payment without shop name falls back to shopping`() {
        val sms = "QWERTY1234 Confirmed. KSh200.00 paid to 567890 on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals("Shopping", tx!!.category)
        assertTrue(tx.merchant.contains("567890"))
    }

    @Test
    fun `gifted airtime parses as airtime expense`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh20.00 worth of airtime to 0712345678 on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(20.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Airtime", tx.category)
    }

    @Test
    fun `bundle purchase parses without code or date`() {
        val sms = "Confirmed. You have bought 1GB bundles for KSh99.00. Dial *544# for balance."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(99.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Data", tx.category)
    }

    @Test
    fun `mshwari deposit parses as saving excluded from spending`() {
        val sms = "QWERTY1234 Confirmed. You have transferred KSh500.00 from M-PESA to M-Shwari on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.SAVING, tx.type)
        assertEquals("Savings", tx.category)
    }

    @Test
    fun `bank to mpesa parses as transfer income`() {
        val sms = "QWERTY1234 Confirmed. You have transferred KSh1000.00 from KCB to M-PESA on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Transfers", tx.category)
        assertFalse(tx.isEarnedIncome())
    }

    @Test
    fun `fuliza overdraft parses as income for pending review`() {
        val sms = "QWERTY1234 Confirmed. You have borrowed KSh500.00 via Fuliza M-PESA on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Borrowed funds", tx.subcategory)
    }

    @Test
    fun `ziidi saving and withdrawal are distinct non-earned movements`() {
        val deposit = MpesaParser.parseMessage("You have deposited KSh1,000.00 to Ziidi on 12/9/26 at 10:30 AM")
        val withdrawal = MpesaParser.parseMessage("You have withdrawn KSh300.00 from Ziidi to M-PESA on 12/9/26 at 10:30 AM")
        assertNotNull(deposit)
        assertNotNull(withdrawal)
        assertEquals(TransactionType.SAVING, deposit!!.type)
        assertEquals(TransactionType.INCOME, withdrawal!!.type)
        assertEquals("Ziidi transfer", withdrawal.subcategory)
        assertFalse(withdrawal.isEarnedIncome())
    }

    @Test
    fun `safaricom bundle alert is categorized as data`() {
        val tx = MpesaParser.parseMessage(
            "Confirmed. You have bought 1GB bundles for KSh99.00. Dial *544# for balance.",
            "SAFARICOM"
        )
        assertNotNull(tx)
        assertEquals("Data", tx!!.category)
        assertEquals("Safaricom Data", tx.merchant)
    }

    @Test
    fun `bare reversal without confirmed header parses as transfer`() {
        val sms = "QWERTY1234 You have successfully reversed KSh250.00 to John Doe"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(250.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.TRANSFER, tx.type)
    }

    @Test
    fun `expanded merchant keywords map to new categories`() {        assertEquals("Transport", MpesaParser.inferCategory("Matatu Fare", TransactionType.EXPENSE))
        assertEquals("Clothes", MpesaParser.inferCategory("Shirt Store", TransactionType.EXPENSE))
        assertEquals("Kujibamba", MpesaParser.inferCategory("Salon Beauty", TransactionType.EXPENSE))
        assertEquals("Health", MpesaParser.inferCategory("Pharmacy Plus", TransactionType.EXPENSE))
        assertEquals("Data", MpesaParser.inferCategory("Wifi Kenya", TransactionType.EXPENSE))
        assertEquals("Savings", MpesaParser.inferCategory("M-Shwari", TransactionType.SAVING))
        assertEquals("Transfers", MpesaParser.inferCategory("KCB transfer", TransactionType.INCOME))
    }

    @Test
    fun `hardened patterns tolerate missing spaces and long dates`() {
        val sms = "ab12cd34 Confirmed.You have sent KSh 2,500.00 to John Doe on 12/09/2026 at 10:30AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(2500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertTrue(tx.merchant.contains("John"))
    }

    @Test
    fun `hardened patterns tolerate lowercase messages`() {
        val sms = "shtest12 confirmed. ksh300.00 paid to naivas. on 12/9/26 at 9:15 am"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(300.0, tx!!.amount, 0.001)
        assertEquals("Shopping", tx!!.category)
    }

    @Test
    fun `four digit year does not land in 2020`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh1,000.00 to John Doe on 12/09/2026 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        val year = java.util.Calendar.getInstance().apply { timeInMillis = tx!!.dateTimestamp }
            .get(java.util.Calendar.YEAR)
        assertEquals(2026, year)
    }

    @Test
    fun `24h time parses to the same day`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh1,000.00 to John Doe on 12/09/2026 at 22:30"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = tx!!.dateTimestamp }
        assertEquals(2026, cal.get(java.util.Calendar.YEAR))
        assertEquals(22, cal.get(java.util.Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `agent deposit parses as transfer income`() {
        val sms = "AB12CD34 Confirmed.You have deposited KSh1,000.00 to agent 234567 - JOHN DOE on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Transfers", tx.category)
    }

    @Test
    fun `airtel style send parses without code`() {
        val sms = "Dear customer, you have successfully sent Ksh 500.00 to 0712345678 on 12/9/26. Transaction ID: ABC123XYZ."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertTrue(tx.merchant.contains("0712345678"))
    }

    @Test
    fun `bank credit via sender parses when body omits bank name`() {
        val sms = "Dear member, your account 123456 has been credited with KES 8,500.00 on 12/9/26. Available balance KES 9,000."
        val tx = MpesaParser.parseMessage(sms, "EQUITY")
        assertNotNull(tx)
        assertEquals(8500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Transfers", tx.category)
        assertEquals(PaymentMethod.BANK_TRANSFER, tx.paymentMethod)
    }

    @Test
    fun `bank debit via sender parses as expense`() {
        val sms = "KCB Alert: Withdrawal of Ksh 2,000.00 at ATM Kenyatta Ave on 12/9/26. Available balance Ksh 5,000."
        val tx = MpesaParser.parseMessage(sms, "KCB")
        assertNotNull(tx)
        assertEquals(2000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun `purchased airtime variant parses`() {
        val sms = "Confirmed. You have purchased KSh20.00 airtime for 0712345678."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(20.0, tx!!.amount, 0.001)
        assertEquals("Airtime", tx!!.category)
    }

    @Test
    fun `helb upkeep text parses as income`() {
        val sms = "HELB upkeep of KSh 4,500.00 disbursed to your account."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(4500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
    }

    @Test
    fun `mshwari withdrawal parses as internal transfer`() {
        val sms = "QWERTY1234 Confirmed. You have transferred KSh1,000.00 from M-Shwari to M-PESA on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.TRANSFER, tx.type)
    }

    @Test
    fun `sacco deposit parses as saving`() {
        val sms = "QWERTY1234 Confirmed. You have deposited KSh500.00 to Stima Sacco on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.SAVING, tx.type)
        assertEquals("Savings", tx.category)
    }

    @Test
    fun `digital loan disbursement parses as income debt`() {
        val sms = "Your Tala loan of KSh3,000.00 has been approved and disbursed to M-PESA."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(3000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Debt", tx.category)
    }

    @Test
    fun `loan repayment parses as debt expense`() {
        val sms = "Loan repayment of KSh750.00 received from 0712345678. Thank you."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(750.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Debt", tx.category)
    }

    @Test
    fun `fuliza repaid parses as debt expense`() {
        val sms = "You have repaid KSh200.00 Fuliza amount. Outstanding balance KSh0.00."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(200.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Debt", tx.category)
        assertEquals("Fuliza repayment", tx.subcategory)
    }

    @Test
    fun `fuliza limit notice is skipped not income`() {
        assertNull(MpesaParser.parseMessage("Your Fuliza limit is KSh2,000.00. Available KSh2,000.00."))
        assertNull(MpesaParser.parseMessage("Fuliza balance KSh500.00. Repay to restore your limit."))
    }

    @Test
    fun `safaricom promo ads never parse`() {
        assertNull(MpesaParser.parseMessage("Get unbeatable offers! Saksaka 20GB for KSh499 only. Dial *544# today.", "SAFARICOM"))
        assertNull(MpesaParser.parseMessage("Special offer just for you: FREE 1GB when you buy 5GB. Offer valid till Sunday.", "SAFARICOM"))
        assertNull(MpesaParser.parseMessage("Enjoy amazing discounts on Tunukiwa devices this weekend. Visit a Safaricom shop.", "SAFARICOM"))
        assertNull(MpesaParser.parseMessage("Pata 50% extra data on all weekly bundles. Promotion ends soon. STOP to opt out.", "SAFARICOM"))
        assertNull(MpesaParser.parseMessage("You have received 100MB free data valid 24hrs. Thank you for staying with us.", "SAFARICOM"))
        assertTrue(MpesaParser.isPromoAd("Get unbeatable offers! Dial *544# today."))
        assertTrue(MpesaParser.isPromoAd("Pata 50% extra data. Promotion ends soon."))
        assertFalse(MpesaParser.isPromoAd("Congratulations! You have earned cashback of KSh45.00 on your transaction."))
        assertFalse(MpesaParser.isPromoAd("QHX123 Confirmed. KSh1,000.00 sent to Jane on 20/9/26. New Mpesa balance is ksh0.00."))
    }

    @Test
    fun `cashback parses as income`() {
        val sms = "Congratulations! You have earned cashback of KSh45.00 on your transaction."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(45.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
    }

    @Test
    fun `savings interest parses as income`() {
        val sms = "M-Shwari interest of KSh12.50 has been credited to your savings."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(12.5, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Savings", tx.category)
    }

    @Test
    fun `failed transactions never parse`() {
        assertNull(MpesaParser.parseMessage("QWERTY1234 Transaction failed. You have insufficient funds to send KSh500.00."))
        assertNull(MpesaParser.parseMessage("Sorry, your request for KSh100.00 was unsuccessful. Please try again."))
        assertNull(MpesaParser.parseMessage("Transaction cancelled. KSh250.00 not sent."))
    }

    @Test
    fun `generic confirmed received types as income`() {
        val sms = "QWERTY1234 Confirmed. KSh1,200.00 received from HELB upkeep on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1200.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertTrue(tx.confidenceScore < 0.75f)
    }

    @Test
    fun `generic confirmed transfer types as transfer`() {
        val sms = "QWERTY1234 Confirmed. KSh2,000.00 transferred to KCB account on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(2000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.TRANSFER, tx.type)
    }

    @Test
    fun `generic confirmed paid types as expense`() {
        val sms = "QWERTY1234 Confirmed. KSh350.00 paid to Quickmart Lavington on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(350.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun `new keyword inference covers loans insurance and fuel`() {
        assertEquals("Debt", MpesaParser.inferCategory("Tala Kenya", TransactionType.EXPENSE))
        assertEquals("Savings", MpesaParser.inferCategory("Stima Sacco", TransactionType.EXPENSE))
        assertEquals("Health", MpesaParser.inferCategory("SHIF contribution", TransactionType.EXPENSE))
        assertEquals("Kujibamba", MpesaParser.inferCategory("Netflix subscription", TransactionType.EXPENSE))
        assertEquals("Transport", MpesaParser.inferCategory("Shell fuel", TransactionType.EXPENSE))
        assertEquals("School", MpesaParser.inferCategory("exam fee", TransactionType.EXPENSE))
        assertEquals("Other", MpesaParser.inferCategory("Coffee house", TransactionType.EXPENSE))
    }

    @Test
    fun `salary credit without bank name parses as income`() {
        val sms = "Your salary of KES 45,000.00 has been processed."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(45000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
    }

    @Test
    fun `standing order parses as expense with payee`() {
        val sms = "Your standing order of KSh 2,500.00 to Zuku has been effected."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(2500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertTrue(tx.merchant.contains("Zuku"))
    }

    @Test
    fun `cleared cheque parses as income`() {
        val sms = "Cheque no. 123456 of KES 15,000.00 cleared."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(15000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
    }

    @Test
    fun `bare atm cash out parses as transfer`() {
        val sms = "ATM withdrawal of KSh 5,000.00 at Kenyatta Ave."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(5000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.TRANSFER, tx.type)
    }

    @Test
    fun `card pos purchase parses merchant as expense`() {
        val sms = "POS purchase of KES 1,200.00 at Naivas Westlands."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1200.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertTrue(tx.merchant.contains("Naivas"))
        assertEquals("Shopping", tx.category)
    }

    @Test
    fun `okoa without ksh marker parses`() {
        val sms = "Enjoy! Okoa 50 bob advanced to your line."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(50.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun `loan offers without movement never parse`() {
        assertNull(MpesaParser.parseMessage("You are eligible for a Tala loan of up to KES 30,000. Apply now!"))
        assertNull(MpesaParser.parseMessage("Your loan repayment of KSh 750 is due tomorrow. Pay via M-PESA."))
    }
}
