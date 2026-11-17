package br.com.orcamento.api;
import br.com.orcamento.domain.TransactionType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public record TransactionInput(
    @NotNull TransactionType type,
    @NotNull @DecimalMin("0.01") @Digits(integer=10, fraction=2) BigDecimal amount,
    @NotBlank @Size(max=160) String description,
    @NotBlank @Size(max=60) String category,
    @NotNull LocalDate date) {}
