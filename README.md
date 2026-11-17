# Orçamento por Voz — Spring Boot + Spring AI

API de estudo que recebe um comando em texto ou um arquivo de áudio, interpreta a intenção com IA, executa ferramentas Java e consulta ou registra receitas e despesas. O dinheiro é representado em **reais (BRL)**. Não há conexão bancária, pagamento nem transferência real: são registros de orçamento pessoal.

Este é um projeto completo e independente, criado a partir do enunciado. Nenhum repositório-base ou código das aulas foi fornecido. A aplicação é uma API; use curl, Postman ou outro cliente HTTP. Não inclui interface web.

## O que o projeto faz

1. Recebe áudio em `multipart/form-data`.
2. Transcreve o áudio por meio do Spring AI e do modelo de transcrição.
3. Envia o texto ao `ChatClient`, junto com instruções e ferramentas disponíveis.
4. O modelo solicita uma ferramenta; o Spring AI executa o método Java correspondente.
5. A ferramenta consulta o banco ou prepara uma proposta de transação validada.
6. A API devolve o texto, o ID do comando e, quando houver, os dados para confirmação.
7. Ao confirmar, a API grava a transação e a auditoria na mesma transação de banco.
8. Um endpoint separado converte a resposta em MP3 com voz gerada por IA.

Exemplo de comando completo:

> Registre uma despesa de 35,50 reais com almoço, categoria alimentação, hoje.

Também é possível consultar: “Qual foi meu saldo em novembro de 2026?” ou “Liste minhas transações de novembro de 2026”. Dados ausentes devem ser informados em um novo comando completo. Cada requisição é independente; não há memória de conversa.

## Melhoria implementada

**Confirmação explícita, proteção contra confirmação duplicada e auditoria.** Reconhecimento de voz e interpretação por IA podem errar. Por isso, o modelo só prepara a proposta. Confira `type`, `amount`, `description`, `category` e `date` antes de chamar `/confirm`.

A confirmação usa bloqueio pessimista da linha do comando, uma restrição única no `command_id` da transação e uma transação de banco. Repetir a confirmação do **mesmo ID**, inclusive em paralelo, devolve o mesmo `transactionId`. O evento `TRANSACAO_CRIADA` só é gravado uma vez. A confirmação é uma função real da aplicação, deliberadamente não exposta como ferramenta à IA.

**Limite da idempotência:** reenviar o mesmo texto/áudio cria outro comando e outra proposta. Não confirme ambos. Se a resposta da confirmação se perder, consulte ou confirme novamente o ID original. Uma falha na geração de voz não desfaz a transação; repita apenas `/speech`.

Outras melhorias: valores com `BigDecimal`, validação antes da persistência, paginação, resumo calculado no banco, chave de acesso, Flyway, timeout de conexão/leitura e limite de chamadas de ferramenta por comando.

## Tecnologias

| Tecnologia | Papel |
|---|---|
| Java 21 | Linguagem e runtime |
| Spring Boot 3.5.7 | Aplicação e configuração |
| Spring AI 1.1.0 | `ChatClient`, ferramentas `@Tool`, transcrição e fala |
| OpenAI | Provedor configurável de chat, transcrição e voz |
| Spring Web + Bean Validation | REST, multipart e validação |
| Spring Security | Autenticação stateless por `X-API-Key` |
| Spring Data JPA + H2 | Persistência local em arquivo |
| Flyway | Migração versionada do banco |
| Maven + JUnit 5 + MockMvc | Build e testes de integração |
| Docker Compose | Execução opcional com volume persistente |

As versões foram fixadas para reprodutibilidade; não representam uma promessa de serem as mais recentes. A linha Spring AI 1.1 é compatível com Spring Boot 3.5. Antes de publicar um serviço, revise atualizações e dependências.

## Como executar no Linux Mint

### 1. Instale os requisitos

```bash
sudo apt update
sudo apt install openjdk-21-jdk maven curl python3
java -version
mvn -version
```

Use Java 21 e Maven 3.6.3 ou superior. Se `mvn -version` mostrar outra versão de Java, ajuste `JAVA_HOME`/`update-alternatives`. Extraia o ZIP e entre na pasta `orcamento-voz-api`.

### 2. Configure as chaves

