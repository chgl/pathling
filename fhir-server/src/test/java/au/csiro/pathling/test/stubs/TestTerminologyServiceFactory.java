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

package au.csiro.pathling.test.stubs;

import au.csiro.pathling.fhir.TerminologyServiceFactory;
import au.csiro.pathling.terminology.TerminologyService;
import au.csiro.pathling.test.SharedMocks;
import javax.annotation.Nonnull;
import org.slf4j.Logger;

public class TestTerminologyServiceFactory implements TerminologyServiceFactory {

  private static final long serialVersionUID = -8229464411116137820L;

  public TestTerminologyServiceFactory() {
  }

  @Nonnull
  @Override
  public TerminologyService buildService(@Nonnull final Logger logger) {
    return SharedMocks.getOrCreate(TerminologyService.class);
  }
}
