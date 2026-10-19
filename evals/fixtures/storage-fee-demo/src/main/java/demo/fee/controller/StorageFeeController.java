package demo.fee.controller;

import demo.fee.po.StorageFeeSavePO;
import demo.fee.po.StorageFeeSearchPO;
import demo.fee.service.StorageFeeBizService;
import demo.fee.vo.StorageFeeVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 存储费管理。所有接口均为纯透传：参数校验注解 + 注入登录人 + 一行委托。
 */
@Api(tags = "存储费管理")
@RestController
@RequestMapping("/storageFee")
public class StorageFeeController {

    private final StorageFeeBizService bizService;

    public StorageFeeController(StorageFeeBizService bizService) {
        this.bizService = bizService;
    }

    @ApiOperation("分页查询存储费")
    @PostMapping("/list")
    public Result<List<StorageFeeVO>> list(@RequestBody StorageFeeSearchPO po) {
        return Result.success(bizService.list(po));
    }

    @ApiOperation("月度汇总报表")
    @PostMapping("/monthlySummary")
    public Result<List<StorageFeeVO>> monthlySummary(@RequestBody StorageFeeSearchPO po) {
        return Result.success(bizService.monthlySummary(po));
    }

    @ApiOperation("新增/编辑存储费配置")
    @PostMapping("/save")
    public Result<Long> save(@RequestBody @Validated StorageFeeSavePO po) {
        return Result.success(bizService.save(po, CurrentUser.id()));
    }

    @ApiOperation("删除存储费配置")
    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam Long id) {
        bizService.delete(id, CurrentUser.id());
        return Result.success();
    }

    @ApiOperation("反审（撤销确认）")
    @PostMapping("/reverse")
    public Result<Void> reverse(@RequestParam Long id) {
        bizService.reverse(id, CurrentUser.id());
        return Result.success();
    }

    @ApiOperation("导入存储费（Excel）")
    @PostMapping("/import")
    public Result<String> importExcel(@RequestParam MultipartFile file) {
        return Result.success(bizService.importExcel(file, CurrentUser.id()));
    }

    @ApiOperation("导出存储费（Excel）")
    @GetMapping("/export")
    public void export(StorageFeeSearchPO po, HttpServletResponse response) {
        bizService.export(po, response);
    }

    // TODO 批量确认接口，产品尚未定稿「部分成功」时的提示文案，先不放出
    // @ApiOperation("批量确认")
    // @PostMapping("/batchConfirm")
    // public Result<Void> batchConfirm(@RequestBody List<Long> ids) {
    //     bizService.batchConfirm(ids, CurrentUser.id());
    //     return Result.success();
    // }
}
