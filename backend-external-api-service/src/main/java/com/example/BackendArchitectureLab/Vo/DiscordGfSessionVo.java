package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ApiDto(description = "Discord 虛擬伴侶對話工作階段模型")
public class DiscordGfSessionVo extends BaseGfSessionVo {
    @ApiField(description = "Discord 伺服器（Guild）識別碼", example = "123456789012345678")
    private String guildId;
    @ApiField(description = "Discord 頻道識別碼", example = "987654321098765432")
    private String channelId;
    @ApiField(description = "Discord 使用者識別碼", example = "112233445566778899")
    private String userId;
    @ApiField(description = "虛擬伴侶頭像網址", example = "https://example.com/avatar/gf.png")
    private String gfAvatarUrl;
}
