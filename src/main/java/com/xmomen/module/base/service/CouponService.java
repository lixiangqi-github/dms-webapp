package com.xmomen.module.base.service;

import java.math.BigDecimal;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xmomen.framework.exception.BusinessException;
import com.xmomen.framework.mybatis.dao.MybatisDao;
import com.xmomen.framework.mybatis.page.Page;
import com.xmomen.framework.utils.AssertExt;
import com.xmomen.module.base.constant.AppConstants;
import com.xmomen.module.base.entity.CdCoupon;
import com.xmomen.module.base.entity.CdCouponExample;
import com.xmomen.module.base.entity.CdCouponRef;
import com.xmomen.module.base.entity.CdMember;
import com.xmomen.module.base.entity.CdMemberCouponRelation;
import com.xmomen.module.base.mapper.CouponMapper;
import com.xmomen.module.base.model.CouponModel;
import com.xmomen.module.base.model.CouponQuery;
import com.xmomen.module.base.model.CouponReportModel;
import com.xmomen.module.base.model.ReadCardVo;
import com.xmomen.module.order.entity.TbTradeRecord;
import com.xmomen.module.pick.entity.TbExchangeCardLog;
import com.xmomen.module.pick.entity.TbRechargeLog;
import com.xmomen.module.system.entity.SysUserOrganization;
import com.xmomen.module.wx.module.coupon.model.CouponQueryModel;
import com.xmomen.module.wx.module.coupon.model.WxCouponModel;
import com.xmomen.module.wx.util.DateUtils;

/**
 * Created by Jeng on 2016/3/30.
 */
@Service
public class CouponService {
    private static Logger logger = LoggerFactory.getLogger(CouponService.class);

    @Autowired
    MybatisDao mybatisDao;

    @Autowired
    ItemService itemService;

    /**
     * 查询卡券信息
     *
     * @param couponQuery
     * @param limit
     * @param offset
     * @return
     */
    public Page<CouponModel> queryCoupon(CouponQuery couponQuery, Integer limit, Integer offset) {
        return (Page<CouponModel>) mybatisDao.selectPage(CouponMapper.CouponMapperNameSpace + "getCouponList", couponQuery, limit, offset);
    }

    /**
     * 查询卡券信息
     *
     * @param couponQuery
     * @param limit
     * @param offset
     * @return
     */
    public Page<CouponModel> queryCouponActivity(CouponQuery couponQuery, Integer limit, Integer offset) {
        return (Page<CouponModel>) mybatisDao.selectPage(CouponMapper.CouponMapperNameSpace + "getCouponActivityList", couponQuery, limit, offset);
    }

    public CouponModel queryOneCoupon(CouponQuery couponQuery) {
        return mybatisDao.getSqlSessionTemplate().selectOne(CouponMapper.CouponMapperNameSpace + "getCouponList", couponQuery);
    }

    @Transactional
    public CdCoupon createCoupon(CdCoupon cdCoupon) {
        return mybatisDao.saveByModel(cdCoupon);
    }

    @Transactional
    public void updateCoupon(CdCoupon cdCoupon) {
        mybatisDao.update(cdCoupon);
    }

    public CdCoupon getCoupon(Integer id) {
        return mybatisDao.selectByPrimaryKey(CdCoupon.class, id);
    }

    public Page<CdCoupon> getCouponList(String keyword, Integer limit, Integer offset) {
        CdCouponExample cdCouponExample = new CdCouponExample();
        cdCouponExample.createCriteria().andCouponNumberLike("%" + StringUtils.trimToEmpty(keyword) + "%");
        return mybatisDao.selectPageByExample(cdCouponExample, limit, offset);
    }

    @Transactional
    public Boolean bindMember(String couponNumber, Integer memberId, String password) throws Exception {
        CouponQuery couponQuery = new CouponQuery();
        couponQuery.setCouponNumber(couponNumber);
        List<ReadCardVo> existingBindCards = mybatisDao.getSqlSessionTemplate().selectList(CouponMapper.CouponMapperNameSpace + "getCouponByCouponNo", couponQuery);
        if (CollectionUtils.isEmpty(existingBindCards)) {
            throw new BusinessException("该卡不存在!");
        }
        for (ReadCardVo readCardVo : existingBindCards) {
            String userName = readCardVo.getUserName();
            if (!StringUtils.isEmpty(userName)) {
                throw new BusinessException("该卡已经被绑定了!");
            }
        }

        CdCoupon query = new CdCoupon();
        query.setCouponNumber(couponNumber);
        CdCoupon coupon = mybatisDao.selectOneByModel(query);
        String prePassword = coupon.getCouponPassword();
        if (!StringUtils.isEmpty(prePassword)) {
            if (!prePassword.equals(password)) {
                throw new BusinessException("密码错误!");
            }
        }

        CdMemberCouponRelation relation = new CdMemberCouponRelation();
        relation.setCdMemberId(memberId);
        relation.setCouponNumber(couponNumber);
        mybatisDao.save(relation);
        return Boolean.TRUE;
    }

