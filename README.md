# Luz Vital · API Java (Spring Boot)

**Global Solution 2026/2 · CitizenTech · Java Advanced**

O Luz Vital cadastra quem depende de aparelhos elétricos para viver (ventilador, concentrador de oxigênio, aspirador de secreção, bomba de infusão...) e, quando a luz cai, **ordena o socorro pelos minutos de bateria que restam em cada casa**. Esta é a API Java: o núcleo com os cadastros, os papéis, a IA generativa e o evento assíncrono que monta a fila do gestor. É a mesma API consumida pelo app React Native.

| | |
|---|---|
| Repositório | https://github.com/GabrielRobertoni/GlobalSolution-LuzVital-JavaAdvanced |
| Vídeo pitch | `<link do vídeo>` |
| Deploy (Swagger) | https://luz-vital-api.onrender.com/swagger-ui.html |
| **Requisito 5 (arquitetura distribuída)** | **Opção A: RabbitMQ**, com evento publicado e consumido de forma assíncrona |

## Como testar (recomendado: pelo deploy, sem instalar nada)

A API está no ar, ligada ao **Oracle da FIAP** (tabelas do banco da disciplina), ao **RabbitMQ** (CloudAMQP) e à **IA generativa** (Gemini). É a forma mais completa de testar: tudo funciona, inclusive a IA.

1. Abra o Swagger: **https://luz-vital-api.onrender.com/swagger-ui.html** (o endereço curto https://luz-vital-api.onrender.com também leva para ele).
   > O plano gratuito do Render desliga a API depois de 15 minutos sem uso: **a primeira abertura pode levar de 1 a 3 minutos**. Depois disso, fica rápida.
2. Em **1. Autenticação**, use `POST /auth/login` → **Try it out** com um usuário de teste e clique em **Execute**:
   - gestora: `{"email": "luciana.mendes@defesacivil.sp.gov.br", "senha": "hash_luciana_adm"}`
   - cuidador: `{"email": "carlos.silva@gmail.com", "senha": "hash_carlos123"}`
