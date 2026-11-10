package com.qizixuan.heritage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("heritage_auction")
public class HeritageAuction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workId;
    private Long sellerId;
    private String sellerName;
    private String title;
    private String coverUrl;
    private String description;
    private BigDecimal startPrice;
    private BigDecimal currentPrice;
    private BigDecimal minIncrement;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Integer bidCount;
    private Long winnerId;
    private String winnerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
