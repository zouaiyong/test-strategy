package demo.fee.po;

import lombok.Data;

/**
 * 查询条件。billMonth 后端不兜底：传 null 就查全部月份，
 * 业务上页面打开应默认选中「上个月」，由前端负责带参数。
 */
@Data
public class StorageFeeSearchPO {
    private String warehouseCode;
    private String billMonth;
    private String keyword;
    private Integer pageNo = 1;
    private Integer pageSize = 20;
}
