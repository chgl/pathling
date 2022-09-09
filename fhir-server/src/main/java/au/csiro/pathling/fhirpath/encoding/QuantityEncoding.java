/*
 * Copyright © 2018-2022, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.fhirpath.encoding;

import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.struct;

import au.csiro.pathling.encoders.QuantitySupport;
import au.csiro.pathling.encoders.datatypes.DecimalCustomCoder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.MetadataBuilder;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Quantity.QuantityComparator;

public class QuantityEncoding {

  public static final String CANONICALIZED_VALUE_COLUMN = QuantitySupport.VALUE_CANONICALIZED_FIELD_NAME();
  public static final String CANONICALIZED_CODE_COLUMN = QuantitySupport.CODE_CANONICALIZED_FIELD_NAME();

  @Nullable
  public static Row encode(@Nullable final Quantity quantity) {
    if (quantity == null) {
      return null;
    }
    final String comparator = Optional.ofNullable(quantity.getComparator())
        .map(QuantityComparator::toCode).orElse(null);
    if (quantity.getValue().scale() > DecimalCustomCoder.scale()) {
      quantity.setValue(
          quantity.getValue().setScale(DecimalCustomCoder.scale(), RoundingMode.HALF_UP));
    }
    return RowFactory.create(quantity.getId(), quantity.getValue(), quantity.getValue().scale(),
        comparator, quantity.getUnit(), quantity.getSystem(), quantity.getCode(), null /* _fid */);
  }

  @Nonnull
  public static Quantity decode(@Nonnull final Row row) {
    final Quantity quantity = new Quantity();

    Optional.ofNullable(row.getString(0)).ifPresent(quantity::setId);

    // The value gets converted to a BigDecimal, taking into account the scale that has been encoded 
    // alongside it.
    final int scale = row.getInt(2);
    final BigDecimal value = Optional.ofNullable(row.getDecimal(1))
        .map(bd -> bd.scale() > DecimalCustomCoder.scale()
                   ? bd.setScale(scale, RoundingMode.HALF_UP)
                   : bd)
        .orElse(null);
    quantity.setValue(value);

    // The comparator is encoded as a string code, we need to convert it back to an enum.
    Optional.ofNullable(row.getString(3))
        .map(QuantityComparator::fromCode)
        .ifPresent(quantity::setComparator);

    Optional.ofNullable(row.getString(4)).ifPresent(quantity::setUnit);
    Optional.ofNullable(row.getString(5)).ifPresent(quantity::setSystem);
    Optional.ofNullable(row.getString(6)).ifPresent(quantity::setCode);

    return quantity;
  }

  @Nonnull
  public static StructType dataType() {
    final Metadata metadata = new MetadataBuilder().build();
    final StructField id = new StructField("id", DataTypes.StringType, true, metadata);
    final StructField value = new StructField("value", DataTypes.createDecimalType(
        DecimalCustomCoder.precision(), DecimalCustomCoder.scale()), true, metadata);
    final StructField valueScale = new StructField("value_scale", DataTypes.IntegerType, true,
        metadata);
    final StructField comparator = new StructField("comparator", DataTypes.StringType, true,
        metadata);
    final StructField unit = new StructField("unit", DataTypes.StringType, true, metadata);
    final StructField system = new StructField("system", DataTypes.StringType, true, metadata);
    final StructField code = new StructField("code", DataTypes.StringType, true, metadata);
    final StructField canonicalizedValue = new StructField(CANONICALIZED_VALUE_COLUMN,
        DataTypes.createDecimalType(
            DecimalCustomCoder.precision(), DecimalCustomCoder.scale()), true, metadata);
    final StructField canonicalizedCode = new StructField(CANONICALIZED_CODE_COLUMN,
        DataTypes.StringType, true, metadata);
    final StructField fid = new StructField("_fid", DataTypes.IntegerType, true,
        metadata);
    return new StructType(
        new StructField[]{id, value, valueScale, comparator, unit, system, code, canonicalizedValue,
            canonicalizedCode, fid});
  }
  
  @Nonnull
  public static Column toStruct(
      @Nonnull final Column id,
      @Nonnull final Column value,
      @Nonnull final Column value_scale,
      @Nonnull final Column comparator,
      @Nonnull final Column unit,
      @Nonnull final Column system,
      @Nonnull final Column code,
      @Nonnull final Column canonicalizedValue,
      @Nonnull final Column canonicalizedCode,
      @Nonnull final Column _fid
  ) {
    return struct(
        id.as("id"),
        value.as("value"),
        value_scale.as("value_scale"),
        comparator.as("comparator"),
        unit.as("unit"),
        system.as("system"),
        code.as("code"),
        canonicalizedValue.as(CANONICALIZED_VALUE_COLUMN),
        canonicalizedCode.as(CANONICALIZED_CODE_COLUMN),
        _fid.as("_fid")
    );
  }

}
