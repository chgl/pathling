/*
 * Copyright © 2018-2021, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.fhirpath.function.subsumes;

import static au.csiro.pathling.test.assertions.Assertions.assertThat;
import static au.csiro.pathling.test.builders.DatasetBuilder.makeEid;
import static au.csiro.pathling.test.helpers.SparkHelpers.codeableConceptStructType;
import static au.csiro.pathling.test.helpers.SparkHelpers.codingStructType;
import static au.csiro.pathling.test.helpers.SparkHelpers.rowFromCodeableConcept;
import static au.csiro.pathling.test.helpers.SparkHelpers.rowFromCoding;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import au.csiro.pathling.errors.InvalidUserInputError;
import au.csiro.pathling.fhir.TerminologyClient;
import au.csiro.pathling.fhir.TerminologyClientFactory;
import au.csiro.pathling.fhirpath.FhirPath;
import au.csiro.pathling.fhirpath.NonLiteralPath;
import au.csiro.pathling.fhirpath.element.BooleanPath;
import au.csiro.pathling.fhirpath.element.CodingPath;
import au.csiro.pathling.fhirpath.element.ElementPath;
import au.csiro.pathling.fhirpath.function.NamedFunction;
import au.csiro.pathling.fhirpath.function.NamedFunctionInput;
import au.csiro.pathling.fhirpath.literal.CodingLiteralPath;
import au.csiro.pathling.fhirpath.literal.StringLiteralPath;
import au.csiro.pathling.fhirpath.parser.ParserContext;
import au.csiro.pathling.test.assertions.DatasetAssert;
import au.csiro.pathling.test.assertions.FhirPathAssertion;
import au.csiro.pathling.test.builders.DatasetBuilder;
import au.csiro.pathling.test.builders.ElementPathBuilder;
import au.csiro.pathling.test.builders.ParserContextBuilder;
import au.csiro.pathling.test.fixtures.ConceptMapEntry;
import au.csiro.pathling.test.fixtures.ConceptMapFixtures;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.DataTypes;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.Enumerations.FHIRDefinedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * @author Piotr Szul
 */
@Tag("UnitTest")
public class SubsumesFunctionTest {

  private TerminologyClient terminologyClient;
  private TerminologyClientFactory terminologyClientFactory;

  private static final String TEST_SYSTEM = "uuid:1";

  private static final Coding CODING_SMALL = new Coding(TEST_SYSTEM, "SMALL", null);
  private static final Coding CODING_MEDIUM = new Coding(TEST_SYSTEM, "MEDIUM", null);
  private static final Coding CODING_LARGE = new Coding(TEST_SYSTEM, "LARGE", null);
  private static final Coding CODING_OTHER1 = new Coding(TEST_SYSTEM, "OTHER1", null);
  private static final Coding CODING_OTHER2 = new Coding(TEST_SYSTEM, "OTHER2", null);
  private static final Coding CODING_OTHER3 = new Coding(TEST_SYSTEM, "OTHER3", null);
  private static final Coding CODING_OTHER4 = new Coding(TEST_SYSTEM, "OTHER4", null);
  private static final Coding CODING_OTHER5 = new Coding(TEST_SYSTEM, "OTHER5", null);

  private static final String RES_ID1 = "Condition/xyz1";
  private static final String RES_ID2 = "Condition/xyz2";
  private static final String RES_ID3 = "Condition/xyz3";
  private static final String RES_ID4 = "Condition/xyz4";
  private static final String RES_ID5 = "Condition/xyz5";

  // coding_large -- subsumes --> coding_medium --> subsumes --> coding_small
  private static final ConceptMap MAP_LARGE_MEDIUM_SMALL =
      ConceptMapFixtures.createConceptMap(ConceptMapEntry.subsumesOf(CODING_MEDIUM, CODING_LARGE),
          ConceptMapEntry.specializesOf(CODING_MEDIUM, CODING_SMALL));

  private static final List<String> ALL_RES_IDS =
      Arrays.asList(RES_ID1, RES_ID2, RES_ID3, RES_ID4, RES_ID5);

  private static Row codeableConceptRowFromCoding(final Coding coding) {
    return codeableConceptRowFromCoding(coding, CODING_OTHER4);
  }

