package cn.iocoder.yudao.module.mate.controller.app.match;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.mate.dal.dataobject.match.MateMatchDO;
import cn.iocoder.yudao.module.mate.framework.auth.RequireAuthLevel;
import cn.iocoder.yudao.module.mate.service.match.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户端 - 匹配与附近")
@RestController
@RequestMapping("/mate/match")
@Validated
public class AppMatchController {

    @Resource
    private MatchService matchService;

    public record LocationReqVO(@NotNull Double longitude, @NotNull Double latitude, @NotNull String cityCode) {}
    public record SwipeReqVO(@NotNull Long targetId, @NotNull Integer action) {}
    public record SwipeRespVO(Boolean matched, Long matchId) {}

    @PostMapping("/location")
    @Operation(summary = "上报定位")
    @RequireAuthLevel(2)
    public CommonResult<Boolean> location(@Valid @RequestBody LocationReqVO reqVO) {
        matchService.reportLocation(SecurityFrameworkUtils.getLoginUserId(),
                reqVO.longitude(), reqVO.latitude(), reqVO.cityCode());
        return success(true);
    }

    @GetMapping("/nearby")
    @Operation(summary = "附近的人")
    @RequireAuthLevel(2)
    public CommonResult<List<MatchService.NearbyUser>> nearby(@RequestParam String cityCode,
                                                              @RequestParam Double longitude,
                                                              @RequestParam Double latitude,
                                                              @RequestParam(required = false) Double radius) {
        return success(matchService.nearby(SecurityFrameworkUtils.getLoginUserId(),
                cityCode, longitude, latitude, radius));
    }

    @PostMapping("/swipes")
    @Operation(summary = "滑动喜欢/跳过")
    @RequireAuthLevel(2)
    public CommonResult<SwipeRespVO> swipe(@Valid @RequestBody SwipeReqVO reqVO) {
        MateMatchDO match = matchService.swipe(SecurityFrameworkUtils.getLoginUserId(),
                reqVO.targetId(), reqVO.action());
        return success(new SwipeRespVO(match != null, match == null ? null : match.getMatchId()));
    }
}
