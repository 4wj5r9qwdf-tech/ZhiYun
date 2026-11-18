package org.dromara.ai.snailai;

import com.aizuda.snail.ai.common.model.Result;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiConversationIdentityRequest;
import com.aizuda.snail.ai.openapi.client.core.annotation.OpenApiMapping;

import java.util.List;
import java.util.Map;

/** 通过智能体后台读取员工对话及图片。 / Read employee history from the agent service. */
public interface SnailAiHistoryClient {
    @OpenApiMapping(path = "/openapi/v1/zhiyun/history/trace-images", method = OpenApiMapping.HttpMethod.POST)
    Result<List<Map<String, String>>> traceImages(Map<String, Object> request);
    @OpenApiMapping(path = "/openapi/v1/zhiyun/history/conversations", method = OpenApiMapping.HttpMethod.POST)
    Result<Map<String, Object>> conversations(Map<String, Object> request);
    @OpenApiMapping(path = "/openapi/v1/zhiyun/history/messages", method = OpenApiMapping.HttpMethod.POST)
    Result<List<Map<String, Object>>> messages(OpenApiConversationIdentityRequest request);

    @OpenApiMapping(path = "/openapi/v1/zhiyun/history/image", method = OpenApiMapping.HttpMethod.POST)
    Result<Map<String, String>> image(Map<String, Object> request);
}
