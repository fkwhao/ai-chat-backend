package com.ethan.chatapp.history.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ethan.chatapp.history.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
