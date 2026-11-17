package br.com.orcamento.service;
import br.com.orcamento.api.*;
import br.com.orcamento.domain.*;
import br.com.orcamento.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
@Transactional
public class CommandStore {
    private final CommandRepository commands;
    private final TransactionRepository transactions;
    private final AuditRepository audit;
    private final TransactionService service;
    private final ObjectMapper mapper;
    public CommandStore(CommandRepository commands, TransactionRepository transactions, AuditRepository audit, TransactionService service, ObjectMapper mapper) {
        this.commands=commands;this.transactions=transactions;this.audit=audit;this.service=service;this.mapper=mapper;
    }
    public UUID start(String source) {
        var c=commands.save(new CommandRecord(source)); event(c.id,"RECEBIDO"); return c.id;
    }
    public CommandRecord finish(UUID id, String transcript, String answer, TransactionInput proposal) {
        var c=locked(id); c.transcript=transcript;
        if(proposal!=null) {
            service.validate(proposal); c.proposalJson=encode(proposal); c.status=CommandStatus.AGUARDANDO_CONFIRMACAO;
            c.answer="Proposta preparada. Confira os dados e confirme para registrar. Nenhuma transação foi gravada ainda.";
        } else { c.status=CommandStatus.CONCLUIDO; c.answer=answer; }
        event(id,c.status.name()); return c;
    }
    public void fail(UUID id) { var c=locked(id); c.status=CommandStatus.FALHOU; c.answer="Não foi possível processar o comando. Nenhuma transação foi criada."; event(id,"FALHOU"); }
    @Transactional(readOnly=true)
    public CommandRecord get(UUID id) { return commands.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Comando não encontrado.")); }
    public CommandRecord confirm(UUID id) {
        var c=locked(id);
        if(c.status==CommandStatus.CONFIRMADO) return c; // Repetir o mesmo ID devolve a transação existente.
        if(c.status!=CommandStatus.AGUARDANDO_CONFIRMACAO) throw new ApiException(HttpStatus.CONFLICT,"Este comando não aguarda confirmação.");
        var proposal=service.validate(decode(c.proposalJson));
        var transaction=transactions.save(new FinancialTransaction(proposal,id));
        c.transactionId=transaction.id; c.status=CommandStatus.CONFIRMADO; c.answer="Transação registrada com sucesso.";
        event(id,"TRANSACAO_CRIADA"); return c;
    }
    public CommandRecord cancel(UUID id) {
        var c=locked(id);
        if(c.status==CommandStatus.CANCELADO) return c;
        if(c.status!=CommandStatus.AGUARDANDO_CONFIRMACAO) throw new ApiException(HttpStatus.CONFLICT,"Somente propostas pendentes podem ser canceladas.");
        c.status=CommandStatus.CANCELADO; c.answer="Proposta cancelada. Nenhuma transação foi criada.";event(id,"CANCELADO");return c;
    }
    @Transactional(readOnly=true)
    public List<AuditEvent> audit(UUID id) { get(id);return audit.findByCommandIdOrderByOccurredAtAsc(id); }
    public TransactionInput decode(String json) {
        if(json==null) return null;
        try { return mapper.readValue(json,TransactionInput.class); } catch(JsonProcessingException e) { throw new IllegalStateException("Proposta inválida no banco.",e); }
    }
    private String encode(TransactionInput input) { try{return mapper.writeValueAsString(input);}catch(JsonProcessingException e){throw new IllegalStateException(e);} }
    private CommandRecord locked(UUID id) { return commands.findLockedById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Comando não encontrado.")); }
    private void event(UUID id,String action) {audit.save(new AuditEvent(id,action));}
}
