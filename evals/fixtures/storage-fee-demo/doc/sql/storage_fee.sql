CREATE TABLE storage_fee
(
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    warehouse_code VARCHAR(32)    NOT NULL,
    warehouse_name VARCHAR(64),
    bill_month     CHAR(7)        NOT NULL COMMENT 'yyyy-MM',
    type           VARCHAR(16)    NOT NULL,
    amount         DECIMAL(12, 2),
    confirm_amount DECIMAL(12, 2),
    confirm_by     BIGINT,
    status         VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
    operator_id    BIGINT,
    settle_time    DATETIME,
    is_deleted     TINYINT        NOT NULL DEFAULT 0,
    UNIQUE KEY uk_warehouse_month (warehouse_code, bill_month)
) COMMENT '存储费';

CREATE TABLE storage_fee_detail
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    fee_id     BIGINT      NOT NULL,
    sku_code   VARCHAR(64) NOT NULL,
    qty        INT         NOT NULL,
    amount     DECIMAL(12, 2),
    is_deleted TINYINT     NOT NULL DEFAULT 0,
    KEY idx_fee_id (fee_id)
) COMMENT '存储费明细';

CREATE TABLE warehouse
(
    warehouse_code VARCHAR(32) PRIMARY KEY,
    warehouse_name VARCHAR(64) NOT NULL,
    is_deleted     TINYINT     NOT NULL DEFAULT 0
) COMMENT '仓库主数据（来自 wms-base，本库只读镜像）';
