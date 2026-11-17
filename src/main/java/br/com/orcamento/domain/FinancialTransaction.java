package br.com.orcamento.domain;
import br.com.orcamento.api.TransactionInput;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity
@Table(name="financial_transactions")
public class FinancialTransaction {
    @Id public UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public TransactionType type;
    @Column(nullable=false, precision=12, scale=2) public BigDecimal amount;
    @Column(nullable=false, length=160) public String description;
    @Column(nullable=false, length=60) public String category;
    @Column(name="transaction_date", nullable=false) public LocalDate date;
    @Column(nullable=false) public Instant createdAt;
    @Column(unique=true) public UUID commandId;
    protected FinancialTransaction() {}
    public FinancialTransaction(TransactionInput input, UUID commandId) {
        id=UUID.randomUUID(); type=input.type(); amount=input.amount(); description=input.description().trim();
        category=input.category().trim(); date=input.date(); createdAt=Instant.now(); this.commandId=commandId;
    }
}
