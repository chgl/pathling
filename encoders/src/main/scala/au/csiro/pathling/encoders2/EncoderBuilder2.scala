package au.csiro.pathling.encoders2

import au.csiro.pathling.encoders.datatypes.DataTypeMappings
import ca.uhn.fhir.context._
import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder

import scala.reflect.ClassTag

/**
 * Spark Encoder for FHIR data models.
 */
object EncoderBuilder2 {

  val UNSUPPORTED_RESOURCES: Set[String] = Set("Parameters",
    "Task", "StructureDefinition", "StructureMap", "Bundle")

  /**
   * Returns an encoder for the FHIR resource implemented by the given class
   *
   * @param resourceDefinition The FHIR resource definition
   * @param fhirContext        the FHIR context to use
   * @param mappings           the data type mappings to use
   * @param maxNestingLevel    the max nesting level to use to expand recursive data types.
   *                           Zero means that fields of type T are skipped in a composite od type T.
   * @return An ExpressionEncoder for the resource
   */

  def of(resourceDefinition: RuntimeResourceDefinition,
         fhirContext: FhirContext,
         mappings: DataTypeMappings,
         maxNestingLevel: Int): ExpressionEncoder[_] = {

    if (UNSUPPORTED_RESOURCES.contains(resourceDefinition.getName)) {
      throw new IllegalArgumentException(s"Encoding is not supported for resource: ${resourceDefinition.getName}")
    }

    val fhirClass = resourceDefinition
      .asInstanceOf[BaseRuntimeElementDefinition[_]].getImplementingClass
    val schemaConverter = new SchemaConverter2(fhirContext, mappings, maxNestingLevel)
    val serializerBuilder = SerializerBuilder2(schemaConverter)
    val deserializerBuilder = DeserializerBuilder2(schemaConverter)
    new ExpressionEncoder(
      serializerBuilder.buildSerializer(resourceDefinition),
      deserializerBuilder.buildDeserializer(resourceDefinition),
      ClassTag(fhirClass))
  }
}