3. Copie o `token` da resposta, clique em **Authorize** (cadeado, no topo), cole e confirme.
4. Siga o [roteiro de teste](#roteiro-de-teste-ponta-a-ponta). Os demais usuários estão em [Usuários de teste](#usuários-de-teste).

Para rodar no próprio computador (Docker ou Maven, com PostgreSQL e RabbitMQ locais), veja [Como rodar](#como-rodar). Localmente, a IA só funciona com uma chave própria do Gemini em `IA_API_KEY`; sem ela, a API usa o plano padrão por regras.

> **O Luz Vital não é serviço de emergência.** Não substitui o SAMU (192), os Bombeiros (193) nem o canal da distribuidora, e não dá orientação clínica. Todas as respostas de ocorrência e de plano trazem esse aviso.

## Equipe

| Nome | RM | Turma |
|---|---|---|
| Hebert Lopes do Santos | 563192 | 2TDSR |
| Nicolas Monteiro Ramiro | 562380 | 2TDSR |
| Marcus Vinícius Vila Nova da Silva | 558771 | 2TDSR |
| Bruno Ferreira | 563489 | 2TDSR |
| Gabriel Robertoni Padilha | 566293 | 2TDSR |

---

## Sumário
0. [Como testar (deploy)](#como-testar-recomendado-pelo-deploy-sem-instalar-nada)
1. [Tecnologias](#tecnologias)
2. [Arquitetura](#arquitetura)
3. [Modelo de dados (integrado ao banco da disciplina)](#modelo-de-dados-integrado-ao-banco-da-disciplina)
4. [Como rodar](#como-rodar)
5. [Usuários de teste](#usuários-de-teste)
6. [Endpoints principais](#endpoints-principais)
7. [Roteiro de teste (ponta a ponta)](#roteiro-de-teste-ponta-a-ponta)
8. [Como cada requisito foi atendido](#como-cada-requisito-foi-atendido)
9. [Integração com o app Mobile e com a API .NET](#integração-com-o-app-mobile-e-com-a-api-net)
10. [Testes automatizados](#testes-automatizados)
11. [Declaração de uso de IA](#declaração-de-uso-de-ia)

---

## Tecnologias

- Java 21, **Spring Boot 4.1**
- **Spring Data JPA** (Hibernate 7) sobre as tabelas `T_GS_*` do banco da disciplina. PostgreSQL por padrão; Oracle no perfil `oracle`
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
    API -- JPA --> DB[(Oracle T_GS_* /<br/>PostgreSQL)]
    API -- "publica energia.caiu<br/>e plano.solicitado" --> MQ{{RabbitMQ<br/>exchange luzvital.eventos}}
    MQ -- luzvital.energia-caiu --> C1[Consumidor:<br/>prioridade + pontos do bairro]
    MQ -- luzvital.plano-contingencia --> C2[Consumidor:<br/>plano com Spring AI]
    C2 -- ChatClient --> LLM[LLM<br/>Gemini / OpenAI]
    API -- "foto da etiqueta<br/>(multimodal)" --> LLM
    C1 --> DB
    C2 --> DB
```

**Caminho crítico ("Estou sem luz")**
1. O app chama `POST /ocorrencias` com o cabeçalho `Idempotency-Key`.
2. A API trava a linha do paciente, confere a idempotência e se já existe ocorrência ativa (a mesma regra de `PR_T_GS_ABRIR_OCORRENCIA`), calcula a bateria inicial (menor autonomia entre os aparelhos de suporte à vida) e grava em `T_GS_OCORRENCIA`.
3. **Só depois do commit**, publica `energia.caiu` no RabbitMQ (`@TransactionalEventListener(AFTER_COMMIT)`).
4. O consumidor calcula o score de prioridade, avisa os pontos de energia disponíveis no bairro do paciente (gerador primeiro) e grava os avisos. Ele é idempotente: uma mensagem reentregue não duplica avisos.
5. O gestor vê a ocorrência em `GET /ocorrencias/fila`, **ordenada pelos minutos de bateria que restam agora**. No empate, ventilador vem antes de concentrador, depois o maior score e por fim a ordem de chegada.

**Regra de prioridade.** É a mesma do pacote Oracle `PKG_T_GS_EQUIPAMENTO`:
- **Minutos restantes:** menor autonomia de suporte à vida menos o tempo desde que a bateria foi informada (`FN_T_GS_MINUTOS_RESTANTES`).
- **Score** (`FN_T_GS_CALCULA_PRIORIDADE`):

  | Minutos restantes | Score |
  |---|---|
  | 0 | 1000 |
  | até 60 | 800 + (60 − min) |
  | até 180 | 500 + (180 − min) |
  | acima de 180 | 100 |

  Soma **+200** quando o paciente usa aparelho de suporte à vida.

**Resiliência da mensageria**
- Um erro no consumidor gera 3 tentativas com backoff; depois a mensagem vai para a fila `.dlq`, sem loop infinito.
- Se o RabbitMQ estiver fora do ar na abertura, a ocorrência é gravada mesmo assim e aparece na fila (ordenada pelos minutos), e um job republica o evento a cada 60 s até ser consumido.

## Modelo de dados (integrado ao banco da disciplina)

A API usa **as tabelas do script da disciplina de banco (`GSBancoOracle.sql`) sem nenhuma alteração**: mesmos nomes de tabela e coluna, mesmos tipos, mesmos valores de domínio. Inclusive os tipos de socorro com acento exigidos pela constraint `CK_T_GS_ATEND_TIPO` (`REMOÇAO_UTI`, `RELIGAÇÃO_PRIORITARIA`) e o aparelho `ASPIRADOR_SECREÇÃO` da carga.

| Tabela da disciplina de banco | Entidade JPA | Relacionamento |
|---|---|---|
| `T_GS_USUARIO` | `Usuario` | cuidador 1:N pacientes |
| `T_GS_PACIENTE` | `Paciente` | 1:N equipamentos, 1:N ocorrências |
| `T_GS_EQUIPAMENTO` | `Equipamento` | N:1 paciente |
| `T_GS_PONTO_ENERGIA` | `PontoEnergia` | 1:N atendimentos |
| `T_GS_OCORRENCIA` | `Ocorrencia` | N:1 paciente, 1:N atendimentos |
| `T_GS_ATENDIMENTO` | `Atendimento` | N:1 ocorrência, N:1 ponto |

**Nenhuma tabela extra.** A API trabalha só com as tabelas do script e, ao subir, não cria nem altera nenhum dado. O que é exclusivo do backend e não tem coluna no modelo do banco fica **em memória da API**:

| Estado em memória | Para que serve | Se a API reiniciar |
|---|---|---|
| Chave de idempotência do app | O "Estou sem luz" reenviado devolve a mesma ocorrência | A regra "uma ocorrência ativa por paciente" continua impedindo duplicidade |
| Horário em que a bateria foi informada | Contagem regressiva dos minutos depois de `PATCH /bateria` | A contagem volta a usar `dh_inicio` |
| Avisos aos pontos de energia | Resultado do consumidor de `energia.caiu` | Novos avisos são feitos nas próximas ocorrências |
| Plano de contingência | Resposta estruturada da IA (o app guarda no celular para uso offline) | É gerado de novo na próxima consulta |
| Tokens revogados | Logout | Com `JWT_SECRET` vazio, todos os tokens antigos deixam de valer. Com `JWT_SECRET` fixo, um token já revogado volta a valer até expirar (`JWT_EXPIRACAO`, padrão 8 h) |

```mermaid
erDiagram
    T_GS_USUARIO ||--o{ T_GS_PACIENTE : "cuida de"
    T_GS_PACIENTE ||--o{ T_GS_EQUIPAMENTO : "usa"
    T_GS_PACIENTE ||--o{ T_GS_OCORRENCIA : "abre"
    T_GS_OCORRENCIA ||--o{ T_GS_ATENDIMENTO : "recebe"
    T_GS_PONTO_ENERGIA |o--o{ T_GS_ATENDIMENTO : "é acionado"
```

Os pacotes e gatilhos do banco continuam valendo sobre os dados gravados pela API:
- A trilha `TRG_T_GS_AUDITORIA_OCORRENCIA` registra cada abertura, atualização e encerramento feito pela API.
- `TRG_T_GS_UPDATE_BATERIA` recalcula o score quando a API atualiza a bateria. A entidade usa `@DynamicUpdate`, então o gatilho só dispara quando `qt_bateria_min` muda de fato.

## Como rodar

Há duas formas: usar o **deploy** (já no ar, nada para instalar) ou **rodar localmente** com Docker ou Maven. Localmente, com um PostgreSQL vazio, o primeiro start cria as tabelas e carrega **os mesmos dados do script Oracle da disciplina**: 5 usuários, 5 pacientes, 5 equipamentos, 5 pontos de energia, 5 ocorrências e 5 atendimentos, com os mesmos usuários e senhas de teste.

### Deploy no Render (recomendado para testar)

A API está publicada em **https://luz-vital-api.onrender.com/swagger-ui.html**, ligada aos mesmos serviços usados no desenvolvimento:

| Peça | Serviço |
|---|---|
| API Java | Render (plano gratuito, container Docker deste repositório) |
| Banco | Oracle 19c da FIAP, só com as tabelas `T_GS_*` do script da equipe de banco |
| Mensageria | RabbitMQ gerenciado no CloudAMQP (plano gratuito), conexão `amqps://` |
| IA generativa | Gemini, via Spring AI |

**Por que o deploy:** assim o professor e o app mobile testam a mesma API, com os mesmos dados, sem instalar JDK, PostgreSQL ou RabbitMQ. A configuração está em [`render.yaml`](render.yaml). As senhas e chaves (Oracle, CloudAMQP, Gemini) ficam só no painel do Render, nunca no repositório. A chave do JWT é gerada pelo próprio Render.

> **Primeiro acesso lento:** no plano gratuito, o Render desliga a API depois de 15 minutos sem uso. A primeira chamada depois disso pode levar de 1 a 3 minutos enquanto ela liga de novo. As seguintes são rápidas.

Para testar: siga o passo a passo de [Como testar](#como-testar-recomendado-pelo-deploy-sem-instalar-nada).

### Rodar localmente, opção 1: Docker

Pré-requisito: Docker Desktop.

```bash
docker compose up --build
```

- Swagger: http://localhost:8080/swagger-ui.html
- RabbitMQ: http://localhost:15672 (guest / guest)

Para ligar a IA generativa, copie `.env.example` para `.env`, preencha `IA_API_KEY` e rode o mesmo comando. A chave do Gemini é gratuita em https://aistudio.google.com/apikey.

### Rodar localmente, opção 2: sem Docker (Maven Wrapper)

Pré-requisitos: JDK 21+, PostgreSQL 14+ e RabbitMQ 3.12+ rodando localmente.

1. Crie o banco:
   ```sql
   CREATE ROLE luzvital LOGIN PASSWORD 'luzvital';
   CREATE DATABASE luzvital OWNER luzvital;
   ```
2. (Opcional) Copie `.env.example` para `.env` e preencha `IA_API_KEY` e `JWT_SECRET`. A API lê o `.env` da pasta do projeto também fora do Docker (IntelliJ ou Maven). Uma variável de ambiente definida no sistema tem prioridade sobre o `.env`.
3. Suba a API:
   ```bash
   ./mvnw spring-boot:run
   ```
   No Windows, use `mvnw.cmd spring-boot:run`. No IntelliJ, rode a classe `LuzVitalApplication` com a pasta do projeto como diretório de trabalho.

> **Certificados no Windows:** a API usa automaticamente os certificados do Windows nas chamadas HTTPS (ex.: provedor de IA). Isso evita o erro `unable to find valid certification path` em redes ou antivírus que inspecionam HTTPS. Para desligar, defina a variável `LUZVITAL_CERTIFICADOS_WINDOWS=false`.

### Rodar localmente, opção 3: ligado ao Oracle da FIAP (banco da disciplina)

A API foi testada conectada ao **Oracle 19c da FIAP**, usando **só** as tabelas `T_GS_*` criadas pelo script da equipe de banco, sem nenhuma tabela extra e sem alterar nenhum dado ao subir:
- login dos 5 usuários da carga com as senhas gravadas no script;
- leitura dos pacientes, aparelhos (inclusive `ASPIRADOR_SECREÇÃO`), pontos, ocorrências, atendimentos com tipo acentuado e fila do gestor;
- plano de contingência gerado via RabbitMQ;
- logout com revogação do token.

Os gatilhos do banco reagem às operações de escrita da API: `TRG_T_GS_AUDITORIA_OCORRENCIA` registra cada passo e `TRG_T_GS_UPDATE_BATERIA` recalcula o score.

1. O schema Oracle precisa ter o script da disciplina de banco (`GSBancoOracle.sql`) já rodado. Nada mais é necessário.
2. No `.env`, troque as linhas do banco por estas:
   ```properties
   DB_URL=jdbc:oracle:thin:@oracle.fiap.com.br:1521:ORCL
   DB_USER=<usuário Oracle>
   DB_PASSWORD=<senha Oracle>
   JPA_DDL_AUTO=none
   ```
   Com `JPA_DDL_AUTO=none`, a API não cria nem altera nenhuma tabela no Oracle. O esquema é o do script.
3. Suba a API normalmente (IntelliJ ou `mvnw spring-boot:run`). O RabbitMQ continua sendo o local, do Docker ou do CloudAMQP.

Como o banco já tem dados, a carga inicial não roda: a API só lê e grava o que o usuário fizer pelo app ou pelo Swagger.

### Variáveis de ambiente

Nenhuma credencial real fica no repositório.

| Variável | Padrão | Para que serve |
|---|---|---|
| `JWT_SECRET` | vazio (chave aleatória a cada subida) | Chave HS256 com 32+ caracteres. **A mesma chave na API .NET** |
| `IA_API_KEY` | vazio (IA desligada) | Chave do provedor de IA. Sem ela, o plano usa as regras padrão e a leitura de etiqueta devolve 503 |
| `IA_BASE_URL` | `https://generativelanguage.googleapis.com/v1beta/openai` | Endpoint compatível com OpenAI (Gemini, `https://api.openai.com/v1`, Groq...) |
| `IA_MODELO` | `gemini-3.5-flash-lite` | Modelo. Para a foto da etiqueta, precisa aceitar imagem |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `localhost` / `5432` / `luzvital` / `luzvital` / `luzvital` | PostgreSQL |
| `DB_URL` / `JPA_DDL_AUTO` | vazio / `update` | URL JDBC completa (ex.: Oracle da FIAP, com `JPA_DDL_AUTO=none`) |
| `RABBITMQ_ADDRESSES` | `amqp://guest:guest@localhost:5672` | RabbitMQ (aceita `amqps://` do CloudAMQP) |
| `CORS_ORIGENS` | `*` | Origens liberadas (Expo web) |
| `PORT` | `8080` | Porta HTTP |

**IA testada com o Gemini real.** Os modelos `gemini-3.5-flash-lite` (padrão: plano em cerca de 15 s e leitura da etiqueta em cerca de 3 s) e `gemini-3.5-flash` foram testados com chave real. No plano gratuito, o Google limita as requisições por dia **para cada modelo**. Quando a cota acaba (erro 429), a API continua funcionando: o plano sai pelas regras padrão e a leitura de etiqueta pede o preenchimento manual. Para seguir testando, troque `IA_MODELO` para outro modelo ou aguarde a renovação diária da cota.

## Usuários de teste

São os mesmos usuários do script Oracle da disciplina de banco, com **a senha que está gravada no script** (coluna `ds_senha`). No Oracle a senha é conferida como está no script; no PostgreSQL local a carga guarda essas mesmas senhas com BCrypt. Usuários novos (`/auth/cadastro`, `/usuarios/gestores`) sempre são gravados com BCrypt.

| Papel | E-mail | Senha | Paciente e aparelho (carga do banco) |
|---|---|---|---|
| CUIDADOR | `carlos.silva@gmail.com` | `hash_carlos123` | João Carlos Silva: ventilador Philips Trilogy Evo, 900 min de bateria |
| CUIDADOR | `mariana.rocha@hotmail.com` | `hash_mariana456` | Maria Aparecida Rocha: concentrador EverFlo **sem bateria** (ocorrência aberta) |
| CUIDADOR | `roberto.ferreira@outlook.com` | `hash_roberto789` | Pedro Henrique Ferreira: só bomba de infusão, que **não** é de suporte à vida (teste negativo de abertura) |
| GESTOR | `luciana.mendes@defesacivil.sp.gov.br` | `hash_luciana_adm` | Defesa Civil: vê a fila, registra atendimentos, gerencia pontos e gestores |
| GESTOR | `fernando.souza@enel.com.br` | `hash_enel_gestor` | Distribuidora: mesmo acesso de gestor |

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
| `GET /ocorrencias`, `GET /ocorrencias/{id}` | dono · GESTOR | Ocorrências com contagem regressiva, atendimentos e pontos avisados |
| `GET /ocorrencias/fila` | **GESTOR** | **Fila ordenada pelos minutos de bateria restantes** |
| `PATCH /ocorrencias/{id}/bateria` | dono · GESTOR | Informa a bateria atual e recalcula a prioridade |
| `POST /ocorrencias/{id}/atendimentos` | **GESTOR** | Registra o socorro: `GERADOR_MOVEL`, `RECARGA_BATERIA`, `REMOCAO_UTI` ou `RELIGACAO_PRIORITARIA` |
| `POST /ocorrencias/{id}/encerramento` | dono · GESTOR | Encerra (ATENDIDO), anexando o desfecho à observação como `PR_T_GS_ENCERRAR_OCORRENCIA` |
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

Vale para o deploy e para a execução local: os ids são os mesmos da carga do banco (pacientes 1 = João, 2 = Maria, 3 = Pedro; ocorrência 1 = Maria; ponto 2 = Padaria Central Novo Milênio). Para o login no Swagger, veja [Como testar](#como-testar-recomendado-pelo-deploy-sem-instalar-nada).

1. Login como **gestora** (`luciana.mendes@defesacivil.sp.gov.br` / `hash_luciana_adm`) e `GET /ocorrencias/fila`.
   - As 3 ocorrências ativas da carga aparecem ordenadas pelos minutos de bateria restantes. No deploy, a carga do Oracle foi feita há dias e as três já estão com 0 minuto: o desempate pelo aparelho coloca José (ventilador) à frente de Maria (concentrador). Num PostgreSQL recém-carregado, a ordem é Maria, Ana e José.
   - Todas vêm com `priorizada: true`, com o score gravado na carga do banco.
2. Login como **Mariana** e `GET /ocorrencias/1`: o atendimento `GERADOR_MOVEL` da carga aparece. Os `pontosNotificados` são preenchidos pelo consumidor do RabbitMQ nas ocorrências abertas pela API (passo 6).
3. Login como **Carlos** (`carlos.silva@gmail.com` / `hash_carlos123`) e `GET /pacientes`: aparece o João (e os pacientes que você cadastrar). Em `GET /pacientes/2` (paciente da Mariana), a resposta é **403**.
4. `GET /pacientes/1/plano-contingencia`: a primeira consulta devolve `GERANDO` e publica `plano.solicitado`. Alguns segundos depois, a mesma consulta traz o plano `PRONTO`, com `origem` igual a `IA` quando há chave, ou `REGRAS` quando não há.
5. `POST /equipamentos` com `potenciaWatts: 0` devolve **400** com os campos inválidos.
6. `POST /ocorrencias` com `{"pacienteId": 1}` e um UUID qualquer em `Idempotency-Key` (ex.: `3f1c2b9e-8a4d-4c6e-9f00-1b2c3d4e5f60`) devolve **201**.
   - Se voltar **409** logo na primeira vez, alguém já abriu uma ocorrência do João no deploy: cancele-a com `POST /ocorrencias/{id}/cancelamento` (o id está em `GET /ocorrencias?pacienteId=1&status=EM_ABERTO`) e repita.
   - Repetir com a mesma chave devolve **200** com a mesma ocorrência.
   - Repetir sem chave devolve **409**.
   - `GET /ocorrencias/{id}` mostra em `pontosNotificados` a Padaria Central Novo Milênio, ponto do bairro Jardim Paulista avisado pelo consumidor de `energia.caiu`.
7. `PATCH /ocorrencias/{id}/bateria` com `{"minutosRestantes": 45}` devolve o score 1015 e reposiciona o João na fila pelos 45 minutos restantes.
8. Como gestora, `POST /ocorrencias/{id}/atendimentos` com `{"tipoSocorro":"REMOCAO_UTI","descricao":"...","pontoEnergiaId":2}` muda o status para `EM_ATENDIMENTO`.
9. `POST /ocorrencias/{id}/encerramento` devolve 200. Encerrar de novo devolve **409**.
10. Login como **Roberto** e `POST /ocorrencias` com `{"pacienteId": 3}` devolve **422** (nenhum aparelho de suporte à vida).
11. Como Carlos, `GET /ocorrencias/fila` devolve **403**.
12. RabbitMQ: no deploy, a prova do processamento assíncrono são os `pontosNotificados` do passo 6 (consumidor de `energia.caiu`) e o plano que passa de `GERANDO` para `PRONTO` no passo 4 (consumidor de `plano.solicitado`). Rodando localmente, o painel http://localhost:15672 (guest / guest) mostra em *Queues* as filas `luzvital.energia-caiu` e `luzvital.plano-contingencia` com consumidores.

## Como cada requisito foi atendido

| # | Requisito | Onde está |
|---|---|---|
| 01 | API REST, 3+ entidades relacionadas, CRUD, paginação | 6 entidades mapeadas nas tabelas `T_GS_*` do banco da disciplina (pacote `domain`): Usuário 1:N Paciente 1:N Equipamento; Paciente 1:N Ocorrência 1:N Atendimento N:1 Ponto de energia. CRUD completo de pacientes, equipamentos e pontos; todas as listagens paginadas |
| 02 | Bean Validation nos DTOs + `@RestControllerAdvice` | Records no pacote `dto` (CPF, CEP, UF, telefone, e-mail, potência > 0, autonomia ≥ 0, tipos de socorro da constraint...). `exception/GlobalExceptionHandler` e `security/ErroSegurancaHandler` (401/403 no mesmo formato) |
| 03 | Spring Security + JWT, 2 papéis, rotas por papel | `config/SecurityConfig` (regras por rota + `@PreAuthorize`), `security/JwtService`. O cuidador só acessa os próprios pacientes (`service/Acesso`). Usuário inativo (`st_usuario = 'N'`) não entra. Logout revoga o token |
| 04 | Spring AI integrado ao fluxo | `ia/IaService`: (1) **plano de contingência** gerado ao salvar um aparelho, com saída estruturada em `PlanoGerado` (etapas por faixa de tempo, contatos, sinais de alerta); (2) **leitura da etiqueta por foto** (multimodal), com potência estimada por V × A. Guarda-corpos: sem orientação clínica, sem nome ou CPF no prompt (LGPD), 192 e 193 sempre presentes, e plano por regras quando a IA falha |
| 05 | Arquitetura distribuída: **opção A (RabbitMQ)** | `messaging/RabbitConfig` (exchange topic, filas, DLQ), `EventoPublisher` (pós-commit), `EventoConsumidor` (`energia.caiu` e `plano.solicitado`), `ReprocessamentoJob` |
| 06 | Swagger, README e integração com o Mobile | `/swagger-ui.html` com Bearer configurado, este README e a seção de integração abaixo |

## Integração com o app Mobile e com a API .NET

**App React Native (Axios)**
- Cabeçalho: `Authorization: Bearer <token>` (o token vem de `/auth/login` ou de `/auth/cadastro`).
- URL base: use o deploy, `https://luz-vital-api.onrender.com`. Rodando a API localmente, no emulador Android use `http://10.0.2.2:8080`; no celular físico, use o IP da máquina na rede (`http://192.168.x.x:8080`).
- Paciente: `{nome, cpf, endereco, bairro, cidade, uf, cep, telefone}`.
- Aparelho: `{pacienteId, tipoAparelho, fabricante, modelo, potenciaWatts, autonomiaMinutos, suporteVida, fonteReserva}`.
- "Estou sem luz": gere um UUID por toque, guarde-o na fila offline e reenvie **com a mesma** `Idempotency-Key`. Assim o reenvio nunca duplica a ocorrência.
- Plano de contingência: `GET /pacientes/{id}/plano-contingencia`. Se o `status` for `GERANDO`, consulte de novo em alguns segundos. Salve o plano no aparelho para uso offline.
- Cadastro por foto: envie `multipart/form-data` com o campo `foto` (JPEG, PNG ou WEBP, até 5 MB), mostre os dados para o cuidador confirmar e depois chame `POST /equipamentos`.

**API .NET (mesmo JWT)**
- Algoritmo HS256 com a chave `JWT_SECRET` (a mesma nas duas APIs). Valide `iss = luz-vital-api` e `aud = luz-vital-app`.
- Claims: `sub` (id do usuário em `T_GS_USUARIO`), `email`, `nome`, `role` (`CUIDADOR` ou `GESTOR`), `jti`, `exp`.
- Para avisar a API Java de uma ocorrência, chame `POST /ocorrencias` repassando o token do usuário e a mesma `Idempotency-Key`. A Java publica `energia.caiu`.
- Health check: `GET /actuator/health`.

## Testes automatizados

```bash
./mvnw test
```

São 33 testes:
- **Regras:** score igual ao de `FN_T_GS_CALCULA_PRIORIDADE`, fila, contagem regressiva e códigos gravados no banco (constraints com acento).
- **Etiqueta:** potência estimada por V × A.
- **Spring AI:** `ChatModel` simulado, com saída estruturada, guarda-corpos e nova tentativa quando a resposta vem cortada.
- **Integração com MockMvc:** 401/403 no formato padrão, validação, publicação de `energia.caiu`, idempotência, 409 e 422.
- **`OracleModeloBancoTest`:** roda a API sobre **o DDL e a carga originais do `GSBancoOracle.sql`** (H2 em modo Oracle, `ddl-auto=none`), sem nenhuma tabela extra. Prova que a subida não grava nada e que login com as senhas do script, fila, atendimentos com constraint, bateria, idempotência, CRUD, plano e logout funcionam no modelo do banco sem alterá-lo.

Os testes usam H2 em memória e não precisam de Docker.

## Declaração de uso de IA

- **No produto:** a API usa IA generativa via Spring AI para gerar o plano de contingência e ler a etiqueta do aparelho por foto. A IA não dá orientação clínica e o cuidador sempre confirma os dados lidos. Sem chave configurada, a API continua funcionando com o plano padrão por regras.
- **No desenvolvimento:** usamos assistente de IA generativa (Claude, da Anthropic) como apoio na implementação, na revisão do código, nos testes e na documentação. Todo o conteúdo foi revisado, executado e testado pela equipe, que é responsável pelas decisões de projeto e pela entrega.

## Riscos e cuidados

- **LGPD:** saúde e endereço são dados sensíveis. Use só dados fictícios. O prompt da IA não recebe nome, CPF nem endereço do paciente.
- **A regra de prioridade é uma hipótese de projeto**, a validar com profissional de saúde. Não é um protocolo clínico.
- **A leitura por foto pode errar.** O app sempre pede confirmação e aceita digitação manual.
