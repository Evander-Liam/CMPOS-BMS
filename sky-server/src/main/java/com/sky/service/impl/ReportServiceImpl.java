package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportServiceImpl implements ReportService {
    @Autowired
    OrderMapper orderMapper;
    @Autowired
    UserMapper userMapper;

    /**
     * 根据时间区间统计营业额
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        // 日期与营业额
        List<LocalDate> dateList = new ArrayList<>();
        List<BigDecimal> turnoverList = new ArrayList<>();

        for (LocalDate curr = begin; !curr.isAfter(end); curr = curr.plusDays(1)) {
            // 数据库下单时间为datetime，所以Java需将LocalDate进行转型为LocalDateTime来传入
            LocalDateTime beginTime = LocalDateTime.of(curr, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(curr, LocalTime.MAX);

            Map<Object, Object> paramMap = new HashMap<>();
            paramMap.put("status", Orders.COMPLETED);
            paramMap.put("beginTime", beginTime);
            paramMap.put("endTime", endTime);

            BigDecimal turnover = orderMapper.sumByMap(paramMap);

            dateList.add(curr);
            turnoverList.add(turnover);
        }

        // 将数据以逗号分隔，并封装到TurnoverReportVO
        TurnoverReportVO turnoverReportVO = TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();

        return turnoverReportVO;
    }

    /**
     * 用户统计，统计新增用户数列表和总用户量列表
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        // 日期、新增用户、用户总量
        List<LocalDate> dateList = new ArrayList<>();
        List<Integer> newUserList = new ArrayList<>();
        List<Integer> totalUserList = new ArrayList<>();

        Integer totalUser = 0;
        for (LocalDate curr = begin; !curr.isAfter(end); curr = curr.plusDays(1)) {
            LocalDateTime beginTime = LocalDateTime.of(curr, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(curr, LocalTime.MAX);

            Map<Object, Object> paramMap = new HashMap<>();
            paramMap.put("beginTime", beginTime);
            paramMap.put("endTime", endTime);

            // 获取当天的新增用户，及其用户总量
            Integer newUser = userMapper.countByMap(paramMap);
            totalUser += newUser;

            dateList.add(curr);
            newUserList.add(newUser);
            totalUserList.add(totalUser);
        }

        // 将数据以逗号分隔，并封装到UserReportVO
        UserReportVO userReportVO = UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .build();

        return userReportVO;
    }

    /**
     * 订单统计，有效订单列表、订单数列表、有效订单数、订单总数、订单完成率
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        // 日期、有效订单列表、订单数列表
        List<LocalDate> dateList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();
        List<Integer> orderCountList = new ArrayList<>();

        for (LocalDate curr = begin; !curr.isAfter(end); curr = curr.plusDays(1)) {
            LocalDateTime beginTime = LocalDateTime.of(curr, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(curr, LocalTime.MAX);

            dateList.add(curr);

            // 获取当天订单总数
            Map<Object, Object> paramMap = new HashMap<>();
            paramMap.put("beginTime", beginTime);
            paramMap.put("endTime", endTime);

            Integer totalOrder = orderMapper.countByMap(paramMap);
            orderCountList.add(totalOrder);

            // 获取当天有效订单数
            paramMap.put("status", Orders.COMPLETED);

            Integer validOrder = orderMapper.countByMap(paramMap);
            validOrderCountList.add(validOrder);
        }

        // 时间区间内的总订单数
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        // 时间区间内的总有效订单数
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();
        // 订单完成率
        Double orderCompletionRate = 0.0;
        if (totalOrderCount != 0) {
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }

        // 将数据以逗号分隔，并封装到OrderReportVO
        OrderReportVO orderReportVO = OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCount(validOrderCount)
                .totalOrderCount(totalOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();

        return orderReportVO;
    }
}
