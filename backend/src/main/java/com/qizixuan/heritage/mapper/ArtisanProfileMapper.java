package com.qizixuan.heritage.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qizixuan.heritage.entity.ArtisanProfile;
import org.apache.ibatis.annotations.Select;

public interface ArtisanProfileMapper extends BaseMapper<ArtisanProfile> {
    @Select("SELECT * FROM artisan_profile WHERE id = #{id} FOR UPDATE")
    ArtisanProfile lockById(Long id);
}
