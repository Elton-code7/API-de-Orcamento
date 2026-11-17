package br.com.orcamento.repository;
import br.com.orcamento.domain.CommandRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface CommandRepository extends JpaRepository<CommandRecord, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CommandRecord c where c.id=:id")
    Optional<CommandRecord> findLockedById(UUID id);
}
