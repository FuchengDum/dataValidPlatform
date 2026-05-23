DROP TABLE IF EXISTS "contract_bill";
DROP TABLE IF EXISTS payment;

CREATE TABLE "contract_bill" (
    "bill_id" VARCHAR(20) PRIMARY KEY,
    "contract_amount" DECIMAL(12,2),
    "discount_amount" DECIMAL(12,2),
    "recv" DECIMAL(12,2)
);

CREATE TABLE payment (
    payment_id VARCHAR(20) PRIMARY KEY,
    bill_id VARCHAR(20),
    paid_amount DECIMAL(12,2)
);

INSERT INTO "contract_bill" ("bill_id", "contract_amount", "discount_amount", "recv")
VALUES ('B001', 1000, 80, 920);

INSERT INTO "contract_bill" ("bill_id", "contract_amount", "discount_amount", "recv")
VALUES ('B002', 1000, 80, 950);

INSERT INTO payment (payment_id, bill_id, paid_amount)
VALUES ('P001', 'B001', 920);
