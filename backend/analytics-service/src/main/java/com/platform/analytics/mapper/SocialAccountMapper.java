package com.platform.analytics.mapper;

import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.entity.SocialAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.factory.Mappers;

/**
 * MapStruct mapper converting between {@link SocialAccount} entities and
 * their DTO representations. Access/refresh tokens are intentionally
 * excluded from the response for security.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SocialAccountMapper {

    SocialAccountMapper INSTANCE = Mappers.getMapper(SocialAccountMapper.class);

    @Mapping(target = "id", source = "id")
    SocialAccountResponse toResponse(SocialAccount entity);
}
