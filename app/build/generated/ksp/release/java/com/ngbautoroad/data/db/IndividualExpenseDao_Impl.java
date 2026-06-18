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
public final class IndividualExpenseDao_Impl implements IndividualExpenseDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<IndividualExpenseEntity> __insertionAdapterOfIndividualExpenseEntity;

  private final EntityDeletionOrUpdateAdapter<IndividualExpenseEntity> __deletionAdapterOfIndividualExpenseEntity;

  private final EntityDeletionOrUpdateAdapter<IndividualExpenseEntity> __updateAdapterOfIndividualExpenseEntity;

  public IndividualExpenseDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfIndividualExpenseEntity = new EntityInsertionAdapter<IndividualExpenseEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `individual_expenses` (`id`,`vehicleId`,`title`,`category`,`totalAmount`,`installments`,`installmentsPaid`,`monthlyAmount`,`startDate`,`dueDay`,`isIncludedInCalc`,`isRecurringAnnual`,`frequency`,`notes`,`isPaid`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final IndividualExpenseEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getVehicleId());
        statement.bindString(3, entity.getTitle());
        statement.bindString(4, entity.getCategory());
        statement.bindDouble(5, entity.getTotalAmount());
        statement.bindLong(6, entity.getInstallments());
        statement.bindLong(7, entity.getInstallmentsPaid());
        statement.bindDouble(8, entity.getMonthlyAmount());
        statement.bindLong(9, entity.getStartDate());
        statement.bindLong(10, entity.getDueDay());
        final int _tmp = entity.isIncludedInCalc() ? 1 : 0;
        statement.bindLong(11, _tmp);
        final int _tmp_1 = entity.isRecurringAnnual() ? 1 : 0;
        statement.bindLong(12, _tmp_1);
        statement.bindString(13, entity.getFrequency());
        statement.bindString(14, entity.getNotes());
        final int _tmp_2 = entity.isPaid() ? 1 : 0;
        statement.bindLong(15, _tmp_2);
        statement.bindLong(16, entity.getCreatedAt());
      }
    };
    this.__deletionAdapterOfIndividualExpenseEntity = new EntityDeletionOrUpdateAdapter<IndividualExpenseEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `individual_expenses` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final IndividualExpenseEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfIndividualExpenseEntity = new EntityDeletionOrUpdateAdapter<IndividualExpenseEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `individual_expenses` SET `id` = ?,`vehicleId` = ?,`title` = ?,`category` = ?,`totalAmount` = ?,`installments` = ?,`installmentsPaid` = ?,`monthlyAmount` = ?,`startDate` = ?,`dueDay` = ?,`isIncludedInCalc` = ?,`isRecurringAnnual` = ?,`frequency` = ?,`notes` = ?,`isPaid` = ?,`createdAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final IndividualExpenseEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getVehicleId());
        statement.bindString(3, entity.getTitle());
        statement.bindString(4, entity.getCategory());
        statement.bindDouble(5, entity.getTotalAmount());
        statement.bindLong(6, entity.getInstallments());
        statement.bindLong(7, entity.getInstallmentsPaid());
        statement.bindDouble(8, entity.getMonthlyAmount());
        statement.bindLong(9, entity.getStartDate());
        statement.bindLong(10, entity.getDueDay());
        final int _tmp = entity.isIncludedInCalc() ? 1 : 0;
        statement.bindLong(11, _tmp);
        final int _tmp_1 = entity.isRecurringAnnual() ? 1 : 0;
        statement.bindLong(12, _tmp_1);
        statement.bindString(13, entity.getFrequency());
        statement.bindString(14, entity.getNotes());
        final int _tmp_2 = entity.isPaid() ? 1 : 0;
        statement.bindLong(15, _tmp_2);
        statement.bindLong(16, entity.getCreatedAt());
        statement.bindLong(17, entity.getId());
      }
    };
  }

  @Override
  public Object insert(final IndividualExpenseEntity expense,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfIndividualExpenseEntity.insertAndReturnId(expense);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final IndividualExpenseEntity expense,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfIndividualExpenseEntity.handle(expense);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final IndividualExpenseEntity expense,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfIndividualExpenseEntity.handle(expense);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<IndividualExpenseEntity>> getAllExpenses() {
    final String _sql = "SELECT * FROM individual_expenses ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"individual_expenses"}, new Callable<List<IndividualExpenseEntity>>() {
      @Override
      @NonNull
      public List<IndividualExpenseEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVehicleId = CursorUtil.getColumnIndexOrThrow(_cursor, "vehicleId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTotalAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "totalAmount");
          final int _cursorIndexOfInstallments = CursorUtil.getColumnIndexOrThrow(_cursor, "installments");
          final int _cursorIndexOfInstallmentsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "installmentsPaid");
          final int _cursorIndexOfMonthlyAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "monthlyAmount");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfDueDay = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDay");
          final int _cursorIndexOfIsIncludedInCalc = CursorUtil.getColumnIndexOrThrow(_cursor, "isIncludedInCalc");
          final int _cursorIndexOfIsRecurringAnnual = CursorUtil.getColumnIndexOrThrow(_cursor, "isRecurringAnnual");
          final int _cursorIndexOfFrequency = CursorUtil.getColumnIndexOrThrow(_cursor, "frequency");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "isPaid");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<IndividualExpenseEntity> _result = new ArrayList<IndividualExpenseEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final IndividualExpenseEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpVehicleId;
            _tmpVehicleId = _cursor.getLong(_cursorIndexOfVehicleId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpTotalAmount;
            _tmpTotalAmount = _cursor.getDouble(_cursorIndexOfTotalAmount);
            final int _tmpInstallments;
            _tmpInstallments = _cursor.getInt(_cursorIndexOfInstallments);
            final int _tmpInstallmentsPaid;
            _tmpInstallmentsPaid = _cursor.getInt(_cursorIndexOfInstallmentsPaid);
            final double _tmpMonthlyAmount;
            _tmpMonthlyAmount = _cursor.getDouble(_cursorIndexOfMonthlyAmount);
            final long _tmpStartDate;
            _tmpStartDate = _cursor.getLong(_cursorIndexOfStartDate);
            final int _tmpDueDay;
            _tmpDueDay = _cursor.getInt(_cursorIndexOfDueDay);
            final boolean _tmpIsIncludedInCalc;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsIncludedInCalc);
            _tmpIsIncludedInCalc = _tmp != 0;
            final boolean _tmpIsRecurringAnnual;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsRecurringAnnual);
            _tmpIsRecurringAnnual = _tmp_1 != 0;
            final String _tmpFrequency;
            _tmpFrequency = _cursor.getString(_cursorIndexOfFrequency);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsPaid;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsPaid);
            _tmpIsPaid = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new IndividualExpenseEntity(_tmpId,_tmpVehicleId,_tmpTitle,_tmpCategory,_tmpTotalAmount,_tmpInstallments,_tmpInstallmentsPaid,_tmpMonthlyAmount,_tmpStartDate,_tmpDueDay,_tmpIsIncludedInCalc,_tmpIsRecurringAnnual,_tmpFrequency,_tmpNotes,_tmpIsPaid,_tmpCreatedAt);
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
  public Flow<List<IndividualExpenseEntity>> getIncludedExpenses() {
    final String _sql = "SELECT * FROM individual_expenses WHERE isIncludedInCalc = 1 ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"individual_expenses"}, new Callable<List<IndividualExpenseEntity>>() {
      @Override
      @NonNull
      public List<IndividualExpenseEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVehicleId = CursorUtil.getColumnIndexOrThrow(_cursor, "vehicleId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTotalAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "totalAmount");
          final int _cursorIndexOfInstallments = CursorUtil.getColumnIndexOrThrow(_cursor, "installments");
          final int _cursorIndexOfInstallmentsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "installmentsPaid");
          final int _cursorIndexOfMonthlyAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "monthlyAmount");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfDueDay = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDay");
          final int _cursorIndexOfIsIncludedInCalc = CursorUtil.getColumnIndexOrThrow(_cursor, "isIncludedInCalc");
          final int _cursorIndexOfIsRecurringAnnual = CursorUtil.getColumnIndexOrThrow(_cursor, "isRecurringAnnual");
          final int _cursorIndexOfFrequency = CursorUtil.getColumnIndexOrThrow(_cursor, "frequency");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "isPaid");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<IndividualExpenseEntity> _result = new ArrayList<IndividualExpenseEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final IndividualExpenseEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpVehicleId;
            _tmpVehicleId = _cursor.getLong(_cursorIndexOfVehicleId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpTotalAmount;
            _tmpTotalAmount = _cursor.getDouble(_cursorIndexOfTotalAmount);
            final int _tmpInstallments;
            _tmpInstallments = _cursor.getInt(_cursorIndexOfInstallments);
            final int _tmpInstallmentsPaid;
            _tmpInstallmentsPaid = _cursor.getInt(_cursorIndexOfInstallmentsPaid);
            final double _tmpMonthlyAmount;
            _tmpMonthlyAmount = _cursor.getDouble(_cursorIndexOfMonthlyAmount);
            final long _tmpStartDate;
            _tmpStartDate = _cursor.getLong(_cursorIndexOfStartDate);
            final int _tmpDueDay;
            _tmpDueDay = _cursor.getInt(_cursorIndexOfDueDay);
            final boolean _tmpIsIncludedInCalc;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsIncludedInCalc);
            _tmpIsIncludedInCalc = _tmp != 0;
            final boolean _tmpIsRecurringAnnual;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsRecurringAnnual);
            _tmpIsRecurringAnnual = _tmp_1 != 0;
            final String _tmpFrequency;
            _tmpFrequency = _cursor.getString(_cursorIndexOfFrequency);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsPaid;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsPaid);
            _tmpIsPaid = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new IndividualExpenseEntity(_tmpId,_tmpVehicleId,_tmpTitle,_tmpCategory,_tmpTotalAmount,_tmpInstallments,_tmpInstallmentsPaid,_tmpMonthlyAmount,_tmpStartDate,_tmpDueDay,_tmpIsIncludedInCalc,_tmpIsRecurringAnnual,_tmpFrequency,_tmpNotes,_tmpIsPaid,_tmpCreatedAt);
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
  public Object getIncludedExpensesSync(
      final Continuation<? super List<IndividualExpenseEntity>> $completion) {
    final String _sql = "SELECT * FROM individual_expenses WHERE isIncludedInCalc = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<IndividualExpenseEntity>>() {
      @Override
      @NonNull
      public List<IndividualExpenseEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVehicleId = CursorUtil.getColumnIndexOrThrow(_cursor, "vehicleId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTotalAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "totalAmount");
          final int _cursorIndexOfInstallments = CursorUtil.getColumnIndexOrThrow(_cursor, "installments");
          final int _cursorIndexOfInstallmentsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "installmentsPaid");
          final int _cursorIndexOfMonthlyAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "monthlyAmount");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfDueDay = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDay");
          final int _cursorIndexOfIsIncludedInCalc = CursorUtil.getColumnIndexOrThrow(_cursor, "isIncludedInCalc");
          final int _cursorIndexOfIsRecurringAnnual = CursorUtil.getColumnIndexOrThrow(_cursor, "isRecurringAnnual");
          final int _cursorIndexOfFrequency = CursorUtil.getColumnIndexOrThrow(_cursor, "frequency");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "isPaid");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<IndividualExpenseEntity> _result = new ArrayList<IndividualExpenseEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final IndividualExpenseEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpVehicleId;
            _tmpVehicleId = _cursor.getLong(_cursorIndexOfVehicleId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpTotalAmount;
            _tmpTotalAmount = _cursor.getDouble(_cursorIndexOfTotalAmount);
            final int _tmpInstallments;
            _tmpInstallments = _cursor.getInt(_cursorIndexOfInstallments);
            final int _tmpInstallmentsPaid;
            _tmpInstallmentsPaid = _cursor.getInt(_cursorIndexOfInstallmentsPaid);
            final double _tmpMonthlyAmount;
            _tmpMonthlyAmount = _cursor.getDouble(_cursorIndexOfMonthlyAmount);
            final long _tmpStartDate;
            _tmpStartDate = _cursor.getLong(_cursorIndexOfStartDate);
            final int _tmpDueDay;
            _tmpDueDay = _cursor.getInt(_cursorIndexOfDueDay);
            final boolean _tmpIsIncludedInCalc;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsIncludedInCalc);
            _tmpIsIncludedInCalc = _tmp != 0;
            final boolean _tmpIsRecurringAnnual;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsRecurringAnnual);
            _tmpIsRecurringAnnual = _tmp_1 != 0;
            final String _tmpFrequency;
            _tmpFrequency = _cursor.getString(_cursorIndexOfFrequency);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsPaid;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsPaid);
            _tmpIsPaid = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new IndividualExpenseEntity(_tmpId,_tmpVehicleId,_tmpTitle,_tmpCategory,_tmpTotalAmount,_tmpInstallments,_tmpInstallmentsPaid,_tmpMonthlyAmount,_tmpStartDate,_tmpDueDay,_tmpIsIncludedInCalc,_tmpIsRecurringAnnual,_tmpFrequency,_tmpNotes,_tmpIsPaid,_tmpCreatedAt);
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
  public Flow<List<IndividualExpenseEntity>> getByVehicle(final long vehicleId) {
    final String _sql = "SELECT * FROM individual_expenses WHERE vehicleId = ? ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, vehicleId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"individual_expenses"}, new Callable<List<IndividualExpenseEntity>>() {
      @Override
      @NonNull
      public List<IndividualExpenseEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVehicleId = CursorUtil.getColumnIndexOrThrow(_cursor, "vehicleId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTotalAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "totalAmount");
          final int _cursorIndexOfInstallments = CursorUtil.getColumnIndexOrThrow(_cursor, "installments");
          final int _cursorIndexOfInstallmentsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "installmentsPaid");
          final int _cursorIndexOfMonthlyAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "monthlyAmount");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfDueDay = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDay");
          final int _cursorIndexOfIsIncludedInCalc = CursorUtil.getColumnIndexOrThrow(_cursor, "isIncludedInCalc");
          final int _cursorIndexOfIsRecurringAnnual = CursorUtil.getColumnIndexOrThrow(_cursor, "isRecurringAnnual");
          final int _cursorIndexOfFrequency = CursorUtil.getColumnIndexOrThrow(_cursor, "frequency");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "isPaid");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<IndividualExpenseEntity> _result = new ArrayList<IndividualExpenseEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final IndividualExpenseEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpVehicleId;
            _tmpVehicleId = _cursor.getLong(_cursorIndexOfVehicleId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpTotalAmount;
            _tmpTotalAmount = _cursor.getDouble(_cursorIndexOfTotalAmount);
            final int _tmpInstallments;
            _tmpInstallments = _cursor.getInt(_cursorIndexOfInstallments);
            final int _tmpInstallmentsPaid;
            _tmpInstallmentsPaid = _cursor.getInt(_cursorIndexOfInstallmentsPaid);
            final double _tmpMonthlyAmount;
            _tmpMonthlyAmount = _cursor.getDouble(_cursorIndexOfMonthlyAmount);
            final long _tmpStartDate;
            _tmpStartDate = _cursor.getLong(_cursorIndexOfStartDate);
            final int _tmpDueDay;
            _tmpDueDay = _cursor.getInt(_cursorIndexOfDueDay);
            final boolean _tmpIsIncludedInCalc;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsIncludedInCalc);
            _tmpIsIncludedInCalc = _tmp != 0;
            final boolean _tmpIsRecurringAnnual;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsRecurringAnnual);
            _tmpIsRecurringAnnual = _tmp_1 != 0;
            final String _tmpFrequency;
            _tmpFrequency = _cursor.getString(_cursorIndexOfFrequency);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsPaid;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsPaid);
            _tmpIsPaid = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new IndividualExpenseEntity(_tmpId,_tmpVehicleId,_tmpTitle,_tmpCategory,_tmpTotalAmount,_tmpInstallments,_tmpInstallmentsPaid,_tmpMonthlyAmount,_tmpStartDate,_tmpDueDay,_tmpIsIncludedInCalc,_tmpIsRecurringAnnual,_tmpFrequency,_tmpNotes,_tmpIsPaid,_tmpCreatedAt);
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
  public Flow<List<IndividualExpenseEntity>> getByCategory(final String category) {
    final String _sql = "SELECT * FROM individual_expenses WHERE category = ? ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, category);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"individual_expenses"}, new Callable<List<IndividualExpenseEntity>>() {
      @Override
      @NonNull
      public List<IndividualExpenseEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVehicleId = CursorUtil.getColumnIndexOrThrow(_cursor, "vehicleId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTotalAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "totalAmount");
          final int _cursorIndexOfInstallments = CursorUtil.getColumnIndexOrThrow(_cursor, "installments");
          final int _cursorIndexOfInstallmentsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "installmentsPaid");
          final int _cursorIndexOfMonthlyAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "monthlyAmount");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfDueDay = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDay");
          final int _cursorIndexOfIsIncludedInCalc = CursorUtil.getColumnIndexOrThrow(_cursor, "isIncludedInCalc");
          final int _cursorIndexOfIsRecurringAnnual = CursorUtil.getColumnIndexOrThrow(_cursor, "isRecurringAnnual");
          final int _cursorIndexOfFrequency = CursorUtil.getColumnIndexOrThrow(_cursor, "frequency");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "isPaid");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<IndividualExpenseEntity> _result = new ArrayList<IndividualExpenseEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final IndividualExpenseEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpVehicleId;
            _tmpVehicleId = _cursor.getLong(_cursorIndexOfVehicleId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpTotalAmount;
            _tmpTotalAmount = _cursor.getDouble(_cursorIndexOfTotalAmount);
            final int _tmpInstallments;
            _tmpInstallments = _cursor.getInt(_cursorIndexOfInstallments);
            final int _tmpInstallmentsPaid;
            _tmpInstallmentsPaid = _cursor.getInt(_cursorIndexOfInstallmentsPaid);
            final double _tmpMonthlyAmount;
            _tmpMonthlyAmount = _cursor.getDouble(_cursorIndexOfMonthlyAmount);
            final long _tmpStartDate;
            _tmpStartDate = _cursor.getLong(_cursorIndexOfStartDate);
            final int _tmpDueDay;
            _tmpDueDay = _cursor.getInt(_cursorIndexOfDueDay);
            final boolean _tmpIsIncludedInCalc;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsIncludedInCalc);
            _tmpIsIncludedInCalc = _tmp != 0;
            final boolean _tmpIsRecurringAnnual;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsRecurringAnnual);
            _tmpIsRecurringAnnual = _tmp_1 != 0;
            final String _tmpFrequency;
            _tmpFrequency = _cursor.getString(_cursorIndexOfFrequency);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsPaid;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsPaid);
            _tmpIsPaid = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new IndividualExpenseEntity(_tmpId,_tmpVehicleId,_tmpTitle,_tmpCategory,_tmpTotalAmount,_tmpInstallments,_tmpInstallmentsPaid,_tmpMonthlyAmount,_tmpStartDate,_tmpDueDay,_tmpIsIncludedInCalc,_tmpIsRecurringAnnual,_tmpFrequency,_tmpNotes,_tmpIsPaid,_tmpCreatedAt);
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
  public Object getActiveUnpaidSync(
      final Continuation<? super List<IndividualExpenseEntity>> $completion) {
    final String _sql = "SELECT * FROM individual_expenses WHERE isPaid = 0 AND isIncludedInCalc = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<IndividualExpenseEntity>>() {
      @Override
      @NonNull
      public List<IndividualExpenseEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVehicleId = CursorUtil.getColumnIndexOrThrow(_cursor, "vehicleId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTotalAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "totalAmount");
          final int _cursorIndexOfInstallments = CursorUtil.getColumnIndexOrThrow(_cursor, "installments");
          final int _cursorIndexOfInstallmentsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "installmentsPaid");
          final int _cursorIndexOfMonthlyAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "monthlyAmount");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfDueDay = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDay");
          final int _cursorIndexOfIsIncludedInCalc = CursorUtil.getColumnIndexOrThrow(_cursor, "isIncludedInCalc");
          final int _cursorIndexOfIsRecurringAnnual = CursorUtil.getColumnIndexOrThrow(_cursor, "isRecurringAnnual");
          final int _cursorIndexOfFrequency = CursorUtil.getColumnIndexOrThrow(_cursor, "frequency");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfIsPaid = CursorUtil.getColumnIndexOrThrow(_cursor, "isPaid");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<IndividualExpenseEntity> _result = new ArrayList<IndividualExpenseEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final IndividualExpenseEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpVehicleId;
            _tmpVehicleId = _cursor.getLong(_cursorIndexOfVehicleId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final double _tmpTotalAmount;
            _tmpTotalAmount = _cursor.getDouble(_cursorIndexOfTotalAmount);
            final int _tmpInstallments;
            _tmpInstallments = _cursor.getInt(_cursorIndexOfInstallments);
            final int _tmpInstallmentsPaid;
            _tmpInstallmentsPaid = _cursor.getInt(_cursorIndexOfInstallmentsPaid);
            final double _tmpMonthlyAmount;
            _tmpMonthlyAmount = _cursor.getDouble(_cursorIndexOfMonthlyAmount);
            final long _tmpStartDate;
            _tmpStartDate = _cursor.getLong(_cursorIndexOfStartDate);
            final int _tmpDueDay;
            _tmpDueDay = _cursor.getInt(_cursorIndexOfDueDay);
            final boolean _tmpIsIncludedInCalc;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsIncludedInCalc);
            _tmpIsIncludedInCalc = _tmp != 0;
            final boolean _tmpIsRecurringAnnual;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsRecurringAnnual);
            _tmpIsRecurringAnnual = _tmp_1 != 0;
            final String _tmpFrequency;
            _tmpFrequency = _cursor.getString(_cursorIndexOfFrequency);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpIsPaid;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsPaid);
            _tmpIsPaid = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new IndividualExpenseEntity(_tmpId,_tmpVehicleId,_tmpTitle,_tmpCategory,_tmpTotalAmount,_tmpInstallments,_tmpInstallmentsPaid,_tmpMonthlyAmount,_tmpStartDate,_tmpDueDay,_tmpIsIncludedInCalc,_tmpIsRecurringAnnual,_tmpFrequency,_tmpNotes,_tmpIsPaid,_tmpCreatedAt);
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
  public Flow<Double> getTotalMonthlyRated() {
    final String _sql = "SELECT SUM(monthlyAmount) FROM individual_expenses WHERE isIncludedInCalc = 1 AND isPaid = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"individual_expenses"}, new Callable<Double>() {
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
  public Object getTotalMonthlyRatedSync(final Continuation<? super Double> $completion) {
    final String _sql = "SELECT SUM(monthlyAmount) FROM individual_expenses WHERE isIncludedInCalc = 1 AND isPaid = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
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
