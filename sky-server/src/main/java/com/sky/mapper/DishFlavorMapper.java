package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.DishFlavor;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DishFlavorMapper {
    /**
     * 批量插入口味数据
     * @param dishFlavors
     */
    void insertBatch(List<DishFlavor> dishFlavors);

    /**
     * 根据菜品Id批量删除口味
     * @param dishIds
     */
    void deleteBatchByDishIds(List<Long> dishIds);
}
