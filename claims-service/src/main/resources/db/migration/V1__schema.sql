-- Members and plans live in MySQL (relational: a member belongs to one plan; claims reference members).
CREATE TABLE plans (
    id                  BIGINT PRIMARY KEY,
    code                VARCHAR(20)    NOT NULL UNIQUE,
    name                VARCHAR(100)   NOT NULL,
    deductible          DECIMAL(12, 2) NOT NULL,
    coinsurance_percent INT            NOT NULL,
    annual_max          DECIMAL(12, 2) NOT NULL,
    covered_services    VARCHAR(200)   NOT NULL
);

CREATE TABLE members (
    id              BIGINT PRIMARY KEY,
    member_number   VARCHAR(20)    NOT NULL UNIQUE,
    full_name       VARCHAR(100)   NOT NULL,
    plan_id         BIGINT         NOT NULL,
    coverage_start  DATE           NOT NULL,
    coverage_end    DATE,
    deductible_met  DECIMAL(12, 2) NOT NULL,
    benefits_used   DECIMAL(12, 2) NOT NULL,
    version         BIGINT         NOT NULL,
    CONSTRAINT fk_members_plan FOREIGN KEY (plan_id) REFERENCES plans (id)
);

CREATE TABLE claims (
    id                 VARCHAR(20)    PRIMARY KEY,
    member_id          BIGINT         NOT NULL,
    service_type       VARCHAR(20)    NOT NULL,
    service_date       DATE           NOT NULL,
    provider_name      VARCHAR(120)   NOT NULL,
    billed_amount      DECIMAL(12, 2) NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    plan_pays          DECIMAL(12, 2),
    member_pays        DECIMAL(12, 2),
    deductible_applied DECIMAL(12, 2),
    reason_codes       VARCHAR(200),
    submitted_at       TIMESTAMP      NOT NULL,
    decided_at         TIMESTAMP,
    version            BIGINT         NOT NULL,
    CONSTRAINT fk_claims_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE INDEX ix_claims_member_submitted ON claims (member_id, submitted_at);
CREATE INDEX ix_claims_status ON claims (status);
