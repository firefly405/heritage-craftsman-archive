package com.qizixuan.heritage.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qizixuan.heritage.common.ApiResponse;
import com.qizixuan.heritage.common.BusinessException;
import com.qizixuan.heritage.common.UserSession;
import com.qizixuan.heritage.entity.ArtisanProfile;
import com.qizixuan.heritage.entity.ArtisanWork;
import com.qizixuan.heritage.entity.HeritageAuction;
import com.qizixuan.heritage.mapper.ArtisanProfileMapper;
import com.qizixuan.heritage.mapper.ArtisanWorkMapper;
import com.qizixuan.heritage.mapper.HeritageAuctionMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/works")
public class WorkController {
    private final ArtisanWorkMapper mapper;
    private final ArtisanProfileMapper artisans;
    private final HeritageAuctionMapper auctions;
    public WorkController(ArtisanWorkMapper mapper, ArtisanProfileMapper artisans, HeritageAuctionMapper auctions) {
        this.mapper = mapper; this.artisans = artisans; this.auctions = auctions;
    }
    @GetMapping public ApiResponse<List<ArtisanWork>> list(@RequestParam(required = false) Long artisanId) {
        LambdaQueryWrapper<ArtisanWork> q = new LambdaQueryWrapper<ArtisanWork>().orderByDesc(ArtisanWork::getCreatedAt);
        if (artisanId != null) q.eq(ArtisanWork::getArtisanId, artisanId);
        List<ArtisanWork> result = mapper.selectList(q);
        if (!isManager()) result = result.stream().filter(this::isPublicOrOwned).toList();
        return ApiResponse.ok(result);
    }
    @PostMapping public ApiResponse<ArtisanWork> create(@RequestBody ArtisanWork item) {
        validate(item);
        ArtisanProfile artisan = requireArtisan(item.getArtisanId()); requireArtisanOwnerOrAdmin(artisan);
        item.setId(null); item.setCreatedAt(LocalDateTime.now());
        if (!"ARCHIVED".equals(artisan.getStatus())) item.setStatus("HIDDEN");
        else if (!isAdmin() || item.getStatus() == null || item.getStatus().isBlank()) item.setStatus("DISPLAY");
        if (!List.of("DISPLAY", "HIDDEN").contains(item.getStatus())) throw new BusinessException("作品状态无效");
        mapper.insert(item); return ApiResponse.ok(item);
    }
    @Transactional
    @PutMapping("/{id}") public ApiResponse<Void> update(@PathVariable Long id, @RequestBody ArtisanWork item) {
        validate(item);
        ArtisanWork current = requireWorkLocked(id); requireArtisanOwnerOrAdmin(requireArtisan(current.getArtisanId()));
        Long targetArtisanId = item.getArtisanId() == null ? current.getArtisanId() : item.getArtisanId();
        ArtisanProfile targetArtisan = requireArtisan(targetArtisanId);
        requireArtisanOwnerOrAdmin(targetArtisan);
        if (!targetArtisanId.equals(current.getArtisanId()) && auctionCount(id) > 0)
            throw new BusinessException("已有拍卖记录的作品不能更换所属艺人");
        item.setId(id); item.setArtisanId(targetArtisanId); item.setCreatedAt(current.getCreatedAt());
        if ("SOLD".equals(current.getStatus())) item.setStatus(current.getStatus());
        else if (item.getStatus() == null || item.getStatus().isBlank()) item.setStatus(current.getStatus());
        else if (!List.of("DISPLAY", "HIDDEN").contains(item.getStatus())) throw new BusinessException("作品状态无效");
        if ("DISPLAY".equals(item.getStatus()) && !"ARCHIVED".equals(targetArtisan.getStatus()))
            throw new BusinessException("只有已归档艺人的作品可以公开展示");
        if (!item.getStatus().equals(current.getStatus()) && activeAuctionCount(id) > 0)
            throw new BusinessException("作品存在未结束的拍卖，不能修改展示状态");
        mapper.updateById(item); return ApiResponse.ok();
    }
    @Transactional
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) {
        ArtisanWork current = requireWorkLocked(id); requireArtisanOwnerOrAdmin(requireArtisan(current.getArtisanId()));
        if (auctionCount(id) > 0) throw new BusinessException("已有拍卖记录的作品不能删除");
        mapper.deleteById(id); return ApiResponse.ok();
    }
    private ArtisanWork requireWork(Long id) { ArtisanWork item = mapper.selectById(id); if (item == null) throw new BusinessException("非遗作品不存在"); return item; }
    private ArtisanWork requireWorkLocked(Long id) { ArtisanWork item = mapper.lockById(id); if (item == null) throw new BusinessException("非遗作品不存在"); return item; }
    private ArtisanProfile requireArtisan(Long id) { ArtisanProfile item = id == null ? null : artisans.selectById(id); if (item == null) throw new BusinessException("作品对应的艺人档案不存在"); return item; }
    private boolean isAdmin() { return "ADMIN".equals(UserSession.get().getRole()); }
    private boolean isManager() { return UserSession.get() != null && List.of("ADMIN", "REVIEWER").contains(UserSession.get().getRole()); }
    private boolean isPublicOrOwned(ArtisanWork work) {
        ArtisanProfile artisan = artisans.selectById(work.getArtisanId());
        if (artisan == null) return false;
        if (artisan.getUserId() != null && artisan.getUserId().equals(UserSession.id())) return true;
        return "ARCHIVED".equals(artisan.getStatus()) && List.of("DISPLAY", "SOLD").contains(work.getStatus());
    }
    private long auctionCount(Long workId) {
        return auctions.selectCount(new LambdaQueryWrapper<HeritageAuction>().eq(HeritageAuction::getWorkId, workId));
    }
    private long activeAuctionCount(Long workId) {
        return auctions.selectCount(new LambdaQueryWrapper<HeritageAuction>()
                .eq(HeritageAuction::getWorkId, workId)
                .in(HeritageAuction::getStatus, List.of("DRAFT", "UPCOMING", "ONGOING")));
    }
    private void validate(ArtisanWork item) {
        if (item == null || item.getTitle() == null || item.getTitle().isBlank()) throw new BusinessException("作品名称不能为空");
        item.setTitle(item.getTitle().trim());
        if (item.getTitle().length() > 120) throw new BusinessException("作品名称不能超过120个字符");
        if (item.getPrice() != null && (item.getPrice().signum() < 0 || item.getPrice().precision() - item.getPrice().scale() > 8 || item.getPrice().scale() > 2))
            throw new BusinessException("参考价格必须是0至99999999.99之间、最多两位小数的金额");
        if (item.getImageUrl() != null && item.getImageUrl().length() > 500) throw new BusinessException("图片地址不能超过500个字符");
    }
    private void requireArtisanOwnerOrAdmin(ArtisanProfile artisan) {
        if (!isAdmin() && (!"ARTISAN".equals(UserSession.get().getRole())
                || artisan.getUserId() == null || !artisan.getUserId().equals(UserSession.id()))) {
            throw new BusinessException("只能维护自己的非遗作品");
        }
    }
}
