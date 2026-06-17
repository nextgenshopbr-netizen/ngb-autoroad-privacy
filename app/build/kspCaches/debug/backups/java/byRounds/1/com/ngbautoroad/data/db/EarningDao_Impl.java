package com.ngbautoroad.data.db;

import android.database.Cursor;
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
        return "INSERT OR ABORT INTO `earnings` (`id`,`platform`,`amount`,`tips`,`bonus`,`distance`,`duration`,`ridesCount`,`date`,`description`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
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
        return "UPDATE OR ABORT `earnings` SET `id` = ?,`platform` = ?,`amount` = ?,`tips` = ?,`bonus` = ?,`distance` = ?,`duration` = ?,`ridesCount` = ?,`date` = ?,`description` = ? WHERE `id` = ?";
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
        statement.bindLong(11, entity.getId());
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
            _item = new EarningEntity(_tmpId,_tmpPlatform,_tmpAmount,_tmpTips,_tmpBonus,_tmpDistance,_tmpDuration,_tmpRidesCount,_tmpDate,_tmpDescription);
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
            _item = new EarningEntity(_tmpId,_tmpPlatform,_tmpAmount,_tmpTips,_tmpBonus,_tmpDistance,_tmpDuration,_tmpRidesCount,_tmpDate,_tmpDescription);
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
            _item = new EarningEntity(_tmpId,_tmpPlatform,_tmpAmount,_tmpTips,_tmpBonus,_tmpDistance,_tmpDuration,_tmpRidesCount,_tmpDate,_tmpDescription);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
