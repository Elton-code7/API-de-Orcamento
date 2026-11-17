package br.com.orcamento.service;
import br.com.orcamento.api.*;
import br.com.orcamento.domain.*;
import br.com.orcamento.repository.TransactionRepository;
import jakarta.validation.Validator;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
@Service
@Transactional(readOnly=true)
public class TransactionService {
    private final TransactionRepository repository;
    private final Validator validator;
    public TransactionService(TransactionRepository repository, Validator validator) { this.repository=repository; this.validator=validator; }
    public TransactionInput validate(TransactionInput input) {
        if (input==null || !validator.validate(input).isEmpty())
            throw new ApiException(HttpStatus.BAD_REQUEST,"Transação inválida: informe tipo, valor positivo com até 2 casas decimais, descrição, categoria e data.");
        return input;
    }
    public Page<FinancialTransaction> list(LocalDate start, LocalDate end, int page, int size) {
        range(start,end);
        if(page<0 || size<1 || size>100) throw new ApiException(HttpStatus.BAD_REQUEST,"Use page >= 0 e size entre 1 e 100.");
        return repository.findByDateBetween(start,end,PageRequest.of(page,size,Sort.by(Sort.Order.desc("date"),Sort.Order.desc("createdAt"),Sort.Order.asc("id"))));
    }
    public Summary summary(LocalDate start, LocalDate end) {
        range(start,end);
        BigDecimal income=repository.total(TransactionType.RECEITA,start,end), expense=repository.total(TransactionType.DESPESA,start,end);
        return new Summary(start,end,income,expense,income.subtract(expense));
    }
    private void range(LocalDate start,LocalDate end) {
        if(start==null || end==null || start.isAfter(end)) throw new ApiException(HttpStatus.BAD_REQUEST,"Período inválido: início deve ser anterior ou igual ao fim.");
    }
    public record Summary(LocalDate start, LocalDate end, BigDecimal income, BigDecimal expense, BigDecimal balance) {}
}
