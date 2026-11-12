-- =====================================================================
--  Luz Vital - tabelas de apoio da API Java (Oracle)
--  Rodar DEPOIS do script da disciplina de banco (GSBancoOracle.sql).
--
--  A API Java usa as tabelas do modelo da disciplina SEM ALTERAÇÃO:
--  T_GS_USUARIO, T_GS_PACIENTE, T_GS_EQUIPAMENTO, T_GS_PONTO_ENERGIA,
--  T_GS_OCORRENCIA e T_GS_ATENDIMENTO (os gatilhos e pacotes continuam
--  funcionando sobre os dados gravados pela API).
--
--  Estas 4 tabelas guardam só o que é exclusivo do backend e seguem a
--  mesma convenção de nomes (T_GS_, id_, ds_, dh_, st_, tp_, nm_).
-- =====================================================================

-- 1. Controle da ocorrência (1:1 com T_GS_OCORRENCIA)
--    ds_chave_idempotencia: cabeçalho Idempotency-Key do app ("Estou sem luz" reenviado não duplica)
--    dh_bateria: quando qt_bateria_min foi informada (contagem regressiva correta após atualizar a bateria)
--    dh_priorizacao: quando o consumidor do RabbitMQ (evento energia.caiu) priorizou a ocorrência
CREATE TABLE T_GS_OCORRENCIA_CONTROLE (
    id_ocorrencia          NUMBER NOT NULL,
    ds_chave_idempotencia  VARCHAR2(100),
    dh_bateria             TIMESTAMP NOT NULL,
    dh_priorizacao         TIMESTAMP,
    CONSTRAINT PK_T_GS_OCO_CONTROLE PRIMARY KEY (id_ocorrencia),
    CONSTRAINT UK_T_GS_OCO_CTRL_CHAVE UNIQUE (ds_chave_idempotencia),
    CONSTRAINT FK_T_GS_OCO_CTRL_OCORRENCIA FOREIGN KEY (id_ocorrencia)
        REFERENCES T_GS_OCORRENCIA (id_ocorrencia)
);

-- 2. Avisos aos pontos de energia do bairro (gravados pelo consumidor do evento energia.caiu)
CREATE TABLE T_GS_NOTIFICACAO_PONTO (
    id_notificacao  NUMBER GENERATED ALWAYS AS IDENTITY,
    id_ocorrencia   NUMBER NOT NULL,
    id_ponto        NUMBER NOT NULL,
    ds_mensagem     VARCHAR2(300) NOT NULL,
    dh_envio        TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_T_GS_NOTIFICACAO_PONTO PRIMARY KEY (id_notificacao),
    CONSTRAINT UK_T_GS_NOTIF_OCO_PONTO UNIQUE (id_ocorrencia, id_ponto),
    CONSTRAINT FK_T_GS_NOTIF_OCORRENCIA FOREIGN KEY (id_ocorrencia)
        REFERENCES T_GS_OCORRENCIA (id_ocorrencia),
    CONSTRAINT FK_T_GS_NOTIF_PONTO FOREIGN KEY (id_ponto)
        REFERENCES T_GS_PONTO_ENERGIA (id_ponto)
);

-- 3. Plano de contingência gerado pela IA (Spring AI), 1 por paciente
CREATE TABLE T_GS_PLANO_CONTINGENCIA (
    id_plano        NUMBER GENERATED ALWAYS AS IDENTITY,
    id_paciente     NUMBER NOT NULL,
    st_plano        VARCHAR2(10) NOT NULL,
    tp_origem       VARCHAR2(10),
    ds_conteudo     CLOB,
    nm_modelo_ia    VARCHAR2(80),
    ds_mensagem     VARCHAR2(300),
    dh_solicitacao  TIMESTAMP NOT NULL,
    dh_geracao      TIMESTAMP,
    CONSTRAINT PK_T_GS_PLANO_CONTINGENCIA PRIMARY KEY (id_plano),
    CONSTRAINT UK_T_GS_PLANO_PACIENTE UNIQUE (id_paciente),
    CONSTRAINT FK_T_GS_PLANO_PACIENTE FOREIGN KEY (id_paciente)
        REFERENCES T_GS_PACIENTE (id_paciente),
    CONSTRAINT CK_T_GS_PLANO_STATUS CHECK (st_plano IN ('GERANDO','PRONTO','ERRO')),
    CONSTRAINT CK_T_GS_PLANO_ORIGEM CHECK (tp_origem IN ('IA','REGRAS'))
);

-- 4. Tokens JWT revogados no logout
CREATE TABLE T_GS_TOKEN_REVOGADO (
    id_jti        VARCHAR2(64) NOT NULL,
    dh_expiracao  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_T_GS_TOKEN_REVOGADO PRIMARY KEY (id_jti)
);
