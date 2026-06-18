package com.ngbautoroad.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomDatabaseKt;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
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
public final class VehicleProfileDao_Impl implements VehicleProfileDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<VehicleProfileEntity> __insertionAdapterOfVehicleProfileEntity;

  private final EntityDeletionOrUpdateAdapter<VehicleProfileEntity> __deletionAdapterOfVehicleProfileEntity;

  private final EntityDeletionOrUpdateAdapter<VehicleProfileEntity> __updateAdapterOfVehicleProfileEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeactivateAll;

  private final SharedSQLiteStatement __preparedStmtOfSetActive;

  public VehicleProfileDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfVehicleProfileEntity = new EntityInsertionAdapter<VehicleProfileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `vehicle_profiles` (`id`,`isActive`,`brand`,`model`,`year`,`plate`,`vehicleType`,`fuelType`,`averageConsumption`,`fuelPrice`,`costPerKm`,`isOwned`,`rentalCost`,`purchaseValue`,`currentOdometer`,`tireLifeKm`,`tireCost`,`brakepadLifeKm`,`brakepadCost`,`oilChangeKm`,`oilChangeCost`,`maintenanceIntervalKm`,`maintenanceCost`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final VehicleProfileEntity entity) {
        statement.bindLong(1, entity.getId());
        final int _tmp = entity.isActive() ? 1 : 0;
        statement.bindLong(2, _tmp);
        statement.bindString(3, entity.getBrand());
        statement.bindString(4, entity.getModel());
        statement.bindLong(5, entity.getYear());
        statement.bindString(6, entity.getPlate());
        statement.bindString(7, entity.getVehicleType());
        statement.bindString(8, entity.getFuelType());
        statement.bindDouble(9, entity.getAverageConsumption());
        statement.bindDouble(10, entity.getFuelPrice());
        statement.bindDouble(11, entity.getCostPerKm());
        final int _tmp_1 = entity.isOwned() ? 1 : 0;
        statement.bindLong(12, _tmp_1);
        statement.bindDouble(13, entity.getRentalCost());
        statement.bindDouble(14, entity.getPurchaseValue());
        statement.bindLong(15, entity.getCurrentOdometer());
        statement.bindLong(16, entity.getTireLifeKm());
        statement.bindDouble(17, entity.getTireCost());
        statement.bindLong(18, entity.getBrakepadLifeKm());
        statement.bindDouble(19, entity.getBrakepadCost());
        statement.bindLong(20, entity.getOilChangeKm());
        statement.bindDouble(21, entity.getOilChangeCost());
        statement.bindLong(22, entity.getMaintenanceIntervalKm());
        statement.bindDouble(23, entity.getMaintenanceCost());
        statement.bindLong(24, entity.getCreatedAt());
      }
    };
    this.__deletionAdapterOfVehicleProfileEntity = new EntityDeletionOrUpdateAdapter<VehicleProfileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `vehicle_profiles` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final VehicleProfileEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfVehicleProfileEntity = new EntityDeletionOrUpdateAdapter<VehicleProfileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `vehicle_profiles` SET `id` = ?,`isActive` = ?,`brand` = ?,`model` = ?,`year` = ?,`plate` = ?,`vehicleType` = ?,`fuelType` = ?,`averageConsumption` = ?,`fuelPrice` = ?,`costPerKm` = ?,`isOwned` = ?,`rentalCost` = ?,`purchaseValue` = ?,`currentOdometer` = ?,`tireLifeKm` = ?,`tireCost` = ?,`brakepadLifeKm` = ?,`brakepadCost` = ?,`oilChangeKm` = ?,`oilChangeCost` = ?,`maintenanceIntervalKm` = ?,`maintenanceCost` = ?,`createdAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final VehicleProfileEntity entity) {
        statement.bindLong(1, entity.getId());
        final int _tmp = entity.isActive() ? 1 : 0;
        statement.bindLong(2, _tmp);
        statement.bindString(3, entity.getBrand());
        statement.bindString(4, entity.getModel());
        statement.bindLong(5, entity.getYear());
        statement.bindString(6, entity.getPlate());
        statement.bindString(7, entity.getVehicleType());
        statement.bindString(8, entity.getFuelType());
        statement.bindDouble(9, entity.getAverageConsumption());
        statement.bindDouble(10, entity.getFuelPrice());
        statement.bindDouble(11, entity.getCostPerKm());
        final int _tmp_1 = entity.isOwned() ? 1 : 0;
        statement.bindLong(12, _tmp_1);
        statement.bindDouble(13, entity.getRentalCost());
        statement.bindDouble(14, entity.getPurchaseValue());
        statement.bindLong(15, entity.getCurrentOdometer());
        statement.bindLong(16, entity.getTireLifeKm());
        statement.bindDouble(17, entity.getTireCost());
        statement.bindLong(18, entity.getBrakepadLifeKm());
        statement.bindDouble(19, entity.getBrakepadCost());
        statement.bindLong(20, entity.getOilChangeKm());
        statement.bindDouble(21, entity.getOilChangeCost());
        statement.bindLong(22, entity.getMaintenanceIntervalKm());
        statement.bindDouble(23, entity.getMaintenanceCost());
        statement.bindLong(24, entity.getCreatedAt());
        statement.bindLong(25, entity.getId());
      }
    };
    this.__preparedStmtOfDeactivateAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE vehicle_profiles SET isActive = 0";
        return _query;
      }
    };
    this.__preparedStmtOfSetActive = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE vehicle_profiles SET isActive = 1 WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final VehicleProfileEntity vehicle,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfVehicleProfileEntity.insertAndReturnId(vehicle);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final VehicleProfileEntity vehicle,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfVehicleProfileEntity.handle(vehicle);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final VehicleProfileEntity vehicle,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfVehicleProfileEntity.handle(vehicle);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object switchActiveVehicle(final long vehicleId,
      final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> VehicleProfileDao.DefaultImpls.switchActiveVehicle(VehicleProfileDao_Impl.this, vehicleId, __cont), $completion);
  }

  @Override
  public Object deactivateAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeactivateAll.acquire();
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
          __preparedStmtOfDeactivateAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setActive(final long vehicleId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetActive.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, vehicleId);
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
          __preparedStmtOfSetActive.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<VehicleProfileEntity>> getAllVehicles() {
    final String _sql = "SELECT * FROM vehicle_profiles ORDER BY isActive DESC, createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"vehicle_profiles"}, new Callable<List<VehicleProfileEntity>>() {
      @Override
      @NonNull
      public List<VehicleProfileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfBrand = CursorUtil.getColumnIndexOrThrow(_cursor, "brand");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfYear = CursorUtil.getColumnIndexOrThrow(_cursor, "year");
          final int _cursorIndexOfPlate = CursorUtil.getColumnIndexOrThrow(_cursor, "plate");
          final int _cursorIndexOfVehicleType = CursorUtil.getColumnIndexOrThrow(_cursor, "vehicleType");
          final int _cursorIndexOfFuelType = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelType");
          final int _cursorIndexOfAverageConsumption = CursorUtil.getColumnIndexOrThrow(_cursor, "averageConsumption");
          final int _cursorIndexOfFuelPrice = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelPrice");
          final int _cursorIndexOfCostPerKm = CursorUtil.getColumnIndexOrThrow(_cursor, "costPerKm");
          final int _cursorIndexOfIsOwned = CursorUtil.getColumnIndexOrThrow(_cursor, "isOwned");
          final int _cursorIndexOfRentalCost = CursorUtil.getColumnIndexOrThrow(_cursor, "rentalCost");
          final int _cursorIndexOfPurchaseValue = CursorUtil.getColumnIndexOrThrow(_cursor, "purchaseValue");
          final int _cursorIndexOfCurrentOdometer = CursorUtil.getColumnIndexOrThrow(_cursor, "currentOdometer");
          final int _cursorIndexOfTireLifeKm = CursorUtil.getColumnIndexOrThrow(_cursor, "tireLifeKm");
          final int _cursorIndexOfTireCost = CursorUtil.getColumnIndexOrThrow(_cursor, "tireCost");
          final int _cursorIndexOfBrakepadLifeKm = CursorUtil.getColumnIndexOrThrow(_cursor, "brakepadLifeKm");
          final int _cursorIndexOfBrakepadCost = CursorUtil.getColumnIndexOrThrow(_cursor, "brakepadCost");
          final int _cursorIndexOfOilChangeKm = CursorUtil.getColumnIndexOrThrow(_cursor, "oilChangeKm");
          final int _cursorIndexOfOilChangeCost = CursorUtil.getColumnIndexOrThrow(_cursor, "oilChangeCost");
          final int _cursorIndexOfMaintenanceIntervalKm = CursorUtil.getColumnIndexOrThrow(_cursor, "maintenanceIntervalKm");
          final int _cursorIndexOfMaintenanceCost = CursorUtil.getColumnIndexOrThrow(_cursor, "maintenanceCost");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<VehicleProfileEntity> _result = new ArrayList<VehicleProfileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final VehicleProfileEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final String _tmpBrand;
            _tmpBrand = _cursor.getString(_cursorIndexOfBrand);
            final String _tmpModel;
            _tmpModel = _cursor.getString(_cursorIndexOfModel);
            final int _tmpYear;
            _tmpYear = _cursor.getInt(_cursorIndexOfYear);
            final String _tmpPlate;
            _tmpPlate = _cursor.getString(_cursorIndexOfPlate);
            final String _tmpVehicleType;
            _tmpVehicleType = _cursor.getString(_cursorIndexOfVehicleType);
            final String _tmpFuelType;
            _tmpFuelType = _cursor.getString(_cursorIndexOfFuelType);
            final double _tmpAverageConsumption;
            _tmpAverageConsumption = _cursor.getDouble(_cursorIndexOfAverageConsumption);
            final double _tmpFuelPrice;
            _tmpFuelPrice = _cursor.getDouble(_cursorIndexOfFuelPrice);
            final double _tmpCostPerKm;
            _tmpCostPerKm = _cursor.getDouble(_cursorIndexOfCostPerKm);
            final boolean _tmpIsOwned;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsOwned);
            _tmpIsOwned = _tmp_1 != 0;
            final double _tmpRentalCost;
            _tmpRentalCost = _cursor.getDouble(_cursorIndexOfRentalCost);
            final double _tmpPurchaseValue;
            _tmpPurchaseValue = _cursor.getDouble(_cursorIndexOfPurchaseValue);
            final int _tmpCurrentOdometer;
            _tmpCurrentOdometer = _cursor.getInt(_cursorIndexOfCurrentOdometer);
            final int _tmpTireLifeKm;
            _tmpTireLifeKm = _cursor.getInt(_cursorIndexOfTireLifeKm);
            final double _tmpTireCost;
            _tmpTireCost = _cursor.getDouble(_cursorIndexOfTireCost);
            final int _tmpBrakepadLifeKm;
            _tmpBrakepadLifeKm = _cursor.getInt(_cursorIndexOfBrakepadLifeKm);
            final double _tmpBrakepadCost;
            _tmpBrakepadCost = _cursor.getDouble(_cursorIndexOfBrakepadCost);
            final int _tmpOilChangeKm;
            _tmpOilChangeKm = _cursor.getInt(_cursorIndexOfOilChangeKm);
            final double _tmpOilChangeCost;
            _tmpOilChangeCost = _cursor.getDouble(_cursorIndexOfOilChangeCost);
            final int _tmpMaintenanceIntervalKm;
            _tmpMaintenanceIntervalKm = _cursor.getInt(_cursorIndexOfMaintenanceIntervalKm);
            final double _tmpMaintenanceCost;
            _tmpMaintenanceCost = _cursor.getDouble(_cursorIndexOfMaintenanceCost);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new VehicleProfileEntity(_tmpId,_tmpIsActive,_tmpBrand,_tmpModel,_tmpYear,_tmpPlate,_tmpVehicleType,_tmpFuelType,_tmpAverageConsumption,_tmpFuelPrice,_tmpCostPerKm,_tmpIsOwned,_tmpRentalCost,_tmpPurchaseValue,_tmpCurrentOdometer,_tmpTireLifeKm,_tmpTireCost,_tmpBrakepadLifeKm,_tmpBrakepadCost,_tmpOilChangeKm,_tmpOilChangeCost,_tmpMaintenanceIntervalKm,_tmpMaintenanceCost,_tmpCreatedAt);
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
  public Flow<VehicleProfileEntity> getActiveVehicle() {
    final String _sql = "SELECT * FROM vehicle_profiles WHERE isActive = 1 LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"vehicle_profiles"}, new Callable<VehicleProfileEntity>() {
      @Override
      @Nullable
      public VehicleProfileEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfBrand = CursorUtil.getColumnIndexOrThrow(_cursor, "brand");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfYear = CursorUtil.getColumnIndexOrThrow(_cursor, "year");
          final int _cursorIndexOfPlate = CursorUtil.getColumnIndexOrThrow(_cursor, "plate");
          final int _cursorIndexOfVehicleType = CursorUtil.getColumnIndexOrThrow(_cursor, "vehicleType");
          final int _cursorIndexOfFuelType = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelType");
          final int _cursorIndexOfAverageConsumption = CursorUtil.getColumnIndexOrThrow(_cursor, "averageConsumption");
          final int _cursorIndexOfFuelPrice = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelPrice");
          final int _cursorIndexOfCostPerKm = CursorUtil.getColumnIndexOrThrow(_cursor, "costPerKm");
          final int _cursorIndexOfIsOwned = CursorUtil.getColumnIndexOrThrow(_cursor, "isOwned");
          final int _cursorIndexOfRentalCost = CursorUtil.getColumnIndexOrThrow(_cursor, "rentalCost");
          final int _cursorIndexOfPurchaseValue = CursorUtil.getColumnIndexOrThrow(_cursor, "purchaseValue");
          final int _cursorIndexOfCurrentOdometer = CursorUtil.getColumnIndexOrThrow(_cursor, "currentOdometer");
          final int _cursorIndexOfTireLifeKm = CursorUtil.getColumnIndexOrThrow(_cursor, "tireLifeKm");
          final int _cursorIndexOfTireCost = CursorUtil.getColumnIndexOrThrow(_cursor, "tireCost");
          final int _cursorIndexOfBrakepadLifeKm = CursorUtil.getColumnIndexOrThrow(_cursor, "brakepadLifeKm");
          final int _cursorIndexOfBrakepadCost = CursorUtil.getColumnIndexOrThrow(_cursor, "brakepadCost");
          final int _cursorIndexOfOilChangeKm = CursorUtil.getColumnIndexOrThrow(_cursor, "oilChangeKm");
          final int _cursorIndexOfOilChangeCost = CursorUtil.getColumnIndexOrThrow(_cursor, "oilChangeCost");
          final int _cursorIndexOfMaintenanceIntervalKm = CursorUtil.getColumnIndexOrThrow(_cursor, "maintenanceIntervalKm");
          final int _cursorIndexOfMaintenanceCost = CursorUtil.getColumnIndexOrThrow(_cursor, "maintenanceCost");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final VehicleProfileEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final String _tmpBrand;
            _tmpBrand = _cursor.getString(_cursorIndexOfBrand);
            final String _tmpModel;
            _tmpModel = _cursor.getString(_cursorIndexOfModel);
            final int _tmpYear;
            _tmpYear = _cursor.getInt(_cursorIndexOfYear);
            final String _tmpPlate;
            _tmpPlate = _cursor.getString(_cursorIndexOfPlate);
            final String _tmpVehicleType;
            _tmpVehicleType = _cursor.getString(_cursorIndexOfVehicleType);
            final String _tmpFuelType;
            _tmpFuelType = _cursor.getString(_cursorIndexOfFuelType);
            final double _tmpAverageConsumption;
            _tmpAverageConsumption = _cursor.getDouble(_cursorIndexOfAverageConsumption);
            final double _tmpFuelPrice;
            _tmpFuelPrice = _cursor.getDouble(_cursorIndexOfFuelPrice);
            final double _tmpCostPerKm;
            _tmpCostPerKm = _cursor.getDouble(_cursorIndexOfCostPerKm);
            final boolean _tmpIsOwned;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsOwned);
            _tmpIsOwned = _tmp_1 != 0;
            final double _tmpRentalCost;
            _tmpRentalCost = _cursor.getDouble(_cursorIndexOfRentalCost);
            final double _tmpPurchaseValue;
            _tmpPurchaseValue = _cursor.getDouble(_cursorIndexOfPurchaseValue);
            final int _tmpCurrentOdometer;
            _tmpCurrentOdometer = _cursor.getInt(_cursorIndexOfCurrentOdometer);
            final int _tmpTireLifeKm;
            _tmpTireLifeKm = _cursor.getInt(_cursorIndexOfTireLifeKm);
            final double _tmpTireCost;
            _tmpTireCost = _cursor.getDouble(_cursorIndexOfTireCost);
            final int _tmpBrakepadLifeKm;
            _tmpBrakepadLifeKm = _cursor.getInt(_cursorIndexOfBrakepadLifeKm);
            final double _tmpBrakepadCost;
            _tmpBrakepadCost = _cursor.getDouble(_cursorIndexOfBrakepadCost);
            final int _tmpOilChangeKm;
            _tmpOilChangeKm = _cursor.getInt(_cursorIndexOfOilChangeKm);
            final double _tmpOilChangeCost;
            _tmpOilChangeCost = _cursor.getDouble(_cursorIndexOfOilChangeCost);
            final int _tmpMaintenanceIntervalKm;
            _tmpMaintenanceIntervalKm = _cursor.getInt(_cursorIndexOfMaintenanceIntervalKm);
            final double _tmpMaintenanceCost;
            _tmpMaintenanceCost = _cursor.getDouble(_cursorIndexOfMaintenanceCost);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new VehicleProfileEntity(_tmpId,_tmpIsActive,_tmpBrand,_tmpModel,_tmpYear,_tmpPlate,_tmpVehicleType,_tmpFuelType,_tmpAverageConsumption,_tmpFuelPrice,_tmpCostPerKm,_tmpIsOwned,_tmpRentalCost,_tmpPurchaseValue,_tmpCurrentOdometer,_tmpTireLifeKm,_tmpTireCost,_tmpBrakepadLifeKm,_tmpBrakepadCost,_tmpOilChangeKm,_tmpOilChangeCost,_tmpMaintenanceIntervalKm,_tmpMaintenanceCost,_tmpCreatedAt);
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
  public Object getActiveVehicleSync(final Continuation<? super VehicleProfileEntity> $completion) {
    final String _sql = "SELECT * FROM vehicle_profiles WHERE isActive = 1 LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<VehicleProfileEntity>() {
      @Override
      @Nullable
      public VehicleProfileEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfBrand = CursorUtil.getColumnIndexOrThrow(_cursor, "brand");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfYear = CursorUtil.getColumnIndexOrThrow(_cursor, "year");
          final int _cursorIndexOfPlate = CursorUtil.getColumnIndexOrThrow(_cursor, "plate");
          final int _cursorIndexOfVehicleType = CursorUtil.getColumnIndexOrThrow(_cursor, "vehicleType");
          final int _cursorIndexOfFuelType = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelType");
          final int _cursorIndexOfAverageConsumption = CursorUtil.getColumnIndexOrThrow(_cursor, "averageConsumption");
          final int _cursorIndexOfFuelPrice = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelPrice");
          final int _cursorIndexOfCostPerKm = CursorUtil.getColumnIndexOrThrow(_cursor, "costPerKm");
          final int _cursorIndexOfIsOwned = CursorUtil.getColumnIndexOrThrow(_cursor, "isOwned");
          final int _cursorIndexOfRentalCost = CursorUtil.getColumnIndexOrThrow(_cursor, "rentalCost");
          final int _cursorIndexOfPurchaseValue = CursorUtil.getColumnIndexOrThrow(_cursor, "purchaseValue");
          final int _cursorIndexOfCurrentOdometer = CursorUtil.getColumnIndexOrThrow(_cursor, "currentOdometer");
          final int _cursorIndexOfTireLifeKm = CursorUtil.getColumnIndexOrThrow(_cursor, "tireLifeKm");
          final int _cursorIndexOfTireCost = CursorUtil.getColumnIndexOrThrow(_cursor, "tireCost");
          final int _cursorIndexOfBrakepadLifeKm = CursorUtil.getColumnIndexOrThrow(_cursor, "brakepadLifeKm");
          final int _cursorIndexOfBrakepadCost = CursorUtil.getColumnIndexOrThrow(_cursor, "brakepadCost");
          final int _cursorIndexOfOilChangeKm = CursorUtil.getColumnIndexOrThrow(_cursor, "oilChangeKm");
          final int _cursorIndexOfOilChangeCost = CursorUtil.getColumnIndexOrThrow(_cursor, "oilChangeCost");
          final int _cursorIndexOfMaintenanceIntervalKm = CursorUtil.getColumnIndexOrThrow(_cursor, "maintenanceIntervalKm");
          final int _cursorIndexOfMaintenanceCost = CursorUtil.getColumnIndexOrThrow(_cursor, "maintenanceCost");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final VehicleProfileEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final String _tmpBrand;
            _tmpBrand = _cursor.getString(_cursorIndexOfBrand);
            final String _tmpModel;
            _tmpModel = _cursor.getString(_cursorIndexOfModel);
            final int _tmpYear;
            _tmpYear = _cursor.getInt(_cursorIndexOfYear);
            final String _tmpPlate;
            _tmpPlate = _cursor.getString(_cursorIndexOfPlate);
            final String _tmpVehicleType;
            _tmpVehicleType = _cursor.getString(_cursorIndexOfVehicleType);
            final String _tmpFuelType;
            _tmpFuelType = _cursor.getString(_cursorIndexOfFuelType);
            final double _tmpAverageConsumption;
            _tmpAverageConsumption = _cursor.getDouble(_cursorIndexOfAverageConsumption);
            final double _tmpFuelPrice;
            _tmpFuelPrice = _cursor.getDouble(_cursorIndexOfFuelPrice);
            final double _tmpCostPerKm;
            _tmpCostPerKm = _cursor.getDouble(_cursorIndexOfCostPerKm);
            final boolean _tmpIsOwned;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsOwned);
            _tmpIsOwned = _tmp_1 != 0;
            final double _tmpRentalCost;
            _tmpRentalCost = _cursor.getDouble(_cursorIndexOfRentalCost);
            final double _tmpPurchaseValue;
            _tmpPurchaseValue = _cursor.getDouble(_cursorIndexOfPurchaseValue);
            final int _tmpCurrentOdometer;
            _tmpCurrentOdometer = _cursor.getInt(_cursorIndexOfCurrentOdometer);
            final int _tmpTireLifeKm;
            _tmpTireLifeKm = _cursor.getInt(_cursorIndexOfTireLifeKm);
            final double _tmpTireCost;
            _tmpTireCost = _cursor.getDouble(_cursorIndexOfTireCost);
            final int _tmpBrakepadLifeKm;
            _tmpBrakepadLifeKm = _cursor.getInt(_cursorIndexOfBrakepadLifeKm);
            final double _tmpBrakepadCost;
            _tmpBrakepadCost = _cursor.getDouble(_cursorIndexOfBrakepadCost);
            final int _tmpOilChangeKm;
            _tmpOilChangeKm = _cursor.getInt(_cursorIndexOfOilChangeKm);
            final double _tmpOilChangeCost;
            _tmpOilChangeCost = _cursor.getDouble(_cursorIndexOfOilChangeCost);
            final int _tmpMaintenanceIntervalKm;
            _tmpMaintenanceIntervalKm = _cursor.getInt(_cursorIndexOfMaintenanceIntervalKm);
            final double _tmpMaintenanceCost;
            _tmpMaintenanceCost = _cursor.getDouble(_cursorIndexOfMaintenanceCost);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new VehicleProfileEntity(_tmpId,_tmpIsActive,_tmpBrand,_tmpModel,_tmpYear,_tmpPlate,_tmpVehicleType,_tmpFuelType,_tmpAverageConsumption,_tmpFuelPrice,_tmpCostPerKm,_tmpIsOwned,_tmpRentalCost,_tmpPurchaseValue,_tmpCurrentOdometer,_tmpTireLifeKm,_tmpTireCost,_tmpBrakepadLifeKm,_tmpBrakepadCost,_tmpOilChangeKm,_tmpOilChangeCost,_tmpMaintenanceIntervalKm,_tmpMaintenanceCost,_tmpCreatedAt);
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
