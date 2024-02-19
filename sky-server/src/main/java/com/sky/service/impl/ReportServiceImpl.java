package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {
    @Autowired
    OrderMapper orderMapper;
    @Autowired
    UserMapper userMapper;
    @Autowired
    WorkspaceService workspaceService;

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
        List<Double> turnoverList = new ArrayList<>();

        for (LocalDate curr = begin; !curr.isAfter(end); curr = curr.plusDays(1)) {
            // 数据库下单时间为datetime，所以Java需将LocalDate进行转型为LocalDateTime来传入
            LocalDateTime beginTime = LocalDateTime.of(curr, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(curr, LocalTime.MAX);

            Map<Object, Object> paramMap = new HashMap<>();
            paramMap.put("status", Orders.COMPLETED);
            paramMap.put("beginTime", beginTime);
            paramMap.put("endTime", endTime);

            Double turnover = orderMapper.sumByMap(paramMap);

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

    /**
     * 查询指定时间区间内的销量排名top10
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        // 商品名称列表、销量列表
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MIN);
        List<GoodsSalesDTO> goodsSalesDTOList = orderMapper.getSalesTop10(beginTime, endTime);

        List<String> nameList = goodsSalesDTOList.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        List<Integer> numberList = goodsSalesDTOList.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());

        // 将数据以逗号分隔，并封装到TurnoverReportVO
        SalesTop10ReportVO salesTop10ReportVO = SalesTop10ReportVO.builder()
                .nameList(StringUtils.join(nameList, ","))
                .numberList(StringUtils.join(numberList, ","))
                .build();

        return salesTop10ReportVO;
    }

    /**
     * 导出Excel运营数据报表
     *
     * @param response
     */
    @Override
    public void exportBusinessData(HttpServletResponse response) throws IOException {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");

        XSSFWorkbook excel = new XSSFWorkbook(is);
        XSSFSheet sheet = excel.getSheet("Sheet1");

        // 获取时间区间，从三十天前到昨天
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);

        // 获取概览数据
        BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(begin, LocalTime.MIN),
                LocalDateTime.of(end, LocalTime.MAX));


        // 构造行对象，写入时间区间
        XSSFRow row = sheet.getRow(1);// 下标从0开始
        XSSFCell cell = row.getCell(1);
        cell.setCellValue("时间区间：" + begin + " - " + end);

        // 写入概览数据
        sheet.getRow(3).getCell(2).setCellValue(businessData.getTurnover());
        sheet.getRow(3).getCell(4).setCellValue(businessData.getOrderCompletionRate());
        sheet.getRow(3).getCell(6).setCellValue(businessData.getNewUsers());
        sheet.getRow(4).getCell(2).setCellValue(businessData.getValidOrderCount());
        sheet.getRow(4).getCell(4).setCellValue(businessData.getUnitPrice());

        // 写入明细数据
        for (int i = 0; i < 30; ++i) {
            row = sheet.getRow(7 + i);

            LocalDate date = begin.plusDays(i);
            businessData = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN),
                    LocalDateTime.of(date, LocalTime.MAX));

            row.getCell(1).setCellValue(String.valueOf(date));
            row.getCell(2).setCellValue(businessData.getTurnover());
            row.getCell(3).setCellValue(businessData.getValidOrderCount());
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
            row.getCell(5).setCellValue(businessData.getUnitPrice());
            row.getCell(6).setCellValue(businessData.getNewUsers());
        }

        // 通过输出流将文件下载到客户端浏览器中
        ServletOutputStream os = response.getOutputStream();
        excel.write(os);

        // 关闭资源
        os.flush();
        os.close();
        excel.close();
    }
}
