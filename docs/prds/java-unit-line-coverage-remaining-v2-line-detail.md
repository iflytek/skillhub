# Java Line Coverage Remaining Detail (V2)

当前明细直接来自各模块 `target/site/jacoco/jacoco.xml`。

说明：

- `missed` / `covered` 为 JaCoCo class-level line coverage 计数
- `lines=` 为对应 `sourcefile` 中 `mi > 0` 的行号
- 标记为 `[shared-source]` 的条目表示同一源文件里存在多个未达标类，行号需结合该文件一起处理

## skillhub-app

### repository

- `com.iflytek.skillhub.repository.AdminUserSearchRepository` | source=`AdminUserSearchRepository.java` | missed=`29` | covered=`3` | lines=`42,44,45,46,47,48,49,51,52,53,54,56,57,58,59,60,61,63,71,72,73,74,75,76,77,78,81,82,84`
- `com.iflytek.skillhub.repository.JpaGovernanceQueryRepository` | source=`JpaGovernanceQueryRepository.java` | missed=`12` | covered=`150` | lines=`70,81,92,104,122,131,136,142,150,159,164,175,184,186,199,210,212,221,222,223,224,225,231,240,242,243,244,245,250,251,260,261,267,276,290,291`
- `com.iflytek.skillhub.repository.JpaAdminSkillReportQueryRepository` | source=`JpaAdminSkillReportQueryRepository.java` | missed=`2` | covered=`31` | lines=`30,34`
- `com.iflytek.skillhub.repository.JpaMySkillQueryRepository` | source=`JpaMySkillQueryRepository.java` | missed=`2` | covered=`52` | lines=`93,102`
- `com.iflytek.skillhub.repository.JpaProfileReviewQueryRepository` | source=`JpaProfileReviewQueryRepository.java` | missed=`2` | covered=`38` | lines=`30,38`

### config / bootstrap / runtime

- `com.iflytek.skillhub.config.RuntimeStateEnvironmentPostProcessor` | source=`RuntimeStateEnvironmentPostProcessor.java` | missed=`2` | covered=`9` | lines=`23,24`
- `com.iflytek.skillhub.config.RedissonConfig` | source=`RedissonConfig.java` | missed=`1` | covered=`45` | lines=`22`
- `com.iflytek.skillhub.SkillhubApplication` | source=`SkillhubApplication.java` | missed=`2` | covered=`1` | lines=`19,20`
- `com.iflytek.skillhub.bootstrap.LocalFileIndexStartupSynchronizer` | source=`LocalFileIndexStartupSynchronizer.java` | missed=`5` | covered=`24` | lines=`44,79,84,85,86`

### controller / dto

