package org.delcom.ifs25020springbootcashflow.adapter.ai;

import org.delcom.ifs25020springbootcashflow.domain.entity.AiGeneratedTransaction;
import org.delcom.ifs25020springbootcashflow.domain.entity.TransactionType;
import org.delcom.ifs25020springbootcashflow.domain.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiFinanceServiceTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @SuppressWarnings("unchecked")
    private static HttpClient clientReturning(int status, String body) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(ArgumentMatchers.any(HttpRequest.class),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        return http;
    }

    private static String wrap(String content) {
        return MAPPER.writeValueAsString(
                Map.of("choices", List.of(Map.of("message", Map.of("content", content)))));
    }

    private static List<AiGeneratedTransaction> generate(String content) throws Exception {
        OpenAiFinanceService service = new OpenAiFinanceService(
                "http://ai.test/v1", "key", "model", clientReturning(200, wrap(content)));
        return service.generateTransactions("catatan");
    }

    @Test
    void publicConstructorBuildsRealClientAndRejectsEmptyConfig() {
        OpenAiFinanceService service = new OpenAiFinanceService("", "", "");
        assertThrows(DomainException.class, () -> service.generateTransactions("x"));
    }

    @Test
    void incompleteConfigurationIsRejected() {
        assertThrows(DomainException.class, () -> new OpenAiFinanceService(null, "k", "m").generateTransactions("x"));
        assertThrows(DomainException.class, () -> new OpenAiFinanceService("  ", "k", "m").generateTransactions("x"));
        assertThrows(DomainException.class, () -> new OpenAiFinanceService("e", null, "m").generateTransactions("x"));
        assertThrows(DomainException.class, () -> new OpenAiFinanceService("e", " ", "m").generateTransactions("x"));
        assertThrows(DomainException.class, () -> new OpenAiFinanceService("e", "k", null).generateTransactions("x"));
        assertThrows(DomainException.class, () -> new OpenAiFinanceService("e", "k", " ").generateTransactions("x"));
    }

    @Test
    void parsesValidItemsAndSkipsInvalidOnes() throws Exception {
        String content = "Berikut hasilnya: [" +
                "{\"description\":\" Gaji \",\"amount\":5000000,\"type\":\"income\"}," +
                "{\"description\":\"Kopi\",\"amount\":20000,\"type\":\"PENGELUARAN\"}," +
                "{\"description\":\"Hadiah\",\"amount\":1000,\"type\":\"PEMASUKAN\"}," +
                "{\"description\":\"Makan\",\"amount\":2000,\"type\":\"expense\"}," +
                "{\"description\":\"x\",\"amount\":5,\"type\":\"UNKNOWN\"}," +
                "{\"description\":\"  \",\"amount\":5,\"type\":\"INCOME\"}," +
                "{\"description\":\"nol\",\"amount\":0,\"type\":\"INCOME\"}," +
                "{\"amount\":5,\"type\":\"INCOME\"}," +
                "{\"description\":\"notype\",\"amount\":5}] selesai";
        List<AiGeneratedTransaction> result = generate(content);
        assertEquals(4, result.size());
        assertEquals("Gaji", result.get(0).description());
        assertEquals(5_000_000L, result.get(0).amount());
        assertEquals(TransactionType.INCOME, result.get(0).type());
        assertEquals(TransactionType.EXPENSE, result.get(1).type());
        assertEquals(TransactionType.INCOME, result.get(2).type());
        assertEquals(TransactionType.EXPENSE, result.get(3).type());
    }

    @Test
    void emptyArrayGivesEmptyList() throws Exception {
        assertTrue(generate("[]").isEmpty());
    }

    @Test
    void sendsRequestToChatCompletionsEndpoint() throws Exception {
        HttpClient http = clientReturning(200, wrap("[]"));
        new OpenAiFinanceService("http://ai.test/v1//", "secret", "model-x", http).generateTransactions("beli kopi");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        HttpRequest request = captor.getValue();
        assertEquals("http://ai.test/v1/chat/completions", request.uri().toString());
        assertEquals("Bearer secret", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("POST", request.method());
    }

    @Test
    void replyWithoutListIsRejected() {
        DomainException e1 = assertThrows(DomainException.class, () -> generate("tidak ada daftar"));
        assertTrue(e1.getMessage().contains("tidak berisi"));
        assertThrows(DomainException.class, () -> generate("] ["));
    }

    @Test
    void missingChoicesIsRejected() throws Exception {
        OpenAiFinanceService service = new OpenAiFinanceService("e", "k", "m", clientReturning(200, "{}"));
        assertThrows(DomainException.class, () -> service.generateTransactions("x"));
    }

    @Test
    void unreadableJsonIsRejected() throws Exception {
        DomainException inner = assertThrows(DomainException.class, () -> generate("[abc]"));
        assertTrue(inner.getMessage().contains("dibaca"));
        OpenAiFinanceService outer = new OpenAiFinanceService("e", "k", "m", clientReturning(200, "bukan json"));
        assertThrows(DomainException.class, () -> outer.generateTransactions("x"));
    }

    @Test
    void non2xxStatusIsRejected() throws Exception {
        OpenAiFinanceService service = new OpenAiFinanceService("e", "k", "m", clientReturning(500, "error"));
        DomainException e = assertThrows(DomainException.class, () -> service.generateTransactions("x"));
        assertTrue(e.getMessage().contains("500"));
    }

    @Test
    void ioErrorIsWrapped() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(ArgumentMatchers.any(HttpRequest.class),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenThrow(new IOException("boom"));
        OpenAiFinanceService service = new OpenAiFinanceService("http://ai.test", "k", "m", http);
        DomainException e = assertThrows(DomainException.class, () -> service.generateTransactions("x"));
        assertTrue(e.getMessage().contains("boom"));
    }

    @Test
    void invalidEndpointIsWrapped() {
        HttpClient http = mock(HttpClient.class);
        OpenAiFinanceService service = new OpenAiFinanceService("http://bad host", "k", "m", http);
        assertThrows(DomainException.class, () -> service.generateTransactions("x"));
    }

    @Test
    void interruptionIsReportedAndFlagRestored() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(ArgumentMatchers.any(HttpRequest.class),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenThrow(new InterruptedException());
        OpenAiFinanceService service = new OpenAiFinanceService("http://ai.test", "k", "m", http);
        try {
            assertThrows(DomainException.class, () -> service.generateTransactions("x"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void inMemoryServiceReturnsFixedDataAndRemembersPrompt() throws Exception {
        InMemoryFinanceAiService defaults = new InMemoryFinanceAiService();
        assertNull(defaults.getLastPrompt());
        assertEquals(2, defaults.generateTransactions("apa saja").size());
        assertEquals("apa saja", defaults.getLastPrompt());
        InMemoryFinanceAiService custom = new InMemoryFinanceAiService(
                List.of(new AiGeneratedTransaction("Tes", 1, TransactionType.INCOME)));
        assertEquals(1, custom.generateTransactions("x").size());
    }
}