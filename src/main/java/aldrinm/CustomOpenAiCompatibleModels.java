package aldrinm;

import com.embabel.agent.config.models.OpenAiCompatibleModelFactory;
import com.embabel.common.ai.model.Llm;
import com.embabel.common.ai.model.PerTokenPricingModel;
import io.micrometer.observation.ObservationRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

@Configuration
class CustomOpenAiCompatibleModels extends OpenAiCompatibleModelFactory {

    @Value("${CUSTOM_MODEL_NAME}")
    private String customModelName;

    @Value("${CUSTOM_MODEL_PROVIDER}")
    private String provider;

    @Value("${CUSTOM_MODEL_usdPer1mInputTokens:0.0}")
    private Double usdPer1mInputTokens;

    @Value("${CUSTOM_MODEL_CUSTOM_MODEL_usdPer1mOutputTokens:0.0}")
    private Double usdPer1mOutputTokens;

    public CustomOpenAiCompatibleModels(
            @Value("${CUSTOM_MODEL_BASE_URL:#{null}}") @Nullable String baseUrl,
            @Value("${CUSTOM_MODEL_API_KEY}") @Nullable String apiKey,
            @NotNull ObservationRegistry observationRegistry) {
        super(baseUrl, apiKey, null, null, observationRegistry);
    }

    @Bean
    Llm customModel() {
        return openAiCompatibleLlm(
                customModelName,
                new PerTokenPricingModel(usdPer1mInputTokens, usdPer1mOutputTokens),
                provider,
                null);
//                LocalDate.of(2025, 1, 1));
    }
}