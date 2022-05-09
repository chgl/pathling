/*
 * Copyright © 2018-2022, Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230. Licensed under the CSIRO Open Source
 * Software Licence Agreement.
 */

package au.csiro.pathling.fhirpath.parser;

import static au.csiro.pathling.utilities.Preconditions.checkNotNull;

import au.csiro.pathling.encoders.terminology.ucum.Ucum;
import au.csiro.pathling.errors.InvalidUserInputError;
import au.csiro.pathling.fhirpath.FhirPath;
import au.csiro.pathling.fhirpath.literal.BooleanLiteralPath;
import au.csiro.pathling.fhirpath.literal.CodingLiteralPath;
import au.csiro.pathling.fhirpath.literal.DateLiteralPath;
import au.csiro.pathling.fhirpath.literal.DateTimeLiteralPath;
import au.csiro.pathling.fhirpath.literal.DecimalLiteralPath;
import au.csiro.pathling.fhirpath.literal.IntegerLiteralPath;
import au.csiro.pathling.fhirpath.literal.NullLiteralPath;
import au.csiro.pathling.fhirpath.literal.QuantityLiteralPath;
import au.csiro.pathling.fhirpath.literal.StringLiteralPath;
import au.csiro.pathling.fhirpath.literal.TimeLiteralPath;
import au.csiro.pathling.fhirpath.parser.generated.FhirPathBaseVisitor;
import au.csiro.pathling.fhirpath.parser.generated.FhirPathParser.BooleanLiteralContext;
import au.csiro.pathling.fhirpath.parser.generated.FhirPathParser.CodingLiteralContext;
import au.csiro.pathling.fhirpath.parser.generated.FhirPathParser.DateLiteralContext;
import au.csiro.pathling.fhirpath.parser.generated.FhirPathParser.DateTimeLiteralContext;
import au.csiro.pathling.fhirpath.parser.generated.FhirPathParser.NullLiteralContext;
import au.csiro.pathling.fhirpath.parser.generated.FhirPathParser.NumberLiteralContext;
import au.csiro.pathling.fhirpath.parser.generated.FhirPathParser.QuantityLiteralContext;
import au.csiro.pathling.fhirpath.parser.generated.FhirPathParser.StringLiteralContext;
import au.csiro.pathling.fhirpath.parser.generated.FhirPathParser.TimeLiteralContext;
import java.text.ParseException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.fhir.ucum.UcumException;

/**
 * This class deals with terms that are literal expressions.
 *
 * @author John Grimes
 */
class LiteralTermVisitor extends FhirPathBaseVisitor<FhirPath> {

  @Nonnull
  private final ParserContext context;

  LiteralTermVisitor(@Nonnull final ParserContext context) {
    this.context = context;
  }

  @Override
  @Nonnull
  public FhirPath visitCodingLiteral(@Nullable final CodingLiteralContext ctx) {
    @Nullable final String fhirPath = checkNotNull(ctx).getText();
    checkNotNull(fhirPath);
    try {
      return CodingLiteralPath.fromString(fhirPath,
          context.getThisContext().orElse(context.getInputContext()));
    } catch (final IllegalArgumentException e) {
      throw new InvalidUserInputError(e.getMessage(), e);
    }
  }

  @Override
  @Nonnull
  public FhirPath visitStringLiteral(@Nullable final StringLiteralContext ctx) {
    @Nullable final String fhirPath = checkNotNull(ctx).getText();
    checkNotNull(fhirPath);
    return StringLiteralPath.fromString(fhirPath,
        context.getThisContext().orElse(context.getInputContext()));
  }

  @Override
  public FhirPath visitDateLiteral(@Nullable final DateLiteralContext ctx) {
    @Nullable final String fhirPath = checkNotNull(ctx).getText();
    checkNotNull(fhirPath);
    try {
      return DateLiteralPath.fromString(fhirPath,
          context.getThisContext().orElse(context.getInputContext()));
    } catch (final ParseException ex) {
      throw new InvalidUserInputError("Unable to parse date format: " + fhirPath);
    }
  }

  @Override
  @Nonnull
  public FhirPath visitDateTimeLiteral(@Nullable final DateTimeLiteralContext ctx) {
    @Nullable final String fhirPath = checkNotNull(ctx).getText();
    checkNotNull(fhirPath);
    try {
      return DateTimeLiteralPath.fromString(fhirPath,
          context.getThisContext().orElse(context.getInputContext()));
    } catch (final ParseException ex) {
      throw new InvalidUserInputError("Unable to parse date/time format: " + fhirPath);
    }
  }

  @Override
  @Nonnull
  public FhirPath visitTimeLiteral(@Nullable final TimeLiteralContext ctx) {
    @Nullable final String fhirPath = checkNotNull(ctx).getText();
    checkNotNull(fhirPath);
    return TimeLiteralPath.fromString(fhirPath,
        context.getThisContext().orElse(context.getInputContext()));
  }

  @Override
  @Nonnull
  public FhirPath visitNumberLiteral(@Nullable final NumberLiteralContext ctx) {
    @Nullable final String fhirPath = checkNotNull(ctx).getText();
    checkNotNull(fhirPath);
    // The FHIRPath grammar lumps these two types together, so we tease them apart by trying to 
    // parse them. A better way of doing this would be to modify the grammar.
    try {
      return IntegerLiteralPath.fromString(fhirPath,
          context.getThisContext().orElse(context.getInputContext()));
    } catch (final NumberFormatException e) {
      try {
        return DecimalLiteralPath.fromString(fhirPath,
            context.getThisContext().orElse(context.getInputContext()));
      } catch (final NumberFormatException ex) {
        throw new InvalidUserInputError("Invalid date format: " + fhirPath);
      }
    }
  }

  @Override
  @Nonnull
  public FhirPath visitBooleanLiteral(@Nullable final BooleanLiteralContext ctx) {
    checkNotNull(ctx);
    @Nullable final String fhirPath = ctx.getText();
    checkNotNull(fhirPath);
    return BooleanLiteralPath.fromString(fhirPath,
        context.getThisContext().orElse(context.getInputContext()));
  }

  @Override
  @Nonnull
  public FhirPath visitNullLiteral(@Nullable final NullLiteralContext ctx) {
    return NullLiteralPath.build(context.getThisContext().orElse(context.getInputContext()));
  }

  @Override
  @Nonnull
  public FhirPath visitQuantityLiteral(@Nullable final QuantityLiteralContext ctx) {
    checkNotNull(ctx);
    @Nullable final String number = ctx.quantity().NUMBER().getText();
    checkNotNull(number);

    final FhirPath resultContext = this.context.getThisContext().orElse(context.getInputContext());
    @Nullable final TerminalNode ucumUnit = ctx.quantity().unit().STRING();

    if (ucumUnit == null) {
      // Create a calendar duration literal.
      final String fhirPath = String.format("%s %s", number, ctx.quantity().unit().getText());
      return QuantityLiteralPath.fromCalendarDurationString(fhirPath, resultContext);
    } else {
      // Create a UCUM Quantity literal.
      final String fhirPath = String.format("%s %s", number, ucumUnit.getText());
      try {
        return QuantityLiteralPath.fromUcumString(fhirPath, resultContext,
            Ucum.ucumEssenceService());
      } catch (final UcumException e) {
        throw new RuntimeException(e);
      }
    }
  }

}
