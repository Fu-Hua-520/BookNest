package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.BarLevelTitle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BarLevelTitleMapper {

    /**
     * 新增或覆盖某等级的称号（uk_bar_level 冲突时更新 title）
     * @param title 称号记录
     */
    void upsert(BarLevelTitle title);

    /**
     * 清空某吧的全部称号（吧主整批重设前先清一遍，去掉的等级要真的消失）
     * @param barId 书吧ID
     */
    void deleteByBarId(@Param("barId") String barId);

    /**
     * 查询某吧的全部称号（按等级升序）
     * @param barId 书吧ID
     * @return 称号记录
     */
    List<BarLevelTitle> listByBar(@Param("barId") String barId);

    /**
     * 取某吧某等级的称号名（没有则 null）
     * @param barId 书吧ID
     * @param level 等级
     * @return 称号名
     */
    String selectTitle(@Param("barId") String barId, @Param("level") Integer level);
}
