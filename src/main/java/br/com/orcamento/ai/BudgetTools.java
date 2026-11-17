package br.com.orcamento.ai;
import br.com.orcamento.api.TransactionInput;
import br.com.orcamento.service.TransactionService;
import org.springframework.ai.tool.annotation.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
/** Uma instância por comando; nunca compartilha propostas entre requisições. */
public class BudgetTools {
    private final TransactionService service;
    private TransactionInput proposal;
    private int calls;
    public BudgetTools(TransactionService service) { this.service=service; }
    @Tool(description="Prepara UMA receita ou despesa em BRL para confirmação humana. Não grava no banco. Use apenas dados informados; nunca invente valor, descrição ou categoria.")
    public Map<String,Object> proposeTransaction(@ToolParam(description="Dados completos da transação; data ISO yyyy-MM-dd; valor decimal positivo") TransactionInput input) {
        limit(); service.validate(input);
        if(proposal!=null && !proposal.equals(input)) throw new IllegalArgumentException("Envie somente uma transação por comando.");
        proposal=input;
        return Map.of("status","AGUARDANDO_CONFIRMACAO","proposal",proposal,"persisted",false);
    }
    @Tool(description="Consulta transações reais de um período inclusivo. Datas ISO yyyy-MM-dd. Página começa em zero; tamanho entre 1 e 100. Informe totalElements e avise se houver mais páginas.")
    public Map<String,Object> listTransactions(String start, String end, int page, int size) {
        limit(); var result=service.list(LocalDate.parse(start),LocalDate.parse(end),page,size);
        var response=new LinkedHashMap<String,Object>(); response.put("items",result.getContent()); response.put("totalElements",result.getTotalElements()); response.put("hasNext",result.hasNext());return response;
    }
    @Tool(description="Calcula receitas, despesas e saldo reais em BRL no período inclusivo informado, sem limite de paginação. Datas ISO yyyy-MM-dd.")
    public TransactionService.Summary summarize(String start,String end) {limit();return service.summary(LocalDate.parse(start),LocalDate.parse(end));}
    public TransactionInput proposal() {return proposal;}
    private void limit() {if(++calls>8) throw new IllegalStateException("Limite de ferramentas por comando atingido.");}
}
