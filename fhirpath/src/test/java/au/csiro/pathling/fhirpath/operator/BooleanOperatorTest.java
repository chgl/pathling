/*
 * Copyright 2023 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.csiro.pathling.fhirpath.operator;

import static au.csiro.pathling.test.assertions.Assertions.assertThat;

import au.csiro.pathling.fhirpath.FhirPath;
import au.csiro.pathling.fhirpath.literal.BooleanLiteralPath;
import au.csiro.pathling.fhirpath.parser.ParserContext;
import au.csiro.pathling.test.SpringBootUnitTest;
import au.csiro.pathling.test.builders.DatasetBuilder;
import au.csiro.pathling.test.builders.ElementPathBuilder;
import au.csiro.pathling.test.builders.ParserContextBuilder;
import ca.uhn.fhir.context.FhirContext;
import java.util.Collections;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.hl7.fhir.r4.model.Enumerations.FHIRDefinedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author John Grimes
 */
@SpringBootUnitTest
class BooleanOperatorTest {

  @Autowired
  SparkSession spark;

  @Autowired
  FhirContext fhirContext;

  static final String ID_ALIAS = "_abc123";

  FhirPath left;
  FhirPath right;
  ParserContext parserContext;

  @BeforeEach
  void setUp() {
    final Dataset<Row> leftDataset = new DatasetBuilder(spark)
        .withIdColumn(ID_ALIAS)
        .withColumn(DataTypes.BooleanType)
        .withRow("patient-1", true)
        .withRow("patient-2", true)
        .withRow("patient-3", false)
        .withRow("patient-4", false)
        .withRow("patient-5", null)
        .withRow("patient-6", null)
        .withRow("patient-7", true)
        .withRow("patient-8", null)
        .build();
    left = new ElementPathBuilder(spark)
        .fhirType(FHIRDefinedType.BOOLEAN)
        .dataset(leftDataset)
        .idAndValueColumns()
        .singular(true)
        .expression("estimatedAge")
        .build();

    final Dataset<Row> rightDataset = new DatasetBuilder(spark)
        .withIdColumn(ID_ALIAS)
        .withColumn(DataTypes.BooleanType)
        .withRow("patient-1", false)
        .withRow("patient-2", null)
        .withRow("patient-3", true)
        .withRow("patient-4", null)
        .withRow("patient-5", true)
        .withRow("patient-6", false)
        .withRow("patient-7", true)
        .withRow("patient-8", null)
        .build();
    right = new ElementPathBuilder(spark)
        .fhirType(FHIRDefinedType.BOOLEAN)
        .dataset(rightDataset)
        .idAndValueColumns()
        .singular(true)
        .expression("deceasedBoolean")
        .build();

    parserContext = new ParserContextBuilder(spark, fhirContext)
        .groupingColumns(Collections.singletonList(left.getIdColumn()))
        .build();
  }

  @Test
  void and() {
    final OperatorInput input = new OperatorInput(parserContext, left, right);

    final Operator booleanOperator = Operator.getInstance("and");
    final FhirPath result = booleanOperator.invoke(input);

    assertThat(result).selectOrderedResult().hasRows(
        RowFactory.create("patient-1", false),
        RowFactory.create("patient-2", null),
        RowFactory.create("patient-3", false),
        RowFactory.create("patient-4", false),
        RowFactory.create("patient-5", null),
        RowFactory.create("patient-6", false),
        RowFactory.create("patient-7", true),
        RowFactory.create("patient-8", null)
    );
  }

  @Test
  void or() {
    final OperatorInput input = new OperatorInput(parserContext, left, right);

    final Operator booleanOperator = Operator.getInstance("or");
    final FhirPath result = booleanOperator.invoke(input);

    assertThat(result).selectOrderedResult().hasRows(
        RowFactory.create("patient-1", true),
        RowFactory.create("patient-2", true),
        RowFactory.create("patient-3", true),
        RowFactory.create("patient-4", null),
        RowFactory.create("patient-5", true),
        RowFactory.create("patient-6", null),
        RowFactory.create("patient-7", true),
        RowFactory.create("patient-8", null)
    );
  }

  @Test
  void xor() {
    final OperatorInput input = new OperatorInput(parserContext, left, right);

    final Operator booleanOperator = Operator.getInstance("xor");
    final FhirPath result = booleanOperator.invoke(input);

    assertThat(result).selectOrderedResult().hasRows(
        RowFactory.create("patient-1", true),
        RowFactory.create("patient-2", null),
        RowFactory.create("patient-3", true),
        RowFactory.create("patient-4", null),
        RowFactory.create("patient-5", null),
        RowFactory.create("patient-6", null),
        RowFactory.create("patient-7", false),
        RowFactory.create("patient-8", null)
    );
  }

  @Test
  void implies() {
    final OperatorInput input = new OperatorInput(parserContext, left, right);

    final Operator booleanOperator = Operator.getInstance("implies");
    final FhirPath result = booleanOperator.invoke(input);

    assertThat(result).selectOrderedResult().hasRows(
        RowFactory.create("patient-1", false),
        RowFactory.create("patient-2", null),
        RowFactory.create("patient-3", true),
        RowFactory.create("patient-4", true),
        RowFactory.create("patient-5", true),
        RowFactory.create("patient-6", null),
        RowFactory.create("patient-7", true),
        RowFactory.create("patient-8", null)
    );
  }

  @Test
  void leftIsLiteral() {
    final FhirPath literalLeft = BooleanLiteralPath.fromString("true", left);
    final OperatorInput input = new OperatorInput(parserContext, literalLeft, right);

    final Operator booleanOperator = Operator.getInstance("and");
    final FhirPath result = booleanOperator.invoke(input);

    assertThat(result).selectOrderedResult().hasRows(
        RowFactory.create("patient-1", false),
        RowFactory.create("patient-2", null),
        RowFactory.create("patient-3", true),
        RowFactory.create("patient-4", null),
        RowFactory.create("patient-5", true),
        RowFactory.create("patient-6", false),
        RowFactory.create("patient-7", true),
        RowFactory.create("patient-8", null)
    );
  }

  @Test
  void rightIsLiteral() {
    final FhirPath literalRight = BooleanLiteralPath.fromString("true", right);
    final OperatorInput input = new OperatorInput(parserContext, left, literalRight);

    final Operator booleanOperator = Operator.getInstance("and");
    final FhirPath result = booleanOperator.invoke(input);

    assertThat(result).selectOrderedResult().hasRows(
        RowFactory.create("patient-1", true),
        RowFactory.create("patient-2", true),
        RowFactory.create("patient-3", false),
        RowFactory.create("patient-4", false),
        RowFactory.create("patient-5", null),
        RowFactory.create("patient-6", null),
        RowFactory.create("patient-7", true),
        RowFactory.create("patient-8", null)
    );
  }
}
