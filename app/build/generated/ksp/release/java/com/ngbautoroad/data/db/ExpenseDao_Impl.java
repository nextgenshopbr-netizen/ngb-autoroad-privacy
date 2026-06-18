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
public final class ExpenseDao_Impl implements ExpenseDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ExpenseEntity> __insertionAdapterOfExpenseEntity;

  private final EntityDeletionOrUpdateAdapter<ExpenseEntity> __deletionAdapterOfExpenseEntity;

  private final EntityDeletionOrUpdateAdapter<ExpenseEntity> __updateAdapterOfExpenseEntity;

  public ExpenseDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfExpenseEntity = new EntityInsertionAdapter<ExpenseEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `expenses` (`id`,`category`,`amount`,`description`,`date`,`isRecurring`,`recurringDay`,`recurringDays`,`recurringDuration`,`recurringEndDate`,`liters`,`pricePerLiter`,`odometer`,`fuelType`,`parentExpenseId`,`isGenerated`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ExpenseEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getCategory());
        statement.bindDouble(3, entity.getAmount());
        statement.bindString(4, entity.getDescription());
        statement.bindLong(5, entity.getDate());
        final int _tmp = entity.isRecurring() ? 1 : 0;
        statement.bindLong(6, _tmp);
        statement.bindLong(7, entity.getRecurringDay());
        statement.bindString(8, entity.getRecurringDays());
        statement.bindLong(9, entity.getRecurringDuration());
        statement.bindLong(10, entity.getRecurringEndDate());
        if (entity.getLiters() == null) {
          statement.bindNull(11);
        } else {
          statement.bindDouble(11, entity.getLiters());
        }
        if (entity.getPricePerLiter() == null) {
          statement.bindNull(12);
        } else {
          statement.bindDouble(12, entity.getPricePerLiter());
        }
        if (entity.getOdometer() == null) {
          statement.bindNull(13);
        } else {
          statement.bindLong(13, entity.getOdometer());
        }
        if (entity.getFuelType() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getFuelType());
        }
        statement.bindLong(15, entity.getParentExpenseId());
        final int _tmp_1 = entity.isGenerated() ? 1 : 0;
        statement.bindLong(16, _tmp_1);
      }
    };
    this.__deletionAdapterOfExpenseEntity = new EntityDeletionOrUpdateAdapter<ExpenseEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `expenses` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ExpenseEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfExpenseEntity = new EntityDeletionOrUpdateAdapter<ExpenseEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `expenses` SET `id` = ?,`category` = ?,`amount` = ?,`description` = ?,`date` = ?,`isRecurring` = ?,`recurringDay` = ?,`recurringDays` = ?,`recurringDuration` = ?,`recurringEndDate` = ?,`liters` = ?,`pricePerLiter` = ?,`odometer` = ?,`fuelType` = ?,`parentExpenseId` = ?,`isGenerated` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ExpenseEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getCategory());
        statement.bindDouble(3, entity.getAmount());
        statement.bindString(4, entity.getDescription());
        statement.bindLong(5, entity.getDate());
        final int _tmp = entity.isRecurring() ? 1 : 0;
        statement.bindLong(6, _tmp);
        statement.bindLong(7, entity.getRecurringDay());
        statement.bindString(8, entity.getRecurringDays());
        statement.bindLong(9, entity.getRecurringDuration());
        statement.bindLong(10, entity.getRecurringEndDate());
        if (entity.getLiters() == null) {
          statement.bindNull(11);
        } else {
          statement.bindDouble(11, entity.getLiters());
        }
        if (entity.getPricePerLiter() == null) {
          statement.bindNull(12);
        } else {
          statement.bindDouble(12, entity.getPricePerLiter());
        }
        if (entity.getOdometer() == null) {
          statement.bindNull(13);
        } else {
          statement.bindLong(13, entity.getOdometer());
        }
        if (entity.getFuelType() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getFuelType());
        }
        statement.bindLong(15, entity.getParentExpenseId());
        final int _tmp_1 = entity.isGenerated() ? 1 : 0;
        statement.bindLong(16, _tmp_1);
        statement.bindLong(17, entity.getId());
      }
    };
  }

  @Override
  public Object insert(final ExpenseEntity expense, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfExpenseEntity.insertAndReturnId(expense);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<ExpenseEntity> expenses,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfExpenseEntity.insert(expenses);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final ExpenseEntity expense, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfExpenseEntity.handle(expense);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final ExpenseEntity expense, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfExpenseEntity.handle(expense);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ExpenseEntity>> getAllExpenses() {
    final String _sql = "SELECT * FROM expenses ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"expenses"}, new Callable<List<ExpenseEntity>>() {
      @Override
      @NonNull
      public List<ExpenseEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfIsRecurring = CursorUtil.getColumnIndexOrThrow(_cursor, "isRecurring");
          final int _cursorIndexOfRecurringDay = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDay");
          final int _cursorIndexOfRecurringDays = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDays");
          final int _cursorIndexOfRecurringDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDuration");
          final int _cursorIndexOfRecurringEndDate = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringEndDate");
          final int _cursorIndexOfLiters = CursorUtil.getColumnIndexOrThrow(_cursor, "liters");
          final int _cursorIndexOfPricePerLiter = CursorUtil.getColumnIndexOrThrow(_cursor, "pricePerLiter");
          final int _cursorIndexOfOdometer = CursorUtil.getColumnIndexOrThrow(_cursor, "odometer");
          final int _cursorIndexOfFuelType = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelType");
          final int _cursorIndexOfParentExpenseId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentExpenseId");
          final int _cursorIndexOfIsGenerated = CursorUtil.getColumnIndexOrThrow(_cursor, "isGenerated");
          final List<ExpenseEntity> _result = new ArrayList<ExpenseEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ExpenseEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final boolean _tmpIsRecurring;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsRecurring);
            _tmpIsRecurring = _tmp != 0;
            final int _tmpRecurringDay;
            _tmpRecurringDay = _cursor.getInt(_cursorIndexOfRecurringDay);
            final String _tmpRecurringDays;
            _tmpRecurringDays = _cursor.getString(_cursorIndexOfRecurringDays);
            final int _tmpRecurringDuration;
            _tmpRecurringDuration = _cursor.getInt(_cursorIndexOfRecurringDuration);
            final long _tmpRecurringEndDate;
            _tmpRecurringEndDate = _cursor.getLong(_cursorIndexOfRecurringEndDate);
            final Double _tmpLiters;
            if (_cursor.isNull(_cursorIndexOfLiters)) {
              _tmpLiters = null;
            } else {
              _tmpLiters = _cursor.getDouble(_cursorIndexOfLiters);
            }
            final Double _tmpPricePerLiter;
            if (_cursor.isNull(_cursorIndexOfPricePerLiter)) {
              _tmpPricePerLiter = null;
            } else {
              _tmpPricePerLiter = _cursor.getDouble(_cursorIndexOfPricePerLiter);
            }
            final Integer _tmpOdometer;
            if (_cursor.isNull(_cursorIndexOfOdometer)) {
              _tmpOdometer = null;
            } else {
              _tmpOdometer = _cursor.getInt(_cursorIndexOfOdometer);
            }
            final String _tmpFuelType;
            if (_cursor.isNull(_cursorIndexOfFuelType)) {
              _tmpFuelType = null;
            } else {
              _tmpFuelType = _cursor.getString(_cursorIndexOfFuelType);
            }
            final long _tmpParentExpenseId;
            _tmpParentExpenseId = _cursor.getLong(_cursorIndexOfParentExpenseId);
            final boolean _tmpIsGenerated;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsGenerated);
            _tmpIsGenerated = _tmp_1 != 0;
            _item = new ExpenseEntity(_tmpId,_tmpCategory,_tmpAmount,_tmpDescription,_tmpDate,_tmpIsRecurring,_tmpRecurringDay,_tmpRecurringDays,_tmpRecurringDuration,_tmpRecurringEndDate,_tmpLiters,_tmpPricePerLiter,_tmpOdometer,_tmpFuelType,_tmpParentExpenseId,_tmpIsGenerated);
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
  public Flow<List<ExpenseEntity>> getExpensesByPeriod(final long startDate, final long endDate) {
    final String _sql = "SELECT * FROM expenses WHERE date >= ? AND date <= ? ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startDate);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endDate);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"expenses"}, new Callable<List<ExpenseEntity>>() {
      @Override
      @NonNull
      public List<ExpenseEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfIsRecurring = CursorUtil.getColumnIndexOrThrow(_cursor, "isRecurring");
          final int _cursorIndexOfRecurringDay = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDay");
          final int _cursorIndexOfRecurringDays = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDays");
          final int _cursorIndexOfRecurringDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDuration");
          final int _cursorIndexOfRecurringEndDate = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringEndDate");
          final int _cursorIndexOfLiters = CursorUtil.getColumnIndexOrThrow(_cursor, "liters");
          final int _cursorIndexOfPricePerLiter = CursorUtil.getColumnIndexOrThrow(_cursor, "pricePerLiter");
          final int _cursorIndexOfOdometer = CursorUtil.getColumnIndexOrThrow(_cursor, "odometer");
          final int _cursorIndexOfFuelType = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelType");
          final int _cursorIndexOfParentExpenseId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentExpenseId");
          final int _cursorIndexOfIsGenerated = CursorUtil.getColumnIndexOrThrow(_cursor, "isGenerated");
          final List<ExpenseEntity> _result = new ArrayList<ExpenseEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ExpenseEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final boolean _tmpIsRecurring;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsRecurring);
            _tmpIsRecurring = _tmp != 0;
            final int _tmpRecurringDay;
            _tmpRecurringDay = _cursor.getInt(_cursorIndexOfRecurringDay);
            final String _tmpRecurringDays;
            _tmpRecurringDays = _cursor.getString(_cursorIndexOfRecurringDays);
            final int _tmpRecurringDuration;
            _tmpRecurringDuration = _cursor.getInt(_cursorIndexOfRecurringDuration);
            final long _tmpRecurringEndDate;
            _tmpRecurringEndDate = _cursor.getLong(_cursorIndexOfRecurringEndDate);
            final Double _tmpLiters;
            if (_cursor.isNull(_cursorIndexOfLiters)) {
              _tmpLiters = null;
            } else {
              _tmpLiters = _cursor.getDouble(_cursorIndexOfLiters);
            }
            final Double _tmpPricePerLiter;
            if (_cursor.isNull(_cursorIndexOfPricePerLiter)) {
              _tmpPricePerLiter = null;
            } else {
              _tmpPricePerLiter = _cursor.getDouble(_cursorIndexOfPricePerLiter);
            }
            final Integer _tmpOdometer;
            if (_cursor.isNull(_cursorIndexOfOdometer)) {
              _tmpOdometer = null;
            } else {
              _tmpOdometer = _cursor.getInt(_cursorIndexOfOdometer);
            }
            final String _tmpFuelType;
            if (_cursor.isNull(_cursorIndexOfFuelType)) {
              _tmpFuelType = null;
            } else {
              _tmpFuelType = _cursor.getString(_cursorIndexOfFuelType);
            }
            final long _tmpParentExpenseId;
            _tmpParentExpenseId = _cursor.getLong(_cursorIndexOfParentExpenseId);
            final boolean _tmpIsGenerated;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsGenerated);
            _tmpIsGenerated = _tmp_1 != 0;
            _item = new ExpenseEntity(_tmpId,_tmpCategory,_tmpAmount,_tmpDescription,_tmpDate,_tmpIsRecurring,_tmpRecurringDay,_tmpRecurringDays,_tmpRecurringDuration,_tmpRecurringEndDate,_tmpLiters,_tmpPricePerLiter,_tmpOdometer,_tmpFuelType,_tmpParentExpenseId,_tmpIsGenerated);
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
  public Flow<List<ExpenseEntity>> getExpensesByCategory(final String category) {
    final String _sql = "SELECT * FROM expenses WHERE category = ? ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, category);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"expenses"}, new Callable<List<ExpenseEntity>>() {
      @Override
      @NonNull
      public List<ExpenseEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfIsRecurring = CursorUtil.getColumnIndexOrThrow(_cursor, "isRecurring");
          final int _cursorIndexOfRecurringDay = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDay");
          final int _cursorIndexOfRecurringDays = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDays");
          final int _cursorIndexOfRecurringDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDuration");
          final int _cursorIndexOfRecurringEndDate = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringEndDate");
          final int _cursorIndexOfLiters = CursorUtil.getColumnIndexOrThrow(_cursor, "liters");
          final int _cursorIndexOfPricePerLiter = CursorUtil.getColumnIndexOrThrow(_cursor, "pricePerLiter");
          final int _cursorIndexOfOdometer = CursorUtil.getColumnIndexOrThrow(_cursor, "odometer");
          final int _cursorIndexOfFuelType = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelType");
          final int _cursorIndexOfParentExpenseId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentExpenseId");
          final int _cursorIndexOfIsGenerated = CursorUtil.getColumnIndexOrThrow(_cursor, "isGenerated");
          final List<ExpenseEntity> _result = new ArrayList<ExpenseEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ExpenseEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final boolean _tmpIsRecurring;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsRecurring);
            _tmpIsRecurring = _tmp != 0;
            final int _tmpRecurringDay;
            _tmpRecurringDay = _cursor.getInt(_cursorIndexOfRecurringDay);
            final String _tmpRecurringDays;
            _tmpRecurringDays = _cursor.getString(_cursorIndexOfRecurringDays);
            final int _tmpRecurringDuration;
            _tmpRecurringDuration = _cursor.getInt(_cursorIndexOfRecurringDuration);
            final long _tmpRecurringEndDate;
            _tmpRecurringEndDate = _cursor.getLong(_cursorIndexOfRecurringEndDate);
            final Double _tmpLiters;
            if (_cursor.isNull(_cursorIndexOfLiters)) {
              _tmpLiters = null;
            } else {
              _tmpLiters = _cursor.getDouble(_cursorIndexOfLiters);
            }
            final Double _tmpPricePerLiter;
            if (_cursor.isNull(_cursorIndexOfPricePerLiter)) {
              _tmpPricePerLiter = null;
            } else {
              _tmpPricePerLiter = _cursor.getDouble(_cursorIndexOfPricePerLiter);
            }
            final Integer _tmpOdometer;
            if (_cursor.isNull(_cursorIndexOfOdometer)) {
              _tmpOdometer = null;
            } else {
              _tmpOdometer = _cursor.getInt(_cursorIndexOfOdometer);
            }
            final String _tmpFuelType;
            if (_cursor.isNull(_cursorIndexOfFuelType)) {
              _tmpFuelType = null;
            } else {
              _tmpFuelType = _cursor.getString(_cursorIndexOfFuelType);
            }
            final long _tmpParentExpenseId;
            _tmpParentExpenseId = _cursor.getLong(_cursorIndexOfParentExpenseId);
            final boolean _tmpIsGenerated;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsGenerated);
            _tmpIsGenerated = _tmp_1 != 0;
            _item = new ExpenseEntity(_tmpId,_tmpCategory,_tmpAmount,_tmpDescription,_tmpDate,_tmpIsRecurring,_tmpRecurringDay,_tmpRecurringDays,_tmpRecurringDuration,_tmpRecurringEndDate,_tmpLiters,_tmpPricePerLiter,_tmpOdometer,_tmpFuelType,_tmpParentExpenseId,_tmpIsGenerated);
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
  public Flow<Double> getTotalExpenses(final long startDate, final long endDate) {
    final String _sql = "SELECT SUM(amount) FROM expenses WHERE date >= ? AND date <= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startDate);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endDate);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"expenses"}, new Callable<Double>() {
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
  public Flow<Double> getTotalByCategory(final String category, final long startDate,
      final long endDate) {
    final String _sql = "SELECT SUM(amount) FROM expenses WHERE category = ? AND date >= ? AND date <= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, category);
    _argIndex = 2;
    _statement.bindLong(_argIndex, startDate);
    _argIndex = 3;
    _statement.bindLong(_argIndex, endDate);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"expenses"}, new Callable<Double>() {
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
  public Flow<List<ExpenseEntity>> getRecurringExpenses() {
    final String _sql = "SELECT * FROM expenses WHERE isRecurring = 1 AND isGenerated = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"expenses"}, new Callable<List<ExpenseEntity>>() {
      @Override
      @NonNull
      public List<ExpenseEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfIsRecurring = CursorUtil.getColumnIndexOrThrow(_cursor, "isRecurring");
          final int _cursorIndexOfRecurringDay = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDay");
          final int _cursorIndexOfRecurringDays = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDays");
          final int _cursorIndexOfRecurringDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDuration");
          final int _cursorIndexOfRecurringEndDate = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringEndDate");
          final int _cursorIndexOfLiters = CursorUtil.getColumnIndexOrThrow(_cursor, "liters");
          final int _cursorIndexOfPricePerLiter = CursorUtil.getColumnIndexOrThrow(_cursor, "pricePerLiter");
          final int _cursorIndexOfOdometer = CursorUtil.getColumnIndexOrThrow(_cursor, "odometer");
          final int _cursorIndexOfFuelType = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelType");
          final int _cursorIndexOfParentExpenseId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentExpenseId");
          final int _cursorIndexOfIsGenerated = CursorUtil.getColumnIndexOrThrow(_cursor, "isGenerated");
          final List<ExpenseEntity> _result = new ArrayList<ExpenseEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ExpenseEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final boolean _tmpIsRecurring;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsRecurring);
            _tmpIsRecurring = _tmp != 0;
            final int _tmpRecurringDay;
            _tmpRecurringDay = _cursor.getInt(_cursorIndexOfRecurringDay);
            final String _tmpRecurringDays;
            _tmpRecurringDays = _cursor.getString(_cursorIndexOfRecurringDays);
            final int _tmpRecurringDuration;
            _tmpRecurringDuration = _cursor.getInt(_cursorIndexOfRecurringDuration);
            final long _tmpRecurringEndDate;
            _tmpRecurringEndDate = _cursor.getLong(_cursorIndexOfRecurringEndDate);
            final Double _tmpLiters;
            if (_cursor.isNull(_cursorIndexOfLiters)) {
              _tmpLiters = null;
            } else {
              _tmpLiters = _cursor.getDouble(_cursorIndexOfLiters);
            }
            final Double _tmpPricePerLiter;
            if (_cursor.isNull(_cursorIndexOfPricePerLiter)) {
              _tmpPricePerLiter = null;
            } else {
              _tmpPricePerLiter = _cursor.getDouble(_cursorIndexOfPricePerLiter);
            }
            final Integer _tmpOdometer;
            if (_cursor.isNull(_cursorIndexOfOdometer)) {
              _tmpOdometer = null;
            } else {
              _tmpOdometer = _cursor.getInt(_cursorIndexOfOdometer);
            }
            final String _tmpFuelType;
            if (_cursor.isNull(_cursorIndexOfFuelType)) {
              _tmpFuelType = null;
            } else {
              _tmpFuelType = _cursor.getString(_cursorIndexOfFuelType);
            }
            final long _tmpParentExpenseId;
            _tmpParentExpenseId = _cursor.getLong(_cursorIndexOfParentExpenseId);
            final boolean _tmpIsGenerated;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsGenerated);
            _tmpIsGenerated = _tmp_1 != 0;
            _item = new ExpenseEntity(_tmpId,_tmpCategory,_tmpAmount,_tmpDescription,_tmpDate,_tmpIsRecurring,_tmpRecurringDay,_tmpRecurringDays,_tmpRecurringDuration,_tmpRecurringEndDate,_tmpLiters,_tmpPricePerLiter,_tmpOdometer,_tmpFuelType,_tmpParentExpenseId,_tmpIsGenerated);
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
  public Object getRecurringExpensesSync(
      final Continuation<? super List<ExpenseEntity>> $completion) {
    final String _sql = "SELECT * FROM expenses WHERE isRecurring = 1 AND isGenerated = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ExpenseEntity>>() {
      @Override
      @NonNull
      public List<ExpenseEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfIsRecurring = CursorUtil.getColumnIndexOrThrow(_cursor, "isRecurring");
          final int _cursorIndexOfRecurringDay = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDay");
          final int _cursorIndexOfRecurringDays = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDays");
          final int _cursorIndexOfRecurringDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringDuration");
          final int _cursorIndexOfRecurringEndDate = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringEndDate");
          final int _cursorIndexOfLiters = CursorUtil.getColumnIndexOrThrow(_cursor, "liters");
          final int _cursorIndexOfPricePerLiter = CursorUtil.getColumnIndexOrThrow(_cursor, "pricePerLiter");
          final int _cursorIndexOfOdometer = CursorUtil.getColumnIndexOrThrow(_cursor, "odometer");
          final int _cursorIndexOfFuelType = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelType");
          final int _cursorIndexOfParentExpenseId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentExpenseId");
          final int _cursorIndexOfIsGenerated = CursorUtil.getColumnIndexOrThrow(_cursor, "isGenerated");
          final List<ExpenseEntity> _result = new ArrayList<ExpenseEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ExpenseEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            final boolean _tmpIsRecurring;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsRecurring);
            _tmpIsRecurring = _tmp != 0;
            final int _tmpRecurringDay;
            _tmpRecurringDay = _cursor.getInt(_cursorIndexOfRecurringDay);
            final String _tmpRecurringDays;
            _tmpRecurringDays = _cursor.getString(_cursorIndexOfRecurringDays);
            final int _tmpRecurringDuration;
            _tmpRecurringDuration = _cursor.getInt(_cursorIndexOfRecurringDuration);
            final long _tmpRecurringEndDate;
            _tmpRecurringEndDate = _cursor.getLong(_cursorIndexOfRecurringEndDate);
            final Double _tmpLiters;
            if (_cursor.isNull(_cursorIndexOfLiters)) {
              _tmpLiters = null;
            } else {
              _tmpLiters = _cursor.getDouble(_cursorIndexOfLiters);
            }
            final Double _tmpPricePerLiter;
            if (_cursor.isNull(_cursorIndexOfPricePerLiter)) {
              _tmpPricePerLiter = null;
            } else {
              _tmpPricePerLiter = _cursor.getDouble(_cursorIndexOfPricePerLiter);
            }
            final Integer _tmpOdometer;
            if (_cursor.isNull(_cursorIndexOfOdometer)) {
              _tmpOdometer = null;
            } else {
              _tmpOdometer = _cursor.getInt(_cursorIndexOfOdometer);
            }
            final String _tmpFuelType;
            if (_cursor.isNull(_cursorIndexOfFuelType)) {
              _tmpFuelType = null;
            } else {
              _tmpFuelType = _cursor.getString(_cursorIndexOfFuelType);
            }
            final long _tmpParentExpenseId;
            _tmpParentExpenseId = _cursor.getLong(_cursorIndexOfParentExpenseId);
            final boolean _tmpIsGenerated;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsGenerated);
            _tmpIsGenerated = _tmp_1 != 0;
            _item = new ExpenseEntity(_tmpId,_tmpCategory,_tmpAmount,_tmpDescription,_tmpDate,_tmpIsRecurring,_tmpRecurringDay,_tmpRecurringDays,_tmpRecurringDuration,_tmpRecurringEndDate,_tmpLiters,_tmpPricePerLiter,_tmpOdometer,_tmpFuelType,_tmpParentExpenseId,_tmpIsGenerated);
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
  public Flow<Double> getTotalRecurring() {
    final String _sql = "SELECT SUM(amount) FROM expenses WHERE isRecurring = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"expenses"}, new Callable<Double>() {
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
  public Object countGeneratedForDay(final long parentId, final long dayStart, final long dayEnd,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM expenses WHERE parentExpenseId = ? AND date >= ? AND date <= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, parentId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, dayStart);
    _argIndex = 3;
    _statement.bindLong(_argIndex, dayEnd);
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
  public Object getExpenseSummaryByCategory(final long startDate, final long endDate,
      final Continuation<? super List<CategorySummary>> $completion) {
    final String _sql = "SELECT category, SUM(amount) as total FROM expenses WHERE date >= ? AND date <= ? GROUP BY category ORDER BY total DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startDate);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endDate);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CategorySummary>>() {
      @Override
      @NonNull
      public List<CategorySummary> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCategory = 0;
          final int _cursorIndexOfTotal = 1;
          final List<CategorySummary> _result = new ArrayList<CategorySummary>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CategorySummary _item;
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpTotal;
            _tmpTotal = _cursor.getDouble(_cursorIndexOfTotal);
            _item = new CategorySummary(_tmpCategory,_tmpTotal);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
