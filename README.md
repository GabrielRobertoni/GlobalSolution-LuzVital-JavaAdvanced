# Luz Vital · API Java (Spring Boot)

**Global Solution 2026/2 · CitizenTech · Java Advanced**

O Luz Vital cadastra quem depende de aparelhos elétricos para viver (ventilador, concentrador de oxigênio, bomba de infusão...) e, quando a luz cai, **ordena o socorro pelos minutos de bateria que restam em cada casa**. Esta é a API Java: o núcleo com os cadastros, os papéis, a IA generativa e o evento assíncrono que monta a fila do gestor. É a mesma API consumida pelo app React Native.

| | |
|---|---|
| Repositório | `<link do repositório>` |
| Vídeo pitch | `<link do vídeo>` |
| Deploy | `<link do deploy, se houver>` |
| **Requisito 5 (arquitetura distribuída)** | **Opção A: RabbitMQ**, com evento publicado e consumido de forma assíncrona |

> **O Luz Vital não é serviço de emergência.** Não substitui o SAMU (192), os Bombeiros (193) nem o canal da distribuidora, e não dá orientação clínica. Todas as respostas de ocorrência e de plano trazem esse aviso.

## Equipe

| Nome | RM |
|---|---|
| `<nome>` | `<RM>` |

---

## Sumário
1. [Tecnologias](#tecnologias)
2. [Arquitetura](#arquitetura)
3. [Como rodar](#como-rodar)
4. [Usuários de teste](#usuários-de-teste)
5. [Endpoints principais](#endpoints-principais)
6. [Roteiro de teste (ponta a ponta)](#roteiro-de-teste-ponta-a-ponta)
7. [Como cada requisito foi atendido](#como-cada-requisito-foi-atendido)
8. [Integração com o app Mobile e com a API .NET](#integração-com-o-app-mobile-e-com-a-api-net)
9. [Testes automatizados](#testes-automatizados)
10. [Declaração de uso de IA](#declaração-de-uso-de-ia)

---

## Tecnologias

- Java 21, **Spring Boot 4.1**
- **Spring Data JPA** (Hibernate 7). PostgreSQL por padrão; Oracle no perfil `oracle`, com as mesmas tabelas do script da disciplina de banco
- **Spring Security + JWT** (resource server do Spring Security, HS256) com os papéis `CUIDADOR` e `GESTOR`
- **Spring AI 2.0**: `ChatClient` com saída estruturada (records Java) e entrada multimodal (foto). O provedor é compatível com a API da OpenAI: Gemini por padrão, ou OpenAI e Groq
- **RabbitMQ** (Spring AMQP): exchange topic, filas duráveis e dead letter queue
- Bean Validation, `@RestControllerAdvice`, springdoc-openapi (Swagger)
- JUnit 5, MockMvc, H2 (testes)

## Arquitetura

```mermaid
flowchart LR
    APP[App React Native<br/>cuidador e gestor] -- JWT --> API[API Java<br/>Spring Boot]
    NET[API .NET<br/>canal de emergência] -. mesmo JWT .-> API
    API -- JPA --> DB[(PostgreSQL / Oracle)]
    API -- "publica energia.caiu<br/>e plano.solicitado" --> MQ{{RabbitMQ<br/>exchange luzvital.eventos}}
    MQ -- luzvital.energia-caiu --> C1[Consumidor:<br/>prioridade + pontos de energia]
    MQ -- luzvital.plano-contingencia --> C2[Consumidor:<br/>plano com Spring AI]
    C2 -- ChatClient --> LLM[LLM<br/>Gemini / OpenAI]
    API -- "foto da etiqueta<br/>(multimodal)" --> LLM
    C1 --> DB
    C2 --> DB
```

**Caminho crítico ("Estou sem luz")**
1. O app chama `POST /ocorrencias` com o cabeçalho `Idempotency-Key`.
2. A API trava a linha do paciente, confere a idempotência e se já existe ocorrência ativa, calcula a bateria inicial (menor autonomia entre os aparelhos de suporte à vida) e grava.
3. **Só depois do commit**, publica `energia.caiu` no RabbitMQ (`@TransactionalEventListener(AFTER_COMMIT)`).
4. O consumidor calcula o score de prioridade, encontra os pontos de energia próximos (num raio de 5 km pelas coordenadas, ou no mesmo bairro) e grava as notificações. Ele é idempotente: uma mensagem reentregue não duplica avisos.
5. O gestor vê a ocorrência em `GET /ocorrencias/fila`, **ordenada pelos minutos de bateria que restam agora**. No empate, ventilador vem antes de concentrador, depois o maior score e por fim a ordem de chegada.

**Resiliência da mensageria**
- Um erro no consumidor gera 3 tentativas com backoff; depois a mensagem vai para a fila `.dlq`, sem loop infinito.
- Se o RabbitMQ estiver fora do ar na abertura, a ocorrência é gravada mesmo assim e aparece na fila (ordenada pelos minutos), e um job republica o evento a cada 60 s até ser consumido.

**Modelo de dados.** As tabelas e colunas têm os mesmos nomes do script Oracle da equipe de banco.

```mermaid
erDiagram
    USUARIO ||--o{ PACIENTE : "cuida de"
    PACIENTE ||--o{ EQUIPAMENTO : "usa"
    PACIENTE ||--o| PLANO_CONTINGENCIA : "tem"
    PACIENTE ||--o{ OCORRENCIA : "abre"
    OCORRENCIA ||--o{ ATENDIMENTO : "recebe"
    OCORRENCIA ||--o{ NOTIFICACAO_PONTO : "avisa"
    PONTO_ENERGIA ||--o{ NOTIFICACAO_PONTO : "é avisado"
    PONTO_ENERGIA |o--o{ ATENDIMENTO : "é acionado"
    USUARIO ||--o{ ATENDIMENTO : "registra (gestor)"
```

## Como rodar

O primeiro start cria as tabelas e carrega os dados de teste automaticamente: usuários, 3 pacientes, aparelhos e 5 pontos de energia.

### Opção 1: Docker (recomendado)

Pré-requisito: Docker Desktop.

```bash
docker compose up --build
```

- Swagger: http://localhost:8080/swagger-ui.html
- RabbitMQ: http://localhost:15672 (guest / guest)

Para ligar a IA generativa, copie `.env.example` para `.env`, preencha `IA_API_KEY` e rode o mesmo comando. A chave do Gemini é gratuita em https://aistudio.google.com/apikey.

### Opção 2: Sem Docker (Maven Wrapper)

Pré-requisitos: JDK 21+, PostgreSQL 14+ e RabbitMQ 3.12+ rodando localmente.

1. Crie o banco:
   ```sql
   CREATE ROLE luzvital LOGIN PASSWORD 'luzvital';
   CREATE DATABASE luzvital OWNER luzvital;
   ```
2. Defina as variáveis desejadas (opcional) e suba a API:
   ```bash
   ./mvnw spring-boot:run
   ```
   No Windows, use `mvnw.cmd spring-boot:run`.

### Opção 3: Oracle (banco da disciplina)

1. Rode o script principal da equipe de banco e depois [`scripts/oracle-complemento-api-java.sql`](scripts/oracle-complemento-api-java.sql).
2. Suba com o perfil `oracle`:
   ```bash
   SPRING_PROFILES_ACTIVE=oracle ORACLE_URL=jdbc:oracle:thin:@oracle.fiap.com.br:1521:ORCL DB_USER=<rm> DB_PASSWORD=<senha> ./mvnw spring-boot:run
   ```
   Na primeira subida, os pacientes do script sem dono são associados a `cuidador@luzvital.com`.

### Variáveis de ambiente

Nenhuma credencial real fica no repositório.

| Variável | Padrão | Para que serve |
|---|---|---|
| `JWT_SECRET` | vazio (chave aleatória a cada subida) | Chave HS256 com 32+ caracteres. **A mesma chave na API .NET** |
| `IA_API_KEY` | vazio (IA desligada) | Chave do provedor de IA. Sem ela, o plano usa as regras padrão e a leitura de etiqueta devolve 503 |
| `IA_BASE_URL` | `https://generativelanguage.googleapis.com/v1beta/openai` | Endpoint compatível com OpenAI (Gemini, `https://api.openai.com/v1`, Groq...) |
| `IA_MODELO` | `gemini-3.5-flash` | Modelo. Para a foto da etiqueta, precisa aceitar imagem |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `localhost` / `5432` / `luzvital` / `luzvital` / `luzvital` | PostgreSQL |
| `RABBITMQ_ADDRESSES` | `amqp://guest:guest@localhost:5672` | RabbitMQ (aceita `amqps://` do CloudAMQP) |
| `CORS_ORIGENS` | `*` | Origens liberadas (Expo web) |
| `PORT` | `8080` | Porta HTTP |

## Usuários de teste

| Papel | E-mail | Senha | Dados |
|---|---|---|---|
| GESTOR | `gestor@luzvital.com` | `Gestor@123` | Vê a fila, registra atendimentos, gerencia pontos de energia e gestores |
| CUIDADOR | `cuidador@luzvital.com` | `Cuidador@123` | Maria Oliveira (ventilador Trilogy Evo, 15 h de bateria) e João Santos (concentrador EverFlo, **sem bateria**) |
| CUIDADOR | `cuidador2@luzvital.com` | `Cuidador@123` | Carlos Eduardo (só bomba de infusão, que **não** é de suporte à vida: serve para o teste negativo de abertura) |

Para usar no Swagger, chame `POST /auth/login`, copie o `token`, clique em **Authorize** e cole o token.

## Endpoints principais

Todas as listagens são paginadas: `?page=0&size=10&sort=campo,asc`. A resposta vem como `{conteudo, pagina, tamanho, totalElementos, totalPaginas, ultima}`.

| Método e rota | Quem pode | O que faz |
|---|---|---|
| `POST /auth/cadastro` | público | Cadastro do cuidador (sempre com o papel CUIDADOR) e devolve o token |
| `POST /auth/login` | público | Login e devolve o JWT |
| `POST /auth/logout` | autenticado | Revoga o token atual |
| `GET /auth/me` | autenticado | Usuário logado |
| `GET/POST /pacientes`, `GET/PUT/DELETE /pacientes/{id}` | CUIDADOR (os seus) · GESTOR lê todos | CRUD de pacientes |
| `GET/POST /equipamentos`, `GET/PUT/DELETE /equipamentos/{id}` | CUIDADOR (os seus) · GESTOR lê | CRUD de aparelhos. Salvar, alterar ou excluir **gera de novo o plano pela IA** |
| `POST /equipamentos/leitura-etiqueta` | CUIDADOR | **Cadastro por foto**: a IA lê fabricante, modelo, V, A e W (multipart, campo `foto`) |
| `GET /pacientes/{id}/plano-contingencia` | dono · GESTOR | **Plano de contingência gerado pela IA** (estruturado) |
| `POST /pacientes/{id}/plano-contingencia` | dono | Pede um novo plano (202, assíncrono) |
| `GET/POST /pontos-energia`, `GET/PUT/DELETE /pontos-energia/{id}` | todos leem · GESTOR escreve | Rede de pontos de energia |
| `POST /ocorrencias` | CUIDADOR | **Estou sem luz** (cabeçalho `Idempotency-Key`) e publica `energia.caiu` |
| `GET /ocorrencias`, `GET /ocorrencias/{id}` | dono · GESTOR | Ocorrências com contagem regressiva e pontos avisados |
| `GET /ocorrencias/fila` | **GESTOR** | **Fila ordenada pelos minutos de bateria restantes** |
| `PATCH /ocorrencias/{id}/bateria` | dono · GESTOR | Informa a bateria atual e recalcula a prioridade |
| `POST /ocorrencias/{id}/atendimentos` | **GESTOR** | Registra gerador, recarga, remoção ou religação |
| `POST /ocorrencias/{id}/encerramento` | dono · GESTOR | Encerra (ATENDIDO) |
| `POST /ocorrencias/{id}/cancelamento` | dono · GESTOR | Cancela (por exemplo, a energia voltou) |
| `GET /ocorrencias/indicadores` | **GESTOR** | Tempo médio de atendimento, % resolvidas antes do fim da bateria, pontos por bairro |
| `POST /usuarios/gestores`, `GET /usuarios` | **GESTOR** | Só um gestor cria outro gestor |
| `GET /actuator/health` | público | Health check (usado pela API .NET) |

### Formato padrão de erro

Vale para todos os erros: 400, 401, 403, 404, 409, 422, 503 e 500.

```json
{
  "timestamp": "2026-11-10T21:30:00",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "Dados inválidos",
  "caminho": "/equipamentos",
  "campos": [
    { "campo": "potenciaWatts", "mensagem": "a potência deve ser maior que zero" },
    { "campo": "autonomiaMinutos", "mensagem": "a autonomia não pode ser negativa" }
  ]
}
```

## Roteiro de teste (ponta a ponta)

1. `POST /auth/login` como **cuidador**. Depois `GET /pacientes`: aparecem só Maria e João.
2. `GET /pacientes/2/plano-contingencia`: o plano gerado na carga inicial, com `origem` igual a `IA` quando há chave, ou `REGRAS` quando não há.
3. `POST /equipamentos` com `potenciaWatts: 0` devolve **400** com os campos inválidos.
4. `POST /ocorrencias` com `{"pacienteId": 2}` e `Idempotency-Key: 3f1c...` devolve **201**. Repetir a mesma chave devolve **200** com a mesma ocorrência. Repetir sem chave devolve **409**.
5. `POST /ocorrencias` com `{"pacienteId": 1}` devolve **201**.
6. `GET /ocorrencias/fila` como cuidador devolve **403**.
7. Login como **gestor** e `GET /ocorrencias/fila`: João vem primeiro (0 min, concentrador sem bateria), depois Maria (900 min), e as duas aparecem com `priorizada: true` (o consumidor do RabbitMQ processou o evento).
8. `GET /ocorrencias/{id}` mostra os `pontosNotificados` com a distância em km.
9. `POST /ocorrencias/{id}/atendimentos` com `{"tipo":"GERADOR","descricao":"...","pontoEnergiaId":1}` muda o status para `EM_ATENDIMENTO`.
10. `POST /ocorrencias/{id}/encerramento` devolve 200. Encerrar de novo devolve **409**.
11. Login como **cuidador2** e `POST /ocorrencias` com `{"pacienteId": 3}` devolve **422** (nenhum aparelho de suporte à vida).
12. No painel do RabbitMQ (http://localhost:15672), em *Queues*, aparecem as filas `luzvital.energia-caiu` e `luzvital.plano-contingencia` com consumidores.

## Como cada requisito foi atendido

| # | Requisito | Onde está |
|---|---|---|
| 01 | API REST, 3+ entidades relacionadas, CRUD, paginação | Entidades `Usuario`, `Paciente`, `Equipamento`, `PontoEnergia`, `Ocorrencia`, `Atendimento`, `NotificacaoPonto` e `PlanoContingencia` (pacote `domain`). Paciente 1:N Equipamento; Ocorrência N:1 Paciente; Ocorrência 1:N Atendimento. CRUD completo de pacientes, equipamentos e pontos; todas as listagens paginadas |
| 02 | Bean Validation nos DTOs + `@RestControllerAdvice` | Records no pacote `dto` (CPF, CEP, UF, telefone, coordenadas, potência > 0, autonomia ≥ 0...). `exception/GlobalExceptionHandler` e `security/ErroSegurancaHandler` (401/403 no mesmo formato) |
| 03 | Spring Security + JWT, 2 papéis, rotas por papel | `config/SecurityConfig` (regras por rota + `@PreAuthorize`), `security/JwtService`. O cuidador só acessa os próprios pacientes (`service/Acesso`). Logout revoga o token |
| 04 | Spring AI integrado ao fluxo | `ia/IaService`: (1) **plano de contingência** gerado ao salvar um aparelho, com saída estruturada em `PlanoGerado` (etapas por faixa de tempo, contatos, sinais de alerta); (2) **leitura da etiqueta por foto** (multimodal), com potência estimada por V × A. Guarda-corpos: sem orientação clínica, sem nome ou CPF no prompt (LGPD), 192 e 193 sempre presentes, e plano por regras quando a IA falha |
| 05 | Arquitetura distribuída: **opção A (RabbitMQ)** | `messaging/RabbitConfig` (exchange topic, filas, DLQ), `EventoPublisher` (pós-commit), `EventoConsumidor` (`energia.caiu` e `plano.solicitado`), `ReprocessamentoJob` |
| 06 | Swagger, README e integração com o Mobile | `/swagger-ui.html` com Bearer configurado, este README e a seção de integração abaixo |

## Integração com o app Mobile e com a API .NET

**App React Native (Axios)**
- Cabeçalho: `Authorization: Bearer <token>` (o token vem de `/auth/login` ou de `/auth/cadastro`).
- URL base: no emulador Android use `http://10.0.2.2:8080`; no celular físico, use o IP da máquina na rede (`http://192.168.x.x:8080`) ou o deploy.
- "Estou sem luz": gere um UUID por toque, guarde-o na fila offline e reenvie **com a mesma** `Idempotency-Key`. Assim o reenvio nunca duplica a ocorrência.
- Plano de contingência: `GET /pacientes/{id}/plano-contingencia`. Se o `status` for `GERANDO`, consulte de novo em alguns segundos. Salve o plano no aparelho para uso offline.
- Cadastro por foto: envie `multipart/form-data` com o campo `foto` (JPEG, PNG ou WEBP, até 5 MB), mostre os dados para o cuidador confirmar e depois chame `POST /equipamentos`.

**API .NET (mesmo JWT)**
- Algoritmo HS256 com a chave `JWT_SECRET` (a mesma nas duas APIs). Valide `iss = luz-vital-api` e `aud = luz-vital-app`.
- Claims: `sub` (id do usuário), `email`, `nome`, `role` (`CUIDADOR` ou `GESTOR`), `jti`, `exp`.
- Para avisar a API Java de uma ocorrência, chame `POST /ocorrencias` repassando o token do usuário e a mesma `Idempotency-Key`. A Java publica `energia.caiu`.
- Health check: `GET /actuator/health`.

## Testes automatizados

```bash
./mvnw test
```

São 20 testes: regra de prioridade e fila, contagem regressiva, haversine, conversão da etiqueta (potência estimada), Spring AI com um `ChatModel` simulado (saída estruturada e guarda-corpos), e testes de integração com MockMvc (401/403 no formato padrão, validação, publicação de `energia.caiu`, idempotência, 409 e 422). Os testes usam H2 em memória e não precisam de Docker.

## Declaração de uso de IA

- **No produto:** a API usa IA generativa via Spring AI para gerar o plano de contingência e ler a etiqueta do aparelho por foto. A IA não dá orientação clínica e o cuidador sempre confirma os dados lidos. Sem chave configurada, a API continua funcionando com o plano padrão por regras.
- **No desenvolvimento:** usamos assistente de IA generativa (Claude, da Anthropic) como apoio na implementação, na revisão do código, nos testes e na documentação. Todo o conteúdo foi revisado, executado e testado pela equipe, que é responsável pelas decisões de projeto e pela entrega.

## Riscos e cuidados

- **LGPD:** saúde e endereço são dados sensíveis. Use só dados fictícios. O prompt da IA não recebe nome, CPF nem endereço do paciente.
- **A regra de prioridade é uma hipótese de projeto**, a validar com profissional de saúde. Não é um protocolo clínico.
- **A leitura por foto pode errar.** O app sempre pede confirmação e aceita digitação manual.
