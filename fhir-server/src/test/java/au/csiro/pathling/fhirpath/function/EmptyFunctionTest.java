/*
 * Copyright © 2018-2020, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.fhirpath.function;

import static au.csiro.pathling.test.assertions.Assertions.assertThat;
import static au.csiro.pathling.test.helpers.SparkHelpers.codeableConceptStructType;
import static au.csiro.pathling.test.helpers.SparkHelpers.rowFromCodeableConcept;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import au.csiro.pathling.errors.InvalidUserInputError;
import au.csiro.pathling.fhirpath.FhirPath;
import au.csiro.pathling.fhirpath.element.BooleanPath;
import au.csiro.pathling.fhirpath.element.ElementPath;
import au.csiro.pathling.fhirpath.literal.StringLiteralPath;
import au.csiro.pathling.fhirpath.parser.ParserContext;
import au.csiro.pathling.test.builders.DatasetBuilder;
import au.csiro.pathling.test.builders.ElementPathBuilder;
import au.csiro.pathling.test.builders.ParserContextBuilder;
import au.csiro.pathling.test.helpers.TestHelpers;
import java.util.Collections;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.DataTypes;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations.FHIRDefinedType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * @author John Grimes
 */
@Tag("UnitTest")
public class EmptyFunctionTest {

  @Test
  public void returnsCorrectResults() {
    final Coding coding1 = new Coding(TestHelpers.SNOMED_URL, "840546002", "Exposure to COVID-19");
    final CodeableConcept concept1 = new CodeableConcept(coding1);
    final Coding coding2 = new Coding(TestHelpers.SNOMED_URL, "248427009", "Fever symptoms");
    final CodeableConcept concept2 = new CodeableConcept(coding2);

    final ParserContext parserContext = new ParserContextBuilder().build();
    final Dataset<Row> dataset = new DatasetBuilder()
        .withIdColumn()
        .withColumn(codeableConceptStructType())
        .withRow("Observation/abc1", null)
        .withRow("Observation/abc2", null)
        .withRow("Observation/abc2", null)
        .withRow("Observation/abc3", rowFromCodeableConcept(concept1))
        .withRow("Observation/abc4", rowFromCodeableConcept(concept1))
        .withRow("Observation/abc4", null)
        .withRow("Observation/abc5", rowFromCodeableConcept(concept1))
        .withRow("Observation/abc5", rowFromCodeableConcept(concept2))
        .build();
    final ElementPath input = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODEABLECONCEPT)
        .dataset(dataset)
        .idAndValueColumns()
        .expression("code")
        .build();

    // Set up the function input.
    final NamedFunctionInput emptyInput = new NamedFunctionInput(parserContext, input,
        Collections.emptyList());

    // Invoke the function.
    final NamedFunction emptyFunction = NamedFunction.getInstance("empty");
    final FhirPath result = emptyFunction.invoke(emptyInput);

    // Check the result.
    final Dataset<Row> expectedDataset = new DatasetBuilder()
        .withIdColumn()
        .withColumn(DataTypes.BooleanType)
        .withRow("Observation/abc1", true)
        .withRow("Observation/abc2", true)
        .withRow("Observation/abc3", false)
        .withRow("Observation/abc4", false)
        .withRow("Observation/abc5", false)
        .build();
    assertThat(result)
        .hasExpression("code.empty()")
        .isSingular()
        .isElementPath(BooleanPath.class)
        .selectResult()
        .hasRows(expectedDataset);
  }

  @Test
  public void inputMustNotContainArguments() {
    final ElementPath input = new ElementPathBuilder().build();
    final StringLiteralPath argument = StringLiteralPath
        .fromString("'some argument'", input);

    final ParserContext parserContext = new ParserContextBuilder().build();
    final NamedFunctionInput emptyInput = new NamedFunctionInput(parserContext, input,
        Collections.singletonList(argument));

    final NamedFunction emptyFunction = NamedFunction.getInstance("empty");
    final InvalidUserInputError error = assertThrows(
        InvalidUserInputError.class,
        () -> emptyFunction.invoke(emptyInput));
    assertEquals(
        "Arguments can not be passed to empty function",
        error.getMessage());
  }

}