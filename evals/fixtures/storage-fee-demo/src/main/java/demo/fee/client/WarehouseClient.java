package demo.fee.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 基础数据服务：仓库主数据。页面「仓库」下拉框与保存校验都会打这个服务。 */
@FeignClient(name = "wms-base", path = "/warehouse")
public interface WarehouseClient {

    @GetMapping("/exists")
    boolean exists(@RequestParam("code") String warehouseCode);

    @GetMapping("/options")
    List<WarehouseOption> options();

    class WarehouseOption {
        public String code;
        public String name;
    }
}
