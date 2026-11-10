package com.qizixuan.heritage;

import com.qizixuan.heritage.common.BusinessException;
import com.qizixuan.heritage.common.UserSession;
import com.qizixuan.heritage.controller.*;
import com.qizixuan.heritage.entity.*;
import com.qizixuan.heritage.mapper.*;
import com.qizixuan.heritage.service.AuctionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:security;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "app.seed-admin.username=admin",
        "app.seed-admin.password=123456"
})
class SecurityBoundaryTests {
    @Autowired CategoryController categories;
    @Autowired AnnouncementController announcements;
    @Autowired OperationLogController operationLogs;
    @Autowired ArtisanController artisanController;
    @Autowired WorkController workController;
    @Autowired UserController userController;
    @Autowired AuctionService auctionService;
    @Autowired SysUserMapper users;
    @Autowired ArtisanProfileMapper artisans;
    @Autowired ArtisanWorkMapper works;

    @AfterEach
    void clearSession() {
        UserSession.clear();
    }

    @Test
    void ordinaryUserCannotMutateAdministrativeDataOrReadLogs() {
        SysUser ordinary = createUser("security_viewer_1", "普通用户一", "VIEWER");
        UserSession.set(ordinary);

        CraftCategory category = new CraftCategory();
        category.setName("越权类目");
        assertThrows(BusinessException.class, () -> categories.create(category));

        Announcement announcement = new Announcement();
        announcement.setTitle("越权公告");
        announcement.setContent("不应被创建");
        assertThrows(BusinessException.class, () -> announcements.create(announcement));
        assertThrows(BusinessException.class, operationLogs::list);
    }

    @Test
    void viewerCannotMaintainOwnedArchivesOrWorks() {
        SysUser viewer = createUser("security_read_only", "只读用户", "VIEWER");
        ArtisanProfile profile = createProfile(viewer, "只读关联档案", "DRAFT", null);
        UserSession.set(viewer);

        ArtisanProfile edited = new ArtisanProfile();
        edited.setName(profile.getName());
        assertThrows(BusinessException.class, () -> artisanController.update(profile.getId(), edited));

        ArtisanWork work = new ArtisanWork();
        work.setArtisanId(profile.getId());
        work.setTitle("不允许新增的作品");
        assertThrows(BusinessException.class, () -> workController.create(work));
    }

    @Test
    void ordinaryUserSeesOnlyPublicOrOwnArchivesAndPrivateFieldsAreMasked() {
        SysUser owner = createUser("security_owner", "档案所有者", "ARTISAN");
        SysUser viewer = createUser("security_viewer", "档案浏览者", "ARTISAN");
        ArtisanProfile archived = createProfile(owner, "公开归档艺人", "ARCHIVED", "13800000000");
        ArtisanProfile draft = createProfile(owner, "未公开草稿艺人", "DRAFT", "13900000000");
        ArtisanProfile submitted = createProfile(owner, "待审核艺人", "SUBMITTED", "13700000000");
        ArtisanWork privateWork = new ArtisanWork();
        privateWork.setArtisanId(draft.getId());
        privateWork.setTitle("草稿档案私有作品");
        privateWork.setStatus("DISPLAY");
        privateWork.setCreatedAt(LocalDateTime.now());
        works.insert(privateWork);

        UserSession.set(viewer);
        List<ArtisanProfile> visible = artisanController.list("", "").data();
        ArtisanProfile publicCopy = visible.stream().filter(item -> item.getId().equals(archived.getId())).findFirst().orElseThrow();
        assertNull(publicCopy.getUserId());
        assertNull(publicCopy.getPhone());
        assertTrue(visible.stream().noneMatch(item -> item.getId().equals(draft.getId())));
        assertThrows(BusinessException.class, () -> artisanController.get(draft.getId()));
        assertThrows(BusinessException.class, () -> artisanController.reviewHistory(archived.getId()));
        assertTrue(workController.list(null).data().stream().noneMatch(item -> item.getId().equals(privateWork.getId())));

        UserSession.set(owner);
        ArtisanProfile editRequest = new ArtisanProfile();
        editRequest.setName(submitted.getName());
        editRequest.setRegion(submitted.getRegion());
        editRequest.setExperienceYears(submitted.getExperienceYears());
        assertThrows(BusinessException.class, () -> artisanController.update(submitted.getId(), editRequest));
    }

    @Test
    void activeAuctionProtectsWorkAndAdministratorCannotLockOutCurrentAccount() {
        SysUser admin = users.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "admin"));
        SysUser owner = createUser("security_seller", "安全测试卖家", "ARTISAN");
        ArtisanProfile profile = createProfile(owner, "拍卖保护艺人", "ARCHIVED", null);
        ArtisanWork work = new ArtisanWork();
        work.setArtisanId(profile.getId());
        work.setTitle("受保护作品");
        work.setDescription("已有拍卖时不可隐藏或删除");
        work.setPrice(new BigDecimal("500.00"));
        work.setStatus("DISPLAY");
        work.setCreatedAt(LocalDateTime.now());
        works.insert(work);

        UserSession.set(admin);
        auctionService.create(new AuctionService.AuctionRequest(work.getId(), "作品保护拍卖", "并发和关联完整性测试",
                new BigDecimal("500.00"), new BigDecimal("20.00"),
                LocalDateTime.now().plusMinutes(10), LocalDateTime.now().plusHours(1)));

        ArtisanWork edited = new ArtisanWork();
        edited.setArtisanId(profile.getId());
        edited.setTitle(work.getTitle());
        edited.setDescription(work.getDescription());
        edited.setPrice(work.getPrice());
        edited.setStatus("HIDDEN");
        assertThrows(BusinessException.class, () -> workController.update(work.getId(), edited));
        assertThrows(BusinessException.class, () -> workController.delete(work.getId()));
        assertThrows(BusinessException.class,
                () -> userController.status(admin.getId(), new UserController.StatusRequest(false)));
        assertThrows(BusinessException.class,
                () -> userController.role(admin.getId(), new UserController.RoleRequest("ARTISAN")));
    }

    private SysUser createUser(String username, String realName, String role) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword("test-only");
        user.setRealName(realName);
        user.setRole(role);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        users.insert(user);
        return user;
    }

    private ArtisanProfile createProfile(SysUser owner, String name, String status, String phone) {
        ArtisanProfile profile = new ArtisanProfile();
        profile.setUserId(owner.getId());
        profile.setName(name);
        profile.setPhone(phone);
        profile.setRegion("黑龙江哈尔滨");
        profile.setExperienceYears(10);
        profile.setStatus(status);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        artisans.insert(profile);
        return profile;
    }
}
