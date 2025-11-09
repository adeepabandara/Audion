# Database Migration Fix - Version 6 → 7

**Date:** November 5, 2025  
**Issue:** Room database schema mismatch error  
**Resolution:** Added migration to handle unique constraint  
**Build Status:** ✅ **SUCCESSFUL**

---

## Problem

After implementing the clinical compliance fixes (adding unique constraint to `HearingTestResult`), the app crashed on startup with:

```
java.lang.IllegalStateException: Room cannot verify the data integrity. 
Looks like you've changed schema but forgot to update the version number.
Expected identity hash: 23d5f05f624667df8a434dd2af3b4ccf, 
found: 8ce2ed3a579359e03a53a7edbbbb0ab9
```

**Root Cause:** The `HearingTestResult` entity was modified to add a unique composite index on `(userId, earSide, frequency, hearingProfileId)`, but the database version was not incremented.

---

## Solution

### Changes Made

**File:** `AppDatabase.java`

1. **Incremented database version:** `version = 6` → `version = 7`

2. **Added Migration 6→7:**
   - Creates new table with unique constraint
   - Copies only the latest test result for each unique combination
   - Removes duplicate entries automatically
   - Drops old table and renames new table
   - Creates unique index to enforce constraint

3. **Updated migration chain:** Added `MIGRATION_6_7` to the builder

---

## Migration Logic

### Step-by-Step Process

```sql
-- Step 1: Create new table with same schema
CREATE TABLE hearing_test_results_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    userId INTEGER NOT NULL,
    earSide TEXT NOT NULL,
    frequency INTEGER NOT NULL,
    amplitudeStep INTEGER NOT NULL,
    thresholdDbHL REAL NOT NULL,
    thresholdDbSPL REAL NOT NULL,
    isReliable INTEGER NOT NULL,
    reversalCount INTEGER NOT NULL,
    reliabilityScore REAL NOT NULL,
    testTimestamp INTEGER NOT NULL,
    hearingProfileId INTEGER NOT NULL,
    FOREIGN KEY(hearingProfileId) REFERENCES hearing_profiles(id) ON DELETE CASCADE
);

-- Step 2: Copy data, keeping only latest test per unique combination
INSERT INTO hearing_test_results_new
SELECT h1.* FROM hearing_test_results h1
INNER JOIN (
    SELECT userId, earSide, frequency, hearingProfileId, MAX(id) as maxId
    FROM hearing_test_results
    GROUP BY userId, earSide, frequency, hearingProfileId
) h2 ON h1.id = h2.maxId;

-- Step 3: Drop old table
DROP TABLE hearing_test_results;

-- Step 4: Rename new table
ALTER TABLE hearing_test_results_new RENAME TO hearing_test_results;

-- Step 5: Create unique index
CREATE UNIQUE INDEX index_hearing_test_results_userId_earSide_frequency_hearingProfileId
ON hearing_test_results (userId, earSide, frequency, hearingProfileId);
```

---

## What Happens to Existing Data

### If Users Have Duplicate Test Results:

**Before Migration:**
```
id | userId | earSide | frequency | thresholdDbHL | hearingProfileId
1  | 1      | RIGHT   | 1000      | 35.0         | 1
2  | 1      | RIGHT   | 1000      | 40.0         | 1  (← duplicate)
3  | 1      | RIGHT   | 2000      | 45.0         | 1
```

**After Migration:**
```
id | userId | earSide | frequency | thresholdDbHL | hearingProfileId
2  | 1      | RIGHT   | 1000      | 40.0         | 1  (← kept latest)
3  | 1      | RIGHT   | 2000      | 45.0         | 1
```

**Logic:** The migration keeps the test result with the **highest ID** (most recent) for each unique combination of (userId, earSide, frequency, hearingProfileId).

---

## Testing the Migration

### For Fresh Installs:
- Database created at version 7
- Unique constraint enforced from the start
- No duplicates possible

### For Existing Installations:
1. App detects database version 6
2. Runs MIGRATION_6_7 automatically
3. Removes any duplicate test results
4. Enforces unique constraint going forward
5. App continues normally

---

## Verification

### Build Status: ✅ **SUCCESSFUL**

```
> Task :app:compileDebugJavaWithJavac
Note: Some input files use or override a deprecated API.

BUILD SUCCESSFUL in 1m 17s
44 actionable tasks: 19 executed, 25 up-to-date
```

### Migration Chain (Complete):
- **MIGRATION_3_4:** Adds audiometry_results and calibration_profiles tables
- **MIGRATION_4_5:** Adds per-frequency MCL/UCL JSON fields
- **MIGRATION_5_6:** Adds clinical fields (thresholdDbHL, reliability metrics)
- **MIGRATION_6_7:** Adds unique constraint to prevent duplicates ⬅️ **NEW**

---

## Impact on Users

### Positive:
- ✅ No more duplicate test results
- ✅ Cleaner database
- ✅ Latest test results always displayed
- ✅ Automatic cleanup of old duplicates
- ✅ Seamless migration (happens automatically)

### Important Notes:
- **Data Loss:** Users with duplicate tests will lose older duplicates
- **Safety:** Always keeps the most recent test (highest ID)
- **Recommendation:** Test thoroughly before deploying to production

---

## Rollback Strategy

If migration fails or causes issues:

### Option 1: Force Database Rebuild (Dev Only)
```java
// In AppDatabase.getInstance():
.fallbackToDestructiveMigration()  // WARNING: Deletes all data
```

### Option 2: Revert to Version 6
```java
version = 6,  // Revert to previous version
// Remove MIGRATION_6_7 from addMigrations()
```

### Option 3: Manual Data Export (Production)
```sql
-- Export before migration
SELECT * FROM hearing_test_results 
INTO OUTFILE '/sdcard/backup.csv';
```

---

## Future Considerations

### Preventing Future Schema Mismatches:

1. **Always increment version** when changing entity annotations
2. **Write migration immediately** after schema changes
3. **Test migrations** with existing data
4. **Export schema** for documentation: `exportSchema = true`
5. **Use Room's schema validation** in tests

### Schema Changes That Require Migration:
- ✅ Adding/removing columns
- ✅ Adding/removing indices
- ✅ Changing column types
- ✅ Adding/removing unique constraints
- ✅ Changing foreign key relationships

---

## Summary

**Problem Resolved:** ✅  
**Database Version:** 6 → 7  
**Migration Added:** MIGRATION_6_7  
**Duplicate Handling:** Automatic (keeps latest)  
**Build Status:** ✅ SUCCESSFUL  
**Ready for Testing:** ✅ YES  

The app will now migrate existing databases automatically when users update, removing any duplicate test results and enforcing the unique constraint going forward.

---

**Next Steps:**
1. Test on device with existing database
2. Verify migration runs successfully
3. Confirm no duplicates after migration
4. Verify new test results respect unique constraint
5. Deploy to production

