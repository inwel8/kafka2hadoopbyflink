package com.company.bigdata.flinkciscodpi;

public enum Topic {
    FLOW_NW("prod_nw_cisco_flow"),
    FLOW_MSK("prod_msk_cisco_flow"),
    FLOW_UG("prod_ug_cisco_flow"),
    FLOW_PV("prod_pv_cisco_flow"),
    FLOW_URAL("prod_ural_cisco_flow"),
    FLOW_SIB("prod_sib_cisco_flow"),
    FLOW_DV("prod_dv_cisco_flow"),
    HTTP_DV("prod_dv_cisco_http");

    private String code;

    Topic(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
