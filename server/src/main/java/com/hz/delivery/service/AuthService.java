package com.hz.delivery.service;

import com.hz.delivery.common.BizException;
import com.hz.delivery.common.Constants;
import com.hz.delivery.common.ErrorCode;
import com.hz.delivery.config.BizProperties;
import com.hz.delivery.entity.Grantee;
import com.hz.delivery.mapper.GranteeMapper;
import com.hz.delivery.service.sms.auth.SmsVerificationService;
import com.hz.delivery.service.sms.auth.SmsVerificationException;
import com.hz.delivery.vo.ClientLoginVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * C 端身份核验：手机号必须在甲方提供的名单内，验证码登录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String SMS_LIMIT_KEY = "hz:sms:limit:";
    private static final String SMS_DAILY_KEY = "hz:sms:daily:";
    private static final String LOGIN_FAIL_KEY = "hz:login:fail:";
    private static final String LOGIN_LOCK_KEY = "hz:login:lock:";

    /** 免短信登录：手机号 + 姓名核验（仅演示）。 */
    public static final String MODE_NAME = "name";
    /** 短信验证码登录：接入真实短信通道后启用 */
    public static final String MODE_SMS = "sms";

    private final GranteeMapper granteeMapper;
    private final StringRedisTemplate redis;
    private final BizProperties props;
    private final SmsVerificationService smsVerification;

    /** 当前是否为短信验证码登录方式 */
    public boolean smsMode() {
        return MODE_SMS.equalsIgnoreCase(props.getLoginMode());
    }

    /**
     * 登录方式元信息。C 端据此决定登录页渲染「姓名」输入框还是「验证码」输入框，
     * 以及是否需要「获取验证码」按钮。
     */
    public Map<String, Object> loginModeInfo() {
        boolean sms = smsMode();
        Map<String, Object> m = new HashMap<>();
        m.put("mode", sms ? MODE_SMS : MODE_NAME);
        m.put("nameRequired", !sms);
        m.put("codeRequired", sms);
        m.put("maxFail", props.getLoginMaxFail());
        m.put("lockMinutes", props.getLoginLockMinutes());
        if (sms) {
            m.put("codeTtl", props.getCodeTtl());
            // 仅演示通道才下发固定验证码提示，接入真实通道后绝不会返回
            if (smsVerification.mockCodeHint() != null) {
                m.put("mockCode", smsVerification.mockCodeHint());
            }
        }
        return m;
    }

    @PostConstruct
    void selfCheck() {
        if (smsVerification.mockCodeHint() == null && StringUtils.hasText(props.getMockSmsCode())) {
            log.warn("登录认证 provider={}，忽略演示固定验证码 MOCK_SMS_CODE", smsVerification.provider());
        }
        log.info("C 端登录方式={}，验证码认证={}，通知短信={}",
                props.getLoginMode(), smsVerification.provider(), props.getSms().getProvider());
    }

    /**
     * 发送验证码。名单外手机号直接拦截，不发送，避免无效短信费用。
     */
    public Map<String, Object> sendCode(String phone) {
        // 免短信登录方式下不提供验证码通道，直接回明确的业务提示，避免前端误以为发送失败
        if (!smsMode()) {
            throw BizException.of(ErrorCode.SMS_NOT_REQUIRED);
        }
        Grantee g = granteeMapper.selectByPhone(phone);
        if (g == null) {
            throw BizException.of(ErrorCode.PHONE_NOT_IN_LIST);
        }
        if (g.getStatus() == null || g.getStatus() != 1) {
            throw BizException.of(ErrorCode.GRANTEE_DISABLED);
        }

        String limitKey = SMS_LIMIT_KEY + phone;
        String reservation = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(limitKey, reservation, Duration.ofSeconds(60)))) {
            throw BizException.of(ErrorCode.CODE_TOO_FREQUENT);
        }

        // 每日总量闸门。60 秒频控只挡「同一号码连点」，挡不住「对名单内号码逐个轰炸」，
        // 后者既消耗短信费用又骚扰教职工，故按自然日再设一道上限。
        String dailyKey = SMS_DAILY_KEY + LocalDate.now() + ":" + phone;
        Long todaySent = redis.opsForValue().increment(dailyKey);
        if (todaySent != null && todaySent == 1L) {
            redis.expire(dailyKey, 2, TimeUnit.DAYS);
        }
        if (todaySent != null && todaySent > props.getSmsDailyLimit()) {
            // 超限的这次不计入配额，否则计数会被越推越高
            redis.opsForValue().decrement(dailyKey);
            releaseCooldown(limitKey, reservation);
            log.warn("验证码发送触发每日上限 phone={} limit={}", maskPhone(phone), props.getSmsDailyLimit());
            throw BizException.of(ErrorCode.CODE_TOO_FREQUENT,
                    "今日验证码发送次数已达上限（" + props.getSmsDailyLimit()
                            + " 次），请明日再试，或联系本单位工会核实");
        }

        SmsVerificationService.SendCodeResult sent;
        try {
            sent = smsVerification.sendCode(phone);
        } catch (RuntimeException e) {
            // 只有明确未发送才退回额度；超时/未知结果保留60秒冷却及当天额度。
            if (e instanceof SmsVerificationException failure && !failure.isDeliveryUncertain()) {
                redis.opsForValue().decrement(dailyKey);
                releaseCooldown(limitKey, reservation);
            }
            log.warn("验证码下发未确认 phone={} provider={} type={}",
                    maskPhone(phone), smsVerification.provider(), e.getClass().getSimpleName());
            throw e;
        }
        log.info("验证码已下发 phone={} provider={}", maskPhone(phone), sent.provider());

        Map<String, Object> result = new HashMap<>();
        result.put("sent", true);
        result.put("ttl", sent.ttl());
        result.put("provider", sent.provider());
        // 仅演示通道回传验证码，方便联调；真实通道下绝不回传
        if (sent.mockCode() != null) {
            result.put("mockCode", sent.mockCode());
            result.put("tip", "当前为演示通道，验证码固定为 " + sent.mockCode());
        }
        return result;
    }

    /**
     * 身份核验并签发 Token。
     *
     * <p>核验方式由 {@code hz.delivery.login-mode} 决定：
     * <ul>
     *   <li>{@code name}：手机号 + 姓名，仅演示，二者须与甲方名单同时匹配；</li>
     *   <li>{@code sms}：手机号 + 短信验证码，接入真实短信服务后启用。</li>
     * </ul>
     *
     * <p>无论哪种方式，失败次数都会累计；达到上限后临时锁定该手机号，
     * 防止有人借同事的手机号枚举姓名冒领。
     */
    public ClientLoginVO login(String phone, String code, String name) {
        String lockKey = LOGIN_LOCK_KEY + phone;
        // 已锁定：直接拒绝，连名单都不查，避免被当成探测名单的工具
        Long lockTtl = redis.getExpire(lockKey, TimeUnit.MINUTES);
        if (lockTtl != null && lockTtl >= 0) {
            throw BizException.of(ErrorCode.LOGIN_LOCKED,
                    "身份核验失败次数过多，请 " + Math.max(1L, lockTtl) + " 分钟后再试");
        }

        Grantee g = granteeMapper.selectByPhone(phone);
        if (g == null) {
            throw BizException.of(ErrorCode.PHONE_NOT_IN_LIST);
        }
        if (g.getStatus() == null || g.getStatus() != 1) {
            throw BizException.of(ErrorCode.GRANTEE_DISABLED);
        }

        boolean sms = smsMode();
        boolean pass;
        String failReason;
        if (sms) {
            pass = smsVerification.verifyCode(phone, code);
            failReason = "验证码错误或已失效";
        } else {
            pass = StringUtils.hasText(name) && normalizeName(name).equals(normalizeName(g.getName()));
            failReason = "姓名与名单登记不一致，请填写本人真实姓名";
        }

        if (!pass) {
            long fails = recordFail(phone);
            if (fails >= props.getLoginMaxFail()) {
                redis.opsForValue().set(lockKey, "1", props.getLoginLockMinutes(), TimeUnit.MINUTES);
                redis.delete(LOGIN_FAIL_KEY + phone);
                log.warn("C 端身份核验连续失败 {} 次，手机号 {} 已锁定 {} 分钟",
                        fails, maskPhone(phone), props.getLoginLockMinutes());
                throw BizException.of(ErrorCode.LOGIN_LOCKED,
                        "身份核验已连续失败 " + fails + " 次，请 "
                                + props.getLoginLockMinutes() + " 分钟后再试，或联系本单位工会核实");
            }
            long remain = Math.max(0, props.getLoginMaxFail() - fails);
            throw BizException.of(sms ? ErrorCode.CODE_ERROR : ErrorCode.NAME_MISMATCH,
                    failReason + "，还可尝试 " + remain + " 次");
        }

        redis.delete(LOGIN_FAIL_KEY + phone);

        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(Constants.KEY_CLIENT_TOKEN + token, String.valueOf(g.getId()),
                Constants.CLIENT_TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("C 端身份核验通过 granteeId={} phone={} mode={}", g.getId(), maskPhone(phone), props.getLoginMode());

        return toVO(g, token);
    }

    /** 累加失败次数，首次失败时设置与锁定同长的过期窗口 */
    private long recordFail(String phone) {
        String key = LOGIN_FAIL_KEY + phone;
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1L) {
            redis.expire(key, props.getLoginLockMinutes(), TimeUnit.MINUTES);
        }
        return n == null ? 1L : n;
    }

    private void releaseCooldown(String key, String owner) {
        var script = new org.springframework.data.redis.core.script.DefaultRedisScript<Long>(
                "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end", Long.class);
        redis.execute(script, java.util.List.of(key), owner);
    }

    /** 姓名归一化：去掉首尾与中间的所有空白（含全角空格），避免因空格录入差异被误拒 */
    private String normalizeName(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\u3000]", "");
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 名单预校验：不发送短信，仅判断手机号是否在甲方提供的名单内，用于前端实时提示
     */
    public Map<String, Object> checkPhone(String phone) {
        Map<String, Object> m = new HashMap<>();
        if (!StringUtils.hasText(phone) || !phone.matches("^1\\d{10}$")) {
            m.put("inList", false);
            m.put("message", "请填写正确的 11 位手机号");
            return m;
        }
        Grantee g = granteeMapper.selectByPhone(phone);
        if (g == null) {
            m.put("inList", false);
            m.put("message", "该手机号不在本次慰问品领取名单内，请联系本单位工会核实");
            return m;
        }
        // 只回「是否在名单」。本接口免登录可调且无频率限制，
        // 若连姓名/单位/部门一并回传，等于把花名册开放给任何知道手机号的人
        //（脱敏成「张*」后结合单位信息同样能被推断出来）。
        m.put("inList", true);
        m.put("message", "该手机号在本次领取名单内，请使用本人手机号获取验证码登录");
        return m;
    }

    /**
     * 根据已有 Token 获取当前登录人（用于页面刷新后恢复会话）
     */
    public ClientLoginVO profile(Long granteeId) {
        Grantee g = granteeMapper.selectById(granteeId);
        if (g == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return toVO(g, null);
    }

    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            redis.delete(Constants.KEY_CLIENT_TOKEN + token);
        }
    }

    private ClientLoginVO toVO(Grantee g, String token) {
        ClientLoginVO vo = new ClientLoginVO();
        vo.setToken(token);
        vo.setGranteeId(g.getId());
        vo.setEmpNo(g.getEmpNo());
        vo.setName(g.getName());
        vo.setPhone(g.getPhone());
        vo.setOrg(g.getOrg());
        vo.setDept(g.getDept());
        vo.setQuota(g.getQuota());
        vo.setUsed(g.getUsed());
        vo.setCanClaim(g.getUsed() == null || g.getQuota() == null || g.getUsed() < g.getQuota());
        return vo;
    }
}