    @Transactional
    public void sendOneCoupon(Integer id, Integer companyId, Integer customerMangerId, String couponNumber, String batch, Integer isGift) {
        //更新卡发放状态
        CdCoupon coupon = mybatisDao.selectByPrimaryKey(CdCoupon.class, id);
        //如果是后付款类型则发卡就激活
        if (coupon.getPaymentType() == 2) {
            coupon.setIsUseful(1);
            //如果是卡则记录激活时间
            if (coupon.getCouponType() == 1) {
                coupon.setUsefulDate(DateUtils.getNowDate());
            }
        }
        coupon.setIsSend(1);
        coupon.setId(id);
        //是否赠送
        coupon.setIsGift(isGift == null ? 0 : isGift);
        coupon.setBatch(batch);
        coupon.setCdCompanyId(companyId);
        coupon.setCdUserId(customerMangerId);
        mybatisDao.updateByModel(coupon);
    }

    /**
     * 退卡
     *
     * @param id
     */
    @Transactional
    public void returnCoupon(Integer id) {
        //更新卡券为未发送
        mybatisDao.getSqlSessionTemplate().update(CouponMapper.CouponMapperNameSpace + "updateReturnCoupon", id);
    }

    /**
     * 卡充值
     *
     * @param couponNo
     * @param rechargePrice
     */
    @Transactional
    public void cardRecharge(String couponNo, BigDecimal rechargePrice) {
        CdCoupon coupon = new CdCoupon();
        coupon.setCouponNumber(couponNo);
        coupon = mybatisDao.selectOneByModel(coupon);
        AssertExt.notNull(coupon, "卡号不存在！");
        BigDecimal userPrice = coupon.getUserPrice() == null ? BigDecimal.ZERO : coupon.getUserPrice();
        coupon.setUserPrice(userPrice.add(rechargePrice));
        mybatisDao.update(coupon);

        Integer userId = (Integer) SecurityUtils.getSubject().getSession().getAttribute(AppConstants.SESSION_USER_ID_KEY);
        SysUserOrganization userOrganization = new SysUserOrganization();
        userOrganization.setUserId(userId);
        userOrganization = mybatisDao.selectOneByModel(userOrganization);
        TbRechargeLog rechargeLog = new TbRechargeLog();
        rechargeLog.setCouponNo(couponNo);
        rechargeLog.setRechargeDate(mybatisDao.getSysdate());
        rechargeLog.setRechargePlace(userOrganization.getOrganizationId());
        rechargeLog.setRechargePrice(rechargePrice);
        rechargeLog.setRechargeUser(userId);
        mybatisDao.save(rechargeLog);

        //添加交易记录
        TbTradeRecord tradeRecord = new TbTradeRecord();
        tradeRecord.setAmount(rechargePrice);
        tradeRecord.setCreateTime(mybatisDao.getSysdate());
        tradeRecord.setTradeNo(couponNo);
        tradeRecord.setTradeType("recharge");
        tradeRecord.setRecordUser(userId);
        tradeRecord.setRemark("卡充值记录");
        mybatisDao.save(tradeRecord);
    }
    
    /**
     * 卡充值
     *
     * @param couponNo
     * @param rechargePrice
     */
    @Transactional
    public void cardWxRecharge(String couponNo, BigDecimal rechargePrice, Integer userId) {
        CdCoupon coupon = new CdCoupon();
        coupon.setCouponNumber(couponNo);
        coupon = mybatisDao.selectOneByModel(coupon);
        AssertExt.notNull(coupon, "卡号不存在！");
        BigDecimal userPrice = coupon.getUserPrice() == null ? BigDecimal.ZERO : coupon.getUserPrice();
        coupon.setUserPrice(userPrice.add(rechargePrice));
        mybatisDao.update(coupon);

        
        /*SysUserOrganization userOrganization = new SysUserOrganization();
        userOrganization.setUserId(userId);
        userOrganization = mybatisDao.selectOneByModel(userOrganization);*/
        TbRechargeLog rechargeLog = new TbRechargeLog();
        rechargeLog.setCouponNo(couponNo);
        rechargeLog.setRechargeDate(mybatisDao.getSysdate());
        // 微信用户没有组织，设为默认值0
        rechargeLog.setRechargePlace(0);
        rechargeLog.setRechargePrice(rechargePrice);
        // userId 是微信端的memberId(cd_member)
        rechargeLog.setRechargeUser(userId);
        mybatisDao.save(rechargeLog);

        //添加交易记录
        TbTradeRecord tradeRecord = new TbTradeRecord();
        tradeRecord.setAmount(rechargePrice);
        tradeRecord.setCreateTime(mybatisDao.getSysdate());
        tradeRecord.setTradeNo(couponNo);
        tradeRecord.setTradeType("recharge");
        tradeRecord.setRecordUser(userId);
        tradeRecord.setRemark("卡充值记录");
        mybatisDao.save(tradeRecord);
    }

