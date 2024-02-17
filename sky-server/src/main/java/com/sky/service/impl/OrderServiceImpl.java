package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.BaiduGeocodingUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private BaiduGeocodingUtil baiduGeocodingUtil;
    @Autowired
    private WebSocketServer webSocketServer;


    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        // 1. 查询数据，处理异常情况
        // 查询当前用户的地址簿，判地址簿为空
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 查询当前用户的购物车，判购物车为空
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);
        if (shoppingCarts == null || shoppingCarts.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 判 用户的收货地址 距离 商家门店 超出配送范围
        Integer distance = baiduGeocodingUtil.getDistance(addressBook.getDetail());
        if (distance > 5000) {
            throw new OrderBusinessException("超出配送范围");
        }

        // 2. 构造订单数据
        Orders order = Orders.builder().number(String.valueOf(System.currentTimeMillis())) // 订单号为当前时间戳
                .status(Orders.PENDING_PAYMENT).userId(userId).orderTime(LocalDateTime.now()).payStatus(Orders.UN_PAID).phone(addressBook.getPhone()).address(addressBook.getDetail()).consignee(addressBook.getConsignee()).build();
        BeanUtils.copyProperties(ordersSubmitDTO, order);

        // 向订单表中插入一条数据
        orderMapper.insert(order);
        Long orderId = order.getId();

        // 3. 构造订单明细数据
        List<OrderDetail> orderDetails = new ArrayList<>();
        for (ShoppingCart cart : shoppingCarts) {
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setOrderId(orderId);
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetails.add(orderDetail);
        }

        // 向订单明细表中插入多条数据
        orderDetailMapper.insertBatch(orderDetails);

        // 清理购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        // 4. 封装返回数据OrderSubmitVO
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder().id(orderId).orderNumber(order.getNumber()).orderAmount(order.getAmount()).orderTime(order.getOrderTime()).build();
        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        /*
        // 调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), // 商户订单号
                new BigDecimal(0.01), // 支付金额，单位 元
                "苍穹外卖订单", // 商品描述
                user.getOpenid() // 微信用户的openid
        );
         */

        // 生成空JSONObject，跳过微信支付过程
        JSONObject jsonObject = new JSONObject();

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    @Override
    public void paySuccess(String outTradeNo) {
        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders order = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(order);

        // 通过WebSocket实现来单提醒，向客户端浏览器推送消息
        Map<Object, Object> paramMap = new HashMap<>();
        paramMap.put("type", 1);
        paramMap.put("orderId", order.getId());
        paramMap.put("content", "订单号：" + outTradeNo);

        webSocketServer.sendToAllClient(JSONObject.toJSONString(paramMap));
    }

    /**
     * 历史订单查询
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery4User(OrdersPageQueryDTO ordersPageQueryDTO) {
        // 设置分页
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        // 查询当前用户、当前状态下的订单数据
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        Page<Orders> orders = orderMapper.pageQuery(ordersPageQueryDTO);

        // 查询订单明细，并封装至OrderVO
        List<OrderVO> orderVOS = getOrderVOS(orders, false);

        return new PageResult(orders.getTotal(), orderVOS);
    }

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
        OrderVO orderVO = new OrderVO();

        // 根据id查询订单
        Orders order = orderMapper.getById(id);

        // 根据订单id查询订单详情，并封装至OrderVO
        if (order != null) {
            BeanUtils.copyProperties(order, orderVO);
            List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(order.getId());
            orderVO.setOrderDetailList(orderDetails);
        }

        return orderVO;
    }

    /**
     * 取消订单
     *
     * @param id
     */
    @Override
    @Transactional
    public void userCancelById(Long id) throws Exception {
        // 根据Id查询订单，并处理业务异常
        // 判订单是否存在
        Orders order = orderMapper.getById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 判当前订单状态是否可取消，仅“代付款”、“待接单”可取消
        // 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (order.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 若用户已支付，则需退款
        if (order.getPayStatus().equals(Orders.PAID)) {
            // 模拟退款成功，跳过微信退款过程
            /*
            weChatPayUtil.refund(
                    order.getNumber(), // 商户订单号
                    order.getNumber(), // 商户退款单号
                    new BigDecimal(0.01),// 退款金额，单位 元
                    new BigDecimal(0.01));// 原订单金额
            */

            // 支付状态修改为 退款
            order.setPayStatus(Orders.REFUND);
        }

        // 更新订单状态、取消原因、取消时间
        order.setStatus(Orders.CANCELLED);
        order.setCancelReason("用户取消");
        order.setCancelTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    /**
     * 再来一单
     *
     * @param id
     */
    @Override
    public void repetition(Long id) {
        // 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        // 根据订单id查询订单明细
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);

        // 将订单明细转换为购物车对象
        List<ShoppingCart> shoppingCarts = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetails) {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            shoppingCarts.add(shoppingCart);
        }

        // 批量添加购物车对象
        shoppingCartMapper.insertBatch(shoppingCarts);
    }

    /**
     * 订单搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery4Admin(OrdersPageQueryDTO ordersPageQueryDTO) {
        // 设置分页
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        // 查询当前用户、当前状态下的订单数据
        Page<Orders> orders = orderMapper.pageQuery(ordersPageQueryDTO);

        // 查询订单明细，并封装至OrderVO
        List<OrderVO> orderVOS = getOrderVOS(orders, true);

        return new PageResult(orders.getTotal(), orderVOS);
    }

    /**
     * 根据分页查询结果，获取订单明细，并封装List<OrderVO>
     *
     * @param orders
     * @param isAdmin
     * @return
     */
    private List<OrderVO> getOrderVOS(Page<Orders> orders, boolean isAdmin) {
        List<OrderVO> orderVOS = new ArrayList<>();
        if (orders != null && !orders.isEmpty()) {
            for (Orders order : orders) {
                // 查询订单明细
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(order.getId());

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(order, orderVO);
                orderVO.setOrderDetailList(orderDetails);

                // 若是管理端查询，则需完善订单菜品信息
                if (isAdmin) {
                    orderVO.setOrderDishes(getOrderDishes(orderDetails));
                }

                orderVOS.add(orderVO);
            }
        }
        return orderVOS;
    }

    /**
     * 拼接订单菜品信息，如：宫保鸡丁*3；鱼香豆腐*2；
     *
     * @param orderDetails
     * @return
     */
    private String getOrderDishes(List<OrderDetail> orderDetails) {
        StringBuilder orderDishes = new StringBuilder();
        for (OrderDetail orderDetail : orderDetails) {
            orderDishes.append(orderDetail.getName() + "*" + orderDetail.getNumber() + "；");
        }
        return orderDishes.toString();
    }


    /**
     * 各个状态的订单数量统计
     * 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
     *
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        // 根据订单状态，分别查询待接单、已接单、派送中的订单数量
        Integer toBeConfirmed = orderMapper.countByStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countByStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countByStatus(Orders.DELIVERY_IN_PROGRESS);

        // 将查询结果封装到OrderStatisticsVO
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO(toBeConfirmed, confirmed, deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders order = Orders.builder().id(ordersConfirmDTO.getId()).status(Orders.CONFIRMED).build();

        orderMapper.update(order);
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        // 查询当前订单，处理业务异常
        Orders order = orderMapper.getById(ordersRejectionDTO.getId());

        // 判订单不存在
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 判当前订单状态，因为只有“待接单”才可拒单
        if (!order.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 若用户已支付，则需退款
        if (order.getPayStatus().equals(Orders.PAID)) {
            /*
            // 模拟退款成功，跳过微信退款流程
            String refund = weChatPayUtil.refund(
                    order.getNumber(),
                    order.getNumber(),
                    new BigDecimal(0.01),
                    new BigDecimal(0.01));
            log.info("申请退款：{}", refund);
            */

            order.setPayStatus(Orders.REFUND);
        }
        // 更新订单
        order.setStatus(Orders.CANCELLED);
        order.setCancelTime(LocalDateTime.now());
        order.setRejectionReason(ordersRejectionDTO.getRejectionReason());

        orderMapper.update(order);
    }

    /**
     * 商家取消订单
     *
     * @param ordersCancelDTO
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        // 查询当前订单，处理业务异常
        Orders order = orderMapper.getById(ordersCancelDTO.getId());

        // 判订单不存在
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 若用户已支付，则需退款
        if (order.getPayStatus().equals(Orders.PAID)) {
            /*
            // 模拟退款成功，跳过微信退款流程
            String refund = weChatPayUtil.refund(
                    order.getNumber(),
                    order.getNumber(),
                    new BigDecimal(0.01),
                    new BigDecimal(0.01));
            log.info("申请退款：{}", refund);
            */

            order.setPayStatus(Orders.REFUND);
        }

        // 更新订单
        order.setStatus(Orders.CANCELLED);
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason(ordersCancelDTO.getCancelReason());

        orderMapper.update(order);
    }

    /**
     * 派送订单
     *
     * @param id
     */
    @Override
    public void delivery(Long id) {
        // 查询当前订单，处理业务异常
        Orders order = orderMapper.getById(id);

        // 判订单不存在
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 判订单状态为“已接单”
        if (!order.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 更新订单
        order.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(order);
    }

    /**
     * 完成订单
     *
     * @param id
     */
    @Override
    public void complete(Long id) {
        // 查询当前订单，处理业务异常
        Orders order = orderMapper.getById(id);

        // 判订单不存在
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 判订单状态为“派送中”
        if (!order.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 更新订单
        order.setStatus(Orders.COMPLETED);
        order.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(order);
    }
}
