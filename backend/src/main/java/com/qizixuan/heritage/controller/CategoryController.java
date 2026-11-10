package com.qizixuan.heritage.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qizixuan.heritage.common.ApiResponse;
import com.qizixuan.heritage.common.BusinessException;
import com.qizixuan.heritage.common.UserSession;
import com.qizixuan.heritage.entity.CraftCategory;
import com.qizixuan.heritage.mapper.CraftCategoryMapper;
import com.qizixuan.heritage.mapper.ArtisanProfileMapper;
import com.qizixuan.heritage.entity.ArtisanProfile;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CraftCategoryMapper mapper;
    private final ArtisanProfileMapper artisans;
    public CategoryController(CraftCategoryMapper mapper,ArtisanProfileMapper artisans) { this.mapper = mapper;this.artisans=artisans; }
    @GetMapping public ApiResponse<List<CraftCategory>> list() { return ApiResponse.ok(mapper.selectList(new LambdaQueryWrapper<CraftCategory>().orderByAsc(CraftCategory::getName))); }
    @PostMapping public ApiResponse<CraftCategory> create(@RequestBody CraftCategory item) {
        requireAdmin(); validate(item);
        if (mapper.selectCount(new LambdaQueryWrapper<CraftCategory>().eq(CraftCategory::getName, item.getName())) > 0)
            throw new BusinessException("类目名称已存在");
        item.setId(null); item.setCreatedAt(LocalDateTime.now()); mapper.insert(item); return ApiResponse.ok(item);
    }
    @PutMapping("/{id}") public ApiResponse<Void> update(@PathVariable Long id, @RequestBody CraftCategory item) {
        requireAdmin(); validate(item);
        CraftCategory current = mapper.selectById(id);
        if (current == null) throw new BusinessException("技艺类目不存在");
        if (mapper.selectCount(new LambdaQueryWrapper<CraftCategory>().eq(CraftCategory::getName, item.getName()).ne(CraftCategory::getId, id)) > 0)
            throw new BusinessException("类目名称已存在");
        item.setId(id); item.setCreatedAt(current.getCreatedAt()); mapper.updateById(item); return ApiResponse.ok();
    }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) {
        requireAdmin();
        if(artisans.selectCount(new LambdaQueryWrapper<ArtisanProfile>().eq(ArtisanProfile::getCategoryId,id))>0)throw new BusinessException("该类目已被艺人档案使用，请先调整档案类目，不能直接删除");
        if (mapper.deleteById(id) == 0) throw new BusinessException("技艺类目不存在");
        return ApiResponse.ok();
    }
    private void requireAdmin() {
        if (UserSession.get() == null || !"ADMIN".equals(UserSession.get().getRole()))
            throw new BusinessException("仅管理员可维护技艺类目");
    }
    private void validate(CraftCategory item) {
        if (item == null || item.getName() == null || item.getName().isBlank()) throw new BusinessException("类目名称不能为空");
        item.setName(item.getName().trim());
        if (item.getName().length() > 80) throw new BusinessException("类目名称不能超过80个字符");
        if (item.getDescription() != null && item.getDescription().length() > 500) throw new BusinessException("类目说明不能超过500个字符");
    }
}