```bash
cp .env.example .env
nano .env
```

Preencha `OPENAI_API_KEY` com uma chave válida do provedor. Gere `APP_API_KEY` para proteger esta API:

```bash
python3 -c 'import secrets; print(secrets.token_hex(32))'
```

Cole o resultado em `APP_API_KEY` no `.env`. Use ao menos 24 caracteres. `.env` não deve entrar no Git. Nunca coloque a chave do provedor no cliente. A execução real precisa de internet, acesso aos modelos e saldo/créditos na API do provedor; assinatura de chat não é configuração de faturamento da API.

### 3. Execute

```bash
bash scripts/run.sh
```

A aplicação fica em `http://localhost:8080`. Por padrão, escuta somente em `127.0.0.1`. O script carrega `.env`; executar `mvn spring-boot:run` diretamente **não** carrega esse arquivo automaticamente.

Alternativa, exportando as variáveis manualmente:

```bash
set -a
source .env
set +a
mvn spring-boot:run
```

Verifique:

```bash
curl http://localhost:8080/api/health
# {"status":"UP"}
```

O health verifica que o servidor está respondendo, não a disponibilidade dos modelos. O banco é criado em `./data/orcamento.mv.db` e permanece após reiniciar. Não execute duas instâncias sobre o mesmo arquivo H2. Faça cópias do banco com a aplicação parada.

### Alternativa com Docker

Com Docker Engine e Compose instalados e `.env` configurado:

```bash
docker compose up --build
# Parar preservando dados:
docker compose down
```

O Compose publica a porta somente no localhost e mantém os dados em `budget-data`. `docker compose down -v` apaga esse volume. O Dockerfile executa os testes durante o build; a construção precisa baixar dependências, mas os testes não usam a API paga.

## Como testar o fluxo principal

Em outro terminal, dentro da pasta do projeto:

```bash
set -a
source .env
set +a
```

### 1. Envie texto ou áudio

Texto:

```bash
curl --fail-with-body -sS http://localhost:8080/api/commands/text \
  -H "X-API-Key: $APP_API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"text":"Registre uma despesa de 35,50 reais com almoço, categoria alimentação, em 17 de novembro de 2026."}'
```

Áudio: grave a mesma frase e salve, por exemplo, como `comando.wav`.

```bash
curl --fail-with-body -sS http://localhost:8080/api/commands/audio \
  -H "X-API-Key: $APP_API_KEY" \
  -F 'file=@/caminho/para/comando.wav'
```

Formatos: MP3, MP4, MPEG, MPGA, M4A, WAV e WEBM, até 10 MB. A validação local checa extensão, tamanho e vazio; a decodificação/validade do conteúdo fica com o provedor. Renomear um arquivo não o converte em áudio.

Exemplo de resposta (IDs e instante variam):

```json
{
  "id": "b34f8d79-2831-4a4a-93da-8b4ed2b54fb3",
  "status": "AGUARDANDO_CONFIRMACAO",
  "source": "TEXTO",
  "transcript": "Registre uma despesa de 35,50 reais com almoço...",
  "answer": "Proposta preparada. Confira os dados e confirme para registrar. Nenhuma transação foi gravada ainda.",
  "proposal": {
    "type": "DESPESA",
    "amount": 35.50,
    "description": "Almoço",
    "category": "Alimentação",
    "date": "2026-11-17"
  },
  "transactionId": null,
  "createdAt": "2026-11-17T14:00:00Z"
}
```

Se faltarem dados, pode vir `CONCLUIDO` com uma pergunta na resposta, sem proposta. Envie novamente o comando completo. A resposta da IA pode variar; o campo `status` e os dados retornados pela API definem o resultado real.

### 2. Confira e confirme a proposta

Use o `id` efetivamente retornado, não o UUID ilustrativo:

```bash
ID='cole-o-id-retornado'
curl --fail-with-body -sS "http://localhost:8080/api/commands/$ID" \
  -H "X-API-Key: $APP_API_KEY"

curl --fail-with-body -sS -X POST "http://localhost:8080/api/commands/$ID/confirm" \
  -H "X-API-Key: $APP_API_KEY"
```

A resposta passa a `CONFIRMADO` e contém `transactionId`. Repetir o último comando não duplica esse lançamento. Para descartar uma proposta **ainda pendente**:

