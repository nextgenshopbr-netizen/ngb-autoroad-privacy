package com.ngbautoroad.data.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class FinanceDatabase_Impl extends FinanceDatabase {
  private volatile ExpenseDao _expenseDao;

  private volatile EarningDao _earningDao;

  private volatile ReminderDao _reminderDao;

  private volatile VehicleConfigDao _vehicleConfigDao;

  private volatile FinancialGoalDao _financialGoalDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(3) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `amount` REAL NOT NULL, `description` TEXT NOT NULL, `date` INTEGER NOT NULL, `isRecurring` INTEGER NOT NULL, `recurringDay` INTEGER NOT NULL, `recurringDays` TEXT NOT NULL, `recurringDuration` INTEGER NOT NULL, `recurringEndDate` INTEGER NOT NULL, `liters` REAL, `pricePerLiter` REAL, `odometer` INTEGER, `fuelType` TEXT, `parentExpenseId` INTEGER NOT NULL, `isGenerated` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `earnings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `platform` TEXT NOT NULL, `amount` REAL NOT NULL, `tips` REAL NOT NULL, `bonus` REAL NOT NULL, `distance` REAL NOT NULL, `duration` INTEGER NOT NULL, `ridesCount` INTEGER NOT NULL, `date` INTEGER NOT NULL, `description` TEXT NOT NULL, `period` TEXT NOT NULL, `isAutoImported` INTEGER NOT NULL, `rideHistoryId` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `maintenance_reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `nextDate` INTEGER NOT NULL, `nextOdometer` INTEGER NOT NULL, `intervalDays` INTEGER NOT NULL, `intervalKm` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `vehicle_config` (`id` INTEGER NOT NULL, `vehicleType` TEXT NOT NULL, `fuelType` TEXT NOT NULL, `brand` TEXT NOT NULL, `model` TEXT NOT NULL, `year` INTEGER NOT NULL, `plate` TEXT NOT NULL, `averageConsumption` REAL NOT NULL, `fuelPrice` REAL NOT NULL, `costPerKm` REAL NOT NULL, `monthlyFixedCosts` REAL NOT NULL, `isOwned` INTEGER NOT NULL, `rentalCost` REAL NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `financial_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `targetAmount` REAL NOT NULL, `currentAmount` REAL NOT NULL, `period` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'aa8a852a1ab9de2f465400286f3632cc')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `expenses`");
        db.execSQL("DROP TABLE IF EXISTS `earnings`");
        db.execSQL("DROP TABLE IF EXISTS `maintenance_reminders`");
        db.execSQL("DROP TABLE IF EXISTS `vehicle_config`");
        db.execSQL("DROP TABLE IF EXISTS `financial_goals`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsExpenses = new HashMap<String, TableInfo.Column>(16);
        _columnsExpenses.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("amount", new TableInfo.Column("amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("date", new TableInfo.Column("date", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("isRecurring", new TableInfo.Column("isRecurring", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("recurringDay", new TableInfo.Column("recurringDay", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("recurringDays", new TableInfo.Column("recurringDays", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("recurringDuration", new TableInfo.Column("recurringDuration", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("recurringEndDate", new TableInfo.Column("recurringEndDate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("liters", new TableInfo.Column("liters", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("pricePerLiter", new TableInfo.Column("pricePerLiter", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("odometer", new TableInfo.Column("odometer", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("fuelType", new TableInfo.Column("fuelType", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("parentExpenseId", new TableInfo.Column("parentExpenseId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExpenses.put("isGenerated", new TableInfo.Column("isGenerated", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysExpenses = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesExpenses = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoExpenses = new TableInfo("expenses", _columnsExpenses, _foreignKeysExpenses, _indicesExpenses);
        final TableInfo _existingExpenses = TableInfo.read(db, "expenses");
        if (!_infoExpenses.equals(_existingExpenses)) {
          return new RoomOpenHelper.ValidationResult(false, "expenses(com.ngbautoroad.data.db.ExpenseEntity).\n"
                  + " Expected:\n" + _infoExpenses + "\n"
                  + " Found:\n" + _existingExpenses);
        }
        final HashMap<String, TableInfo.Column> _columnsEarnings = new HashMap<String, TableInfo.Column>(13);
        _columnsEarnings.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEarnings.put("platform", new TableInfo.Column("platform", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEarnings.put("amount", new TableInfo.Column("amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEarnings.put("tips", new TableInfo.Column("tips", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEarnings.put("bonus", new TableInfo.Column("bonus", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEarnings.put("distance", new TableInfo.Column("distance", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEarnings.put("duration", new TableInfo.Column("duration", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEarnings.put("ridesCount", new TableInfo.Column("ridesCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEarnings.put("date", new TableInfo.Column("date", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEarnings.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEarnings.put("period", new TableInfo.Column("period", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEarnings.put("isAutoImported", new TableInfo.Column("isAutoImported", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEarnings.put("rideHistoryId", new TableInfo.Column("rideHistoryId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysEarnings = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesEarnings = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoEarnings = new TableInfo("earnings", _columnsEarnings, _foreignKeysEarnings, _indicesEarnings);
        final TableInfo _existingEarnings = TableInfo.read(db, "earnings");
        if (!_infoEarnings.equals(_existingEarnings)) {
          return new RoomOpenHelper.ValidationResult(false, "earnings(com.ngbautoroad.data.db.EarningEntity).\n"
                  + " Expected:\n" + _infoEarnings + "\n"
                  + " Found:\n" + _existingEarnings);
        }
        final HashMap<String, TableInfo.Column> _columnsMaintenanceReminders = new HashMap<String, TableInfo.Column>(8);
        _columnsMaintenanceReminders.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMaintenanceReminders.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMaintenanceReminders.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMaintenanceReminders.put("nextDate", new TableInfo.Column("nextDate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMaintenanceReminders.put("nextOdometer", new TableInfo.Column("nextOdometer", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMaintenanceReminders.put("intervalDays", new TableInfo.Column("intervalDays", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMaintenanceReminders.put("intervalKm", new TableInfo.Column("intervalKm", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMaintenanceReminders.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMaintenanceReminders = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMaintenanceReminders = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoMaintenanceReminders = new TableInfo("maintenance_reminders", _columnsMaintenanceReminders, _foreignKeysMaintenanceReminders, _indicesMaintenanceReminders);
        final TableInfo _existingMaintenanceReminders = TableInfo.read(db, "maintenance_reminders");
        if (!_infoMaintenanceReminders.equals(_existingMaintenanceReminders)) {
          return new RoomOpenHelper.ValidationResult(false, "maintenance_reminders(com.ngbautoroad.data.db.ReminderEntity).\n"
                  + " Expected:\n" + _infoMaintenanceReminders + "\n"
                  + " Found:\n" + _existingMaintenanceReminders);
        }
        final HashMap<String, TableInfo.Column> _columnsVehicleConfig = new HashMap<String, TableInfo.Column>(13);
        _columnsVehicleConfig.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVehicleConfig.put("vehicleType", new TableInfo.Column("vehicleType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVehicleConfig.put("fuelType", new TableInfo.Column("fuelType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVehicleConfig.put("brand", new TableInfo.Column("brand", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVehicleConfig.put("model", new TableInfo.Column("model", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVehicleConfig.put("year", new TableInfo.Column("year", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVehicleConfig.put("plate", new TableInfo.Column("plate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVehicleConfig.put("averageConsumption", new TableInfo.Column("averageConsumption", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVehicleConfig.put("fuelPrice", new TableInfo.Column("fuelPrice", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVehicleConfig.put("costPerKm", new TableInfo.Column("costPerKm", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVehicleConfig.put("monthlyFixedCosts", new TableInfo.Column("monthlyFixedCosts", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVehicleConfig.put("isOwned", new TableInfo.Column("isOwned", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsVehicleConfig.put("rentalCost", new TableInfo.Column("rentalCost", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysVehicleConfig = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesVehicleConfig = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoVehicleConfig = new TableInfo("vehicle_config", _columnsVehicleConfig, _foreignKeysVehicleConfig, _indicesVehicleConfig);
        final TableInfo _existingVehicleConfig = TableInfo.read(db, "vehicle_config");
        if (!_infoVehicleConfig.equals(_existingVehicleConfig)) {
          return new RoomOpenHelper.ValidationResult(false, "vehicle_config(com.ngbautoroad.data.db.VehicleConfigEntity).\n"
                  + " Expected:\n" + _infoVehicleConfig + "\n"
                  + " Found:\n" + _existingVehicleConfig);
        }
        final HashMap<String, TableInfo.Column> _columnsFinancialGoals = new HashMap<String, TableInfo.Column>(7);
        _columnsFinancialGoals.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFinancialGoals.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFinancialGoals.put("targetAmount", new TableInfo.Column("targetAmount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFinancialGoals.put("currentAmount", new TableInfo.Column("currentAmount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFinancialGoals.put("period", new TableInfo.Column("period", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFinancialGoals.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFinancialGoals.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysFinancialGoals = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesFinancialGoals = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoFinancialGoals = new TableInfo("financial_goals", _columnsFinancialGoals, _foreignKeysFinancialGoals, _indicesFinancialGoals);
        final TableInfo _existingFinancialGoals = TableInfo.read(db, "financial_goals");
        if (!_infoFinancialGoals.equals(_existingFinancialGoals)) {
          return new RoomOpenHelper.ValidationResult(false, "financial_goals(com.ngbautoroad.data.db.FinancialGoalEntity).\n"
                  + " Expected:\n" + _infoFinancialGoals + "\n"
                  + " Found:\n" + _existingFinancialGoals);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "aa8a852a1ab9de2f465400286f3632cc", "1c649a5976f7dd71ddd7800b5074210a");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "expenses","earnings","maintenance_reminders","vehicle_config","financial_goals");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `expenses`");
      _db.execSQL("DELETE FROM `earnings`");
      _db.execSQL("DELETE FROM `maintenance_reminders`");
      _db.execSQL("DELETE FROM `vehicle_config`");
      _db.execSQL("DELETE FROM `financial_goals`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(ExpenseDao.class, ExpenseDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(EarningDao.class, EarningDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ReminderDao.class, ReminderDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(VehicleConfigDao.class, VehicleConfigDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(FinancialGoalDao.class, FinancialGoalDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public ExpenseDao expenseDao() {
    if (_expenseDao != null) {
      return _expenseDao;
    } else {
      synchronized(this) {
        if(_expenseDao == null) {
          _expenseDao = new ExpenseDao_Impl(this);
        }
        return _expenseDao;
      }
    }
  }

  @Override
  public EarningDao earningDao() {
    if (_earningDao != null) {
      return _earningDao;
    } else {
      synchronized(this) {
        if(_earningDao == null) {
          _earningDao = new EarningDao_Impl(this);
        }
        return _earningDao;
      }
    }
  }

  @Override
  public ReminderDao reminderDao() {
    if (_reminderDao != null) {
      return _reminderDao;
    } else {
      synchronized(this) {
        if(_reminderDao == null) {
          _reminderDao = new ReminderDao_Impl(this);
        }
        return _reminderDao;
      }
    }
  }

  @Override
  public VehicleConfigDao vehicleConfigDao() {
    if (_vehicleConfigDao != null) {
      return _vehicleConfigDao;
    } else {
      synchronized(this) {
        if(_vehicleConfigDao == null) {
          _vehicleConfigDao = new VehicleConfigDao_Impl(this);
        }
        return _vehicleConfigDao;
      }
    }
  }

  @Override
  public FinancialGoalDao financialGoalDao() {
    if (_financialGoalDao != null) {
      return _financialGoalDao;
    } else {
      synchronized(this) {
        if(_financialGoalDao == null) {
          _financialGoalDao = new FinancialGoalDao_Impl(this);
        }
        return _financialGoalDao;
      }
    }
  }
}
