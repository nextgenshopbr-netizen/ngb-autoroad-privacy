package com.ngbautoroad.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class EarningDao_Impl implements EarningDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<EarningEntity> __insertionAdapterOfEarningEntity;

  private final EntityDeletionOrUpdateAdapter<EarningEntity> __deletionAdapterOfEarningEntity;

  private final EntityDeletionOrUpdateAdapter<EarningEntity> __updateAdapterOfEarningEntity;

  public EarningDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfEarningEntity = new EntityInsertionAdapter<EarningEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `earnings` (`id`,`platform`,`amount`,`tips`,`bonus`,`distance`,`duration`,`ridesCount`,`date`,`description`,`period`,`isAutoImported`,`rideHistoryId`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EarningEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getPlatform());
        statement.bindDouble(3, entity.getAmount());
        statement.bindDouble(4, entity.getTips());
        statement.bindDouble(5, entity.getBonus());
        statement.bindDouble(6, entity.getDistance());
        statement.bindLong(7, entity.getDuration());
        statement.bindLong(8, entity.getRidesCount());
        statement.bindLong(9, entity.getDate());
        statement.bindString(10, entity.getDescription());
        statement.bindString(11, entity.getPeriod());
        final int _tmp = entity.isAutoImported() ? 1 : 0;
        statement.bindLong(12, _tmp);
        statement.bindLong(13, entity.getRideHistoryId());
      }
    };
    this.__deletionAdapterOfEarningEntity = new EntityDeletionOrUpdateAdapter<EarningEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `earnings` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EarningEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfEarningEntity = new EntityDeletionOrUpdateAdapter<EarningEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `earnings` SET `id` = ?,`platform` = ?,`amount` = ?,`tips` = ?,`bonus` = ?,`distance` = ?,`duration` = ?,`ridesCount` = ?,`date` = ?,`description` = ?,`period` = ?,`isAutoImported` = ?,`rideHistoryId` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EarningEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getPlatform());
        statement.bindDouble(3, entity.getAmount());
        statement.bindDouble(4, entity.getTips());
        statement.bindDouble(5, entity.getBonus());
        statement.bindDouble(6, entity.getDistance());
        statement.bindLong(7, entity.getDuration());
        statement.bindLong(8, entity.getRidesCount());
        statement.bindLong(9, entity.getDate());
        statement.bindString(10, entity.getDescription());
        statement.bindString(11, entity.getPeriod());
        final int _tmp = entity.isAutoImported() ? 1 : 0;
        statement.bindLong(12, _tmp);
        statement.bindLong(13, entity.getRideHistoryId());
        statement.bindLong(14, entity.getId());
      }
    };
  }

  @Override
  public Object insert(final EarningEntity earning, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfEarningEntity.insertAndReturnId(earning);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final EarningEntity earning, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfEarningEntity.handle(earning);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final EarningEntity earning, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfEarningEntity.handle(earning);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<EarningEntity>> getAllEarnings() {
    final String _sql = "SELECT * FROM earnings ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"earnings"}, new Callable<List<EarningEntity>>() {
      @Override
      @NonNull
      public List<EarningEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfTips = CursorUtil.getColumnIndexOrThrow(_cursor, "tips");
          final int _cursorIndexOfBonus = CursorUtil.getColumnIndexOrThrow(_cursor, "bonus");
          final int _cursorIndexOfDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "distance");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfRidesCount = CursorUtil.getColumnIndexOrThrow(_cursor, "ridesCount");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "period");
          final int _cursorIndexOfIsAutoImported = CursorUtil.getColumnIndexOrThrow(_cursor, "isAutoImported");
          final int _cursorIndexOfRideHistoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "rideHistoryId");
          final List<EarningEntity> _result = new ArrayList<EarningEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EarningEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPlatform;
            _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final double _tmpTips;
            _tmpTips = _cursor.getDouble(_cursorIndexOfTips);
            final double _tmpBonus;
            _tmpBonus = _cursor.getDouble(_cursorIndexOfBonus);
            final double _tmpDistance;
            _tmpDistance = _cursor.getDouble(_cursorIndexOfDistance);
            final int _tmpDuration;
            _tmpDuration = _cursor.getInt(_cursorIndexOfDuration);
            final int _tmpRidesCount;
            _tmpRidesCount = _cursor.getInt(_cursorIndexOfRidesCount);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpPeriod;
            _tmpPeriod = _cursor.getString(_cursorIndexOfPeriod);
            final boolean _tmpIsAutoImported;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsAutoImported);
            _tmpIsAutoImported = _tmp != 0;
            final long _tmpRideHistoryId;
            _tmpRideHistoryId = _cursor.getLong(_cursorIndexOfRideHistoryId);
            _item = new EarningEntity(_tmpId,_tmpPlatform,_tmpAmount,_tmpTips,_tmpBonus,_tmpDistance,_tmpDuration,_tmpRidesCount,_tmpDate,_tmpDescription,_tmpPeriod,_tmpIsAutoImported,_tmpRideHistoryId);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<EarningEntity>> getEarningsByPeriod(final long startDate, final long endDate) {
    final String _sql = "SELECT * FROM earnings WHERE date >= ? AND date <= ? ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startDate);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endDate);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"earnings"}, new Callable<List<EarningEntity>>() {
      @Override
      @NonNull
      public List<EarningEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfTips = CursorUtil.getColumnIndexOrThrow(_cursor, "tips");
          final int _cursorIndexOfBonus = CursorUtil.getColumnIndexOrThrow(_cursor, "bonus");
          final int _cursorIndexOfDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "distance");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfRidesCount = CursorUtil.getColumnIndexOrThrow(_cursor, "ridesCount");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "period");
          final int _cursorIndexOfIsAutoImported = CursorUtil.getColumnIndexOrThrow(_cursor, "isAutoImported");
          final int _cursorIndexOfRideHistoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "rideHistoryId");
          final List<EarningEntity> _result = new ArrayList<EarningEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EarningEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPlatform;
            _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final double _tmpTips;
            _tmpTips = _cursor.getDouble(_cursorIndexOfTips);
            final double _tmpBonus;
            _tmpBonus = _cursor.getDouble(_cursorIndexOfBonus);
            final double _tmpDistance;
            _tmpDistance = _cursor.getDouble(_cursorIndexOfDistance);
            final int _tmpDuration;
            _tmpDuration = _cursor.getInt(_cursorIndexOfDuration);
            final int _tmpRidesCount;
            _tmpRidesCount = _cursor.getInt(_cursorIndexOfRidesCount);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpPeriod;
            _tmpPeriod = _cursor.getString(_cursorIndexOfPeriod);
            final boolean _tmpIsAutoImported;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsAutoImported);
            _tmpIsAutoImported = _tmp != 0;
            final long _tmpRideHistoryId;
            _tmpRideHistoryId = _cursor.getLong(_cursorIndexOfRideHistoryId);
            _item = new EarningEntity(_tmpId,_tmpPlatform,_tmpAmount,_tmpTips,_tmpBonus,_tmpDistance,_tmpDuration,_tmpRidesCount,_tmpDate,_tmpDescription,_tmpPeriod,_tmpIsAutoImported,_tmpRideHistoryId);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<EarningEntity>> getEarningsByPlatform(final String platform) {
    final String _sql = "SELECT * FROM earnings WHERE platform = ? ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, platform);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"earnings"}, new Callable<List<EarningEntity>>() {
      @Override
      @NonNull
      public List<EarningEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfTips = CursorUtil.getColumnIndexOrThrow(_cursor, "tips");
          final int _cursorIndexOfBonus = CursorUtil.getColumnIndexOrThrow(_cursor, "bonus");
          final int _cursorIndexOfDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "distance");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfRidesCount = CursorUtil.getColumnIndexOrThrow(_cursor, "ridesCount");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "period");
          final int _cursorIndexOfIsAutoImported = CursorUtil.getColumnIndexOrThrow(_cursor, "isAutoImported");
          final int _cursorIndexOfRideHistoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "rideHistoryId");
          final List<EarningEntity> _result = new ArrayList<EarningEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EarningEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPlatform;
            _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final double _tmpTips;
            _tmpTips = _cursor.getDouble(_cursorIndexOfTips);
            final double _tmpBonus;
            _tmpBonus = _cursor.getDouble(_cursorIndexOfBonus);
            final double _tmpDistance;
            _tmpDistance = _cursor.getDouble(_cursorIndexOfDistance);
            final int _tmpDuration;
            _tmpDuration = _cursor.getInt(_cursorIndexOfDuration);
            final int _tmpRidesCount;
            _tmpRidesCount = _cursor.getInt(_cursorIndexOfRidesCount);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpPeriod;
            _tmpPeriod = _cursor.getString(_cursorIndexOfPeriod);
            final boolean _tmpIsAutoImported;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsAutoImported);
            _tmpIsAutoImported = _tmp != 0;
            final long _tmpRideHistoryId;
            _tmpRideHistoryId = _cursor.getLong(_cursorIndexOfRideHistoryId);
            _item = new EarningEntity(_tmpId,_tmpPlatform,_tmpAmount,_tmpTips,_tmpBonus,_tmpDistance,_tmpDuration,_tmpRidesCount,_tmpDate,_tmpDescription,_tmpPeriod,_tmpIsAutoImported,_tmpRideHistoryId);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Double> getTotalEarnings(final long startDate, final long endDate) {
    final String _sql = "SELECT SUM(amount + tips + bonus) FROM earnings WHERE date >= ? AND date <= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startDate);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endDate);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"earnings"}, new Callable<Double>() {
      @Override
      @Nullable
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final Double _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getDouble(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getTotalEarningsSync(final long startDate, final long endDate,
      final Continuation<? super Double> $completion) {
    final String _sql = "SELECT SUM(amount + tips + bonus) FROM earnings WHERE date >= ? AND date <= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startDate);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endDate);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Double>() {
      @Override
      @Nullable
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final Double _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getDouble(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<Double> getTotalDistance(final long startDate, final long endDate) {
    final String _sql = "SELECT SUM(distance) FROM earnings WHERE date >= ? AND date <= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startDate);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endDate);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"earnings"}, new Callable<Double>() {
      @Override
      @Nullable
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final Double _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getDouble(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Integer> getTotalDuration(final long startDate, final long endDate) {
    final String _sql = "SELECT SUM(duration) FROM earnings WHERE date >= ? AND date <= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startDate);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endDate);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"earnings"}, new Callable<Integer>() {
      @Override
      @Nullable
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Integer> getTotalRides(final long startDate, final long endDate) {
    final String _sql = "SELECT SUM(ridesCount) FROM earnings WHERE date >= ? AND date <= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startDate);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endDate);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"earnings"}, new Callable<Integer>() {
      @Override
      @Nullable
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getTotalRidesSync(final long startDate, final long endDate,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT SUM(ridesCount) FROM earnings WHERE date >= ? AND date <= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startDate);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endDate);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @Nullable
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getById(final long id, final Continuation<? super EarningEntity> $completion) {
    final String _sql = "SELECT * FROM earnings WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<EarningEntity>() {
      @Override
      @Nullable
      public EarningEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfTips = CursorUtil.getColumnIndexOrThrow(_cursor, "tips");
          final int _cursorIndexOfBonus = CursorUtil.getColumnIndexOrThrow(_cursor, "bonus");
          final int _cursorIndexOfDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "distance");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfRidesCount = CursorUtil.getColumnIndexOrThrow(_cursor, "ridesCount");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "period");
          final int _cursorIndexOfIsAutoImported = CursorUtil.getColumnIndexOrThrow(_cursor, "isAutoImported");
          final int _cursorIndexOfRideHistoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "rideHistoryId");
          final EarningEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPlatform;
            _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final double _tmpTips;
            _tmpTips = _cursor.getDouble(_cursorIndexOfTips);
            final double _tmpBonus;
            _tmpBonus = _cursor.getDouble(_cursorIndexOfBonus);
            final double _tmpDistance;
            _tmpDistance = _cursor.getDouble(_cursorIndexOfDistance);
            final int _tmpDuration;
            _tmpDuration = _cursor.getInt(_cursorIndexOfDuration);
            final int _tmpRidesCount;
            _tmpRidesCount = _cursor.getInt(_cursorIndexOfRidesCount);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpPeriod;
            _tmpPeriod = _cursor.getString(_cursorIndexOfPeriod);
            final boolean _tmpIsAutoImported;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsAutoImported);
            _tmpIsAutoImported = _tmp != 0;
            final long _tmpRideHistoryId;
            _tmpRideHistoryId = _cursor.getLong(_cursorIndexOfRideHistoryId);
            _result = new EarningEntity(_tmpId,_tmpPlatform,_tmpAmount,_tmpTips,_tmpBonus,_tmpDistance,_tmpDuration,_tmpRidesCount,_tmpDate,_tmpDescription,_tmpPeriod,_tmpIsAutoImported,_tmpRideHistoryId);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getEarningsByPlatformSummary(final long startDate, final long endDate,
      final Continuation<? super List<PlatformSummary>> $completion) {
    final String _sql = "SELECT platform, SUM(amount + tips + bonus) as total, SUM(ridesCount) as rides, SUM(distance) as km FROM earnings WHERE date >= ? AND date <= ? GROUP BY platform ORDER BY total DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startDate);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endDate);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PlatformSummary>>() {
      @Override
      @NonNull
      public List<PlatformSummary> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPlatform = 0;
          final int _cursorIndexOfTotal = 1;
          final int _cursorIndexOfRides = 2;
          final int _cursorIndexOfKm = 3;
          final List<PlatformSummary> _result = new ArrayList<PlatformSummary>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PlatformSummary _item;
            final String _tmpPlatform;
            _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            final double _tmpTotal;
            _tmpTotal = _cursor.getDouble(_cursorIndexOfTotal);
            final int _tmpRides;
            _tmpRides = _cursor.getInt(_cursorIndexOfRides);
            final double _tmpKm;
            _tmpKm = _cursor.getDouble(_cursorIndexOfKm);
            _item = new PlatformSummary(_tmpPlatform,_tmpTotal,_tmpRides,_tmpKm);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object countAutoImportedByRideId(final long rideId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM earnings WHERE rideHistoryId = ? AND isAutoImported = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, rideId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getTodayEarnings(final long dayStart, final long dayEnd,
      final Continuation<? super Double> $completion) {
    final String _sql = "SELECT SUM(amount + tips + bonus) FROM earnings WHERE date >= ? AND date <= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, dayStart);
    _argIndex = 2;
    _statement.bindLong(_argIndex, dayEnd);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Double>() {
      @Override
      @Nullable
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final Double _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getDouble(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
