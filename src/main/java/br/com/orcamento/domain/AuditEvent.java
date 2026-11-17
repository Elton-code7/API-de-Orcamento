package br.com.orcamento.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="audit_events")
public class AuditEvent {
    @Id public UUID id;
    @Column(nullable=false) public UUID commandId;
    @Column(nullable=false, length=50) public String action;
    @Column(nullable=false) public Instant occurredAt;
    protected AuditEvent() {}
    public AuditEvent(UUID commandId, String action) { this.id=UUID.randomUUID(); this.commandId=commandId; this.action=action; this.occurredAt=Instant.now(); }
}
