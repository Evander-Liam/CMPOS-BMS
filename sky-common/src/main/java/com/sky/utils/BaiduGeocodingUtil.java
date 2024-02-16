package com.sky.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sky.exception.OrderBusinessException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@Slf4j
public class BaiduGeocodingUtil {
    private String address;
    private String ak;
    private String output;
    private static final String GEOCODING_URL = "https://api.map.baidu.com/geocoding/v3";
    private static final String DIRECTION_LITE_URL = "https://api.map.baidu.com/directionlite/v1/driving";


    /**
     * 结构化地址为对应位置坐标
     *
     * @param address
     * @return
     */
    public String getCoordinate(String address) {
        // 发送请求以获取地理编码
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("address", address);
        paramMap.put("output", output);
        paramMap.put("ak", ak);

        String res = HttpClientUtil.doGet(GEOCODING_URL, paramMap);

        // 获取地址的经纬度坐标
        JSONObject jsonObject = JSON.parseObject(res);
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String coordinate = location.getString("lat") + "," + location.getString("lng");

        return coordinate;
    }

    /**
     * 获取店铺与用户的规划的配送路线的距离
     *
     * @param userAddress
     * @return
     */
    public Integer getDistance(String userAddress) {
        // 获取店铺、用户坐标
        String shopCoordinate = getCoordinate(address);
        String userCoordinate = getCoordinate(userAddress);

        // 发送请求以获取路线规划
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("origin", shopCoordinate);
        paramMap.put("destination", userCoordinate);
        paramMap.put("ak", ak);
        paramMap.put("steps_info", "0");// 是否下发step详情

        String res = HttpClientUtil.doGet(DIRECTION_LITE_URL, paramMap);

        // 获取第一条规划的配送路线方案的距离
        JSONObject jsonObject = JSON.parseObject(res);

        // 判规划成功
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException("配送路线规划失败");
        }

        // 数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        return distance;
    }
}