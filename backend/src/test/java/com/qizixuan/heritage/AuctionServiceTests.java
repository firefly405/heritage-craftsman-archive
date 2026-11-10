package com.qizixuan.heritage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qizixuan.heritage.common.BusinessException;
import com.qizixuan.heritage.common.UserSession;
import com.qizixuan.heritage.entity.ArtisanProfile;
import com.qizixuan.heritage.entity.ArtisanWork;
import com.qizixuan.heritage.entity.HeritageAuction;
import com.qizixuan.heritage.entity.SysUser;
import com.qizixuan.heritage.mapper.ArtisanProfileMapper;
import com.qizixuan.heritage.mapper.ArtisanWorkMapper;
import com.qizixuan.heritage.mapper.HeritageAuctionMapper;
import com.qizixuan.heritage.mapper.SysUserMapper;
import com.qizixuan.heritage.service.AuctionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:auction;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.sql.init.mode=always",
    "app.seed-admin.username=admin",
    "app.seed-admin.password=123456"
})
class AuctionServiceTests {
    @Autowired AuctionService service;
    @Autowired SysUserMapper users;
    @Autowired ArtisanProfileMapper artisans;
    @Autowired ArtisanWorkMapper works;
    @Autowired HeritageAuctionMapper auctions;

    @AfterEach
    void clearSession() {
        UserSession.clear();
    }

    @Test
    void auctionLifecycleEnforcesOwnershipPriceAndDeadline() {
        SysUser admin = users.selectById(1L);
        SysUser seller = user("seller_test", "传承人甲", "ARTISAN");
        SysUser bidder = user("bidder_test", "收藏者乙", "ARTISAN");
        SysUser viewer = user("viewer_test", "普通用户丙", "VIEWER");
        ArtisanProfile artisan = archivedArtisan(seller);
        ArtisanWork work = work(artisan);

        UserSession.set(admin);
        HeritageAuction auction = service.create(new AuctionService.AuctionRequest(
                work.getId(), "剪纸长卷公益拍卖", "用于非遗传承课程建设",
                new BigDecimal("100.00"), new BigDecimal("10.00"),
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1)));
        assertEquals("DRAFT", auction.getStatus());
        auction = service.publish(auction.getId());
        assertEquals("ONGOING", auction.getStatus());

        UserSession.set(seller);
        Long auctionId = auction.getId();
        assertThrows(BusinessException.class, () -> service.placeBid(auctionId, new BigDecimal("100.00")));

        UserSession.set(admin);
        BusinessException adminError = assertThrows(BusinessException.class,
                () -> service.placeBid(auctionId, new BigDecimal("100.00")));
        assertEquals("仅非遗艺人或普通用户账号可以参与竞价", adminError.getMessage());

        UserSession.set(viewer);
        HeritageAuction afterBid = service.placeBid(auctionId, new BigDecimal("100.00"));
        assertEquals(1, afterBid.getBidCount());
        assertEquals(0, new BigDecimal("100.00").compareTo(afterBid.getCurrentPrice()));
        assertNull(afterBid.getWinnerId(), "普通竞拍者不能看到用户主键");
        assertEquals("我", afterBid.getWinnerName());
        assertEquals(viewer.getId(), auctions.selectById(auctionId).getWinnerId());
        assertEquals(1, service.bidHistory(auctionId).size());

        UserSession.set(bidder);
        assertThrows(BusinessException.class, () -> service.placeBid(auctionId, new BigDecimal("105.00")));

        HeritageAuction persisted = auctions.selectById(auctionId);
        persisted.setEndTime(LocalDateTime.now().minusSeconds(1));
        auctions.updateById(persisted);
        assertThrows(BusinessException.class, () -> service.placeBid(auctionId, new BigDecimal("120.00")));
        assertEquals("ENDED", auctions.selectById(auctionId).getStatus(), "截止结算不能被异常事务回滚");
        assertEquals("ENDED", service.get(auctionId).getStatus());
        assertEquals("SOLD", works.selectById(work.getId()).getStatus());

        UserSession.set(viewer);
        assertEquals(auctionId, service.payableByCurrentUser(auctionId).getId());
        UserSession.set(bidder);
        assertThrows(BusinessException.class, () -> service.payableByCurrentUser(auctionId));
        UserSession.set(admin);
        assertThrows(BusinessException.class, () -> service.payableByCurrentUser(auctionId));

