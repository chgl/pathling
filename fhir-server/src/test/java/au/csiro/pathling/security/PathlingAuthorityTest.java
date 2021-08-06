/*
 * Copyright © 2018-2021, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.security;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import au.csiro.pathling.security.PathlingAuthority.AccessType;
import java.util.Arrays;
import java.util.Collections;
import javax.annotation.Nonnull;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("UnitTest")
public class PathlingAuthorityTest {

  @Nonnull
  private static PathlingAuthority auth(@Nonnull final String authority) {
    return PathlingAuthority.fromAuthority(authority);
  }

  @SuppressWarnings("SameParameterValue")
  private static void assertFailsValidation(@Nonnull final String authority,
      @Nonnull final String message) {
    assertThrows(IllegalArgumentException.class, () -> PathlingAuthority.fromAuthority(authority),
        message);
  }

  private static void assertFailsValidation(@Nonnull final String authority) {
    assertThrows(IllegalArgumentException.class, () -> PathlingAuthority.fromAuthority(authority),
        "Authority is not recognized: " + authority);
  }

  @Test
  void testValidation() {
    // positive cases
    PathlingAuthority.fromAuthority("pathling");
    PathlingAuthority.fromAuthority("pathling:read");
    PathlingAuthority.fromAuthority("pathling:write");
    PathlingAuthority.fromAuthority("pathling:aggregate");
    PathlingAuthority.fromAuthority("pathling:search");
    PathlingAuthority.fromAuthority("pathling:import");
    for (final ResourceType resourceType : ResourceType.values()) {
      PathlingAuthority.fromAuthority("pathling:read:" + resourceType.toCode());
      PathlingAuthority.fromAuthority("pathling:write:" + resourceType.toCode());
    }

    // negative cases
    assertFailsValidation("*");
    assertFailsValidation("read");
    assertFailsValidation("read:Patient");
    assertFailsValidation("pathling:read:*");
    assertFailsValidation("pathling:*");
    assertFailsValidation("pathling:*:*");
    assertFailsValidation("pathling::");
    assertFailsValidation("pathling::Patient");
    assertFailsValidation("pathling:search:Patient", "Subject not supported for action: search");
    assertFailsValidation("pathling:se_arch");
    assertFailsValidation("pathling:read:Clinical_Impression");
  }

  @Test
  public void testResourceAccessSubsumedBy() {
    final PathlingAuthority patientRead = PathlingAuthority.fromAuthority("pathling:read:Patient");
    final PathlingAuthority conditionWrite = PathlingAuthority
        .fromAuthority("pathling:write:Condition");

    // positive cases
    assertTrue(patientRead.subsumedBy(auth("pathling:read:Patient")));
    assertTrue(patientRead.subsumedBy(auth("pathling:read")));
    assertTrue(patientRead.subsumedBy(auth("pathling")));

    assertTrue(conditionWrite.subsumedBy(auth("pathling:write:Condition")));
    assertTrue(conditionWrite.subsumedBy(auth("pathling:write")));
    assertTrue(conditionWrite.subsumedBy(auth("pathling")));

    // negative cases
    assertFalse(patientRead.subsumedBy(auth("pathling:write")));
    assertFalse(patientRead.subsumedBy(auth("pathling:write:Patient")));
    assertFalse(conditionWrite.subsumedBy(auth("pathling:read")));
    assertFalse(conditionWrite.subsumedBy(auth("pathling:write:DiagnosticReport")));
  }

  @Test
  public void testOperationAccessSubsumedBy() {
    final PathlingAuthority search = PathlingAuthority.fromAuthority("pathling:search");

    // positive cases
    assertTrue(search.subsumedBy(auth("pathling:search")));
    assertTrue(search.subsumedBy(auth("pathling")));

    // negative cases
    assertFalse(search.subsumedBy(auth("pathling:import")));
    assertFalse(search.subsumedBy(auth("pathling:read")));
    assertFalse(search.subsumedBy(auth("pathling:write:ClinicalImpression")));
  }

  @Test
  public void testSubsumedByAny() {
    final PathlingAuthority search = PathlingAuthority.fromAuthority("pathling:search");

    // Negative cases
    assertFalse(search.subsumedByAny(Collections.emptyList()));
    assertFalse(search.subsumedByAny(Arrays.asList(
        auth("pathling:import"),
        auth("pathling:aggregate")
    )));

    assertTrue(search.subsumedByAny(Arrays.asList(
        auth("pathling:import"),
        auth("pathling:search")
    )));
  }

  @Test
  void testResourceAccess() {
    final PathlingAuthority authority = PathlingAuthority
        .resourceAccess(AccessType.READ, ResourceType.PATIENT);
    assertEquals("pathling:read:Patient", authority.getAuthority());
    assertTrue(authority.getAction().isPresent());
    assertEquals("read", authority.getAction().get());
    assertTrue(authority.getSubject().isPresent());
    assertEquals("Patient", authority.getSubject().get());
  }

  @Test
  void testOperationAccess() {
    final PathlingAuthority authority = PathlingAuthority.operationAccess("aggregate");
    assertEquals("pathling:aggregate", authority.getAuthority());
    assertTrue(authority.getAction().isPresent());
    assertEquals("aggregate", authority.getAction().get());
  }

}