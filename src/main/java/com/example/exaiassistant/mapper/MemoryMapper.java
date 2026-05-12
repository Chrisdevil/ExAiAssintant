package com.example.exaiassistant.mapper;

import com.example.exaiassistant.model.Memory;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MemoryMapper {

    @Insert("INSERT INTO memory (content, embedding, conversation_id, created_at) VALUES (#{content}, #{embedding}, #{conversationId}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Memory memory);

    @Select("SELECT id, content, embedding, conversation_id, created_at FROM memory ORDER BY created_at DESC")
    List<Memory> selectAll();
}
