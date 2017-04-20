package com.xmomen.module.wx.module.order.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.util.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSON;
import com.xmomen.framework.mybatis.dao.MybatisDao;
import com.xmomen.module.base.service.CouponService;
import com.xmomen.module.order.entity.TbOrder;
import com.xmomen.module.order.service.OrderService;
import com.xmomen.module.resource.service.ResourceUtilsService;
import com.xmomen.module.wx.model.PayAttachModel;
import com.xmomen.module.wx.module.order.mapper.MyOrderMapper;
import com.xmomen.module.wx.module.order.model.MyOrderQuery;
import com.xmomen.module.wx.module.order.model.OrderDetailModel;
import com.xmomen.module.wx.module.order.model.OrderModel;
import com.xmomen.module.wx.module.order.model.OrderProductItem;
import com.xmomen.module.wx.module.order.model.OrderStatisticModel;
import com.xmomen.module.wx.module.order.service.MyOrderService;
import com.xmomen.module.wx.pay.model.PayResData;

@Service
public class MyOrderServiceImpl implements MyOrderService {

    @Autowired
    MybatisDao mybatisDao;

    @Autowired
    OrderService orderService;
    
    @Autowired
    CouponService couponService;

    @Override
    public List<OrderModel> myOrder(MyOrderQuery myOrderQuery) {
        List<OrderModel> orders = mybatisDao.getSqlSessionTemplate().selectList(MyOrderMapper.MY_ORDER_MAPPER_NAMESPACE + "selectOrders", myOrderQuery);
        if (orders != null) {
            for (OrderModel order : orders) {
                List<OrderProductItem> items = order.getProducts();
                if (items != null) {
                    for (OrderProductItem item : items) {
                    	if (StringUtils.isEmpty(item.getPicUrl())) {
                    		item.setPicUrl(ResourceUtilsService.getDefaultPicPath());
                        }
                        else {
                        	item.setPicUrl(ResourceUtilsService.getWholeHttpPath(item.getPicUrl()));
                        }
                    }
                }
            }
        }
        return orders;
    }

    @Override
    public OrderDetailModel getOrderDetail(MyOrderQuery myOrderQuery) {
        if (myOrderQuery.getOrderId() == null && StringUtils.isEmpty(myOrderQuery.getOrderNo())) {
            return null;
        }
        OrderDetailModel orderDetail = mybatisDao.getSqlSessionTemplate().selectOne(MyOrderMapper.MY_ORDER_MAPPER_NAMESPACE + "getOrderDetail", myOrderQuery);
        if (orderDetail != null) {
            List<OrderProductItem> items = orderDetail.getProducts();
            for (OrderProductItem item : items) {
            	if (StringUtils.isEmpty(item.getPicUrl())) {
            		item.setPicUrl(ResourceUtilsService.getDefaultPicPath());
                }
                else {
                	item.setPicUrl(ResourceUtilsService.getWholeHttpPath(item.getPicUrl()));
                }
            }
        }
        return orderDetail;
    }

    @Override
    public Boolean confirmReceiveOrder(Integer orderId, Integer userId) {
        TbOrder tbOrder = mybatisDao.selectByPrimaryKey(TbOrder.class, orderId);
        if (tbOrder == null || userId == null || !String.valueOf(userId).equals(tbOrder.getMemberCode())) {
            throw new IllegalArgumentException("订单不存在或者不属于当前用户!");
        }
        tbOrder.setOrderStatus("6");//确认本人收货
        tbOrder.setShouHuoDate(new Date());
        mybatisDao.update(tbOrder);
        return Boolean.TRUE;
    }

    @Override
    public Boolean cancelOrder(Integer orderId, Integer userId) {
        TbOrder tbOrder = mybatisDao.selectByPrimaryKey(TbOrder.class, orderId);
        if (tbOrder == null || userId == null || !String.valueOf(userId).equals(tbOrder.getMemberCode())) {
            throw new IllegalArgumentException("订单不存在或者不属于当前用户!");
        }
        Integer payStatus = tbOrder.getPayStatus();
        if (payStatus == 1) throw new IllegalArgumentException("订单已支付,不能取消!");
        tbOrder.setOrderStatus("9");//取消订单
        mybatisDao.update(tbOrder);
        return Boolean.TRUE;
    }

	@Override
	public Map<String, Integer> getOrderStatistic(Integer userId) {
		Map<String, Integer> result = new HashMap<String, Integer>();
		List<OrderStatisticModel> orderStatisticModels = mybatisDao.getSqlSessionTemplate().selectList(MyOrderMapper.MY_ORDER_MAPPER_NAMESPACE + "getOrderStatistic", userId);
		int notPayCount = 0;
		if(!CollectionUtils.isEmpty(orderStatisticModels)) {
			for(OrderStatisticModel orderStatisticModel: orderStatisticModels) {
				if(orderStatisticModel.getPayStatus() != 1) {
					notPayCount += orderStatisticModel.getCount();
				}
				String statusDesc = orderStatisticModel.getOrderStatusDesc();
				if(statusDesc != null && !orderStatisticModel.getOrderStatus().equals(0)) {
					if(result.containsKey(statusDesc)) {
						result.put(statusDesc, result.get(statusDesc) + orderStatisticModel.getCount());
					} else {
						result.put(statusDesc, orderStatisticModel.getCount());
					}
				}
			}
		}
		if(notPayCount > 0) {
			result.put("待付款", notPayCount);
		}
		return result;
	}

	@Override
	@Transactional
	public void payCallBack(PayResData payResData) {
		String attachement = payResData.getAttach();
		PayAttachModel payAttachModel = JSON.parseObject(attachement, PayAttachModel.class);
		double totalFee = payResData.getTotal_fee();
		if(1 == payAttachModel.getType()) {
			//微信支付
			String orderNo = payAttachModel.getTradeNo();
			TbOrder query = new TbOrder();
			query.setOrderNo(orderNo);
			TbOrder tbOrder = mybatisDao.selectOneByModel(query);
			if(tbOrder == null) {
				throw new IllegalArgumentException("订单不存在!");
			}
			//设置为微信支付类型
			tbOrder.setPaymentMode(8);
			tbOrder.setPayStatus(1);
			tbOrder.setOrderStatus("1");
			mybatisDao.update(tbOrder);
		} else if(2 == payAttachModel.getType()) {
			//卡充值
			String couponNo = payAttachModel.getTradeNo();
			couponService.cardRecharge(couponNo, new BigDecimal(totalFee/100));
		}
		//TODO 更新支付记录tb_pay_record
	}

}