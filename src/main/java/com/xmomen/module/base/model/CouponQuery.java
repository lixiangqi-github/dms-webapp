package com.xmomen.module.base.model;

import java.io.Serializable;

import lombok.Data;

/**
 * Created by Jeng on 2016/3/30.
 */
public @Data 
class CouponQuery implements Serializable {

    private String keyword;
    private String couponNumber;
    private String password;
    private String couponType;
    private Integer couponCategoryId;
    private Integer categoryType;
    private Integer isSend;
    private Integer cdCompanyId;
    private Integer customerMangerId;
    private Integer isUseful;
    private Integer isOver;
    private Integer managerId;
    private String batch;
}