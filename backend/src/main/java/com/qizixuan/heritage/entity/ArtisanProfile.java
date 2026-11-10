package com.qizixuan.heritage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("artisan_profile")
public class ArtisanProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String gender;
    private Integer birthYear;
    private String phone;
    private String region;
    private Long categoryId;
    private Integer experienceYears;
    private String skillDescription;
    private String honors;
    private String inheritanceStory;
    private String avatarUrl;
    private String status;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
