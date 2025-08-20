package aldrinm.agent;

public record CypherExecutionResult(String cypher, java.util.Collection<java.util.Map<String, Object>> result) {
}
