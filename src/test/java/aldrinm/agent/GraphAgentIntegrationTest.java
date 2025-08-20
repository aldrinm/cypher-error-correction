package aldrinm.agent;

import com.embabel.agent.api.common.autonomy.AgentInvocation;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.testing.integration.EmbabelMockitoIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;

class GraphAgentIntegrationTest extends EmbabelMockitoIntegrationTest {

    @Test
    void shouldExecuteCompleteWorkflow() {
        var input = new UserInput("How many minifigs are available ?");

        CypherStatementRequest cypherStatementRequest = new CypherStatementRequest("MATCH (n:Minifig) RETURN count(n) as numMinifigs");
        whenCreateObject(contains("How many minifigs are available ?"), CypherStatementRequest.class)
        .thenReturn(cypherStatementRequest);

        whenGenerateText(contains("Format this response for text presentation"))
                .thenReturn("There are 42 minifigs present");

        var invocation = AgentInvocation.create(agentPlatform, FormattedResponse.class);
        var formattedResponse = invocation.invoke(input);
        System.out.println("formattedResponse = " + formattedResponse);
        assertNotNull(formattedResponse);
        assertEquals("There are 42 minifigs present", formattedResponse.response());

        verifyCreateObjectMatching(prompt -> prompt.contains("How many minifigs are available ?"), CypherStatementRequest.class,
                llm -> llm.getToolGroups().isEmpty() == true);
        verifyGenerateTextMatching(prompt -> prompt.contains("Format this response for text presentation"));
        verifyNoMoreInteractions();
    }
}
