package aldrinm.agent;

import com.embabel.agent.api.common.autonomy.AgentInvocation;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.testing.integration.EmbabelMockitoIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;

class GraphAgentIntegrationTest extends EmbabelMockitoIntegrationTest {

    @BeforeAll
    static void setUp() {
        // Set shell configuration to non-interactive mode
        System.setProperty("embabel.agent.shell.interactive.enabled", "false");
    }

    @Test
    void shouldExecuteCompleteWorkflow() {
        var input = new UserInput("How many minifigs are available ?");

        CypherStatementRequest cypherStatementRequest = new CypherStatementRequest("MATCH (n:Minifig) RETURN count(n) as numMinifigs");
        whenCreateObject(prompt -> prompt.contains("How many minifigs are available ?"), CypherStatementRequest.class)
                .thenReturn(cypherStatementRequest);

        whenGenerateText(prompt -> prompt.contains("Format this response for text presentation"))
                .thenReturn("There are 42 minifigs present");

        var invocation = AgentInvocation.create(agentPlatform, FormattedResponse.class);
        var formattedResponse = invocation.invoke(input);
        System.out.println("formattedResponse = " + formattedResponse);
        assertNotNull(formattedResponse);
        assertEquals("There are 42 minifigs present", formattedResponse.response());

        verifyCreateObjectMatching(prompt -> prompt.contains("How many minifigs are available ?"), CypherStatementRequest.class,
                llm -> llm.getToolGroups().isEmpty());
        verifyGenerateTextMatching(prompt -> prompt.contains("Format this response for text presentation"));
        verifyNoMoreInteractions();
    }
}