        UserSession.set(bidder);
        assertThrows(BusinessException.class, () -> service.create(new AuctionService.AuctionRequest(
                work.getId(), "无权创建", "", new BigDecimal("50.00"), new BigDecimal("5.00"),
                LocalDateTime.now().plusHours(2), LocalDateTime.now().plusHours(3))));
    }

    @Test
    void concurrentCreationAndBiddingKeepSingleWinner() throws Exception {
        SysUser admin = users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin"));
        SysUser seller = user("seller_concurrent", "并发卖家", "ARTISAN");
        SysUser bidderA = user("bidder_concurrent_a", "竞拍人甲", "ARTISAN");
        SysUser bidderB = user("bidder_concurrent_b", "竞拍人乙", "ARTISAN");
        ArtisanProfile artisan = archivedArtisan(seller);
        ArtisanWork work = work(artisan);
        AuctionService.AuctionRequest request = new AuctionService.AuctionRequest(
                work.getId(), "并发保护拍卖", "验证同一作品不会生成两个活动拍卖",
                new BigDecimal("100.00"), new BigDecimal("10.00"),
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));

        List<Long> createdIds = runTogether(
                () -> createAs(admin, request),
                () -> createAs(admin, request));
        assertEquals(1, createdIds.stream().filter(id -> id != null).count(), "同一作品只能创建一个活动拍卖");
        Long auctionId = createdIds.stream().filter(id -> id != null).findFirst().orElseThrow();

        UserSession.set(admin);
        service.publish(auctionId);
        List<Boolean> bidResults = runTogether(
                () -> bidAs(bidderA, auctionId),
                () -> bidAs(bidderB, auctionId));
        assertEquals(1, bidResults.stream().filter(Boolean::booleanValue).count(), "相同价格的并发出价只能有一个成功");

        UserSession.set(admin);
        HeritageAuction persisted = service.get(auctionId);
        assertEquals(1, persisted.getBidCount());
        assertEquals(0, new BigDecimal("100.00").compareTo(persisted.getCurrentPrice()));
        assertEquals(1, service.bidHistory(auctionId).size());
    }

    private Long createAs(SysUser user, AuctionService.AuctionRequest request) {
        try {
            UserSession.set(user);
            return service.create(request).getId();
        } catch (BusinessException expected) {
            return null;
        } finally {
            UserSession.clear();
        }
    }

    private boolean bidAs(SysUser user, Long auctionId) {
        try {
            UserSession.set(user);
            service.placeBid(auctionId, new BigDecimal("100.00"));
            return true;
        } catch (BusinessException expected) {
            return false;
        } finally {
            UserSession.clear();
        }
    }

    @SafeVarargs
    private <T> List<T> runTogether(Callable<T>... tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = java.util.Arrays.stream(tasks)
                    .map(task -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试启动超时");
                        return task.call();
                    }))
                    .toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<T> results = new java.util.ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(10, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private SysUser user(String username, String realName, String role) {
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

    private ArtisanProfile archivedArtisan(SysUser seller) {
        ArtisanProfile artisan = new ArtisanProfile();
        artisan.setUserId(seller.getId());
        artisan.setName("剪纸传承人甲");
        artisan.setRegion("黑龙江哈尔滨");
        artisan.setExperienceYears(18);
        artisan.setSkillDescription("传统剪纸技艺");
        artisan.setStatus("ARCHIVED");
        artisan.setCreatedAt(LocalDateTime.now());
        artisan.setUpdatedAt(LocalDateTime.now());
        artisans.insert(artisan);
        return artisan;
    }

    private ArtisanWork work(ArtisanProfile artisan) {
        ArtisanWork work = new ArtisanWork();
        work.setArtisanId(artisan.getId());
        work.setTitle("冰城印象剪纸长卷");
        work.setDescription("传统技法创作的主题长卷");
        work.setPrice(new BigDecimal("100.00"));
        work.setStatus("DISPLAY");
        work.setCreatedAt(LocalDateTime.now());
        works.insert(work);
        return work;
    }
}
