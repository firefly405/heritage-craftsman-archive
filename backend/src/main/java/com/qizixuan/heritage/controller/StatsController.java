package com.qizixuan.heritage.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qizixuan.heritage.common.ApiResponse;
import com.qizixuan.heritage.common.BusinessException;
import com.qizixuan.heritage.common.UserSession;
import com.qizixuan.heritage.entity.ArtisanProfile;
import com.qizixuan.heritage.entity.HeritageAuction;
import com.qizixuan.heritage.mapper.AnnouncementMapper;
import com.qizixuan.heritage.mapper.ArtisanProfileMapper;
import com.qizixuan.heritage.mapper.ArtisanWorkMapper;
import com.qizixuan.heritage.mapper.CraftCategoryMapper;
import com.qizixuan.heritage.mapper.HeritageAuctionMapper;
import com.qizixuan.heritage.service.AuctionService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {
    private final ArtisanProfileMapper artisans;
    private final ArtisanWorkMapper works;
    private final CraftCategoryMapper categories;
    private final AnnouncementMapper announcements;
    private final HeritageAuctionMapper auctions;
    private final AuctionService auctionService;
    public StatsController(ArtisanProfileMapper artisans, ArtisanWorkMapper works, CraftCategoryMapper categories,
                           AnnouncementMapper announcements, HeritageAuctionMapper auctions, AuctionService auctionService) {
        this.artisans = artisans; this.works = works; this.categories = categories;
        this.announcements = announcements; this.auctions = auctions; this.auctionService = auctionService;
    }
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        List<HeritageAuction> visibleAuctions=auctionService.list("","");
        boolean manager=UserSession.get()!=null && List.of("ADMIN","REVIEWER").contains(UserSession.get().getRole());
        List<ArtisanProfile> visibleArtisans=artisans.selectList(null).stream().filter(a->manager || "ARCHIVED".equals(a.getStatus()) || java.util.Objects.equals(a.getUserId(),UserSession.id())).toList();
        java.util.Set<Long> visibleIds=visibleArtisans.stream().map(ArtisanProfile::getId).collect(java.util.stream.Collectors.toSet());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artisanCount", visibleArtisans.size());
        result.put("archivedCount", visibleArtisans.stream().filter(a->"ARCHIVED".equals(a.getStatus())).count());
        result.put("pendingCount", visibleArtisans.stream().filter(a->"SUBMITTED".equals(a.getStatus())).count());
        result.put("workCount", works.selectList(null).stream().filter(w->manager || visibleIds.contains(w.getArtisanId()) && (List.of("DISPLAY","SOLD").contains(w.getStatus()) || visibleArtisans.stream().anyMatch(a->a.getId().equals(w.getArtisanId()) && java.util.Objects.equals(a.getUserId(),UserSession.id())))).count());
        result.put("categoryCount", categories.selectCount(null));
        result.put("announcementCount", announcements.selectList(null).stream().filter(a->"ADMIN".equals(UserSession.get().getRole()) || Boolean.TRUE.equals(a.getPublished())).count());
        result.put("auctionCount", visibleAuctions.size());
        result.put("activeAuctionCount", visibleAuctions.stream().filter(a->List.of("UPCOMING","ONGOING").contains(a.getStatus())).count());
        Map<String, Long> regionData = new LinkedHashMap<>();
        visibleArtisans.forEach(a -> regionData.merge(a.getRegion() == null || a.getRegion().isBlank() ? "未填写" : a.getRegion(), 1L, Long::sum));
        result.put("regionData", regionData);
        return ApiResponse.ok(result);
    }
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        if (UserSession.get() == null || !List.of("ADMIN", "REVIEWER").contains(UserSession.get().getRole()))
            throw new BusinessException("仅管理员或审核员可导出档案");
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=artisan-archives.csv");
        StringBuilder csv = new StringBuilder("\uFEFF编号,姓名,地区,从艺年限,状态\n");
        List<ArtisanProfile> list = artisans.selectList(new LambdaQueryWrapper<ArtisanProfile>().orderByAsc(ArtisanProfile::getId));
        for (ArtisanProfile a : list) csv.append(a.getId()).append(',').append(clean(a.getName())).append(',').append(clean(a.getRegion())).append(',').append(a.getExperienceYears()).append(',').append(a.getStatus()).append('\n');
        response.getOutputStream().write(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    private String clean(String value) {
        if (value == null) return "";
        String cleaned = value.replace(",", "，").replace("\r", " ").replace("\n", " ");
        return !cleaned.isEmpty() && "=+-@".indexOf(cleaned.charAt(0)) >= 0 ? "'" + cleaned : cleaned;
    }
}
