package com.ngbautoroad.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class VehicleConfigDao_Impl implements VehicleConfigDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<VehicleConfigEntity> __insertionAdapterOfVehicleConfigEntity;

  public VehicleConfigDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfVehicleConfigEntity = new EntityInsertionAdapter<VehicleConfigEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `vehicle_config` (`id`,`vehicleType`,`fuelType`,`brand`,`model`,`year`,`plate`,`averageConsumption`,`fuelPrice`,`costPerKm`,`monthlyFixedCosts`,`isOwned`,`rentalCost`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final VehicleConfigEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getVehicleType());
        statement.bindString(3, entity.getFuelType());
        statement.bindString(4, entity.getBrand());
        statement.bindString(5, entity.getModel());
        statement.bindLong(6, entity.getYear());
        statement.bindString(7, entity.getPlate());
        statement.bindDouble(8, entity.getAverageConsumption());
        statement.bindDouble(9, entity.getFuelPrice());
        statement.bindDouble(10, entity.getCostPerKm());
        statement.bindDouble(11, entity.getMonthlyFixedCosts());
        final int _tmp = entity.isOwned() ? 1 : 0;
        statement.bindLong(12, _tmp);
        statement.bindDouble(13, entity.getRentalCost());
      }
    };
  }

  @Override
  public Object save(final VehicleConfigEntity config,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfVehicleConfigEntity.insert(config);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<VehicleConfigEntity> getConfig() {
    final String _sql = "SELECT * FROM vehicle_config WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"vehicle_config"}, new Callable<VehicleConfigEntity>() {
      @Override
      @Nullable
      public VehicleConfigEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVehicleType = CursorUtil.getColumnIndexOrThrow(_cursor, "vehicleType");
          final int _cursorIndexOfFuelType = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelType");
          final int _cursorIndexOfBrand = CursorUtil.getColumnIndexOrThrow(_cursor, "brand");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfYear = CursorUtil.getColumnIndexOrThrow(_cursor, "year");
          final int _cursorIndexOfPlate = CursorUtil.getColumnIndexOrThrow(_cursor, "plate");
          final int _cursorIndexOfAverageConsumption = CursorUtil.getColumnIndexOrThrow(_cursor, "averageConsumption");
          final int _cursorIndexOfFuelPrice = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelPrice");
          final int _cursorIndexOfCostPerKm = CursorUtil.getColumnIndexOrThrow(_cursor, "costPerKm");
          final int _cursorIndexOfMonthlyFixedCosts = CursorUtil.getColumnIndexOrThrow(_cursor, "monthlyFixedCosts");
          final int _cursorIndexOfIsOwned = CursorUtil.getColumnIndexOrThrow(_cursor, "isOwned");
          final int _cursorIndexOfRentalCost = CursorUtil.getColumnIndexOrThrow(_cursor, "rentalCost");
          final VehicleConfigEntity _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpVehicleType;
            _tmpVehicleType = _cursor.getString(_cursorIndexOfVehicleType);
            final String _tmpFuelType;
            _tmpFuelType = _cursor.getString(_cursorIndexOfFuelType);
            final String _tmpBrand;
            _tmpBrand = _cursor.getString(_cursorIndexOfBrand);
            final String _tmpModel;
            _tmpModel = _cursor.getString(_cursorIndexOfModel);
            final int _tmpYear;
            _tmpYear = _cursor.getInt(_cursorIndexOfYear);
            final String _tmpPlate;
            _tmpPlate = _cursor.getString(_cursorIndexOfPlate);
            final double _tmpAverageConsumption;
            _tmpAverageConsumption = _cursor.getDouble(_cursorIndexOfAverageConsumption);
            final double _tmpFuelPrice;
            _tmpFuelPrice = _cursor.getDouble(_cursorIndexOfFuelPrice);
            final double _tmpCostPerKm;
            _tmpCostPerKm = _cursor.getDouble(_cursorIndexOfCostPerKm);
            final double _tmpMonthlyFixedCosts;
            _tmpMonthlyFixedCosts = _cursor.getDouble(_cursorIndexOfMonthlyFixedCosts);
            final boolean _tmpIsOwned;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsOwned);
            _tmpIsOwned = _tmp != 0;
            final double _tmpRentalCost;
            _tmpRentalCost = _cursor.getDouble(_cursorIndexOfRentalCost);
            _result = new VehicleConfigEntity(_tmpId,_tmpVehicleType,_tmpFuelType,_tmpBrand,_tmpModel,_tmpYear,_tmpPlate,_tmpAverageConsumption,_tmpFuelPrice,_tmpCostPerKm,_tmpMonthlyFixedCosts,_tmpIsOwned,_tmpRentalCost);
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
  public Object getConfigSync(final Continuation<? super VehicleConfigEntity> $completion) {
    final String _sql = "SELECT * FROM vehicle_config WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<VehicleConfigEntity>() {
      @Override
      @Nullable
      public VehicleConfigEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVehicleType = CursorUtil.getColumnIndexOrThrow(_cursor, "vehicleType");
          final int _cursorIndexOfFuelType = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelType");
          final int _cursorIndexOfBrand = CursorUtil.getColumnIndexOrThrow(_cursor, "brand");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfYear = CursorUtil.getColumnIndexOrThrow(_cursor, "year");
          final int _cursorIndexOfPlate = CursorUtil.getColumnIndexOrThrow(_cursor, "plate");
          final int _cursorIndexOfAverageConsumption = CursorUtil.getColumnIndexOrThrow(_cursor, "averageConsumption");
          final int _cursorIndexOfFuelPrice = CursorUtil.getColumnIndexOrThrow(_cursor, "fuelPrice");
          final int _cursorIndexOfCostPerKm = CursorUtil.getColumnIndexOrThrow(_cursor, "costPerKm");
          final int _cursorIndexOfMonthlyFixedCosts = CursorUtil.getColumnIndexOrThrow(_cursor, "monthlyFixedCosts");
          final int _cursorIndexOfIsOwned = CursorUtil.getColumnIndexOrThrow(_cursor, "isOwned");
          final int _cursorIndexOfRentalCost = CursorUtil.getColumnIndexOrThrow(_cursor, "rentalCost");
          final VehicleConfigEntity _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpVehicleType;
            _tmpVehicleType = _cursor.getString(_cursorIndexOfVehicleType);
            final String _tmpFuelType;
            _tmpFuelType = _cursor.getString(_cursorIndexOfFuelType);
            final String _tmpBrand;
            _tmpBrand = _cursor.getString(_cursorIndexOfBrand);
            final String _tmpModel;
            _tmpModel = _cursor.getString(_cursorIndexOfModel);
            final int _tmpYear;
            _tmpYear = _cursor.getInt(_cursorIndexOfYear);
            final String _tmpPlate;
            _tmpPlate = _cursor.getString(_cursorIndexOfPlate);
            final double _tmpAverageConsumption;
            _tmpAverageConsumption = _cursor.getDouble(_cursorIndexOfAverageConsumption);
            final double _tmpFuelPrice;
            _tmpFuelPrice = _cursor.getDouble(_cursorIndexOfFuelPrice);
            final double _tmpCostPerKm;
            _tmpCostPerKm = _cursor.getDouble(_cursorIndexOfCostPerKm);
            final double _tmpMonthlyFixedCosts;
            _tmpMonthlyFixedCosts = _cursor.getDouble(_cursorIndexOfMonthlyFixedCosts);
            final boolean _tmpIsOwned;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsOwned);
            _tmpIsOwned = _tmp != 0;
            final double _tmpRentalCost;
            _tmpRentalCost = _cursor.getDouble(_cursorIndexOfRentalCost);
            _result = new VehicleConfigEntity(_tmpId,_tmpVehicleType,_tmpFuelType,_tmpBrand,_tmpModel,_tmpYear,_tmpPlate,_tmpAverageConsumption,_tmpFuelPrice,_tmpCostPerKm,_tmpMonthlyFixedCosts,_tmpIsOwned,_tmpRentalCost);
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
