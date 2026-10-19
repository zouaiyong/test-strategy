package demo.fee.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import demo.fee.entity.StorageFeeDetail;
import demo.fee.mapper.StorageFeeDetailMapper;
import org.springframework.stereotype.Service;

@Service
public class StorageFeeDetailService extends ServiceImpl<StorageFeeDetailMapper, StorageFeeDetail> {
}
