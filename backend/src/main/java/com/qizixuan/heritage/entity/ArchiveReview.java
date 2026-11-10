package com.qizixuan.heritage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("archive_review")
public class ArchiveReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long artisanId;
    private Long reviewerId;
    private String action;
    private String comment;
    private LocalDateTime createdAt;
}
