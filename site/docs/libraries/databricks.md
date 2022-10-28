---
sidebar_position: 5
---

# Installation in Databricks

To make the Pathling encoders available within notebooks, navigate to the
"Compute" section and click on the cluster. Click on the "Libraries" tab, and
click "Install new".

Install both the `pathling` PyPI package, and
the `au.csiro.pathling:library-api`
Maven package. Once the cluster is restarted, the libraries should be available
for import and use within all notebooks.

By default, Databricks uses Java 8 within its clusters, while Pathling requires
Java 11. To enable Java 11 support within your cluster, navigate to __Advanced
Options > Spark > Environment Variables__ and add the following:

```bash
JNAME=zulu11-ca-amd64
```

See the Databricks documentation on
[Libraries](https://docs.databricks.com/libraries/index.html) for more
information.