/*
 * Copyright © Australian e-Health Research Centre, CSIRO. All rights reserved.
 */

package au.csiro.clinsight.query.spark;

import static au.csiro.clinsight.query.spark.Mappings.getFhirTypeForFunction;
import static au.csiro.clinsight.query.spark.Mappings.translateFunctionToSpark;

import au.csiro.clinsight.fhir.FhirPathBaseVisitor;
import au.csiro.clinsight.fhir.FhirPathLexer;
import au.csiro.clinsight.fhir.FhirPathParser;
import au.csiro.clinsight.fhir.FhirPathParser.ExpressionContext;
import au.csiro.clinsight.fhir.FhirPathParser.FunctionInvocationContext;
import au.csiro.clinsight.fhir.FhirPathParser.ParamListContext;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * Parses a FHIRPath aggregation expression, and returns an object which contains a Spark SQL SELECT
 * expression, the data type that the expression will return, and the names of the tables that will
 * need to be included within the FROM clause.
 *
 * @author John Grimes
 */
class AggregationParser {

  ParseResult parse(String expression) {
    FhirPathLexer lexer = new FhirPathLexer(CharStreams.fromString(expression));
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    FhirPathParser parser = new FhirPathParser(tokens);

    ExpressionVisitor expressionVisitor = new ExpressionVisitor();
    return expressionVisitor.visit(parser.expression());
  }

  private static class ExpressionVisitor extends FhirPathBaseVisitor<ParseResult> {


    @Override
    public ParseResult visitFunctionInvocation(FunctionInvocationContext ctx) {
      String functionIdentifier = ctx.functn().identifier().getText();
      String translatedIdentifier = translateFunctionToSpark(functionIdentifier);
      if (translatedIdentifier == null) {
        throw new InvalidRequestException(
            "Unrecognised function: " + functionIdentifier);
      } else {
        ParseResult result = new ParseResult();
        ParseResult paramListResult = ctx.functn().paramList().accept(new ParamListVisitor());
        result.setExpression(translatedIdentifier + "(" + paramListResult.getExpression() + ")");
        result.setResultType(getFhirTypeForFunction(functionIdentifier));
        if (result.getResultType() == null) {
          throw new AssertionError(
              "Data type not found for FHIRPath aggregation function: " + functionIdentifier);
        }
        result.setFromTable(paramListResult.getFromTable());
        return result;
      }
    }

  }

  private static class ParamListVisitor extends FhirPathBaseVisitor<ParseResult> {

    @Override
    public ParseResult visitParamList(ParamListContext ctx) {
      ParseResult parseResult = new ParseResult("");
      for (ExpressionContext expression : ctx.expression()) {
        ParseResult expressionResult = expression.accept(new ValidatingInvocationParser());
        if (parseResult.getExpression().length() > 0
            && expressionResult.getExpression().length() > 0) {
          parseResult
              .setExpression(parseResult.getExpression() + ", " + expressionResult.getExpression());
        } else {
          parseResult.setExpression(expressionResult.getExpression());
        }
        if (parseResult.getFromTable() != null &&
            !parseResult.getFromTable().equals(expressionResult.getFromTable())) {
          throw new IllegalArgumentException(
              "Arguments to aggregate function are from different resources");
        }
        parseResult.setFromTable(expressionResult.getFromTable());
      }
      return parseResult;
    }

  }

}