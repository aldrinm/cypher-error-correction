package aldrinm.agent;

import aldrinm.agent.cyver.CyverSyntaxValidatonResult;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.agent.domain.io.UserInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.jetbrains.annotations.Nullable;
import org.neo4j.driver.types.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Agent(description = "Answers any questions the user may have")
public class GraphAgent {

    private final static Logger logger = LoggerFactory.getLogger(GraphAgent.class);
    public final static String CYPHER_HISTORY = "cypherHistory";

    private final static String CYPHER_VALID = "CYPHER_VALID";
    private final static String CYPHER_NOT_VALID = "CYPHER_NOT_VALID";
    private final static String VALIDATE_CYPHER_NEEDED = "VALIDATE_CYPHER_NEEDED";

    private final List<McpSyncClient> mcpSyncClients;
    private final Neo4jClient neo4jClient;

    public GraphAgent(List<McpSyncClient> mcpSyncClients, Neo4jClient neo4jClient) {
        this.mcpSyncClients = mcpSyncClients;
        this.neo4jClient = neo4jClient;
    }

    @Action(
            description = "Generates a cypher statement that may be used to answer the user's query ",
            post = VALIDATE_CYPHER_NEEDED
    )
    CypherStatementRequest generateCypher(UserInput userInput, OperationContext context) {
        context.set(CYPHER_HISTORY, new ConcurrentLinkedQueue<String>()); //updated in the validateCypher method

        String schema = getSchema();
        CypherStatementRequest cypherStatementRequest = context
                .ai()
                .withAutoLlm()
                .createObject(String.format("""
                                Build a cypher query to answer the user's query. Use a case-insensitive contains for
                                string comparisons wherever appropriate. Always limit results to 20 rows
                                # User query
                                %s

                                Use this database schema:
                                # Schema
                                %s

                                Return the cypher as a plain string with no markdown or triple quotes
                                """,
                                userInput.getContent(), schema).trim(),
                        CypherStatementRequest.class);
        logger.info("First attempt at cypher generation: {} ", cypherStatementRequest);
        return cypherStatementRequest;
    }

    @Action(
            description = "Validates the given cypher and generates a report",
            post = {CYPHER_VALID, CYPHER_NOT_VALID},
            canRerun = true,
            pre = VALIDATE_CYPHER_NEEDED
    )
    public ValidationReport validateCypher(CypherStatementRequest cypherStatementRequest, OperationContext context) {
        updateCypherHistory(cypherStatementRequest, context);

        CyverSyntaxValidatonResult syntaxValidationResult = validateSyntax(cypherStatementRequest.cypher());
        CyverSyntaxValidatonResult schemaValidationResult = null;
        CyverSyntaxValidatonResult propertiesValidationResult = null;

        if (syntaxValidationResult.isValid()) {
            //only bother with these next validations if the syntax is correct
            schemaValidationResult = validateCypherSchema(cypherStatementRequest.cypher());
            propertiesValidationResult = validateCypherProperties(cypherStatementRequest.cypher());
        }
        return new ValidationReport(syntaxValidationResult, schemaValidationResult, propertiesValidationResult);
    }

    @Action(
            pre = CYPHER_NOT_VALID,
            description = "Tries to correct the cypher based on feedback",
            post = { VALIDATE_CYPHER_NEEDED },
            canRerun = true
    )
    public CypherStatementRequest rectifyCypher(CypherStatementRequest cypherStatementRequest, ValidationReport validationReport, OperationContext context) {
        logger.info("Attempting to fix the cypher {}", cypherStatementRequest);
        String feedback = formatFeedbackIfAny(validationReport);

        String schema = getSchema();
        CypherStatementRequest updatedCypher = context
                .ai()
                .withAutoLlm()
                .createObject(String.format("""
                                        Review and correct the cypher query.  
                                        # Cypher query
                                        %s
                                        
                                        Feedback about this cypher is:
                                        # Feedback
                                        %s
                                        
                                        Use this database schema:
                                        # Schema
                                        %s
                                        
                                        Return the correct cypher as a plain string with no markdown or triple-quotes
                                        """,
                                cypherStatementRequest.cypher(), feedback, schema).trim(),
                        CypherStatementRequest.class);


        logger.info("Updated cypher = {}", updatedCypher);
        return updatedCypher;
    }

