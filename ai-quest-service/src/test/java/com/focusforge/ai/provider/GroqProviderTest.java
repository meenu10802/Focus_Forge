package com.focusforge.ai.provider;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GroqProviderTest {

    private static AiProperties props(String key) {
        var groq = new AiProperties.Endpoint(key, "https://groq.test/v1", "llama-test");
        var gemini = new AiProperties.Endpoint("", "https://gemini.test", "g");
        return new AiProperties("groq", Duration.ofSeconds(1), Duration.ofSeconds(1), 300, groq, gemini);
    }

    @Test
    void sendsJsonModeChatRequestAndReturnsMessageContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://groq.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("llama-test"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("<goal>x</goal>"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"{\\"tasks\\":[]}"}}]}""",
                        MediaType.APPLICATION_JSON));

        String out = new GroqProvider(props("sk-test"), builder).completeJson("sys", "<goal>x</goal>");

        assertThat(out).isEqualTo("{\"tasks\":[]}");
        server.verify();
    }

    @Test
    void missingKeyFailsFastWithoutCallingTheApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        assertThatThrownBy(() -> new GroqProvider(props(" "), builder).completeJson("s", "u"))
                .isInstanceOf(AiNotConfiguredException.class);
        server.verify(); // no requests expected
    }

    @Test
    void upstreamErrorsSurfaceAsRestClientExceptions() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://groq.test/v1/chat/completions")).andRespond(withServerError());

        assertThatThrownBy(() -> new GroqProvider(props("sk"), builder).completeJson("s", "u"))
                .isInstanceOf(HttpServerErrorException.class);
    }
}