    /**
     * 换卡
     *
     * @param oldCouponNo
     * @param oldPassword
     * @param newCouponNo
     * @param newPassword
     */
    @Transactional
    public void exchangeCard(String oldCouponNo, String oldPassword, String newCouponNo, String newPassword) {
        CdCoupon oldCoupon = new CdCoupon();
        oldCoupon.setCouponNumber(oldCouponNo);
        oldCoupon.setCouponPassword(oldPassword);
        oldCoupon = mybatisDao.selectOneByModel(oldCoupon);
        AssertExt.notNull(oldCoupon, "老卡卡号错误或者密码错误！");
        CdCoupon newCoupon = new CdCoupon();
        newCoupon.setCouponNumber(newCouponNo);
        newCoupon.setCouponPassword(newPassword);
        newCoupon = mybatisDao.selectOneByModel(newCoupon);
        AssertExt.notNull(oldCoupon, "新卡卡号错误或者密码错误！");
        if (1 == newCoupon.getIsSend()) {
            AssertExt.notNull(newCoupon, "新卡已经发卡不能再次换卡！");
        }
        //老卡作废
        oldCoupon.setIsUsed(3);
        mybatisDao.update(oldCoupon);
        //将老卡的所有关系转移给新卡
        //转移余额
        BigDecimal userPrice = newCoupon.getUserPrice() == null ? BigDecimal.ZERO : newCoupon.getUserPrice();
        newCoupon.setUserPrice(userPrice.add(oldCoupon.getUserPrice()));
        newCoupon.setIsSend(1);
        newCoupon.setIsUsed(1);
        newCoupon.setIsOver(1);
        newCoupon.setIsUseful(1);
        mybatisDao.update(newCoupon);
        //卡客户关系
        CdMemberCouponRelation memberCouponRelation = new CdMemberCouponRelation();
        memberCouponRelation.setCouponNumber(oldCoupon.getCouponNumber());
        memberCouponRelation = mybatisDao.selectOneByModel(memberCouponRelation);
        memberCouponRelation.setCouponNumber(newCoupon.getCouponNumber());
        mybatisDao.update(memberCouponRelation);
        //卡劵的发放单位和客户经理
        CdCouponRef couponRef = new CdCouponRef();
        couponRef.setCouponNumber(oldCoupon.getCouponNumber());
        List<CdCouponRef> couponRefs = mybatisDao.selectByModel(couponRef);
        for (CdCouponRef couponRefdb : couponRefs) {
            couponRefdb.setCdCouponId(newCoupon.getId());
            couponRefdb.setCouponNumber(newCoupon.getCouponNumber());
            mybatisDao.update(couponRefdb);
        }
        //记录换卡记录
        Integer userId = (Integer) SecurityUtils.getSubject().getSession().getAttribute(AppConstants.SESSION_USER_ID_KEY);
        SysUserOrganization userOrganization = new SysUserOrganization();
        userOrganization.setUserId(userId);
        userOrganization = mybatisDao.selectOneByModel(userOrganization);
        TbExchangeCardLog exchangeCardLog = new TbExchangeCardLog();
        exchangeCardLog.setNewCouponId(newCoupon.getId());
        exchangeCardLog.setNewCouponNo(newCouponNo);
        exchangeCardLog.setOldCouponId(oldCoupon.getId());
        exchangeCardLog.setOldCouponNo(oldCouponNo);
        exchangeCardLog.setRechargePlace(userOrganization.getOrganizationId());
        exchangeCardLog.setRechargeUser(userId);
        mybatisDao.save(exchangeCardLog);
    }

    /**
     * 手工调整金额
     *
     * @param couponNo
     * @param updatePrice
     * @param remark
     */
    @Transactional
    public void updateBalance(String couponNo, BigDecimal updatePrice, String remark) {
        Integer userId = (Integer) SecurityUtils.getSubject().getSession().getAttribute(AppConstants.SESSION_USER_ID_KEY);

        CdCoupon coupon = new CdCoupon();
        coupon.setCouponNumber(couponNo);
        coupon = mybatisDao.selectOneByModel(coupon);
        AssertExt.notNull(coupon, "卡号" + couponNo + "不存在,调整失败！");

        BigDecimal userPrice = coupon.getUserPrice();
        if (userPrice == null) {
            userPrice = BigDecimal.ZERO;
        }
        userPrice = userPrice.add(updatePrice);
        coupon.setUserPrice(userPrice);
        mybatisDao.update(coupon);

        //添加交易记录
        TbTradeRecord tradeRecord = new TbTradeRecord();
        tradeRecord.setAmount(updatePrice);
        tradeRecord.setCreateTime(mybatisDao.getSysdate());
        tradeRecord.setTradeNo(couponNo);
        tradeRecord.setTradeType("adjustment");
        tradeRecord.setRecordUser(userId);
        tradeRecord.setRemark(remark);
        mybatisDao.save(tradeRecord);
    }


