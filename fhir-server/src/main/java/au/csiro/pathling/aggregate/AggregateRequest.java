/*
 * Copyright © 2018-2020, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.aggregate;

import static au.csiro.pathling.utilities.Preconditions.checkUserInput;

import au.csiro.pathling.errors.InvalidUserInputError;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Value;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.StringType;

/**
 * Represents the information provided as part of an invocation of the "aggregate" operation.
 *
 * @author John Grimes
 */
@Value
public class AggregateRequest {

  @Nonnull
  ResourceType subjectResource;

  @Nonnull
  List<Aggregation> aggregations;

  @Nonnull
  List<Grouping> groupings;

  @Nonnull
  List<String> filters;

  /**
   * This static build method takes a {@link Parameters} resource (with the parameters defined
   * within the "aggregate" OperationDefinition) and populates the values into a new {@link
   * AggregateRequest} object.
   *
   * @param parameters a {@link Parameters} object
   * @return an AggregateRequest
   */
  @Nonnull
  public static AggregateRequest from(@Nonnull final Parameters parameters) {
    final ResourceType subjectResource = getSubjectResource(parameters);
    final List<Aggregation> aggregations = getAggregations(parameters);
    final List<Grouping> groupings = getGroupings(parameters);
    final List<String> filters = getFilters(parameters);

    return new AggregateRequest(subjectResource, aggregations, groupings, filters);
  }

  @Nonnull
  private static ResourceType getSubjectResource(@Nonnull final Parameters parameters) {
    final ParametersParameterComponent subjectResourceParam = parameters.getParameter().stream()
        .filter(param -> param.getName().equals("subjectResource"))
        .findFirst()
        .orElseThrow(
            () -> new InvalidUserInputError("There must be one subject resource parameter"));
    checkUserInput(subjectResourceParam.getValue() instanceof CodeType,
        "Subject resource parameter must have code value");
    final CodeType subjectResourceCode = (CodeType) subjectResourceParam.getValue();
    final ResourceType subjectResource;
    try {
      subjectResource = ResourceType.fromCode(subjectResourceCode.asStringValue());
    } catch (final FHIRException e) {
      throw new InvalidUserInputError(
          "Subject resource must be a member of https://hl7.org/fhir/ValueSet/resource-types.");
    }
    return subjectResource;
  }

  @Nonnull
  private static List<Aggregation> getAggregations(@Nonnull final Parameters parameters) {
    return parameters.getParameter().stream()
        .filter(param -> param.getName().equals("aggregation"))
        .map(aggregation -> {
          final Optional<String> label = aggregation.getPart()
              .stream()
              .filter(
                  part -> part.getName().equals("label") && part.getValue() instanceof StringType)
              .findFirst()
              .map(parameter -> parameter.getValue().toString());
          final String expression = aggregation.getPart()
              .stream()
              .filter(part -> part.getName().equals("expression") &&
                  part.getValue() instanceof StringType)
              .findFirst()
              .map(parameter -> parameter.getValue().toString())
              .orElseThrow(() -> new InvalidUserInputError("Aggregation must have expression"));
          return new Aggregation(label, expression);
        })
        .collect(Collectors.toList());
  }

  @Nonnull
  private static List<Grouping> getGroupings(@Nonnull final Parameters parameters) {
    return parameters.getParameter().stream()
        .filter(param -> param.getName().equals("grouping"))
        .map(grouping -> {
          final Optional<String> label = grouping.getPart()
              .stream()
              .filter(
                  part -> part.getName().equals("label") && part.getValue() instanceof StringType)
              .findFirst()
              .map(parameter -> parameter.getValue().toString());
          final String expression = grouping.getPart()
              .stream()
              .filter(part -> part.getName().equals("expression") &&
                  part.getValue() instanceof StringType)
              .findFirst()
              .map(parameter -> parameter.getValue().toString())
              .orElseThrow(() -> new InvalidUserInputError("Grouping must have expression"));
          return new Grouping(label, expression);
        })
        .collect(Collectors.toList());
  }

  @Nonnull
  private static List<String> getFilters(@Nonnull final Parameters parameters) {
    return parameters.getParameter().stream()
        .filter(param -> param.getName().equals("filter") && param.getValue() instanceof StringType)
        .map(filter -> filter.getValue().toString())
        .collect(Collectors.toList());
  }

  /**
   * Represents an aggregation parameter within an {@link AggregateRequest}.
   */
  @Value
  public static class Aggregation {

    @Nonnull
    Optional<String> label;

    @Nonnull
    String expression;

  }

  /**
   * Represents a grouping parameter within an {@link AggregateRequest}.
   */
  @Value
  public static class Grouping {

    @Nonnull
    Optional<String> label;

    @Nonnull
    String expression;

  }

}
