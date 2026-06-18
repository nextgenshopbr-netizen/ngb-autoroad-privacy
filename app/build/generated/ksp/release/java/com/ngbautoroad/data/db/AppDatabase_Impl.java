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
public final class AppDatabase_Impl extends AppDatabase {
  private volatile RideHistoryDao _rideHistoryDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(3) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `ride_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `platform` TEXT NOT NULL, `rideValue` REAL NOT NULL, `rideDuration` REAL NOT NULL, `pickupDistance` REAL NOT NULL, `dropoffDistance` REAL NOT NULL, `passengerRating` REAL NOT NULL, `intermediateStops` INTEGER NOT NULL, `pickupNeighborhood` TEXT NOT NULL, `dropoffNeighborhood` TEXT NOT NULL, `score` REAL NOT NULL, `status` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `scoreBreakdown` TEXT NOT NULL, `criteriaUsed` INTEGER NOT NULL, `totalCriteria` INTEGER NOT NULL, `hasViolations` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '71679b20670b4e27f78d699438700ba3')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `ride_history`");
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
        final HashMap<String, TableInfo.Column> _columnsRideHistory = new HashMap<String, TableInfo.Column>(17);
        _columnsRideHistory.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("platform", new TableInfo.Column("platform", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("rideValue", new TableInfo.Column("rideValue", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("rideDuration", new TableInfo.Column("rideDuration", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("pickupDistance", new TableInfo.Column("pickupDistance", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("dropoffDistance", new TableInfo.Column("dropoffDistance", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("passengerRating", new TableInfo.Column("passengerRating", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("intermediateStops", new TableInfo.Column("intermediateStops", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("pickupNeighborhood", new TableInfo.Column("pickupNeighborhood", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("dropoffNeighborhood", new TableInfo.Column("dropoffNeighborhood", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("score", new TableInfo.Column("score", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("scoreBreakdown", new TableInfo.Column("scoreBreakdown", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("criteriaUsed", new TableInfo.Column("criteriaUsed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("totalCriteria", new TableInfo.Column("totalCriteria", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRideHistory.put("hasViolations", new TableInfo.Column("hasViolations", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRideHistory = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRideHistory = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoRideHistory = new TableInfo("ride_history", _columnsRideHistory, _foreignKeysRideHistory, _indicesRideHistory);
        final TableInfo _existingRideHistory = TableInfo.read(db, "ride_history");
        if (!_infoRideHistory.equals(_existingRideHistory)) {
          return new RoomOpenHelper.ValidationResult(false, "ride_history(com.ngbautoroad.data.db.RideHistoryEntity).\n"
                  + " Expected:\n" + _infoRideHistory + "\n"
                  + " Found:\n" + _existingRideHistory);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "71679b20670b4e27f78d699438700ba3", "540e7e93d3ccda1c729909a8997e3c29");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "ride_history");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `ride_history`");
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
    _typeConvertersMap.put(RideHistoryDao.class, RideHistoryDao_Impl.getRequiredConverters());
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
  public RideHistoryDao rideHistoryDao() {
    if (_rideHistoryDao != null) {
      return _rideHistoryDao;
    } else {
      synchronized(this) {
        if(_rideHistoryDao == null) {
          _rideHistoryDao = new RideHistoryDao_Impl(this);
        }
        return _rideHistoryDao;
      }
    }
  }
}
