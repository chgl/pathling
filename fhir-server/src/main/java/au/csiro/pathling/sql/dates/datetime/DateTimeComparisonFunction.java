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

package au.csiro.pathling.sql.dates.datetime;

import au.csiro.pathling.sql.dates.TemporalComparisonFunction;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;
import org.hl7.fhir.r4.model.DateTimeType;

/**
 * Base class for functions that compare datetimes.
 *
 * @author John Grimes
 */
public abstract class DateTimeComparisonFunction extends
    TemporalComparisonFunction<Object, DateTimeType> {

  private static final long serialVersionUID = -2449192480093120211L;

  @Override
  protected Function<Object, DateTimeType> parseEncodedValue() {
    return (object) -> {
      if (object instanceof Timestamp) {
        final Instant instant = ((Timestamp) object).toInstant();
        return new DateTimeType(Date.from(instant));
      } else {
        return new DateTimeType((String) object);
      }
    };
  }

}
