package com.platform.analytics.mapper;

import com.platform.analytics.dto.response.AnalyticsResponse;
import com.platform.analytics.entity.Analytics;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * MapStruct mapper converting between {@link Analytics} entities and their
 * DTO representations.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AnalyticsMapper {

    AnalyticsMapper INSTANCE = Mappers.getMapper(AnalyticsMapper.class);

    @Mapping(target = "socialAccountId", source = "socialAccount.id")
    AnalyticsResponse toResponse(Analytics entity);

    List<AnalyticsResponse> toResponseList(List<Analytics> entities);
}
