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
import androidx.room.SharedSQLiteStatement;
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
public final class RideHistoryDao_Impl implements RideHistoryDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<RideHistoryEntity> __insertionAdapterOfRideHistoryEntity;

  private final EntityDeletionOrUpdateAdapter<RideHistoryEntity> __deletionAdapterOfRideHistoryEntity;

  private final EntityDeletionOrUpdateAdapter<RideHistoryEntity> __updateAdapterOfRideHistoryEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public RideHistoryDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfRideHistoryEntity = new EntityInsertionAdapter<RideHistoryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `ride_history` (`id`,`platform`,`rideValue`,`rideDuration`,`pickupDistance`,`dropoffDistance`,`passengerRating`,`intermediateStops`,`pickupNeighborhood`,`dropoffNeighborhood`,`score`,`status`,`timestamp`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RideHistoryEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getPlatform());
        statement.bindDouble(3, entity.getRideValue());
        statement.bindDouble(4, entity.getRideDuration());
        statement.bindDouble(5, entity.getPickupDistance());
        statement.bindDouble(6, entity.getDropoffDistance());
        statement.bindDouble(7, entity.getPassengerRating());
        statement.bindLong(8, entity.getIntermediateStops());
        statement.bindString(9, entity.getPickupNeighborhood());
        statement.bindString(10, entity.getDropoffNeighborhood());
        statement.bindDouble(11, entity.getScore());
        statement.bindString(12, entity.getStatus());
        statement.bindLong(13, entity.getTimestamp());
      }
    };
    this.__deletionAdapterOfRideHistoryEntity = new EntityDeletionOrUpdateAdapter<RideHistoryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `ride_history` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RideHistoryEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfRideHistoryEntity = new EntityDeletionOrUpdateAdapter<RideHistoryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `ride_history` SET `id` = ?,`platform` = ?,`rideValue` = ?,`rideDuration` = ?,`pickupDistance` = ?,`dropoffDistance` = ?,`passengerRating` = ?,`intermediateStops` = ?,`pickupNeighborhood` = ?,`dropoffNeighborhood` = ?,`score` = ?,`status` = ?,`timestamp` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RideHistoryEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getPlatform());
        statement.bindDouble(3, entity.getRideValue());
        statement.bindDouble(4, entity.getRideDuration());
        statement.bindDouble(5, entity.getPickupDistance());
        statement.bindDouble(6, entity.getDropoffDistance());
        statement.bindDouble(7, entity.getPassengerRating());
        statement.bindLong(8, entity.getIntermediateStops());
        statement.bindString(9, entity.getPickupNeighborhood());
        statement.bindString(10, entity.getDropoffNeighborhood());
        statement.bindDouble(11, entity.getScore());
        statement.bindString(12, entity.getStatus());
        statement.bindLong(13, entity.getTimestamp());
        statement.bindLong(14, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM ride_history";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final RideHistoryEntity ride, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfRideHistoryEntity.insertAndReturnId(ride);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final RideHistoryEntity ride, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfRideHistoryEntity.handle(ride);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final RideHistoryEntity ride, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfRideHistoryEntity.handle(ride);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<RideHistoryEntity>> getAllFlow() {
    final String _sql = "SELECT * FROM ride_history ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"ride_history"}, new Callable<List<RideHistoryEntity>>() {
      @Override
      @NonNull
      public List<RideHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfRideValue = CursorUtil.getColumnIndexOrThrow(_cursor, "rideValue");
          final int _cursorIndexOfRideDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "rideDuration");
          final int _cursorIndexOfPickupDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupDistance");
          final int _cursorIndexOfDropoffDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffDistance");
          final int _cursorIndexOfPassengerRating = CursorUtil.getColumnIndexOrThrow(_cursor, "passengerRating");
          final int _cursorIndexOfIntermediateStops = CursorUtil.getColumnIndexOrThrow(_cursor, "intermediateStops");
          final int _cursorIndexOfPickupNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupNeighborhood");
          final int _cursorIndexOfDropoffNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffNeighborhood");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<RideHistoryEntity> _result = new ArrayList<RideHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RideHistoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPlatform;
            _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            final double _tmpRideValue;
            _tmpRideValue = _cursor.getDouble(_cursorIndexOfRideValue);
            final double _tmpRideDuration;
            _tmpRideDuration = _cursor.getDouble(_cursorIndexOfRideDuration);
            final double _tmpPickupDistance;
            _tmpPickupDistance = _cursor.getDouble(_cursorIndexOfPickupDistance);
            final double _tmpDropoffDistance;
            _tmpDropoffDistance = _cursor.getDouble(_cursorIndexOfDropoffDistance);
            final double _tmpPassengerRating;
            _tmpPassengerRating = _cursor.getDouble(_cursorIndexOfPassengerRating);
            final int _tmpIntermediateStops;
            _tmpIntermediateStops = _cursor.getInt(_cursorIndexOfIntermediateStops);
            final String _tmpPickupNeighborhood;
            _tmpPickupNeighborhood = _cursor.getString(_cursorIndexOfPickupNeighborhood);
            final String _tmpDropoffNeighborhood;
            _tmpDropoffNeighborhood = _cursor.getString(_cursorIndexOfDropoffNeighborhood);
            final double _tmpScore;
            _tmpScore = _cursor.getDouble(_cursorIndexOfScore);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new RideHistoryEntity(_tmpId,_tmpPlatform,_tmpRideValue,_tmpRideDuration,_tmpPickupDistance,_tmpDropoffDistance,_tmpPassengerRating,_tmpIntermediateStops,_tmpPickupNeighborhood,_tmpDropoffNeighborhood,_tmpScore,_tmpStatus,_tmpTimestamp);
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
  public Object getAll(final Continuation<? super List<RideHistoryEntity>> $completion) {
    final String _sql = "SELECT * FROM ride_history ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<RideHistoryEntity>>() {
      @Override
      @NonNull
      public List<RideHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfRideValue = CursorUtil.getColumnIndexOrThrow(_cursor, "rideValue");
          final int _cursorIndexOfRideDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "rideDuration");
          final int _cursorIndexOfPickupDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupDistance");
          final int _cursorIndexOfDropoffDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffDistance");
          final int _cursorIndexOfPassengerRating = CursorUtil.getColumnIndexOrThrow(_cursor, "passengerRating");
          final int _cursorIndexOfIntermediateStops = CursorUtil.getColumnIndexOrThrow(_cursor, "intermediateStops");
          final int _cursorIndexOfPickupNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupNeighborhood");
          final int _cursorIndexOfDropoffNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffNeighborhood");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<RideHistoryEntity> _result = new ArrayList<RideHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RideHistoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPlatform;
            _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            final double _tmpRideValue;
            _tmpRideValue = _cursor.getDouble(_cursorIndexOfRideValue);
            final double _tmpRideDuration;
            _tmpRideDuration = _cursor.getDouble(_cursorIndexOfRideDuration);
            final double _tmpPickupDistance;
            _tmpPickupDistance = _cursor.getDouble(_cursorIndexOfPickupDistance);
            final double _tmpDropoffDistance;
            _tmpDropoffDistance = _cursor.getDouble(_cursorIndexOfDropoffDistance);
            final double _tmpPassengerRating;
            _tmpPassengerRating = _cursor.getDouble(_cursorIndexOfPassengerRating);
            final int _tmpIntermediateStops;
            _tmpIntermediateStops = _cursor.getInt(_cursorIndexOfIntermediateStops);
            final String _tmpPickupNeighborhood;
            _tmpPickupNeighborhood = _cursor.getString(_cursorIndexOfPickupNeighborhood);
            final String _tmpDropoffNeighborhood;
            _tmpDropoffNeighborhood = _cursor.getString(_cursorIndexOfDropoffNeighborhood);
            final double _tmpScore;
            _tmpScore = _cursor.getDouble(_cursorIndexOfScore);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new RideHistoryEntity(_tmpId,_tmpPlatform,_tmpRideValue,_tmpRideDuration,_tmpPickupDistance,_tmpDropoffDistance,_tmpPassengerRating,_tmpIntermediateStops,_tmpPickupNeighborhood,_tmpDropoffNeighborhood,_tmpScore,_tmpStatus,_tmpTimestamp);
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
  public Object getRecent(final int limit,
      final Continuation<? super List<RideHistoryEntity>> $completion) {
    final String _sql = "SELECT * FROM ride_history ORDER BY timestamp DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<RideHistoryEntity>>() {
      @Override
      @NonNull
      public List<RideHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfRideValue = CursorUtil.getColumnIndexOrThrow(_cursor, "rideValue");
          final int _cursorIndexOfRideDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "rideDuration");
          final int _cursorIndexOfPickupDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupDistance");
          final int _cursorIndexOfDropoffDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffDistance");
          final int _cursorIndexOfPassengerRating = CursorUtil.getColumnIndexOrThrow(_cursor, "passengerRating");
          final int _cursorIndexOfIntermediateStops = CursorUtil.getColumnIndexOrThrow(_cursor, "intermediateStops");
          final int _cursorIndexOfPickupNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupNeighborhood");
          final int _cursorIndexOfDropoffNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffNeighborhood");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<RideHistoryEntity> _result = new ArrayList<RideHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RideHistoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPlatform;
            _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            final double _tmpRideValue;
            _tmpRideValue = _cursor.getDouble(_cursorIndexOfRideValue);
            final double _tmpRideDuration;
            _tmpRideDuration = _cursor.getDouble(_cursorIndexOfRideDuration);
            final double _tmpPickupDistance;
            _tmpPickupDistance = _cursor.getDouble(_cursorIndexOfPickupDistance);
            final double _tmpDropoffDistance;
            _tmpDropoffDistance = _cursor.getDouble(_cursorIndexOfDropoffDistance);
            final double _tmpPassengerRating;
            _tmpPassengerRating = _cursor.getDouble(_cursorIndexOfPassengerRating);
            final int _tmpIntermediateStops;
            _tmpIntermediateStops = _cursor.getInt(_cursorIndexOfIntermediateStops);
            final String _tmpPickupNeighborhood;
            _tmpPickupNeighborhood = _cursor.getString(_cursorIndexOfPickupNeighborhood);
            final String _tmpDropoffNeighborhood;
            _tmpDropoffNeighborhood = _cursor.getString(_cursorIndexOfDropoffNeighborhood);
            final double _tmpScore;
            _tmpScore = _cursor.getDouble(_cursorIndexOfScore);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new RideHistoryEntity(_tmpId,_tmpPlatform,_tmpRideValue,_tmpRideDuration,_tmpPickupDistance,_tmpDropoffDistance,_tmpPassengerRating,_tmpIntermediateStops,_tmpPickupNeighborhood,_tmpDropoffNeighborhood,_tmpScore,_tmpStatus,_tmpTimestamp);
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
  public Object getSince(final long since,
      final Continuation<? super List<RideHistoryEntity>> $completion) {
    final String _sql = "SELECT * FROM ride_history WHERE timestamp >= ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<RideHistoryEntity>>() {
      @Override
      @NonNull
      public List<RideHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfRideValue = CursorUtil.getColumnIndexOrThrow(_cursor, "rideValue");
          final int _cursorIndexOfRideDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "rideDuration");
          final int _cursorIndexOfPickupDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupDistance");
          final int _cursorIndexOfDropoffDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffDistance");
          final int _cursorIndexOfPassengerRating = CursorUtil.getColumnIndexOrThrow(_cursor, "passengerRating");
          final int _cursorIndexOfIntermediateStops = CursorUtil.getColumnIndexOrThrow(_cursor, "intermediateStops");
          final int _cursorIndexOfPickupNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupNeighborhood");
          final int _cursorIndexOfDropoffNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffNeighborhood");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<RideHistoryEntity> _result = new ArrayList<RideHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RideHistoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPlatform;
            _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            final double _tmpRideValue;
            _tmpRideValue = _cursor.getDouble(_cursorIndexOfRideValue);
            final double _tmpRideDuration;
            _tmpRideDuration = _cursor.getDouble(_cursorIndexOfRideDuration);
            final double _tmpPickupDistance;
            _tmpPickupDistance = _cursor.getDouble(_cursorIndexOfPickupDistance);
            final double _tmpDropoffDistance;
            _tmpDropoffDistance = _cursor.getDouble(_cursorIndexOfDropoffDistance);
            final double _tmpPassengerRating;
            _tmpPassengerRating = _cursor.getDouble(_cursorIndexOfPassengerRating);
            final int _tmpIntermediateStops;
            _tmpIntermediateStops = _cursor.getInt(_cursorIndexOfIntermediateStops);
            final String _tmpPickupNeighborhood;
            _tmpPickupNeighborhood = _cursor.getString(_cursorIndexOfPickupNeighborhood);
            final String _tmpDropoffNeighborhood;
            _tmpDropoffNeighborhood = _cursor.getString(_cursorIndexOfDropoffNeighborhood);
            final double _tmpScore;
            _tmpScore = _cursor.getDouble(_cursorIndexOfScore);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new RideHistoryEntity(_tmpId,_tmpPlatform,_tmpRideValue,_tmpRideDuration,_tmpPickupDistance,_tmpDropoffDistance,_tmpPassengerRating,_tmpIntermediateStops,_tmpPickupNeighborhood,_tmpDropoffNeighborhood,_tmpScore,_tmpStatus,_tmpTimestamp);
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
  public Object getSinceWithStatus(final long since, final String status,
      final Continuation<? super List<RideHistoryEntity>> $completion) {
    final String _sql = "SELECT * FROM ride_history WHERE timestamp >= ? AND status = ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    _argIndex = 2;
    _statement.bindString(_argIndex, status);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<RideHistoryEntity>>() {
      @Override
      @NonNull
      public List<RideHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfRideValue = CursorUtil.getColumnIndexOrThrow(_cursor, "rideValue");
          final int _cursorIndexOfRideDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "rideDuration");
          final int _cursorIndexOfPickupDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupDistance");
          final int _cursorIndexOfDropoffDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffDistance");
          final int _cursorIndexOfPassengerRating = CursorUtil.getColumnIndexOrThrow(_cursor, "passengerRating");
          final int _cursorIndexOfIntermediateStops = CursorUtil.getColumnIndexOrThrow(_cursor, "intermediateStops");
          final int _cursorIndexOfPickupNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupNeighborhood");
          final int _cursorIndexOfDropoffNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffNeighborhood");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<RideHistoryEntity> _result = new ArrayList<RideHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RideHistoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPlatform;
            _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            final double _tmpRideValue;
            _tmpRideValue = _cursor.getDouble(_cursorIndexOfRideValue);
            final double _tmpRideDuration;
            _tmpRideDuration = _cursor.getDouble(_cursorIndexOfRideDuration);
            final double _tmpPickupDistance;
            _tmpPickupDistance = _cursor.getDouble(_cursorIndexOfPickupDistance);
            final double _tmpDropoffDistance;
            _tmpDropoffDistance = _cursor.getDouble(_cursorIndexOfDropoffDistance);
            final double _tmpPassengerRating;
            _tmpPassengerRating = _cursor.getDouble(_cursorIndexOfPassengerRating);
            final int _tmpIntermediateStops;
            _tmpIntermediateStops = _cursor.getInt(_cursorIndexOfIntermediateStops);
            final String _tmpPickupNeighborhood;
            _tmpPickupNeighborhood = _cursor.getString(_cursorIndexOfPickupNeighborhood);
            final String _tmpDropoffNeighborhood;
            _tmpDropoffNeighborhood = _cursor.getString(_cursorIndexOfDropoffNeighborhood);
            final double _tmpScore;
            _tmpScore = _cursor.getDouble(_cursorIndexOfScore);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new RideHistoryEntity(_tmpId,_tmpPlatform,_tmpRideValue,_tmpRideDuration,_tmpPickupDistance,_tmpDropoffDistance,_tmpPassengerRating,_tmpIntermediateStops,_tmpPickupNeighborhood,_tmpDropoffNeighborhood,_tmpScore,_tmpStatus,_tmpTimestamp);
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
  public Object getByStatus(final String status,
      final Continuation<? super List<RideHistoryEntity>> $completion) {
    final String _sql = "SELECT * FROM ride_history WHERE status = ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, status);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<RideHistoryEntity>>() {
      @Override
      @NonNull
      public List<RideHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfRideValue = CursorUtil.getColumnIndexOrThrow(_cursor, "rideValue");
          final int _cursorIndexOfRideDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "rideDuration");
          final int _cursorIndexOfPickupDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupDistance");
          final int _cursorIndexOfDropoffDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffDistance");
          final int _cursorIndexOfPassengerRating = CursorUtil.getColumnIndexOrThrow(_cursor, "passengerRating");
          final int _cursorIndexOfIntermediateStops = CursorUtil.getColumnIndexOrThrow(_cursor, "intermediateStops");
          final int _cursorIndexOfPickupNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupNeighborhood");
          final int _cursorIndexOfDropoffNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffNeighborhood");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<RideHistoryEntity> _result = new ArrayList<RideHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RideHistoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPlatform;
            _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            final double _tmpRideValue;
            _tmpRideValue = _cursor.getDouble(_cursorIndexOfRideValue);
            final double _tmpRideDuration;
            _tmpRideDuration = _cursor.getDouble(_cursorIndexOfRideDuration);
            final double _tmpPickupDistance;
            _tmpPickupDistance = _cursor.getDouble(_cursorIndexOfPickupDistance);
            final double _tmpDropoffDistance;
            _tmpDropoffDistance = _cursor.getDouble(_cursorIndexOfDropoffDistance);
            final double _tmpPassengerRating;
            _tmpPassengerRating = _cursor.getDouble(_cursorIndexOfPassengerRating);
            final int _tmpIntermediateStops;
            _tmpIntermediateStops = _cursor.getInt(_cursorIndexOfIntermediateStops);
            final String _tmpPickupNeighborhood;
            _tmpPickupNeighborhood = _cursor.getString(_cursorIndexOfPickupNeighborhood);
            final String _tmpDropoffNeighborhood;
            _tmpDropoffNeighborhood = _cursor.getString(_cursorIndexOfDropoffNeighborhood);
            final double _tmpScore;
            _tmpScore = _cursor.getDouble(_cursorIndexOfScore);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new RideHistoryEntity(_tmpId,_tmpPlatform,_tmpRideValue,_tmpRideDuration,_tmpPickupDistance,_tmpDropoffDistance,_tmpPassengerRating,_tmpIntermediateStops,_tmpPickupNeighborhood,_tmpDropoffNeighborhood,_tmpScore,_tmpStatus,_tmpTimestamp);
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
  public Object getBetweenDates(final long start, final long end,
      final Continuation<? super List<RideHistoryEntity>> $completion) {
    final String _sql = "SELECT * FROM ride_history WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, start);
    _argIndex = 2;
    _statement.bindLong(_argIndex, end);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<RideHistoryEntity>>() {
      @Override
      @NonNull
      public List<RideHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfRideValue = CursorUtil.getColumnIndexOrThrow(_cursor, "rideValue");
          final int _cursorIndexOfRideDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "rideDuration");
          final int _cursorIndexOfPickupDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupDistance");
          final int _cursorIndexOfDropoffDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffDistance");
          final int _cursorIndexOfPassengerRating = CursorUtil.getColumnIndexOrThrow(_cursor, "passengerRating");
          final int _cursorIndexOfIntermediateStops = CursorUtil.getColumnIndexOrThrow(_cursor, "intermediateStops");
          final int _cursorIndexOfPickupNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupNeighborhood");
          final int _cursorIndexOfDropoffNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffNeighborhood");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<RideHistoryEntity> _result = new ArrayList<RideHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RideHistoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPlatform;
            _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            final double _tmpRideValue;
            _tmpRideValue = _cursor.getDouble(_cursorIndexOfRideValue);
            final double _tmpRideDuration;
            _tmpRideDuration = _cursor.getDouble(_cursorIndexOfRideDuration);
            final double _tmpPickupDistance;
            _tmpPickupDistance = _cursor.getDouble(_cursorIndexOfPickupDistance);
            final double _tmpDropoffDistance;
            _tmpDropoffDistance = _cursor.getDouble(_cursorIndexOfDropoffDistance);
            final double _tmpPassengerRating;
            _tmpPassengerRating = _cursor.getDouble(_cursorIndexOfPassengerRating);
            final int _tmpIntermediateStops;
            _tmpIntermediateStops = _cursor.getInt(_cursorIndexOfIntermediateStops);
            final String _tmpPickupNeighborhood;
            _tmpPickupNeighborhood = _cursor.getString(_cursorIndexOfPickupNeighborhood);
            final String _tmpDropoffNeighborhood;
            _tmpDropoffNeighborhood = _cursor.getString(_cursorIndexOfDropoffNeighborhood);
            final double _tmpScore;
            _tmpScore = _cursor.getDouble(_cursorIndexOfScore);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new RideHistoryEntity(_tmpId,_tmpPlatform,_tmpRideValue,_tmpRideDuration,_tmpPickupDistance,_tmpDropoffDistance,_tmpPassengerRating,_tmpIntermediateStops,_tmpPickupNeighborhood,_tmpDropoffNeighborhood,_tmpScore,_tmpStatus,_tmpTimestamp);
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
  public Object getByPlatform(final String platform, final int limit,
      final Continuation<? super List<RideHistoryEntity>> $completion) {
    final String _sql = "SELECT * FROM ride_history WHERE platform = ? ORDER BY timestamp DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, platform);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<RideHistoryEntity>>() {
      @Override
      @NonNull
      public List<RideHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfRideValue = CursorUtil.getColumnIndexOrThrow(_cursor, "rideValue");
          final int _cursorIndexOfRideDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "rideDuration");
          final int _cursorIndexOfPickupDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupDistance");
          final int _cursorIndexOfDropoffDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffDistance");
          final int _cursorIndexOfPassengerRating = CursorUtil.getColumnIndexOrThrow(_cursor, "passengerRating");
          final int _cursorIndexOfIntermediateStops = CursorUtil.getColumnIndexOrThrow(_cursor, "intermediateStops");
          final int _cursorIndexOfPickupNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupNeighborhood");
          final int _cursorIndexOfDropoffNeighborhood = CursorUtil.getColumnIndexOrThrow(_cursor, "dropoffNeighborhood");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<RideHistoryEntity> _result = new ArrayList<RideHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RideHistoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPlatform;
            _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            final double _tmpRideValue;
            _tmpRideValue = _cursor.getDouble(_cursorIndexOfRideValue);
            final double _tmpRideDuration;
            _tmpRideDuration = _cursor.getDouble(_cursorIndexOfRideDuration);
            final double _tmpPickupDistance;
            _tmpPickupDistance = _cursor.getDouble(_cursorIndexOfPickupDistance);
            final double _tmpDropoffDistance;
            _tmpDropoffDistance = _cursor.getDouble(_cursorIndexOfDropoffDistance);
            final double _tmpPassengerRating;
            _tmpPassengerRating = _cursor.getDouble(_cursorIndexOfPassengerRating);
            final int _tmpIntermediateStops;
            _tmpIntermediateStops = _cursor.getInt(_cursorIndexOfIntermediateStops);
            final String _tmpPickupNeighborhood;
            _tmpPickupNeighborhood = _cursor.getString(_cursorIndexOfPickupNeighborhood);
            final String _tmpDropoffNeighborhood;
            _tmpDropoffNeighborhood = _cursor.getString(_cursorIndexOfDropoffNeighborhood);
            final double _tmpScore;
            _tmpScore = _cursor.getDouble(_cursorIndexOfScore);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new RideHistoryEntity(_tmpId,_tmpPlatform,_tmpRideValue,_tmpRideDuration,_tmpPickupDistance,_tmpDropoffDistance,_tmpPassengerRating,_tmpIntermediateStops,_tmpPickupNeighborhood,_tmpDropoffNeighborhood,_tmpScore,_tmpStatus,_tmpTimestamp);
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
  public Object countSince(final long since, final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM ride_history WHERE timestamp >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
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
  public Object countSinceWithStatus(final long since, final String status,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM ride_history WHERE timestamp >= ? AND status = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    _argIndex = 2;
    _statement.bindString(_argIndex, status);
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
  public Object averageScoreSince(final long since,
      final Continuation<? super Double> $completion) {
    final String _sql = "SELECT AVG(score) FROM ride_history WHERE timestamp >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
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
  public Object totalEarningsSince(final long since,
      final Continuation<? super Double> $completion) {
    final String _sql = "SELECT SUM(rideValue) FROM ride_history WHERE timestamp >= ? AND status = 'ACCEPTED'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
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
  public Object bestRideSince(final long since, final Continuation<? super Double> $completion) {
    final String _sql = "SELECT MAX(rideValue) FROM ride_history WHERE timestamp >= ? AND status = 'ACCEPTED'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
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
  public Object averageValuePerKmSince(final long since,
      final Continuation<? super Double> $completion) {
    final String _sql = "SELECT AVG(rideValue / CASE WHEN dropoffDistance > 0 THEN dropoffDistance ELSE 1 END) FROM ride_history WHERE timestamp >= ? AND status = 'ACCEPTED'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
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
  public Object topPlatformSince(final long since, final Continuation<? super String> $completion) {
    final String _sql = "SELECT platform FROM ride_history WHERE timestamp >= ? GROUP BY platform ORDER BY COUNT(*) DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<String>() {
      @Override
      @Nullable
      public String call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final String _result;
          if (_cursor.moveToFirst()) {
            if (_cursor.isNull(0)) {
              _result = null;
            } else {
              _result = _cursor.getString(0);
            }
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
  public Object count(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM ride_history";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