- `com.iflytek.skillhub.controller.MockUassController` | source=`MockUassController.java` | missed=`17` | covered=`3` | lines=`37,39,40,41,42,43,44,45,52,53,54,55,56,57,58,59,61`
- `com.iflytek.skillhub.controller.DeviceAuthWebController` | source=`DeviceAuthWebController.java` | missed=`12` | covered=`0` | lines=`33,34,35,36,44,45,46,50,51,52,53,55,58` | `[shared-source]`
- `com.iflytek.skillhub.controller.DeviceAuthWebController.AuthorizeRequest` | source=`DeviceAuthWebController.java` | missed=`1` | covered=`0` | lines=`33,34,35,36,44,45,46,50,51,52,53,55,58` | `[shared-source]`
- `com.iflytek.skillhub.controller.UserProfileController` | source=`UserProfileController.java` | missed=`12` | covered=`71` | lines=`80,135,160,161,165,167,168,171,172,175,216,218,225,228,232,240`
- `com.iflytek.skillhub.controller.AuthController` | source=`AuthController.java` | missed=`7` | covered=`51` | lines=`91,92,96,102,172,173,174,176,187,190,194`
- `com.iflytek.skillhub.controller.DeviceAuthController` | source=`DeviceAuthController.java` | missed=`5` | covered=`0` | lines=`25,26,27,31,36,39` | `[shared-source]`
- `com.iflytek.skillhub.controller.DeviceAuthController.TokenRequest` | source=`DeviceAuthController.java` | missed=`1` | covered=`0` | lines=`25,26,27,31,36,39` | `[shared-source]`
- `com.iflytek.skillhub.controller.LocalAuthController` | source=`LocalAuthController.java` | missed=`4` | covered=`38` | lines=`80,81,82,117,120,124`
- `com.iflytek.skillhub.controller.TokenController` | source=`TokenController.java` | missed=`4` | covered=`34` | lines=`43,47,48,49,50`
- `com.iflytek.skillhub.controller.AccountMergeController` | source=`AccountMergeController.java` | missed=`3` | covered=`19` | lines=`39,54,68`
- `com.iflytek.skillhub.controller.BaseApiController` | source=`BaseApiController.java` | missed=`1` | covered=`4` | lines=`22`
- `com.iflytek.skillhub.controller.admin.AdminProfileReviewController` | source=`AdminProfileReviewController.java` | missed=`1` | covered=`27` | lines=`89,92,96`
- `com.iflytek.skillhub.controller.admin.UserManagementController` | source=`UserManagementController.java` | missed=`1` | covered=`14` | lines=`90`
- `com.iflytek.skillhub.controller.portal.SkillPublishController` | source=`SkillPublishController.java` | missed=`2` | covered=`20` | lines=`64,65`
- `com.iflytek.skillhub.controller.portal.SkillSearchController` | source=`SkillSearchController.java` | missed=`2` | covered=`22` | lines=`89,90,96`
- `com.iflytek.skillhub.controller.portal.SkillRatingController` | source=`SkillRatingController.java` | missed=`1` | covered=`10` | lines=`44`
- `com.iflytek.skillhub.controller.portal.SkillStarController` | source=`SkillStarController.java` | missed=`1` | covered=`10` | lines=`47`
- `com.iflytek.skillhub.dto.MockUassLoginRequest` | source=`MockUassLoginRequest.java` | missed=`1` | covered=`0` | lines=`6`
- `com.iflytek.skillhub.dto.MockUassLoginResponse` | source=`MockUassLoginResponse.java` | missed=`1` | covered=`0` | lines=`3`

### service / filter / ratelimit / security

- `com.iflytek.skillhub.security.AuthFailureThrottleService` | source=`AuthFailureThrottleService.java` | missed=`7` | covered=`83` | lines=`75,79,82,102,129,136,170,171`
- `com.iflytek.skillhub.service.SkillDeleteAppService` | source=`SkillDeleteAppService.java` | missed=`6` | covered=`53` | lines=`58,67,69,89,123,124,131,133,150,151,152,153,161,166`
- `com.iflytek.skillhub.service.LabelAdminAppService` | source=`LabelAdminAppService.java` | missed=`5` | covered=`72` | lines=`44,45,46,157,158,168,171,172,174` | `[shared-source]`
- `com.iflytek.skillhub.service.LabelAdminAppService$1` | source=`LabelAdminAppService.java` | missed=`3` | covered=`0` | lines=`44,45,46,157,158,168,171,172,174` | `[shared-source]`
- `com.iflytek.skillhub.service.NamespaceMemberCandidateService` | source=`NamespaceMemberCandidateService.java` | missed=`4` | covered=`29` | lines=`60,77,81,88`
- `com.iflytek.skillhub.service.AdminProfileReviewAppService` | source=`AdminProfileReviewAppService.java` | missed=`3` | covered=`12` | lines=`48,52,53`
- `com.iflytek.skillhub.service.AdminSkillReportAppService` | source=`AdminSkillReportAppService.java` | missed=`3` | covered=`10` | lines=`39,43,44,45`
- `com.iflytek.skillhub.service.ReviewSkillDetailAppService` | source=`ReviewSkillDetailAppService.java` | missed=`3` | covered=`69` | lines=`80,133,134,135,144,146,147`
- `com.iflytek.skillhub.service.AdminUserAppService` | source=`AdminUserAppService.java` | missed=`2` | covered=`67` | lines=`96,130,137`
- `com.iflytek.skillhub.service.GovernanceWorkbenchAppService` | source=`GovernanceWorkbenchAppService.java` | missed=`1` | covered=`70` | lines=`145,168,196,197`
- `com.iflytek.skillhub.service.SkillSearchAppService` | source=`SkillSearchAppService.java` | missed=`1` | covered=`86` | lines=`88,95,103,106,110,155,175`
- `com.iflytek.skillhub.filter.AuthContextFilter` | source=`AuthContextFilter.java` | missed=`3` | covered=`50` | lines=`79,84,104,123,124`
- `com.iflytek.skillhub.filter.RequestLoggingFilter` | source=`RequestLoggingFilter.java` | missed=`3` | covered=`45` | lines=`61,75,100,101`
- `com.iflytek.skillhub.ratelimit.InMemorySlidingWindowRateLimiter` | source=`InMemorySlidingWindowRateLimiter.java` | missed=`3` | covered=`15` | lines=`28,41,42`
- `com.iflytek.skillhub.ratelimit.RateLimitInterceptor` | source=`RateLimitInterceptor.java` | missed=`2` | covered=`60` | lines=`49,123,141`

