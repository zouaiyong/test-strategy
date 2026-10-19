package demo.fee.po;

import demo.fee.entity.StorageFee;
import demo.fee.entity.StorageFeeDetail;
import demo.fee.enums.StorageFeeTypeEnum;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;

@Data
public class StorageFeeSavePO {
    private Long id;
    @NotBlank(message = "仓库编码不能为空")
    private String warehouseCode;
    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "账期格式应为 yyyy-MM")
    private String billMonth;
    @NotNull
    private StorageFeeTypeEnum type;
    @NotNull
    private BigDecimal length;
    @NotNull
    private BigDecimal width;
    @NotNull
    private BigDecimal height;
    @NotNull
    private Integer days;

    /** 仅 type = AUXILIARY 时必填，前端应随类型切换显隐 */
    private BigDecimal fixedArea;
    /** 仅 type = B2C 时必填 */
    private BigDecimal firstPieceFee;
    private BigDecimal nextPieceFee;

    private List<DetailPO> details;

    public StorageFee toEntity() {
        StorageFee fee = new StorageFee();
        fee.setId(id);
        fee.setWarehouseCode(warehouseCode);
        fee.setBillMonth(billMonth);
        fee.setType(type);
        return fee;
    }

    @Data
    public static class DetailPO {
        @NotBlank
        private String skuCode;
        @NotNull
        private Integer qty;

        public StorageFeeDetail toEntity(Long feeId) {
            StorageFeeDetail d = new StorageFeeDetail();
            d.setFeeId(feeId);
            d.setSkuCode(skuCode);
            d.setQty(qty);
            return d;
        }
    }
}