  private static Row codeableConceptRowFromCoding(final Coding coding, final Coding otherCoding) {
    return rowFromCodeableConcept(new CodeableConcept(coding).addCoding(otherCoding));
  }

  @BeforeEach
  public void setUp() {
    // NOTE: We need to make TerminologyClient mock serializable so that the TerminologyClientFactory
    // mock is serializable too.
    terminologyClient = mock(TerminologyClient.class, Mockito.withSettings().serializable());
    terminologyClientFactory = mock(TerminologyClientFactory.class,
        Mockito.withSettings().serializable());
    when(terminologyClientFactory.build(any())).thenReturn(terminologyClient);
    when(terminologyClient.closure(any(), any())).thenReturn(MAP_LARGE_MEDIUM_SMALL);
  }

  private static CodingPath createCodingInput() {
    final Dataset<Row> dataset = new DatasetBuilder()
        .withIdColumn()
        .withEidColumn()
        .withStructTypeColumns(codingStructType())
        .withRow(RES_ID1, makeEid(1), rowFromCoding(CODING_SMALL))
        .withRow(RES_ID2, makeEid(1), rowFromCoding(CODING_MEDIUM))
        .withRow(RES_ID3, makeEid(1), rowFromCoding(CODING_LARGE))
        .withRow(RES_ID4, makeEid(1), rowFromCoding(CODING_OTHER1))
        .withRow(RES_ID5, null, null /* NULL coding value */)
        .withRow(RES_ID1, makeEid(0), rowFromCoding(CODING_OTHER2))
        .withRow(RES_ID2, makeEid(0), rowFromCoding(CODING_OTHER2))
        .withRow(RES_ID3, makeEid(0), rowFromCoding(CODING_OTHER2))
        .withRow(RES_ID4, makeEid(0), rowFromCoding(CODING_OTHER2))
        .buildWithStructValue();
    final ElementPath inputExpression = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODING)
        .dataset(dataset)
        .idAndEidAndValueColumns()
        .singular(false)
        .build();

