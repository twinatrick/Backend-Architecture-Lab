package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Entity.DiscordSubscription;
import com.example.BackendArchitectureLab.Vo.DiscordSubscriptionVo;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DiscordSubscriptionMapper {
    DiscordSubscriptionVo toVo(DiscordSubscription entity);
    List<DiscordSubscriptionVo> toVoList(List<DiscordSubscription> entities);
}
