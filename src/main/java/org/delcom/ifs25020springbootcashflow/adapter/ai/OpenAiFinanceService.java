package org.delcom.ifs25020springbootcashflow.adapter.ai;

import org.delcom.ifs25020springbootcashflow.domain.entity.AiGeneratedTransaction;
import org.delcom.ifs25020springbootcashflow.domain.entity.TransactionType;
import org.delcom.ifs25020springbootcashflow.domain.exception.DomainException;
import org.delcom.ifs25020springbootcashflow.domain.repository.IFinanceAiService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Adapter AI: HTTP ke API OpenAI-compatible (/chat/completions), balasan di-parse jadi transaksi. */
public class OpenAiFinanceService implements IFinanceAiService {

    static final String SYSTEM_PROMPT = """
            Kamu adalah asisten pencatat keuangan pribadi. Ubah catatan pengguna menjadi daftar transaksi.
            Balas HANYA dengan JSON array, tanpa teks lain dan tanpa markdown. Setiap elemen berbentuk:
            {"description": "keterangan singkat", "amount": 25000, "type": "INCOME" atau "EXPENSE"}
            Aturan: amount adalah bilangan bulat rupiah lebih dari 0 tanpa titik atau koma;
            INCOME untuk pemasukan (gaji, hadiah, dan sejenisnya),
            EXPENSE untuk pengeluaran (belanja, makan, transport, dan sejenisnya).
            """;

    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public OpenAiFinanceService(String endpoint, String apiKey, String model) {
        this(endpoint, apiKey, model,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    OpenAiFinanceService(String endpoint, String apiKey, String model, HttpClient http) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.http = http;
    }

    @Override
    public List<AiGeneratedTransaction> generateTransactions(String prompt) throws DomainException {
        if (isBlank(endpoint) || isBlank(apiKey) || isBlank(model)) {
            throw new DomainException("Konfigurasi AI belum lengkap (OPENAI_ENDPOINT, OPENAI_KEY, OPENAI_MODEL)");
        }
        HttpResponse<String> response = send(prompt);
        if (response.statusCode() / 100 != 2) {
            throw new DomainException("Layanan AI mengembalikan status " + response.statusCode());
        }
        return parse(response.body());
    }

    private HttpResponse<String> send(String prompt) throws DomainException {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", 0.2,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", prompt))));
            HttpRequest request = HttpRequest.newBuilder(URI.create(chatUrl()))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | JacksonException | IllegalArgumentException e) {
            throw new DomainException("Gagal menghubungi layanan AI: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomainException("Permintaan ke layanan AI dibatalkan");
        }
    }

    private String chatUrl() {
        return endpoint.trim().replaceAll("/+$", "") + "/chat/completions";
    }

    private List<AiGeneratedTransaction> parse(String responseBody) throws DomainException {
        try {
            String content = mapper.readTree(responseBody)
                    .path("choices").path(0).path("message").path("content").asString("");
            int start = content.indexOf('[');
            int end = content.lastIndexOf(']');
            if (start < 0 || end < start) {
                throw new DomainException("Balasan AI tidak berisi daftar transaksi");
            }
            JsonNode array = mapper.readTree(content.substring(start, end + 1));
            List<AiGeneratedTransaction> result = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                JsonNode item = array.path(i);
                TransactionType type = parseType(item.path("type").asString(""));
                String description = item.path("description").asString("").trim();
                long amount = item.path("amount").asLong(0);
                if (type != null && !description.isEmpty() && amount > 0) {
                    result.add(new AiGeneratedTransaction(description, amount, type));
                }
            }
            return result;
        } catch (JacksonException e) {
            throw new DomainException("Balasan AI tidak dapat dibaca");
        }
    }

    private static TransactionType parseType(String text) {
        return switch (text.trim().toUpperCase(Locale.ROOT)) {
            case "INCOME", "PEMASUKAN" -> TransactionType.INCOME;
            case "EXPENSE", "PENGELUARAN" -> TransactionType.EXPENSE;
            default -> null;
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}