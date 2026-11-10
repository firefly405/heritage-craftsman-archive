package com.qizixuan.heritage.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qizixuan.heritage.common.ApiResponse;
import com.qizixuan.heritage.common.BusinessException;
import com.qizixuan.heritage.common.UserSession;
import com.qizixuan.heritage.entity.OperationLog;
import com.qizixuan.heritage.mapper.OperationLogMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {
    private final OperationLogMapper mapper;

    public OperationLogController(OperationLogMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public ApiResponse<List<OperationLog>> list() {
        if (UserSession.get() == null || !"ADMIN".equals(UserSession.get().getRole()))
            throw new BusinessException("仅管理员可查看操作日志");
        return ApiResponse.ok(mapper.selectList(
                new LambdaQueryWrapper<OperationLog>().orderByDesc(OperationLog::getCreatedAt).last("LIMIT 200")));
    }
    @GetMapping("/page") public ApiResponse<Map<String,Object>> page(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="") String keyword){
        if(UserSession.get()==null || !"ADMIN".equals(UserSession.get().getRole()))throw new BusinessException("仅管理员可查看拍卖日志");
        if(page<1 || pageSize<1 || pageSize>100)throw new BusinessException("分页参数不正确");
        LambdaQueryWrapper<OperationLog> q=new LambdaQueryWrapper<OperationLog>().eq(OperationLog::getModuleName,"非遗拍卖").orderByDesc(OperationLog::getCreatedAt).orderByDesc(OperationLog::getId);
        if(!keyword.isBlank())q.and(x->x.like(OperationLog::getOperationType,keyword.trim()).or().like(OperationLog::getOperatorName,keyword.trim()).or().like(OperationLog::getOperationDetail,keyword.trim()));
        Long total=mapper.selectCount(q);
        q.last("LIMIT "+pageSize+" OFFSET "+((long)(page-1)*pageSize));
        return ApiResponse.ok(Map.of("records",mapper.selectList(q),"total",total));
    }
}