    /**
     * 导入卡劵
     *
     * @param couponReport
     */
    @Transactional
    public void importCoupon(CouponReportModel couponReport) {
        // TODO Auto-generated method stub
        CdCoupon coupon = new CdCoupon();
        coupon.setCouponCategory(1);
        coupon.setCouponDesc(couponReport.getCouponDesc());
        coupon.setCouponNumber(couponReport.getCouponNumber());
        coupon.setCouponPassword(couponReport.getCouponPassword());
        coupon.setCouponType(couponReport.getCouponType());
        coupon.setCouponValue(couponReport.getCouponValue());
        coupon.setPaymentType(couponReport.getPaymentType());
        coupon.setCouponCategory(couponReport.getCouponCategoryId());
        try {
            mybatisDao.save(coupon);
        } catch (Exception e) {
            logger.error("卡劵已经存在！卡劵号：" + coupon.getCouponNumber());
        }
    }

    @Transactional
    public void auditCoupon(Integer id, boolean locked) {
        //db的卡
        CdCoupon cdCouponDb = mybatisDao.selectByPrimaryKey(CdCoupon.class, id);
        CdCoupon coupon = new CdCoupon();
        coupon.setAuditDate(DateUtils.getNowDate());
        coupon.setIsUseful(locked ? 1 : 0);
        //如果是预付款类型则审核就激活
        if (locked && cdCouponDb.getPaymentType() == 1 && cdCouponDb.getCouponType() == 1) {
            //激活时间
            coupon.setUsefulDate(DateUtils.getNowDate());
        }
        coupon.setId(id);
        mybatisDao.update(coupon);
    }

    public CouponModel getCouponModel(String couponNumber) {
        return mybatisDao.getSqlSessionTemplate().selectOne(CouponMapper.CouponMapperNameSpace + "getCouponItemsByByCouponNo", couponNumber);
    }

    public List<WxCouponModel> getMyCouponList(CouponQueryModel queryModel) {
        return mybatisDao.getSqlSessionTemplate().selectList(CouponMapper.CouponMapperNameSpace + "getMyCouponList", queryModel);
    }

    public Boolean validate(String couponNo, String password) {
        CdCoupon couponQuery = new CdCoupon();
        couponQuery.setCouponNumber(couponNo);
        CdCoupon coupon = mybatisDao.selectOneByModel(couponQuery);
        if (coupon == null) return false;
        String prePassword = coupon.getCouponPassword();
        if (StringUtils.isEmpty(prePassword) && StringUtils.isEmpty(password)) {
            return true;
        }
        else {
            if (password == null) password = "";
            if (password.equals(prePassword)) return true;
            return false;
        }
    }

    public Boolean resetPasword(String couponNumber, String password, String newPassWord, Integer memberId) throws Exception {
        CouponQuery couponQuery = new CouponQuery();
        couponQuery.setCouponNumber(couponNumber);
        List<ReadCardVo> existingBindCards = mybatisDao.getSqlSessionTemplate().selectList(CouponMapper.CouponMapperNameSpace + "getCouponByCouponNo", couponQuery);
        if (CollectionUtils.isEmpty(existingBindCards)) {
            throw new BusinessException("该卡不存在!");
        }
        CdMember cdMember = mybatisDao.selectByPrimaryKey(CdMember.class, memberId);
        if (cdMember == null) {
            throw new BusinessException("当前用户不存在!");
        }
        boolean belongTo = false;
        for (ReadCardVo readCardVo : existingBindCards) {
            String userName = readCardVo.getUserName();
            if (cdMember.getName().equals(userName)) {
                belongTo = true;
            }
        }
        if (!belongTo) {
            throw new BusinessException("该卡未被激活或者不属于当前用户!");
        }
        CdCoupon query = new CdCoupon();
        query.setCouponNumber(couponNumber);
        CdCoupon coupon = mybatisDao.selectOneByModel(query);
        String prePassword = coupon.getCouponPassword();
        if (StringUtils.isEmpty(prePassword) && StringUtils.isEmpty(password)) {
            coupon.setCouponPassword(newPassWord);
        }
        else if (!StringUtils.isEmpty(prePassword)) {
            if (prePassword.equals(password)) {
                coupon.setCouponPassword(newPassWord);
            }
            else {
                throw new BusinessException("密码不正确!");
            }
        }
        mybatisDao.update(coupon);
        return Boolean.TRUE;
    }
}
