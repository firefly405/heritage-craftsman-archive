package com.qizixuan.heritage;
import com.qizixuan.heritage.common.UserSession;
import com.qizixuan.heritage.controller.ArtisanController;
import com.qizixuan.heritage.entity.ArtisanProfile;
import com.qizixuan.heritage.mapper.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:archive_lifecycle;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","app.seed-admin.username=admin","app.seed-admin.password=123456"})
class ArchiveReviewLifecycleTests {
    @Autowired SysUserMapper users;
    @Autowired ArtisanProfileMapper artisans;
    @Autowired ArtisanController controller;
    @AfterEach void clear(){UserSession.clear();}
    @Test void rejectionClearsAfterResubmissionAndArchive(){
        var actor=users.selectById(1L);UserSession.set(actor);ArtisanProfile p=new ArtisanProfile();p.setName("流转测试艺人");p.setRegion("哈尔滨");p.setPhone("13800000000");p.setCategoryId(1L);p.setBirthYear(1990);p.setExperienceYears(10);p.setSkillDescription("传统剪纸技艺");p.setInheritanceStory("师承学习与传承");
        Long id=controller.create(p).data().getId();controller.submit(id);actor.setRole("REVIEWER");controller.review(id,new ArtisanController.ReviewRequest(false,"请补正"));assertEquals("请补正",artisans.selectById(id).getRejectReason());
        actor.setRole("ADMIN");controller.submit(id);assertNull(artisans.selectById(id).getRejectReason());actor.setRole("REVIEWER");controller.review(id,new ArtisanController.ReviewRequest(true,"通过"));
        assertEquals("ARCHIVED",artisans.selectById(id).getStatus());assertNull(artisans.selectById(id).getRejectReason());
        var history=controller.reviewHistory(id).data();assertEquals("APPROVE",history.get(0).getAction());assertEquals(4,history.size());
    }
}
