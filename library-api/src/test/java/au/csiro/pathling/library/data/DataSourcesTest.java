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

package au.csiro.pathling.library.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import au.csiro.pathling.encoders.FhirEncoders;
import au.csiro.pathling.library.FhirMimeTypes;
import au.csiro.pathling.library.PathlingContext;
import au.csiro.pathling.library.TestHelpers;
import au.csiro.pathling.terminology.TerminologyServiceFactory;
import au.csiro.pathling.test.assertions.DatasetAssert;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class DataSourcesTest {

  protected static final Path TEST_DATA_PATH = Path.of(
      "src/test/resources/test-data").toAbsolutePath().normalize();

  protected static final Path TEST_JSON_DATA_PATH = TEST_DATA_PATH.resolve("ndjson");
  protected static final Path TEST_DELTA_DATA_PATH = TEST_DATA_PATH.resolve("delta");

  protected static PathlingContext pathlingCtx;
  protected static SparkSession spark;

  /**
   * Set up Spark.
   */
  @BeforeAll
  public static void setupContext() {
    spark = TestHelpers.spark();

    final TerminologyServiceFactory terminologyServiceFactory = mock(
        TerminologyServiceFactory.class, withSettings().serializable());

    pathlingCtx = PathlingContext.create(spark, FhirEncoders.forR4().getOrCreate(),
        terminologyServiceFactory);
  }

  /**
   * Tear down Spark.
   */
  @AfterAll
  public static void tearDownAll() {
    spark.stop();
  }

  public static Stream<Arguments> dataSources() {
    final Dataset<Row> patientJsonDf = spark.read()
        .text(TEST_JSON_DATA_PATH.resolve("Patient.ndjson").toString());
    final Dataset<Row> conditionJsonDf = spark.read()
        .text(TEST_JSON_DATA_PATH.resolve("Condition.ndjson").toString());

    return Stream.of(
        Arguments.of("with datasetBuilder()", pathlingCtx.read()
            .datasetBuilder()
            .withResource(ResourceType.PATIENT,
                pathlingCtx.encode(patientJsonDf, "Patient"))
            .withResource("Condition",
                pathlingCtx.encode(conditionJsonDf, "Condition"))
            .build()
        ),
        Arguments.arguments("with filesystemBuilder()",
            pathlingCtx.read().fileSystemBuilder()
                .withGlob(TEST_JSON_DATA_PATH.resolve("*.ndjson").toString())
                .withReader(spark.read().format("text"))
                .withFilePathMapper(SupportFunctions::baseNameWithQualifierToResource)
                .withDatasetTransformer(
                    SupportFunctions.textEncodingTransformer(pathlingCtx, FhirMimeTypes.FHIR_JSON))
                .build()
        ),
        Arguments.arguments("with databaseBuilder()",
            new DeltaSourceBuilder(pathlingCtx)
                .withPath(TEST_DELTA_DATA_PATH.toString())
                .build()
        ),
        Arguments.arguments("files with reader='delta'",
            pathlingCtx.read().files(
                TEST_DELTA_DATA_PATH.resolve("*.parquet").toString(),
                SupportFunctions::baseNameWithQualifierToResource, spark.read().format("delta"))
        ),
        Arguments.arguments("files with format='delta'",
            pathlingCtx.read().files(
                TEST_DELTA_DATA_PATH.resolve("*.parquet").toString(),
                SupportFunctions::baseNameWithQualifierToResource, "delta")
        ),
        Arguments.arguments("text with mimeType='ndjson'",
            pathlingCtx.read()
                .text(TEST_JSON_DATA_PATH.resolve("*.ndjson").toUri().toString(),
                    SupportFunctions::baseNameWithQualifierToResource,
                    FhirMimeTypes.FHIR_JSON)
        ),
        Arguments.arguments("delta",
            pathlingCtx.read().delta(TEST_DELTA_DATA_PATH.toString())
        ),
        Arguments.of("ndjson", pathlingCtx.read()
            .ndjson(TEST_JSON_DATA_PATH.toUri().toString())),
        Arguments.arguments("parquet",
            pathlingCtx.read().parquet(TEST_DELTA_DATA_PATH.toUri().toString())
        )
    );
  }

  @ParameterizedTest(name = "DataSource: {0}")
  @MethodSource("dataSources")
  public void testBuildsCorrectDataSource(final @Nonnull ReadableSource readableSource) {
    final Dataset<Row> patientCount = readableSource.aggregate(ResourceType.PATIENT)
        .withAggregation("count()").execute();
    DatasetAssert.of(patientCount).hasRows(RowFactory.create(9));

    final Dataset<Row> conditionCount = readableSource.aggregate("Condition")
        .withAggregation("count()").execute();
    DatasetAssert.of(conditionCount).hasRows(RowFactory.create(71));
  }

  @Test
  void testS3Uri() {
    final Exception exception = assertThrows(RuntimeException.class,
        () -> pathlingCtx.read()
            .ndjson("s3://pathling-test-data/ndjson/"));
    assertTrue(exception.getCause() instanceof ClassNotFoundException);
    assertEquals("Class org.apache.hadoop.fs.s3a.S3AFileSystem not found",
        exception.getCause().getMessage());
  }

}