```bash
curl --fail-with-body -sS -X POST "http://localhost:8080/api/commands/$ID/cancel" \
  -H "X-API-Key: $APP_API_KEY"
```

### 3. Consulte o banco e a auditoria

```bash
curl --fail-with-body -sS 'http://localhost:8080/api/transactions?start=2026-11-01&end=2026-11-30&page=0&size=20' \
  -H "X-API-Key: $APP_API_KEY"

curl --fail-with-body -sS 'http://localhost:8080/api/transactions/summary?start=2026-11-01&end=2026-11-30' \
  -H "X-API-Key: $APP_API_KEY"

curl --fail-with-body -sS "http://localhost:8080/api/commands/$ID/audit" \
  -H "X-API-Key: $APP_API_KEY"
```

Para consultar via IA:

```bash
curl --fail-with-body -sS http://localhost:8080/api/commands/text \
  -H "X-API-Key: $APP_API_KEY" -H 'Content-Type: application/json' \
  -d '{"text":"Qual foi o total de receitas, despesas e o saldo de novembro de 2026?"}'
```

O resumo usa todas as transações do período; não soma apenas a primeira página.

### 4. Gere a resposta falada

```bash
curl --fail-with-body -sS "http://localhost:8080/api/commands/$ID/speech" \
  -H "X-API-Key: $APP_API_KEY" --output resposta.mp3
xdg-open resposta.mp3
```

**A voz é sintetizada por IA.** O áudio fala a resposta correspondente ao estado atual do comando. Não é armazenado no servidor; cada chamada gera uma nova síntese e pode ter custo. A geração de voz é separada para permitir nova tentativa sem repetir o lançamento.

### Smoke test opcional

```bash
bash scripts/smoke-test.sh
```

Usa IA real, prepara uma despesa fictícia, cancela a proposta e mostra a auditoria. Não confirma lançamentos. Os exemplos também estão em `docs/requests.http`.

## Endpoints

Todos exigem `X-API-Key`, exceto `/api/health`.

| Método | Caminho | Função |
|---|---|---|
| GET | `/api/health` | Disponibilidade do servidor |
| POST | `/api/commands/text` | JSON `{"text":"..."}` |
| POST | `/api/commands/audio` | Multipart com campo `file` |
| GET | `/api/commands/{id}` | Estado, transcrição, proposta e resposta |
| POST | `/api/commands/{id}/confirm` | Confirma proposta pendente |
| POST | `/api/commands/{id}/cancel` | Cancela proposta pendente |
| GET | `/api/commands/{id}/audit` | Eventos do comando |
| GET | `/api/commands/{id}/speech` | MP3 da resposta atual |
| GET | `/api/transactions` | `start`, `end`, `page=0`, `size=20` |
| GET | `/api/transactions/summary` | `start` e `end` inclusivos |

Datas de consulta são `YYYY-MM-DD`. Tamanho de página: 1–100. Valores: de R$ 0,01 a R$ 9.999.999.999,99 e no máximo duas casas decimais. Receita/despesa usam valor positivo; o tipo determina a entrada ou saída. Datas relativas usam `APP_ZONE`, padrão `America/Sao_Paulo`.

## Organização do código

```text
src/main/java/br/com/orcamento/
  ai/          ChatClient, @Tool e integração de áudio
  api/         Controllers, DTOs, erros HTTP
  config/      Autenticação e timeouts HTTP
  domain/      Entidades e estados
  repository/  Acesso ao banco
  service/     Validação, consultas e transições de estado
src/main/resources/db/migration/  Migração Flyway
src/test/                        Testes automatizados
```

Controller cuida de HTTP; `AssistantService` orquestra IA; `AudioService` cuida de áudio; `BudgetTools` oferece operações limitadas; `TransactionService` valida e consulta; `CommandStore` controla persistência e confirmação. O modelo não recebe acesso a SQL, shell, arquivos ou método de confirmação.

Chamadas externas não mantêm uma transação de banco aberta. `CommandStore` é outro bean para que `@Transactional` seja efetivamente aplicado pelo proxy do Spring. Uma ferramenta só prepara a proposta em memória; ela é salva após a chamada de chat terminar. Se o modelo/provedor falhar antes disso, nenhuma transação financeira é criada.

