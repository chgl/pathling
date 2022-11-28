/*
 * Copyright 2022 Commonwealth Scientific and Industrial Research
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

package au.csiro.pathling.library;

import static au.csiro.pathling.test.helpers.TerminologyServiceHelpers.setupSubsumes;
import static au.csiro.pathling.test.helpers.TerminologyServiceHelpers.setupTranslate;
import static au.csiro.pathling.test.helpers.TerminologyServiceHelpers.setupValidate;
import static org.apache.spark.sql.functions.col;
import static org.hl7.fhir.r4.model.codesystems.ConceptMapEquivalence.EQUIVALENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import au.csiro.pathling.config.HttpCacheConf;
import au.csiro.pathling.config.HttpClientConf;
import au.csiro.pathling.config.TerminologyAuthConfiguration;
import au.csiro.pathling.encoders.FhirEncoders;
import au.csiro.pathling.terminology.DefaultTerminologyServiceFactory;
import au.csiro.pathling.terminology.TerminologyService2;
import au.csiro.pathling.terminology.TerminologyService2.Translation;
import au.csiro.pathling.terminology.TerminologyServiceFactory;
import au.csiro.pathling.fhirpath.encoding.CodingEncoding;
import au.csiro.pathling.test.SchemaAsserts;
import java.util.List;
import java.util.Map;
import ca.uhn.fhir.context.FhirVersionEnum;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.OutputMode;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Enumerations.ConceptMapEquivalence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scala.collection.mutable.WrappedArray;

@Slf4j
public class PathlingContextTest {

  private static SparkSession spark;
  private static final String testDataUrl = "target/encoders-tests/data";

  /**
   * Set up Spark.
   */
  @BeforeAll
  public static void setUpAll() {
    spark = TestHelpers.spark();
  }

  /**
   * Tear down Spark.
   */
  @AfterAll
  public static void tearDownAll() {
    spark.stop();
  }

  private TerminologyServiceFactory terminologyServiceFactory;
  private TerminologyService2 terminologyService;

  @BeforeEach
  public void setUp() {
    // setup terminology mocks
    terminologyServiceFactory = mock(
        TerminologyServiceFactory.class, withSettings().serializable());
    terminologyService = mock(TerminologyService2.class,
        withSettings().serializable());
    when(terminologyServiceFactory.buildService2()).thenReturn(terminologyService);

    DefaultTerminologyServiceFactory.reset();
  }


  @Test
  public void testEncodeResourcesFromJsonBundle() {

    final Dataset<String> bundlesDF = spark.read().option("wholetext", true)
        .textFile(testDataUrl + "/bundles/R4/json");

    final PathlingContext pathling = PathlingContext.create(spark);

    final Dataset<Row> patientsDataframe = pathling.encodeBundle(bundlesDF.toDF(),
        "Patient", FhirMimeTypes.FHIR_JSON);
    assertEquals(5, patientsDataframe.count());

    // Test omission of MIME type.
    final Dataset<Row> patientsDataframe2 = pathling.encodeBundle(bundlesDF.toDF(),
        "Patient");
    assertEquals(5, patientsDataframe2.count());

    final Dataset<Condition> conditionsDataframe = pathling.encodeBundle(bundlesDF, Condition.class,
        FhirMimeTypes.FHIR_JSON);
    assertEquals(107, conditionsDataframe.count());
  }


  @Test
  public void testEncodeResourcesFromXmlBundle() {

    final Dataset<String> bundlesDF = spark.read().option("wholetext", true)
        .textFile(testDataUrl + "/bundles/R4/xml");

    final PathlingContext pathling = PathlingContext.create(spark);
    final Dataset<Condition> conditionsDataframe = pathling.encodeBundle(bundlesDF, Condition.class,
        FhirMimeTypes.FHIR_XML);
    assertEquals(107, conditionsDataframe.count());
  }


  @Test
  public void testEncodeResourcesFromJson() {
    final Dataset<String> jsonResources = spark.read()
        .textFile(testDataUrl + "/resources/R4/json");

    final PathlingContext pathling = PathlingContext.create(spark);

    final Dataset<Row> patientsDataframe = pathling.encode(jsonResources.toDF(), "Patient",
        FhirMimeTypes.FHIR_JSON);
    assertEquals(9, patientsDataframe.count());

    final Dataset<Condition> conditionsDataframe = pathling.encode(jsonResources, Condition.class,
        FhirMimeTypes.FHIR_JSON);
    assertEquals(71, conditionsDataframe.count());
  }

  @Test
  public void testEncoderOptions() {
    final Dataset<Row> jsonResourcesDF = spark.read()
        .text(testDataUrl + "/resources/R4/json");

    // Test the defaults
    final Row defaultRow = PathlingContext.create(spark)
        .encode(jsonResourcesDF, "Questionnaire")
        .head();
    SchemaAsserts.assertFieldNotPresent("_extension", defaultRow.schema());
    final Row defaultItem = (Row) defaultRow.getList(defaultRow.fieldIndex("item")).get(0);
    SchemaAsserts.assertFieldNotPresent("item", defaultItem.schema());

    // Test explicit options
    // Nested items
    final PathlingContextConfiguration config = PathlingContextConfiguration.builder()
        .maxNestingLevel(1)
        .build();
    final Row rowWithNesting = PathlingContext.create(spark, config)
        .encode(jsonResourcesDF, "Questionnaire").head();
    SchemaAsserts.assertFieldNotPresent("_extension", rowWithNesting.schema());
    // Test item nesting
    final Row itemWithNesting = (Row) rowWithNesting
        .getList(rowWithNesting.fieldIndex("item")).get(0);
    SchemaAsserts.assertFieldPresent("item", itemWithNesting.schema());
    final Row nestedItem = (Row) itemWithNesting
        .getList(itemWithNesting.fieldIndex("item")).get(0);
    SchemaAsserts.assertFieldNotPresent("item", nestedItem.schema());

    // Test explicit options
    // Extensions and open types
    final PathlingContextConfiguration config2 = PathlingContextConfiguration.builder()
        .extensionsEnabled(true)
        .openTypesEnabled(List.of("boolean", "string", "Address"))
        .build();
    final Row rowWithExtensions = PathlingContext.create(spark, config2)
        .encode(jsonResourcesDF, "Patient").head();
    SchemaAsserts.assertFieldPresent("_extension", rowWithExtensions.schema());

    final Map<Integer, WrappedArray<Row>> extensions = rowWithExtensions
        .getJavaMap(rowWithExtensions.fieldIndex("_extension"));

    // get the first extension of some extension set
    final Row extension = (Row) extensions.values().toArray(WrappedArray[]::new)[0].apply(0);
    SchemaAsserts.assertFieldPresent("valueString", extension.schema());
    SchemaAsserts.assertFieldPresent("valueAddress", extension.schema());
    SchemaAsserts.assertFieldPresent("valueBoolean", extension.schema());
    SchemaAsserts.assertFieldNotPresent("valueInteger", extension.schema());
  }

  @Test
  public void testEncodeResourceStream() throws Exception {
    final PathlingContextConfiguration config = PathlingContextConfiguration.builder()
        .extensionsEnabled(true)
        .build();
    final PathlingContext pathling = PathlingContext.create(spark, config);

    final Dataset<Row> jsonResources = spark.readStream().text(testDataUrl + "/resources/R4/json");

    assertTrue(jsonResources.isStreaming());

    final Dataset<Row> patientsStream = pathling.encode(jsonResources, "Patient",
        FhirMimeTypes.FHIR_JSON);

    assertTrue(patientsStream.isStreaming());

    final StreamingQuery patientsQuery = patientsStream
        .writeStream()
        .queryName("patients")
        .format("memory")
        .start();

    patientsQuery.processAllAvailable();
    final long patientsCount = spark.sql("select count(*) from patients").head().getLong(0);
    assertEquals(patientsCount, patientsCount);

    final StreamingQuery conditionQuery = pathling.encode(jsonResources, "Condition",
            FhirMimeTypes.FHIR_JSON)
        .groupBy()
        .count()
        .writeStream()
        .outputMode(OutputMode.Complete())
        .queryName("countCondition")
        .format("memory")
        .start();

    conditionQuery.processAllAvailable();
    final long conditionsCount = spark.sql("select * from countCondition").head().getLong(0);
    assertEquals(71, conditionsCount);
  }

  @Test
  void testMemberOf() {
    final String valueSetUri = "urn:test:456";
    final Coding coding1 = new Coding("urn:test:123", "ABC", "abc");
    final Coding coding2 = new Coding("urn:test:123", "DEF", "def");

    setupValidate(terminologyService).withValueSet(valueSetUri, coding1);

    final PathlingContext pathlingContext = PathlingContext.create(spark,
        FhirEncoders.forR4().getOrCreate(), terminologyServiceFactory);

    final Row row1 = RowFactory.create("foo", CodingEncoding.encode(coding1));
    final Row row2 = RowFactory.create("bar", CodingEncoding.encode(coding2));
    final List<Row> datasetRows = List.of(row1, row2);
    final StructType schema = DataTypes.createStructType(
        new StructField[]{DataTypes.createStructField("id", DataTypes.StringType, true),
            DataTypes.createStructField("coding", CodingEncoding.codingStructType(), true)});
    final Dataset<Row> codingDataFrame = spark.createDataFrame(datasetRows, schema);
    final Column codingColumn = col("coding");
    final Dataset<Row> result = pathlingContext.memberOf(codingDataFrame, codingColumn,
        valueSetUri, "result");

    final List<Row> rows = result.select("id", "result").collectAsList();
    assertEquals(RowFactory.create("foo", true), rows.get(0));
    assertEquals(RowFactory.create("bar", false), rows.get(1));
  }

  @Test
  void testTranslate() {
    final String conceptMapUri = "urn:test:456";
    final Coding coding1 = new Coding("urn:test:123", "ABC", "abc");
    final Coding coding2 = new Coding("urn:test:123", "DEF", "def");

    setupTranslate(terminologyService).withTranslations(coding1, conceptMapUri,
        Translation.of(EQUIVALENT, coding2));

    final PathlingContext pathlingContext = PathlingContext.create(spark,
        FhirEncoders.forR4().getOrCreate(), terminologyServiceFactory);

    final Row row1 = RowFactory.create("foo", CodingEncoding.encode(coding1));
    final Row row2 = RowFactory.create("bar", CodingEncoding.encode(coding2));
    final List<Row> datasetRows = List.of(row1, row2);
    final StructType schema = DataTypes.createStructType(
        new StructField[]{DataTypes.createStructField("id", DataTypes.StringType, true),
            DataTypes.createStructField("coding", CodingEncoding.codingStructType(), true)});
    final Dataset<Row> codingDataFrame = spark.createDataFrame(datasetRows, schema);
    final Column codingColumn = col("coding");
    final Dataset<Row> result = pathlingContext.translate(codingDataFrame, codingColumn,
        conceptMapUri, false, ConceptMapEquivalence.EQUIVALENT.toCode(), null, "result");

    final List<Row> rows = result.select("id", "result").collectAsList();
    assertEquals(RowFactory.create("foo", WrappedArray.make(new Row[]{CodingEncoding.encode(coding2)})),
        rows.get(0));
  }

  @Test
  void testSubsumes() {
    final Coding coding1 = new Coding("urn:test:123", "ABC", "abc");
    final Coding coding2 = new Coding("urn:test:123", "DEF", "def");
    final Coding coding3 = new Coding("urn:test:123", "GHI", "ghi");

    setupSubsumes(terminologyService).withSubsumes(coding1, coding2);

    final PathlingContext pathlingContext = PathlingContext.create(spark,
        FhirEncoders.forR4().getOrCreate(), terminologyServiceFactory);

    final Row row1 = RowFactory.create("foo", CodingEncoding.encode(coding1),
        CodingEncoding.encode(coding2));
    final Row row2 = RowFactory.create("bar", CodingEncoding.encode(coding1),
        CodingEncoding.encode(coding3));
    final List<Row> datasetRows = List.of(row1, row2);
    final StructType schema = DataTypes.createStructType(
        new StructField[]{DataTypes.createStructField("id", DataTypes.StringType, true),
            DataTypes.createStructField("leftCoding", CodingEncoding.codingStructType(), true),
            DataTypes.createStructField("rightCoding", CodingEncoding.codingStructType(), true)});
    final Dataset<Row> codingDataFrame = spark.createDataFrame(datasetRows, schema);
    final Column leftCoding = col("leftCoding");
    final Column rightCoding = col("rightCoding");
    final Dataset<Row> result = pathlingContext.subsumes(codingDataFrame, leftCoding, rightCoding,
        "result");

    final List<Row> rows = result.select("id", "result").collectAsList();
    assertEquals(RowFactory.create("foo", true), rows.get(0));
    assertEquals(RowFactory.create("bar", false), rows.get(1));
  }

  @Test
  void testBuildContextWithTerminologyDefaults() {

    final String terminologyServerUrl = "https://tx.ontoserver.csiro.au/fhir";

    final PathlingContextConfiguration config = PathlingContextConfiguration.builder()
        .terminologyServerUrl(terminologyServerUrl)
        .build();
    final PathlingContext pathlingContext = PathlingContext.create(spark, config);
    assertNotNull(pathlingContext);
    final DefaultTerminologyServiceFactory expectedFactory = new DefaultTerminologyServiceFactory(
        FhirVersionEnum.R4, terminologyServerUrl, false, HttpClientConf.defaults(),
        HttpCacheConf.defaults(), TerminologyAuthConfiguration.defaults());

    final TerminologyServiceFactory actualServiceFactory = pathlingContext.getTerminologyServiceFactory();
    assertEquals(expectedFactory, actualServiceFactory);
    final TerminologyService2 terminologyService = actualServiceFactory.buildService2();
    assertNotNull(terminologyService);
  }

  @Test
  void testBuildContextWithTerminologyNoCache() {

    final String terminologyServerUrl = "https://tx.ontoserver.csiro.au/fhir";

    final PathlingContextConfiguration config = PathlingContextConfiguration.builder()
        .terminologyServerUrl(terminologyServerUrl)
        .cacheStorageType(null)
        .build();
    final PathlingContext pathlingContext = PathlingContext.create(spark, config);
    assertNotNull(pathlingContext);
    final TerminologyServiceFactory expectedFactory = new DefaultTerminologyServiceFactory(
        FhirVersionEnum.R4, terminologyServerUrl, false, HttpClientConf.defaults(),
        HttpCacheConf.disabled(), TerminologyAuthConfiguration.defaults());

    final TerminologyServiceFactory actualServiceFactory = pathlingContext.getTerminologyServiceFactory();
    assertEquals(expectedFactory, actualServiceFactory);
    final TerminologyService2 terminologyService = actualServiceFactory.buildService2();
    assertNotNull(terminologyService);
  }


  @Test
  void testBuildContextWithCustomizedTerminology() {
    final String terminologyServerUrl = "https://r4.ontoserver.csiro.au/fhir";
    final String tokenEndpoint = "https://auth.ontoserver.csiro.au/auth/realms/aehrc/protocol/openid-connect/token";
    final String clientId = "some-client";
    final String clientSecret = "some-secret";
    final String scope = "openid";
    final long tokenExpiryTolerance = 300L;

    final int maxConnectionsTotal = 66;
    final int maxConnectionsPerRoute = 33;
    final int socketTimeout = 123;

    final int cacheMaxEntries = 1233;
    final long cacheMaxObjectSize = 4453L;
    final String cacheStorgeType = "disk";
    final Map<String, String> cacheStorageProperties = ImmutableMap.of("cacheDir", "/tmp");

    final PathlingContextConfiguration config = PathlingContextConfiguration.builder()
        .terminologyServerUrl(terminologyServerUrl)
        .terminologyVerboseRequestLogging(true)
        .terminologySocketTimeout(socketTimeout)
        .maxConnectionsTotal(maxConnectionsTotal)
        .maxConnectionsPerRoute(maxConnectionsPerRoute)
        .cacheMaxEntries(cacheMaxEntries)
        .cacheMaxObjectSize(cacheMaxObjectSize)
        .cacheStorageType(cacheStorgeType)
        .cacheStorageProperties(cacheStorageProperties)
        .tokenEndpoint(tokenEndpoint)
        .clientId(clientId)
        .clientSecret(clientSecret)
        .scope(scope)
        .tokenExpiryTolerance(tokenExpiryTolerance)
        .build();

    final PathlingContext pathlingContext = PathlingContext.create(spark, config);
    assertNotNull(pathlingContext);
    final TerminologyServiceFactory expectedFactory = new DefaultTerminologyServiceFactory(
        FhirVersionEnum.R4, terminologyServerUrl, true,
        HttpClientConf.builder()
            .socketTimeout(socketTimeout)
            .maxConnectionsTotal(maxConnectionsTotal)
            .maxConnectionsPerRoute(maxConnectionsPerRoute)
            .build(),
        HttpCacheConf.builder()
            .maxCacheEntries(cacheMaxEntries)
            .maxObjectSize(cacheMaxObjectSize)
            .storageType(cacheStorgeType)
            .storage(cacheStorageProperties)
            .build(),
        TerminologyAuthConfiguration.builder()
            .enabled(true)
            .tokenEndpoint(tokenEndpoint)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .scope(scope)
            .tokenExpiryTolerance(tokenExpiryTolerance)
            .build()
    );

    final TerminologyServiceFactory actualServiceFactory = pathlingContext.getTerminologyServiceFactory();
    assertEquals(expectedFactory, actualServiceFactory);
    final TerminologyService2 terminologyService = actualServiceFactory.buildService2();
    assertNotNull(terminologyService);
  }

}
