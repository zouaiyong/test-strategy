package demo.fee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import demo.fee.entity.StorageFee;
import demo.fee.vo.StorageFeeVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface StorageFeeMapper extends BaseMapper<StorageFee> {

    List<StorageFeeVO> selectMonthlySummary(@Param("warehouseCode") String warehouseCode,
                                            @Param("billMonth") String billMonth);

    /** 结算 Job 用：把指定月份所有已确认记录批量置为已结算。 */
    @Update("UPDATE storage_fee SET status = 'SETTLED', settle_time = NOW() " +
            "WHERE bill_month = #{billMonth} AND status = 'CONFIRMED' AND is_deleted = 0")
    int settleByMonth(@Param("billMonth") String billMonth);
}
