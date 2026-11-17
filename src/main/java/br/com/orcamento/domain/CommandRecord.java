package br.com.orcamento.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="commands")
public class CommandRecord {
    @Id public UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public CommandStatus status;
    @Column(nullable=false, length=10) public String source;
    @Column(length=4000) public String transcript;
    @Column(length=6000) public String answer;
    @Column(length=2000) public String proposalJson;
    public UUID transactionId;
    @Column(nullable=false) public Instant createdAt;
    protected CommandRecord() {}
    public CommandRecord(String source) { id=UUID.randomUUID(); this.source=source; status=CommandStatus.PROCESSANDO; createdAt=Instant.now(); }
}
