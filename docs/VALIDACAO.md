# Validação do projeto

Validação realizada em 17/11/2026.

- Build Maven `verify`: **BUILD SUCCESS**.
- JDK utilizado na compilação: OpenJDK 21.0.13.0.3 (Ubuntu).
- Maven: 3.8.7.
- Spring Boot: 3.5.7; Spring AI: 1.1.0.
- Testes: **11 executados, 0 falhas, 0 erros e 0 ignorados**.
- Sintaxe dos scripts Bash: verificada com `bash -n`.
- Pacote JAR executável gerado pelo build (reproduzível com `mvn package`).

## Cenários executados

| Cenário | Resultado |
|---|---|
| Texto → ChatClient → Tool Calling → proposta → confirmação → auditoria | Passou |
| Repetir confirmação retorna a mesma transação | Passou |
| Quatro confirmações concorrentes criam um único lançamento | Passou |
| Cancelamento impede confirmação posterior | Passou |
| Multipart → transcrição simulada → proposta → síntese MP3 simulada | Passou |
| Erro HTTP 503 do provedor não cria lançamento e gera auditoria | Passou |
| Ferramenta de resumo consulta o banco sem criar transações | Passou |
| Cálculo decimal: 0,30 − 0,10 = 0,20 | Passou |
| Valores zero, negativos, com excesso de casas ou acima do limite | Rejeitados |
| Endpoint financeiro sem chave e health público | Comportamento esperado |
| Texto vazio, período inválido, paginação inválida e ID inexistente | Rejeitados |
| Áudio vazio, extensão não aceita e arquivo acima de 10 MB | Rejeitados |

Alguns cenários pertencem ao mesmo método de teste; por isso, há mais linhas que métodos.

## Limites da validação

Os testes usam um servidor HTTP local para simular respostas do provedor. O ChatClient, a execução das ferramentas, a camada REST, o H2 e o Flyway são reais. Os testes não avaliaram a interpretação de um modelo remoto nem a qualidade de transcrição/voz com arquivos reais. O MP3 de teste é um marcador binário, não uma gravação reproduzível.

Não foram usadas credenciais reais nem créditos do provedor. A imagem Docker e o Compose foram fornecidos, mas não executados neste ambiente. A persistência foi exercitada com H2 em memória nos testes; o perfil normal usa H2 em arquivo.

Para reproduzir: `mvn clean verify`. O arquivo de configuração Mockito usa o mock maker por subclasses para dispensar anexação dinâmica de agentes; estes testes não precisam de mocks inline.

O repositório contém os fontes, testes, migrações, scripts, exemplos e documentação. Não contém `.env` com chaves reais, banco de dados, cache Maven nem a pasta `target`. As dependências e o JAR são gerados localmente pelo Maven.
