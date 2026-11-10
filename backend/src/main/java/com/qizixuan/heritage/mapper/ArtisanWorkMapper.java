package com.qizixuan.heritage.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qizixuan.heritage.entity.ArtisanWork;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ArtisanWorkMapper extends BaseMapper<ArtisanWork> {
    @Select("SELECT * FROM artisan_work WHERE id = #{id} FOR UPDATE")
    ArtisanWork lockById(Long id);

    @Update("UPDATE artisan_work SET status = 'SOLD' WHERE id = #{id} AND status = 'DISPLAY'")
    int markSold(Long id);
}