## skillhub-auth

### oauth / mock

- `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor` | source=`GitHubClaimsExtractor.java` | missed=`27` | covered=`0` | lines=`20,22,23,24,25,28,32,33,34,35,36,37,39,41,44,50,51,52,53,54,56,57,60,61,62,63,64,67` | `[shared-source]`
- `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor$1` | source=`GitHubClaimsExtractor.java` | missed=`1` | covered=`0` | lines=`20,22,23,24,25,28,32,33,34,35,36,37,39,41,44,50,51,52,53,54,56,57,60,61,62,63,64,67` | `[shared-source]`
- `com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor.GitHubEmail` | source=`GitHubClaimsExtractor.java` | missed=`1` | covered=`0` | lines=`20,22,23,24,25,28,32,33,34,35,36,37,39,41,44,50,51,52,53,54,56,57,60,61,62,63,64,67` | `[shared-source]`
- `com.iflytek.skillhub.auth.mock.MockAuthFilter` | source=`MockAuthFilter.java` | missed=`22` | covered=`0` | lines=`39,40,41,42,43,48,49,50,51,52,53,54,55,56,57,58,59,60,62,63,65,66`
- `com.iflytek.skillhub.auth.local.PasswordResetService` | source=`PasswordResetService.java` | missed=`17` | covered=`97` | lines=`105,106,107,109,110,112,113,119,120,130,142,146,175,185,192,199,223,224,233`
- `com.iflytek.skillhub.auth.local.LocalAuthService` | source=`LocalAuthService.java` | missed=`16` | covered=`86` | lines=`74,80,85,118,123,144,145,147,148,151,152,153,156,157,158,159,160,215,227`
- `com.iflytek.skillhub.auth.oauth.GitLabClaimsExtractor` | source=`GitLabClaimsExtractor.java` | missed=`8` | covered=`43` | lines=`38,62,69,98,99,109,110,111,135`
- `com.iflytek.skillhub.auth.oauth.SkillHubOAuth2AuthorizationRequestResolver` | source=`SkillHubOAuth2AuthorizationRequestResolver.java` | missed=`3` | covered=`7` | lines=`31,32,33`
- `com.iflytek.skillhub.auth.oauth.OAuth2LoginFailureHandler` | source=`OAuth2LoginFailureHandler.java` | missed=`2` | covered=`8` | lines=`36,37`
- `com.iflytek.skillhub.auth.oauth.OAuth2LoginSuccessHandler` | source=`OAuth2LoginSuccessHandler.java` | missed=`2` | covered=`14` | lines=`47,48`
- `com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport` | source=`OAuthLoginRedirectSupport.java` | missed=`1` | covered=`7` | lines=`23`
- `com.iflytek.skillhub.auth.config.SecurityConfig` | source=`SecurityConfig.java` | missed=`4` | covered=`116` | lines=`114,115,116,166`

### device / request / properties

