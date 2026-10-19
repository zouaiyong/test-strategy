package demo.fee.service;

import demo.fee.vo.StorageFeeVO;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/** 按 StorageFeeVO 的 @ExcelProperty 表头导出；省略 EasyExcel 细节。 */
public class StorageFeeExporter {
    public void export(List<StorageFeeVO> rows, HttpServletResponse response) {
        // EasyExcel.write(response.getOutputStream(), StorageFeeVO.class).sheet("存储费").doWrite(rows);
    }
}
