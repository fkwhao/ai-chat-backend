package com.ethan.chatapp.history.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ethan.chatapp.history.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
