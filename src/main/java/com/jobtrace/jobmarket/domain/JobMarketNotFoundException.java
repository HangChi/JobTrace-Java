package com.jobtrace.jobmarket.domain;

public final class JobMarketNotFoundException extends RuntimeException {

    public JobMarketNotFoundException() {
        super("没有找到这条招聘记录。");
    }
}
