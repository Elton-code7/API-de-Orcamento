package br.com.orcamento.repository;
import br.com.orcamento.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AuditRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByCommandIdOrderByOccurredAtAsc(UUID commandId);
}
