package aldrinm.agent.cyver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties
public record CyverSyntaxValidatonResult(
        @JsonProperty("validation_type") String validationType,
        String query,
        @JsonProperty("is_valid") Boolean isValid,
        Float score,
        List<Map<String, Object>> metadata) {
}
