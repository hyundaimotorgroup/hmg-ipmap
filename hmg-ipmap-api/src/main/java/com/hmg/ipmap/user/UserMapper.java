package com.hmg.ipmap.user;

import com.hmg.ipmap.user.dto.UserRequestDto;
import com.hmg.ipmap.user.dto.UserResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "sourceIp", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "apiKey", ignore = true)
    UserEntity toEntity(UserRequestDto userRequestDto);

    @Mapping(target = "parentId", source = "parent.id")
    UserResponseDto toDto(UserEntity user);
}
