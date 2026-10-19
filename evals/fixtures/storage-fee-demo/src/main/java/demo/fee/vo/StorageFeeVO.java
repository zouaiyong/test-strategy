package demo.fee.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import demo.fee.entity.StorageFee;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StorageFeeVO {
    @ExcelProperty("仓库编码")
    private String warehouseCode;
    @ExcelProperty("仓库名称")
    private String warehouseName;
    @ExcelProperty("账期")
    private String billMonth;
    @ExcelProperty("存储费(元)")
    private BigDecimal amount;
    @ExcelProperty("确认金额(元)")
    private BigDecimal confirmAmount;
    @ExcelProperty("SKU 数")
    private Integer skuCount;
    @ExcelProperty("状态")
    private String statusLabel;
    @ExcelProperty("结算时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime settleTime;

    public static StorageFeeVO of(StorageFee fee) {
        StorageFeeVO vo = new StorageFeeVO();
        vo.setWarehouseCode(fee.getWarehouseCode());
        vo.setWarehouseName(fee.getWarehouseName());
        vo.setBillMonth(fee.getBillMonth());
        vo.setAmount(fee.getAmount());
        vo.setConfirmAmount(fee.getConfirmAmount());
        vo.setStatusLabel(fee.getStatus() == null ? null : fee.getStatus().getLabel());
        vo.setSettleTime(fee.getSettleTime());
        return vo;
    }
}
