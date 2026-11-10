package com.qizixuan.heritage.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qizixuan.heritage.common.ApiResponse;
import com.qizixuan.heritage.common.BusinessException;
import com.qizixuan.heritage.common.PhoneNumbers;
import com.qizixuan.heritage.common.UserSession;
import com.qizixuan.heritage.entity.ArchiveReview;
import com.qizixuan.heritage.entity.ArtisanProfile;
import com.qizixuan.heritage.mapper.ArchiveReviewMapper;
import com.qizixuan.heritage.mapper.ArtisanProfileMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.qizixuan.heritage.service.ArchiveQuality;

@RestController
@RequestMapping("/api/artisans")
public class ArtisanController {
    private final ArtisanProfileMapper mapper;
    private final ArchiveReviewMapper reviews;
    public ArtisanController(ArtisanProfileMapper mapper, ArchiveReviewMapper reviews) { this.mapper = mapper; this.reviews = reviews; }

    @GetMapping
    public ApiResponse<List<ArtisanProfile>> list(@RequestParam(defaultValue = "") String keyword,
                                                  @RequestParam(defaultValue = "") String status) {
        LambdaQueryWrapper<ArtisanProfile> q = new LambdaQueryWrapper<ArtisanProfile>().orderByDesc(ArtisanProfile::getUpdatedAt);
        if (!keyword.isBlank()) q.and(x -> x.like(ArtisanProfile::getName, keyword).or().like(ArtisanProfile::getRegion, keyword));
        if (!canReviewAll()) q.and(x -> x.eq(ArtisanProfile::getStatus, "ARCHIVED").or().eq(ArtisanProfile::getUserId, UserSession.id()));
        if (!status.isBlank()) q.eq(ArtisanProfile::getStatus, status);
        return ApiResponse.ok(mapper.selectList(q).stream().map(this::sanitizeForViewer).toList());
    }
    @GetMapping("/quality") public ApiResponse<List<Map<String,Object>>> quality() {
        LambdaQueryWrapper<ArtisanProfile> query=new LambdaQueryWrapper<ArtisanProfile>().orderByDesc(ArtisanProfile::getUpdatedAt);
        // Quality results must not reveal missing private fields of other owners.
        if(!canReviewAll())query.eq(ArtisanProfile::getUserId,UserSession.id());
        return ApiResponse.ok(mapper.selectList(query).stream().map(ArchiveQuality::assess).toList());
    }
    @GetMapping("/{id}") public ApiResponse<ArtisanProfile> get(@PathVariable Long id) {
        ArtisanProfile item = require(id);
        if (!canReviewAll() && !isOwner(item) && !"ARCHIVED".equals(item.getStatus())) throw new BusinessException("无权查看该艺人档案");
        return ApiResponse.ok(sanitizeForViewer(item));
    }
    @PostMapping public ApiResponse<ArtisanProfile> create(@RequestBody ArtisanProfile item) {
        if(UserSession.get()==null || !List.of("ADMIN","ARTISAN").contains(UserSession.get().getRole()))throw new BusinessException("仅管理员或非遗艺人可以创建档案");
        validate(item);
        item.setId(null);
        if (!isAdmin() || item.getUserId() == null) item.setUserId(UserSession.id());
        item.setStatus("DRAFT"); item.setCreatedAt(LocalDateTime.now()); item.setUpdatedAt(LocalDateTime.now()); mapper.insert(item); return ApiResponse.ok(item);
    }
    @Transactional
    @PutMapping("/{id}") public ApiResponse<Void> update(@PathVariable Long id, @RequestBody ArtisanProfile item) {
        validate(item);
        ArtisanProfile current = requireLocked(id); requireOwnerOrAdmin(current);
        if (!isAdmin() && !List.of("DRAFT", "REJECTED").contains(current.getStatus()))
            throw new BusinessException("已提交或已归档档案不能直接修改");
        item.setId(id); item.setUserId(current.getUserId()); item.setStatus(current.getStatus());
        item.setRejectReason(current.getRejectReason()); item.setCreatedAt(current.getCreatedAt());
        item.setUpdatedAt(LocalDateTime.now()); mapper.updateById(item); return ApiResponse.ok();
    }
    @Transactional
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) {
        ArtisanProfile item = requireLocked(id); requireOwnerOrAdmin(item);
        if (!List.of("DRAFT", "REJECTED").contains(item.getStatus()))
            throw new BusinessException("只有草稿或已驳回档案可以删除");
        mapper.deleteById(id); return ApiResponse.ok();
    }
    @Transactional
    @PostMapping("/{id}/submit") public ApiResponse<Void> submit(@PathVariable Long id) {
        ArtisanProfile item = requireLocked(id); requireOwnerOrAdmin(item);
        if (!List.of("DRAFT", "REJECTED").contains(item.getStatus())) throw new BusinessException("只有草稿或已驳回档案可以提交审核");
        if(!Boolean.TRUE.equals(ArchiveQuality.assess(item).get("ready")))throw new BusinessException("请先通过档案完整度检查，补齐缺项并修正一致性问题");
        item.setStatus("SUBMITTED"); item.setRejectReason(null); item.setUpdatedAt(LocalDateTime.now());
        mapper.update(item,new LambdaUpdateWrapper<ArtisanProfile>().eq(ArtisanProfile::getId,id).set(ArtisanProfile::getRejectReason,null));
        record(id, "SUBMIT", "提交档案审核"); return ApiResponse.ok();
    }
    @Transactional
    @PostMapping("/{id}/review") public ApiResponse<Void> review(@PathVariable Long id, @RequestBody ReviewRequest request) {
        if (UserSession.get() == null || !"REVIEWER".equals(UserSession.get().getRole())) throw new BusinessException("仅审核员可审核");
        ArtisanProfile item = requireLocked(id);
        if (!"SUBMITTED".equals(item.getStatus())) throw new BusinessException("只有待审核档案可以审核");
        if (request == null) throw new BusinessException("审核结果不能为空");
        String comment = request.comment() == null ? "" : request.comment().trim();
        if (comment.length() > 500) throw new BusinessException("审核意见不能超过500个字符");
        if (!request.approved() && comment.isBlank()) throw new BusinessException("驳回时必须填写原因");
        if (request.approved()) { item.setStatus("ARCHIVED"); item.setRejectReason(null); record(id, "APPROVE", comment); }
        else { item.setStatus("REJECTED"); item.setRejectReason(comment); record(id, "REJECT", comment); }
        item.setUpdatedAt(LocalDateTime.now());
        if(request.approved())mapper.update(item,new LambdaUpdateWrapper<ArtisanProfile>().eq(ArtisanProfile::getId,id).set(ArtisanProfile::getRejectReason,null));
        else mapper.updateById(item);
        return ApiResponse.ok();
    }
    @GetMapping("/{id}/reviews") public ApiResponse<List<ArchiveReview>> reviewHistory(@PathVariable Long id) {
        ArtisanProfile item = require(id);
        if (!canReviewAll() && !isOwner(item)) throw new BusinessException("无权查看该档案的审核记录");
        List<ArchiveReview> result = reviews.selectList(new LambdaQueryWrapper<ArchiveReview>()
                .eq(ArchiveReview::getArtisanId, id).orderByDesc(ArchiveReview::getCreatedAt).orderByDesc(ArchiveReview::getId));
        if (!canReviewAll()) result = result.stream().map(this::hideReviewerId).toList();
        return ApiResponse.ok(result);
    }
    private ArtisanProfile require(Long id) { ArtisanProfile item = mapper.selectById(id); if (item == null) throw new BusinessException("艺人档案不存在"); return item; }
    private ArtisanProfile requireLocked(Long id) { ArtisanProfile item = mapper.lockById(id); if (item == null) throw new BusinessException("艺人档案不存在"); return item; }
    private boolean isAdmin() { return "ADMIN".equals(UserSession.get().getRole()); }
    private boolean canReviewAll() { return UserSession.get() != null && List.of("ADMIN", "REVIEWER").contains(UserSession.get().getRole()); }
    private boolean isOwner(ArtisanProfile item) { return item.getUserId() != null && item.getUserId().equals(UserSession.id()); }
    private void requireOwnerOrAdmin(ArtisanProfile item) {
        if (!isAdmin() && (!"ARTISAN".equals(UserSession.get().getRole()) || !isOwner(item))) {
            throw new BusinessException("只能维护自己的艺人档案");
        }
    }
    private ArtisanProfile sanitizeForViewer(ArtisanProfile item) {
        if (canReviewAll() || isOwner(item)) return item;
        ArtisanProfile copy = new ArtisanProfile();
        BeanUtils.copyProperties(item, copy);
        copy.setUserId(null); copy.setPhone(null); copy.setRejectReason(null);
        return copy;
    }
    private ArchiveReview hideReviewerId(ArchiveReview item) {
        ArchiveReview copy = new ArchiveReview();
        BeanUtils.copyProperties(item, copy); copy.setReviewerId(null); return copy;
    }
    private void validate(ArtisanProfile item) {
        if (item == null || item.getName() == null || item.getName().isBlank()) throw new BusinessException("艺人姓名不能为空");
        item.setName(item.getName().trim());
        if (item.getName().length() > 50) throw new BusinessException("艺人姓名不能超过50个字符");
        if (item.getGender() != null && item.getGender().length() > 10) throw new BusinessException("性别内容不能超过10个字符");
        if (item.getBirthYear() != null && (item.getBirthYear() < 1900 || item.getBirthYear() > LocalDate.now().getYear()))
            throw new BusinessException("出生年份不在有效范围内");
        if (item.getExperienceYears() != null && (item.getExperienceYears() < 0 || item.getExperienceYears() > 150))
            throw new BusinessException("从艺年限不在有效范围内");
        item.setPhone(PhoneNumbers.normalize(item.getPhone()));
        if (item.getRegion() != null && item.getRegion().length() > 100) throw new BusinessException("所在地区不能超过100个字符");
        if (item.getAvatarUrl() != null && item.getAvatarUrl().length() > 500) throw new BusinessException("头像地址不能超过500个字符");
    }
    private void record(Long id, String action, String comment) { ArchiveReview r = new ArchiveReview(); r.setArtisanId(id); r.setReviewerId(UserSession.id()); r.setAction(action); r.setComment(comment); r.setCreatedAt(LocalDateTime.now()); reviews.insert(r); }
    public record ReviewRequest(boolean approved, String comment) {}
}