    @Condition(name = VALIDATE_CYPHER_NEEDED)
    public Boolean validateCypherNeeded(OperationContext context) {
        var last = context.lastResult();
        return last instanceof CypherStatementRequest;
    }

    @Condition(name = CYPHER_VALID)
    public boolean isCypherValid(ValidationReport validationReport) {
        return validationReportPasses(validationReport);
    }

    @Condition(name = CYPHER_NOT_VALID)
    public boolean isCypherNotValid(ValidationReport validationReport) {
        return !validationReportPasses(validationReport);
    }

    @Action(
            description = "Executes a cypher statement ",
            pre = CYPHER_VALID
    )
    CypherExecutionResult executeCypher(CypherStatementRequest cypherStatementRequest) {
        Collection<Map<String, Object>> results = neo4jClient
                .query(cypherStatementRequest.cypher())
                .fetch()
                .all();

        List<Map<String, Object>> processedResults = results.stream()
                .map(row -> row.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> {
                                    Object value = entry.getValue();
                                    if (value instanceof Node) {
                                        return ((Node) value).asMap();
                                    }
                                    return value;
                                }
                        )))
                .collect(Collectors.toList());

        return new CypherExecutionResult(cypherStatementRequest.cypher(), processedResults);
    }

    @AchievesGoal(description = "This is the way")
    @Action(description = "Formats the response neatly for presentation to the user")
    FormattedResponse formatResponse(CypherExecutionResult response, OperationContext context) {
        printDebugCypher(context);
        String formattedResponse = context.ai()
                .withAutoLlm()
                .generateText(String.format("""
                                        Format this response for text presentation in plain conversational text.
                                        Only respond with the text response and nothing else. No markdown or triple single quotes.
                                        # Response
                                        %s
                                        """,
                                response.result()).trim());
        return new FormattedResponse(formattedResponse);
    }

    private static boolean validationReportPasses(ValidationReport validationReport) {
        //if there is a syntax validation, report it immediately
        if (validationReport.syntaxResult() != null && validationReport.syntaxResult().isValid() != null && !validationReport.syntaxResult().isValid()) {
            return false;
        }

        // Check schema validation result
        if (validationReport.schemaResult() != null) {
            if (validationReport.schemaResult().score() != null && validationReport.schemaResult().score() < 1.0f) {
                return false;
            }
            if (validationReport.schemaResult().metadata() != null && !validationReport.schemaResult().metadata().isEmpty()) {
                return false;
            }
        }

        // Check properties validation result
        if (validationReport.propertiesResult() != null) {
            if (validationReport.propertiesResult().score() != null && validationReport.propertiesResult().score() < 1.0f) {
                return false;
            }
            if (validationReport.propertiesResult().metadata() != null && !validationReport.propertiesResult().metadata().isEmpty()) {
                return false;
            }
        }

        //everything looks good!
        return true;
    }

    private static void updateCypherHistory(CypherStatementRequest cypherStatementRequest, OperationContext context) {
        if (context.get(CYPHER_HISTORY) != null) {
            @SuppressWarnings("unchecked")
            ConcurrentLinkedQueue<String> cypherHistory = (ConcurrentLinkedQueue<String>) context.get(CYPHER_HISTORY);
            if (cypherHistory != null) {
                cypherHistory.add(cypherStatementRequest.cypher());
            }
        }
    }

    @Nullable
    private static String formatFeedbackIfAny(ValidationReport validationReport) {
        StringBuilder metadataBuilder = new StringBuilder();
        if (validationReport.syntaxResult() != null && validationReport.syntaxResult().isValid() != null) {
            if (!validationReport.syntaxResult().isValid()) {
                metadataBuilder.append("Syntax Validation Result: ");
                for (Map<String, Object> meta : validationReport.syntaxResult().metadata()) {
                    metadataBuilder.append(meta.toString()).append("; ");
                }
                metadataBuilder.append("\n");
            }
        }

        if (validationReport.schemaResult() != null) {
            if (validationReport.schemaResult().metadata() != null && !validationReport.schemaResult().metadata().isEmpty()) {
                metadataBuilder.append("Schema Validation Result: ");
                for (Map<String, Object> meta : validationReport.schemaResult().metadata()) {
                    metadataBuilder.append(meta.toString()).append("; ");
                }
                metadataBuilder.append("\n");
            }
        }

        if (validationReport.propertiesResult() != null) {
            if (validationReport.propertiesResult().metadata() != null && !validationReport.propertiesResult().metadata().isEmpty()) {
                metadataBuilder.append("Properties Validation Metadata: ");
                for (Map<String, Object> meta : validationReport.propertiesResult().metadata()) {
                    metadataBuilder.append(meta.toString()).append("; ");
                }
                metadataBuilder.append("\n");
            }
        }

        // Save metadata to string if we have any
        if (!metadataBuilder.isEmpty()) {
            return metadataBuilder.toString();
        } else {
            return null;
        }
    }

    private static void printDebugCypher(OperationContext context) {
        @SuppressWarnings("unchecked")
        ConcurrentLinkedQueue<String> cypherHistory = (ConcurrentLinkedQueue<String>) context.get(CYPHER_HISTORY);
        logger.info("Cypher History:\n {}", String.join("\n", Objects.requireNonNull(cypherHistory)));
    }

    private String getSchema() {
        ToolCallback callback = findTool("mcp-neo4j-cypher", "get_neo4j_schema");
        String result = callback.call("{}");

        //parse the result string which is in json
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            List<Map<String, Object>> listResult = objectMapper.readValue(result, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            // Assuming the first element of the list contains the actual data
            if (!listResult.isEmpty()) {
                Map<String, Object> dataMap = listResult.get(0);
                String textValue = (String) dataMap.get("text");
                //let's not bother parsing this. LLMs are supposed to work better with JSON isn't it ? ;)
                return textValue;
            }
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to parse result", ex);
        }

        throw new RuntimeException("Could not get the schema. No MCP tools available!");
    }

    private CyverSyntaxValidatonResult validateSyntax(String cypherStatement) {
        return validateCypherWithTool(cypherStatement, "validate_cypher_syntax");
    }

    private CyverSyntaxValidatonResult validateCypherSchema(String cypherStatement) {
        return validateCypherWithTool(cypherStatement, "schema_validator");
    }

    private CyverSyntaxValidatonResult validateCypherProperties(String cypherStatement) {
        return validateCypherWithTool(cypherStatement, "validate_cypher_properties");
    }

    private CyverSyntaxValidatonResult validateCypherWithTool(String cypherStatement, String toolName) {
        ToolCallback callback = findTool("cyver", toolName);
        String result = callback.call("{\"query\": \"" + cypherStatement + "\"}");

        //parse the result string which is in json
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            List<Map<String, Object>> listResult = objectMapper.readValue(result, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            // Assuming the first element of the list contains the actual data
            if (!listResult.isEmpty()) {
                Map<String, Object> dataMap = listResult.get(0);
                String textValue = (String) dataMap.get("text");
                return objectMapper.readValue(textValue, CyverSyntaxValidatonResult.class);
            }
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to parse result", ex);
        }

        throw new RuntimeException("Could not validate the cypher. No MCP tools available!");
    }

    /**
     * Finds an MCP tool by client name and tool name
     * @return ToolCallback for the found tool
     */
    private ToolCallback findTool(String mcpClientName, String toolName) {
        Optional<McpSyncClient> mcpClient = mcpSyncClients.stream()
                .filter(c -> c.getServerInfo().name().equals(mcpClientName))
                .findAny();

        if (mcpClient.isEmpty()) {
            throw new RuntimeException("Could not find the " + mcpClientName + " client");
        }

        var client = mcpClient.get();
        Optional<McpSchema.Tool> tool = client.listTools().tools().stream()
                .filter(t -> t.name().equals(toolName))
                .findAny();

        if (tool.isEmpty()) {
            throw new RuntimeException("Could not find tool '" + toolName + "' in the " + mcpClientName + " client");
        }

        return new SyncMcpToolCallback(client, tool.get());
    }

}