## Testes automatizados

```bash
mvn clean verify
```

Não exige chaves reais nem créditos. Dependências Maven precisam estar disponíveis (internet no primeiro build). Os testes usam H2 em memória e servidor HTTP local simulando o provedor. Eles exercitam a integração real de `ChatClient`, execução das ferramentas, endpoints, banco e migrações. Áudio/transcrição são respostas simuladas; os bytes de teste não comprovam qualidade acústica ou reconhecimento de fala real.

Há cobertura de proposta sem gravação, confirmação e repetição, confirmações concorrentes, cancelamento, consulta por ferramenta, cálculos decimais, autenticação, entradas inválidas, upload, resposta MP3 e falha do provedor com auditoria. Consulte `docs/VALIDACAO.md` para o resultado da execução na preparação deste projeto.

## Erros e limites conhecidos

- `400`: texto vazio, JSON/datas inválidos, parâmetros ou valores inválidos.
- `401`: `X-API-Key` ausente ou incorreto.
- `404`: comando inexistente.
- `409`: confirmação/cancelamento incompatível com o estado atual.
- `413`: áudio maior que o limite.
- `415`: extensão de áudio não aceita.
- `502`: falha no provedor, chave externa sem acesso/créditos, transcrição ou resposta inválida. Consulte o ID informado; não houve gravação financeira no processamento inicial.
- `500`: falha interna; consulte o comando antes de repetir operações.

O projeto é **local e de usuário único**. A chave dá acesso a todo o orçamento; não há isolamento de múltiplos usuários, papéis, quota ou rate limit. Para uso público, implemente autenticação por usuário, autorização por recurso, HTTPS, limites de uso e banco de produção. As consultas via IA podem produzir texto impreciso; os endpoints estruturados são a fonte de conferência.

Áudio, comandos e resultados necessários às ferramentas são enviados ao provedor de IA. O banco guarda a transcrição, resposta, proposta e registros financeiros; eventos de auditoria guardam ID, ação e horário. Áudio e MP3 não são persistidos. Não há criptografia do arquivo H2 nem retenção automática. A auditoria registra estados relevantes, não cada chamada de ferramenta, e não é um log imutável contra administradores do banco.

Não há fila nem retomada automática: se o processo cair durante uma chamada externa, o comando pode permanecer `PROCESSANDO`. Como nenhuma ferramenta grava lançamentos, não há transação financeira nesse estado. Reenvie o comando completo após verificar o ID. Propostas pendentes não expiram. Não há edição/exclusão de transações, recorrência, múltiplas moedas nem integração bancária.

## O que aprendi durante o desafio

*Texto-base para adaptar à sua experiência antes de entregar o trabalho:*

“Aprendi que usar IA em uma aplicação não significa entregar as regras de negócio ao modelo. O `ChatClient` permite enviar o comando, enquanto o Tool Calling conecta a interpretação a métodos reais em Java. Separei as responsabilidades de HTTP, IA, regras e persistência, usando validações e `BigDecimal` para os valores. Também compreendi que transcrição pode errar, então acrescentei a confirmação antes da gravação. A auditoria e os testes mostraram a importância de tratar falhas e impedir duplicações quando uma requisição é repetida.”

## Fontes oficiais

- [Spring AI — ChatClient, linha 1.1](https://docs.spring.io/spring-ai/reference/1.1/api/chatclient.html)
- [Spring AI — Tool Calling, linha 1.1](https://docs.spring.io/spring-ai/reference/1.1/api/tools.html)
- [Spring AI — Transcrição, linha 1.1](https://docs.spring.io/spring-ai/reference/1.1/api/audio/transcriptions/openai-transcriptions.html)
- [Spring AI — Síntese de voz, linha 1.1](https://docs.spring.io/spring-ai/reference/1.1/api/audio/speech/openai-speech.html)
- [Spring AI — compatibilidade de versões](https://github.com/spring-projects/spring-ai)
- [Spring Boot](https://docs.spring.io/spring-boot/3.5/)

Os links da linha 1.1 podem refletir patches posteriores ao 1.1.0 fixado no `pom.xml`; o build/testes são a verificação das APIs usadas neste projeto.