- `com.iflytek.skillhub.auth.device.DeviceAuthService` | source=`DeviceAuthService.java` | missed=`5` | covered=`52` | lines=`73,78,93,105,111`
- `com.iflytek.skillhub.auth.device.DeviceCodeData` | source=`DeviceCodeData.java` | missed=`1` | covered=`12` | lines=`11`
- `com.iflytek.skillhub.auth.merge.AccountMergeRequest` | source=`AccountMergeRequest.java` | missed=`5` | covered=`18` | lines=`32,47,62,63,66,76`
- `com.iflytek.skillhub.auth.uass.UassProperties` | source=`UassProperties.java` | missed=`1` | covered=`43` | lines=`91`

## skillhub-domain

### validation / metadata

- `com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy` | source=`SkillPackagePolicy.java` | missed=`29` | covered=`58` | lines=`41,46,49,52,58,64,71,77,78,79,82,83,84,88,91,95,101,104,105,107,108,111,112,113,116,117,118,154,155,165`
- `com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser` | source=`SkillMetadataParser.java` | missed=`14` | covered=`49` | lines=`19,30,44,45,46,47,62,67,68,74,83,88,94,104,105,107`
- `com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator` | source=`SkillPackageValidator.java` | missed=`14` | covered=`53` | lines=`127,129,131,133,135,137,138,140,146,147,148,149,150,153`
- `com.iflytek.skillhub.domain.skill.validation.BasicPrePublishValidator` | source=`BasicPrePublishValidator.java` | missed=`12` | covered=`32` | lines=`38,68,69,70,71,72,73,74,75,76,77,78,79,84,87`
- `com.iflytek.skillhub.domain.skill.validation.NoOpPrePublishValidator` | source=`NoOpPrePublishValidator.java` | missed=`2` | covered=`0` | lines=`6,10`

### namespace / user / label / review / governance / report / audit / social

- `com.iflytek.skillhub.domain.namespace.NamespaceService` | source=`NamespaceService.java` | missed=`11` | covered=`44` | lines=`90,91,92,94,95,97,101,102,111,126,127,128`
- `com.iflytek.skillhub.domain.social.SkillRating` | source=`SkillRating.java` | missed=`11` | covered=`11` | lines=`30,33,40,47,48,49,53,54,57,58,59,61,62`
- `com.iflytek.skillhub.domain.user.ProfileReviewService` | source=`ProfileReviewService.java` | missed=`11` | covered=`37` | lines=`46,47,48,49,50,51,56,147,148,155,156`
- `com.iflytek.skillhub.domain.label.LabelPermissionChecker` | source=`LabelPermissionChecker.java` | missed=`10` | covered=`0` | lines=`10,13,21,22,24,25,27,28,30,31`
- `com.iflytek.skillhub.domain.report.SkillReport` | source=`SkillReport.java` | missed=`7` | covered=`24` | lines=`38,54,55,67,68,79,91,119`
- `com.iflytek.skillhub.domain.audit.AuditLogQueryService` | source=`AuditLogQueryService.java` | missed=`4` | covered=`0` | lines=`15,16,17,20`
- `com.iflytek.skillhub.domain.namespace.NamespaceMemberService` | source=`NamespaceMemberService.java` | missed=`4` | covered=`48` | lines=`59,60,108,115`
- `com.iflytek.skillhub.domain.label.LabelSlugValidator` | source=`LabelSlugValidator.java` | missed=`3` | covered=`12` | lines=`18,27,30`
- `com.iflytek.skillhub.domain.review.ReviewPermissionChecker` | source=`ReviewPermissionChecker.java` | missed=`3` | covered=`37` | lines=`45,49,60,122`
- `com.iflytek.skillhub.domain.user.UserProfileService` | source=`UserProfileService.java` | missed=`3` | covered=`60` | lines=`121,186,187`
- `com.iflytek.skillhub.domain.governance.GovernanceNotificationService` | source=`GovernanceNotificationService.java` | missed=`1` | covered=`15` | lines=`61,67`
- `com.iflytek.skillhub.domain.namespace.SlugValidator` | source=`SlugValidator.java` | missed=`1` | covered=`25` | lines=`11`
- `com.iflytek.skillhub.domain.user.ModerationResult` | source=`ModerationResult.java` | missed=`1` | covered=`3` | lines=`23`
- `com.iflytek.skillhub.domain.user.UpdateProfileResult` | source=`UpdateProfileResult.java` | missed=`1` | covered=`2` | lines=`22,37` | `[shared-source]`
- `com.iflytek.skillhub.domain.user.UpdateProfileResult.Mixed` | source=`UpdateProfileResult.java` | missed=`1` | covered=`0` | lines=`22,37` | `[shared-source]`

