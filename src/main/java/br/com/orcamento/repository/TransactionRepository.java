package br.com.orcamento.repository;
import br.com.orcamento.domain.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.time.LocalDate;
import java.util.UUID;
public interface TransactionRepository extends JpaRepository<FinancialTransaction, UUID> {
    Page<FinancialTransaction> findByDateBetween(LocalDate start, LocalDate end, Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(t.amount),0) from FinancialTransaction t where t.type=:type and t.date between :start and :end")
    java.math.BigDecimal total(br.com.orcamento.domain.TransactionType type, LocalDate start, LocalDate end);
}
