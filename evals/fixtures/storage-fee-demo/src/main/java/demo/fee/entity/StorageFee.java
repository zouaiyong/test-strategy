package demo.fee.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import demo.fee.enums.StorageFeeStatusEnum;
import demo.fee.enums.StorageFeeTypeEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 建表见 doc/sql/storage_fee.sql：uk_warehouse_month(warehouse_code, bill_month)，不含 is_deleted。 */
@Data
@TableName("storage_fee")
public class StorageFee {
    @TableId
    private Long id;
    private String warehouseCode;
    private String warehouseName;
    /** yyyy-MM */
    private String billMonth;
    private StorageFeeTypeEnum type;
    private BigDecimal amount;
    private BigDecimal confirmAmount;
    private Long confirmBy;
    private StorageFeeStatusEnum status;
    private Long operatorId;
    private LocalDateTime settleTime;
    @TableLogic
    private Integer isDeleted;
}
