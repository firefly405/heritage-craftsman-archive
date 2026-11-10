package com.qizixuan.heritage;
import com.qizixuan.heritage.entity.ArtisanProfile;
import com.qizixuan.heritage.service.ArchiveQuality;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ArchiveQualityTests{
 @Test void incompleteCannotPass(){var r=ArchiveQuality.assess(new ArtisanProfile());assertEquals(0,r.get("score"));assertEquals(false,r.get("ready"));}
 @Test void futureBirthAndImpossibleExperienceWarn(){ArtisanProfile p=new ArtisanProfile();p.setBirthYear(2100);p.setExperienceYears(30);assertFalse(((java.util.List<?>)ArchiveQuality.assess(p).get("warnings")).isEmpty());}
 @Test void invalidMainlandMobileWarns(){ArtisanProfile p=new ArtisanProfile();p.setPhone("12800000000");assertTrue(((java.util.List<?>)ArchiveQuality.assess(p).get("warnings")).contains("手机号格式不正确"));}
 @Test void completeProfilePasses(){ArtisanProfile p=new ArtisanProfile();p.setName("艺人");p.setRegion("地区");p.setCategoryId(1L);p.setPhone("13800000000");p.setBirthYear(1980);p.setExperienceYears(20);p.setSkillDescription("工艺");p.setInheritanceStory("传承");assertEquals(true,ArchiveQuality.assess(p).get("ready"));}
}
