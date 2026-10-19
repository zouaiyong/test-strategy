package demo.fee.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import demo.fee.mapper.StorageFeeMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 每月 1 日凌晨把上个月所有已确认的存储费置为已结算。
 * 参数为空时取上月；传 yyyy-MM 时按参数月份结算。
 */
@Component
public class StorageFeeSettleJob {

    private final StorageFeeMapper mapper;

    public StorageFeeSettleJob(StorageFeeMapper mapper) {
        this.mapper = mapper;
    }

    @XxlJob("storageFeeSettleHandler")
    public void settle(String param) {
        String billMonth = (param == null || param.isEmpty())
                ? LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"))
                : param;
        mapper.settleByMonth(billMonth);
    }
}