### skill services / security

- `com.iflytek.skillhub.domain.skill.service.SkillTagService` | source=`SkillTagService.java` | missed=`5` | covered=`46` | lines=`56,67,89,93,101,102,122,129,143`
- `com.iflytek.skillhub.domain.skill.service.SkillStorageDeletionCompensationService` | source=`SkillStorageDeletionCompensationService.java` | missed=`4` | covered=`21` | lines=`64,65,72,73`
- `com.iflytek.skillhub.domain.skill.service.SkillHardDeleteService` | source=`SkillHardDeleteService.java` | missed=`3` | covered=`72` | lines=`99,141,180,181`
- `com.iflytek.skillhub.domain.security.SecurityScanService` | source=`SecurityScanService.java` | missed=`2` | covered=`88` | lines=`66,94,97,131,132`
- `com.iflytek.skillhub.domain.skill.service.SkillReviewSubmitService` | source=`SkillReviewSubmitService.java` | missed=`2` | covered=`46` | lines=`73,75,88,116,118,136`

## skillhub-infra

### scanner

- `com.iflytek.skillhub.infra.scanner.SkillScannerAdapter` | source=`SkillScannerAdapter.java` | missed=`2` | covered=`57` | lines=`65,88,94,114`
- `com.iflytek.skillhub.infra.scanner.SkillScannerService` | source=`SkillScannerService.java` | missed=`2` | covered=`62` | lines=`76,110,116`

### jpa repository

- `com.iflytek.skillhub.infra.jpa.NotificationJpaRepository` | source=`NotificationJpaRepository.java` | missed=`2` | covered=`0` | lines=`23,28`
- `com.iflytek.skillhub.infra.jpa.SkillJpaRepository` | source=`SkillJpaRepository.java` | missed=`2` | covered=`0` | lines=`29,39`
- `com.iflytek.skillhub.infra.jpa.SkillVersionJpaRepository` | source=`SkillVersionJpaRepository.java` | missed=`2` | covered=`0` | lines=`27,32`
- `com.iflytek.skillhub.infra.jpa.SecurityAuditJpaRepository` | source=`SecurityAuditJpaRepository.java` | missed=`1` | covered=`0` | lines=`65`
- `com.iflytek.skillhub.infra.jpa.SkillReportJpaRepository` | source=`SkillReportJpaRepository.java` | missed=`1` | covered=`0` | lines=`23`

## skillhub-storage

- `com.iflytek.skillhub.storage.S3StorageService` | source=`S3StorageService.java` | missed=`62` | covered=`68` | lines=`62,63,64,65,66,67,68,69,70,71,72,74,85,86,95,100,132,133,134,144,145,165,166,167,168,176,184,185,186,187,188,191,193,194,195,196,197,198,202,203,205,206,211,212,213,214,220,221,222,223,224,226,227,228,229,230,231,232,233,234,236,237,238`
- `com.iflytek.skillhub.storage.S3StorageProperties` | source=`S3StorageProperties.java` | missed=`9` | covered=`27` | lines=`28,41,42,44,46,47,48,49,50`
- `com.iflytek.skillhub.storage.StorageProperties` | source=`StorageProperties.java` | missed=`3` | covered=`4` | lines=`12,13,15`
- `com.iflytek.skillhub.storage.LocalFileStorageService` | source=`LocalFileStorageService.java` | missed=`2` | covered=`27` | lines=`41,50,59,74`
- `com.iflytek.skillhub.storage.StorageAccessException` | source=`StorageAccessException.java` | missed=`2` | covered=`4` | lines=`18,22`
