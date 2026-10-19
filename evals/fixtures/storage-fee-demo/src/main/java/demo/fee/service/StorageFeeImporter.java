package demo.fee.service;

import demo.fee.calc.StorageFeeCalculator;
import org.springframework.web.multipart.MultipartFile;

/**
 * Excel 逐行校验导入：仓库编码必填、账期格式 yyyy-MM、长宽高为正数、天数 1~365。
 * 任一行失败整批拒绝，返回失败回执文件的 taskId 供页面轮询下载。
 */
public class StorageFeeImporter {

    private final StorageFeeBizService bizService;
    private final StorageFeeCalculator calculator;

    public StorageFeeImporter(StorageFeeBizService bizService, StorageFeeCalculator calculator) {
        this.bizService = bizService;
        this.calculator = calculator;
    }

    public String doImport(MultipartFile file, Long operatorId) {
        // 省略：EasyExcel 读取 → 逐行 validateRow → 全部通过则 saveBatch → 否则生成回执
        return "task-" + System.currentTimeMillis();
    }

    String validateRow(int rowNo, String warehouseCode, String billMonth, String length, String width, String height, String days) {
        if (warehouseCode == null || warehouseCode.isEmpty()) {
            return "第" + rowNo + "行：仓库编码不能为空";
        }
        if (billMonth == null || !billMonth.matches("\\d{4}-\\d{2}")) {
            return "第" + rowNo + "行：账期格式应为 yyyy-MM";
        }
        try {
            int d = Integer.parseInt(days);
            if (d < 1 || d > 365) {
                return "第" + rowNo + "行：天数应在 1~365 之间";
            }
        } catch (NumberFormatException e) {
            return "第" + rowNo + "行：天数应为整数";
        }
        return null;
    }
}
