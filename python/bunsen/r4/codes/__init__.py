"""
Bunsen Python API for working with Code Systems.
"""

from bunsen.codes import ConceptMaps, ValueSets, Hierarchies

def get_concept_maps(spark_session, database='ontologies'):
    """
    Returns a :class:`bunsen.codes.ConceptMaps` instance for the given database.

    :param database: the database containing the concept maps to load
    :return: a :class:`bunsen.codes.ConceptMaps` with the loaded maps
    """
    jconcept_maps = spark_session._jvm.com.cerner.bunsen.r4.codes \
      .ConceptMaps.getFromDatabase(spark_session._jsparkSession, database)

    jfunctions = spark_session._jvm.com.cerner.bunsen.r4.python.Functions

    java_package = spark_session._jvm.org.hl7.fhir.r4.model

    return ConceptMaps(spark_session,
                       jconcept_maps,
                       jfunctions,
                       java_package)

def create_concept_maps(spark_session):
    """
    Creates a new, empty :py:class:`bunsen.codes.ConceptMaps` instance.

    :return: an empty :class:`bunsen.codes.ConceptMaps` instance
    """
    jconcept_maps = spark_session._jvm.com.cerner.bunsen.r4.codes \
      .ConceptMaps.getEmpty(spark_session._jsparkSession)

    jfunctions = spark_session._jvm.com.cerner.bunsen.r4.python.Functions

    java_package = spark_session._jvm.org.hl7.fhir.r4.model

    return ConceptMaps(spark_session,
                       jconcept_maps,
                       jfunctions,
                       java_package)

def get_value_sets(spark_session, database='ontologies'):
    """
    Returns a :class:`bunsen.codes.ValueSets` instance for the given database.

    :param database: the database containing the value sets to load
    :return: a :class:`bunsen.codes.ValueSets` with the loaded value sets
    """
    jvalue_sets = spark_session._jvm.com.cerner.bunsen.r4.codes \
      .ValueSets.getFromDatabase(spark_session._jsparkSession, database)

    jfunctions = spark_session._jvm.com.cerner.bunsen.r4.python.Functions

    java_package = spark_session._jvm.org.hl7.fhir.r4.model

    return ValueSets(spark_session,
                     jvalue_sets,
                     jfunctions,
                     java_package)

def create_value_sets(spark_session):
    """
    Creates a new, empty :class:`bunsen.codes.ValueSets` instance.

    :return: an empty :class:`bunsen.codes.ValueSets` instance
    """
    jvalue_sets = spark_session._jvm.com.cerner.bunsen.r4.codes \
      .ValueSets.getEmpty(spark_session._jsparkSession)

    jfunctions = spark_session._jvm.com.cerner.bunsen.r4.python.Functions

    java_package = spark_session._jvm.org.hl7.fhir.r4.model

    return ValueSets(spark_session,
                     jvalue_sets,
                     jfunctions,
                     java_package)

def get_hierarchies(spark_session, database='ontologies'):
    """
    Returns a :class:`bunsen.codes.Hierarchies` instance for the given database.

    :param database: the database containing the hierarchies to load
    :return: a :class:`bunsen.codes.Hierarchies` with the loaded value sets
    """
    jhierarchies = spark_session._jvm.com.cerner.bunsen.codes \
        .Hierarchies.getFromDatabase(spark_session._jsparkSession, database)

    return Hierarchies(spark_session, jhierarchies)

def create_hierarchies(spark_session):
    """
    Creates a new, empty :class:`bunsen.codes.Hierarchies` instance.

    :return: an empty :class:`bunsen.codes.Hierarchies` instance
    """
    jhierarchies = spark_session._jvm.com.cerner.bunsen.codes \
        .Hierarchies.getEmpty(spark_session._jsparkSession)

    return Hierarchies(spark_session, jhierarchies)
