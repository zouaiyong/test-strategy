package demo.fee.enums;

/** 待确认 → 已确认 → 已结算；已结算为终态，页面上删除/反审按钮应置灰。 */
public enum StorageFeeStatusEnum {
    PENDING("待确认"),
    CONFIRMED("已确认"),
    SETTLED("已结算");

    private final String label;

    StorageFeeStatusEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