    return (CodingPath) inputExpression;
  }

  private static CodingPath createSingularCodingInput() {
    final Dataset<Row> dataset = new DatasetBuilder()
        .withIdColumn()
        .withStructTypeColumns(codingStructType())
        .withRow(RES_ID1, rowFromCoding(CODING_SMALL))
        .withRow(RES_ID2, rowFromCoding(CODING_MEDIUM))
        .withRow(RES_ID3, rowFromCoding(CODING_LARGE))
        .withRow(RES_ID4, rowFromCoding(CODING_OTHER1))
        .withRow(RES_ID5, null /* NULL coding value */)
        .buildWithStructValue();
    final ElementPath inputExpression = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODING)
        .dataset(dataset)
        .idAndValueColumns()
        .singular(true)
        .build();

    return (CodingPath) inputExpression;
  }


  private static ElementPath createCodeableConceptInput() {
    final Dataset<Row> dataset = new DatasetBuilder()
        .withIdColumn()
        .withEidColumn()
        .withStructTypeColumns(codeableConceptStructType())
        .withRow(RES_ID1, makeEid(1), codeableConceptRowFromCoding(CODING_SMALL))
        .withRow(RES_ID2, makeEid(1), codeableConceptRowFromCoding(CODING_MEDIUM))
        .withRow(RES_ID3, makeEid(1), codeableConceptRowFromCoding(CODING_LARGE))
        .withRow(RES_ID4, makeEid(1), codeableConceptRowFromCoding(CODING_OTHER1))
        .withRow(RES_ID5, null, null /* NULL codeable concept value */)
        .withRow(RES_ID1, makeEid(0), codeableConceptRowFromCoding(CODING_OTHER2))
        .withRow(RES_ID2, makeEid(0), codeableConceptRowFromCoding(CODING_OTHER2))
        .withRow(RES_ID3, makeEid(0), codeableConceptRowFromCoding(CODING_OTHER2))
        .withRow(RES_ID4, makeEid(0), codeableConceptRowFromCoding(CODING_OTHER2))
        .buildWithStructValue();

    return new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODEABLECONCEPT)
        .dataset(dataset)
        .idAndEidAndValueColumns()
        .singular(false)
        .build();
  }

  private static CodingLiteralPath createLiteralArgOrInput() {
    final Dataset<Row> literalContextDataset = new DatasetBuilder()
        .withIdColumn()
        .withColumn(DataTypes.BooleanType)
        .withIdsAndValue(false, ALL_RES_IDS)
        .build();
    final ElementPath literalContext = new ElementPathBuilder()
        .dataset(literalContextDataset)
        .idAndValueColumns()
        .build();

    return CodingLiteralPath.fromString(CODING_MEDIUM.getSystem() + "|" + CODING_MEDIUM.getCode(),
        literalContext);
  }

  private static CodingPath createCodingArg() {
    final Dataset<Row> dataset = new DatasetBuilder()
        .withIdColumn()
        .withStructTypeColumns(codingStructType())
        .withIdValueRows(ALL_RES_IDS, id -> rowFromCoding(CODING_MEDIUM))
        .withIdValueRows(ALL_RES_IDS, id -> rowFromCoding(CODING_OTHER3))
        .buildWithStructValue();
    final ElementPath argument = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODING)
        .dataset(dataset)
        .idAndValueColumns()
        .build();

    return (CodingPath) argument;
  }

  private static ElementPath createCodeableConceptArg() {
    final Dataset<Row> dataset = new DatasetBuilder()
        .withIdColumn()
        .withStructTypeColumns(codeableConceptStructType())
        .withIdValueRows(ALL_RES_IDS,
            id -> codeableConceptRowFromCoding(CODING_MEDIUM, CODING_OTHER5))
        .withIdValueRows(ALL_RES_IDS,
            id -> codeableConceptRowFromCoding(CODING_OTHER3, CODING_OTHER5))
        .buildWithStructValue();

    return new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODEABLECONCEPT)
        .dataset(dataset)
        .idAndValueColumns()
        .build();
  }

  private static CodingPath createNullCodingArg() {
    final Dataset<Row> dataset = new DatasetBuilder()
        .withIdColumn()
        .withStructTypeColumns(codingStructType())
        .withIdValueRows(ALL_RES_IDS, id -> null)
        .buildWithStructValue();

    final ElementPath argument = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODING)
        .dataset(dataset)
        .idAndValueColumns()
        .build();

    return (CodingPath) argument;
  }

  private static DatasetBuilder expectedSubsumes() {
    return new DatasetBuilder()
        .withIdColumn()
        .withEidColumn()
        .withColumn(DataTypes.BooleanType)
        .withRow(RES_ID1, makeEid(0), false)
        .withRow(RES_ID1, makeEid(1), false)
        .withRow(RES_ID2, makeEid(0), false)
        .withRow(RES_ID2, makeEid(1), true)
        .withRow(RES_ID3, makeEid(0), false)
        .withRow(RES_ID3, makeEid(1), true)
        .withRow(RES_ID4, makeEid(0), false)
        .withRow(RES_ID4, makeEid(1), false)
        .withRow(RES_ID5, null, null);
  }

  private static DatasetBuilder expectedSubsumedBy() {
    return new DatasetBuilder()
        .withIdColumn()
        .withEidColumn()
        .withColumn(DataTypes.BooleanType)
        .withRow(RES_ID1, makeEid(0), false)
        .withRow(RES_ID1, makeEid(1), true)
        .withRow(RES_ID2, makeEid(0), false)
        .withRow(RES_ID2, makeEid(1), true)
        .withRow(RES_ID3, makeEid(0), false)
        .withRow(RES_ID3, makeEid(1), false)
        .withRow(RES_ID4, makeEid(0), false)
        .withRow(RES_ID4, makeEid(1), false)
        .withRow(RES_ID5, null, null);
  }

  @Nonnull
  private static DatasetBuilder expectedAllNonNull(final boolean result) {
    return new DatasetBuilder()
        .withIdColumn()
        .withEidColumn()
        .withColumn(DataTypes.BooleanType)
        .withRow(RES_ID1, makeEid(0), result)
        .withRow(RES_ID1, makeEid(1), result)
        .withRow(RES_ID2, makeEid(0), result)
        .withRow(RES_ID2, makeEid(1), result)
        .withRow(RES_ID3, makeEid(0), result)
        .withRow(RES_ID3, makeEid(1), result)
        .withRow(RES_ID4, makeEid(0), result)
        .withRow(RES_ID4, makeEid(1), result)
        .withRow(RES_ID5, null, null);
  }

  private FhirPathAssertion assertCallSuccess(final NamedFunction function,
      final NonLiteralPath inputExpression, final FhirPath argumentExpression) {
    final ParserContext parserContext = new ParserContextBuilder()
        .terminologyClient(terminologyClient)
        .terminologyClientFactory(terminologyClientFactory)
        .build();

    final NamedFunctionInput functionInput = new NamedFunctionInput(parserContext, inputExpression,
        Collections.singletonList(argumentExpression));
    final FhirPath result = function.invoke(functionInput);

    return assertThat(result)
        .isElementPath(BooleanPath.class)
        .preservesCardinalityOf(inputExpression);
  }

  private DatasetAssert assertSubsumesSuccess(final NonLiteralPath inputExpression,
      final FhirPath argumentExpression) {
    return assertCallSuccess(NamedFunction.getInstance("subsumes"), inputExpression,
        argumentExpression).selectOrderedResultWithEid();
  }

  private DatasetAssert assertSubsumedBySuccess(final NonLiteralPath inputExpression,
      final FhirPath argumentExpression) {
    return assertCallSuccess(NamedFunction.getInstance("subsumedBy"), inputExpression,
        argumentExpression).selectOrderedResultWithEid();
  }

  //
  // Test subsumes on selected pairs of argument types
  // (Coding, CodingLiteral) && (CodeableConcept, Coding) && (Literal, CodeableConcept)
  // plus (Coding, Coding)
  //
  @Test
  public void testSubsumesCodingWithLiteralCorrectly() {
    assertSubsumesSuccess(createCodingInput(), createLiteralArgOrInput())
        .hasRows(expectedSubsumes());
  }

  @Test
  public void testSubsumesCodeableConceptWithCodingCorrectly() {
    assertSubsumesSuccess(createCodeableConceptInput(), createCodingArg())
        .hasRows(expectedSubsumes());
  }

  @Test
  public void testSubsumesCodingWithCodingCorrectly() {
    assertSubsumesSuccess(createCodingInput(), createCodingArg()).hasRows(expectedSubsumes());
  }

  //
  // Test subsumedBy on selected pairs of argument types
  // (Coding, CodeableConcept) && (CodeableConcept, Literal) && (Literal, Coding)
  // plus (CodeableConcept, CodeableConcept)
  //

  @Test
  public void testSubsumedByCodingWithCodeableConceptCorrectly() {

    assertSubsumedBySuccess(createCodingInput(), createCodeableConceptArg())
        .hasRows(expectedSubsumedBy());
  }

  @Test
  public void testSubsumedByCodeableConceptWithLiteralCorrectly() {
    assertSubsumedBySuccess(createCodeableConceptInput(), createLiteralArgOrInput())
        .hasRows(expectedSubsumedBy());
  }

  @Test
  public void testSubsumedByCodeableConceptWithCodeableConceptCorrectly() {
    // call subsumedBy but expect subsumes result
    // because input is switched with argument
    assertSubsumedBySuccess(createCodeableConceptInput(), createCodeableConceptArg())
        .hasRows(expectedSubsumedBy());
  }

  //
  // Test against nulls
  //

  @Test
  public void testAllFalseWhenSubsumesNullCoding() {
    assertSubsumesSuccess(createCodingInput(), createNullCodingArg())
        .hasRows(expectedAllNonNull(false));
  }

  @Test
  public void testAllFalseWhenSubsumedByNullCoding() {
    assertSubsumedBySuccess(createCodeableConceptInput(), createNullCodingArg())
        .hasRows(expectedAllNonNull(false));
  }


  @Test
  public void testAllNonNullTrueWhenSubsumesItself() {

    final DatasetBuilder expectedResult = new DatasetBuilder()
        .withIdColumn()
        .withEidColumn()
        .withColumn(DataTypes.BooleanType)
        .withRow(RES_ID1, null, true)
        .withRow(RES_ID2, null, true)
        .withRow(RES_ID3, null, true)
        .withRow(RES_ID4, null, true)
        .withRow(RES_ID5, null, null);

    assertSubsumesSuccess(createSingularCodingInput(), createSingularCodingInput())
        .hasRows(expectedResult);
  }


  @Test
  public void testAllNonNullTrueSubsumedByItself() {
    assertSubsumedBySuccess(createCodeableConceptInput(), createCodeableConceptInput())
        .hasRows(expectedAllNonNull(true));
  }

  //
  // Test for various validation errors
  //

  @Test
  public void throwsErrorIfInputTypeIsUnsupported() {
    final ParserContext parserContext = new ParserContextBuilder()
        .terminologyClient(mock(TerminologyClient.class))
        .terminologyClientFactory(mock(TerminologyClientFactory.class))
        .build();

    final ElementPath argument = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODEABLECONCEPT)
        .build();

    final ElementPath input = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.STRING)
        .build();

    final NamedFunctionInput functionInput = new NamedFunctionInput(parserContext, input,
        Collections.singletonList(argument));
    final NamedFunction subsumesFunction = NamedFunction.getInstance("subsumedBy");
    final InvalidUserInputError error = assertThrows(
        InvalidUserInputError.class,
        () -> subsumesFunction.invoke(functionInput));
    assertEquals(
        "subsumedBy function accepts input of type Coding or CodeableConcept",
        error.getMessage());
  }

  @Test
  public void throwsErrorIfArgumentTypeIsUnsupported() {
    final ParserContext parserContext = new ParserContextBuilder()
        .terminologyClient(mock(TerminologyClient.class))
        .terminologyClientFactory(mock(TerminologyClientFactory.class))
        .build();

    final ElementPath input = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODEABLECONCEPT)
        .build();
    final StringLiteralPath argument = StringLiteralPath
        .fromString("'str'", input);

    final NamedFunctionInput functionInput = new NamedFunctionInput(parserContext, input,
        Collections.singletonList(argument));
    final NamedFunction subsumesFunction = NamedFunction.getInstance("subsumes");
    final InvalidUserInputError error = assertThrows(
        InvalidUserInputError.class,
        () -> subsumesFunction.invoke(functionInput));
    assertEquals("subsumes function accepts argument of type Coding or CodeableConcept",
        error.getMessage());
  }


  @Test
  public void throwsErrorIfMoreThanOneArgument() {

    final ParserContext parserContext = new ParserContextBuilder()
        .terminologyClient(mock(TerminologyClient.class))
        .terminologyClientFactory(mock(TerminologyClientFactory.class))
        .build();

    final ElementPath input = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODEABLECONCEPT)
        .build();

    final CodingLiteralPath argument1 = CodingLiteralPath
        .fromString(CODING_MEDIUM.getSystem() + "|" + CODING_MEDIUM.getCode(),
            input);

    final CodingLiteralPath argument2 = CodingLiteralPath
        .fromString(CODING_MEDIUM.getSystem() + "|" + CODING_MEDIUM.getCode(),
            input);

    final NamedFunctionInput functionInput = new NamedFunctionInput(parserContext, input,
        Arrays.asList(argument1, argument2));
    final NamedFunction subsumesFunction = NamedFunction.getInstance("subsumes");
    final InvalidUserInputError error = assertThrows(
        InvalidUserInputError.class,
        () -> subsumesFunction.invoke(functionInput));
    assertEquals("subsumes function accepts one argument of type Coding or CodeableConcept",
        error.getMessage());
  }

  @Test
  public void throwsErrorIfTerminologyServiceNotConfigured() {
    final ElementPath input = new ElementPathBuilder()
        .fhirType(FHIRDefinedType.CODEABLECONCEPT)
        .build();

    final CodingLiteralPath argument = CodingLiteralPath
        .fromString(CODING_MEDIUM.getSystem() + "|" + CODING_MEDIUM.getCode(),
            input);

    final ParserContext context = new ParserContextBuilder()
        .build();

    final NamedFunctionInput functionInput = new NamedFunctionInput(context, input,
        Collections.singletonList(argument));

    final InvalidUserInputError error = assertThrows(InvalidUserInputError.class,
        () -> new SubsumesFunction().invoke(functionInput));
    assertEquals(
        "Attempt to call terminology function subsumes when terminology service has not been configured",
        error.getMessage());
  }
}
