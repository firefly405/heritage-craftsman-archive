package com.qizixuan.heritage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("artisan_work")
public class ArtisanWork {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long artisanId;
    private String title;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    private String status;
    private LocalDateTime createdAt;
}
