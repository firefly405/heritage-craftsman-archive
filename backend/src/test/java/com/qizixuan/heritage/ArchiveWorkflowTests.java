package com.qizixuan.heritage;
import com.qizixuan.heritage.controller.ArtisanController;
import com.qizixuan.heritage.common.*;
import com.qizixuan.heritage.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:archiveflow;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","app.seed-admin.username=admin","app.seed-admin.password=123456"})
@Transactional class ArchiveWorkflowTests {
 @Autowired ArtisanController api;
 @BeforeEach void login(){user(900L,"ARTISAN");}
 @AfterEach void clear(){UserSession.clear();}
 private void user(long id,String role){var u=new SysUser();u.setId(id);u.setRole(role);UserSession.set(u);}
 private ArtisanProfile profile(){var p=new ArtisanProfile();p.setName("测试艺人");p.setRegion("浙江");p.setCategoryId(1L);p.setPhone("13800000000");p.setBirthYear(1990);p.setExperienceYears(10);p.setSkillDescription("木雕技艺");p.setInheritanceStory("师承学习");return p;}
 @Test void incompleteCannotSubmit(){var p=profile();p.setPhone(null);api.create(p);assertThrows(BusinessException.class,()->api.submit(p.getId()));}
 @Test void invalidMainlandMobileCannotBeSaved(){var p=profile();p.setPhone("1380000000");assertThrows(BusinessException.class,()->api.create(p));}
 @Test void anotherOwnerCannotViewDraft(){var p=profile();api.create(p);user(901,"ARTISAN");assertThrows(BusinessException.class,()->api.get(p.getId()));}
 @Test void administratorCannotReview(){var p=profile();api.create(p);api.submit(p.getId());user(901,"ADMIN");assertThrows(BusinessException.class,()->api.review(p.getId(),new ArtisanController.ReviewRequest(true,"通过")));}
 @Test void submissionLocksEditsAndReviewIsSingleUse(){var p=profile();api.create(p);api.submit(p.getId());assertThrows(BusinessException.class,()->api.update(p.getId(),profile()));user(902,"REVIEWER");api.review(p.getId(),new ArtisanController.ReviewRequest(true,"通过"));assertThrows(BusinessException.class,()->api.review(p.getId(),new ArtisanController.ReviewRequest(true,"重复")));}
}
