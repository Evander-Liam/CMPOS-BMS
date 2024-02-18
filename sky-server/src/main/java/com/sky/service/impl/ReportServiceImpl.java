package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
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

    /**
     * 根据时间区间统计营业额
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
}
