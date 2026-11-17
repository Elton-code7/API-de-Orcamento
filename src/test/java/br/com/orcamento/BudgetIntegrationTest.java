package br.com.orcamento;

import br.com.orcamento.api.*;
import br.com.orcamento.domain.*;
import br.com.orcamento.repository.*;
import br.com.orcamento.service.*;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockMultipartFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP simulado apenas na fronteira externa: ChatClient, tools, REST, JPA e Flyway são reais. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetIntegrationTest {
    static final String KEY="test-key-with-more-than-24-characters";
    static final ObjectMapper JSON=new ObjectMapper();
    static HttpServer provider;
    static volatile boolean failure;
    static final AtomicInteger chatRequests=new AtomicInteger();
    static {
        try {
            provider=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            provider.createContext("/v1/chat/completions", exchange->{
                chatRequests.incrementAndGet();
                JsonNode request=JSON.readTree(exchange.getRequestBody());
                String body;
                int status=200;
                if(failure) {status=503;body="{\"error\":{\"message\":\"simulated outage\",\"type\":\"server_error\"}}";}
                else {
                    boolean toolResult=false;
                    for(JsonNode m:request.path("messages")) if(m.path("role").asText().equals("tool")) toolResult=true;
                    Map<String,Object> message;
                    if(toolResult) message=Map.of("role","assistant","content","Resultado consultado. Confira os dados da proposta.");
                    else {
                        String content=request.path("messages").toString();
                        String name=content.contains("CONSULTA_TESTE")?"summarize":"proposeTransaction";
                        String args=name.equals("summarize") ? "{\"start\":\"2026-11-01\",\"end\":\"2026-11-30\"}" : "{\"input\":{\"type\":\"DESPESA\",\"amount\":35.50,\"description\":\"Almoço\",\"category\":\"Alimentação\",\"date\":\"2026-11-17\"}}";
                        message=Map.of("role","assistant","content","","tool_calls",List.of(Map.of("id","call_test","type","function","function",Map.of("name",name,"arguments",args))));
                    }
                    body=JSON.writeValueAsString(Map.of("id","chatcmpl-test","object","chat.completion","created",1,"model","gpt-4o-mini","choices",List.of(Map.of("index",0,"message",message,"finish_reason",toolResult?"stop":"tool_calls")),"usage",Map.of("prompt_tokens",10,"completion_tokens",10,"total_tokens",20)));
                }
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });
            provider.createContext("/v1/audio/transcriptions",exchange->{
                exchange.getRequestBody().readAllBytes();
                byte[] bytes="Registre uma despesa de 35,50 reais com almoço, categoria alimentação, hoje.".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","text/plain");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });
            provider.createContext("/v1/audio/speech",exchange->{
                exchange.getRequestBody().readAllBytes();byte[] bytes="ID3-test-audio".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","audio/mpeg");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });
            provider.start();
        } catch(Exception e) {throw new ExceptionInInitializerError(e);}
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {r.add("spring.ai.openai.base-url",()->"http://127.0.0.1:"+provider.getAddress().getPort());}
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CommandStore store;
    @Autowired TransactionService service;
    @Autowired TransactionRepository transactions;
    @Autowired CommandRepository commands;
    @Autowired AuditRepository audits;
    @BeforeEach void reset() {failure=false;chatRequests.set(0);}
    @AfterAll static void stop() {provider.stop(0);}
    UUID propose() throws Exception {
        String body=mvc.perform(post("/api/commands/text").header("X-API-Key",KEY).contentType("application/json").content("{\"text\":\"Registre almoço 35,50, despesa, alimentação, hoje.\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AGUARDANDO_CONFIRMACAO"))
            .andExpect(jsonPath("$.proposal.amount").value(35.5)).andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(body).get("id").asText());
    }
    @Test void completeTextToolConfirmationAndAudit() throws Exception {
        long before=transactions.count();UUID id=propose();
        assertThat(chatRequests.get()).isEqualTo(2);assertThat(transactions.count()).isEqualTo(before);
        String result=mvc.perform(post("/api/commands/"+id+"/confirm").header("X-API-Key",KEY)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMADO")).andReturn().getResponse().getContentAsString();
        String transactionId=mapper.readTree(result).get("transactionId").asText();
        mvc.perform(post("/api/commands/"+id+"/confirm").header("X-API-Key",KEY)).andExpect(status().isOk()).andExpect(jsonPath("$.transactionId").value(transactionId));
        assertThat(transactions.count()).isEqualTo(before+1);
        mvc.perform(get("/api/commands/"+id+"/audit").header("X-API-Key",KEY)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
    }
    @Test void simultaneousConfirmationsCreateOnlyOneTransaction() throws Exception {
        UUID id=propose();long before=transactions.count();
        try(var executor=Executors.newFixedThreadPool(4)) {
            var ready=new CountDownLatch(4);var start=new CountDownLatch(1);
            List<Future<UUID>> futures=new ArrayList<>();
            for(int i=0;i<4;i++) futures.add(executor.submit(()->{ready.countDown();start.await();return store.confirm(id).transactionId;}));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            Set<UUID> ids=new HashSet<>();for(var f:futures)ids.add(f.get(10,TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
        }
        assertThat(transactions.count()).isEqualTo(before+1);
        assertThat(store.audit(id).stream().filter(e->e.action.equals("TRANSACAO_CRIADA"))).hasSize(1);
    }
    @Test void cancelledProposalCannotBeConfirmed() throws Exception {
        UUID id=propose();long before=transactions.count();
        mvc.perform(post("/api/commands/"+id+"/cancel").header("X-API-Key",KEY)).andExpect(status().isOk());
        mvc.perform(post("/api/commands/"+id+"/confirm").header("X-API-Key",KEY)).andExpect(status().isConflict());
        assertThat(transactions.count()).isEqualTo(before);
    }
    @Test void audioUsesTranscriptionAndSpeechEndpoints() throws Exception {
        var file=new MockMultipartFile("file","teste.wav","audio/wav",new byte[]{82,73,70,70,0,0,0,0,87,65,86,69});
        String response=mvc.perform(multipart("/api/commands/audio").file(file).header("X-API-Key",KEY)).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("AUDIO")).andExpect(jsonPath("$.transcript").isNotEmpty()).andReturn().getResponse().getContentAsString();
        String id=mapper.readTree(response).get("id").asText();
        mvc.perform(get("/api/commands/"+id+"/speech").header("X-API-Key",KEY)).andExpect(status().isOk()).andExpect(content().contentType("audio/mpeg")).andExpect(content().bytes("ID3-test-audio".getBytes(StandardCharsets.UTF_8)));
    }
    @Test void providerFailureIsAuditedWithoutFinancialWrite() throws Exception {
        failure=true;long before=transactions.count();long auditBefore=audits.count();
        mvc.perform(post("/api/commands/text").header("X-API-Key",KEY).contentType("application/json").content("{\"text\":\"Teste de falha\"}")).andExpect(status().isBadGateway());
        assertThat(transactions.count()).isEqualTo(before);assertThat(audits.count()).isEqualTo(auditBefore+2);
        assertThat(commands.findAll()).anyMatch(c->c.status==CommandStatus.FALHOU);
    }
    @Test void summaryToolReadsDatabase() throws Exception {
        long before=transactions.count();
        mvc.perform(post("/api/commands/text").header("X-API-Key",KEY).contentType("application/json").content("{\"text\":\"CONSULTA_TESTE resumo de novembro de 2026\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONCLUIDO")).andExpect(jsonPath("$.proposal").doesNotExist());
        assertThat(chatRequests.get()).isEqualTo(2);assertThat(transactions.count()).isEqualTo(before);
    }
    @Test void summaryUsesExactDecimalArithmetic() {
        var date=LocalDate.of(2099,1,1);
        for(var entry:List.of(new TransactionInput(TransactionType.RECEITA,new BigDecimal("0.30"),"Receita","Teste",date),new TransactionInput(TransactionType.DESPESA,new BigDecimal("0.10"),"Despesa","Teste",date))) {
            UUID id=store.start("TEXTO");store.finish(id,"teste","teste",entry);store.confirm(id);
        }
        assertThat(service.summary(date,date).balance()).isEqualByComparingTo("0.20");
    }
    @Test void invalidAmountsNeverBecomeProposals() {
        for(String value:List.of("0","-1","12.345","10000000000")) {
            var input=new TransactionInput(TransactionType.DESPESA,new BigDecimal(value),"Teste","Teste",LocalDate.now());
            assertThatThrownBy(()->service.validate(input)).isInstanceOf(ApiException.class);
        }
    }
    @Test void requestsRequireApiKey() throws Exception {
        mvc.perform(get("/api/transactions?start=2026-01-01&end=2026-12-31")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }
    @Test void validatesTextAndQueryParameters() throws Exception {
        mvc.perform(post("/api/commands/text").header("X-API-Key",KEY).contentType("application/json").content("{\"text\":\"  \"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/transactions?start=2026-12-31&end=2026-01-01").header("X-API-Key",KEY)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/transactions?start=2026-01-01&end=2026-12-31&size=101").header("X-API-Key",KEY)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/commands/"+UUID.randomUUID()).header("X-API-Key",KEY)).andExpect(status().isNotFound());
    }
    @Test void rejectsEmptyUnsupportedAndLargeAudio() throws Exception {
        for(var file:List.of(new MockMultipartFile("file","a.wav","audio/wav",new byte[0]),new MockMultipartFile("file","a.exe","application/octet-stream",new byte[]{1}),new MockMultipartFile("file","a.wav","audio/wav",new byte[10*1024*1024+1]))) {
            mvc.perform(multipart("/api/commands/audio").file(file).header("X-API-Key",KEY)).andExpect(status().is4xxClientError());
        }
        assertThat(chatRequests.get()).isZero();
    }
}
