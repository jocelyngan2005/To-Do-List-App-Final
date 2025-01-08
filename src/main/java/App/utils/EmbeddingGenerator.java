package App.utils;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class EmbeddingGenerator {
    private static final String API_URL = "https://api-inference.huggingface.co/pipeline/feature-extraction/sentence-transformers/all-MiniLM-L6-v2";
    private static final String API_TOKEN = "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";

    public static float[] getEmbedding(String text) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(API_URL);
            request.setHeader("Authorization", "Bearer " + API_TOKEN);
            request.setHeader("Content-Type", "application/json");

            Map<String, String> input = new HashMap<>();
            input.put("inputs", text);

            ObjectMapper mapper = new ObjectMapper();
            String jsonInput = mapper.writeValueAsString(input);
            request.setEntity(new StringEntity(jsonInput));

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    String responseString = EntityUtils.toString(entity);
                    if (responseString.contains("error")) {
                        throw new RuntimeException("Hugging Face API error: " + responseString);
                    }
                    return mapper.readValue(responseString, float[].class);
                } else {
                    throw new RuntimeException("No response from Hugging Face API");
                }
            }
        }
    }

    public static float[] getCombinedEmbedding(String title, String description) throws Exception {
        float[] titleEmbedding = getEmbedding(title);
        float[] descriptionEmbedding = getEmbedding(description);

        float[] combinedEmbedding = new float[titleEmbedding.length];
        for (int i = 0; i < titleEmbedding.length; i++) {
            combinedEmbedding[i] = (titleEmbedding[i] + descriptionEmbedding[i]) / 2.0f;
        }
        System.out.println("Generated embedding length: " + combinedEmbedding.length);
        return combinedEmbedding;
    }
}
