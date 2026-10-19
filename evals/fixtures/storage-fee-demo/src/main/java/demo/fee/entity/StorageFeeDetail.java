package demo.fee.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("storage_fee_detail")
public class StorageFeeDetail {
    @TableId
    private Long id;
    private Long feeId;
    private String skuCode;
    private Integer qty;
    private BigDecimal amount;
    @TableLogic
    private Integer isDeleted;
}
