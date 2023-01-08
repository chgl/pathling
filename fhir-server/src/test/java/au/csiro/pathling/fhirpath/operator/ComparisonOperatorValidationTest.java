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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import au.csiro.pathling.errors.InvalidUserInputError;
import au.csiro.pathling.fhirpath.element.ElementPath;
import au.csiro.pathling.fhirpath.parser.ParserContext;
import au.csiro.pathling.test.builders.ElementPathBuilder;
import au.csiro.pathling.test.builders.ParserContextBuilder;
import ca.uhn.fhir.context.FhirContext;
import org.apache.spark.sql.SparkSession;
import org.hl7.fhir.r4.model.Enumerations.FHIRDefinedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author John Grimes
 */
@SpringBootTest
@Tag("UnitTest")
class ComparisonOperatorValidationTest {

  @Autowired
  SparkSession spark;

  @Autowired
  FhirContext fhirContext;

  ParserContext parserContext;

  @BeforeEach
  void setUp() {
    parserContext = new ParserContextBuilder(spark, fhirContext).build();
  }

  @Test
  void operandsAreNotComparable() {
    final ElementPath left = new ElementPathBuilder(spark)
        .fhirType(FHIRDefinedType.BOOLEAN)
        .singular(true)
        .expression("foo")
        .build();
    final ElementPath right = new ElementPathBuilder(spark)
        .fhirType(FHIRDefinedType.STRING)
        .singular(true)
        .expression("bar")
        .build();

    final OperatorInput input = new OperatorInput(parserContext, left, right);
    final Operator comparisonOperator = Operator.getInstance(">");
    final InvalidUserInputError error = assertThrows(
        InvalidUserInputError.class,
        () -> comparisonOperator.invoke(input));
    assertEquals("Left operand to > operator is not comparable to right operand: foo > bar",
        error.getMessage());

    // Now test the right operand.
    final OperatorInput reversedInput = new OperatorInput(parserContext, right, left);
    final InvalidUserInputError reversedError = assertThrows(
        InvalidUserInputError.class,
        () -> comparisonOperator.invoke(reversedInput));
    assertEquals(
        "Left operand to > operator is not comparable to right operand: bar > foo",
        reversedError.getMessage());
  }

}
