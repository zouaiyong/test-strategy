package demo.fee.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import demo.fee.calc.StorageFeeCalculator;
import demo.fee.client.WarehouseClient;
import demo.fee.entity.StorageFee;
import demo.fee.entity.StorageFeeDetail;
import demo.fee.enums.StorageFeeStatusEnum;
import demo.fee.enums.StorageFeeTypeEnum;
import demo.fee.mapper.StorageFeeMapper;
import demo.fee.po.StorageFeeSavePO;
import demo.fee.po.StorageFeeSearchPO;
import demo.fee.vo.StorageFeeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StorageFeeBizService extends ServiceImpl<StorageFeeMapper, StorageFee> {

    private final StorageFeeCalculator calculator;
    private final StorageFeeDetailService detailService;
    private final WarehouseClient warehouseClient;

    public StorageFeeBizService(StorageFeeCalculator calculator,
                                StorageFeeDetailService detailService,
                                WarehouseClient warehouseClient) {
        this.calculator = calculator;
        this.detailService = detailService;
        this.warehouseClient = warehouseClient;
    }

    /** 列表查询：关键字同时匹配仓库编码或仓库名称。 */
    public List<StorageFeeVO> list(StorageFeeSearchPO po) {
        // 注意：这里的 .or() 没有包进 .and(w -> ...)，会把前面的 warehouse_code / is_deleted 条件短路掉
        List<StorageFee> rows = lambdaQuery()
                .eq(po.getWarehouseCode() != null, StorageFee::getWarehouseCode, po.getWarehouseCode())
                .eq(po.getBillMonth() != null, StorageFee::getBillMonth, po.getBillMonth())
                .like(po.getKeyword() != null, StorageFee::getWarehouseCode, po.getKeyword())
                .or()
                .like(po.getKeyword() != null, StorageFee::getWarehouseName, po.getKeyword())
                .last("LIMIT 500")
                .list();
        return rows.stream().map(StorageFeeVO::of).collect(Collectors.toList());
    }

    /** 月度汇总报表：全部逻辑在手写 XML 里。 */
    public List<StorageFeeVO> monthlySummary(StorageFeeSearchPO po) {
        return baseMapper.selectMonthlySummary(po.getWarehouseCode(), po.getBillMonth());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(StorageFeeSavePO po, Long operatorId) {
        validateByType(po);
        if (!warehouseClient.exists(po.getWarehouseCode())) {
            throw new BizFailException("仓库不存在：" + po.getWarehouseCode());
        }
        StorageFee fee = po.toEntity();
        fee.setAmount(calculator.calc(po.getType(), po.getLength(), po.getWidth(), po.getHeight(), po.getDays()));
        fee.setStatus(StorageFeeStatusEnum.PENDING);
        fee.setOperatorId(operatorId);
        saveOrUpdate(fee);

        // 主子表：先删子表再批量插入
        detailService.remove(detailService.lambdaQuery().eq(StorageFeeDetail::getFeeId, fee.getId()).getWrapper());
        List<StorageFeeDetail> details = po.getDetails().stream()
                .map(d -> d.toEntity(fee.getId()))
                .collect(Collectors.toList());
        detailService.saveBatch(details);
        return fee.getId();
    }

    /** 按类型做条件校验：辅料必须填固定面积；B2C 必须填首件/续件单价。 */
    private void validateByType(StorageFeeSavePO po) {
        if (po.getType() == StorageFeeTypeEnum.AUXILIARY) {
            if (po.getFixedArea() == null || po.getFixedArea().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizFailException("辅料类型必须填写固定面积");
            }
        } else if (po.getType() == StorageFeeTypeEnum.B2C) {
            if (po.getFirstPieceFee() == null || po.getNextPieceFee() == null) {
                throw new BizFailException("B2C 类型必须填写首件和续件单价");
            }
        }
    }

    public void delete(Long id, Long operatorId) {
        StorageFee fee = getById(id);
        if (fee == null) {
            throw new BizFailException("记录不存在");
        }
        if (fee.getStatus() != StorageFeeStatusEnum.PENDING) {
            throw new BizFailException("仅待确认状态可删除");
        }
        // @TableLogic 逻辑删除；表上有 uk_warehouse_month(warehouse_code, bill_month) 且不含 is_deleted
        removeById(id);
    }

    /** 反审：把确认金额与确认人置空，状态退回待确认。 */
    public void reverse(Long id, Long operatorId) {
        StorageFee fee = getById(id);
        if (fee == null || fee.getStatus() != StorageFeeStatusEnum.CONFIRMED) {
            throw new BizFailException("仅已确认状态可反审");
        }
        lambdaUpdate()
                .eq(StorageFee::getId, id)
                .set(StorageFee::getConfirmAmount, null)
                .set(StorageFee::getConfirmBy, null)
                .set(StorageFee::getStatus, StorageFeeStatusEnum.PENDING)
                .update();
    }

    /** Excel 导入：逐行校验，任一行失败则整批拒绝并返回失败回执。 */
    public String importExcel(MultipartFile file, Long operatorId) {
        return new StorageFeeImporter(this, calculator).doImport(file, operatorId);
    }

    public void export(StorageFeeSearchPO po, HttpServletResponse response) {
        new StorageFeeExporter().export(list(po), response);
    }
}
