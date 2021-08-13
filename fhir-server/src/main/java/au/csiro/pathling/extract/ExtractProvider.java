/*
 * Copyright © 2018-2021, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.extract;

import static au.csiro.pathling.fhir.FhirServer.resourceTypeFromClass;

import au.csiro.pathling.security.OperationAccess;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * HAPI resource provider that provides an entry point for the "extract" type-level operation.
 *
 * @author John Grimes
 * @see <a href="https://pathling.csiro.au/docs/extract.html">Extract</a>
 */
@Component
@Scope("prototype")
@Profile("server")
public class ExtractProvider implements IResourceProvider {

  @Nonnull
  private final ExtractExecutor extractExecutor;

  @Nonnull
  private final Class<? extends IBaseResource> resourceClass;

  @Nonnull
  private final ResourceType resourceType;

  /**
   * @param extractExecutor an instance of {@link ExtractExecutor} to process requests
   * @param resourceClass the resource class that this provider will receive requests for
   */
  public ExtractProvider(@Nonnull final ExtractExecutor extractExecutor,
      @Nonnull final Class<? extends IBaseResource> resourceClass) {
    this.extractExecutor = extractExecutor;
    this.resourceClass = resourceClass;
    resourceType = resourceTypeFromClass(resourceClass);
  }

  @Override
  public Class<? extends IBaseResource> getResourceType() {
    return resourceClass;
  }

  /**
   * Extended FHIR operation: "extract".
   *
   * @param column a list of column expressions
   * @param filter a list of filter expressions
   * @return {@link Parameters} object representing the result
   */
  @Operation(name = "$extract", idempotent = true)
  @OperationAccess("extract")
  public Parameters extract(
      @Nullable @OperationParam(name = "column") final List<String> column,
      @Nullable @OperationParam(name = "filter") final List<String> filter) {
    final ExtractRequest query = new ExtractRequest(
        resourceType, Optional.ofNullable(column), Optional.ofNullable(filter));
    final ExtractResponse result = extractExecutor.execute(query);
    return result.toParameters();
  }

}
