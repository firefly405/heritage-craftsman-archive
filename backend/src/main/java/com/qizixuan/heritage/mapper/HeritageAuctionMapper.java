package com.qizixuan.heritage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qizixuan.heritage.entity.HeritageAuction;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface HeritageAuctionMapper extends BaseMapper<HeritageAuction> {
    @Update("""
        UPDATE heritage_auction
        SET work_id = #{item.workId}, seller_id = #{item.sellerId}, seller_name = #{item.sellerName},
            title = #{item.title}, cover_url = #{item.coverUrl}, description = #{item.description},
            start_price = #{item.startPrice}, current_price = #{item.startPrice},
            min_increment = #{item.minIncrement}, start_time = #{item.startTime}, end_time = #{item.endTime},
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{item.id} AND status = 'DRAFT'
        """)
    int updateDraft(@Param("item") HeritageAuction item);

    @Update("""
        UPDATE heritage_auction
        SET status = CASE WHEN start_time > CURRENT_TIMESTAMP THEN 'UPCOMING' ELSE 'ONGOING' END,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id} AND status = 'DRAFT' AND end_time > CURRENT_TIMESTAMP
        """)
    int publish(@Param("id") Long id);

    @Update("""
        UPDATE heritage_auction SET status = 'ONGOING', updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id} AND status = 'UPCOMING'
          AND start_time <= CURRENT_TIMESTAMP AND end_time > CURRENT_TIMESTAMP
        """)
    int startIfDue(@Param("id") Long id);

    @Update("""
        UPDATE heritage_auction SET status = 'ENDED', updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id} AND status IN ('UPCOMING', 'ONGOING') AND end_time <= CURRENT_TIMESTAMP
        """)
    int finishIfExpired(@Param("id") Long id);

    @Update("""
        UPDATE heritage_auction SET status = 'ENDED', updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id} AND status IN ('UPCOMING', 'ONGOING')
        """)
    int close(@Param("id") Long id);

    @Update("""
        UPDATE heritage_auction SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id} AND status IN ('DRAFT', 'UPCOMING') AND bid_count = 0
        """)
    int cancel(@Param("id") Long id);

    @Update("""
        UPDATE heritage_auction
        SET current_price = #{amount}, bid_count = bid_count + 1,
            winner_id = #{bidderId}, winner_name = #{bidderName}, updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id} AND status = 'ONGOING'
          AND start_time <= CURRENT_TIMESTAMP AND end_time > CURRENT_TIMESTAMP
          AND current_price = #{expectedPrice} AND bid_count = #{expectedBidCount}
        """)
    int placeBid(@Param("id") Long id,
                 @Param("amount") BigDecimal amount,
                 @Param("bidderId") Long bidderId,
                 @Param("bidderName") String bidderName,
                 @Param("expectedPrice") BigDecimal expectedPrice,
                 @Param("expectedBidCount") Integer expectedBidCount);
}
