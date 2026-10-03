package com.pesaflow.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters
import com.pesaflow.app.data.context.ContextFact
import com.pesaflow.app.data.income.IncomeSource
import com.pesaflow.app.data.models.*
import com.pesaflow.app.data.places.Place


@Database(
    entities = [Transaction::class, PendingTransaction::class, Budget::class, SavingsGoal::class, UniversityProfile::class, Bill::class, Debt::class, MealItem::class, ChamaGroup::class, Belonging::class, KitchenStock::class, UserRhythm::class, MoneyAccount::class, IncomeSource::class, FinancialProfile::class, ContextFact::class, Place::class],
    version = 25,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun universityProfileDao(): UniversityProfileDao
    abstract fun billDao(): BillDao
    abstract fun debtDao(): DebtDao
    abstract fun mealDao(): MealDao
    abstract fun chamaDao(): ChamaDao
    abstract fun belongingDao(): BelongingDao
    abstract fun kitchenStockDao(): KitchenStockDao
    abstract fun userRhythmDao(): UserRhythmDao
    abstract fun moneyAccountDao(): MoneyAccountDao
    abstract fun incomeSourceDao(): IncomeSourceDao
    abstract fun financialProfileDao(): FinancialProfileDao
    abstract fun userContextDao(): UserContextDao
    abstract fun placeDao(): PlaceDao


    companion object {
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kitchen_stock ADD COLUMN expiryTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE kitchen_stock ADD COLUMN eatByDays INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debts ADD COLUMN direction TEXT NOT NULL DEFAULT 'THEY_OWE'")
            }
        }
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isSample INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN batchId TEXT")
            }
        }
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS user_rhythms (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, category TEXT NOT NULL, confidence REAL NOT NULL, hint TEXT NOT NULL, dayOfMonth INTEGER NOT NULL, amount REAL NOT NULL, sourceCode TEXT NOT NULL, confirmed INTEGER NOT NULL, dismissed INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
            }
        }
        // Bill paybills: which M-Pesa number clears it (watchtower directory).
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN paybill TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE university_profiles ADD COLUMN programme TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE university_profiles ADD COLUMN yearOfStudy TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN paidBy TEXT NOT NULL DEFAULT 'ME'")
            }
        }
        // Foodstuff link: ledger rows can point at the shelf row they stocked.
        // Nullable with no backfill — old rows simply have no link.
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN stockId TEXT")
            }
        }
        // Milestone: M-Pesa codes become unique. Pre-existing duplicates from
        // the old approve race are collapsed (earliest kept) before the index
        // is created, so no upgrade can fail on real user data.
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM transactions WHERE sourceTransactionId IS NOT NULL AND id NOT IN (SELECT MIN(id) FROM transactions WHERE sourceTransactionId IS NOT NULL GROUP BY sourceTransactionId)")
                db.execSQL("DELETE FROM pending_transactions WHERE sourceTransactionId IS NOT NULL AND id NOT IN (SELECT MIN(id) FROM pending_transactions WHERE sourceTransactionId IS NOT NULL GROUP BY sourceTransactionId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_sourceTransactionId ON transactions(sourceTransactionId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pending_transactions_sourceTransactionId ON pending_transactions(sourceTransactionId)")
            }
        }
        // Older onboarding incorrectly seeded expected sponsor upkeep as
        // opening cash. Remove only those precisely identified phantom rows.
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM transactions WHERE type = 'INCOME' AND isOpening = 1 " +
                        "AND merchant = 'Monthly upkeep' " +
                        "AND description = 'Home/sponsor monthly upkeep, in hand'"
                )
            }
        }
        // Phase 8 places catalogue: user-entered local knowledge first.
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS places (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL DEFAULT 'FOOD_OUTLET', area TEXT NOT NULL DEFAULT '', priceMin REAL NOT NULL DEFAULT 0.0, priceMax REAL NOT NULL DEFAULT 0.0, note TEXT NOT NULL DEFAULT '', source TEXT NOT NULL DEFAULT 'USER_ENTERED', verifiedAt INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL DEFAULT 0)")
            }
        }
        // Phase 8 user context: explicit fact graph with provenance.
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS user_context (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL, source TEXT NOT NULL DEFAULT 'USER_ENTERED', confidence REAL NOT NULL DEFAULT 1.0, createdAt INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL DEFAULT 0, expiresAt INTEGER NOT NULL DEFAULT 0, userConfirmed INTEGER NOT NULL DEFAULT 0)")
            }
        }
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS financial_profile (id TEXT NOT NULL PRIMARY KEY, housing TEXT NOT NULL, commute TEXT NOT NULL, food TEXT NOT NULL, household TEXT NOT NULL, incomeStability TEXT NOT NULL, incomeKindsCsv TEXT NOT NULL, academic TEXT NOT NULL, debtLevel TEXT NOT NULL, savingsPressure TEXT NOT NULL, risk TEXT NOT NULL, roommates INTEGER NOT NULL, rentShare REAL NOT NULL, utilityShare REAL NOT NULL, commuteDays INTEGER NOT NULL, cookingDays INTEGER NOT NULL, dependants INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            }
        }
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS income_sources (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, label TEXT NOT NULL, bank TEXT NOT NULL, expectedAmount REAL NOT NULL, frequency TEXT NOT NULL, dayOfMonth INTEGER NOT NULL, autoTrack INTEGER NOT NULL, useInBudget INTEGER NOT NULL)")
            }
        }
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN transferSide TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bills ADD COLUMN linkedPaymentId TEXT")
                db.execSQL("ALTER TABLE bills ADD COLUMN amountRemaining REAL NOT NULL DEFAULT 0")
                db.execSQL("UPDATE bills SET amountRemaining = amount")
            }
        }
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS money_accounts (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, label TEXT NOT NULL, openingMinorUnits INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("ALTER TABLE transactions ADD COLUMN accountKind TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN transferGroupId TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN isOpening INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE transactions SET accountKind = CASE paymentMethod WHEN 'MPESA' THEN 'M_PESA' WHEN 'CASH' THEN 'CASH' WHEN 'BANK_TRANSFER' THEN 'BANK' WHEN 'AIRTIME' THEN 'M_PESA' ELSE 'OTHER' END WHERE accountKind = ''")
                db.execSQL("UPDATE transactions SET accountKind = 'ZIIDI' WHERE accountKind != 'ZIIDI' AND merchant LIKE '%ziidi%' AND (type = 'SAVING' OR type = 'INCOME')")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null


        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pesaflow_secure_db"
                ).addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25).fallbackToDestructiveMigrationOnDowngrade().build()
                INSTANCE = instance
                instance
            }
        }
    }
}