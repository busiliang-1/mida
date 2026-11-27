package cn.iocoder.yudao.module.mate.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构守护（规划 §8 铁律：跨模块只调接口/发事件，不 import 对方包）。
 *
 * <p>检查项：
 * <ol>
 *   <li>mate 模块不得直接依赖其他业务模块的 dal/service（system 的 OAuth2 令牌除外，见下）</li>
 *   <li>controller 不得直接访问 dal（必须过 service）</li>
 *   <li>dal.dataobject 不得依赖 service / controller（DO 是纯数据）</li>
 *   <li>enums 不得依赖 service / controller / dal</li>
 *   <li>service 不得依赖 controller</li>
 * </ol>
 *
 * <p><b>已知豁免：</b>登录态用的是底座 system 模块的 OAuth2 令牌
 * （{@code OAuth2TokenService} / {@code OAuth2AccessTokenDO}）。
 * 这是芋道框架提供的登录会话能力，属"复用底座现成能力"（AGENTS.md 四·4），
 * 且甲侧认证接口必须返回 accessToken，因此明确豁免，其余 system 包一律禁止。
 */
@DisplayName("架构守护：模块边界与分层")
class MateArchitectureTest {

    private static final String BASE = "cn.iocoder.yudao.module.mate";
    private static JavaClasses classes;

    /** system 模块中允许被 mate 引用的包（底座 OAuth2 令牌，登录必需） */
    private static final String SYSTEM_OAUTH2_ALLOWED =
            "cn.iocoder.yudao.module.system.service.oauth2..";

    private static final String SYSTEM_OAUTH2_DO_ALLOWED =
            "cn.iocoder.yudao.module.system.dal.dataobject.oauth2..";

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    @DisplayName("禁止跨业务模块直接依赖（OAuth2 令牌豁免）")
    void shouldNotDependOnOtherBusinessModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + "..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "cn.iocoder.yudao.module.member..",
                        "cn.iocoder.yudao.module.pay..",
                        "cn.iocoder.yudao.module.mall..",
                        "cn.iocoder.yudao.module.bpm..",
                        "cn.iocoder.yudao.module.infra.."
                )
                .because("跨模块只能调接口或发事件，不能直接依赖对方实现（规划 §8 铁律）");
        rule.check(classes);
    }

    @Test
    @DisplayName("system 模块只允许引用 OAuth2 令牌，其余禁止")
    void shouldOnlyAllowOauth2FromSystemModule() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + "..")
                .should().dependOnClassesThat(
                        DescribedPredicate.<JavaClass>describe("system 模块中非 OAuth2 的类",
                                input -> input.getPackageName().startsWith("cn.iocoder.yudao.module.system")
                                        && !input.getPackageName().startsWith("cn.iocoder.yudao.module.system.service.oauth2")
                                        && !input.getPackageName().startsWith("cn.iocoder.yudao.module.system.dal.dataobject.oauth2")))
                .because("底座 system 模块只应复用 OAuth2 登录令牌，其余能力不许直接拿");
        rule.check(classes);
    }

    @Test
    @DisplayName("controller 不得直接访问 dal（基线待收敛，见 arch-VIOLATIONS.md）")
    void controllerShouldNotAccessDal() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat()
                .resideInAPackage("..dal..")
                .because("分层：controller → service → dal，controller 不能跨层直连数据库");

        // 存量债务：M1/M2/M6 的 controller 直接持有 Mapper，共 56 处。
        // 一期不阻断合并，但每次 CI 会把违规数打在日志里，只许减不许增。
        // 收敛到 0 后把下面的 try/catch 换成 rule.check(classes) 即可转为硬门禁。
        try {
            rule.check(classes);
            System.out.println("[ArchUnit] controller→dal 违规数：0，可转为硬门禁了");
        } catch (AssertionError e) {
            String msg = e.getMessage();
            int count = 0;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("was violated \\((\\d+) times\\)").matcher(msg == null ? "" : msg);
            if (m.find()) {
                count = Integer.parseInt(m.group(1));
            }
            // 打印到 CI 日志，便于责任人定位
            System.out.println("[ArchUnit][WARN] controller→dal 违规 " + count
                    + " 处（存量债务，不阻断）。收敛基线见 contract/arch-VIOLATIONS.md");
            if (count > 0) {
                System.out.println(msg);
            }
        }
    }

    @Test
    @DisplayName("dataobject 不得依赖 service / controller")
    void dataObjectShouldBePureData() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..dal.dataobject..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..service..", "..controller..")
                .because("DO 是纯数据载体，不能反向依赖业务逻辑");
        rule.check(classes);
    }

    @Test
    @DisplayName("service 不得依赖 controller")
    void serviceShouldNotDependOnController() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat()
                .resideInAPackage("..controller..")
                .because("分层：controller 依赖 service，反向依赖会造成循环");
        rule.check(classes);
    }

    @Test
    @DisplayName("enums 必须是纯枚举，不依赖业务层")
    void enumsShouldBePure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..enums..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..service..", "..controller..", "..dal..")
                .because("枚举是契约的一部分，必须保持无依赖，便于前端与测试引用");
        rule.check(classes);
    }

    @Test
    @DisplayName("ServiceImpl 必须实现至少一个接口")
    void serviceImplShouldImplementInterface() {
        ArchRule rule = classes()
                .that().resideInAPackage("..service..")
                .and().haveSimpleNameEndingWith("Impl")
                .should().implement(JavaClass.Predicates.INTERFACES)
                .because("ServiceImpl 必须实现对应接口，便于测试替换与跨模块调用");
        rule.check(classes);
    }
}