package com.ngbautoroad.data.db;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
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
public final class FinancialGoalDao_Impl implements FinancialGoalDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<FinancialGoalEntity> __insertionAdapterOfFinancialGoalEntity;

  private final EntityDeletionOrUpdateAdapter<FinancialGoalEntity> __deletionAdapterOfFinancialGoalEntity;

  private final EntityDeletionOrUpdateAdapter<FinancialGoalEntity> __updateAdapterOfFinancialGoalEntity;

  public FinancialGoalDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfFinancialGoalEntity = new EntityInsertionAdapter<FinancialGoalEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `financial_goals` (`id`,`title`,`targetAmount`,`currentAmount`,`period`,`isActive`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FinancialGoalEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindDouble(3, entity.getTargetAmount());
        statement.bindDouble(4, entity.getCurrentAmount());
        statement.bindString(5, entity.getPeriod());
        final int _tmp = entity.isActive() ? 1 : 0;
        statement.bindLong(6, _tmp);
        statement.bindLong(7, entity.getCreatedAt());
      }
    };
    this.__deletionAdapterOfFinancialGoalEntity = new EntityDeletionOrUpdateAdapter<FinancialGoalEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `financial_goals` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FinancialGoalEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfFinancialGoalEntity = new EntityDeletionOrUpdateAdapter<FinancialGoalEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `financial_goals` SET `id` = ?,`title` = ?,`targetAmount` = ?,`currentAmount` = ?,`period` = ?,`isActive` = ?,`createdAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FinancialGoalEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindDouble(3, entity.getTargetAmount());
        statement.bindDouble(4, entity.getCurrentAmount());
        statement.bindString(5, entity.getPeriod());
        final int _tmp = entity.isActive() ? 1 : 0;
        statement.bindLong(6, _tmp);
        statement.bindLong(7, entity.getCreatedAt());
        statement.bindLong(8, entity.getId());
      }
    };
  }

  @Override
  public Object insert(final FinancialGoalEntity goal,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfFinancialGoalEntity.insertAndReturnId(goal);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final FinancialGoalEntity goal,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfFinancialGoalEntity.handle(goal);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final FinancialGoalEntity goal,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfFinancialGoalEntity.handle(goal);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<FinancialGoalEntity>> getActiveGoals() {
    final String _sql = "SELECT * FROM financial_goals WHERE isActive = 1 ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"financial_goals"}, new Callable<List<FinancialGoalEntity>>() {
      @Override
      @NonNull
      public List<FinancialGoalEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfTargetAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "targetAmount");
          final int _cursorIndexOfCurrentAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "currentAmount");
          final int _cursorIndexOfPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "period");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<FinancialGoalEntity> _result = new ArrayList<FinancialGoalEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FinancialGoalEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final double _tmpTargetAmount;
            _tmpTargetAmount = _cursor.getDouble(_cursorIndexOfTargetAmount);
            final double _tmpCurrentAmount;
            _tmpCurrentAmount = _cursor.getDouble(_cursorIndexOfCurrentAmount);
            final String _tmpPeriod;
            _tmpPeriod = _cursor.getString(_cursorIndexOfPeriod);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new FinancialGoalEntity(_tmpId,_tmpTitle,_tmpTargetAmount,_tmpCurrentAmount,_tmpPeriod,_tmpIsActive,_tmpCreatedAt);
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
  public Flow<List<FinancialGoalEntity>> getAllGoals() {
    final String _sql = "SELECT * FROM financial_goals ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"financial_goals"}, new Callable<List<FinancialGoalEntity>>() {
      @Override
      @NonNull
      public List<FinancialGoalEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfTargetAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "targetAmount");
          final int _cursorIndexOfCurrentAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "currentAmount");
          final int _cursorIndexOfPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "period");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<FinancialGoalEntity> _result = new ArrayList<FinancialGoalEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FinancialGoalEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final double _tmpTargetAmount;
            _tmpTargetAmount = _cursor.getDouble(_cursorIndexOfTargetAmount);
            final double _tmpCurrentAmount;
            _tmpCurrentAmount = _cursor.getDouble(_cursorIndexOfCurrentAmount);
            final String _tmpPeriod;
            _tmpPeriod = _cursor.getString(_cursorIndexOfPeriod);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new FinancialGoalEntity(_tmpId,_tmpTitle,_tmpTargetAmount,_tmpCurrentAmount,_tmpPeriod,_tmpIsActive,_tmpCreatedAt);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
