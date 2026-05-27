package com.example.datavalidator.service;

import com.example.datavalidator.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleChatClientTest {
    @Test
    void sendsOpenAiCompatibleChatRequestAndParsesContent() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AppProperties properties = new AppProperties();
        properties.getAi().setEnabled(true);
        properties.getAi().setEndpoint("http://localhost:9000");
        properties.getAi().setApiKey("local-key");
        properties.getAi().setModel("local-model");
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(
                properties, restTemplate, new ObjectMapper());

        server.expect(requestTo("http://localhost:9000/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer local-key"))
                .andExpect(jsonPath("$.model", is("local-model")))
                .andExpect(jsonPath("$.messages[0].role", is("system")))
                .andExpect(jsonPath("$.messages[0].content", is("system prompt")))
                .andExpect(jsonPath("$.messages[1].role", is("user")))
                .andExpect(jsonPath("$.messages[1].content", is("user prompt")))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"model answer\"}}]}",
                        MediaType.APPLICATION_JSON));

        Optional<String> result = client.complete("system prompt", "user prompt");

        assertThat(result).contains("model answer");
        server.verify();
    }

    @Test
    void extractsR026RecommendationContentWhenReasoningContentIsPresent() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AppProperties properties = new AppProperties();
        properties.getAi().setEnabled(true);
        properties.getAi().setEndpoint("http://127.0.0.1:8317/v1/chat/completions");
        properties.getAi().setApiKey("local-key");
        properties.getAi().setModel("gpt-5.4");
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(
                properties, restTemplate, new ObjectMapper());
        String content = "{\"templateCode\":\"RELATION_EXISTS\",\"templateParams\":{\"source\":\"t_order\","
                + "\"target\":\"t_payment\",\"keys\":[{\"sourceField\":\"订单ID\",\"targetField\":\"订单ID\"}],"
                + "\"expectExists\":false,\"sourceWhere\":{\"left\":{\"field\":\"订单状态\"},"
                + "\"operator\":\"==\",\"right\":{\"literal\":\"已取消\"}},"
                + "\"targetWhere\":{\"and\":[{\"left\":{\"field\":\"支付状态\"},\"operator\":\"==\","
                + "\"right\":{\"literal\":\"支付成功\"}},{\"left\":{\"field\":\"退款金额\"},"
                + "\"operator\":\"==\",\"right\":{\"literal\":0}}]},\"confidence\":0.91,"
                + "\"explanation\":\"模型推荐取消订单支付反向存在性\"}";
        String response = "{\"id\":\"resp_08cc10bc683c018f016a0001a7f1e0819a95f7665c0e58ff99\","
                + "\"object\":\"chat.completion\",\"created\":1778385319,\"model\":\"gpt-5.4\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":"
                + new ObjectMapper().valueToTree(content)
                + ",\"reasoning_content\":\"**Evaluating JSON output rules**\"},"
                + "\"finish_reason\":\"stop\",\"native_finish_reason\":\"stop\"}]}";

        server.expect(requestTo("http://127.0.0.1:8317/v1/chat/completions"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        Optional<String> result = client.complete("system", "user");

        assertThat(result).contains(content);
        assertThat(result.orElse("")).contains("\"templateCode\":\"RELATION_EXISTS\"");
        assertThat(result.orElse("")).doesNotContain("reasoning_content");
        server.verify();
    }

    @Test
    void springCanCreateClientBeanWithAutowiredConstructor() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(AppProperties.class, AppProperties::new);
        context.registerBean(RestTemplateBuilder.class, () -> new RestTemplateBuilder());
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.register(OpenAiCompatibleChatClient.class);

        context.refresh();

        assertThat(context.getBean(OpenAiCompatibleChatClient.class)).isNotNull();
        context.close();
    }

    @Test
    void acceptsFullChatCompletionsEndpointWithoutAppendingPathTwice() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AppProperties properties = new AppProperties();
        properties.getAi().setEnabled(true);
        properties.getAi().setEndpoint("http://127.0.0.1:8317/v1/chat/completions");
        properties.getAi().setApiKey("local-key");
        properties.getAi().setModel("gpt-5.4");
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(
                properties, restTemplate, new ObjectMapper());

        server.expect(requestTo("http://127.0.0.1:8317/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}",
                        MediaType.APPLICATION_JSON));

        Optional<String> result = client.complete("system", "user");

        assertThat(result).contains("ok");
        server.verify();
    }
}
