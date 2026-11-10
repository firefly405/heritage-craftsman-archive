package com.qizixuan.heritage;
import com.qizixuan.heritage.common.*;
import com.qizixuan.heritage.controller.*;
import com.qizixuan.heritage.entity.*;
import com.qizixuan.heritage.mapper.*;
import com.qizixuan.heritage.service.AuctionService;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeatureCompletionTests {
    @Test void auctionAndLogPagesSupportRefresh() throws Exception {var paths=java.util.List.of(SpaController.class.getMethod("index").getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value());assertTrue(paths.contains("/auctions"));assertTrue(paths.contains("/auction-logs"));}
    @BeforeEach void login(){SysUser u=new SysUser();u.setId(1L);u.setRole("ADMIN");u.setRealName("测试管理员");UserSession.set(u);}
    @AfterEach void clear(){UserSession.clear();}
    private void artisan(){UserSession.get().setRole("ARTISAN");}
    private Announcement announcement(boolean published){Announcement a=new Announcement();a.setId(10L);a.setTitle("测试公告");a.setContent("公告正文");a.setPublished(published);return a;}
    @Test void publishedAnnouncementCanBeRead(){AnnouncementMapper m=mock(AnnouncementMapper.class);when(m.selectById(10L)).thenReturn(announcement(true));artisan();assertEquals("公告正文",new AnnouncementController(m).get(10L).data().getContent());}
    @Test void draftCannotBeReadByArtisan(){AnnouncementMapper m=mock(AnnouncementMapper.class);when(m.selectById(10L)).thenReturn(announcement(false));artisan();assertThrows(BusinessException.class,()->new AnnouncementController(m).get(10L));}
    @Test void administratorCanReadDraft(){AnnouncementMapper m=mock(AnnouncementMapper.class);when(m.selectById(10L)).thenReturn(announcement(false));assertFalse(new AnnouncementController(m).get(10L).data().getPublished());}
    @Test void missingAnnouncementRejected(){assertThrows(BusinessException.class,()->new AnnouncementController(mock(AnnouncementMapper.class)).get(999L));}
    @Test void announcementCannotExceedDatabaseTextLimit(){Announcement a=announcement(true);a.setContent("字".repeat(22000));AnnouncementMapper m=mock(AnnouncementMapper.class);assertThrows(BusinessException.class,()->new AnnouncementController(m).create(a));verifyNoInteractions(m);}
    @Test void normalUserCannotPublishAnnouncement(){artisan();AnnouncementMapper m=mock(AnnouncementMapper.class);assertThrows(BusinessException.class,()->new AnnouncementController(m).create(announcement(true)));verifyNoInteractions(m);}
    @Test void referencedCategoryCannotBeDeleted(){CraftCategoryMapper c=mock(CraftCategoryMapper.class);ArtisanProfileMapper a=mock(ArtisanProfileMapper.class);when(a.selectCount(any())).thenReturn(1L);assertThrows(BusinessException.class,()->new CategoryController(c,a).delete(1L));verify(c,never()).deleteById(any(Long.class));}
    @Test void unusedCategoryCanBeDeleted(){CraftCategoryMapper c=mock(CraftCategoryMapper.class);ArtisanProfileMapper a=mock(ArtisanProfileMapper.class);when(a.selectCount(any())).thenReturn(0L);when(c.deleteById(1L)).thenReturn(1);new CategoryController(c,a).delete(1L);verify(c).deleteById(1L);}
    @Test void logsRejectUnboundedPagination(){OperationLogController c=new OperationLogController(mock(OperationLogMapper.class));assertThrows(BusinessException.class,()->c.page(0,20,""));assertThrows(BusinessException.class,()->c.page(1,1000,""));}
    @Test void logsAreAdministratorOnly(){artisan();OperationLogController c=new OperationLogController(mock(OperationLogMapper.class));assertThrows(BusinessException.class,c::list);assertThrows(BusinessException.class,()->c.page(1,20,""));}
    @Test void reviewerCannotForgeArtisanCreation(){UserSession.get().setRole("REVIEWER");ArtisanProfileMapper m=mock(ArtisanProfileMapper.class);assertThrows(BusinessException.class,()->new ArtisanController(m,mock(ArchiveReviewMapper.class)).create(new ArtisanProfile()));verifyNoInteractions(m);}
    @Test void statsDoNotExposeOthersDraftRegions(){
        artisan();ArtisanProfileMapper a=mock(ArtisanProfileMapper.class);ArtisanProfile visible=new ArtisanProfile();visible.setId(1L);visible.setUserId(9L);visible.setStatus("ARCHIVED");visible.setRegion("公开地区");
        ArtisanProfile own=new ArtisanProfile();own.setId(2L);own.setUserId(1L);own.setStatus("DRAFT");own.setRegion("本人地区");
        ArtisanProfile hidden=new ArtisanProfile();hidden.setId(3L);hidden.setUserId(8L);hidden.setStatus("DRAFT");hidden.setRegion("不应公开地区");when(a.selectList(null)).thenReturn(List.of(visible,own,hidden));
        ArtisanWorkMapper w=mock(ArtisanWorkMapper.class);when(w.selectList(null)).thenReturn(List.of());AnnouncementMapper n=mock(AnnouncementMapper.class);when(n.selectList(null)).thenReturn(List.of());AuctionService s=mock(AuctionService.class);when(s.list("","")).thenReturn(List.of());
        var stats=new StatsController(a,w,mock(CraftCategoryMapper.class),n,mock(HeritageAuctionMapper.class),s).overview().data();assertEquals(2,stats.get("artisanCount"));assertFalse(((Map<?,?>)stats.get("regionData")).containsKey("不应公开地区"));
    }
}
