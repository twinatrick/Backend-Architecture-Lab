package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IApiUsageLogDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IBotConfigDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ILineGfSessionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserVoiceUploadDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IVoiceTranslationDataAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        UserVoiceUploadDataAccessImpl.class,
        LineGfSessionDataAccessImpl.class,
        BotConfigDataAccessImpl.class,
        ApiUsageLogDataAccessImpl.class,
        VoiceTranslationDataAccessImpl.class
})
@DisplayName("External API DataAccess Spring Context Wiring 裝配測試")
class ExternalApiDataAccessWiringTest {

    private final IUserVoiceUploadDataAccess userVoiceUploadDataAccess;
    private final ILineGfSessionDataAccess lineGfSessionDataAccess;
    private final IBotConfigDataAccess botConfigDataAccess;
    private final IApiUsageLogDataAccess apiUsageLogDataAccess;
    private final IVoiceTranslationDataAccess voiceTranslationDataAccess;

    @Autowired
    public ExternalApiDataAccessWiringTest(
            IUserVoiceUploadDataAccess userVoiceUploadDataAccess,
            ILineGfSessionDataAccess lineGfSessionDataAccess,
            IBotConfigDataAccess botConfigDataAccess,
            IApiUsageLogDataAccess apiUsageLogDataAccess,
            IVoiceTranslationDataAccess voiceTranslationDataAccess) {
        this.userVoiceUploadDataAccess = userVoiceUploadDataAccess;
        this.lineGfSessionDataAccess = lineGfSessionDataAccess;
        this.botConfigDataAccess = botConfigDataAccess;
        this.apiUsageLogDataAccess = apiUsageLogDataAccess;
        this.voiceTranslationDataAccess = voiceTranslationDataAccess;
    }

    @Test
    @DisplayName("所有 DataAccess Bean 應由 Spring Context 正確完成建構子注入裝配")
    void allDataAccessBeansShouldBeWiredSuccessfully() {
        assertThat(userVoiceUploadDataAccess)
                .isNotNull()
                .isInstanceOf(UserVoiceUploadDataAccessImpl.class);
        assertThat(lineGfSessionDataAccess)
                .isNotNull()
                .isInstanceOf(LineGfSessionDataAccessImpl.class);
        assertThat(botConfigDataAccess)
                .isNotNull()
                .isInstanceOf(BotConfigDataAccessImpl.class);
        assertThat(apiUsageLogDataAccess)
                .isNotNull()
                .isInstanceOf(ApiUsageLogDataAccessImpl.class);
        assertThat(voiceTranslationDataAccess)
                .isNotNull()
                .isInstanceOf(VoiceTranslationDataAccessImpl.class);
    }
}
