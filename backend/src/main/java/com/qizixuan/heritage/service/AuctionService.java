package com.qizixuan.heritage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qizixuan.heritage.common.BusinessException;
import com.qizixuan.heritage.common.UserSession;
import com.qizixuan.heritage.entity.*;
import com.qizixuan.heritage.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuctionService {
    private static final List<String> ACTIVE_STATUSES = List.of("DRAFT", "UPCOMING", "ONGOING");
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");

    private final HeritageAuctionMapper auctions;
    private final AuctionBidMapper bids;
    private final ArtisanWorkMapper works;
    private final ArtisanProfileMapper artisans;
    private final OperationLogMapper operationLogs;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate bidTransaction;
    private final TransactionTemplate lifecycleTransaction;

    public AuctionService(HeritageAuctionMapper auctions,
                          AuctionBidMapper bids,
                          ArtisanWorkMapper works,
                          ArtisanProfileMapper artisans,
                          OperationLogMapper operationLogs,
                          PlatformTransactionManager transactionManager) {
        this.auctions = auctions;
        this.bids = bids;
        this.works = works;
        this.artisans = artisans;
        this.operationLogs = operationLogs;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.bidTransaction = new TransactionTemplate(transactionManager);
        this.lifecycleTransaction = new TransactionTemplate(transactionManager);
        this.lifecycleTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<HeritageAuction> list(String keyword, String status) {
        synchronizeActiveAuctions();
        LambdaQueryWrapper<HeritageAuction> query = new LambdaQueryWrapper<HeritageAuction>()
                .orderByAsc(HeritageAuction::getStatus)
                .orderByDesc(HeritageAuction::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) {
            query.and(q -> q.like(HeritageAuction::getTitle, keyword.trim())
                    .or().like(HeritageAuction::getSellerName, keyword.trim()));
        }
        SysUser viewer = UserSession.get();
        if (viewer != null && !"ADMIN".equals(viewer.getRole())) {
            query.and(q -> q.in(HeritageAuction::getStatus, List.of("UPCOMING", "ONGOING", "ENDED"))
                    .or().eq(HeritageAuction::getSellerId, viewer.getId()));
        }
        if (status != null && !status.isBlank()) query.eq(HeritageAuction::getStatus, status.trim());
        return auctions.selectList(query).stream().map(this::sanitizeForViewer).toList();
    }

    public HeritageAuction get(Long id) {
        HeritageAuction auction = synchronize(require(id));
        requireVisible(auction);
        return sanitizeForViewer(auction);
    }

    public HeritageAuction payableByCurrentUser(Long id) {
        HeritageAuction auction = synchronize(require(id));
        SysUser user = UserSession.get();
        if (user == null || !List.of("ARTISAN", "VIEWER").contains(user.getRole())
                || !"ENDED".equals(auction.getStatus()) || auction.getBidCount() == null
                || auction.getBidCount() <= 0 || !user.getId().equals(auction.getWinnerId())) {
            throw new BusinessException("仅成交人可以发起支付宝沙箱演示支付");
        }
        return auction;
    }

    public List<AuctionBid> bidHistory(Long id) {
        HeritageAuction auction = synchronize(require(id));
        requireVisible(auction);
        boolean manager = canManage(auction);
        Long viewerId = UserSession.id();
        return bids.selectList(new LambdaQueryWrapper<AuctionBid>()
                .eq(AuctionBid::getAuctionId, id)
                .orderByDesc(AuctionBid::getBidTime)
                .orderByDesc(AuctionBid::getId)).stream()
                .map(bid -> manager ? bid : maskedBid(bid, viewerId))
                .toList();
    }

    public HeritageAuction create(AuctionRequest request) {
        Long workId = requireWorkId(request);
        synchronizeWorkAuctions(workId);
        return writeTransaction.execute(transaction -> {
            HeritageAuction auction = new HeritageAuction();
            populateEditableFields(auction, request, null);
            auction.setId(null);
            auction.setCurrentPrice(auction.getStartPrice());
            auction.setBidCount(0);
            auction.setStatus("DRAFT");
            auction.setCreatedAt(LocalDateTime.now());
            auction.setUpdatedAt(LocalDateTime.now());
            auctions.insert(auction);
            record("创建拍卖", auction, "创建拍卖草稿");
            return auction;
        });
    }

    public HeritageAuction update(Long id, AuctionRequest request) {
        HeritageAuction visible = require(id);
        requireManager(visible);
        Long workId = requireWorkId(request);
        synchronizeWorkAuctions(workId);
        return writeTransaction.execute(transaction -> {
            HeritageAuction auction = require(id);
            requireManager(auction);
            if (!"DRAFT".equals(auction.getStatus())) throw new BusinessException("仅拍卖草稿可以修改");
            populateEditableFields(auction, request, id);
            if (auctions.updateDraft(auction) != 1) throw new BusinessException("拍卖状态已经变化，请刷新后重试");
            auction = require(id);
            record("修改拍卖", auction, "修改拍卖时间与价格规则");
            return auction;
        });
    }

    @Transactional
    public HeritageAuction publish(Long id) {
        HeritageAuction auction = require(id);
        requireManager(auction);
        if (!"DRAFT".equals(auction.getStatus())) throw new BusinessException("只有草稿状态可以发布");
        ArtisanWork work = works.lockById(auction.getWorkId());
        ArtisanProfile artisan = work == null ? null : artisans.lockById(work.getArtisanId());
        if (work == null || !"DISPLAY".equals(work.getStatus())) throw new BusinessException("作品已不处于正常展示状态，不能发布拍卖");
        if (artisan == null || !"ARCHIVED".equals(artisan.getStatus())) throw new BusinessException("作品对应的艺人档案未归档，不能发布拍卖");
        if (auctions.publish(id) != 1) throw new BusinessException("拍卖已过期或状态已经变化，请刷新后重试");
        auction = require(id);
        record("发布拍卖", auction, "拍卖已发布，状态为" + auction.getStatus());
        return auction;
    }

    public HeritageAuction placeBid(Long id, BigDecimal rawAmount) {
        HeritageAuction auction = synchronize(require(id));
        if (!"ONGOING".equals(auction.getStatus())) throw new BusinessException("当前拍卖不在竞价时间内");
        SysUser bidder = UserSession.get();
        if (bidder == null) throw new BusinessException("请先登录后再出价");
        if (!"ARTISAN".equals(bidder.getRole()) && !"VIEWER".equals(bidder.getRole())) {
            throw new BusinessException("仅非遗艺人或普通用户账号可以参与竞价");
        }
        if (auction.getSellerId() != null && auction.getSellerId().equals(bidder.getId())) {
            throw new BusinessException("拍卖发起人不能参与自己的拍卖");
        }
        BigDecimal amount = money(rawAmount, "出价金额");
        int currentBidCount = auction.getBidCount() == null ? 0 : auction.getBidCount();
        BigDecimal minimum = currentBidCount == 0
                ? auction.getStartPrice()
                : auction.getCurrentPrice().add(auction.getMinIncrement()).setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(minimum) < 0) {
            throw new BusinessException("最低有效出价为￥" + minimum.toPlainString());
        }
        HeritageAuction expected = auction;
        bidTransaction.executeWithoutResult(transaction -> {
            int changed = auctions.placeBid(expected.getId(), amount, bidder.getId(), bidder.getRealName(),
                    expected.getCurrentPrice(), currentBidCount);
            if (changed != 1) throw new BusinessException("其他用户刚刚提交了更高出价或拍卖已经结束，请刷新后重试");
            AuctionBid bid = new AuctionBid();
            bid.setAuctionId(expected.getId());
            bid.setBidderId(bidder.getId());
            bid.setBidderName(bidder.getRealName());
            bid.setBidAmount(amount);
            bid.setBidTime(LocalDateTime.now());
            bids.insert(bid);
            record("参与竞价", expected, bidder.getRealName() + " 出价￥" + amount.toPlainString());
        });
        return sanitizeForViewer(require(id));
    }

    public HeritageAuction close(Long id) {
        requireAdmin();
        HeritageAuction synchronizedAuction = synchronize(require(id));
        return writeTransaction.execute(transaction -> {
            requireAdmin();
            HeritageAuction auction = require(synchronizedAuction.getId());
            if (List.of("ENDED", "CANCELLED").contains(auction.getStatus())) throw new BusinessException("该拍卖已经结束");
            if ("DRAFT".equals(auction.getStatus())) throw new BusinessException("草稿不能直接结束，请先发布或取消");
            if (auctions.close(id) != 1) throw new BusinessException("拍卖状态已经变化，请刷新后重试");
            auction = require(id);
            markWorkSold(auction);
            record("结束拍卖", auction, auction.getBidCount() > 0 ? "拍卖成交" : "流拍，无有效出价");
            return auction;
        });
    }

    public HeritageAuction cancel(Long id) {
        HeritageAuction visible = require(id);
        requireManager(visible);
        HeritageAuction synchronizedAuction = synchronize(visible);
        return writeTransaction.execute(transaction -> {
            HeritageAuction auction = require(synchronizedAuction.getId());
            requireManager(auction);
            if (!List.of("DRAFT", "UPCOMING").contains(auction.getStatus())) {
                throw new BusinessException("只有草稿或尚未开始的拍卖可以取消");
            }
            if (auction.getBidCount() != null && auction.getBidCount() > 0) throw new BusinessException("已有出价的拍卖不能取消");
            if (auctions.cancel(id) != 1) throw new BusinessException("拍卖状态已经变化，无法取消");
            auction = require(id);
            record("取消拍卖", auction, "取消尚未开始的拍卖");
            return auction;
        });
    }

    private void populateEditableFields(HeritageAuction auction, AuctionRequest request, Long editingId) {
        // 作品行锁串行化同一作品的创建/改绑操作；锁后的查询属于当前读，
        // 因此能看到前一事务刚创建的拍卖，避免出现两个活动拍卖。
        ArtisanWork work = works.lockById(request.workId());
        if (work == null) throw new BusinessException("所选非遗作品不存在");
        ArtisanProfile artisan = artisans.lockById(work.getArtisanId());
        if (artisan == null) throw new BusinessException("作品对应的艺人档案不存在");
        if (!"ARCHIVED".equals(artisan.getStatus())) throw new BusinessException("仅已审核归档的艺人作品可以参加拍卖");
        if (!"DISPLAY".equals(work.getStatus())) throw new BusinessException("只有正常展示中的作品可以参加拍卖");
        requireWorkManager(artisan);

        LambdaQueryWrapper<HeritageAuction> duplicate = new LambdaQueryWrapper<HeritageAuction>()
                .eq(HeritageAuction::getWorkId, work.getId())
                .in(HeritageAuction::getStatus, ACTIVE_STATUSES);
        if (editingId != null) duplicate.ne(HeritageAuction::getId, editingId);
        if (auctions.selectCount(duplicate) > 0) throw new BusinessException("该作品已经存在未结束的拍卖");

        BigDecimal startPrice = money(request.startPrice(), "起拍价");
        BigDecimal minIncrement = money(request.minIncrement(), "最低加价幅度");
        LocalDateTime startTime = request.startTime();
        LocalDateTime endTime = request.endTime();
        if (startTime == null || endTime == null) throw new BusinessException("请填写拍卖开始和结束时间");
        if (!endTime.isAfter(startTime)) throw new BusinessException("拍卖结束时间必须晚于开始时间");

        auction.setWorkId(work.getId());
        auction.setSellerId(artisan.getUserId() == null ? UserSession.id() : artisan.getUserId());
        auction.setSellerName(artisan.getName());
        String title = request.title() == null || request.title().isBlank() ? work.getTitle() : request.title().trim();
        if (title == null || title.isBlank()) throw new BusinessException("请填写拍卖标题");
        if (title.length() > 150) throw new BusinessException("拍卖标题不能超过150个字符");
        auction.setTitle(title);
        auction.setCoverUrl(work.getImageUrl());
        String description = request.description() == null || request.description().isBlank()
                ? work.getDescription() : request.description().trim();
        if (description != null && description.getBytes(StandardCharsets.UTF_8).length > 65000)
            throw new BusinessException("拍卖说明内容过长，请控制在65000字节以内");
        auction.setDescription(description);
        auction.setStartPrice(startPrice);
        auction.setMinIncrement(minIncrement);
        auction.setStartTime(startTime);
        auction.setEndTime(endTime);
    }

    private Long requireWorkId(AuctionRequest request) {
        if (request == null || request.workId() == null) throw new BusinessException("请选择参与拍卖的非遗作品");
        return request.workId();
    }

    private BigDecimal money(BigDecimal value, String label) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(label + "必须大于0");
        if (value.scale() > 2) throw new BusinessException(label + "最多保留两位小数");
        if (value.compareTo(MAX_MONEY) > 0) throw new BusinessException(label + "不能超过" + MAX_MONEY.toPlainString());
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void requireWorkManager(ArtisanProfile artisan) {
        SysUser user = UserSession.get();
        if (user == null) throw new BusinessException("请先登录");
        if (!"ADMIN".equals(user.getRole()) && (!"ARTISAN".equals(user.getRole())
                || artisan.getUserId() == null || !artisan.getUserId().equals(user.getId()))) {
            throw new BusinessException("只能为自己的非遗作品创建拍卖");
        }
    }

    private void requireManager(HeritageAuction auction) {
        SysUser user = UserSession.get();
        if (user == null) throw new BusinessException("请先登录");
        if (!"ADMIN".equals(user.getRole()) && (!"ARTISAN".equals(user.getRole())
                || auction.getSellerId() == null || !auction.getSellerId().equals(user.getId()))) {
            throw new BusinessException("仅管理员或拍卖发起人可执行此操作");
        }
    }

    private boolean canManage(HeritageAuction auction) {
        SysUser user = UserSession.get();
        return user != null && ("ADMIN".equals(user.getRole())
                || "ARTISAN".equals(user.getRole()) && auction.getSellerId() != null && auction.getSellerId().equals(user.getId()));
    }

    private void requireAdmin() {
        SysUser user = UserSession.get();
        if (user == null || !"ADMIN".equals(user.getRole())) {
            throw new BusinessException("为保证竞价公平，仅管理员可以提前结束拍卖");
        }
    }

    private void requireVisible(HeritageAuction auction) {
        if (List.of("DRAFT", "CANCELLED").contains(auction.getStatus()) && !canManage(auction)) {
            throw new BusinessException("无权查看该拍卖");
        }
    }

    private HeritageAuction require(Long id) {
        HeritageAuction auction = auctions.selectById(id);
        if (auction == null) throw new BusinessException("拍卖信息不存在");
        return auction;
    }

    private void synchronizeActiveAuctions() {
        auctions.selectList(new LambdaQueryWrapper<HeritageAuction>()
                .in(HeritageAuction::getStatus, List.of("UPCOMING", "ONGOING")))
                .forEach(this::synchronize);
    }

    private void synchronizeWorkAuctions(Long workId) {
        auctions.selectList(new LambdaQueryWrapper<HeritageAuction>()
                .eq(HeritageAuction::getWorkId, workId)
                .in(HeritageAuction::getStatus, List.of("UPCOMING", "ONGOING")))
                .forEach(this::synchronize);
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void refreshStatuses() {
        synchronizeActiveAuctions();
    }

    private HeritageAuction synchronize(HeritageAuction auction) {
        return lifecycleTransaction.execute(transaction -> {
            HeritageAuction current = require(auction.getId());
            auctions.startIfDue(current.getId());
            int finished = auctions.finishIfExpired(current.getId());
            current = require(current.getId());
            if (finished == 1) {
                markWorkSold(current);
                recordSystem(current, current.getBidCount() != null && current.getBidCount() > 0
                        ? "到时自动成交" : "到时自动流拍");
            }
            return current;
        });
    }

    private void record(String operationType, HeritageAuction auction, String detail) {
        SysUser user = UserSession.get();
        if (user == null) return;
        OperationLog log = new OperationLog();
        log.setOperatorId(user.getId());
        log.setOperatorName(user.getRealName());
        log.setModuleName("非遗拍卖");
        log.setOperationType(operationType);
        log.setTargetId(auction.getId());
        log.setOperationDetail(detail);
        log.setResultStatus("SUCCESS");
        log.setCreatedAt(LocalDateTime.now());
        operationLogs.insert(log);
    }

    private void recordSystem(HeritageAuction auction, String detail) {
        OperationLog log = new OperationLog();
        log.setOperatorId(null);
        log.setOperatorName("系统任务");
        log.setModuleName("非遗拍卖");
        log.setOperationType("自动结束拍卖");
        log.setTargetId(auction.getId());
        log.setOperationDetail(detail);
        log.setResultStatus("SUCCESS");
        log.setCreatedAt(LocalDateTime.now());
        operationLogs.insert(log);
    }

    private void markWorkSold(HeritageAuction auction) {
        if (auction.getBidCount() == null || auction.getBidCount() <= 0) return;
        works.markSold(auction.getWorkId());
    }

    private HeritageAuction sanitizeForViewer(HeritageAuction auction) {
        if (canManage(auction)) return auction;
        Long viewerId = UserSession.id();
        boolean ownWin = viewerId != null && viewerId.equals(auction.getWinnerId());
        auction.setSellerId(null);
        auction.setWinnerId(null);
        if (auction.getWinnerName() != null) auction.setWinnerName(ownWin ? "我" : maskName(auction.getWinnerName()));
        return auction;
    }

    private AuctionBid maskedBid(AuctionBid source, Long viewerId) {
        AuctionBid copy = new AuctionBid();
        copy.setId(source.getId());
        copy.setAuctionId(source.getAuctionId());
        copy.setBidderId(null);
        copy.setBidderName(viewerId != null && viewerId.equals(source.getBidderId()) ? "我" : maskName(source.getBidderName()));
        copy.setBidAmount(source.getBidAmount());
        copy.setBidTime(source.getBidTime());
        return copy;
    }

    private String maskName(String name) {
        if (name == null || name.isBlank()) return "匿名用户";
        return name.substring(0, 1) + "**";
    }

    public record AuctionRequest(Long workId, String title, String description,
                                 BigDecimal startPrice, BigDecimal minIncrement,
                                 LocalDateTime startTime, LocalDateTime endTime) {}
}
