A sample project that generates cypher from a natural language query. The cypher is evaluated for syntax correctness
and schema validity. If there are any discrepancies then it tries to correct the cypher.

## Neo4j
Requires a running instance of Neo4j database. This is configured through `application.properties`
```properties
spring.neo4j.uri=bolt://localhost:7687
spring.neo4j.authentication.username=neo4j
spring.neo4j.authentication.password=password
spring.data.neo4j.database=lego
```

## MCP
Requires the CyVer MCP tool and the Neo4j's mcp cypher tool. These are configured through `application.properties`

CyVer is a python library available at https://gitlab.com/netmode/CyVer . I've wrapped into an MCP tool available here - https://github.com/aldrinm/cyver-mcp . This is
what we configure here. Requires python to be installed locally.

```properties
spring.ai.mcp.client.stdio.connections.cyver.command=C:\\Aldrin\\projects\\aldrinm\\cyver-mcp\\cyver-mcp-env\\Scripts\\python.exe
spring.ai.mcp.client.stdio.connections.cyver.args=C:\\Aldrin\\projects\\aldrinm\\cyver-mcp\\cyver-mcp-server.py
spring.ai.mcp.client.stdio.connections.cyver.env.NEO4J_URL=bolt://localhost:7687
spring.ai.mcp.client.stdio.connections.cyver.env.NEO4J_USERNAME=neo4j
spring.ai.mcp.client.stdio.connections.cyver.env.NEO4J_PASSWORD=password
spring.ai.mcp.client.stdio.connections.cyver.env.NEO4J_DATABASE=lego

spring.ai.mcp.client.stdio.connections.mcp-neo4j-cypher.command=docker
spring.ai.mcp.client.stdio.connections.mcp-neo4j-cypher.args=run,-i,--rm,-e,NEO4J_URI=bolt://localhost:7687,-e,NEO4J_USERNAME=neo4j,-e,NEO4J_PASSWORD=password,-e,NEO4J_DATABASE=lego,mcp/neo4j-cypher:latest
```

## Custom Model Configuration
The application uses a custom OpenAI-compatible model. Configure these environment variables with your model details

```
CUSTOM_MODEL_BASE_URL=<your_model_base_url>
CUSTOM_MODEL_API_KEY=<your_model_api_key>
CUSTOM_MODEL_NAME=<your_model_name>
```

These values are picked up by the `CustomOpenAiCompatibleModels.java` class to configure the custom LLM.

## NOT FOR PRODUCTION USE
Note that the cypher is executed against the Neo4j database. Therefore, use with caution. Ensure that the database doesn't have any 
confidential or critical information. Other techniques are available to secure the database against malicious 
queries.
