/*
 * Copyright © 2018-2022, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.security.ga4gh;

import static au.csiro.pathling.test.assertions.Assertions.assertJson;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import au.csiro.pathling.Configuration;
import au.csiro.pathling.Configuration.Authorization;
import au.csiro.pathling.Configuration.Authorization.Ga4ghPassports;
import au.csiro.pathling.Configuration.Spark;
import au.csiro.pathling.Configuration.Storage;
import au.csiro.pathling.fhirpath.element.BooleanPath;
import au.csiro.pathling.fhirpath.parser.AbstractParserTest;
import au.csiro.pathling.io.ResourceReader;
import ca.uhn.fhir.context.FhirContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.junit.jupiter.api.Test;

@Slf4j
class ManifestConverterTest extends AbstractParserTest {

  private static final String PATIENT_ID_1 = "0dc85075-4f59-4e4f-b75d-a2f601d0cf24";
  private static final String PATIENT_ID_2 = "1f276fc3-7e91-4fc9-a287-be19228e8807";
  private static final String PATIENT_ID_3 = "f34e77c9-df31-49c4-92e2-e871fa76026e";
  private static final String PATIENT_ID_4 = "2cc8ffdd-6233-4dd4-ba71-36eccb8204e2";

  @Test
  void convertsManifest() {
    final PassportScope passportScope = new PassportScope();
    final VisaManifest manifest = new VisaManifest();
    manifest.setPatientIds(Arrays.asList(PATIENT_ID_1, PATIENT_ID_2, PATIENT_ID_3, PATIENT_ID_4));

    // Find the test data warehouse URL.
    final File warehouseDirectory = new File("src/test/resources/test-data");
    final String warehouseUrl = warehouseDirectory.toURI().toString();

    // Mock the configuration.
    final Configuration configuration = mock(Configuration.class);
    final Spark sparkConfig = mock(Spark.class);
    final Authorization auth = mock(Authorization.class);
    final Storage storage = mock(Storage.class);
    final Ga4ghPassports ga4gh = mock(Ga4ghPassports.class);
    when(configuration.getSpark()).thenReturn(sparkConfig);
    when(sparkConfig.getCacheDatasets()).thenReturn(false);
    when(configuration.getAuth()).thenReturn(auth);
    when(auth.getGa4ghPassports()).thenReturn(ga4gh);
    when(configuration.getStorage()).thenReturn(storage);
    when(storage.getWarehouseUrl()).thenReturn(warehouseUrl);
    when(storage.getDatabaseName()).thenReturn("parquet");
    when(ga4gh.getPatientIdSystem()).thenReturn("https://github.com/synthetichealth/synthea");

    // Set up a resource reader pointing to the test data.
    mockReader = new ResourceReader(configuration, spark);
    final List<ResourceType> availableResourceTypes = new ArrayList<>(
        mockReader.getAvailableResourceTypes());
    // Questionnaire and QuestionnaireResponse do not reference a subject patient.
    availableResourceTypes.removeAll(
        Arrays.asList(ResourceType.QUESTIONNAIRE, ResourceType.QUESTIONNAIRERESPONSE));

    final FhirContext fhirContext = FhirContext.forR4();
    final ManifestConverter manifestConverter = new ManifestConverter(configuration, fhirContext);

    manifestConverter.populateScope(passportScope, manifest);

    // Convert the scope to JSON and compare it to a test fixture.
    final Gson gson = new GsonBuilder().create();
    final String json = gson.toJson(passportScope);
    assertJson("responses/ManifestConverterTest/convertsManifest.json", json);

    // Go through each filter in the scope and try it out on the test data, if we have test data for 
    // that resource type. There should be at least one resource of each type linked back to one of 
    // our test patients.
    for (final ResourceType resourceType : passportScope.keySet()) {
      if (availableResourceTypes.contains(resourceType)) {
        boolean found = false;
        for (final String filter : passportScope.get(resourceType)) {
          final Dataset<Row> dataset = assertThatResultOf(resourceType, filter)
              .isElementPath(BooleanPath.class)
              .selectResult()
              .apply(result -> result.filter(result.columns()[1]))
              .getDataset();
          if (dataset.count() > 0) {
            found = true;
          }
        }
        assertTrue(found, "No results found for " + resourceType);
      }
    }
  }

}