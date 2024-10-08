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

package au.csiro.pathling.library.query;

import static au.csiro.pathling.utilities.Preconditions.requireNonBlank;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;

/**
 * Base class for queries that use filters. Subclasses must implement the {@link #execute} method to
 * perform the actual execution of the query.
 *
 * @param <T> actual type of the query
 */
@SuppressWarnings({"unchecked"})
public abstract class QueryBuilder<T extends QueryBuilder<?>> {

  @Nonnull
  protected final ResourceType subjectResource;

  @Nonnull
  protected QueryDispatcher dispatcher;

  @Nonnull
  protected final List<String> filters = new ArrayList<>();

  protected QueryBuilder(@Nonnull final QueryDispatcher dispatcher,
      @Nonnull final ResourceType subjectResource) {
    this.dispatcher = dispatcher;
    this.subjectResource = subjectResource;
  }

  /**
   * Adds a filter expression to the query. The extract query result will only include rows for
   * resources that match all the filters.
   *
   * @param filter the filter expression to add
   * @return this query
   */
  @Nonnull
  public T filter(@Nullable final String filter) {
    filters.add(requireNonBlank(filter, "Filter expression cannot be blank"));
    return (T) this;
  }

  /**
   * Executes the query.
   *
   * @return the dataset with the result of the query
   */
  @Nonnull
  public abstract Dataset<Row> execute();

}
